package ai.dana.domain

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanaAiRouterTest {
    @Test
    fun chosenEnginesOverlapAndKeepResponsesSeparate() = testRunning {
        val connected = Channel<String>(2)
        val release = CompletableDeferred<Unit>()
        val a = profile("alpha")
        val b = profile("beta")
        val router = router { config, _, prompt ->
            flow {
                assertEquals("پرسش", prompt)
                connected.send(config.name)
                release.await()
                emit("پاسخ ${config.name}")
            }
        }
        val run = async { router.compare(request(a, b)).toList() }
        assertEquals(setOf("alpha", "beta"), setOf(connected.receive(), connected.receive()))
        release.complete(Unit)
        val events = run.await()
        assertEquals(mapOf("alpha" to "پاسخ alpha", "beta" to "پاسخ beta"),
            events.filterIsInstance<EngineEvent.Text>().associate { it.profileId to it.chunk.value })
        assertEquals(setOf("alpha", "beta"), events.filterIsInstance<EngineEvent.Complete>().map { it.profileId }.toSet())
        assertEquals(EngineEvent.Finished, events.last())
    }

    @Test
    fun rateLimitOnlyFailsThatEngineAndNeverSelectsAnother() = testRunning {
        val events = router { config, _, _ ->
            flow {
                if (config.name == "limited") throw EngineFailure(EngineProblem.RATE_LIMIT)
                emit("پاسخ دوم")
            }
        }.compare(request(profile("limited"), profile("working"))).toList()
        assertTrue(EngineEvent.Failed("limited", EngineProblem.RATE_LIMIT) in events)
        assertTrue(events.any { it is EngineEvent.Complete && it.profileId == "working" })
        assertEquals(listOf("working"), events.filterIsInstance<EngineEvent.Text>().map { it.profileId })
    }

    @Test
    fun offlineOrUnreadableSettingNeverResolvesAKeyOrStartsTransport() = testRunning {
        val prefs = listOf(Preferences(initial = true), object : PrivacyPreferences {
            override val offlineMode: Flow<Boolean> = flow { throw IOException("test privacy failure") }
            override suspend fun setOfflineMode(enabled: Boolean) = Unit
        })
        for (preference in prefs) {
            val router = router(preference, credentials = { error("Must not access keys in offline mode") }) { _, _, _ ->
                error("Must not start any transport in offline mode")
            }
            assertEquals(listOf(EngineEvent.Offline), router.compare(request(profile("a"))).toList())
        }
    }

    @Test
    fun enablingOfflineCancelsAnInFlightStreamAndDoesNotRetry() = testRunning {
        val prefs = Preferences(initial = false)
        val connected = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val router = router(prefs) { _, _, _ ->
            flow {
                try {
                    check(connected.complete(Unit)) { "Do not replay a prompt" }
                    awaitCancellation()
                } finally {
                    stopped.complete(Unit)
                }
            }
        }
        val run = async { router.compare(request(profile("active"))).toList() }
        connected.await()
        prefs.setOfflineMode(true)
        stopped.await()
        prefs.setOfflineMode(false)
        assertEquals(listOf(EngineEvent.Offline), run.await())
    }

    @Test
    fun deletedOrEditedEngineDoesNotSendToOldDestination() = testRunning {
        val router = router(credentials = { throw InputRejected(InputIssue.RECORD_NOT_FOUND) }) { _, _, _ ->
            error("Do not contact a stale profile")
        }
        val events = router.compare(request(profile("old"))).toList()
        assertEquals(listOf(EngineEvent.Failed("old", EngineProblem.CONFIGURATION), EngineEvent.Finished), events)
    }

    @Test
    fun capsOutputAndDoesNotDiscardEarlierChunks() = testRunning {
        val router = router { _, _, _ ->
            flow {
                emit("بخش اول")
                emit("x".repeat(ComparisonLimits.ANSWER_CHARS))
            }
        }
        val events = router.compare(request(profile("long"))).toList()
        assertEquals("بخش اول", events.filterIsInstance<EngineEvent.Text>().single().chunk.value)
        assertTrue(EngineEvent.Failed("long", EngineProblem.OUTPUT_LIMIT) in events)
    }

    @Test
    fun disallowsBlankPromptEmptySelectionAndDuplicateTargets() {
        val target = profile("a")
        assertNull(ComparisonRequest.create("  ", listOf(target)))
        assertNull(ComparisonRequest.create("سؤال", emptyList()))
        assertNull(ComparisonRequest.create("سؤال", listOf(target, target)))
        assertNull(ComparisonRequest.create("سؤال", (1..4).map { profile("p$it") }))
        assertNull(ComparisonRequest.create("x".repeat(ComparisonLimits.PROMPT_CHARS + 1), listOf(target)))
    }

    private fun request(vararg profiles: ProviderProfile) = checkNotNull(ComparisonRequest.create("پرسش", profiles.toList()))

    private fun profile(id: String) = ProviderProfile(
        id,
        ProviderConfig.create(id, ProviderProtocol.OPENAI_COMPATIBLE, ProviderNetwork.INTERNET,
            "https://$id.example.com/v1", "test-model"),
        hasCredential = true,
    )

    private fun router(
        prefs: PrivacyPreferences = Preferences(initial = false),
        credentials: suspend (ProviderProfile) -> ApiCredential? = { ApiCredential.parse("test-key") },
        stream: (ProviderConfig, ApiCredential?, String) -> Flow<String>,
    ): DanaAiRouter = DanaAiRouter(prefs, object : ProviderRepository {
        override val profiles: Flow<List<ProviderProfile>> = emptyFlow()
        override suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String = error("Unexpected write")
        override suspend fun delete(id: String) = error("Unexpected delete")
        override suspend fun credentialFor(profile: ProviderProfile) = credentials(profile)
    }, object : EngineTransport {
        override fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput) = stream(config, key, input.prompt).map { EngineText(it) }
    })

    private fun testRunning(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        runBlocking { withTimeout(5_000, block) }
    }

    private class Preferences(initial: Boolean) : PrivacyPreferences {
        override val offlineMode = MutableStateFlow(initial)
        override suspend fun setOfflineMode(enabled: Boolean) { offlineMode.value = enabled }
    }
}
