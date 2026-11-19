package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ArtRequest
import ai.dana.domain.ArtShape
import ai.dana.domain.AssistantStyle
import ai.dana.domain.AssistantTask
import ai.dana.domain.ContextMessage
import ai.dana.domain.ConversationInput
import ai.dana.domain.EmailAccounts
import ai.dana.domain.ManagedCatalog
import ai.dana.domain.ManagedCloud
import ai.dana.domain.ManagedFailure
import ai.dana.domain.ManagedKind
import ai.dana.domain.ManagedModel
import ai.dana.domain.ManagedProblem
import ai.dana.domain.MessageRole
import ai.dana.domain.PrivacyPreferences
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class HostedTurn(val question: String, val answer: String, val model: ManagedModel)

internal class HostedApproval(
    val model: ManagedModel,
    val prompt: String,
    val input: ConversationInput?,
    val shape: ArtShape,
    val expiresAt: Long,
    val id: String = UUID.randomUUID().toString(),
) {
    val outbound get() = input?.prompt ?: prompt
}

internal data class ManagedWorkspaceState(
    val resumed: Boolean = false,
    val offline: Boolean = true,
    val loading: Boolean = false,
    val catalog: ManagedCatalog? = null,
    val catalogError: ManagedProblem? = null,
    val error: Int? = null,
    val chatDraft: String = "",
    val imageDraft: String = "",
    val chatModelId: String? = null,
    val imageModelId: String? = null,
    val shape: ArtShape = ArtShape.SQUARE,
    val turns: List<HostedTurn> = emptyList(),
    val preview: ArtPreview? = null,
    val approval: HostedApproval? = null,
    val running: HostedApproval? = null,
) {
    fun available(kind: ManagedKind) = catalog?.models.orEmpty().filter { it.kind == kind }
    fun selected(kind: ManagedKind): ManagedModel? {
        val choices = available(kind)
        val selected = if (kind == ManagedKind.CHAT) chatModelId else imageModelId
        // An explicit selection is never silently replaced if the service removes it.
        return if (selected == null) choices.firstOrNull() else choices.firstOrNull { it.id == selected }
    }
    override fun toString() = "ManagedWorkspaceState(online=${!offline}, resumed=$resumed)"
}

@HiltViewModel
internal class ManagedWorkspaceViewModel @Inject constructor(
    private val cloud: ManagedCloud,
    accounts: EmailAccounts,
    privacy: PrivacyPreferences,
) : ViewModel() {
    val configured = cloud.configured
    private val mutable = MutableStateFlow(ManagedWorkspaceState())
    val state = mutable.asStateFlow()
    private val reload = MutableStateFlow(0L)
    private var work: Job? = null

    init {
        if (configured) viewModelScope.launch {
            val known = accounts.setup.map { it.canResume }
                .catch { error -> if (error is CancellationException || error !is Exception) throw error else emit(false) }
                .distinctUntilChanged()
            val offline = privacy.offlineMode
                .catch { error -> if (error is CancellationException || error !is Exception) throw error else emit(true) }
                .distinctUntilChanged()
            combine(known, offline, reload) { active, blocked, revision -> Triple(active, blocked, revision) }.collectLatest { (resumed, onDevice, _) ->
                if (!resumed || onDevice) {
                    stop()
                    if (!resumed) mutable.value = ManagedWorkspaceState(offline = onDevice)
                    else mutable.update { it.copy(resumed = true, offline = true, catalog = null, loading = false) }
                } else {
                    mutable.update { it.copy(resumed = true, offline = false, loading = true, catalogError = null) }
                    try {
                        val catalog = cloud.catalog()
                        mutable.update { it.copy(catalog = catalog, loading = false,
                            approval = it.approval?.takeIf { plan -> plan.model in catalog.models && plan.model.units <= catalog.dailyRemaining }) }
                    } catch (canceled: CancellationException) {
                        throw canceled
                    } catch (error: Exception) {
                        mutable.update { it.copy(loading = false, catalog = null, approval = null,
                            catalogError = (error as? ManagedFailure)?.problem ?: ManagedProblem.TRANSPORT) }
                    }
                }
            }
        }
    }

    @MainThread fun refresh() { reload.update { it + 1 } }
    @MainThread fun draft(kind: ManagedKind, value: String) {
        val limit = if (kind == ManagedKind.CHAT) ManagedCloud.CHAT_PROMPT_LIMIT else ArtRequest.PROMPT_LIMIT
        if (value.length <= limit && mutable.value.running == null) mutable.update {
            if (kind == ManagedKind.CHAT) it.copy(chatDraft = value, approval = null, error = null)
            else it.copy(imageDraft = value, approval = null, error = null)
        }
    }
    @MainThread fun select(model: ManagedModel) {
        if (model !in mutable.value.available(model.kind) || mutable.value.running != null) return
        mutable.update {
            if (model.kind == ManagedKind.CHAT) it.copy(chatModelId = model.id, approval = null, error = null)
            else it.copy(imageModelId = model.id, approval = null, error = null)
        }
    }
    @MainThread fun shape(shape: ArtShape) {
        if (mutable.value.running == null) mutable.update { it.copy(shape = shape, approval = null, error = null) }
    }
    @MainThread fun newChat() {
        if (mutable.value.running == null) mutable.update { it.copy(chatDraft = "", turns = emptyList(), approval = null, error = null) }
    }
    @MainThread fun propose(kind: ManagedKind) {
        val state = mutable.value
        val model = state.selected(kind) ?: return
        if (state.offline || !state.resumed || state.running != null || state.approval != null || state.loading ||
            model.units > (state.catalog?.dailyRemaining ?: 0)) return
        val raw = (if (kind == ManagedKind.CHAT) state.chatDraft else state.imageDraft).trim().takeIf(String::isNotBlank) ?: return
        val input = if (kind == ManagedKind.CHAT) {
            val recent = state.turns.takeLast(3).flatMap { listOf(ContextMessage(MessageRole.USER, it.question), ContextMessage(MessageRole.ASSISTANT, it.answer)) }
            ConversationInput.prepare(raw, recent, emptyList(), AssistantStyle.BALANCED, AssistantTask.ASK, null, mask = true)
                ?: return
        } else null
        mutable.update { it.copy(approval = HostedApproval(model, raw, input, state.shape, SystemClock.elapsedRealtime() + 60_000L), error = null) }
    }
    @MainThread fun dismiss() { mutable.update { it.copy(approval = null) } }

    @MainThread fun approve(plan: HostedApproval) {
        val state = mutable.value
        if (state.approval !== plan || state.offline || !state.resumed || state.loading || state.running != null ||
            plan.model !in state.available(plan.model.kind)) return
        dismiss()
        if (SystemClock.elapsedRealtime() >= plan.expiresAt) {
            mutable.update { it.copy(error = R.string.managed_approval_expired) }
            return
        }
        mutable.update { it.copy(running = plan, error = null) }
        work = viewModelScope.launch {
            try {
                val started = TimeSource.Monotonic.markNow()
                val input = plan.input
                if (input != null) {
                    val text = cloud.chat(plan.model, input, plan.id)
                    if (mutable.value.running === plan) mutable.update {
                        it.copy(turns = (it.turns + HostedTurn(input.prompt, text, plan.model)).takeLast(24), chatDraft = "")
                    }
                } else {
                    val image = cloud.image(plan.model, plan.prompt, plan.shape, plan.id)
                    val preview = withContext(Dispatchers.Default) {
                        decodeArtPreview(image, "${plan.model.provider} · ${plan.model.title}", started.elapsedNow().inWholeMilliseconds)
                    }
                    if (mutable.value.running === plan) mutable.update { it.copy(preview = preview, imageDraft = "") }
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                if (mutable.value.running === plan) mutable.update {
                    it.copy(error = (error as? ManagedFailure)?.problem?.let(::managedError) ?: DanaErrorHandler.messageFor(error))
                }
            } finally {
                if (mutable.value.running === plan) mutable.update { it.copy(running = null) }
                refresh() // The service can charge an upstream attempt even if we stop waiting.
            }
        }
    }

    @MainThread fun stop() {
        mutable.update { it.copy(approval = null, running = null) }
        work?.cancel()
        work = null
    }
}

internal fun managedError(problem: ManagedProblem): Int = when (problem) {
    ManagedProblem.SIGN_IN -> R.string.managed_login_again
    ManagedProblem.NOT_ENABLED -> R.string.managed_not_enabled
    ManagedProblem.QUOTA -> R.string.managed_quota
    ManagedProblem.BUSY -> R.string.managed_busy
    ManagedProblem.DUPLICATE -> R.string.managed_duplicate
    ManagedProblem.MODEL_UNAVAILABLE -> R.string.managed_model_gone
    ManagedProblem.OFFLINE -> R.string.managed_offline
    ManagedProblem.TRANSPORT -> R.string.managed_network_error
    ManagedProblem.RESPONSE -> R.string.error_engine_protocol
}
