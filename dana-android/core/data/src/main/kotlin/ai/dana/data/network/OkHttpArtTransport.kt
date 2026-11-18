package ai.dana.data.network

import ai.dana.data.network.EngineProtocol.string
import ai.dana.domain.ApiCredential
import ai.dana.domain.ArtImage
import ai.dana.domain.ArtRequest
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.ImageTransport
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.RouterCode
import ai.dana.domain.RouterKeyExchange
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class OkHttpArtTransport(client: OkHttpClient) : ImageTransport {
    private val imageClient = client.newBuilder().callTimeout(165, TimeUnit.SECONDS).readTimeout(150, TimeUnit.SECONDS).build()

    override suspend fun generate(request: ArtRequest, key: ApiCredential): ArtImage {
        val endpoint = request.model.service.endpoint
        if (!request.model.matches(request.profile)) throw EngineFailure(EngineProblem.CONFIGURATION)
        val gemini = endpoint.protocol == ProviderProtocol.GEMINI_NATIVE
        val url = EngineProtocol.url(endpoint)
        val payload = if (gemini) {
            url.addPathSegments("models").addPathSegment("${request.model.id}:generateContent")
            buildJsonObject {
                putJsonArray("contents") { add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", request.prompt) }) }
                }) }
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add(JsonPrimitive("TEXT")); add(JsonPrimitive("IMAGE")) }
                    putJsonObject("imageConfig") {
                        put("aspectRatio", request.shape.ratio)
                        put("imageSize", "1K")
                    }
                }
            }
        } else {
            url.addPathSegments("images/generations")
            buildJsonObject {
                put("model", request.model.id)
                put("prompt", request.prompt)
                put("size", request.shape.openAiSize)
                put("quality", "low")
                put("output_format", "png")
                put("n", 1)
            }
        }
        val builder = Request.Builder().url(url.build()).header("Accept", "application/json")
        EngineProtocol.authenticate(builder, endpoint, key)
        return imageClient.json(builder.post(EngineProtocol.singleAttemptJson(payload)).build(), endpoint.protocol, MAX_JSON_BYTES) {
            if (gemini) extractGemini(it) else {
                if (it["error"] is JsonObject) throw EngineFailure(EngineProblem.PROTOCOL)
                val image = (it["data"] as? JsonArray)?.firstOrNull() as? JsonObject
                decode("image/png", image?.get("b64_json").string())
            }
        }
    }

    private fun extractGemini(root: JsonObject): ArtImage {
        val feedback = (root["promptFeedback"] as? JsonObject)?.get("blockReason").string()
        if (feedback != null && feedback != "BLOCK_REASON_UNSPECIFIED") throw EngineFailure(EngineProblem.FILTERED)
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        when (candidate?.get("finishReason").string()) {
            null, "STOP", "FINISH_REASON_UNSPECIFIED" -> Unit
            "MAX_TOKENS" -> throw EngineFailure(EngineProblem.OUTPUT_LIMIT)
            "NO_IMAGE" -> throw EngineFailure(EngineProblem.NO_IMAGE)
            else -> throw EngineFailure(EngineProblem.FILTERED)
        }
        val content = candidate?.get("content") as? JsonObject
        val parts = (content?.get("parts") as? JsonArray).orEmpty()
        val inline = parts.asSequence().mapNotNull { it as? JsonObject }
            .filterNot { it["thought"] == JsonPrimitive(true) }
            .mapNotNull { (it["inlineData"] ?: it["inline_data"]) as? JsonObject }.firstOrNull()
            ?: throw EngineFailure(EngineProblem.NO_IMAGE)
        val mime = (inline["mimeType"] ?: inline["mime_type"]).string() ?: throw EngineFailure(EngineProblem.PROTOCOL)
        return decode(mime, inline["data"].string())
    }

    private fun decode(mime: String, encoded: String?): ArtImage {
        if (encoded.isNullOrEmpty()) throw EngineFailure(EngineProblem.NO_IMAGE)
        if (encoded.length > MAX_BASE64 || mime !in setOf("image/png", "image/jpeg")) throw EngineFailure(EngineProblem.PROTOCOL)
        val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) {
            throw EngineFailure(EngineProblem.PROTOCOL)
        }
        return ArtImage.create(mime, bytes) ?: throw EngineFailure(EngineProblem.PROTOCOL)
    }

    private companion object {
        const val MAX_BASE64 = 4 * ((ArtImage.BYTE_LIMIT + 2) / 3)
        const val MAX_JSON_BYTES = MAX_BASE64 + 256_000L
    }
}

internal class OkHttpRouterKeyExchange(private val client: OkHttpClient) : RouterKeyExchange {
    override suspend fun exchange(code: RouterCode): ApiCredential {
        val payload = buildJsonObject {
            put("code", code.code)
            put("code_verifier", code.verifier)
            put("code_challenge_method", "S256")
        }
        val request = Request.Builder().url("https://openrouter.ai/api/v1/auth/keys").header("Accept", "application/json")
            .post(EngineProtocol.singleAttemptJson(payload)).build()
        return client.json(request, ProviderProtocol.OPENROUTER_FREE, 16_384) { root ->
            val key = root["key"].string()?.takeIf { it.startsWith("sk-or-v1-") } ?: throw EngineFailure(EngineProblem.AUTHENTICATION)
            ApiCredential.parse(key)
        }
    }
}

/** Cancel the socket (including response-body read) when the foreground operation stops. */
internal suspend fun <T> OkHttpClient.json(request: Request, protocol: ProviderProtocol, limit: Long, parse: (JsonObject) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWith(Result.failure(e.toEngineFailure()))

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        it.requireSuccess(protocol)
                        if (it.body.contentType()?.subtype != "json") throw EngineFailure(EngineProblem.PROTOCOL)
                        Result.success(parse(EngineProtocol.decodeObject(it.body.boundedUtf8(limit))))
                    }
                } catch (failure: EngineFailure) {
                    Result.failure(failure)
                } catch (failure: IOException) {
                    Result.failure(failure.toEngineFailure())
                } catch (_: Exception) {
                    Result.failure(EngineFailure(EngineProblem.PROTOCOL))
                }
                continuation.resumeWith(result)
            }
        })
    }
