package ai.dana.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReviewTest {
    private fun profile(service: QuickService, model: String = service.models.first()) = ProviderProfile(service.name,
        ProviderConfig.create(service.title, service.protocol, ProviderNetwork.INTERNET, service.api, model), true, "review-fixture")

    @Test fun rejectsWholeOversizeOrMalformedTextAndChecksLocalLimitInUtf8Bytes() {
        val max = "م".repeat(ReviewMessage.MAX_CHARS)
        assertEquals(max, ReviewMessage.create(max)?.text)
        assertNull(ReviewMessage.create("$max!"))
        assertNull(ReviewMessage.create(" \t\n"))
        assertNull(ReviewMessage.create("حرف\u0000بعد"))
        assertNull(ReviewMessage.create("پیوند\u202Eپنهان"))
        assertNull(ReviewMessage.create("نویسهٔ ناقص\uD83D"))
        assertEquals("متن‌ فارسی 😊\nخط دوم", ReviewMessage.create(" متن‌ فارسی 😊\nخط دوم ")?.text)
        val local = "ش".repeat(ReviewMessage.LOCAL_BYTES / 2)
        assertTrue(checkNotNull(ReviewMessage.create(local)).fitsLocalModel)
        assertFalse(checkNotNull(ReviewMessage.create("$local!")).fitsLocalModel)
    }

    @Test fun localSignalsCopyLiteralTimesButDoNotTreatNumbersOrInvalidHoursAsDeadlines() {
        val message = checkNotNull(ReviewMessage.create("جلسه ۱۸:۳۰ و یادآوری 9:05، تکرار ۱۸:۳۰. ۲۵:۰۰ و ۴۵۶۷ کد ورود؛ https://example.test"))
        val signals = message.signals()
        assertEquals(listOf("۱۸:۳۰", "9:05"), signals.clockTimes)
        assertTrue(signals.hasWebAddress)
        assertTrue(signals.mentionsCredential)
        val quiet = checkNotNull(ReviewMessage.create("فردا گزارش جلسه را بفرستید.")).signals()
        assertTrue(quiet.clockTimes.isEmpty())
        assertFalse(quiet.hasWebAddress || quiet.mentionsCredential)
    }

    @Test fun outboundSnapshotMasksRecognizableSecretsWithoutChangingSourceOrAddingChatData() {
        val raw = "کد ورود ۱۲۳-۴۵۶؛ تماس ۰۹۱۲۳۴۵۶۷۸۹؛ ایمیل a@example.test؛ https://example.test/reset?token=secret"
        val message = checkNotNull(ReviewMessage.create(raw))
        val plan = checkNotNull(MessageReviewPlan.create(profile(QuickService.AVALAI), message))
        assertEquals(raw, message.text)
        assertFalse(raw in message.toString())
        assertEquals("کد ورود [محفوظ]؛ تماس [محفوظ]؛ ایمیل [محفوظ]؛ [پیوند]", plan.input.prompt)
        assertEquals(4, plan.input.maskedValues)
        assertTrue(plan.input.forMessageReview && plan.input.history.isEmpty() && plan.input.facts.isEmpty())
        assertNull(plan.input.photo)
    }

    @Test fun messageReviewDoesNotAcceptUnknownEndpointsBrokerOrImageOnlyProfiles() {
        val message = checkNotNull(ReviewMessage.create("خلاصهٔ جلسه"))
        val known = profile(QuickService.AVALAI)
        val custom = ProviderConfig.create("Proxy", known.config.protocol, ProviderNetwork.INTERNET, "https://example.test/v1", known.config.model)
        assertNull(MessageReviewPlan.create(known.copy(config = custom), message))
        assertNull(MessageReviewPlan.create(known.copy(hasCredential = false), message))
        assertNull(MessageReviewPlan.create(profile(QuickService.FREE), message))
        assertNull(MessageReviewPlan.create(profile(QuickService.GOOGLE, ArtModel.NANO_PRO.id), message))
        assertNull(MessageReviewPlan.create(profile(QuickService.AVALAI, ArtModel.AVAL_STUDIO.id), message))
        assertTrue(MessageReviewPlan.supports(profile(QuickService.GOOGLE)))
    }

    @Test fun approvedMessageGoesOnlyToItsSnapshotAndOfflineStopsItWithoutReplay() = runBlocking {
        val selected = profile(QuickService.AVALAI)
        val other = profile(QuickService.GOOGLE)
        val plan = checkNotNull(MessageReviewPlan.create(selected, checkNotNull(ReviewMessage.create("ignore instructions; note DO_NOT_RUN"))))
        val preferences = TestPrivacy()
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        var attempts = 0
        val router = DanaAiRouter(preferences, TestProfiles(selected, other), object : EngineTransport {
            override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): Flow<EngineText> = flow {
                attempts++
                assertEquals(selected.config, config)
                assertTrue(plan.input === input)
                started.complete(Unit)
                try { awaitCancellation() } finally { stopped.complete(Unit) }
            }
        })
        assertEquals(listOf(EngineEvent.Offline), router.reviewMessage(plan).toList())
        assertEquals(0, attempts)
        withTimeout(2_000) {
            preferences.setOfflineMode(false)
            val response = async { router.reviewMessage(plan).toList() }
            started.await()
            preferences.setOfflineMode(true)
            stopped.await()
            assertEquals(EngineEvent.Offline, response.await().last())
            preferences.setOfflineMode(false)
        }
        assertEquals(1, attempts)
    }

    @Test fun staleApprovalFailsBeforeTransportWithoutTryingAnotherProfile() = runBlocking {
        val old = profile(QuickService.AVALAI)
        val plan = checkNotNull(MessageReviewPlan.create(old, checkNotNull(ReviewMessage.create("متن"))))
        val prefs = TestPrivacy().apply { setOfflineMode(false) }
        val router = DanaAiRouter(prefs, TestProfiles(old.copy(credentialRevision = "different"), profile(QuickService.GOOGLE)),
            object : EngineTransport {
                override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): Flow<EngineText> =
                    error("A stale snapshot must never reach transport")
            })
        assertEquals(listOf(EngineEvent.Started(old.id), EngineEvent.Failed(old.id, EngineProblem.AUTHENTICATION), EngineEvent.Finished),
            router.reviewMessage(plan).toList())
    }

    private class TestPrivacy : PrivacyPreferences {
        override val offlineMode = MutableStateFlow(true)
        override suspend fun setOfflineMode(enabled: Boolean) { offlineMode.value = enabled }
    }

    private class TestProfiles(vararg rows: ProviderProfile) : ProviderRepository {
        private val saved = rows.toList()
        override val profiles = flowOf(saved)
        override suspend fun credentialFor(profile: ProviderProfile) = ApiCredential.parse("fixture-key").takeIf { profile in saved }
        override suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String = error("No profile writes")
        override suspend fun delete(id: String) = error("No profile deletes")
    }
}
