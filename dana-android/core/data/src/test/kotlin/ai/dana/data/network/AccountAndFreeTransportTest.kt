package ai.dana.data.network

import ai.dana.data.account.SupabaseEmailApi
import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountProblem
import ai.dana.domain.ApiCredential
import ai.dana.domain.EmailProject
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.OPENROUTER_BASE_URL
import ai.dana.domain.OPENROUTER_FREE_MODEL
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProtocol
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAndFreeTransportTest {
    @Test
    fun emailOtpVerificationRefreshAndLocalScopeRevocationUseTheSameTrustedHost() = fixture(project.host) { server, http ->
        val api = SupabaseEmailApi(http)
        server.enqueue(json("{}"))
        server.enqueue(json(verified("refresh-fixture-one")))
        server.enqueue(json(verified("refresh-fixture-two")))
        server.enqueue(MockResponse.Builder().code(204).build())
        api.send(project, email)
        val signed = api.verify(project, email, "983061")
        assertEquals(email, signed.email)
        assertFalse(signed.toString().contains(email))
        val rotated = api.refresh(project, signed.refresh)
        assertEquals("refresh-fixture-two", rotated.refresh.value)
        api.revoke(project, rotated.access)
        val (send, verify, refresh, logout) = List(4) { next(server) }
        assertEquals("/auth/v1/otp", send.url.encodedPath)
        assertEquals(JsonPrimitive(true), data(send)["create_user"])
        assertEquals(JsonPrimitive(email), data(send)["email"])
        assertEquals("/auth/v1/verify", verify.url.encodedPath)
        assertEquals(JsonPrimitive("email"), data(verify)["type"])
        assertEquals(JsonPrimitive("983061"), data(verify)["token"])
        assertEquals("/auth/v1/token", refresh.url.encodedPath)
        assertEquals("refresh_token", refresh.url.queryParameter("grant_type"))
        assertEquals(JsonPrimitive("refresh-fixture-one"), data(refresh)["refresh_token"])
        assertEquals("/auth/v1/logout", logout.url.encodedPath)
        assertEquals("local", logout.url.queryParameter("scope"))
        assertEquals("Bearer access-fixture", logout.headers["Authorization"])
        for (request in listOf(send, verify, refresh, logout)) {
            assertEquals(project.host, request.url.host)
            assertEquals("POST", request.method)
            assertEquals(project.publishableKey, request.headers["apikey"])
            assertFalse(request.url.toString().contains(email))
            assertFalse(request.url.toString().contains("983061"))
        }
        for (request in listOf(send, verify, refresh)) assertNull(request.headers["Authorization"])
    }

    @Test
    fun emailRejectsWrongUserUnconfirmedSessionAndExpiredCode() = fixture(project.host) { server, http ->
        val api = SupabaseEmailApi(http)
        server.enqueue(json(verified("skip").replace(email, "different@example.test")))
        expect(AccountProblem.RESPONSE) { api.verify(project, email, "983061") }
        server.enqueue(json(verified("skip").replace("2026-11-17T00:00:00Z", "")))
        expect(AccountProblem.RESPONSE) { api.verify(project, email, "983061") }
        server.enqueue(json("""{"error_code":"otp_expired","msg":"untrusted service message"}""", 403))
        expect(AccountProblem.INVALID_CODE) { api.verify(project, email, "983061") }
        server.enqueue(json("""{"error_code":"refresh_token_not_found"}""", 400))
        expect(AccountProblem.EXPIRED_SESSION) { api.refresh(project, ApiCredential.parse("not-a-session")) }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun emailClientDoesNotReplayThrottledRequestOrFollowRedirect() = fixture(project.host) { server, http ->
        val api = SupabaseEmailApi(http)
        server.enqueue(MockResponse.Builder().code(307).setHeader("Location", "https://unexpected.example/otp").build())
        expect(AccountProblem.RESPONSE) { api.send(project, email) }
        server.enqueue(MockResponse.Builder().code(429).setHeader("Retry-After", "0").build())
        expect(AccountProblem.RATE_LIMIT) { api.send(project, email) }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun openRouterUsesFixedZeroPriceRouterAndOnlyReadsKeyMetadataOnCheck() = fixture("openrouter.ai") { server, http ->
        val config = freeConfig()
        val key = ApiCredential.parse("sk-or-v1-fixture-no-production-use")
        server.enqueue(json("""{"data":{"label":"restricted key"}}"""))
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "text/event-stream")
            .body(": OPENROUTER PROCESSING\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"سلام\"}}]}\n\ndata: [DONE]\n\n").build())
        assertEquals(listOf(OPENROUTER_FREE_MODEL), OkHttpModelDirectory(http).list(config.endpoint, key).map { it.id })
        assertEquals(listOf("سلام"), OkHttpEngineTransport(http).stream(config, key, "نمونه").map { it.value }.toList())
        val check = next(server)
        val chat = next(server)
        assertEquals("/api/v1/key", check.url.encodedPath)
        assertEquals("GET", check.method)
        assertEquals(0L, check.body?.size ?: 0L)
        assertEquals("/api/v1/chat/completions", chat.url.encodedPath)
        assertEquals("Bearer ${key.value}", chat.headers["Authorization"])
        val body = data(chat)
        assertEquals(JsonPrimitive(OPENROUTER_FREE_MODEL), body["model"])
        assertNull(body["models"])
        val caps = (body.getValue("provider") as JsonObject).getValue("max_price") as JsonObject
        assertEquals(setOf("prompt", "completion", "image", "request"), caps.keys)
        assertTrue(caps.values.all { it == JsonPrimitive(0) })
    }

    @Test
    fun openRouterCreditLimitIsReportedNotRetriedOrReplacedWithCostlyRequest() = fixture("openrouter.ai") { server, http ->
        val key = ApiCredential.parse("sk-or-v1-fixture-no-production-use")
        val transport = OkHttpEngineTransport(http)
        server.enqueue(json("""{"error":{"code":402}}""", 402))
        try {
            transport.stream(freeConfig(), key, "نمونه").toList()
            throw AssertionError("Expected limit")
        } catch (error: EngineFailure) {
            assertEquals(EngineProblem.PAYMENT_REQUIRED, error.problem)
            assertEquals(402, error.httpStatus)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun publicPickerCatalogFiltersPaidAndNonChatRowsAndNeverBearsAKey() = fixture("openrouter.ai") { server, http ->
        fun row(id: String, prompt: String = "0", output: String = "text", input: String = "text", request: String = "0") =
            """{"id":"$id","name":"Official $id","architecture":{"input_modalities":["$input"],"output_modalities":["$output"]},
               "pricing":{"prompt":"$prompt","completion":"0","request":"$request"}}"""
        val rows = listOf(row("catalog/free-a:free"), row("catalog/free-a:free"), row("catalog/paid-z"),
            row("catalog/surcharge:free", request = "0.002"), row("catalog/not-zero:free", prompt = "0.5"),
            row("catalog/image:free", output = "image"), row("catalog/audio:free", input = "audio"), row("catalog/free-b:free", prompt = "0.0000"))
        server.enqueue(json("""{"data":[${rows.joinToString(",")}]}"""))
        val listing = OpenRouterDirectory(http).freeTextModels()
        assertEquals(listOf("catalog/free-a:free", "catalog/free-b:free"), listing.map { it.id })
        val get = next(server)
        assertEquals("/api/v1/models", get.url.encodedPath)
        assertEquals("text", get.url.queryParameter("output_modalities"))
        assertNull(get.headers["Authorization"])
        val free = ProviderConfig.create("Free catalog", ProviderProtocol.OPENROUTER_FREE, ProviderNetwork.INTERNET, OPENROUTER_BASE_URL, listing.last().id)
        server.enqueue(json("""{"error":{"code":429}}""", 429))
        try {
            OkHttpEngineTransport(http).stream(free, ApiCredential.parse("sk-or-v1-fixture"), "private fixture").toList()
            throw AssertionError("Expected rate limit")
        } catch (e: EngineFailure) { assertEquals(EngineProblem.RATE_LIMIT, e.problem) }
        val send = data(next(server))
        assertEquals(JsonPrimitive("catalog/free-b:free"), send["model"])
        assertNull(send["models"])
        assertEquals(2, server.requestCount)
    }

    private fun fixture(host: String, verify: suspend (MockWebServer, OkHttpClient) -> Unit) {
        runBlocking {
            withTimeout(15_000) {
                MockWebServer().use { server ->
                    val cert = HeldCertificate.Builder().commonName(host).addSubjectAlternativeName(host).build()
                    server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
                    server.start()
                    val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
                    val http = danaHttpClient().newBuilder().proxy(Proxy.NO_PROXY)
                        .dns(Dns { name -> check(name == host); listOf(InetAddress.getByName("127.0.0.1")) })
                        .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
                        .addInterceptor { chain ->
                            val initial = chain.request()
                            check(initial.url.host == host && initial.url.port == 443)
                            chain.proceed(initial.newBuilder().url(initial.url.newBuilder().port(server.port).build()).build())
                        }.build()
                    try { verify(server, http) } finally {
                        http.connectionPool.evictAll()
                        http.dispatcher.executorService.shutdown()
                    }
                }
            }
        }
    }

    private fun freeConfig() = ProviderConfig.create("OpenRouter Free", ProviderProtocol.OPENROUTER_FREE, ProviderNetwork.INTERNET,
        OPENROUTER_BASE_URL, OPENROUTER_FREE_MODEL)
    private fun next(server: MockWebServer) = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
    private fun data(request: RecordedRequest) = EngineProtocol.decodeObject(checkNotNull(request.body).utf8())
    private fun json(raw: String, status: Int = 200) = MockResponse.Builder().code(status).setHeader("Content-Type", "application/json").body(raw).build()
    private fun verified(refresh: String) = """{"access_token":"access-fixture","refresh_token":"$refresh","token_type":"bearer","user":{"email":"$email","email_confirmed_at":"2026-11-17T00:00:00Z"}}"""
    private suspend fun expect(problem: AccountProblem, block: suspend () -> Unit) {
        try { block(); throw AssertionError("Expected $problem") }
        catch (error: AccountFailure) { assertEquals(problem, error.problem) }
    }

    companion object {
        private val project = checkNotNull(EmailProject.parse("https://abcdefghijklmnopqrst.supabase.co", "sb_publishable_test_fixture_only_098765"))
        private const val email = "tester@example.test"
    }
}
