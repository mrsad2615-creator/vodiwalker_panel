package ai.dana.data.network

import ai.dana.data.network.EngineProtocol.string
import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountProblem
import ai.dana.domain.ArtImage
import ai.dana.domain.ArtRequest
import ai.dana.domain.ArtShape
import ai.dana.domain.ConversationInput
import ai.dana.domain.DurationTarget
import ai.dana.domain.LocalAction
import ai.dana.domain.ManagedCatalog
import ai.dana.domain.ManagedCloud
import ai.dana.domain.ManagedFailure
import ai.dana.domain.ManagedKind
import ai.dana.domain.ManagedModel
import ai.dana.domain.ManagedProblem
import ai.dana.domain.MessageRole
import ai.dana.domain.PhoneIntent
import ai.dana.domain.ReviewMessage
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal interface ManagedAuthorization {
    suspend fun <T> withAccess(operation: suspend (String) -> T): T
}

/** Authorization from the fixed issuer goes only to the compiled gateway origin, never to providers. */
internal class OkHttpManagedCloud(
    private val origin: HttpUrl?,
    private val auth: ManagedAuthorization,
    client: OkHttpClient,
) : ManagedCloud {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    override val configured: Boolean = origin?.let {
        it.isHttps && it.encodedPath == "/" && it.query == null && it.fragment == null && it.username.isEmpty() && it.password.isEmpty()
    } == true

    override suspend fun catalog(): ManagedCatalog = call("catalog") { response ->
        val root = response.readObject(24_576)
        val items = (root["models"] as? JsonArray)?.takeIf { it.size in 1..33 } ?: invalid()
        val models = items.map { entry ->
            val model = entry as? JsonObject ?: invalid()
            val id = model["id"].string()?.takeIf(MODEL_ID::matches) ?: invalid()
            val kind = when (model["kind"].string()) { "chat" -> ManagedKind.CHAT; "image" -> ManagedKind.IMAGE; else -> invalid() }
            val units = model.int("units", 1..50)
            val quality = model["quality"].string()?.let { validLabel(it, 12) }
            if (kind == ManagedKind.IMAGE && quality == null) invalid()
            ManagedModel(id, validLabel(model["title"].string()), validLabel(model["provider"].string()), kind, units, quality)
        }
        if (models.map { it.id }.toSet().size != models.size) invalid()
        val planner = root["planner"].string()?.let { id -> models.singleOrNull { it.id == id && it.kind == ManagedKind.CHAT } ?: invalid() }
        ManagedCatalog(models, planner, root.int("daily_remaining", 0..50_000))
    }

    override suspend fun chat(model: ManagedModel, input: ConversationInput, requestId: String): String {
        if (model.kind != ManagedKind.CHAT || !ManagedCloud.acceptsChat(input)) invalid()
        val payload = buildJsonObject {
            put("model", model.id)
            put("prompt", input.prompt)
            putJsonArray("history") {
                for (turn in input.history) add(buildJsonObject {
                    put("role", if (turn.role == MessageRole.USER) "user" else "assistant")
                    put("content", turn.body)
                })
            }
        }
        return call("chat", payload, requestId) { response ->
            response.readObject(96_000)["text"].string()?.takeIf { ManagedCloud.acceptsText(it, ManagedCloud.ANSWER_LIMIT) } ?: invalid()
        }
    }

    override suspend fun image(model: ManagedModel, prompt: String, shape: ArtShape, requestId: String): ArtImage {
        if (model.kind != ManagedKind.IMAGE || !ManagedCloud.acceptsText(prompt, ArtRequest.PROMPT_LIMIT)) invalid()
        val payload = buildJsonObject { put("model", model.id); put("prompt", prompt); put("shape", shape.name.lowercase(Locale.ROOT)) }
        return call("image", payload, requestId) { response ->
            val mime = response.body.contentType()?.let { "${it.type}/${it.subtype}" } ?: invalid()
            if (mime !in setOf("image/png", "image/jpeg") || response.body.contentLength() > ArtImage.BYTE_LIMIT) invalid()
            val source = response.body.source()
            if (source.request(ArtImage.BYTE_LIMIT + 1L)) invalid()
            ArtImage.create(mime, source.readByteArray()) ?: invalid()
        }
    }

    override suspend fun command(model: ManagedModel, prompt: String, requestId: String): PhoneIntent {
        if (model.kind != ManagedKind.CHAT || !ManagedCloud.acceptsText(prompt, ManagedCloud.COMMAND_LIMIT)) invalid()
        return call("command", buildJsonObject { put("prompt", prompt); put("model", model.id) }, requestId) { parseManagedPlan(it.readObject(2_048)) }
    }

    private suspend fun <T> call(path: String, data: JsonObject? = null, requestId: String = "", decode: (Response) -> T): T {
        val base = origin?.takeIf { configured } ?: throw ManagedFailure(ManagedProblem.NOT_ENABLED)
        try {
            return auth.withAccess { token ->
                val url = base.newBuilder().addPathSegment("v1").addPathSegment(path).build()
                val request = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Cache-Control", "no-store")
                    .header("Accept", if (path == "image") "image/png, image/jpeg" else "application/json")
                if (data != null) {
                    val uuid = runCatching { UUID.fromString(requestId) }.getOrNull()
                    if (uuid?.version() != 4 || uuid.toString() != requestId) invalid()
                    request.header("Idempotency-Key", requestId).post(EngineProtocol.singleAttemptJson(data))
                }
                exchange(request.build(), decode)
            }
        } catch (problem: AccountFailure) {
            throw ManagedFailure(when (problem.problem) {
                AccountProblem.OFFLINE -> ManagedProblem.OFFLINE
                AccountProblem.EXPIRED_SESSION -> ManagedProblem.SIGN_IN
                AccountProblem.SETUP -> ManagedProblem.NOT_ENABLED
                AccountProblem.RATE_LIMIT -> ManagedProblem.BUSY
                else -> ManagedProblem.TRANSPORT
            })
        }
    }

    private suspend fun <T> exchange(request: Request, decode: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(ManagedFailure(ManagedProblem.TRANSPORT)))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching {
                    response.use {
                        if (!it.isSuccessful) throw ManagedFailure(problem(it))
                        decode(it)
                    }
                }.recoverCatching { error ->
                    throw when (error) {
                        is ManagedFailure -> error
                        is IOException -> ManagedFailure(ManagedProblem.TRANSPORT)
                        else -> ManagedFailure(ManagedProblem.RESPONSE)
                    }
                })
            }
        })
    }

    private fun problem(response: Response): ManagedProblem = when (response.code) {
        401 -> ManagedProblem.SIGN_IN
        403 -> ManagedProblem.NOT_ENABLED
        404 -> ManagedProblem.MODEL_UNAVAILABLE
        409 -> ManagedProblem.DUPLICATE
        429 -> if (runCatching { response.readObject(1_024)["error"].string() }.getOrNull() == "quota") ManagedProblem.QUOTA else ManagedProblem.BUSY
        in 300..499, 502 -> ManagedProblem.RESPONSE
        else -> ManagedProblem.TRANSPORT
    }

    private fun Response.readObject(limit: Long): JsonObject {
        if (body.contentType()?.subtype != "json") invalid()
        return EngineProtocol.decodeObject(body.boundedUtf8(limit))
    }

    private fun validLabel(text: String?, max: Int = 72) = text?.takeIf { it.length in 1..max && it.isNotBlank() && !it.any(Char::isISOControl) && ReviewMessage.acceptsDraft(it) } ?: invalid()

    private companion object { val MODEL_ID = Regex("[a-z0-9][a-z0-9_./:-]{0,159}") }
}

private fun invalid(): Nothing = throw ManagedFailure(ManagedProblem.RESPONSE)
private fun JsonObject.int(key: String, allowed: IntRange) = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }
    ?.intOrNull?.takeIf { it in allowed } ?: invalid()
private fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: invalid()

/** Validate again on the handset. A server response cannot supply an Intent, URI, component or arbitrary program. */
internal fun parseManagedPlan(root: JsonObject): PhoneIntent {
    val action = root["action"].string() ?: invalid()
    fun fields(vararg names: String) { if (root.keys != setOf("action", *names)) invalid() }
    fun seconds() = root.int("seconds", 1..86_400)
    val value = when (action) {
        "timer" -> { fields("seconds"); LocalAction.Timer(seconds()) }
        "alarm" -> { fields("hour", "minute"); LocalAction.Alarm(root.int("hour", 0..23), root.int("minute", 0..59)) }
        "wifi" -> { fields("on"); LocalAction.WifiChange(root.flag("on")) }
        "wifi_reminder" -> { fields("seconds", "on"); LocalAction.WifiReminder(seconds(), root.flag("on")) }
        "volume" -> { fields("percent"); LocalAction.SetVolume(root.int("percent", 0..100)) }
        "torch" -> { fields("on"); LocalAction.Torch(root.flag("on")) }
        "battery" -> { fields(); LocalAction.Battery }
        "need_duration" -> {
            fields("target")
            return PhoneIntent.NeedDuration(when (root["target"].string()) {
                "timer" -> DurationTarget.TIMER
                "wifi_off" -> DurationTarget.WIFI_OFF
                "wifi_on" -> DurationTarget.WIFI_ON
                else -> invalid()
            })
        }
        "unknown" -> { fields(); return PhoneIntent.Unknown }
        else -> invalid()
    }
    return PhoneIntent.Propose(value)
}
