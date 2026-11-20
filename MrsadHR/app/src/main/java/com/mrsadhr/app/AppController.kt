package com.mrsadhr.app

import com.mrsadhr.core.AppFlags
import com.mrsadhr.core.format.TimeFormat
import com.mrsadhr.core.model.AppState
import com.mrsadhr.core.model.CompletionState
import com.mrsadhr.core.model.Language
import com.mrsadhr.core.model.Lap
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.model.Preset
import com.mrsadhr.core.model.Settings
import com.mrsadhr.core.model.TimePart
import com.mrsadhr.core.time.TimeSource
import com.mrsadhr.data.KeyValueStore
import com.mrsadhr.platform.HapticKind
import com.mrsadhr.platform.PlatformBridge
import com.mrsadhr.platform.SoundEngine
import com.mrsadhr.sounds.SoundCatalog
import com.mrsadhr.stopwatch.StopwatchEngine
import com.mrsadhr.themes.ThemeId
import com.mrsadhr.timer.CountdownEngine
import kotlin.math.roundToLong

/**
 * Single source of truth for the running application.
 *
 * The controller owns the engines, settings and completion state, persists everything that must
 * survive process death, and pushes state changes to the UI. It is intentionally free of Android
 * imports so it can be unit tested on a plain JVM and driven by the preview harness.
 */
class AppController(
    private val time: TimeSource,
    private val store: KeyValueStore,
    private val platform: PlatformBridge,
    private val sounds: SoundEngine,
) {

    private val timer = CountdownEngine(time)
    private val stopwatch = StopwatchEngine(time)

    private var settings: Settings = Settings()
    private var mode: Mode = Mode.Timer
    private var completion: CompletionState? = null
    private var previewingId: String? = null
    private var previewingUntil = 0L
    private val listeners = mutableListOf<(AppState) -> Unit>()

    private var lastHeartbeatSecond: Long = -1L
    private var lastTimerPersist = 0L

    init {
        load()
    }

    // ------------------------------------------------------------------ public API

    fun state(): AppState = AppState(
        settings = settings,
        mode = mode,
        timer = timer.snapshot(),
        stopwatch = stopwatch.snapshot(),
        completion = completion,
    )

    fun observe(listener: (AppState) -> Unit) {
        listeners += listener
        listener(state())
    }

    fun removeObserver(listener: (AppState) -> Unit) {
        listeners -= listener
    }

    /** The sound that is auditioning right now, or null once its clip has finished. */
    fun previewingSound(): String? {
        if (previewingId != null && time.monoMs() > previewingUntil) previewingId = null
        return previewingId
    }

    /** Cheap, allocation-light poll used by the render loop and the foreground service. */
    fun tick(): Boolean {
        var changed = false
        if (timer.hasExpired()) {
            timer.finish()
            onCompleted()
            changed = true
        } else if (timer.isRunning) {
            val second = time.monoMs() / 1000L
            if (second != lastHeartbeatSecond) {
                lastHeartbeatSecond = second
                platform.onTimerTick(timer.remainingMs(), timer.durationMs)
            }
        }
        return changed
    }

    fun dispatch(action: Action) {
        val settingsBefore = settings
        when (action) {
            is Action.SetMode -> mode = action.mode

            Action.TimerStart -> startTimer()
            Action.TimerPause -> pauseTimer()
            Action.TimerReset -> {
                timer.reset()
                completion = null
                platform.onTimerStateChanged(false, timer.remainingMs(), timer.durationMs)
                platform.onTimerDismissed()
                persistTimer()
                haptic(HapticKind.Light)
            }
            Action.TimerRepeat -> {
                completion = null
                timer.setDuration(timer.durationMs)
                startTimer()
            }
            Action.DismissCompletion -> {
                completion = null
                timer.dismissCompletion()
                platform.onTimerDismissed()
                persistTimer()
            }

            is Action.SetDuration -> {
                timer.setDuration(action.ms)
                completion = null
                persistTimer()
            }
            is Action.StepPart -> stepPart(action.part, action.delta)
            is Action.SelectPreset -> {
                timer.setDuration(action.seconds * 1000L)
                completion = null
                persistTimer()
                haptic(HapticKind.Light)
            }
            Action.SavePreset -> saveCurrentPreset()
            is Action.DeletePreset -> deletePreset(action.id)
            is Action.UpdatePreset -> updatePreset(action.id)

            is Action.SetTheme -> settings = settings.copy(theme = action.theme)
            is Action.SetLanguage -> settings = settings.copy(language = action.language)

            Action.ToggleHaptics -> settings = settings.copy(haptics = !settings.haptics)
            Action.ToggleSound -> settings = settings.copy(soundEnabled = !settings.soundEnabled)
            is Action.SetVolume -> settings = settings.copy(soundVolume = action.volume.coerceIn(0f, 1f))
            is Action.SelectSound -> {
                settings = settings.copy(soundId = action.id)
                previewSound(action.id)
            }
            is Action.PreviewSound -> previewSound(action.id)
            Action.StopPreview -> stopPreview()

            Action.ToggleFocusMode -> settings = settings.copy(focusMode = !settings.focusMode)
            Action.ToggleReducedMotion -> {
                settings = settings.copy(reducedMotion = !settings.reducedMotion)
                AppFlags.reducedMotion = settings.reducedMotion
            }
            Action.ToggleKeepScreenOn -> settings = settings.copy(keepScreenOn = !settings.keepScreenOn)
            Action.ToggleNotifications -> settings = settings.copy(notifications = !settings.notifications)

            Action.StopwatchToggle -> {
                mode = Mode.Stopwatch
                if (stopwatch.snapshot().running) stopwatch.pause() else stopwatch.start()
                haptic(HapticKind.Light)
                persistStopwatch()
            }
            Action.StopwatchReset -> {
                stopwatch.reset()
                haptic(HapticKind.Medium)
                persistStopwatch()
            }
            Action.StopwatchLap -> {
                stopwatch.lap()
                haptic(HapticKind.Light)
                persistStopwatch()
            }
            Action.StopwatchClearLaps -> {
                stopwatch.clearLaps()
                persistStopwatch()
            }
        }
        if (settings !== settingsBefore) persistSettings()
        notifyListeners()
    }

    // ----------------------------------------------------------------- timer verbs

    private fun startTimer() {
        val previous = timer.remainingMs()
        if (!timer.start()) return
        completion = null
        platform.onTimerStateChanged(true, timer.remainingMs(), timer.durationMs)
        if (previous != timer.remainingMs()) stopPreview()
        haptic(HapticKind.Medium)
        persistTimer()
    }

    private fun pauseTimer() {
        if (!timer.pause()) return
        platform.onTimerStateChanged(false, timer.remainingMs(), timer.durationMs)
        haptic(HapticKind.Light)
        persistTimer()
    }

    private fun onCompleted() {
        completion = CompletionState(time.monoMs(), timer.durationMs)
        platform.onTimerStateChanged(false, 0L, timer.durationMs)
        val s = settings
        platform.onTimerCompleted(s.soundId, s.soundVolume, s.soundEnabled)
        haptic(HapticKind.Success)
        persistTimer()
        notifyListeners()
    }

    /**
     * Steps one unit with carry/borrow semantics: 0:59 +1s becomes 1:00, and the total is clamped to
     * the supported range so a long press can never wrap a 5 minute timer back to zero.
     */
    private fun stepPart(part: TimePart, delta: Int) {
        val unitMs = when (part) {
            TimePart.Hours -> 3_600_000L
            TimePart.Minutes -> 60_000L
            TimePart.Seconds -> 1_000L
        }
        val total = (timer.durationMs + delta * unitMs)
            .coerceIn(0L, CountdownEngine.MAX_DURATION_MS)
        timer.setDuration(total)
        completion = null
        persistTimer(allowThrottle = true)
    }

    // ---------------------------------------------------------------- presets

    private fun saveCurrentPreset() {
        val seconds = (timer.durationMs / 1000.0).roundToLong()
        if (seconds <= 0L) return
        if (settings.presets.any { it.seconds == seconds }) return
        if (settings.presets.size >= MAX_PRESETS) return
        val nextNumber = (settings.presets.mapNotNull { it.id.removePrefix("p").toIntOrNull() }.maxOrNull() ?: 0) + 1
        settings = settings.copy(presets = settings.presets + Preset("p$nextNumber", seconds))
        haptic(HapticKind.Success)
    }

    private fun deletePreset(id: String) {
        settings = settings.copy(presets = settings.presets.filterNot { it.id == id })
        haptic(HapticKind.Warning)
    }

    private fun updatePreset(id: String) {
        val seconds = (timer.durationMs / 1000.0).roundToLong()
        if (seconds <= 0L) return
        settings = settings.copy(presets = settings.presets.map { if (it.id == id) it.copy(seconds = seconds) else it })
        haptic(HapticKind.Success)
    }

    // ---------------------------------------------------------------- sound

    private fun previewSound(id: String) {
        previewingId = id
        previewingUntil = time.monoMs() + SoundCatalog.byId(id).durationMs + 120L
        sounds.playPreview(id, settings.soundVolume)
    }

    private fun stopPreview() {
        previewingId = null
        previewingUntil = 0L
        sounds.stopPreview()
    }

    fun releaseSounds() {
        sounds.release()
    }

    private fun haptic(kind: HapticKind) {
        if (settings.haptics) platform.haptic(kind)
    }

    private fun notifyListeners() {
        val s = state()
        AppFlags.reducedMotion = s.settings.reducedMotion
        listeners.toList().forEach { it(s) }
    }

    // ------------------------------------------------------------ persistence

    private fun persistSettings() {
        val s = settings
        store.putString(KEY_THEME, s.theme.key)
        store.putString(KEY_LANGUAGE, s.language.key)
        store.putBool(KEY_HAPTICS, s.haptics)
        store.putBool(KEY_SOUND_ENABLED, s.soundEnabled)
        store.putFloat(KEY_VOLUME, s.soundVolume)
        store.putString(KEY_SOUND_ID, s.soundId)
        store.putBool(KEY_FOCUS, s.focusMode)
        store.putBool(KEY_REDUCED_MOTION, s.reducedMotion)
        store.putBool(KEY_KEEP_SCREEN, s.keepScreenOn)
        store.putBool(KEY_NOTIFICATIONS, s.notifications)
        store.putString(KEY_PRESETS, s.presets.joinToString("|") { "${it.id}:${it.seconds}" })
    }

    /**
     * Timer state is written on every transition. Repeated writes caused by holding a stepper
     * button are throttled so a long press cannot flood the disk with saves.
     */
    private fun persistTimer(allowThrottle: Boolean = false) {
        val now = time.monoMs()
        if (allowThrottle && now - lastTimerPersist < 180L) return
        lastTimerPersist = now
        val p = timer.persist()
        store.putLong(KEY_TIMER_DURATION, p.durationMs)
        store.putLong(KEY_TIMER_REMAINING, p.remainingMs)
        store.putBool(KEY_TIMER_RUNNING, p.running)
        store.putBool(KEY_TIMER_COMPLETED, p.completed)
        store.putLong(KEY_TIMER_DEADLINE, p.deadlineMono)
        store.putLong(KEY_TIMER_SAVED_MONO, p.savedMono)
        store.putLong(KEY_TIMER_SAVED_WALL, p.savedWall)
    }

    private fun persistStopwatch() {
        val p = stopwatch.persist()
        store.putBool(KEY_SW_RUNNING, p.running)
        store.putLong(KEY_SW_ELAPSED, p.elapsed)
        store.putString(KEY_SW_LAPS, p.laps.joinToString("|") { "${it.index}:${it.totalMs}:${it.splitMs}" })
    }

    private fun load() {
        settings = Settings(
            theme = ThemeId.fromKey(store.getString(KEY_THEME, ThemeId.Obsidian.key)),
            language = Language.fromKey(store.getString(KEY_LANGUAGE, Language.English.key)),
            haptics = store.getBool(KEY_HAPTICS, true),
            soundEnabled = store.getBool(KEY_SOUND_ENABLED, true),
            soundVolume = store.getFloat(KEY_VOLUME, 0.85f),
            soundId = store.getString(KEY_SOUND_ID, "digital-chime"),
            focusMode = store.getBool(KEY_FOCUS, true),
            reducedMotion = store.getBool(KEY_REDUCED_MOTION, false),
            keepScreenOn = store.getBool(KEY_KEEP_SCREEN, true),
            notifications = store.getBool(KEY_NOTIFICATIONS, true),
            presets = parsePresets(store.getString(KEY_PRESETS, "")),
        )
        AppFlags.reducedMotion = settings.reducedMotion

        timer.restore(
            CountdownEngine.Persisted(
                durationMs = store.getLong(KEY_TIMER_DURATION, CountdownEngine.DEFAULT_DURATION_MS),
                remainingMs = store.getLong(KEY_TIMER_REMAINING, CountdownEngine.DEFAULT_DURATION_MS),
                running = store.getBool(KEY_TIMER_RUNNING, false),
                completed = store.getBool(KEY_TIMER_COMPLETED, false),
                deadlineMono = store.getLong(KEY_TIMER_DEADLINE, 0L),
                savedMono = store.getLong(KEY_TIMER_SAVED_MONO, 0L),
                savedWall = store.getLong(KEY_TIMER_SAVED_WALL, 0L),
            ),
        )
        if (timer.snapshot().completed) {
            completion = CompletionState(time.monoMs(), timer.durationMs)
        }

        stopwatch.restore(
            StopwatchEngine.Persisted(
                running = store.getBool(KEY_SW_RUNNING, false),
                elapsed = store.getLong(KEY_SW_ELAPSED, 0L),
                startedMono = time.monoMs(),
                laps = parseLaps(store.getString(KEY_SW_LAPS, "")),
            ),
        )
    }

    private fun parsePresets(raw: String): List<Preset> {
        if (raw.isBlank()) return Settings().presets
        val parsed = raw.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val secs = parts[1].toLongOrNull() ?: return@mapNotNull null
            if (secs <= 0L) return@mapNotNull null
            Preset(parts[0], secs)
        }
        return parsed.ifEmpty { Settings().presets }
    }

    private fun parseLaps(raw: String): List<Lap> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) return@mapNotNull null
            Lap(
                index = parts[0].toIntOrNull() ?: return@mapNotNull null,
                totalMs = parts[1].toLongOrNull() ?: return@mapNotNull null,
                splitMs = parts[2].toLongOrNull() ?: return@mapNotNull null,
            )
        }
    }

    companion object {
        const val MAX_PRESETS = 8

        private const val KEY_THEME = "settings.theme"
        private const val KEY_LANGUAGE = "settings.language"
        private const val KEY_HAPTICS = "settings.haptics"
        private const val KEY_SOUND_ENABLED = "settings.soundEnabled"
        private const val KEY_VOLUME = "settings.volume"
        private const val KEY_SOUND_ID = "settings.soundId"
        private const val KEY_FOCUS = "settings.focus"
        private const val KEY_REDUCED_MOTION = "settings.reducedMotion"
        private const val KEY_KEEP_SCREEN = "settings.keepScreenOn"
        private const val KEY_NOTIFICATIONS = "settings.notifications"
        private const val KEY_PRESETS = "settings.presets"

        private const val KEY_TIMER_DURATION = "timer.duration"
        private const val KEY_TIMER_REMAINING = "timer.remaining"
        private const val KEY_TIMER_RUNNING = "timer.running"
        private const val KEY_TIMER_COMPLETED = "timer.completed"
        private const val KEY_TIMER_DEADLINE = "timer.deadline"
        private const val KEY_TIMER_SAVED_MONO = "timer.savedMono"
        private const val KEY_TIMER_SAVED_WALL = "timer.savedWall"

        private const val KEY_SW_RUNNING = "stopwatch.running"
        private const val KEY_SW_ELAPSED = "stopwatch.elapsed"
        private const val KEY_SW_LAPS = "stopwatch.laps"
    }
}
