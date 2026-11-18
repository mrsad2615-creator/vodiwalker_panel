package ai.dana.data.network

import ai.dana.domain.ApiCredential
import ai.dana.domain.AssistantStyle
import ai.dana.domain.AssistantTask
import ai.dana.domain.CloudEngine
import ai.dana.domain.ConversationInput
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.PhotoSupport
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.VisionPhoto
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEngineContractTest {
    private val token = ApiCredential.parse("cloud-test-only-key")
    private val format = ProviderProtocol.OPENAI_COMPATIBLE
    private fun profile(provider: CloudEngine, model: String = provider.suggested.first()) =
        ProviderConfig.create(provider.label, format, ProviderNetwork.INTERNET, provider.baseUrl, model)

    @Test
    fun usesOfficialPathsPersonalKeyAndProviderSpecificPhotoAndTokenFields() {
        val jpeg = checkNotNull(VisionPhoto.fromJpeg(byteArrayOf(-1, -40, 1, -1, -39)))
        val input = checkNotNull(ConversationInput.prepare("این تصویر چیست؟", emptyList(), emptyList(),
            AssistantStyle.BALANCED, AssistantTask.ASK, jpeg, mask = false))
        val urls = mapOf(CloudEngine.AVALAI to "https://api.avalai.ir/v1/chat/completions", CloudEngine.OPENAI to "https://api.openai.com/v1/chat/completions",
            CloudEngine.GROQ to "https://api.groq.com/openai/v1/chat/completions",
            CloudEngine.MISTRAL to "https://api.mistral.ai/v1/chat/completions",
            CloudEngine.DEEPSEEK to "https://api.deepseek.com/chat/completions",
            CloudEngine.CEREBRAS to "https://api.cerebras.ai/v1/chat/completions")
        for (cloud in CloudEngine.entries) {
            val config = profile(cloud)
            assertEquals(EngineProblem.AUTHENTICATION, assertThrows(EngineFailure::class.java) {
                EngineProtocol.request(config, null, input)
            }.problem)
            val request = EngineProtocol.request(config, token, input)
            assertEquals(urls[cloud], request.url.toString())
            assertEquals("POST", request.method)
            assertEquals("Bearer ${token.value}", request.header("Authorization"))
            assertEquals("text/event-stream, application/json", request.header("Accept"))
            val output = Buffer()
            val body = requireNotNull(request.body)
            assertTrue(body.isOneShot())
            body.writeTo(output)
            val json = output.readUtf8()
            assertTrue(json.contains("\"stream\":true"))
            assertTrue(json.contains("\"${cloud.completionLimitField(config.model)}\":1024"))
            assertFalse(json.contains("tool_choice"))
            assertFalse(json.contains(token.value))
            assertTrue(json.contains("این تصویر چیست؟"))
            assertTrue(json.contains("data:image/jpeg;base64,${jpeg.encodeBase64()}"))
            if (cloud == CloudEngine.MISTRAL) assertTrue(json.contains("\"image_url\":\"data:"))
            if (cloud == CloudEngine.DEEPSEEK) assertTrue(json.contains("\"thinking\":{\"type\":\"disabled\"}"))
        }
    }

    @Test
    fun directoryIsPromptFreeAndIgnoresNonChatModels() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            calls += req.url.toString()
            check(req.method == "GET" && req.body == null && req.header("Authorization") == "Bearer ${token.value}")
            val catalog = if (req.url.host == "api.groq.com") """{"data":[
                {"id":"groq/compound-mini"},{"id":"whisper-large-v3"},{"id":"retired","active":false},
                {"id":"qwen/qwen3.8-27b","active":true}]}
            """ else """{"data":[{"id":"fim-only","capabilities":{"completion_chat":false}},
                {"id":"mistral-small-latest","capabilities":{"completion_chat":true,"vision":true}}]}
            """
            Response.Builder().request(req).code(200).protocol(Protocol.HTTP_1_1).message("Fixture")
                .body(catalog.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val directory = OkHttpModelDirectory(client)
        for (cloud in listOf(CloudEngine.GROQ, CloudEngine.MISTRAL)) {
            val models = directory.list(profile(cloud).endpoint, token)
            assertEquals(cloud.suggested.first(), models.single().id)
            assertEquals(PhotoSupport.YES, models.single().photoSupport)
        }
        assertEquals(listOf("https://api.groq.com/openai/v1/models", "https://api.mistral.ai/v1/models"), calls)
    }

    @Test
    fun streamOnlyShowsVisibleTextAndDoesNotMarkTruncationComplete() {
        val source = Buffer().writeUtf8("""
            data: {"choices":[{"delta":{"content":[{"type":"thinking","text":"hidden"},{"type":"text","text":"پاسخ ناقص"}]}}]}

            data: {"choices":[{"delta":{},"finish_reason":"length"}]}
        """.trimIndent() + "\n\n")
        val chunks = mutableListOf<String>()
        assertEquals(EngineProblem.OUTPUT_LIMIT, assertThrows(EngineFailure::class.java) {
            EngineProtocol.read(source, format) { chunks.add(it.value) }
        }.problem)
        assertEquals(listOf("پاسخ ناقص"), chunks)
    }
}
