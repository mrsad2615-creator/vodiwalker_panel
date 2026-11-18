package ai.dana.ui

import ai.dana.R
import ai.dana.domain.DanaAiRouter
import ai.dana.domain.EngineEvent
import ai.dana.domain.EngineFailure
import ai.dana.domain.EngineProblem
import ai.dana.domain.MessageReviewPlan
import ai.dana.domain.MessageSignals
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderRepository
import ai.dana.domain.ReviewMessage
import ai.dana.localai.DeviceModel
import ai.dana.localai.DeviceModelProblem
import ai.dana.localai.DeviceModels
import ai.dana.localai.DeviceText
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class MessageReviewState(
    val draft: String = "",
    val revision: Long = 0,
    val pending: MessageReviewPlan? = null,
    val signals: MessageSignals? = null,
    val result: MessageAnalysis? = null,
    val active: String? = null,
    val phase: Int? = null,
    val notice: Int? = null,
    val inputProblem: Int? = null,
    val analysisProblem: Int? = null,
) {
    override fun toString(): String = "MessageReviewState(revision=$revision, active=${active != null})"
}

internal class MessageAnalysis(val engine: String, val text: String)

@HiltViewModel
internal class MessageReviewViewModel @Inject constructor(
    preferences: PrivacyPreferences,
    providers: ProviderRepository,
    private val device: DeviceModels,
    private val router: DanaAiRouter,
) : ViewModel() {
    private val mutable = MutableStateFlow(MessageReviewState())
    val state = mutable.asStateFlow()
    val offline = preferences.offlineMode.catch { if (it is CancellationException || it !is Exception) throw it else emit(true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val profiles = providers.profiles.map { list -> list.filter(MessageReviewPlan::supports) }
        .catch { if (it is CancellationException || it !is Exception) throw it else emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val localReady = MutableStateFlow(false)
    private var operation: Job? = null
    private var ticket = 0L

    init {
        viewModelScope.launch { localReady.value = DeviceModel.QWEN in device.available() }
        viewModelScope.launch { offline.collect { if (it) dismiss() } }
    }

    /** New shares replace the previous review and revoke its approval, even if it was running. */
    @MainThread fun receive(raw: CharSequence?) {
        stop()
        val message = raw?.let { ReviewMessage.create(it) }
        mutable.value = MessageReviewState(draft = message?.text.orEmpty(), revision = mutable.value.revision + 1,
            inputProblem = if (message == null) inputError(raw) else null)
    }

    @MainThread fun paste(raw: CharSequence?) {
        if (blocked()) return
        val message = raw?.let { ReviewMessage.create(it) }
        if (message == null) mutable.update { it.copy(inputProblem = inputError(raw)) } else receive(message.text)
    }

    @MainThread fun edit(raw: String) {
        if (blocked()) return
        if (!ReviewMessage.acceptsDraft(raw)) {
            mutable.update { it.copy(inputProblem = inputError(raw)) }
        } else mutable.update { it.copy(draft = raw, signals = null, result = null, inputProblem = null, analysisProblem = null, notice = null) }
    }

    @MainThread fun clear() {
        stop()
        mutable.value = MessageReviewState(revision = mutable.value.revision + 1)
    }

    @MainThread fun local() {
        val message = currentMessage() ?: return
        val notice = when {
            !DeviceModels.BUNDLED -> R.string.message_compat_explain
            !message.fitsLocalModel -> R.string.message_model_limit
            !localReady.value -> R.string.message_model_unavailable
            else -> null
        }
        mutable.update { it.copy(signals = message.signals(), result = null, notice = notice, analysisProblem = null) }
        if (notice != null) return
        run(DeviceModel.QWEN.title, R.string.models_preparing) { runId ->
            val answer = StringBuilder()
            withTimeoutOrNull(160_000L) {
                device.review(message).collect { event ->
                    when (event) {
                        DeviceText.Preparing -> Unit
                        DeviceText.Generating -> if (runId == ticket) mutable.update { it.copy(phase = R.string.message_analyzing) }
                        is DeviceText.Delta -> { answer.append(event.value) }
                    }
                }
            } ?: throw EngineFailure(EngineProblem.TIMEOUT)
            answer.toString()
        }
    }

    @MainThread fun reviewOnline(profile: ProviderProfile) {
        val message = currentMessage() ?: return
        if (offline.value || profile !in profiles.value) return
        val plan = MessageReviewPlan.create(profile, message) ?: return
        mutable.update { it.copy(pending = plan) }
    }

    @MainThread fun dismiss() { mutable.update { it.copy(pending = null) } }

    @MainThread fun approve(plan: MessageReviewPlan) {
        if (mutable.value.pending !== plan || offline.value || plan.profile !in profiles.value) {
            if (mutable.value.pending === plan) dismiss()
            return
        }
        val config = plan.profile.config
        run("${config.name} · ${config.model}", R.string.message_analyzing) {
            val answer = StringBuilder()
            router.reviewMessage(plan).collect { event ->
                when (event) {
                    is EngineEvent.Text -> {
                        if (event.chunk.replaces) answer.setLength(0)
                        answer.append(event.chunk.value)
                    }
                    is EngineEvent.Failed -> throw EngineFailure(event.problem, event.httpStatus)
                    EngineEvent.Offline -> throw EngineFailure(EngineProblem.OFFLINE)
                    else -> Unit
                }
            }
            answer.toString()
        }
    }

    @MainThread fun stop() {
        val wasRunning = mutable.value.active != null
        ++ticket
        operation?.cancel()
        operation = null
        mutable.update { it.copy(active = null, phase = null, pending = null,
            notice = if (wasRunning) R.string.compare_stopped else it.notice) }
    }

    private fun run(engine: String, phase: Int, analyze: suspend (Long) -> String) {
        val runId = ++ticket
        mutable.update { it.copy(active = engine, phase = phase, pending = null, result = null, notice = null, inputProblem = null, analysisProblem = null) }
        operation = viewModelScope.launch {
            try {
                val answer = analyze(runId).trim()
                if (answer.isEmpty()) throw EngineFailure(EngineProblem.NO_TEXT)
                if (runId == ticket) mutable.update { it.copy(result = MessageAnalysis(engine, answer)) }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                val message = (error as? DeviceModelProblem)?.messageRes ?: DanaErrorHandler.messageFor(error)
                if (runId == ticket) mutable.update { it.copy(analysisProblem = message) }
            } finally {
                if (runId == ticket) mutable.update { it.copy(active = null, phase = null) }
            }
        }
    }

    private fun blocked(): Boolean = mutable.value.let { it.active != null || it.pending != null }
    private fun currentMessage(): ReviewMessage? = if (blocked()) null else ReviewMessage.create(mutable.value.draft)
    private fun inputError(raw: CharSequence?): Int = when {
        raw == null || raw.isEmpty() -> R.string.message_need_text
        raw.length > ReviewMessage.MAX_CHARS -> R.string.message_too_long
        !ReviewMessage.acceptsDraft(raw) -> R.string.message_invalid_text
        else -> R.string.message_need_text
    }
}
