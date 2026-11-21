package dev.mrsad.app.feature.type

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.core.text.PersianText
import dev.mrsad.core.typing.TypingLanguage
import dev.mrsad.core.typing.TypingResult
import dev.mrsad.core.typing.TypingSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TypePhase { SETUP, RUNNING, PAUSED, FINISHED }

data class TypeUiState(
    val phase: TypePhase = TypePhase.SETUP,
    val usePersian: Boolean = true,
    val selectedLevelFilter: Int = 0,
    val selectedTextId: String? = null,
    val selectedDurationSeconds: Int = 60,
    val targetText: String = "",
    val title: String = "",
    val categoryFa: String = "",
    val textLevel: Int = 1,
    val targetWpm: Int = 35,
    val typedPrefixLength: Int = 0,
    val expectedChar: Char? = null,
    val liveWpm: Double = 0.0,
    val liveCpm: Double = 0.0,
    val liveAccuracy: Double = 100.0,
    val errors: Int = 0,
    val remainingMillis: Long = 60_000L,
    val progress: Float = 0f,
    val result: TypingResult? = null,
    val mistakeChar: Char? = null,
    val mistakeTick: Long = 0L,
    val savedSessionId: Long? = null,
    val isSaving: Boolean = false,
) {
    val isActive: Boolean get() = phase == TypePhase.RUNNING || phase == TypePhase.PAUSED
    /** The engine advances only on correct characters, so the visible prefix is exact. */
    val visibleTarget: String get() = targetText.take(typedPrefixLength)
}

/**
 * State machine for MRSAD TYPE.
 *
 * Timekeeping rules that make the metrics trustworthy:
 * - The typing engine (in :core, unit-tested) is clock-injected and pure.
 * - All engine timestamps come from `SystemClock.elapsedRealtime()` — monotonic,
 *   unaffected by wall-clock changes — minus accumulated pause time.
 * - Sessions with zero keystrokes are never persisted (no fabricated records).
 */
class TypeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TypeUiState())
    val state: StateFlow<TypeUiState> = _state.asStateFlow()

    private var session: TypingSession? = null
    private var ticker: Job? = null

    private var accumulatedPausedMillis: Long = 0L
    private var pauseStartedAt: Long = 0L
    private var firstKeyAt: Long? = null

    init {
        chooseText(TypeTexts.dailyPick(usePersian = true, dateIso = MrsadClock.todayIso()))
    }

    fun setLanguage(persian: Boolean) {
        if (_state.value.isActive) return
        _state.update { it.copy(usePersian = persian, selectedLevelFilter = 0) }
        chooseText(TypeTexts.dailyPick(persian, MrsadClock.todayIso()))
    }

    fun setLevelFilter(level: Int) {
        if (_state.value.isActive) return
        val clamped = level.coerceIn(0, 3)
        _state.update { it.copy(selectedLevelFilter = clamped) }
        val filtered = TypeTexts.forLanguage(_state.value.usePersian, clamped)
        if (filtered.isNotEmpty() && filtered.none { it.id == _state.value.selectedTextId }) {
            chooseText(filtered.first())
        }
    }

    fun setDuration(seconds: Int) {
        if (_state.value.isActive) return
        _state.update { it.copy(selectedDurationSeconds = seconds, remainingMillis = seconds * 1000L) }
    }

    fun selectTextById(id: String) {
        val text = TypeTexts.forLanguage(_state.value.usePersian).firstOrNull { it.id == id } ?: return
        if (_state.value.isActive) return
        chooseText(text)
    }

    private fun chooseText(text: PracticeText) {
        session = null
        val normalizedTarget = PersianText.normalizeForComparison(text.body)
        _state.update {
            it.copy(
                phase = TypePhase.SETUP,
                selectedTextId = text.id,
                targetText = normalizedTarget,
                title = text.title,
                categoryFa = text.categoryFa,
                textLevel = text.level,
                targetWpm = text.targetWpm,
                typedPrefixLength = 0,
                expectedChar = normalizedTarget.firstOrNull(),
                liveWpm = 0.0,
                liveCpm = 0.0,
                liveAccuracy = 100.0,
                errors = 0,
                progress = 0f,
                result = null,
                savedSessionId = null,
                mistakeChar = null,
                remainingMillis = it.selectedDurationSeconds * 1000L,
            )
        }
    }

    fun start() {
        val current = _state.value
        if (current.isActive || current.targetText.isEmpty()) return
        val newSession = TypingSession(
            targetText = current.targetText,
            language = if (current.usePersian) TypingLanguage.PERSIAN else TypingLanguage.ENGLISH,
            mode = "timed-${current.selectedDurationSeconds}s",
            startedAtEpochMillis = MrsadClock.nowEpochMillis(),
        )
        session = newSession
        accumulatedPausedMillis = 0L
        firstKeyAt = null
        _state.update {
            it.copy(
                phase = TypePhase.RUNNING,
                typedPrefixLength = 0,
                expectedChar = newSession.expectedChar,
                liveWpm = 0.0,
                liveCpm = 0.0,
                liveAccuracy = 100.0,
                errors = 0,
                progress = 0f,
                result = null,
                savedSessionId = null,
                mistakeChar = null,
                remainingMillis = it.selectedDurationSeconds * 1000L,
            )
        }
        startTicker()
    }

    fun pause() {
        if (_state.value.phase != TypePhase.RUNNING) return
        pauseStartedAt = SystemClock.elapsedRealtime()
        ticker?.cancel()
        _state.update { it.copy(phase = TypePhase.PAUSED) }
    }

    fun resume() {
        if (_state.value.phase != TypePhase.PAUSED) return
        accumulatedPausedMillis += SystemClock.elapsedRealtime() - pauseStartedAt
        _state.update { it.copy(phase = TypePhase.RUNNING) }
        startTicker()
    }

    fun restart() {
        ticker?.cancel()
        session = null
        val current = _state.value
        val text = TypeTexts.forLanguage(current.usePersian)
            .firstOrNull { it.id == current.selectedTextId }
            ?: TypeTexts.dailyPick(current.usePersian, MrsadClock.todayIso())
        chooseText(text)
    }

    /** Engine clock: monotonic milliseconds with all paused time removed. */
    private fun engineNow(): Long {
        val now = SystemClock.elapsedRealtime()
        val ongoingPause =
            if (_state.value.phase == TypePhase.PAUSED) now - pauseStartedAt else 0L
        return now - accumulatedPausedMillis - ongoingPause
    }

    fun onKey(ch: Char) {
        val active = session ?: return
        if (_state.value.phase != TypePhase.RUNNING) return
        val engineTime = engineNow()
        if (firstKeyAt == null) firstKeyAt = engineTime
        val outcome = active.type(ch, engineTime)

        _state.update {
            it.copy(
                typedPrefixLength = active.cursor,
                expectedChar = active.expectedChar,
                errors = active.errorKeystrokes,
                progress = active.progress,
                mistakeChar = if (outcome == TypingSession.KeyOutcome.INCORRECT) ch else null,
                mistakeTick = if (outcome == TypingSession.KeyOutcome.INCORRECT) it.mistakeTick + 1 else it.mistakeTick,
            )
        }
        refreshLive(engineTime)
        if (active.isFinished) finish(completed = true)
    }

    fun onBackspace() {
        val active = session ?: return
        if (_state.value.phase != TypePhase.RUNNING) return
        if (!active.backspace()) return
        _state.update {
            it.copy(
                typedPrefixLength = active.cursor,
                expectedChar = active.expectedChar,
                progress = active.progress,
            )
        }
        refreshLive(engineNow())
    }

    private fun refreshLive(engineTime: Long) {
        val active = session ?: return
        val snapshot = active.liveSnapshot(engineTime)
        refreshRemaining(engineTime)
        _state.update {
            it.copy(
                liveWpm = snapshot.wpm,
                liveCpm = snapshot.cpm,
                liveAccuracy = snapshot.accuracy,
                errors = snapshot.errors,
                progress = snapshot.progress,
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        if (MrsadTestHooks.isDeterministic) {
            // Deterministic mode: no recurring state updates. The remaining
            // time is recomputed on every keystroke instead, so the screen
            // stays still and the Compose idling resource can reach idle.
            refreshRemaining()
            return
        }
        ticker = viewModelScope.launch {
            while (true) {
                val engineTime = engineNow()
                // The clock is updated first so that it stays correct even if
                // the session was released underneath us.
                refreshRemaining(engineTime)
                refreshLive(engineTime)
                if (firstKeyAt != null && _state.value.remainingMillis == 0L) {
                    finish(completed = false)
                    return@launch
                }
                delay(100)
            }
        }
    }

    /** Remaining time of the current run, derived from the monotonic clock. */
    private fun refreshRemaining(engineTime: Long = engineNow()) {
        val started = firstKeyAt
        val elapsed = if (started == null) 0L else (engineTime - started).coerceAtLeast(0L)
        val total = _state.value.selectedDurationSeconds * 1000L
        val remaining = (total - elapsed).coerceAtLeast(0L)
        _state.update { it.copy(remainingMillis = remaining) }
    }

    /** Ends the session and persists it (real data only). */
    fun finish(completed: Boolean) {
        if (_state.value.phase == TypePhase.FINISHED) return
        ticker?.cancel()
        val active = session ?: return
        if (active.totalKeystrokes == 0) {
            _state.update { it.copy(phase = TypePhase.SETUP) }
            return
        }
        val result = active.result(engineNow(), completed = completed && active.isFinished)
        _state.update { it.copy(phase = TypePhase.FINISHED, result = result, isSaving = true) }
        viewModelScope.launch {
            val nowEpoch = MrsadClock.nowEpochMillis()
            val id = runCatching {
                container.typingRepository.record(result, nowEpoch, MrsadClock.isoFor(nowEpoch))
            }.getOrNull()
            _state.update { it.copy(savedSessionId = id, isSaving = false) }
        }
    }

    /** Abandons the current run without persisting (used by "از نو" in results). */
    fun resetToSetup() {
        ticker?.cancel()
        session = null
        val current = _state.value
        val text = TypeTexts.forLanguage(current.usePersian)
            .firstOrNull { it.id == current.selectedTextId }
            ?: TypeTexts.dailyPick(current.usePersian, MrsadClock.todayIso())
        chooseText(text)
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TypeViewModel(container) as T
    }
}
