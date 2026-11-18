package ai.dana.data.network

import ai.dana.domain.APPLE_MAC_MODEL
import ai.dana.domain.ApiCredential
import ai.dana.domain.AssistantTask
import ai.dana.domain.AssistantStyle
import ai.dana.domain.ContextMessage
import ai.dana.domain.ConversationInput
import ai.dana.domain.MessageRole
import ai.dana.domain.VisionPhoto
import ai.dana.domain.EngineText
import ai.dana.domain.ProviderEndpoint
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpEngineTransportTest {
    @Test
    fun twoRealTlsRequestsOverlapAndStreamIndependentAnswers() = localTest { server, transport ->
        val bothAccepted = CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                bothAccepted.countDown()
                if (!bothAccepted.await(5, TimeUnit.SECONDS)) return MockResponse.Builder().code(503).build()
                return if (request.url.encodedPath == "/v1/chat/completions") {
                    sse(OPENAI)
                } else {
                    sse(GEMINI)
                }
            }
        }
        kotlinx.coroutines.coroutineScope {
            val open = async { transport.stream(config(server, OPEN), key, "پرسش هم‌زمان").map { it.value }.toList() }
            val gemini = async { transport.stream(config(server, GEM), key, "پرسش هم‌زمان").map { it.value }.toList() }
            assertEquals(listOf("پاسخ ", "اول"), open.await())
            assertEquals(listOf("پاسخ دوم"), gemini.await())
        }
        val calls = List(2) { checkNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }.associateBy { it.url.encodedPath }
        val open = calls.getValue("/v1/chat/completions")
        val gemini = calls.getValue("/v1beta/models/test-model:streamGenerateContent")
        assertEquals("Bearer test-api-key", open.headers["Authorization"])
        assertNull(open.headers["x-goog-api-key"])
        assertEquals("test-api-key", gemini.headers["x-goog-api-key"])
        assertNull(gemini.headers["Authorization"])
        assertEquals("sse", gemini.url.queryParameter("alt"))
        for (call in calls.values) {
            assertEquals("POST", call.method)
            assertFalse(call.url.toString().contains(key.value))
            val json = requireNotNull(call.body).utf8()
            assertTrue(json.contains("پرسش هم‌زمان"))
            assertFalse(json.contains(key.value))
        }
    }

    @Test
    fun redirectsAreRejectedBeforePostingToAnotherOrigin() = localTest { server, transport ->
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse.Builder().code(307)
                .setHeader("Location", "https://${other.url("/new").host}:${other.port}/new").build())
            expect(EngineProblem.PROTOCOL) { transport.stream(config(server, OPEN), key, "پرسش").map { it.value }.toList() }
            assertEquals(1, server.requestCount)
            assertEquals(0, other.requestCount)
        }
    }

    @Test
    fun rateLimitIsNotRetriedAndMalformedJsonIsRejected() = localTest { server, transport ->
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "0").build())
        expect(EngineProblem.RATE_LIMIT) { transport.stream(config(server, OPEN), key, "پرسش").map { it.value }.toList() }
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "application/json").body("{}").build())
        expect(EngineProblem.PROTOCOL) { transport.stream(config(server, OPEN), key, "پرسش").map { it.value }.toList() }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun chatAcceptsACompleteJsonAnswerFromSameRequest() = localTest { server, transport ->
        server.enqueue(json("""{"choices":[{"message":{"content":"پاسخ یک‌باره"},"finish_reason":"stop"}]}"""))
        assertEquals(listOf("پاسخ یک‌باره"), transport.stream(config(server, OPEN), key, "پرسش").map { it.value }.toList())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun exhaustedCreditIsNotPresentedAsTemporaryThrottling() = localTest { server, transport ->
        server.enqueue(MockResponse.Builder().code(429).setHeader("Content-Type", "application/json")
            .body("""{"error":{"code":"insufficient_quota","message":"this text must not be shown"}}""").build())
        expect(EngineProblem.PAYMENT_REQUIRED) { transport.stream(config(server, OPEN), key, "پرسش").map { it.value }.toList() }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun acceptsCrLfCommentsBomAndMultipleDataLines() {
        val source = Buffer().writeUtf8(
            "\uFEFF: heartbeat\r\nevent: message\r\ndata: {\"choices\":[\r\n" +
                "data: {\"delta\":{\"content\":\"متن\"}}]}\r\n\r\ndata: [DONE]\r\n\r\n",
        )
        val chunks = mutableListOf<String>()
        EngineProtocol.read(source, OPEN) { chunks.add(it.value) }
        assertEquals(listOf("متن"), chunks)
    }

    @Test
    fun incompleteOversizedAndDeeplyNestedFramesAreRejected() {
        val truncated = Buffer().writeUtf8("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n")
        val received = mutableListOf<String>()
        assertEquals(EngineProblem.PROTOCOL, assertThrows(EngineFailure::class.java) {
            EngineProtocol.read(truncated, OPEN) { received.add(it.value) }
        }.problem)
        assertEquals(listOf("partial"), received)
        assertThrows(java.io.EOFException::class.java) {
            EngineProtocol.read(Buffer().writeUtf8("data: " + "x".repeat(129_000) + "\n\n"), OPEN) { true }
        }
        val nested = "[".repeat(80) + "0" + "]".repeat(80)
        assertEquals(EngineProblem.PROTOCOL, assertThrows(EngineFailure::class.java) {
            EngineProtocol.read(Buffer().writeUtf8("data: $nested\n\n"), OPEN) { true }
        }.problem)
    }

    @Test
    fun missingGeminiKeyAndInvalidModelNeverStartACall() = localTest { server, transport ->
        expect(EngineProblem.AUTHENTICATION) { transport.stream(config(server, GEM), null, "پرسش").map { it.value }.toList() }
        expect(EngineProblem.CONFIGURATION) { transport.stream(config(server, GEM, "models/../other"), key, "پرسش").map { it.value }.toList() }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun geminiCatalogUsesHeaderAndPagesWithoutSendingAnyPrompt() = tlsTest { server, client ->
        server.enqueue(json("""{"models":[{"name":"models/text-z","displayName":"Text Z","supportedGenerationMethods":["generateContent"]},
            {"name":"models/embed-only","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"page+=&"}"""))
        server.enqueue(json("""{"models":[{"name":"models/text-a","supportedGenerationMethods":["streamGenerateContent"]},
            {"name":"models/text-z","supportedGenerationMethods":["generateContent"]},
            {"name":"models/../unsafe","supportedGenerationMethods":["generateContent"]}]}"""))
        val directory = OkHttpModelDirectory(client)
        val models = directory.list(config(server, GEM).endpoint, key)
        assertEquals(listOf("models/text-a", "models/text-z"), models.map { it.id })
        assertEquals("Text Z", models.last().label)
        repeat(2) { index ->
            val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("GET", request.method)
            assertEquals("/v1beta/models", request.url.encodedPath)
            assertEquals(if (index == 0) null else "page+=&", request.url.queryParameter("pageToken"))
            assertEquals("100", request.url.queryParameter("pageSize"))
            assertEquals(key.value, request.headers["x-goog-api-key"])
            assertFalse(request.url.toString().contains(key.value))
            assertEquals(0, request.body?.size ?: 0)
        }
    }

    @Test
    fun cyclicAndOversizedCatalogsFailClosed() = tlsTest { server, client ->
        val directory = OkHttpModelDirectory(client)
        repeat(2) { server.enqueue(json("""{"nextPageToken":"same"}""")) }
        expect(EngineProblem.PROTOCOL) { directory.list(config(server, GEM).endpoint, key) }
        assertEquals(2, server.requestCount)
        server.enqueue(json(" ".repeat(1_048_577)))
        expect(EngineProblem.PROTOCOL) { directory.list(config(server, OPEN).endpoint, key) }
    }

    @Test
    fun appleCanReviseSnapshotsAndUsesAnEnglishTaskPrompt() = localTest { server, transport ->
        server.enqueue(sse("""
            data: {"snapshot":"First guess"}

            data: {"snapshot":"Revised text"}

            data: {"done":true}
        """))
        val actual = transport.stream(config(server, APPLE, APPLE_MAC_MODEL), key, "Input material", AssistantTask.SUMMARIZE).toList()
        assertEquals(listOf(EngineText("First guess", true), EngineText("Revised text", true)), actual)
        val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/v1/respond", request.url.encodedPath)
        assertEquals("Bearer test-api-key", request.headers["Authorization"])
        assertNull(request.headers["x-goog-api-key"])
        val body = checkNotNull(request.body).utf8()
        assertTrue(body.contains("Answer in English"))
        assertTrue(body.contains("Summarize the input"))
        assertTrue(body.contains("Input material"))
    }

    @Test
    fun appleReportsModelUnavailabilityAndLanguageSeparately() = tlsTest { server, client ->
        server.enqueue(json("""{"error":{"code":"apple_unavailable"}}""", 503))
        val apple = config(server, APPLE, APPLE_MAC_MODEL)
        val directory = OkHttpModelDirectory(client)
        try {
            directory.list(apple.endpoint, key)
            throw AssertionError("Expected a failed readiness check")
        } catch (error: EngineFailure) {
            assertEquals(503, error.httpStatus)
            assertEquals(EngineProblem.ENGINE_UNAVAILABLE, error.problem)
        }
        server.enqueue(sse("""data: {"error":{"code":"unsupported_language"}}"""))
        expect(EngineProblem.UNSUPPORTED_LANGUAGE) { OkHttpEngineTransport(client).stream(apple, key, "Input").toList() }
    }

    @Test
    fun applePrivateDnsRejectsPublicAddressesEvenWhenDnsReturnsThem() = tlsTest { server, client ->
        val public = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
        val local = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 0, 3))
        val privateDns = PrivateLanDns(Dns { listOf(public, local) })
        assertEquals(listOf(local), privateDns.lookup("example.test"))
        assertThrows(UnknownHostException::class.java) { PrivateLanDns(Dns { listOf(public) }).lookup("example.test") }
        val publicClient = client.newBuilder().dns(Dns { listOf(public) }).build()
        val endpoint = ProviderEndpoint.create(APPLE, ProviderNetwork.LOCAL_NETWORK, "https://mac.example/v1")
        expect(EngineProblem.CONFIGURATION) { OkHttpModelDirectory(publicClient).list(endpoint, key) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun chatPassesStructuredHistoryAndGatedPhotoToCompatibleProviders() = localTest { server, transport ->
        server.enqueue(sse(GEMINI))
        server.enqueue(sse(OPENAI))
        val jpeg = checkNotNull(VisionPhoto.fromJpeg(byteArrayOf(-1, -40, 2, 3, -1, -39)))
        val input = checkNotNull(ConversationInput.prepare("کد تایید ۱۲۳۴", listOf(
            ContextMessage(MessageRole.USER, "Earlier user"), ContextMessage(MessageRole.ASSISTANT, "Earlier answer"),
        ), emptyList(), AssistantStyle.CONCISE, AssistantTask.ASK, jpeg, mask = true))
        for (protocol in listOf(GEM, OPEN)) {
            transport.stream(config(server, protocol), key, input).toList()
            val call = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            val body = checkNotNull(call.body).utf8()
            assertTrue(body.contains("[محفوظ]"))
            assertFalse(body.contains("۱۲۳۴"))
            assertTrue(body.contains("Earlier user"))
            assertTrue(body.contains("Earlier answer"))
            assertTrue(body.contains(if (protocol == GEM) "inlineData" else "data:image/jpeg;base64,"))
            assertTrue(body.contains(jpeg.encodeBase64()))
        }
        expect(EngineProblem.CONTEXT_LIMIT) { transport.stream(config(server, APPLE, APPLE_MAC_MODEL), key, input).toList() }
        assertEquals(2, server.requestCount)
    }

    private fun json(data: String, status: Int = 200) = MockResponse.Builder().code(status)
        .setHeader("Content-Type", "application/json").body(data).build()

    private fun localTest(block: suspend (MockWebServer, OkHttpEngineTransport) -> Unit) =
        tlsTest { server, client -> block(server, OkHttpEngineTransport(client)) }

    private fun tlsTest(block: suspend (MockWebServer, OkHttpClient) -> Unit) {
        runBlocking {
            withTimeout(15_000) {
                MockWebServer().use { server ->
                    val cert = HeldCertificate.Builder().commonName("localhost")
                        .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
                    val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
                    server.useHttps(serverTls.sslSocketFactory())
                    server.start()
                    val tls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
                    val client = danaHttpClient().newBuilder().sslSocketFactory(tls.sslSocketFactory(), tls.trustManager).build()
                    try {
                        block(server, client)
                    } finally {
                        client.connectionPool.evictAll()
                        client.dispatcher.executorService.shutdown()
                    }
                }
            }
        }
    }

    private suspend fun expect(problem: EngineProblem, block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected $problem")
        } catch (error: EngineFailure) {
            assertEquals(problem, error.problem)
        }
    }

    private fun config(server: MockWebServer, protocol: ProviderProtocol, model: String = "test-model") =
        ProviderConfig.create("Test", protocol, ProviderNetwork.LOCAL_NETWORK,
            server.url(if (protocol == GEM) "/v1beta" else "/v1").toString(), model)

    private fun sse(data: String) = MockResponse.Builder().setHeader("Content-Type", "text/event-stream; charset=utf-8")
        .body(data.trimIndent() + "\n\n").build()

    companion object {
        private val key = ApiCredential.parse("test-api-key")
        private val OPEN = ProviderProtocol.OPENAI_COMPATIBLE
        private val GEM = ProviderProtocol.GEMINI_NATIVE
        private val APPLE = ProviderProtocol.APPLE_MAC
        private val OPENAI = """
            : keepalive

            data: {"choices":[{"delta":{"content":"پاسخ "},"finish_reason":null}]}

            data: {"choices":[{"delta":{"content":"اول"},"finish_reason":"stop"}]}
        """
        private val GEMINI = """
            data: {"candidates":[{"content":{"parts":[{"thought":true,"text":"internal"},{"text":"پاسخ دوم"}]},"finishReason":"STOP"}]}
        """
    }
}
