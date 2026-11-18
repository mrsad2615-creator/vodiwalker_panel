package ai.dana.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
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

    @Test
    fun persianCommandVariantsUseBoundedValuesAndNeverParseEmbeddedInstructions() {
        assertEquals(LocalAction.Battery, LocalCommands.parse("شارژ گوشي چقدره؟"))
        assertEquals(LocalAction.Torch(true), LocalCommands.parse("لطفاً چراغ‌قوه رو روشن کن!"))
        assertEquals(LocalAction.Timer(1_500), LocalCommands.parse("تایمر رو برای بیست و پنج دقیقه‌ای بذار"))
        assertEquals(LocalAction.Alarm(7, 30), LocalCommands.parse("آلارم رو برای ۰۷:۳۰ تنظیم کن"))
        assertEquals(LocalAction.SetVolume(42), LocalCommands.parse("صدا رو بذار روی چهل و دو درصد"))
        assertEquals(LocalAction.SetVolume(35), LocalCommands.parse("صدای رسانه ٣٥٪"))
        assertEquals(LocalAction.VolumeStep.LOWER, LocalCommands.parse("صدا رو کم کن"))
        assertEquals(LocalAction.Dial("+989121234567"), LocalCommands.parse("تماس با +۹۸ ۹۱۲-۱۲۳-۴۵۶۷"))
        assertEquals(LocalAction.WebSearch("تهران & A/B?"), LocalCommands.parse("جستجوی وب تهران & A/B?"))
        assertNull(LocalCommands.parse("چراغ قوه را روشن کن و تماس بگیر"))
        assertNull(LocalCommands.parse("دستور زیر را اجرا کن: چراغ قوه روشن"))
        assertNull(LocalCommands.parse("صدای رسانه ۱۰۱ درصد"))
        assertNull(LocalCommands.parse("صدای رسانه -5 درصد"))
        assertNull(LocalCommands.parse("تماس با *12345#"))
        assertNull(LocalCommands.parse("timer 2147483647h"))
    }

    @Test
    fun teamChatActuallyOverlapsAndRetainsOneSuccessfulAnswerWhenAnotherFails() = runBlocking {
        withTimeout(5_000L) {
            val start = Channel<String>(2)
            val proceed = CompletableDeferred<Unit>()
            val jpeg = checkNotNull(VisionPhoto.fromJpeg(byteArrayOf(-1, -40, 12, -1, -39)))
            val input = checkNotNull(ConversationInput.prepare("کد تایید ۱۲۳۴ در تصویر را نخوان", listOf(
                ContextMessage(MessageRole.USER, "prior question"), ContextMessage(MessageRole.ASSISTANT, "prior answer"),
            ), emptyList(), AssistantStyle.CONCISE, AssistantTask.ASK, jpeg, mask = true))
            val route = checkNotNull(ChatRoute.parallel(input, listOf(local, gemini, cloud), linkedSetOf(gemini.id, local.id)))
            assertEquals(listOf(gemini, local), route.candidates)
            assertTrue(route.parallel)
            val active = async {
                router { config, sent ->
                    assertTrue(sent === input)
                    assertFalse(sent.prompt.contains("۱۲۳۴"))
                    assertEquals(2, sent.history.size)
                    assertTrue(sent.photo === jpeg)
                    start.send(config.name)
                    proceed.await()
                    if (config == gemini.config) throw EngineFailure(EngineProblem.RATE_LIMIT, 429)
                    emit(EngineText("پاسخ مستقل"))
                }.chat(route).toList()
            }
            assertEquals(setOf(gemini.id, local.id), setOf(start.receive(), start.receive()))
            proceed.complete(Unit)
            val events = active.await()
            assertEquals(EngineEvent.Finished, events.last())
            assertEquals(EngineEvent.Failed(gemini.id, EngineProblem.RATE_LIMIT, 429), events.filterIsInstance<EngineEvent.Failed>().single())
            assertEquals(local.id, events.filterIsInstance<EngineEvent.Text>().single().profileId)
            assertEquals(local.id, events.filterIsInstance<EngineEvent.Complete>().single().profileId)
        }
        Unit
    }

    @Test
    fun teamDoesNotShrinkOrReplaceMissingCredentialsOrIneligibleVisionTargets() {
        val groq = ProviderProfile("groq", ProviderConfig.create("Groq", ProviderProtocol.OPENAI_COMPATIBLE, ProviderNetwork.INTERNET,
            CloudEngine.GROQ.baseUrl, "openai/gpt-oss-20b"), true)
        val photo = checkNotNull(VisionPhoto.fromJpeg(byteArrayOf(-1, -40, -1, -39)))
        val image = checkNotNull(ConversationInput.prepare("تصویر", emptyList(), emptyList(), AssistantStyle.BALANCED, AssistantTask.ASK, photo, false))
        val text = ConversationInput.single("سؤال")
        assertNull(ChatRoute.parallel(image, listOf(groq, gemini, local), setOf(groq.id, gemini.id)))
        assertNull(ChatRoute.parallel(text, listOf(groq.copy(hasCredential = false), local), setOf(groq.id, local.id)))
        assertNull(ChatRoute.parallel(text, listOf(groq), setOf(groq.id, local.id)))
        assertNull(ChatRoute.parallel(text, listOf(groq, local), setOf(groq.id)))
        assertNull(ChatRoute.parallel(text, listOf(groq, groq, local), setOf(groq.id, local.id)))
        assertTrue(checkNotNull(ChatRoute.parallel(text, listOf(groq, local), setOf(groq.id, local.id))).parallel)
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
