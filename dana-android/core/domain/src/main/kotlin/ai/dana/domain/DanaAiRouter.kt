package ai.dana.domain

import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

object ComparisonLimits {
    const val ENGINE_COUNT = 3
    const val PROMPT_CHARS = 6_000
    const val ANSWER_CHARS = 18_000
}

enum class AssistantTask { ASK, SUMMARIZE, TRANSLATE, DRAFT }

/** Created only from the prompt and explicit destination selection reviewed by the user. */
class ComparisonRequest private constructor(val prompt: String, val targets: List<ProviderProfile>, val task: AssistantTask) {
    companion object {
        fun create(prompt: String, targets: List<ProviderProfile>, task: AssistantTask = AssistantTask.ASK): ComparisonRequest? {
            val clean = prompt.trim()
            if (clean.length !in 1..ComparisonLimits.PROMPT_CHARS ||
                targets.size !in 1..ComparisonLimits.ENGINE_COUNT ||
                targets.map { it.id }.toSet().size != targets.size
            ) return null
            return ComparisonRequest(clean, targets.toList(), task)
        }
    }
}

enum class EngineProblem {
    OFFLINE,
    CONFIGURATION,
    CREDENTIAL_STORE,
    AUTHENTICATION,
    PAYMENT_REQUIRED,
    FORBIDDEN,
    RATE_LIMIT,
    NETWORK,
    TIMEOUT,
    TLS,
    SERVER,
    PROTOCOL,
    FILTERED,
    ENGINE_UNAVAILABLE,
    UNSUPPORTED_LANGUAGE,
    CONTEXT_LIMIT,
    NO_TEXT,
    OUTPUT_LIMIT,
}

class EngineFailure(val problem: EngineProblem, val httpStatus: Int? = null) : Exception(problem.name)

/** Apple sends full snapshots. Gemini and OpenAI send deltas. Never append a snapshot. */
data class EngineText(val value: String, val replaces: Boolean = false)

interface EngineTransport {
    fun stream(config: ProviderConfig, key: ApiCredential?, input: ConversationInput): Flow<EngineText>

    fun stream(config: ProviderConfig, key: ApiCredential?, prompt: String, task: AssistantTask = AssistantTask.ASK): Flow<EngineText> =
        stream(config, key, ConversationInput.single(prompt, task))
}

sealed interface EngineEvent {
    data class Started(val profileId: String) : EngineEvent
    class Text(val profileId: String, val chunk: EngineText, val firstTextMillis: Long?) : EngineEvent
    data class Complete(val profileId: String, val elapsedMillis: Long) : EngineEvent
    data class Failed(val profileId: String, val problem: EngineProblem, val httpStatus: Int? = null) : EngineEvent
    data object Offline : EngineEvent
    data object Finished : EngineEvent
}

/** Bounded fan-out. A failed engine never reroutes the prompt to an unapproved destination. */
class DanaAiRouter(
    private val preferences: PrivacyPreferences,
    private val providers: ProviderRepository,
    private val transport: EngineTransport,
) {
    fun compare(request: ComparisonRequest): Flow<EngineEvent> = whenOnline {
        parallel(ConversationInput.single(request.prompt, request.task), request.targets)
    }

    fun chat(route: ChatRoute): Flow<EngineEvent> = whenOnline {
        if (route.parallel) parallel(route.input, route.candidates) else flow {
            for (profile in route.candidates) {
                emit(EngineEvent.Started(profile.id))
                var textReceived = false
                var failure: EngineProblem? = null
                runEngine(profile, route.input) { event ->
                    if (event is EngineEvent.Text) textReceived = true
                    if (event is EngineEvent.Failed) failure = event.problem
                    emit(event)
                }
                // Never switch on policy/safety refusals, after a visible/partial response, or off the approved route.
                if (textReceived || failure !in FALLBACK_PROBLEMS) break
            }
            emit(EngineEvent.Finished)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun whenOnline(operation: () -> Flow<EngineEvent>): Flow<EngineEvent> = preferences.offlineMode
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
            if (offline) flowOf(EngineEvent.Offline) else operation()
        }
        .buffer(0)
        .transformWhile { event ->
            emit(event)
            event != EngineEvent.Offline && event != EngineEvent.Finished
        }

    private fun parallel(input: ConversationInput, targets: List<ProviderProfile>): Flow<EngineEvent> = channelFlow {
        coroutineScope {
            targets.forEach { profile ->
                launch { runEngine(profile, input) { send(it) } }
            }
        }
        send(EngineEvent.Finished)
    }

    private suspend fun runEngine(
        profile: ProviderProfile,
        input: ConversationInput,
        emit: suspend (EngineEvent) -> Unit,
    ) {
        try {
            // A removed or changed snapshot must fail before any request is created.
            val key = providers.credentialFor(profile)
            if (!profile.config.endpoint.acceptsCredential(key != null)) throw EngineFailure(EngineProblem.AUTHENTICATION)
            val started = TimeSource.Monotonic.markNow()
            var length = 0
            var hasVisibleText = false
            var firstTextMillis: Long? = null
            transport.stream(profile.config, key, input).collect { chunk ->
                val previouslyWritten = if (chunk.replaces) 0 else length
                if (chunk.value.length > ComparisonLimits.ANSWER_CHARS - previouslyWritten) {
                    throw EngineFailure(EngineProblem.OUTPUT_LIMIT)
                }
                length = previouslyWritten + chunk.value.length
                val visible = chunk.value.isNotBlank()
                hasVisibleText = visible || (!chunk.replaces && hasVisibleText)
                if (visible && firstTextMillis == null) firstTextMillis = started.elapsedNow().inWholeMilliseconds
                if (chunk.replaces || chunk.value.isNotEmpty()) emit(EngineEvent.Text(profile.id, chunk, firstTextMillis))
            }
            if (!hasVisibleText) throw EngineFailure(EngineProblem.NO_TEXT)
            emit(EngineEvent.Complete(profile.id, started.elapsedNow().inWholeMilliseconds))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val problem = when (error) {
                is EngineFailure -> error.problem
                is CredentialStoreUnavailable -> EngineProblem.CREDENTIAL_STORE
                is InputRejected -> EngineProblem.CONFIGURATION
                else -> EngineProblem.SERVER
            }
            emit(EngineEvent.Failed(profile.id, problem, (error as? EngineFailure)?.httpStatus))
        }
    }

    private companion object {
        val FALLBACK_PROBLEMS = setOf(
            EngineProblem.RATE_LIMIT, EngineProblem.FORBIDDEN, EngineProblem.NETWORK,
            EngineProblem.TIMEOUT, EngineProblem.SERVER, EngineProblem.ENGINE_UNAVAILABLE,
        )
    }

}
