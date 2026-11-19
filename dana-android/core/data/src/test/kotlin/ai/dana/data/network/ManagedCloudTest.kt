package ai.dana.data.network

import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountProblem
import ai.dana.domain.ArtShape
import ai.dana.domain.ConversationInput
import ai.dana.domain.LocalAction
import ai.dana.domain.ManagedCloud
import ai.dana.domain.ManagedFailure
import ai.dana.domain.ManagedKind
import ai.dana.domain.ManagedModel
import ai.dana.domain.ManagedProblem
import ai.dana.domain.PhoneIntent
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedCloudTest {
    @Test fun oneAccessTokenIsUsedOnlyAtTheFixedHttpsGateway() = fixture { server, client ->
        server.enqueue(json("""{"models":[{"id":"sample/chat","title":"گفتگو","provider":"Fixture","kind":"chat","units":1,"quality":null}],"planner":"sample/chat","daily_remaining":12}"""))
        server.enqueue(json("""{"text":"پاسخ واقعیِ fixture"}"""))
        server.enqueue(json("""{"action":"wifi_reminder","seconds":150,"on":false}"""))
        val catalog = client.catalog()
        val model = catalog.models.single()
        assertEquals(12, catalog.dailyRemaining)
        assertEquals(model, catalog.planner)
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        assertEquals("پاسخ واقعیِ fixture", client.chat(model, ConversationInput.single("سلام"), first))
        assertEquals(PhoneIntent.Propose(LocalAction.WifiReminder(150, false)), client.command(model, "وای‌فای را پس از دو و نیم دقیقه قطع کن", second))
        listOf("catalog", "chat", "command").forEachIndexed { index, path ->
            val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals(server.url("/v1/$path"), request.url)
            assertEquals("Bearer $TOKEN", request.headers["Authorization"])
            assertNull(request.headers["apikey"])
            if (index == 0) {
                assertEquals("GET", request.method)
                assertNull(request.headers["Idempotency-Key"])
            } else {
                assertEquals("POST", request.method)
                assertEquals(if (index == 1) first else second, request.headers["Idempotency-Key"])
                val body = EngineProtocol.decodeObject(checkNotNull(request.body).utf8())
                assertEquals(JsonPrimitive("sample/chat"), body["model"])
                assertEquals(if (index == 1) setOf("model", "prompt", "history") else setOf("model", "prompt"), body.keys)
            }
        }
    }

    @Test fun transportFailsClosedOnRedirectBusyQuotaAndDuplicateWithoutResend() = fixture { server, client ->
        server.enqueue(MockResponse.Builder().code(307).setHeader("Location", "/other").build())
        server.enqueue(json("""{"error":"quota"}""", 429))
        server.enqueue(json("""{"error":"provider_busy"}""", 429))
        server.enqueue(json("""{"error":"already_submitted"}""", 409))
        server.enqueue(json("{}", 401))
        val input = ConversationInput.single("سؤال")
        val id = UUID.randomUUID().toString()
        for (problem in listOf(ManagedProblem.RESPONSE, ManagedProblem.QUOTA, ManagedProblem.BUSY, ManagedProblem.DUPLICATE, ManagedProblem.SIGN_IN)) {
            expect(problem) { client.chat(CHAT, input, id) }
        }
        assertEquals(5, server.requestCount)
        repeat(5) { assertEquals("/v1/chat", checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)).url.encodedPath) }
    }

    @Test fun rejectsUnreviewableAndOversizeResponsesAndInvalidInputs() = fixture { server, client ->
        val id = UUID.randomUUID().toString()
        for (bad in listOf(
            """{"action":"timer","seconds":"90"}""", """{"action":"timer","seconds":86401}""",
            """{"action":"wifi","on":1}""", """{"action":"wifi","on":true,"intent":"unexpected"}""",
            """{"action":"open_app","package":"unexpected"}""", "{}", " ".repeat(3_000),
        )) {
            server.enqueue(json(bad))
            expect(ManagedProblem.RESPONSE) { client.command(CHAT, "دستور آزمون", id) }
        }
        expect(ManagedProblem.RESPONSE) { client.image(IMAGE, " ", ArtShape.SQUARE, id) }
        expect(ManagedProblem.RESPONSE) { client.chat(CHAT, ConversationInput.connectionProbe(), id) }
        expect(ManagedProblem.RESPONSE) { client.chat(CHAT, ConversationInput.single("آزمون\u202E"), id) }
        expect(ManagedProblem.RESPONSE) { client.chat(CHAT, ConversationInput.single("آزمون"), "not-a-uuid") }
        assertEquals(7, server.requestCount)
    }

    @Test fun noSessionTokenIsReadForAnInsecureOrNonOriginAddress() = runBlocking {
        val auth = object : ManagedAuthorization {
            override suspend fun <T> withAccess(operation: suspend (String) -> T): T = error("Must not read a bearer token")
        }
        for (url in listOf("http://example.test", "https://example.test/app", "https://user@example.test", "https://example.test/?id=1")) {
            val client = OkHttpManagedCloud(url.toHttpUrl(), auth, danaHttpClient())
            assertFalse(client.configured)
            expect(ManagedProblem.NOT_ENABLED) { client.catalog() }
        }
        val signedOut = object : ManagedAuthorization {
            override suspend fun <T> withAccess(operation: suspend (String) -> T): T = throw AccountFailure(AccountProblem.EXPIRED_SESSION)
        }
        expect(ManagedProblem.SIGN_IN) { OkHttpManagedCloud("https://example.test".toHttpUrl(), signedOut, danaHttpClient()).catalog() }
    }

    private fun fixture(run: suspend (MockWebServer, ManagedCloud) -> Unit) {
        runBlocking {
            withTimeout(15_000) {
                MockWebServer().use { server ->
                    val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
                    server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
                    server.start()
                    val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
                    val http = danaHttpClient().newBuilder().proxy(Proxy.NO_PROXY).sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
                    val auth = object : ManagedAuthorization {
                        override suspend fun <T> withAccess(operation: suspend (String) -> T): T = operation(TOKEN)
                    }
                    try { run(server, OkHttpManagedCloud(server.url("/"), auth, http)) } finally {
                        http.connectionPool.evictAll()
                        http.dispatcher.executorService.shutdown()
                    }
                }
            }
        }
    }

    private fun json(raw: String, status: Int = 200) = MockResponse.Builder().code(status).setHeader("Content-Type", "application/json").body(raw).build()
    private suspend fun expect(problem: ManagedProblem, run: suspend () -> Unit) {
        try { run(); throw AssertionError("Expected $problem") }
        catch (error: ManagedFailure) { assertEquals(problem, error.problem) }
    }

    private companion object {
        const val TOKEN = "one-email-test-session"
        val CHAT = ManagedModel("sample/chat", "Chat", "Fixture", ManagedKind.CHAT, 1, null)
        val IMAGE = ManagedModel("example-image", "Art", "Fixture", ManagedKind.IMAGE, 5, "2K")
    }
}
