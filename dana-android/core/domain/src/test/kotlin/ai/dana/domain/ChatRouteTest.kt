package ai.dana.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRouteTest {
    @Test
    fun autoPutsPrivateHostFirstThenGeminiAndOnlyUsesApprovedFallbacks() = runBlocking {
        val calls = mutableListOf<String>()
        val profiles = listOf(cloud, gemini, local, macWithoutToken)
        val route = checkNotNull(ChatRoute.select(ConversationInput.single("hello"), profiles, null))
        assertEquals(listOf(local, gemini, cloud), route.candidates)
        val events = router { profile, _ ->
            calls += profile.name
            if (profile == local.config) throw EngineFailure(EngineProblem.RATE_LIMIT, 429)
            emit(EngineText("پاسخ"))
        }.chat(route).toList()
        assertEquals(listOf(local.config.name, gemini.config.name), calls)
        assertEquals(listOf(local.id, gemini.id), events.filterIsInstance<EngineEvent.Started>().map { it.profileId })
        assertEquals(gemini.id, events.filterIsInstance<EngineEvent.Complete>().single().profileId)
        assertEquals(429, events.filterIsInstance<EngineEvent.Failed>().single().httpStatus)
    }

    @Test
    fun policyRefusalAndPartialAnswersNeverLeadToAnotherCall() = runBlocking {
        for ((problem, partial) in listOf(EngineProblem.FILTERED to false, EngineProblem.NETWORK to true, EngineProblem.TLS to false)) {
            val called = mutableListOf<String>()
            val route = checkNotNull(ChatRoute.select(ConversationInput.single("test"), listOf(local, gemini, cloud), null))
            val result = router { config, _ ->
                called += config.name
                if (partial) emit(EngineText("این بخش دریافت شده"))
                throw EngineFailure(problem)
            }.chat(route).toList()
            assertEquals(listOf(local.config.name), called)
            assertEquals(problem, result.filterIsInstance<EngineEvent.Failed>().single().problem)
        }
    }

    @Test
    fun offlineDoesNotRequestEvenPrivateLanAndManualNeverChangesDestination() = runBlocking {
        val route = checkNotNull(ChatRoute.select(ConversationInput.single("hello"), listOf(local, gemini), gemini.id))
        assertEquals(listOf(gemini), route.candidates)
        assertEquals(listOf(EngineEvent.Offline), router(offline = true) { _, _ -> error("off-device work") }.chat(route).toList())
        assertNull(ChatRoute.select(route.input, listOf(local), "removed"))
    }

    @Test
    fun masksOnlyOutboundTextAndSelectsRelevantMemoryWithinBudget() {
        val history = (1..5).flatMap { index ->
            listOf(ContextMessage(MessageRole.USER, "Turns $index"), ContextMessage(MessageRole.ASSISTANT, "Answer $index"))
        }
        val facts = listOf(UserMemory(1, "پروژه سپید و شماره ۱۲۳۴۵۶۷۸۹۰", 2), UserMemory(2, "غذای دلخواه", 5))
        val raw = "در پروژه سپید، کد تأیید: ۱۲۳۴۵۶ و کارت ۶۰۳۷-۹۹۱۲-۰۱۲۳-۴۵۶۷ چیست؟"
        val context = checkNotNull(ConversationInput.prepare(
            raw, history, facts, AssistantStyle.DETAILED, AssistantTask.ASK, null, mask = true,
        ))
        assertEquals(3, context.maskedValues)
        assertEquals(2, context.omittedPairs)
        assertEquals(6, context.history.size)
        assertEquals("Turns 3", context.history.first().body)
        assertEquals(listOf("پروژه سپید و شماره [محفوظ]"), context.facts)
        assertFalse(context.prompt.contains("۱۲۳۴۵۶"))
        assertTrue(raw.contains("۱۲۳۴۵۶"))
    }

    @Test
    fun imageCannotGoToTextOnlyMacAndChangesToOriginalBufferAreIsolated() {
        val storage = byteArrayOf(-1, -40, 64, -1, -39)
        val photo = checkNotNull(VisionPhoto.fromJpeg(storage))
        val before = photo.encodeBase64()
        storage.fill(0)
        assertEquals(before, photo.encodeBase64())
        assertNull(VisionPhoto.fromJpeg(storage))
        val input = checkNotNull(ConversationInput.prepare("تصویر", emptyList(), emptyList(), AssistantStyle.BALANCED, AssistantTask.ASK, photo, mask = true))
        assertFalse(input.fits(macWithoutToken.copy(hasCredential = true)))
        assertTrue(input.fits(gemini))
    }

    @Test
    fun offlineCommandsAreClosedTypedBoundedAndPreserveOriginalContent() {
        assertEquals(LocalAction.Timer(300), LocalCommands.parse("تایمر ۵ دقیقه"))
        assertEquals(LocalAction.Alarm(7, 32), LocalCommands.parse("آلارم 07:۳۲"))
        assertEquals(LocalAction.Note("مقدار A و ۱۲۳۴"), LocalCommands.parse("NOTE مقدار A و ۱۲۳۴"))
        assertEquals(LocalAction.SmsDraft("+989121234567", "سلام BABAK"), LocalCommands.parse("پیامک +۹۸۹۱۲۱۲۳۴۵۶۷ سلام BABAK"))
        assertNull(LocalCommands.parse("پیامک +98912;extra unsafe"))
        assertNull(LocalCommands.parse("تایمر ۳ روز"))
        assertNull(LocalCommands.parse("زنگ ۲۵:۴۰"))
        assertNull(LocalCommands.parse("timer 99999h"))
        assertNull(LocalCommands.parse("{\"intent\":\"android.intent.action.SEND\"}"))
    }

    private fun router(
        offline: Boolean = false,
        stream: suspend kotlinx.coroutines.flow.FlowCollector<EngineText>.(ProviderConfig, ConversationInput) -> Unit,
    ) = DanaAiRouter(
        object : PrivacyPreferences {
            override val offlineMode = flowOf(offline)
            override suspend fun setOfflineMode(enabled: Boolean) = error("Unexpected change")
        },
        object : ProviderRepository {
            override val profiles = flowOf(listOf(local, gemini, cloud, macWithoutToken))
            override suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String = error("Unexpected write")
            override suspend fun delete(id: String) = error("Unexpected delete")
            override suspend fun credentialFor(profile: ProviderProfile) = if (profile.hasCredential) ApiCredential.parse("private-test-only-token") else null
        },
        object : EngineTransport {
            override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): Flow<EngineText> =
                flow { stream(config, input) }
        },
    )

    private companion object {
        val local = profile("local", ProviderProtocol.OPENAI_COMPATIBLE, ProviderNetwork.LOCAL_NETWORK, "https://localhost/v1", false)
        val cloud = profile("cloud", ProviderProtocol.OPENAI_COMPATIBLE, ProviderNetwork.INTERNET, "https://cloud.example/v1", true)
        val gemini = profile("gemini", ProviderProtocol.GEMINI_NATIVE, ProviderNetwork.INTERNET, GOOGLE_GEMINI_BASE_URL, true)
        val macWithoutToken = profile("mac", ProviderProtocol.APPLE_MAC, ProviderNetwork.LOCAL_NETWORK, "https://mac.example/v1", false)
        fun profile(name: String, format: ProviderProtocol, network: ProviderNetwork, url: String, key: Boolean) =
            ProviderProfile(name, ProviderConfig.create(name, format, network, url, if (format == ProviderProtocol.APPLE_MAC) APPLE_MAC_MODEL else "test-model"), key)
    }
}
