package ai.dana.ui

import ai.dana.R
import ai.dana.domain.ApiCredential
import ai.dana.domain.CredentialChange
import ai.dana.domain.DanaNote
import ai.dana.domain.NetworkMonitor
import ai.dana.domain.NetworkStatus
import ai.dana.domain.NotesRepository
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.ProviderConfig
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
    NOTES(R.string.tab_notes),
    SETTINGS(R.string.tab_settings),
}

internal sealed interface Reading<out T> {
    data object Loading : Reading<Nothing>
    data class Ready<T>(val value: T) : Reading<T>
    data class Failed(@StringRes val message: Int) : Reading<Nothing>
}

internal sealed interface Editor {
    data class Note(val previous: DanaNote?) : Editor
    data class Provider(val previous: ProviderProfile?) : Editor
    data class DeleteNote(val id: Long) : Editor
    data class DeleteProvider(val id: String) : Editor
}

internal data class DashboardSession(
    val tab: DashboardTab = DashboardTab.HOME,
    val editor: Editor? = null,
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
)

internal data class DashboardState(
    val network: NetworkStatus = NetworkStatus.CHECKING,
    val offline: Reading<Boolean> = Reading.Loading,
    val notes: Reading<List<DanaNote>> = Reading.Loading,
    val profiles: Reading<List<ProviderProfile>> = Reading.Loading,
    val session: DashboardSession = DashboardSession(),
)

internal sealed interface DanaIntent {
    data class Navigate(val tab: DashboardTab) : DanaIntent
    data class EditNote(val note: DanaNote?) : DanaIntent
    data class EditProvider(val profile: ProviderProfile?) : DanaIntent
    data class RequestNoteDeletion(val id: Long) : DanaIntent
    data class RequestProviderDeletion(val id: String) : DanaIntent
    data object ConfirmDeletion : DanaIntent
    data object DismissEditor : DanaIntent
    data object Reload : DanaIntent
    data class SetOffline(val enabled: Boolean) : DanaIntent
    data class SaveNote(val text: String) : DanaIntent

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
) : ViewModel() {
    private val session = MutableStateFlow(DashboardSession())
    private val reloadGeneration = MutableStateFlow(0L)

    val uiState = combine(
        monitor.status.onStart { emit(NetworkStatus.CHECKING) },
        reloadable(preferences.offlineMode),
        reloadable(notes.notes),
        reloadable(providers.profiles),
        session,
    ) { network, offline, notes, profiles, session ->
        DashboardState(network, offline, notes, profiles, session)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    @MainThread
    fun dispatch(intent: DanaIntent) {
        val current = session.value
        if (current.busy) return
        when (intent) {
            is DanaIntent.Navigate -> session.value = DashboardSession(tab = intent.tab)
            is DanaIntent.EditNote -> show(Editor.Note(intent.note))
            is DanaIntent.EditProvider -> show(Editor.Provider(intent.profile))
            is DanaIntent.RequestNoteDeletion -> show(Editor.DeleteNote(intent.id))
            is DanaIntent.RequestProviderDeletion -> show(Editor.DeleteProvider(intent.id))
            DanaIntent.DismissEditor -> show(null)
            DanaIntent.Reload -> reloadGeneration.update { it + 1 }
            is DanaIntent.SetOffline -> write { preferences.setOfflineMode(intent.enabled) }
            is DanaIntent.SaveNote -> {
                val editor = current.editor as? Editor.Note ?: return
                write(closeEditor = true) { notes.save(editor.previous?.id, intent.text) }
            }
            is DanaIntent.SaveProvider -> {
                val editor = current.editor as? Editor.Provider ?: return
                write(closeEditor = true) {
                    val config = ProviderConfig.create(intent.name, intent.protocol, intent.network, intent.baseUrl, intent.model)
                    val change = when {
                        intent.removeKey -> CredentialChange.Remove
                        intent.key.isNotEmpty() -> CredentialChange.Replace(ApiCredential.parse(intent.key))
                        else -> CredentialChange.Keep
                    }
                    providers.save(editor.previous?.id, config, change)
                }
            }
            DanaIntent.ConfirmDeletion -> when (val editor = current.editor) {
                is Editor.DeleteNote -> write(closeEditor = true) { notes.delete(editor.id) }
                is Editor.DeleteProvider -> write(closeEditor = true) { providers.delete(editor.id) }
                else -> Unit
            }
        }
    }

    private fun show(editor: Editor?) {
        session.update { it.copy(editor = editor, error = null) }
    }

    private fun write(closeEditor: Boolean = false, operation: suspend () -> Unit) {
        session.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                operation()
                session.update { it.copy(busy = false, editor = if (closeEditor) null else it.editor) }
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
