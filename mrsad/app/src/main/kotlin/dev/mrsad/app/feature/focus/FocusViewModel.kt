package dev.mrsad.app.feature.focus

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.mrsad.app.audio.AmbientSoundscape
import dev.mrsad.app.audio.SfxType
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
import java.util.WeakHashMap

enum class FocusTimerMode(val id: String, val titleFa: String, val subtitleFa: String) {
    PROTOCOL(
        id = "protocol",
        titleFa = "پروتکل زمان‌بندی‌شده",
        subtitleFa = "شمارش معکوس چنددوری با زمان تمرکز و استراحت مشخص",
    ),
    FLOWTIME(
        id = "flowtime",
        titleFa = "کرنومتر آزاد (Flowtime)",
        subtitleFa = "شمارش پیوسته رو به بالا بدون قطع جریان فکری و ثبت زمان واقعی",
    ),
}

enum class FocusProtocolPreset(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val badgeFa: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int,
) {
    POMODORO(
        id = "pomodoro",
        titleFa = "پومودورو استاندارد",
        subtitleFa = "۲۵ دقیقه تمرکز عمیق • ۵ دقیقه تنفس • ۴ دور متوالی",
        badgeFa = "۲۵ / ۵ • ۴ دور",
        focusMinutes = 25,
        breakMinutes = 5,
        rounds = 4,
    ),
    DEEP_WORK(
        id = "deep_work",
        titleFa = "کار عمیق (Deep Work)",
        subtitleFa = "۵۰ دقیقه غوطه‌وری شناختی • ۱۰ دقیقه بازیابی • ۳ دور",
        badgeFa = "۵۰ / ۱۰ • ۳ دور",
        focusMinutes = 50,
        breakMinutes = 10,
        rounds = 3,
    ),
    ULTRADIAN(
        id = "ultradian",
        titleFa = "چرخهٔ اولترادیَن",
        subtitleFa = "۹۰ دقیقه اوج هوشیاری زیستی • ۱۵ دقیقه استراحت کامل • ۲ دور",
        badgeFa = "۹۰ / ۱۵ • ۲ دور",
        focusMinutes = 90,
        breakMinutes = 15,
        rounds = 2,
    ),
    SPRINT(
        id = "sprint",
        titleFa = "اسپرینت متمرکز",
        subtitleFa = "۱۵ دقیقه شروع سریع بدون اهمال‌کاری • ۳ دقیقه تنفس • ۴ دور",
        badgeFa = "۱۵ / ۳ • ۴ دور",
        focusMinutes = 15,
        breakMinutes = 3,
        rounds = 4,
    ),
    CUSTOM(
        id = "custom",
        titleFa = "تنظیم دستی سفارشی",
        subtitleFa = "تنظیم دلخواه دقایق تمرکز، استراحت و تعداد دورها",
        badgeFa = "سفارشی",
        focusMinutes = 35,
        breakMinutes = 7,
        rounds = 2,
    ),
}

data class FocusUiState(
    val mode: FocusTimerMode = FocusTimerMode.PROTOCOL,
    val preset: FocusProtocolPreset = FocusProtocolPreset.POMODORO,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val phase: FocusSession.Phase = FocusSession.Phase.FOCUS,
    val round: Int = 1,
    val rounds: Int = 4,
    val remainingMillis: Long = 25 * 60_000L,
    val elapsedFlowMillis: Long = 0L,
    val progress: Float = 0f,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val selectedSoundscape: AmbientSoundscape = AmbientSoundscape.BROWN_WARM,
    val isAmbientPlaying: Boolean = false,
    val ambientVolume: Float = 0.75f,
    val tickSoundEnabled: Boolean = false,
    val completedToday: Int = 0,
    val focusMinutesToday: Int = 0,
    val totalSessions: Int = 0,
    val statusBannerFa: String? = null,
) {
    val phaseLabelFa: String
        get() = when {
            mode == FocusTimerMode.FLOWTIME && isRunning -> "جریان آزاد (Flow)"
            mode == FocusTimerMode.FLOWTIME -> "آمادهٔ حرکت"
            else -> when (phase) {
                FocusSession.Phase.FOCUS -> "تمرکز عمیق"
                FocusSession.Phase.BREAK -> "استراحت و تنفس"
                FocusSession.Phase.DONE -> "تکمیل جلسه"
            }
        }

    val displayClockMillis: Long
        get() = if (mode == FocusTimerMode.FLOWTIME) elapsedFlowMillis else remainingMillis
}

/**
 * Flagship Focus Space & Studio Audio controller.
 *
 * Scoped to [AppContainer]'s application lifecycle so the monotonic timer and
 * the 44.1 kHz stereo ambient synthesizer continue running seamlessly when the
 * user navigates between screens.
 */
class FocusViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    private val scope = container.applicationScope
    private var session: FocusSession? = null
    private var ticker: Job? = null
    private var persisted = false

    // Flowtime monotonic tracking
    private var flowStartedAtMillis: Long = -1L
    private var flowElapsedBeforePause: Long = 0L
    private var lastTickSecond: Long = -1L

    init {
        container.audioEngine.ambientVolume = _state.value.ambientVolume
        loadHistory()
    }

    private fun loadHistory() {
        scope.launch(Dispatchers.IO) {
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

    fun setMode(mode: FocusTimerMode) {
        if (_state.value.isRunning) return
        _state.update {
            it.copy(
                mode = mode,
                elapsedFlowMillis = 0L,
                progress = 0f,
                statusBannerFa = null,
            )
        }
        if (mode == FocusTimerMode.PROTOCOL) {
            resetTimerPreview()
        }
    }

    fun selectPreset(preset: FocusProtocolPreset) {
        if (_state.value.isRunning) return
        _state.update {
            it.copy(
                mode = FocusTimerMode.PROTOCOL,
                preset = preset,
                focusMinutes = preset.focusMinutes,
                breakMinutes = preset.breakMinutes,
                rounds = preset.rounds,
                statusBannerFa = null,
            )
        }
        resetTimerPreview()
    }

    fun configure(focusMinutes: Int, breakMinutes: Int, rounds: Int) {
        if (_state.value.isRunning) return
        val clampedFocus = focusMinutes.coerceIn(5, 120)
        val clampedBreak = breakMinutes.coerceIn(1, 30)
        val clampedRounds = rounds.coerceIn(1, 8)
        val matchedPreset = FocusProtocolPreset.entries.firstOrNull {
            it != FocusProtocolPreset.CUSTOM &&
                it.focusMinutes == clampedFocus &&
                it.breakMinutes == clampedBreak &&
                it.rounds == clampedRounds
        } ?: FocusProtocolPreset.CUSTOM
        _state.update {
            it.copy(
                preset = matchedPreset,
                focusMinutes = clampedFocus,
                breakMinutes = clampedBreak,
                rounds = clampedRounds,
                statusBannerFa = null,
            )
        }
        resetTimerPreview()
    }

    fun adjustFocusMinutes(delta: Int) {
        val current = _state.value
        configure(current.focusMinutes + delta, current.breakMinutes, current.rounds)
    }

    fun adjustBreakMinutes(delta: Int) {
        val current = _state.value
        configure(current.focusMinutes, current.breakMinutes + delta, current.rounds)
    }

    fun adjustRounds(delta: Int) {
        val current = _state.value
        configure(current.focusMinutes, current.breakMinutes, current.rounds + delta)
    }

    // ---- Studio Audio Controls ----------------------------------------------

    fun selectSoundscape(soundscape: AmbientSoundscape, playImmediately: Boolean = true) {
        if (soundscape == AmbientSoundscape.OFF) {
            container.audioEngine.setAmbientSoundscape(AmbientSoundscape.OFF)
            _state.update {
                it.copy(
                    selectedSoundscape = AmbientSoundscape.OFF,
                    isAmbientPlaying = false,
                    statusBannerFa = "صدای محیطی خاموش شد",
                )
            }
            return
        }
        _state.update {
            it.copy(
                selectedSoundscape = soundscape,
                isAmbientPlaying = playImmediately,
                statusBannerFa = if (playImmediately) "در حال پخش: ${soundscape.titleFa}" else null,
            )
        }
        if (playImmediately) {
            container.audioEngine.setAmbientSoundscape(soundscape)
        }
    }

    fun toggleAmbientPlayback() {
        val current = _state.value
        if (current.isAmbientPlaying) {
            container.audioEngine.setAmbientSoundscape(AmbientSoundscape.OFF)
            _state.update {
                it.copy(
                    isAmbientPlaying = false,
                    statusBannerFa = "پخش صدای محیطی متوقف شد",
                )
            }
        } else {
            val target = if (current.selectedSoundscape == AmbientSoundscape.OFF) {
                AmbientSoundscape.BROWN_WARM
            } else {
                current.selectedSoundscape
            }
            container.audioEngine.setAmbientSoundscape(target)
            _state.update {
                it.copy(
                    selectedSoundscape = target,
                    isAmbientPlaying = true,
                    statusBannerFa = "در حال پخش استریو: ${target.titleFa}",
                )
            }
        }
    }

    fun setAmbientVolume(volume: Float) {
        val clamped = volume.coerceIn(0.05f, 1.0f)
        container.audioEngine.ambientVolume = clamped
        _state.update { it.copy(ambientVolume = clamped) }
    }

    fun toggleTickSound() {
        val next = !_state.value.tickSoundEnabled
        _state.update {
            it.copy(
                tickSoundEnabled = next,
                statusBannerFa = if (next) "تیک‌تاک ملایم ساعت فعال شد" else "تیک‌تاک ساعت غیرفعال شد",
            )
        }
        if (next) {
            container.audioEngine.playSfx(SfxType.SOFT_TICK, force = true)
        }
    }

    fun previewChime() {
        container.audioEngine.playSfx(SfxType.SESSION_COMPLETE, force = true)
        _state.update {
            it.copy(statusBannerFa = "تست زنگ هارمونیک استودیویی (44.1kHz Stereo) پخش شد")
        }
    }

    // ---- Timer Execution ----------------------------------------------------

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
                elapsedFlowMillis = 0L,
                progress = 0f,
            )
        }
    }

    fun start() {
        if (_state.value.isRunning) return
        val current = _state.value
        persisted = false
        lastTickSecond = -1L

        // Start selected ambient soundscape automatically if not OFF
        if (current.selectedSoundscape != AmbientSoundscape.OFF && !current.isAmbientPlaying) {
            container.audioEngine.setAmbientSoundscape(current.selectedSoundscape)
            _state.update { it.copy(isAmbientPlaying = true) }
        }
        container.audioEngine.playSfx(SfxType.FOCUS_START, force = true)

        if (current.mode == FocusTimerMode.FLOWTIME) {
            flowStartedAtMillis = SystemClock.elapsedRealtime()
            flowElapsedBeforePause = 0L
            _state.update {
                it.copy(
                    isRunning = true,
                    isPaused = false,
                    phase = FocusSession.Phase.FOCUS,
                    elapsedFlowMillis = 0L,
                    progress = 0f,
                    statusBannerFa = "حالت فلو-تایم آغاز شد • تمرکز عمیق",
                )
            }
            startTicker()
            return
        }

        val newSession = FocusSession(current.focusMinutes, current.breakMinutes, current.rounds)
        session = newSession
        newSession.start(SystemClock.elapsedRealtime())
        _state.update {
            it.copy(
                isRunning = true,
                isPaused = false,
                phase = FocusSession.Phase.FOCUS,
                round = 1,
                remainingMillis = current.focusMinutes * 60_000L,
                progress = 0f,
                statusBannerFa = "دور ۱ از ${current.rounds} آغاز شد",
            )
        }
        notifyPhaseChange(newSession)
        startTicker()
    }

    fun togglePause() {
        val current = _state.value
        if (!current.isRunning) return
        val now = SystemClock.elapsedRealtime()

        if (current.mode == FocusTimerMode.FLOWTIME) {
            if (current.isPaused) {
                flowStartedAtMillis = now - flowElapsedBeforePause
                _state.update { it.copy(isPaused = false, statusBannerFa = "ادامهٔ جریان تمرکز") }
                container.audioEngine.playSfx(SfxType.FOCUS_START, force = true)
                startTicker()
            } else {
                flowElapsedBeforePause = (now - flowStartedAtMillis).coerceAtLeast(0L)
                ticker?.cancel()
                _state.update { it.copy(isPaused = true, statusBannerFa = "توقف موقت تایمر") }
            }
            return
        }

        val active = session ?: return
        if (current.isPaused) {
            active.resume(now)
            _state.update { it.copy(isPaused = false, statusBannerFa = "ادامهٔ جلسهٔ تمرکز") }
            container.audioEngine.playSfx(SfxType.FOCUS_START, force = true)
            startTicker()
        } else {
            active.pause(now)
            ticker?.cancel()
            _state.update { it.copy(isPaused = true, statusBannerFa = "توقف موقت تایمر") }
        }
    }

    /**
     * Advances immediately to the next phase (Focus -> Break, Break -> next Focus,
     * or finishes if in the last Focus round).
     */
    fun skipPhase() {
        val current = _state.value
        if (!current.isRunning || current.mode != FocusTimerMode.PROTOCOL) return
        val active = session ?: return
        val now = SystemClock.elapsedRealtime()
        if (active.isPaused) {
            active.resume(now)
        }
        // Fast-forward the current phase deadline
        val jumpMillis = now + active.remainingMillis(now) + 50L
        val changed = active.tick(jumpMillis)
        // Re-anchor phase start to real `now` so the new phase starts cleanly
        active.start(now)
        when (changed) {
            FocusSession.Phase.BREAK -> {
                container.audioEngine.playSfx(SfxType.BREAK_START, force = true)
                notifyPhaseChange(active)
                _state.update {
                    it.copy(
                        isPaused = false,
                        phase = active.phase,
                        round = active.round,
                        remainingMillis = active.remainingMillis(now),
                        progress = 0f,
                        statusBannerFa = "ورود به مرحلهٔ استراحت (${current.breakMinutes} دقیقه)",
                    )
                }
            }
            FocusSession.Phase.FOCUS -> {
                container.audioEngine.playSfx(SfxType.FOCUS_START, force = true)
                notifyPhaseChange(active)
                _state.update {
                    it.copy(
                        isPaused = false,
                        phase = active.phase,
                        round = active.round,
                        remainingMillis = active.remainingMillis(now),
                        progress = 0f,
                        statusBannerFa = "شروع دور ${active.round} از ${active.rounds}",
                    )
                }
            }
            FocusSession.Phase.DONE -> {
                completeProtocolSession(active)
            }
            null -> Unit
        }
    }

    fun stop() {
        val current = _state.value
        ticker?.cancel()

        // In Flowtime mode, stopping after >= 1 minute records the completed focus session
        if (current.mode == FocusTimerMode.FLOWTIME && current.isRunning) {
            val elapsedMinutes = (current.elapsedFlowMillis / 60_000L).toInt()
            if (elapsedMinutes >= 1) {
                persistCompletedMinutes(elapsedMinutes, rounds = 1)
                container.audioEngine.playSfx(SfxType.SESSION_COMPLETE, force = true)
                _state.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        elapsedFlowMillis = 0L,
                        progress = 0f,
                        statusBannerFa = "جلسهٔ فلو-تایم ($elapsedMinutes دقیقه) با موفقیت ثبت شد",
                    )
                }
                loadHistory()
                return
            }
        }

        session = null
        val ctx = container.appContextOrNull()
        if (ctx != null) FocusNotifications.cancel(ctx)
        _state.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                statusBannerFa = null,
            )
        }
        resetTimerPreview()
    }

    private fun startTicker() {
        ticker?.cancel()
        if (MrsadTestHooks.isDeterministic) {
            refreshFromSession()
            return
        }
        ticker = scope.launch {
            while (_state.value.isRunning && !_state.value.isPaused) {
                val now = SystemClock.elapsedRealtime()
                val current = _state.value

                if (current.mode == FocusTimerMode.FLOWTIME) {
                    val elapsed = (now - flowStartedAtMillis).coerceAtLeast(0L)
                    val cycleProgress = ((elapsed % (25 * 60_000L)).toFloat() / (25 * 60_000f)).coerceIn(0f, 1f)
                    val sec = elapsed / 1000L
                    if (current.tickSoundEnabled && sec != lastTickSecond) {
                        lastTickSecond = sec
                        container.audioEngine.playSfx(SfxType.SOFT_TICK, force = true)
                    }
                    _state.update {
                        it.copy(
                            elapsedFlowMillis = elapsed,
                            progress = cycleProgress,
                        )
                    }
                    delay(250)
                    continue
                }

                val active = session ?: break
                val changed = active.tick(now)
                val rem = active.remainingMillis(now)
                val sec = rem / 1000L
                if (current.tickSoundEnabled && active.phase == FocusSession.Phase.FOCUS && sec != lastTickSecond) {
                    lastTickSecond = sec
                    container.audioEngine.playSfx(SfxType.SOFT_TICK, force = true)
                }

                _state.update {
                    it.copy(
                        phase = active.phase,
                        round = active.round,
                        remainingMillis = rem,
                        progress = active.progress(now),
                    )
                }
                when (changed) {
                    FocusSession.Phase.BREAK -> {
                        container.audioEngine.playSfx(SfxType.BREAK_START, force = true)
                        notifyPhaseChange(active)
                        _state.update {
                            it.copy(statusBannerFa = "پایان دور • زمان تنفس و استراحت")
                        }
                    }
                    FocusSession.Phase.FOCUS -> {
                        container.audioEngine.playSfx(SfxType.FOCUS_START, force = true)
                        notifyPhaseChange(active)
                        _state.update {
                            it.copy(statusBannerFa = "آغاز دور ${active.round} از ${active.rounds}")
                        }
                    }
                    FocusSession.Phase.DONE -> {
                        completeProtocolSession(active)
                        return@launch
                    }
                    null -> Unit
                }
                delay(250)
            }
        }
    }

    private fun completeProtocolSession(active: FocusSession) {
        persistCompletedMinutes(active.plannedFocusMinutes, active.rounds)
        container.audioEngine.playSfx(SfxType.SESSION_COMPLETE, force = true)
        notifyPhaseChange(active)
        ticker?.cancel()
        _state.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                phase = FocusSession.Phase.DONE,
                remainingMillis = 0L,
                progress = 1f,
                statusBannerFa = "تبریک! جلسهٔ تمرکز (${active.plannedFocusMinutes} دقیقه) کامل و ثبت شد",
            )
        }
        loadHistory()
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
        FocusNotifications.notifyPhase(
            context = ctx,
            phase = active.phase,
            round = active.round,
            rounds = active.rounds,
        )
    }

    private fun persistCompletedMinutes(minutes: Int, rounds: Int) {
        if (persisted) return
        persisted = true
        scope.launch(Dispatchers.IO) {
            val now = MrsadClock.nowEpochMillis()
            runCatching {
                container.database.activityDao().insert(
                    ActivityEventEntity(
                        module = "FOCUS",
                        dateIso = MrsadClock.isoFor(now),
                        finishedAtMillis = now,
                        minutes = minutes.coerceAtLeast(1),
                        primaryMetric = minutes.coerceIn(1, 100).toDouble(),
                        secondaryMetric = rounds.toDouble(),
                        payload = "rounds=$rounds",
                    ),
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = shared(container) as T
    }

    companion object {
        private val instances = WeakHashMap<AppContainer, FocusViewModel>()

        @Synchronized
        fun shared(container: AppContainer): FocusViewModel =
            instances.getOrPut(container) { FocusViewModel(container) }
    }
}
