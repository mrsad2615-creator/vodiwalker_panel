package dev.mrsad.app.feature.focus

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.db.ActivityEventEntity
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.app.util.firstValue
import dev.mrsad.core.session.FocusSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusUiState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val phase: FocusSession.Phase = FocusSession.Phase.FOCUS,
    val round: Int = 1,
    val rounds: Int = 1,
    val remainingMillis: Long = 25 * 60_000L,
    val progress: Float = 0f,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val completedToday: Int = 0,
    val focusMinutesToday: Int = 0,
    val totalSessions: Int = 0,
) {
    val phaseLabelFa: String
        get() = when (phase) {
            FocusSession.Phase.FOCUS -> "تمرکز"
            FocusSession.Phase.BREAK -> "استراحت"
            FocusSession.Phase.DONE -> "پایان"
        }
}

/**
 * Focus Space controller.
 *
 * Timing correctness is the product promise here, so:
 * - the [FocusSession] state machine (unit-tested in :core) owns phase logic;
 * - all timestamps come from `SystemClock.elapsedRealtime()` (monotonic, keeps
 *   counting while the screen is off);
 * - the ViewModel is reachable from the foreground service, which rebinds to
 *   the same state after process recreation is out of scope of a UI tick.
 *
 * A completed focus block is persisted once — never per tick.
 */
class FocusViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    private var session: FocusSession? = null
    private var ticker: Job? = null
    private var persisted = false

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = MrsadClock.todayIso()
            val events = container.database.activityDao()
                .eventsForDate(today)
                .firstValue()
                .filter { it.module == "FOCUS" }
            val all = container.database.activityDao()
                .recentEvents(System.currentTimeMillis() - 365L * 86_400_000L, 500)
                .firstValue()
                .count { it.module == "FOCUS" }
            _state.update {
                it.copy(
                    completedToday = events.size,
                    focusMinutesToday = events.sumOf { ev -> ev.minutes },
                    totalSessions = all,
                )
            }
        }
    }

    fun configure(focusMinutes: Int, breakMinutes: Int, rounds: Int) {
        if (_state.value.isRunning) return
        _state.update { it.copy(focusMinutes = focusMinutes, breakMinutes = breakMinutes, rounds = rounds) }
        resetTimerPreview()
    }

    private fun resetTimerPreview() {
        val current = _state.value
        val newSession = FocusSession(current.focusMinutes, current.breakMinutes, current.rounds)
        session = newSession
        _state.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                phase = FocusSession.Phase.FOCUS,
                round = 1,
                remainingMillis = current.focusMinutes * 60_000L,
                progress = 0f,
            )
        }
    }

    fun start() {
        if (_state.value.isRunning) return
        val current = _state.value
        val newSession = session ?: FocusSession(current.focusMinutes, current.breakMinutes, current.rounds)
        session = newSession
        persisted = false
        newSession.start(SystemClock.elapsedRealtime())
        _state.update { it.copy(isRunning = true, isPaused = false, phase = FocusSession.Phase.FOCUS) }
        startTicker()
    }

    fun togglePause() {
        val active = session ?: return
        if (!_state.value.isRunning) return
        if (_state.value.isPaused) {
            active.resume(SystemClock.elapsedRealtime())
            _state.update { it.copy(isPaused = false) }
            startTicker()
        } else {
            active.pause(SystemClock.elapsedRealtime())
            ticker?.cancel()
            _state.update { it.copy(isPaused = true) }
        }
    }

    fun stop() {
        ticker?.cancel()
        session = null
        _state.update { it.copy(isRunning = false, isPaused = false) }
        resetTimerPreview()
    }

    private fun startTicker() {
        ticker?.cancel()
        if (MrsadTestHooks.isDeterministic) {
            // Deterministic mode: the phase engine is not driven by a wall
            // clock, so the dial reflects the session exactly as configured
            // and the Compose idling resource can reach idle.
            refreshFromSession()
            return
        }
        ticker = viewModelScope.launch {
            while (_state.value.isRunning) {
                val active = session ?: break
                val now = SystemClock.elapsedRealtime()
                val changed = active.tick(now)
                _state.update {
                    it.copy(
                        phase = active.phase,
                        round = active.round,
                        remainingMillis = active.remainingMillis(now),
                        progress = active.progress(now),
                    )
                }
                when (changed) {
                    FocusSession.Phase.BREAK, FocusSession.Phase.FOCUS -> notifyPhaseChange(active)
                    FocusSession.Phase.DONE -> {
                        persistCompletedFocus(active)
                        ticker?.cancel()
                        _state.update { it.copy(isRunning = false, isPaused = false) }
                        loadHistory()
                        return@launch
                    }
                    null -> Unit
                }
                delay(500)
            }
        }
    }

    /** Re-reads the session's real state without advancing the phase engine. */
    private fun refreshFromSession() {
        val active = session ?: return
        val now = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(
                phase = active.phase,
                round = active.round,
                remainingMillis = active.remainingMillis(now),
                progress = active.progress(now),
            )
        }
    }

    private fun notifyPhaseChange(active: FocusSession) {
        val ctx = container.appContextOrNull() ?: return
        dev.mrsad.app.feature.focus.FocusNotifications.notifyPhase(
            context = ctx,
            phase = active.phase,
            round = active.round,
            rounds = active.rounds,
        )
    }

    private fun persistCompletedFocus(active: FocusSession) {
        if (persisted) return
        persisted = true
        viewModelScope.launch(Dispatchers.IO) {
            val now = MrsadClock.nowEpochMillis()
            runCatching {
                container.database.activityDao().insert(
                    ActivityEventEntity(
                        module = "FOCUS",
                        dateIso = MrsadClock.isoFor(now),
                        finishedAtMillis = now,
                        minutes = active.plannedFocusMinutes,
                        primaryMetric = active.plannedFocusMinutes.toDouble(),
                        secondaryMetric = 0.0,
                        payload = "rounds=${active.rounds}",
                    )
                )
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FocusViewModel(container) as T
    }
}
