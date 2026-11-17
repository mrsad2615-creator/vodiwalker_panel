package ai.dana.data.network

import ai.dana.domain.ApiCredential
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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
            val open = async { transport.stream(config(server, OPEN), key, "پرسش هم‌زمان").toList() }
            val gemini = async { transport.stream(config(server, GEM), key, "پرسش هم‌زمان").toList() }
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
            expect(EngineProblem.PROTOCOL) { transport.stream(config(server, OPEN), key, "پرسش").toList() }
            assertEquals(1, server.requestCount)
            assertEquals(0, other.requestCount)
        }
    }

    @Test
    fun rateLimitIsNotRetriedAndNonSseIsRejected() = localTest { server, transport ->
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "0").build())
        expect(EngineProblem.RATE_LIMIT) { transport.stream(config(server, OPEN), key, "پرسش").toList() }
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "application/json").body("{}").build())
        expect(EngineProblem.PROTOCOL) { transport.stream(config(server, OPEN), key, "پرسش").toList() }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun acceptsCrLfCommentsBomAndMultipleDataLines() {
        val source = Buffer().writeUtf8(
            "\uFEFF: heartbeat\r\nevent: message\r\ndata: {\"choices\":[\r\n" +
                "data: {\"delta\":{\"content\":\"متن\"}}]}\r\n\r\ndata: [DONE]\r\n\r\n",
        )
        val chunks = mutableListOf<String>()
        EngineProtocol.read(source, OPEN) { chunks.add(it) }
        assertEquals(listOf("متن"), chunks)
    }

    @Test
    fun incompleteOversizedAndDeeplyNestedFramesAreRejected() {
        val truncated = Buffer().writeUtf8("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n")
        val received = mutableListOf<String>()
        assertEquals(EngineProblem.PROTOCOL, assertThrows(EngineFailure::class.java) {
            EngineProtocol.read(truncated, OPEN) { received.add(it) }
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
        expect(EngineProblem.AUTHENTICATION) { transport.stream(config(server, GEM), null, "پرسش").toList() }
        expect(EngineProblem.CONFIGURATION) { transport.stream(config(server, GEM, "models/../other"), key, "پرسش").toList() }
        assertEquals(0, server.requestCount)
    }

    private fun localTest(block: suspend (MockWebServer, OkHttpEngineTransport) -> Unit) {
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
                        block(server, OkHttpEngineTransport(client))
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
            server.url(if (protocol == OPEN) "/v1" else "/v1beta").toString(), model)

    private fun sse(data: String) = MockResponse.Builder().setHeader("Content-Type", "text/event-stream; charset=utf-8")
        .body(data.trimIndent() + "\n\n").build()

    companion object {
        private val key = ApiCredential.parse("test-api-key")
        private val OPEN = ProviderProtocol.OPENAI_COMPATIBLE
        private val GEM = ProviderProtocol.GEMINI_NATIVE
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
