package ai.dana.domain

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineDiscoveryTest {
    @Test
    fun strictOfflineAndUnknownPrivacyNeverReadASecretOrMakeARequest() = runBlocking {
        val repo = Repository()
        val directory = directory { _, _ -> error("No remote work is allowed") }
        val broken = flow<Boolean> { throw IOException("Could not read privacy preferences") }
        for (mode in listOf(flowOf(true), broken)) {
            expect(EngineProblem.OFFLINE) { discovery(mode, repo, directory).models(profile.config.endpoint, profile, CredentialChange.Keep) }
        }
        assertEquals(0, repo.vaultReads)
    }

    @Test
    fun editingDestinationDoesNotExfiltrateTheExistingCredential() = runBlocking {
        val repo = Repository()
        val changed = ProviderEndpoint.create(ProviderProtocol.GEMINI_NATIVE, ProviderNetwork.INTERNET, "https://proxy.example/v1beta")
        val service = discovery(flowOf(false), repo, directory { _, _ -> error("No request with a stored key on a changed destination") })
        try {
            service.models(changed, profile, CredentialChange.Keep)
            throw AssertionError("Expected rejected destination")
        } catch (rejected: InputRejected) {
            assertEquals(InputIssue.CREDENTIAL_DESTINATION_CHANGED, rejected.issue)
        }
        assertEquals(0, repo.vaultReads)
    }

    @Test
    fun validLookupReturnsOnlyCatalogAndMeasuresElapsedTime() = runBlocking {
        val repo = Repository()
        val service = discovery(flowOf(false), repo, directory { endpoint, key ->
            assertEquals(profile.config.endpoint, endpoint)
            assertSame(credential, key)
            listOf(DirectoryModel("models/found", "Found"))
        })
        val result = service.models(profile.config.endpoint, profile, CredentialChange.Keep)
        assertEquals(listOf(DirectoryModel("models/found", "Found")), result.models)
        assertTrue(result.elapsedMillis >= 0)
        assertEquals(1, repo.vaultReads)
    }

    @Test
    fun switchingOfflineCancelsAnInFlightListingWithoutReopeningIt() = runBlocking {
        withTimeout(4_000) {
            val mode = MutableStateFlow(false)
            val requested = CompletableDeferred<Unit>()
            val canceled = CompletableDeferred<Unit>()
            var calls = 0
            val service = discovery(mode, Repository(), directory { _, _ ->
                calls++
                requested.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    canceled.complete(Unit)
                }
            })
            val listing = async {
                expect(EngineProblem.OFFLINE) { service.models(profile.config.endpoint, null, CredentialChange.Replace(credential)) }
            }
            requested.await()
            mode.value = true
            listing.await()
            canceled.await()
            mode.value = false
            assertEquals(1, calls)
        }
    }

    @Test
    fun appleProfilesAreHttpsLanAndBoundToDocumentedModel() {
        assertThrows(InputRejected::class.java) {
            ProviderEndpoint.create(ProviderProtocol.APPLE_MAC, ProviderNetwork.INTERNET, "https://mac.example/v1")
        }
        assertThrows(InputRejected::class.java) {
            ProviderEndpoint.create(ProviderProtocol.APPLE_MAC, ProviderNetwork.LOCAL_NETWORK, "http://mac.example/v1")
        }
        assertThrows(InputRejected::class.java) {
            ProviderConfig.create("Mac", ProviderProtocol.APPLE_MAC, ProviderNetwork.LOCAL_NETWORK, "https://mac.example/v1", "fake-model")
        }
        val valid = ProviderConfig.create("Mac", ProviderProtocol.APPLE_MAC, ProviderNetwork.LOCAL_NETWORK, "https://mac.example/v1", APPLE_MAC_MODEL)
        assertEquals(valid.credentialScope, valid.endpoint.credentialScope)
    }

    private suspend fun expect(problem: EngineProblem, operation: suspend () -> Unit) {
        try {
            operation()
            throw AssertionError("Expected $problem")
        } catch (error: EngineFailure) {
            assertEquals(problem, error.problem)
        }
    }

    private fun directory(operation: suspend (ProviderEndpoint, ApiCredential?) -> List<DirectoryModel>) = object : ModelDirectory {
        override suspend fun list(endpoint: ProviderEndpoint, key: ApiCredential?) = operation(endpoint, key)
    }

    private fun discovery(mode: Flow<Boolean>, repo: ProviderRepository, directory: ModelDirectory): EngineDiscovery {
        val settings = object : PrivacyPreferences {
            override val offlineMode = mode
            override suspend fun setOfflineMode(enabled: Boolean) = error("Test privacy mode is controlled directly")
        }
        return EngineDiscovery(settings, repo, directory)
    }

    private class Repository : ProviderRepository {
        override val profiles = flowOf(listOf(profile))
        var vaultReads = 0
        override suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String = error("Unexpected write")
        override suspend fun delete(id: String) = error("Unexpected delete")
        override suspend fun credentialFor(profile: ProviderProfile): ApiCredential {
            vaultReads++
            assertEquals(Companion.profile, profile)
            return credential
        }
    }

    companion object {
        private val credential = ApiCredential.parse("test-credential")
        private val profile = ProviderProfile("saved", ProviderConfig.create("Gemini", ProviderProtocol.GEMINI_NATIVE,
            ProviderNetwork.INTERNET, GOOGLE_GEMINI_BASE_URL, "models/listed"), hasCredential = true)
    }
}
