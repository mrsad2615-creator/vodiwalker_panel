package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ApiCredential
import ai.dana.domain.AssistantTask
import ai.dana.domain.DirectoryResult
import ai.dana.domain.EngineDiscovery
import ai.dana.domain.EngineFailure
import ai.dana.domain.ComparisonLimits
import ai.dana.domain.ComparisonRequest
import ai.dana.domain.CredentialChange
import ai.dana.domain.DanaAiRouter
import ai.dana.domain.DanaNote
import ai.dana.domain.EngineEvent
import ai.dana.domain.EngineProblem
import ai.dana.domain.NetworkMonitor
import ai.dana.domain.NetworkStatus
import ai.dana.domain.NotesRepository
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderEndpoint
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.ProviderRepository
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class DashboardTab(@StringRes val label: Int) {
    HOME(R.string.tab_home),
    CHAT(R.string.tab_chat),
    COMPARE(R.string.tab_compare),
    NOTES(R.string.tab_notes),
    SETTINGS(R.string.tab_engines),
    ACCOUNT(R.string.tab_account),
}

internal sealed interface Reading<out T> {
    data object Loading : Reading<Nothing>
    data class Ready<T>(val value: T) : Reading<T>
    data class Failed(@StringRes val message: Int) : Reading<Nothing>
}

internal sealed interface Editor {
    data class Note(val previous: DanaNote?, val draft: String = previous?.text.orEmpty()) : Editor
    data class Provider(val previous: ProviderProfile?, val initial: ProviderProtocol, val lanPreset: Boolean = false) : Editor
    data class DeleteNote(val id: Long) : Editor
    data class DeleteProvider(val id: String) : Editor
    class ConfirmComparison(val request: ComparisonRequest) : Editor
}

internal sealed interface DirectoryState {
    data object Loading : DirectoryState
    data class Ready(val result: DirectoryResult) : DirectoryState
    data class Failed(@StringRes val message: Int, val httpStatus: Int?) : DirectoryState
}

internal data class DashboardSession(
    val tab: DashboardTab = DashboardTab.HOME,
    val editor: Editor? = null,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    @StringRes val notice: Int? = null,
    val directory: DirectoryState? = null,
)

internal data class DashboardState(
    val network: NetworkStatus = NetworkStatus.CHECKING,
    val offline: Reading<Boolean> = Reading.Loading,
    val notes: Reading<List<DanaNote>> = Reading.Loading,
    val profiles: Reading<List<ProviderProfile>> = Reading.Loading,
    val session: DashboardSession = DashboardSession(),
    val comparison: ComparisonState = ComparisonState(),
)

internal sealed interface DanaIntent {
    data class Navigate(val tab: DashboardTab) : DanaIntent
    data class EditNote(val note: DanaNote?) : DanaIntent
    data class EditProvider(val profile: ProviderProfile?, val preset: ProviderProtocol = ProviderProtocol.OPENAI_COMPATIBLE, val lanPreset: Boolean = false) : DanaIntent
    data class ChooseTask(val task: AssistantTask, val openWorkspace: Boolean = false) : DanaIntent
    data class NoteFromAnswer(val profileId: String) : DanaIntent
    data object ClearDirectory : DanaIntent
    data object StopRemoteRequests : DanaIntent
    data class RequestNoteDeletion(val id: Long) : DanaIntent
    data class RequestProviderDeletion(val id: String) : DanaIntent
    data object ConfirmDeletion : DanaIntent
    data object DismissEditor : DanaIntent
    data object Reload : DanaIntent
    data class SetOffline(val enabled: Boolean) : DanaIntent
    class ChangePrompt(val value: String) : DanaIntent
    data class ToggleEngine(val profileId: String) : DanaIntent
    data object ReviewComparison : DanaIntent
    data object SendComparison : DanaIntent
    data object StopComparison : DanaIntent
    data class SaveNote(val text: String) : DanaIntent

    class ReadModels(
        val protocol: ProviderProtocol,
        val network: ProviderNetwork,
        val baseUrl: String,
        val key: String,
        val removeKey: Boolean,
    ) : DanaIntent

    // Intentionally not a data class: the credential is never part of a generated toString or UI state.
    class SaveProvider(
        val name: String,
        val protocol: ProviderProtocol,
        val network: ProviderNetwork,
        val baseUrl: String,
        val model: String,
        val key: String,
        val removeKey: Boolean,
    ) : DanaIntent
}

@HiltViewModel
internal class DanaViewModel @Inject constructor(
    monitor: NetworkMonitor,
    private val preferences: PrivacyPreferences,
    private val notes: NotesRepository,
    private val providers: ProviderRepository,
    private val router: DanaAiRouter,
    private val discovery: EngineDiscovery,
) : ViewModel() {
    private val session = MutableStateFlow(DashboardSession())
    private val reloadGeneration = MutableStateFlow(0L)
    private val comparison = MutableStateFlow(ComparisonState())
    private var comparisonJob: Job? = null
    private var directoryJob: Job? = null

    val uiState = combine(
        monitor.status.onStart { emit(NetworkStatus.CHECKING) },
        reloadable(preferences.offlineMode),
        reloadable(notes.notes),
        reloadable(providers.profiles),
        session,
    ) { network, offline, notes, profiles, session ->
        DashboardState(network, offline, notes, profiles, session)
    }.combine(comparison) { state, comparing -> state.copy(comparison = comparing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    @MainThread
    fun dispatch(intent: DanaIntent) {
        val current = session.value
        if (intent == DanaIntent.StopRemoteRequests) {
            comparisonJob?.cancel()
            if (current.directory == DirectoryState.Loading) clearDirectory()
            return
        }
        if (current.busy && intent != DanaIntent.StopComparison) return
        when (intent) {
            is DanaIntent.Navigate -> {
                if (intent.tab != DashboardTab.COMPARE) comparisonJob?.cancel()
                clearDirectory()
                session.value = DashboardSession(tab = intent.tab)
            }
            is DanaIntent.ChangePrompt -> {
                if (!comparison.value.running && intent.value.length <= ComparisonLimits.PROMPT_CHARS) {
                    comparison.update { it.copy(prompt = intent.value, problem = null) }
                }
            }
            is DanaIntent.ChooseTask -> if (!comparison.value.running) {
                comparison.update { it.copy(task = intent.task, problem = null) }
                if (intent.openWorkspace) {
                    clearDirectory()
                    session.value = DashboardSession(tab = DashboardTab.COMPARE)
                }
            }
            is DanaIntent.NoteFromAnswer -> if (!comparison.value.running) {
                comparison.value.lanes.firstOrNull { it.profile.id == intent.profileId }?.answer
                    ?.takeIf { it.isNotBlank() }?.let { answer ->
                        clearDirectory()
                        session.value = DashboardSession(tab = DashboardTab.NOTES, editor = Editor.Note(null, answer))
                    }
            }
            is DanaIntent.ToggleEngine -> toggleEngine(intent.profileId)
            DanaIntent.ReviewComparison -> reviewComparison()
            DanaIntent.SendComparison -> (current.editor as? Editor.ConfirmComparison)?.let { startComparison(it.request) }
            DanaIntent.StopComparison -> comparisonJob?.cancel()
            DanaIntent.StopRemoteRequests -> Unit // Handled before the write lock.
            DanaIntent.ClearDirectory -> clearDirectory()
            is DanaIntent.ReadModels -> readModels(intent)
            is DanaIntent.EditNote -> show(Editor.Note(intent.note))
            is DanaIntent.EditProvider -> show(Editor.Provider(intent.profile, intent.profile?.config?.protocol ?: intent.preset, intent.lanPreset))
            is DanaIntent.RequestNoteDeletion -> show(Editor.DeleteNote(intent.id))
            is DanaIntent.RequestProviderDeletion -> show(Editor.DeleteProvider(intent.id))
            DanaIntent.DismissEditor -> show(null)
            DanaIntent.Reload -> reloadGeneration.update { it + 1 }
            is DanaIntent.SetOffline -> {
                if (intent.enabled) {
                    comparisonJob?.cancel()
                    clearDirectory()
                }
                write { preferences.setOfflineMode(intent.enabled) }
            }
            is DanaIntent.SaveNote -> {
                val editor = current.editor as? Editor.Note ?: return
                write(closeEditor = true) { notes.save(editor.previous?.id, intent.text) }
            }
            is DanaIntent.SaveProvider -> {
                val editor = current.editor as? Editor.Provider ?: return
                if (current.directory == DirectoryState.Loading) return
                write(closeEditor = true) {
                    val config = ProviderConfig.create(intent.name, intent.protocol, intent.network, intent.baseUrl, intent.model)
                    if (config.protocol != editor.initial) return@write
                    providers.save(editor.previous?.id, config, keyChange(intent.key, intent.removeKey))
                    session.update { it.copy(notice = R.string.engine_saved) }
                }
            }
            DanaIntent.ConfirmDeletion -> when (val editor = current.editor) {
                is Editor.DeleteNote -> write(closeEditor = true) { notes.delete(editor.id) }
                is Editor.DeleteProvider -> write(closeEditor = true) { providers.delete(editor.id) }
                else -> Unit
            }
        }
    }

    private fun availableProfiles(): List<ProviderProfile> = (uiState.value.profiles as? Reading.Ready)?.value.orEmpty()

    private fun toggleEngine(id: String) {
        val existing = availableProfiles().mapTo(mutableSetOf()) { it.id }
        if (id !in existing) return
        comparison.update { state ->
            if (state.running) return@update state
            val selected = state.selected.intersect(existing)
            val next = when {
                id in selected -> selected - id
                selected.size < ComparisonLimits.ENGINE_COUNT -> selected + id
                else -> selected
            }
            state.copy(selected = next, problem = null)
        }
    }

    private fun reviewComparison() {
        val state = comparison.value
        if (session.value.tab != DashboardTab.COMPARE || state.running) return
        if ((uiState.value.offline as? Reading.Ready)?.value != false) {
            comparison.update { it.copy(problem = EngineProblem.OFFLINE) }
            return
        }
        val targets = availableProfiles().filter { it.id in state.selected }
        ComparisonRequest.create(state.prompt, targets, state.task)?.let { show(Editor.ConfirmComparison(it)) }
    }

    private fun startComparison(request: ComparisonRequest) {
        if (session.value.tab != DashboardTab.COMPARE || comparison.value.running) return
        show(null)
        comparison.update { it.copy(running = true, lanes = request.targets.map { EngineLane(it) }, problem = null) }
        comparisonJob = viewModelScope.launch {
            try {
                router.compare(request).collect { event ->
                    comparison.update { it.reduce(event) }
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                comparison.update { it.copy(problem = EngineProblem.SERVER) }
            } finally {
                comparison.update { it.finish() }
            }
        }
    }

    private fun keyChange(raw: String, remove: Boolean): CredentialChange = when {
        remove -> CredentialChange.Remove
        raw.isNotEmpty() -> CredentialChange.Replace(ApiCredential.parse(raw))
        else -> CredentialChange.Keep
    }

    private fun readModels(intent: DanaIntent.ReadModels) {
        val editor = session.value.editor as? Editor.Provider ?: return
        if (session.value.directory == DirectoryState.Loading) return
        session.update { it.copy(directory = DirectoryState.Loading, error = null) }
        directoryJob = viewModelScope.launch {
            val result = try {
                val endpoint = ProviderEndpoint.create(intent.protocol, intent.network, intent.baseUrl)
                DirectoryState.Ready(discovery.models(endpoint, editor.previous, keyChange(intent.key, intent.removeKey)))
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                DirectoryState.Failed(DanaErrorHandler.messageFor(error), (error as? EngineFailure)?.httpStatus)
            }
            session.update { it.copy(directory = result) }
        }
    }

    private fun clearDirectory() {
        directoryJob?.cancel()
        directoryJob = null
        session.update { it.copy(directory = null) }
    }

    private fun show(editor: Editor?) {
        clearDirectory()
        session.update { it.copy(editor = editor, error = null, notice = null) }
    }

    private fun write(closeEditor: Boolean = false, operation: suspend () -> Unit) {
        session.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                operation()
                session.update { it.copy(busy = false, editor = if (closeEditor) null else it.editor, directory = if (closeEditor) null else it.directory) }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (error: Exception) {
                session.update { it.copy(busy = false, error = DanaErrorHandler.messageFor(error)) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> reloadable(source: Flow<T>): Flow<Reading<T>> = reloadGeneration.flatMapLatest {
        source.map<T, Reading<T>> { Reading.Ready(it) }
            .onStart { emit(Reading.Loading) }
            .catch { error ->
                if (error is CancellationException || error !is Exception) throw error
                emit(Reading.Failed(DanaErrorHandler.messageFor(error)))
            }
    }
}
