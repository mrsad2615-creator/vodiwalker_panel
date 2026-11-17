package ai.dana.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

object ComparisonLimits {
    const val ENGINE_COUNT = 3
    const val PROMPT_CHARS = 6_000
    const val ANSWER_CHARS = 18_000
}

/** Created only from the prompt and explicit destination selection reviewed by the user. */
class ComparisonRequest private constructor(val prompt: String, val targets: List<ProviderProfile>) {
    companion object {
        fun create(prompt: String, targets: List<ProviderProfile>): ComparisonRequest? {
            val clean = prompt.trim()
            if (clean.length !in 1..ComparisonLimits.PROMPT_CHARS ||
                targets.size !in 1..ComparisonLimits.ENGINE_COUNT ||
                targets.map { it.id }.toSet().size != targets.size
            ) return null
            return ComparisonRequest(clean, targets.toList())
        }
    }
}

enum class EngineProblem {
    OFFLINE,
    CONFIGURATION,
    CREDENTIAL_STORE,
    AUTHENTICATION,
    FORBIDDEN,
    RATE_LIMIT,
    NETWORK,
    TIMEOUT,
    TLS,
    SERVER,
    PROTOCOL,
    FILTERED,
    NO_TEXT,
    OUTPUT_LIMIT,
}

class EngineFailure(val problem: EngineProblem) : Exception(problem.name)

interface EngineTransport {
    fun stream(config: ProviderConfig, key: ApiCredential?, prompt: String): Flow<String>
}

sealed interface EngineEvent {
    class Text(val profileId: String, val delta: String) : EngineEvent
    data class Complete(val profileId: String) : EngineEvent
    data class Failed(val profileId: String, val problem: EngineProblem) : EngineEvent
    data object Offline : EngineEvent
    data object Finished : EngineEvent
}

/** Bounded fan-out. A failed engine never reroutes the prompt to an unapproved destination. */
class DanaAiRouter(
    private val preferences: PrivacyPreferences,
    private val providers: ProviderRepository,
    private val transport: EngineTransport,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun compare(request: ComparisonRequest): Flow<EngineEvent> = preferences.offlineMode
        .catch { error ->
            if (error is CancellationException || error !is Exception) throw error
            emit(true) // Fail closed if we cannot read the privacy setting.
        }
        .distinctUntilChanged()
        .transformWhile { offline ->
            emit(offline)
            !offline // Once blocked, never replay the approved prompt on a later online transition.
        }
        .flatMapLatest { offline ->
            if (offline) flowOf(EngineEvent.Offline) else parallel(request)
        }
        .buffer(0)
        .transformWhile { event ->
            emit(event)
            event != EngineEvent.Offline && event != EngineEvent.Finished
        }

    private fun parallel(request: ComparisonRequest): Flow<EngineEvent> = channelFlow {
        coroutineScope {
            request.targets.forEach { profile ->
                launch { runEngine(profile, request.prompt) { send(it) } }
            }
        }
        send(EngineEvent.Finished)
    }

    private suspend fun runEngine(
        profile: ProviderProfile,
        prompt: String,
        emit: suspend (EngineEvent) -> Unit,
    ) {
        try {
            // A removed or changed snapshot must fail before any request is created.
            val key = providers.credentialFor(profile)
            var length = 0
            var hasVisibleText = false
            transport.stream(profile.config, key, prompt).collect { delta ->
                if (delta.length > ComparisonLimits.ANSWER_CHARS - length) throw EngineFailure(EngineProblem.OUTPUT_LIMIT)
                length += delta.length
                hasVisibleText = hasVisibleText || delta.isNotBlank()
                if (delta.isNotEmpty()) emit(EngineEvent.Text(profile.id, delta))
            }
            if (!hasVisibleText) throw EngineFailure(EngineProblem.NO_TEXT)
            emit(EngineEvent.Complete(profile.id))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val problem = when (error) {
                is EngineFailure -> error.problem
                is CredentialStoreUnavailable -> EngineProblem.CREDENTIAL_STORE
                is InputRejected -> EngineProblem.CONFIGURATION
                else -> EngineProblem.SERVER
            }
            emit(EngineEvent.Failed(profile.id, problem))
        }
    }
}
