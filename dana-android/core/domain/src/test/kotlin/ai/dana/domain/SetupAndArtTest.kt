package ai.dana.domain

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupAndArtTest {
    private fun profile(service: QuickService, id: String = service.name) = ProviderProfile(id,
        ProviderConfig.create(service.title, service.protocol, ProviderNetwork.INTERNET, service.api, service.models.first()),
        hasCredential = true, credentialRevision = "v1.first")

    @Test fun savedGrantMatchesDataTypeAndCredentialRevisionNotPromptOrOrder() {
        val a = profile(QuickService.GOOGLE)
        val b = profile(QuickService.OPENAI)
        fun grant(p: ProviderProfile, photo: Boolean = false, mask: Boolean = true, memory: Boolean = false) =
            SendGrant.chat(listOf(p), false, true, memory, photo, mask)
        assertNotEquals(grant(a), grant(a.copy(credentialRevision = "v1.second")))
        assertNotEquals(grant(a), grant(b))
        assertNotEquals(grant(a), grant(a, photo = true))
        assertNotEquals(grant(a), grant(a, memory = true))
        assertNotEquals(grant(a), grant(a, mask = false))
        assertNotEquals(grant(a), SendGrant.comparison(listOf(a)))
        assertEquals(SendGrant.comparison(listOf(a, b)), SendGrant.comparison(listOf(b, a)))
        assertNotEquals(SendGrant.image(a, ArtModel.NANO.id), SendGrant.image(a, ArtModel.NANO_LITE.id))
    }

    @Test fun directRouteUsesTestedEngineOrExplicitChoiceAndNeverSwitchesInRequest() {
        val free = profile(QuickService.FREE)
        val google = profile(QuickService.GOOGLE)
        val text = ConversationInput.single("یک سؤال")
        assertEquals(listOf(google), ChatRoute.direct(text, listOf(free, google), null, google.id)?.candidates)
        assertEquals(listOf(free), ChatRoute.direct(text, listOf(free, google), free.id, google.id)?.candidates)
        assertNull(ChatRoute.direct(text, listOf(free, google), "removed", google.id))
        assertNull(ChatRoute.direct(text, listOf(free), null, google.id))
    }

    @Test fun quickConnectProbesOnlyTheRequestedPresetWithoutNeedingCatalogPermission() = runBlocking {
        val avalai = QuickService.AVALAI
        val key = "avalai-fixture-key"
        var probes = 0
        val service = QuickConnect(object : ModelDirectory {
            override suspend fun list(endpoint: ProviderEndpoint, key: ApiCredential?): List<DirectoryModel> = error("No catalog for chat")
        }, object : EngineTransport {
            override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): kotlinx.coroutines.flow.Flow<EngineText> {
                probes++
                assertEquals("gpt-5.4-nano", config.model)
                assertEquals(avalai.endpoint, config.endpoint)
                assertEquals("avalai-fixture-key", key?.value)
                assertTrue(input.probe)
                assertTrue(input.history.isEmpty() && input.facts.isEmpty() && input.photo == null)
                assertEquals("Reply with one short word: OK.", input.prompt)
                return flowOf(EngineText("OK"))
            }
        }, object : RouterKeyExchange { override suspend fun exchange(code: RouterCode): ApiCredential = error("No code exchange") })
        val tested = service.test(avalai, key)
        assertEquals(1, probes)
        assertFalse(tested.toString().contains(key))
    }

    @Test fun imageConnectionChecksCatalogWithoutTextProbeAndNeverBecomesChatRoute() = runBlocking {
        val preset = QuickService.AVALAI
        val selected = ArtModel.AVAL_STUDIO
        var models = listOf(DirectoryModel(ArtModel.AVAL_IMAGE.id, ArtModel.AVAL_IMAGE.title))
        var requests = 0
        val service = QuickConnect(object : ModelDirectory {
            override suspend fun list(endpoint: ProviderEndpoint, key: ApiCredential?): List<DirectoryModel> {
                requests++
                assertEquals(preset.endpoint, endpoint)
                return models
            }
        }, object : EngineTransport {
            override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): kotlinx.coroutines.flow.Flow<EngineText> =
                error("No billable completion for image-only setup")
        }, object : RouterKeyExchange { override suspend fun exchange(code: RouterCode): ApiCredential = error("No code exchange") })
        assertEquals(EngineProblem.CONFIGURATION,
            (runCatching { service.test(preset, "fixture", imageModel = selected) }.exceptionOrNull() as EngineFailure).problem)
        models = listOf(DirectoryModel(selected.id, selected.title))
        val imageOnly = ProviderProfile("image-profile", service.test(preset, "fixture", imageModel = selected).config, true)
        assertTrue(selected.matches(imageOnly))
        assertFalse(ArtModel.AVAL_IMAGE.matches(imageOnly))
        assertFalse(ArtModel.GPT_STUDIO.matches(imageOnly))
        assertNotEquals(SendGrant.image(profile(preset), selected.id), SendGrant.image(profile(preset), ArtModel.AVAL_IMAGE.id))
        assertEquals(selected.id, imageOnly.config.model)
        assertNull(ChatRoute.direct(ConversationInput.single("hello"), listOf(imageOnly), null, null))
        assertEquals(EngineProblem.CONFIGURATION, (runCatching {
            service.test(QuickService.OPENAI, "fixture", imageModel = selected)
        }.exceptionOrNull() as EngineFailure).problem)
        assertEquals(2, requests)
    }

    @Test fun fastLaneKeepsAtMostOneCompletePairAndPreservesMasking() {
        val history = (1..3).flatMap {
            listOf(ContextMessage(MessageRole.USER, "question $it"), ContextMessage(MessageRole.ASSISTANT, "answer $it"))
        }
        val input = checkNotNull(ConversationInput.prepare("verification code: 123456", history, emptyList(),
            AssistantStyle.DETAILED, AssistantTask.ASK, null, mask = true, lane = SendLane.FAST))
        assertEquals(2, input.omittedPairs)
        assertEquals(history.takeLast(2), input.history)
        assertEquals(AssistantStyle.CONCISE, input.style)
        assertEquals("verification code: [محفوظ]", input.prompt)
    }

    @Test fun pkceUsesFreshS256ChallengeAndDoesNotPutSecretInUrl() {
        val approval = RouterApproval.create()
        val code = RouterCode.create("ci-code-example", approval)
        val bytes = MessageDigest.getInstance("SHA-256").digest(code.verifier.toByteArray(Charsets.US_ASCII))
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), approval.challenge)
        assertNotEquals(approval.challenge, RouterApproval.create().challenge)
        assertFalse(approval.browserUrl.contains(code.verifier))
        assertTrue("code_challenge_method=S256" in approval.browserUrl)
        assertFalse("callback_url" in approval.browserUrl)
        assertFalse(code.toString().contains(code.code))
    }

    @Test fun imageOnlyUsesChosenOfficialEndpointAndOfflineCancelsWithoutReplay() = runBlocking {
        val google = profile(QuickService.GOOGLE)
        val openai = profile(QuickService.OPENAI)
        assertNull(ArtRequest.create(openai, ArtModel.NANO_LITE, ArtShape.SQUARE, "prompt"))
        assertNull(ArtRequest.create(google.copy(hasCredential = false), ArtModel.NANO_LITE, ArtShape.SQUARE, "prompt"))
        val request = checkNotNull(ArtRequest.create(google, ArtModel.NANO_LITE, ArtShape.PORTRAIT, " طرح درخت "))
        val prefs = object : PrivacyPreferences {
            override val offlineMode = MutableStateFlow(true)
            override suspend fun setOfflineMode(enabled: Boolean) { offlineMode.value = enabled }
        }
        val repo = object : ProviderRepository {
            override val profiles = flowOf(listOf(google))
            override suspend fun credentialFor(profile: ProviderProfile) = ApiCredential.parse("fixture")
            override suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String = error("No write")
            override suspend fun delete(id: String) = Unit
        }
        val started = CompletableDeferred<Unit>()
        val canceled = CompletableDeferred<Unit>()
        var calls = 0
        val images = ImageGeneration(prefs, repo, object : ImageTransport {
            override suspend fun generate(request: ArtRequest, key: ApiCredential): ArtImage {
                assertEquals("طرح درخت", request.prompt)
                calls++
                started.complete(Unit)
                try { awaitCancellation() } finally { canceled.complete(Unit) }
            }
        })
        assertEquals(EngineProblem.OFFLINE, runCatching { images.generate(request) }.exceptionOrNull().let { (it as EngineFailure).problem })
        assertEquals(0, calls)
        withTimeout(2_000) {
            prefs.setOfflineMode(false)
            val reply = async { runCatching { images.generate(request) } }
            started.await()
            prefs.setOfflineMode(true)
            canceled.await()
            val problem = reply.await().exceptionOrNull() as EngineFailure
            assertEquals(EngineProblem.OFFLINE, problem.problem)
            prefs.setOfflineMode(false)
        }
        assertEquals(1, calls)
    }

    @Test fun launcherAndScheduledCalendarCommandsAreBoundedAndKeepLiteralTitle() {
        assertEquals(LocalAction.LaunchApp("تنظیمات"), LocalCommands.parse("لطفاً برنامه تنظیمات را باز کن"))
        assertEquals("کیبورد گوگل", LocalCommands.appLabel(" كيبورد‌گوگل "))
        assertNull(LocalCommands.parse("open app https://example.test"))
        assertEquals(LocalAction.CalendarDraft("جلسهٔ QA", LocalAction.CalendarStart(1, 18, 30)),
            LocalCommands.parse("رویداد فردا ۱۸:۳۰ جلسهٔ QA"))
        assertNull(LocalCommands.parse("رویداد فردا ۲۹:۳۰ جلسه"))
        assertNotNull(LocalCommands.parse("رویداد جلسهٔ معمولی"))
    }
}
