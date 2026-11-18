package ai.dana.data.account

import ai.dana.data.network.EngineProtocol
import ai.dana.data.network.EngineProtocol.string
import ai.dana.data.network.boundedUtf8
import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountInput
import ai.dana.domain.AccountProblem
import ai.dana.domain.ApiCredential
import ai.dana.domain.EmailProject
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink

internal class VerifiedSession(val email: String, val refresh: ApiCredential, val access: String) {
    override fun toString() = "VerifiedSession([redacted])"
}

/** Passwordless Supabase Auth REST. No redirect, deep-link token, sender password or admin key. */
internal class SupabaseEmailApi(private val client: OkHttpClient) {
    suspend fun send(project: EmailProject, email: String) {
        post(project, Step.SEND, buildJsonObject { put("email", email); put("create_user", true) }) { Unit }
    }

    suspend fun verify(project: EmailProject, email: String, code: String): VerifiedSession =
        post(project, Step.VERIFY, buildJsonObject { put("email", email); put("token", code); put("type", "email") }) {
            session(it, email)
        }

    suspend fun refresh(project: EmailProject, refresh: ApiCredential): VerifiedSession =
        post(project, Step.REFRESH, buildJsonObject { put("refresh_token", refresh.value) }) { session(it, null) }

    suspend fun revoke(project: EmailProject, access: String) {
        post(project, Step.REVOKE, buildJsonObject {}, access) { Unit }
    }

    private enum class Step(val path: String) { SEND("otp"), VERIFY("verify"), REFRESH("token"), REVOKE("logout") }

    private suspend fun <T> post(
        project: EmailProject,
        step: Step,
        data: JsonObject,
        accessToken: String? = null,
        decode: (Response) -> T,
    ): T = suspendCancellableCoroutine { result ->
        val url = project.origin.toHttpUrl().newBuilder().addPathSegments("auth/v1").addPathSegment(step.path).apply {
            if (step == Step.REFRESH) addQueryParameter("grant_type", "refresh_token")
            if (step == Step.REVOKE) addQueryParameter("scope", "local")
        }.build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("apikey", project.publishableKey)
            .header("Cache-Control", "no-store")
            .apply { if (accessToken != null) header("Authorization", "Bearer $accessToken") }
            .post(Once(data.toString().toRequestBody(JSON_TYPE))).build()
        val call = client.newCall(request)
        result.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                result.resumeWith(Result.failure(AccountFailure(if (e is SSLException) AccountProblem.TLS else AccountProblem.NETWORK)))
            }

            override fun onResponse(call: Call, response: Response) {
                result.resumeWith(runCatching {
                    response.use {
                        if (!it.isSuccessful) throw AccountFailure(problem(it, step))
                        decode(it)
                    }
                }.recoverCatching { thrown ->
                    throw when (thrown) {
                        is AccountFailure -> thrown
                        is SSLException -> AccountFailure(AccountProblem.TLS)
                        is IOException -> AccountFailure(AccountProblem.NETWORK)
                        else -> AccountFailure(AccountProblem.RESPONSE)
                    }
                })
            }
        })
    }

    private fun problem(response: Response, step: Step): AccountProblem {
        val code = response.code
        if (code == 429) return AccountProblem.RATE_LIMIT
        if (code in 300..399) return AccountProblem.RESPONSE
        if (code >= 500 || code == 408) return AccountProblem.UNAVAILABLE
        // Consume only a bounded machine-readable code; never show remote messages with PII.
        val tag = runCatching { EngineProtocol.decodeObject(response.body.boundedUtf8(8_192))["error_code"].string() }.getOrNull()
        if (step == Step.VERIFY && tag in CODE_ERRORS) return AccountProblem.INVALID_CODE
        if (step == Step.REFRESH && tag in SESSION_ERRORS) return AccountProblem.EXPIRED_SESSION
        if (code == 401 || tag in SETUP_ERRORS) return AccountProblem.SETUP
        if (step == Step.VERIFY && code == 400) return AccountProblem.INVALID_CODE
        return AccountProblem.UNAVAILABLE
    }

    private fun session(response: Response, expected: String?): VerifiedSession {
        if (response.body.contentType()?.subtype != "json") throw AccountFailure(AccountProblem.RESPONSE)
        val root = EngineProtocol.decodeObject(response.body.boundedUtf8(65_536))
        val user = root["user"] as? JsonObject ?: throw AccountFailure(AccountProblem.RESPONSE)
        val email = user["email"].string()?.let(AccountInput::email)
        val confirmedAt = user["email_confirmed_at"].string()
        val type = root["token_type"].string()
        val access = root["access_token"].string()
        val refresh = root["refresh_token"].string()
        if (email == null || confirmedAt.isNullOrBlank() || (expected != null && !expected.equals(email, true)) ||
            !type.equals("bearer", true) || access == null || access.length !in 1..8192 || access.any { it !in '!'..'~' } || refresh == null
        ) throw AccountFailure(AccountProblem.RESPONSE)
        return VerifiedSession(email, ApiCredential.parse(refresh), access)
    }

    private class Once(private val delegate: RequestBody) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
        override fun isOneShot() = true
    }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val CODE_ERRORS = setOf("otp_expired", "invalid_credentials", "validation_failed")
        private val SESSION_ERRORS = setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired", "user_not_found")
        private val SETUP_ERRORS = setOf("email_provider_disabled", "signup_disabled", "email_address_not_authorized", "captcha_failed", "otp_disabled")
    }
}
