package ai.dana.data.network

import ai.dana.domain.ApiCredential
import ai.dana.domain.AssistantTask
import ai.dana.domain.EngineText
import ai.dana.domain.ProviderEndpoint
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
import okhttp3.HttpUrl
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
    internal val geminiId = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}")

    fun url(endpoint: ProviderEndpoint): HttpUrl.Builder = endpoint.baseUrl.toHttpUrlOrNull()?.takeIf { it.isHttps }?.newBuilder()
        ?: throw EngineFailure(EngineProblem.CONFIGURATION)

    fun authenticate(request: Request.Builder, protocol: ProviderProtocol, key: ApiCredential?) {
        when (protocol) {
            ProviderProtocol.GEMINI_NATIVE -> request.header("x-goog-api-key", required(key).value)
            ProviderProtocol.APPLE_MAC -> request.header("Authorization", "Bearer ${required(key).value}")
            ProviderProtocol.OPENAI_COMPATIBLE -> if (key != null) request.header("Authorization", "Bearer ${key.value}")
        }
    }

    private fun required(key: ApiCredential?): ApiCredential = key ?: throw EngineFailure(EngineProblem.AUTHENTICATION)

    fun request(config: ProviderConfig, key: ApiCredential?, prompt: String, task: AssistantTask): Request {
        val url = url(config.endpoint)
        val headers = Request.Builder().header("Accept", "text/event-stream")
        authenticate(headers, config.protocol, key)
        val instructions = instructions(task, config.protocol == ProviderProtocol.APPLE_MAC)
        val body = when (config.protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> {
                url.addPathSegments("chat/completions")
                buildJsonObject {
                    put("model", config.model)
                    put("stream", true)
                    put("max_tokens", MAX_TOKENS)
                    putJsonArray("messages") {
                        add(message("system", instructions))
                        add(message("user", prompt))
                    }
                }
            }
            ProviderProtocol.GEMINI_NATIVE -> {
                val id = config.model.removePrefix("models/")
                if (!geminiId.matches(id)) throw EngineFailure(EngineProblem.CONFIGURATION)
                url.addPathSegment("models").addPathSegment("$id:streamGenerateContent").addQueryParameter("alt", "sse")
                buildJsonObject {
                    put("systemInstruction", geminiContent(instructions))
                    putJsonArray("contents") { add(geminiContent(prompt, role = "user")) }
                    putJsonObject("generationConfig") { put("maxOutputTokens", MAX_TOKENS) }
                }
            }
            ProviderProtocol.APPLE_MAC -> {
                url.addPathSegment("respond")
                buildJsonObject {
                    put("model", config.model)
                    put("instructions", instructions)
                    put("prompt", prompt)
                    put("max_output_tokens", MAX_TOKENS)
                }
            }
        }
        return headers.url(url.build()).post(SingleAttemptBody(body.toString().toRequestBody(jsonType))).build()
    }

    private fun instructions(task: AssistantTask, apple: Boolean): String {
        if (apple) return "You are Dana, a concise and careful assistant. Answer in English. " + when (task) {
            AssistantTask.ASK -> "If unsure, say so."
            AssistantTask.SUMMARIZE -> "Summarize the input and preserve its key facts."
            AssistantTask.TRANSLATE -> "Translate the input into English, preserving meaning and tone."
            AssistantTask.DRAFT -> "Write a polished draft based on the user's request."
        }
        return SYSTEM + when (task) {
            AssistantTask.ASK -> ""
            AssistantTask.SUMMARIZE -> " متن ورودی را خلاصه کن و نکات و داده‌های مهم را حفظ کن."
            AssistantTask.TRANSLATE -> " متن را به فارسی روان ترجمه کن؛ معنا و لحن را حفظ کن."
            AssistantTask.DRAFT -> " بر اساس توضیح کاربر یک پیش‌نویس روشن بنویس؛ داده‌ای از خودت اضافه نکن."
        }
    }

    /** Returns only on an explicit terminal frame or cancellation of the downstream consumer. */
    fun read(source: BufferedSource, protocol: ProviderProtocol, onText: (EngineText) -> Boolean) {
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
                if ((frame.text.replaces || frame.text.value.isNotEmpty()) && !onText(frame.text)) return
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
        val root = decodeObject(data)
        (root["error"] as? JsonObject)?.let { error ->
            throw EngineFailure(if (protocol == ProviderProtocol.APPLE_MAC) appleProblem(error["code"].string()) else EngineProblem.SERVER)
        }
        return when (protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> {
                val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                val finish = choice?.get("finish_reason").string()
                Frame(
                    text = EngineText(((choice?.get("delta") as? JsonObject)?.get("content")).string().orEmpty()),
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
                    text = EngineText(text),
                    finished = reason == "STOP" || reason == "MAX_TOKENS",
                    filtered = (block != null && block != "BLOCK_REASON_UNSPECIFIED") ||
                        (reason != null && reason !in setOf("STOP", "MAX_TOKENS", "FINISH_REASON_UNSPECIFIED")),
                )
            }
            ProviderProtocol.APPLE_MAC -> {
                if (root["done"] == JsonPrimitive(true)) return Frame(finished = true)
                val snapshot = root["snapshot"].string() ?: throw EngineFailure(EngineProblem.PROTOCOL)
                Frame(text = EngineText(snapshot, replaces = true))
            }
        }
    }

    fun appleProblem(code: String?): EngineProblem = when (code) {
        "apple_unavailable" -> EngineProblem.ENGINE_UNAVAILABLE
        "unsupported_language" -> EngineProblem.UNSUPPORTED_LANGUAGE
        "context_length_exceeded" -> EngineProblem.CONTEXT_LIMIT
        "apple_busy" -> EngineProblem.RATE_LIMIT
        "apple_refusal" -> EngineProblem.FILTERED
        "output_limit" -> EngineProblem.OUTPUT_LIMIT
        "timeout" -> EngineProblem.TIMEOUT
        else -> EngineProblem.SERVER
    }

    fun decodeObject(data: String): JsonObject {
        checkNesting(data)
        return try {
            Json.parseToJsonElement(data) as? JsonObject ?: throw EngineFailure(EngineProblem.PROTOCOL)
        } catch (_: IllegalArgumentException) {
            throw EngineFailure(EngineProblem.PROTOCOL)
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

    internal fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

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

    private data class Frame(val text: EngineText = EngineText(""), val finished: Boolean = false, val filtered: Boolean = false)
}
