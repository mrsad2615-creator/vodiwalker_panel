package ai.dana.data.network

import ai.dana.domain.ApiCredential
import ai.dana.domain.ArtModel
import ai.dana.domain.ArtRequest
import ai.dana.domain.ArtShape
import ai.dana.domain.ConversationInput
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.MessageReviewPlan
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProfile
import ai.dana.domain.QuickService
import ai.dana.domain.ReviewMessage
import ai.dana.domain.RouterApproval
import ai.dana.domain.RouterCode
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtAndSetupContractTest {
    private val png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aLSQAAAAASUVORK5CYII="
    private val key = ApiCredential.parse("ci-test-key-only")
    private fun config(engine: QuickService) = ProviderConfig.create(engine.title, engine.protocol, ProviderNetwork.INTERNET, engine.api, engine.models.first())
    private fun request(model: ArtModel) = checkNotNull(ArtRequest.create(ProviderProfile("fixture", config(model.service), true), model,
        ArtShape.LANDSCAPE, "نمونهٔ تصویر"))
    private fun client(code: Int = 200, response: String, check: (Request) -> Unit = {}) =
        danaHttpClient().newBuilder().addInterceptor { chain ->
            val request = chain.request()
            check(request)
            Response.Builder().request(request).code(code).message("Fixture").protocol(Protocol.HTTP_1_1)
                .body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
    private fun body(request: Request): String = Buffer().also { buffer ->
        val data = checkNotNull(request.body)
        assertTrue(data.isOneShot())
        data.writeTo(buffer)
    }.readUtf8()

    @Test fun googleGenerateContentReadsFinalInlineImageOnly() = runBlocking {
        val response = """{"candidates":[{"content":{"parts":[{"text":"caption"},{"thought":true,
          "inlineData":{"mimeType":"image/png","data":"NOT_FINAL"}},{"inlineData":{"mimeType":"image/png","data":"$png"}}]},"finishReason":"STOP"}]}"""
        val transport = OkHttpArtTransport(client(response = response) { request ->
            assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-image:generateContent", request.url.toString())
            assertEquals(key.value, request.header("x-goog-api-key"))
            assertNull(request.header("Authorization"))
            val json = body(request)
            assertTrue("\"responseModalities\":[\"TEXT\",\"IMAGE\"]" in json)
            assertTrue("\"aspectRatio\":\"3:2\"" in json)
            assertTrue("\"imageSize\":\"1K\"" in json)
            assertTrue("نمونهٔ تصویر" in json)
            assertFalse(key.value in json)
        })
        assertArrayEquals(Base64.getDecoder().decode(png), transport.generate(request(ArtModel.NANO_LITE), key).data())
    }

    @Test fun imageOnlySetupFindsExactModelWithoutExposingItInTextCatalog() = runBlocking {
        val service = QuickService.AVALAI
        val response = """{"data":[{"id":"gpt-5.4-nano"},{"id":"gpt-image-2.6-flare"},{"id":"gpt-image-2.6-sunburst"}]}"""
        val http = client(response = response) { request ->
            assertEquals("${service.api}/models", request.url.toString())
            assertEquals("GET", request.method)
            assertEquals("Bearer ${key.value}", request.header("Authorization"))
        }
        val engines = EngineClients(http)
        assertEquals(listOf("gpt-5.4-nano"), engines.catalog.list(service.endpoint, key).map { it.id })
        assertEquals(ArtModel.AVAL_STUDIO.id, engines.setup.test(service, key.value, imageModel = ArtModel.AVAL_STUDIO).config.model)
    }

    @Test fun avalaiUsesTheSameCredentialForTheDocumentedImageRoute() = runBlocking {
        val transport = OkHttpArtTransport(client(response = """{"data":[{"b64_json":"$png"}]}""") { request ->
            assertEquals("https://api.avalai.ir/v1/images/generations", request.url.toString())
            assertEquals("Bearer ${key.value}", request.header("Authorization"))
            val json = body(request)
            assertTrue("\"model\":\"gpt-image-2.6-flare\"" in json)
            assertTrue("\"quality\":\"low\"" in json)
            assertTrue("\"size\":\"1536x1024\"" in json)
            assertTrue("\"n\":1" in json)
        })
        assertArrayEquals(Base64.getDecoder().decode(png), transport.generate(request(ArtModel.AVAL_IMAGE), key).data())
    }

    @Test fun openAiUsesImageEndpointAndOneExplicitImageWithoutChatHistoryOrUrlFetch() = runBlocking {
        val transport = OkHttpArtTransport(client(response = """{"data":[{"b64_json":"$png","url":"https://not-fetched.test"}]}""") { request ->
            assertEquals("https://api.openai.com/v1/images/generations", request.url.toString())
            assertEquals("Bearer ${key.value}", request.header("Authorization"))
            val json = body(request)
            assertTrue("\"model\":\"gpt-image-2.5-flare\"" in json)
            assertTrue("\"size\":\"1536x1024\"" in json)
            assertTrue("\"n\":1" in json)
            assertFalse(listOf("messages", "response_format", key.value).any { it in json })
        })
        assertEquals("image/png", transport.generate(request(ArtModel.GPT_IMAGE), key).mime)
    }

    @Test fun studioSunburstHasOneHighQualityPngOnTheExplicitProviderOnly() = runBlocking {
        for (model in listOf(ArtModel.AVAL_STUDIO, ArtModel.GPT_STUDIO)) {
            var count = 0
            val transport = OkHttpArtTransport(client(response = """{"data":[{"b64_json":"$png"}]}""") { request ->
                count++
                assertEquals("${model.service.api}/images/generations", request.url.toString())
                assertEquals("Bearer ${key.value}", request.header("Authorization"))
                val json = Json.parseToJsonElement(body(request)).jsonObject
                assertEquals(setOf("model", "prompt", "size", "quality", "output_format", "n"), json.keys)
                assertEquals("gpt-image-2.6-sunburst", json.getValue("model").jsonPrimitive.content)
                assertEquals("high", json.getValue("quality").jsonPrimitive.content)
                assertEquals("png", json.getValue("output_format").jsonPrimitive.content)
                assertEquals("1", json.getValue("n").jsonPrimitive.content)
            })
            assertArrayEquals(Base64.getDecoder().decode(png), transport.generate(request(model), key).data())
            assertEquals(1, count)
        }
    }

    @Test fun bananaProUsesTheExactNativeStudioResolutionAndRatio() = runBlocking {
        val response = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"$png"}}]},"finishReason":"STOP"}]}"""
        val transport = OkHttpArtTransport(client(response = response) { call ->
            assertEquals("${QuickService.GOOGLE.api}/models/gemini-3-pro-image:generateContent", call.url.toString())
            assertEquals(key.value, call.header("x-goog-api-key"))
            val json = Json.parseToJsonElement(body(call)).jsonObject
            assertEquals(setOf("contents", "generationConfig"), json.keys)
            val image = json.getValue("generationConfig").jsonObject.getValue("imageConfig").jsonObject
            assertEquals("2K", image.getValue("imageSize").jsonPrimitive.content)
            assertEquals("3:2", image.getValue("aspectRatio").jsonPrimitive.content)
        })
        assertArrayEquals(Base64.getDecoder().decode(png), transport.generate(request(ArtModel.NANO_PRO), key).data())
    }

    @Test fun messageReviewSendsPreciselyTheReviewedTextAndFixedSystemInstruction() {
        val profile = ProviderProfile("review", config(QuickService.AVALAI), true)
        val text = checkNotNull(ReviewMessage.create("verification code: 123456; https://example.test/session"))
        val plan = checkNotNull(MessageReviewPlan.create(profile, text))
        val request = EngineProtocol.request(profile.config, key, plan.input)
        assertEquals("${QuickService.AVALAI.api}/chat/completions", request.url.toString())
        val raw = body(request)
        val json = Json.parseToJsonElement(raw).jsonObject
        assertFalse(listOf("123456", "https://example.test", key.value, "tools", "image_url", "inlineData").any { it in raw })
        assertEquals(profile.config.model, json.getValue("model").jsonPrimitive.content)
        val messages = json.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), messages.map { it.getValue("role").jsonPrimitive.content })
        assertEquals(listOf(ReviewMessage.INSTRUCTIONS, plan.input.prompt), messages.map { it.getValue("content").jsonPrimitive.content })
    }

    @Test fun aTextOnlyResponseOrExternalUrlIsNotAnImageAndBlockIsNotRetried() {
        val art = request(ArtModel.GPT_IMAGE)
        var requests = 0
        val noImage = OkHttpArtTransport(client(response = """{"data":[{"url":"https://do-not-fetch.test"}]}""") { requests++ })
        assertEquals(EngineProblem.NO_IMAGE, assertThrows(EngineFailure::class.java) { runBlocking { noImage.generate(art, key) } }.problem)
        val block = OkHttpArtTransport(client(response = """{"promptFeedback":{"blockReason":"SAFETY"}}""") { requests++ })
        assertEquals(EngineProblem.FILTERED, assertThrows(EngineFailure::class.java) { runBlocking { block.generate(request(ArtModel.NANO_LITE), key) } }.problem)
        assertEquals(2, requests)
    }

    @Test fun malformedOrNonPngPayloadDoesNotBecomeAFile() {
        val transport = OkHttpArtTransport(client(response = """{"data":[{"b64_json":"not-a-base64-image!"}]}"""))
        assertEquals(EngineProblem.PROTOCOL,
            assertThrows(EngineFailure::class.java) { runBlocking { transport.generate(request(ArtModel.GPT_IMAGE), key) } }.problem)
    }

    @Test fun exchangesOnlyAtTheFixedOpenRouterEndpointWithoutAuthorizationHeader() = runBlocking {
        val code = RouterCode.create("fixture-code", RouterApproval.create())
        val http = client(response = """{"key":"sk-or-v1-fixture-only"}""") { request ->
            assertEquals("https://openrouter.ai/api/v1/auth/keys", request.url.toString())
            assertNull(request.header("Authorization"))
            val json = body(request)
            assertTrue("\"code_challenge_method\":\"S256\"" in json)
            assertTrue("\"code_verifier\":\"${code.verifier}\"" in json)
            assertTrue("\"code\":\"fixture-code\"" in json)
        }
        assertEquals("sk-or-v1-fixture-only", OkHttpRouterKeyExchange(http).exchange(code).value)
    }

    @Test fun quickProbeUsesSmallBudgetAndZeroPriceCapsForFreeRouter() {
        val json = body(EngineProtocol.request(config(QuickService.FREE), key, ConversationInput.connectionProbe()))
        assertTrue("\"max_tokens\":256" in json)
        assertTrue("\"stream\":false" in json)
        assertTrue("\"max_price\":{\"prompt\":0,\"completion\":0,\"request\":0,\"image\":0}" in json)
        assertTrue("Reply with one short word: OK." in json)
    }
}
