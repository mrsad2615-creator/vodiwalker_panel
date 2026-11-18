package ai.dana.domain

import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.transformWhile

/** Model IDs returned by the selected provider, never presented as a fallback guess. */
data class DirectoryModel(val id: String, val label: String, val photoSupport: PhotoSupport = PhotoSupport.UNKNOWN)

data class DirectoryResult(val models: List<DirectoryModel>, val elapsedMillis: Long)

interface ModelDirectory {
    suspend fun list(endpoint: ProviderEndpoint, key: ApiCredential?): List<DirectoryModel>
}

/** The key is typed in the app or read from the vault only after destination and offline checks. */
class EngineDiscovery(
    private val preferences: PrivacyPreferences,
    private val profiles: ProviderRepository,
    private val directory: ModelDirectory,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun models(
        endpoint: ProviderEndpoint,
        previous: ProviderProfile?,
        keyChange: CredentialChange,
    ): DirectoryResult = preferences.offlineMode
        .catch { error ->
            if (error is CancellationException || error !is Exception) throw error
            emit(true)
        }
        .distinctUntilChanged()
        .transformWhile { offline ->
            emit(offline)
            !offline
        }
        .transformLatest { offline ->
            if (offline) throw EngineFailure(EngineProblem.OFFLINE)
            keyChange.validateForLookup(previous, endpoint)
            val key = when (keyChange) {
                CredentialChange.Keep -> previous?.takeIf { it.hasCredential }?.let { profiles.credentialFor(it) }
                CredentialChange.Remove -> null
                is CredentialChange.Replace -> keyChange.credential
            }
            val started = TimeSource.Monotonic.markNow()
            emit(DirectoryResult(directory.list(endpoint, key), started.elapsedNow().inWholeMilliseconds))
        }
        .first()
}
