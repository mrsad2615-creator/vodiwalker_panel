package ai.dana.data.network

import ai.dana.domain.ApiCredential
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.BufferedSource

internal object EngineProtocol {
    private const val SYSTEM = "تو دانا، دستیار فارسی‌زبان دقیق و محترم هستی. به فارسی روشن پاسخ بده؛ اگر مطمئن نیستی، صریح بگو."
    private const val MAX_TOKENS = 1_024
    private const val MAX_SSE_LINE_BYTES = 128_000L
    private const val MAX_SSE_EVENT_CHARS = 128_000
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val geminiId = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}")

    fun request(config: ProviderConfig, key: ApiCredential?, prompt: String): Request {
        val base = config.baseUrl.toHttpUrlOrNull()?.takeIf { it.isHttps }
            ?: throw EngineFailure(EngineProblem.CONFIGURATION)
        val url = base.newBuilder()
        val headers = Request.Builder().header("Accept", "text/event-stream")
        val body = when (config.protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> {
                url.addPathSegments("chat/completions")
                key?.let { headers.header("Authorization", "Bearer ${it.value}") }
                buildJsonObject {
                    put("model", config.model)
                    put("stream", true)
                    put("max_tokens", MAX_TOKENS)
                    putJsonArray("messages") {
                        add(message("system", SYSTEM))
                        add(message("user", prompt))
                    }
                }
            }
            ProviderProtocol.GEMINI_NATIVE -> {
                val id = config.model.removePrefix("models/")
                if (!geminiId.matches(id)) throw EngineFailure(EngineProblem.CONFIGURATION)
                if (key == null) throw EngineFailure(EngineProblem.AUTHENTICATION)
                url.addPathSegment("models").addPathSegment("$id:streamGenerateContent").addQueryParameter("alt", "sse")
                headers.header("x-goog-api-key", key.value)
                buildJsonObject {
                    put("systemInstruction", geminiContent(SYSTEM))
                    putJsonArray("contents") { add(geminiContent(prompt, role = "user")) }
                    putJsonObject("generationConfig") { put("maxOutputTokens", MAX_TOKENS) }
                }
            }
        }
        return headers.url(url.build()).post(SingleAttemptBody(body.toString().toRequestBody(jsonType))).build()
    }

    /** Returns only on an explicit terminal frame or cancellation of the downstream consumer. */
    fun read(source: BufferedSource, protocol: ProviderProtocol, onText: (String) -> Boolean) {
        val data = StringBuilder()
        var firstLine = true
        while (!source.exhausted()) {
            val raw = source.readUtf8LineStrict(MAX_SSE_LINE_BYTES)
            val line = if (firstLine) raw.removePrefix("\uFEFF") else raw
            firstLine = false
            if (line.isEmpty()) {
                if (data.isEmpty()) continue
                val frame = decode(data.substring(0, data.length - 1), protocol)
                data.setLength(0)
                if (frame.text.isNotEmpty() && !onText(frame.text)) return
                if (frame.filtered) throw EngineFailure(EngineProblem.FILTERED)
                if (frame.finished) return
            } else if (line == "data" || line.startsWith("data:")) {
                val value = if (line == "data") "" else line.substring(5).removePrefix(" ")
                if (value.length >= MAX_SSE_EVENT_CHARS - data.length) throw EngineFailure(EngineProblem.PROTOCOL)
                data.append(value).append('\n')
            }
        }
        throw EngineFailure(EngineProblem.PROTOCOL)
    }

    private fun decode(data: String, protocol: ProviderProtocol): Frame {
        if (data.isBlank()) return Frame()
        if (protocol == ProviderProtocol.OPENAI_COMPATIBLE && data.trim() == "[DONE]") return Frame(finished = true)
        checkNesting(data)
        val root = try {
            Json.parseToJsonElement(data) as? JsonObject ?: throw EngineFailure(EngineProblem.PROTOCOL)
        } catch (_: IllegalArgumentException) {
            throw EngineFailure(EngineProblem.PROTOCOL)
        }
        if (root["error"] is JsonObject) throw EngineFailure(EngineProblem.SERVER)
        return when (protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> {
                val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                val finish = choice?.get("finish_reason").string()
                Frame(
                    text = ((choice?.get("delta") as? JsonObject)?.get("content")).string().orEmpty(),
                    finished = finish != null,
                    filtered = finish == "content_filter",
                )
            }
            ProviderProtocol.GEMINI_NATIVE -> {
                val block = ((root["promptFeedback"] as? JsonObject)?.get("blockReason")).string()
                val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
                val content = candidate?.get("content") as? JsonObject
                val text = (content?.get("parts") as? JsonArray).orEmpty().mapNotNull { part ->
                    val obj = part as? JsonObject ?: return@mapNotNull null
                    if ((obj["thought"] as? JsonPrimitive)?.content == "true") null else obj["text"].string()
                }.joinToString("")
                val reason = candidate?.get("finishReason").string()
                Frame(
                    text = text,
                    finished = reason == "STOP" || reason == "MAX_TOKENS",
                    filtered = (block != null && block != "BLOCK_REASON_UNSPECIFIED") ||
                        (reason != null && reason !in setOf("STOP", "MAX_TOKENS", "FINISH_REASON_UNSPECIFIED")),
                )
            }
        }
    }

    // A remote endpoint cannot force unbounded JSON parser recursion with a deeply nested frame.
    private fun checkNesting(data: String) {
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in data) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{', '[' -> if (++depth > 40) throw EngineFailure(EngineProblem.PROTOCOL)
                    '}', ']' -> if (--depth < 0) throw EngineFailure(EngineProblem.PROTOCOL)
                }
            }
        }
    }

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun message(role: String, text: String) = buildJsonObject {
        put("role", role)
        put("content", text)
    }

    private fun geminiContent(text: String, role: String? = null) = buildJsonObject {
        role?.let { put("role", it) }
        putJsonArray("parts") { add(buildJsonObject { put("text", text) }) }
    }

    private class SingleAttemptBody(private val inner: RequestBody) : RequestBody() {
        override fun contentType() = inner.contentType()
        override fun contentLength() = inner.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = inner.writeTo(sink)
    }

    private data class Frame(val text: String = "", val finished: Boolean = false, val filtered: Boolean = false)
}
