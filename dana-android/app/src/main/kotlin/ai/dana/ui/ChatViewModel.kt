package ai.dana.ui

import ai.dana.R
import ai.dana.domain.AssistantStyle
import ai.dana.domain.AssistantTask
import ai.dana.domain.ChatLimits
import ai.dana.domain.ChatRoute
import ai.dana.domain.ComparisonLimits
import ai.dana.domain.ContextMessage
import ai.dana.domain.Conversation
import ai.dana.domain.ConversationInput
import ai.dana.domain.DanaAiRouter
import ai.dana.domain.DirectSendSettings
import ai.dana.domain.SendGrant
import ai.dana.domain.EngineEvent
import ai.dana.domain.EngineProblem
import ai.dana.domain.HistoryRepository
import ai.dana.domain.LocalAction
import ai.dana.domain.LocalCommands
import ai.dana.domain.LocalText
import ai.dana.domain.MemoryRepository
import ai.dana.domain.MessageRole
import ai.dana.domain.NotesRepository
import ai.dana.domain.ProviderProfile
import ai.dana.domain.UserMemory
import ai.dana.domain.VisionPhoto
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ChatPage { CONVERSATION, HISTORY, MEMORY, TOOLS }

internal data class ChatOptions(
    val autoHistory: Boolean = false,
    val includeContext: Boolean = true,
    val useMemory: Boolean = false,
    val mask: Boolean = true,
    val readReplies: Boolean = false,
    val style: AssistantStyle = AssistantStyle.BALANCED,
)

internal data class ChatAttempt(val name: String, val problem: EngineProblem, val status: Int?)

internal data class ChatTurn(
    val index: Long,
    val question: String,
    val answer: String = "",
    val engine: String? = null,
    val firstMillis: Long? = null,
    val elapsedMillis: Long? = null,
    val complete: Boolean = false,
    val interrupted: Boolean = false,
    val includedImage: Boolean = false,
    val readWhenDone: Boolean = false,
    val errors: List<ChatAttempt> = emptyList(),
)

internal data class TeamTurn(
    val index: Long,
    val question: String,
    val includedImage: Boolean,
    val historyId: Long?,
    val store: Boolean,
    val readWhenPicked: Boolean,
    val comparison: ComparisonState,
)

internal sealed interface ChatDialog {
    data object Options : ChatDialog
    data object PickTeam : ChatDialog
    class Send(val route: ChatRoute, val question: String, val historyId: Long?, val store: Boolean, val grant: SendGrant) : ChatDialog
    data class Action(val command: LocalAction, val fromText: Boolean) : ChatDialog
    data class EditMemory(val previous: UserMemory?, val draft: String = previous?.fact.orEmpty()) : ChatDialog
    data class RemoveMemory(val id: Long?) : ChatDialog
    data class RenameConversation(val item: Conversation) : ChatDialog
    data class RemoveConversation(val id: Long?) : ChatDialog
}

internal data class ChatState(
    val page: ChatPage = ChatPage.CONVERSATION,
    val current: Conversation? = null,
    val draft: String = "",
    val turns: List<ChatTurn> = emptyList(),
    val running: Boolean = false,
    val writing: Boolean = false,
    val options: ChatOptions = ChatOptions(),
    val manualId: String? = null,
    val teamIds: Set<String> = emptySet(),
    val team: TeamTurn? = null,
    val localOnly: Boolean = false,
    val voiceOpen: Boolean = false,
    val task: AssistantTask = AssistantTask.ASK,
    val photo: VisionPhoto? = null,
    val dialog: ChatDialog? = null,
    @StringRes val error: Int? = null,
    @StringRes val notice: Int? = null,
    val noticePercent: Int? = null,
    val speakNotice: Boolean = false,
) {
    val busy get() = running || writing
}

@HiltViewModel
internal class ChatViewModel @Inject constructor(
    private val router: DanaAiRouter,
    private val history: HistoryRepository,
    private val memory: MemoryRepository,
    private val notes: NotesRepository,
    private val directSend: DirectSendSettings,
) : ViewModel() {
    private val mutable = MutableStateFlow(ChatState())
    val state = mutable.asStateFlow()
    private val search = MutableStateFlow("")
    val query = search.asStateFlow()
    private var generation: Job? = null
    private var preparing: Job? = null
    private var counter = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    val archive = search.flatMapLatest { q ->
        read(if (q.isBlank()) history.conversations else history.search(q))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Reading.Loading)

    val facts = read(memory.facts).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Reading.Loading)

    @MainThread
    fun page(page: ChatPage) {
        if (!mutable.value.writing) {
            stop()
            mutable.update { it.copy(page = page, dialog = null, error = null, notice = null) }
        }
    }

    @MainThread
    fun search(text: String) { search.value = text.take(LocalText.TITLE_LIMIT) }

    @MainThread
    fun draft(text: String) {
        if (!mutable.value.busy && text.length <= ComparisonLimits.PROMPT_CHARS) {
            mutable.update { it.copy(draft = text, error = null, notice = null) }
        }
    }

    @MainThread
    fun importText(text: String) {
        if (mutable.value.busy) return
        val clean = text.trim()
        if (clean.isBlank()) return
        mutable.update {
            it.copy(draft = clean.take(ComparisonLimits.PROMPT_CHARS), page = ChatPage.CONVERSATION, dialog = null,
                error = if (clean.length > ComparisonLimits.PROMPT_CHARS) R.string.chat_import_truncated else null, notice = null)
        }
    }

    @MainThread
    fun photo(photo: VisionPhoto?) {
        if (!mutable.value.busy) mutable.update { it.copy(photo = photo, page = ChatPage.CONVERSATION, error = null, dialog = null,
            localOnly = if (photo != null) false else it.localOnly) }
    }

    @MainThread
    fun engine(id: String?) {
        if (!mutable.value.busy) mutable.update { it.copy(manualId = id, teamIds = emptySet(), error = null) }
    }

    @MainThread
    fun chooseTeam(ids: Set<String>) {
        if (!mutable.value.busy && ids.size in 2..ComparisonLimits.ENGINE_COUNT) {
            mutable.update { it.copy(manualId = null, teamIds = ids.toSet(), dialog = null, error = null) }
        }
    }

    @MainThread
    fun localMode(enabled: Boolean) {
        if (!mutable.value.busy) mutable.update { it.copy(localOnly = enabled, error = null, notice = null, dialog = null) }
    }

    @MainThread
    fun toggleVoice() {
        if (!mutable.value.busy) mutable.update { it.copy(voiceOpen = !it.voiceOpen, options = it.options.copy(readReplies = !it.voiceOpen)) }
    }

    @MainThread
    fun assistant(local: Boolean = false) {
        if (!mutable.value.busy) mutable.update {
            it.copy(page = ChatPage.CONVERSATION, voiceOpen = true, localOnly = local, error = null, notice = null, dialog = null,
                options = it.options.copy(readReplies = true))
        }
    }

    @MainThread
    fun commandExample(text: String) {
        if (!mutable.value.busy) {
            importText(text)
            mutable.update { it.copy(localOnly = true, photo = null) }
        }
    }

    @MainThread
    fun speechConsumed(index: Long) = reviseTurn(index) { it.copy(readWhenDone = false) }

    @MainThread
    fun noticeSpoken() { mutable.update { it.copy(speakNotice = false) } }

    @MainThread
    fun task(value: AssistantTask) {
        if (!mutable.value.busy) mutable.update { it.copy(task = value) }
    }

    @MainThread
    fun options(value: ChatOptions) {
        if (!mutable.value.busy) mutable.update { it.copy(options = value) }
    }

    @MainThread
    fun dialog(value: ChatDialog?) {
        if (!mutable.value.busy) mutable.update { it.copy(dialog = value, error = null) }
    }

    @MainThread
    fun newConversation() {
        if (!mutable.value.busy) resetConversation(ChatPage.CONVERSATION)
    }

    @MainThread
    fun open(item: Conversation) {
        write {
            val stored = history.messages(item.id).first().map { ContextMessage(it.role, it.body) }
            val turns = stored.zipWithNext().mapNotNull { (user, answer) ->
                if (user.role == MessageRole.USER && answer.role == MessageRole.ASSISTANT) {
                    ChatTurn(++counter, user.body, answer.body, complete = true)
                } else null
            }
            resetConversation(ChatPage.CONVERSATION)
            mutable.update { it.copy(current = item, turns = turns, options = it.options.copy(autoHistory = true), writing = true) }
        }
    }

    @MainThread
    fun submit(profiles: List<ProviderProfile>, offline: Boolean, preferred: String?, execute: (LocalAction) -> DeviceOutcome) {
        val chat = mutable.value
        if (chat.busy || chat.dialog != null || chat.draft.isBlank()) return
        if (chat.photo == null) LocalCommands.parse(chat.draft)?.let { command ->
            when {
                offline && command is LocalAction.WebSearch -> error(R.string.phone_web_offline)
                command == LocalAction.Battery || command == LocalAction.MediaVolume ->
                    actionResult(ChatDialog.Action(command, fromText = true), execute(command))
                else -> dialog(ChatDialog.Action(command, fromText = true))
            }
            return
        }
        if (chat.localOnly) {
            error(if (chat.photo == null) R.string.phone_unrecognized else R.string.phone_detach_first)
            return
        }
        if (chat.team != null) {
            error(R.string.chat_team_pick_or_clear)
            return
        }
        if (offline) {
            error(R.string.chat_offline_help)
            return
        }
        prepare {
            // Only completed, opted-in context can leave the phone. No input is sent while making the snapshot.
            val context = if (!chat.options.includeContext) emptyList() else chat.turns.filter { it.complete }.flatMap { t ->
                listOf(ContextMessage(MessageRole.USER, t.question), ContextMessage(MessageRole.ASSISTANT, t.answer))
            }
            val included = if (chat.options.useMemory) memory.facts.first() else emptyList()
            val input = ConversationInput.prepare(chat.draft, context, included, chat.options.style, chat.task, chat.photo, chat.options.mask)
            if (input == null) {
                error(R.string.chat_payload_invalid)
                return@prepare
            }
            val route = if (chat.teamIds.isEmpty()) ChatRoute.direct(input, profiles, chat.manualId, preferred)
                else ChatRoute.parallel(input, profiles, chat.teamIds)
            if (route == null) {
                error(when {
                    chat.teamIds.isNotEmpty() -> R.string.chat_team_unavailable
                    chat.photo != null -> R.string.chat_image_choose_vision
                    else -> R.string.chat_no_engine
                })
                return@prepare
            }
            val grant = SendGrant.chat(route.candidates, route.parallel, chat.options.includeContext,
                chat.options.useMemory, input.photo != null, chat.options.mask)
            val plan = ChatDialog.Send(route, chat.draft.trim(), chat.current?.id, chat.options.autoHistory, grant)
            if (directSend.permits(grant)) start(plan)
            else mutable.update { it.copy(dialog = plan) }
        }
    }

    @MainThread
    fun approve(offline: Boolean) {
        val plan = mutable.value.dialog as? ChatDialog.Send ?: return
        if (offline) {
            dialog(null)
            error(R.string.chat_offline_help)
            return
        }
        prepare {
            directSend.remember(plan.grant)
            start(plan)
        }
    }

    private fun start(approved: ChatDialog.Send) {
        val chat = mutable.value
        if (chat.running) return
        if (approved.route.parallel) {
            startTeam(approved)
            return
        }
        val turn = ChatTurn(++counter, approved.question, includedImage = approved.route.input.photo != null,
            readWhenDone = chat.options.readReplies)
        mutable.value = chat.copy(draft = "", photo = null, running = true, dialog = null, error = null, notice = null,
            turns = (chat.turns + turn).takeLast(ChatLimits.RETAINED_MESSAGES / 2))
        generation = viewModelScope.launch {
            val names = approved.route.candidates.associate { it.id to it.config.name }
            try {
                router.chat(approved.route).collect { event ->
                    when (event) {
                        is EngineEvent.Started -> reviseTurn(turn.index) { it.copy(engine = names[event.profileId]) }
                        is EngineEvent.Text -> reviseTurn(turn.index) {
                            it.copy(answer = if (event.chunk.replaces) event.chunk.value else it.answer + event.chunk.value,
                                firstMillis = event.firstTextMillis)
                        }
                        is EngineEvent.Complete -> reviseTurn(turn.index) { it.copy(complete = true, elapsedMillis = event.elapsedMillis) }
                        is EngineEvent.Failed -> reviseTurn(turn.index) {
                            it.copy(errors = it.errors + ChatAttempt(names[event.profileId].orEmpty(), event.problem, event.httpStatus))
                        }
                        EngineEvent.Offline -> error(R.string.chat_offline_help)
                        EngineEvent.Finished -> Unit
                    }
                }
                val done = mutable.value.turns.lastOrNull()?.takeIf { it.index == turn.index && it.complete }
                if (done != null && approved.store) {
                    val title = approved.question.lineSequence().first().take(LocalText.TITLE_LIMIT).trimEnd().ifBlank { "گفتگو" }
                    val id = history.saveExchange(approved.historyId, title, done.question, done.answer)
                    mutable.update { it.copy(current = it.current?.takeIf { active -> active.id == id } ?: Conversation(id, title, System.currentTimeMillis())) }
                }
            } catch (canceled: CancellationException) {
                reviseTurn(turn.index) { if (it.complete) it else it.copy(interrupted = true) }
                throw canceled
            } catch (failure: Exception) {
                error(DanaErrorHandler.messageFor(failure))
            } finally {
                mutable.update { it.copy(running = false) }
            }
        }
    }

    private fun startTeam(approved: ChatDialog.Send) {
        val chat = mutable.value
        val team = TeamTurn(++counter, approved.question, approved.route.input.photo != null, approved.historyId,
            approved.store, chat.options.readReplies,
            ComparisonState(lanes = approved.route.candidates.map { EngineLane(it) }, running = true))
        mutable.value = chat.copy(team = team, draft = "", photo = null, running = true, dialog = null, error = null, notice = null)
        generation = viewModelScope.launch {
            try {
                router.chat(approved.route).collect { event -> updateTeam { it.reduce(event) } }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                updateTeam { it.copy(problem = EngineProblem.SERVER) }
            } finally {
                updateTeam { it.finish() }
                mutable.update { it.copy(running = false) }
            }
        }
    }

    @MainThread
    fun discardTeam() {
        if (!mutable.value.busy) mutable.update { it.copy(team = null, error = null) }
    }

    @MainThread
    fun keepTeamAnswer(id: String) {
        val st = mutable.value
        val team = st.team ?: return
        if (st.busy) return
        val chosen = team.comparison.lanes.firstOrNull { it.profile.id == id && it.status == LaneStatus.COMPLETE } ?: return
        val answer = ChatTurn(team.index, team.question, chosen.answer, engine = chosen.profile.config.name,
            firstMillis = chosen.firstTextMillis, elapsedMillis = chosen.elapsedMillis, complete = true,
            includedImage = team.includedImage, readWhenDone = team.readWhenPicked)
        write {
            if (team.store) {
                val title = team.question.lineSequence().first().take(LocalText.TITLE_LIMIT).trimEnd().ifBlank { "گفتگو" }
                val cid = history.saveExchange(team.historyId, title, answer.question, answer.answer)
                mutable.update { it.copy(current = it.current?.takeIf { active -> active.id == cid } ?: Conversation(cid, title, System.currentTimeMillis())) }
            }
            mutable.update {
                it.copy(team = null, turns = (it.turns + answer).takeLast(ChatLimits.RETAINED_MESSAGES / 2))
            }
        }
    }

    private fun updateTeam(transform: (ComparisonState) -> ComparisonState) {
        mutable.update { st -> st.copy(team = st.team?.let { it.copy(comparison = transform(it.comparison)) }) }
    }

    @MainThread
    fun stop() {
        preparing?.cancel()
        generation?.cancel()
    }

    @MainThread
    fun background() {
        stop()
        mutable.update { st ->
            st.copy(dialog = st.dialog.takeUnless { it is ChatDialog.Send || it is ChatDialog.Action },
                speakNotice = false, turns = st.turns.map { if (it.readWhenDone) it.copy(readWhenDone = false) else it },
                team = st.team?.copy(readWhenPicked = false))
        }
    }

    @MainThread
    fun executeAction(offline: Boolean, execute: (LocalAction) -> DeviceOutcome) {
        val dialog = mutable.value.dialog as? ChatDialog.Action ?: return
        if (mutable.value.busy) return
        if (offline && dialog.command is LocalAction.WebSearch) {
            actionResult(dialog, DeviceOutcome(R.string.phone_web_offline, ok = false))
            return
        }
        if (dialog.command is LocalAction.Note) {
            write {
                notes.save(null, dialog.command.text)
                actionResult(dialog, DeviceOutcome(R.string.chat_note_saved))
            }
        } else {
            // Consume the approval first. Repeated button events cannot perform it twice.
            mutable.update { it.copy(dialog = null) }
            actionResult(dialog, execute(dialog.command))
        }
    }

    @MainThread
    fun memoryFromReply(text: String) {
        if (text.isNotBlank() && text.length <= LocalText.MEMORY_LIMIT) {
            // Start with an editable draft. Creating a record still requires pressing Save.
            dialog(ChatDialog.EditMemory(null, text))
        }
    }

    @MainThread
    fun saveMemoryDraft(text: String) {
        val editor = mutable.value.dialog as? ChatDialog.EditMemory ?: return
        write { memory.save(editor.previous?.id, text) }
    }

    @MainThread
    fun rename(title: String) {
        val editor = mutable.value.dialog as? ChatDialog.RenameConversation ?: return
        write {
            history.rename(editor.item.id, title)
            mutable.update { st -> st.copy(current = st.current?.let { if (it.id == editor.item.id) it.copy(title = title.trim()) else it }) }
        }
    }

    @MainThread
    fun remove() {
        val editor = mutable.value.dialog
        when (editor) {
            is ChatDialog.RemoveMemory -> write { if (editor.id == null) memory.clear() else memory.delete(editor.id) }
            is ChatDialog.RemoveConversation -> write {
                if (editor.id == null) history.clear() else history.delete(editor.id)
                if (editor.id == null || editor.id == mutable.value.current?.id) {
                    resetConversation(ChatPage.HISTORY)
                    mutable.update { it.copy(writing = true) }
                }
            }
            else -> Unit
        }
    }

    private fun actionResult(editor: ChatDialog.Action, outcome: DeviceOutcome) {
        mutable.update { it.copy(draft = if (editor.fromText && outcome.ok) "" else it.draft, dialog = null,
            notice = outcome.message.takeIf { outcome.ok }, noticePercent = outcome.percent,
            speakNotice = outcome.ok && it.voiceOpen && it.options.readReplies && it.page == ChatPage.CONVERSATION,
            error = outcome.message.takeUnless { outcome.ok }) }
    }

    private fun resetConversation(page: ChatPage) {
        mutable.update { ChatState(page = page, options = it.options.copy(autoHistory = false), voiceOpen = it.voiceOpen, localOnly = it.localOnly, manualId = it.manualId, teamIds = it.teamIds) }
    }

    private fun error(@StringRes message: Int) { mutable.update { it.copy(error = message, notice = null) } }

    private fun reviseTurn(index: Long, change: (ChatTurn) -> ChatTurn) {
        mutable.update { st -> st.copy(turns = st.turns.map { if (it.index == index) change(it) else it }) }
    }

    private fun prepare(operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(writing = true, error = null, notice = null) }
        preparing = viewModelScope.launch {
            try {
                operation()
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (failure: Exception) {
                error(DanaErrorHandler.messageFor(failure))
            } finally {
                mutable.update { it.copy(writing = false) }
            }
        }
    }

    private fun write(close: Boolean = true, operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(writing = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                operation()
                coroutineContext.ensureActive()
                if (close) mutable.update { it.copy(dialog = null) }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (failure: Exception) {
                error(DanaErrorHandler.messageFor(failure))
            } finally {
                mutable.update { it.copy(writing = false) }
            }
        }
    }

    private fun <T> read(source: Flow<T>): Flow<Reading<T>> = source.map<T, Reading<T>> { Reading.Ready(it) }
        .onStart { emit(Reading.Loading) }
        .catch { error ->
            if (error is CancellationException || error !is Exception) throw error
            emit(Reading.Failed(DanaErrorHandler.messageFor(error)))
        }
}
