package com.mrsadhr.test

import com.mrsadhr.app.Action
import com.mrsadhr.app.AppController
import com.mrsadhr.core.format.TimeFormat
import com.mrsadhr.core.model.Language
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.model.TimePart
import com.mrsadhr.core.time.ManualTimeSource
import com.mrsadhr.data.KeyValueStore
import com.mrsadhr.data.MemoryStore
import com.mrsadhr.platform.HapticKind
import com.mrsadhr.platform.NullPlatformBridge
import com.mrsadhr.platform.PlatformBridge
import com.mrsadhr.platform.SoundEngine
import com.mrsadhr.themes.ThemeId

/** Records what the controller asks the platform to do, so the wiring itself can be asserted. */
class RecordingPlatform : PlatformBridge {
    val events = mutableListOf<String>()
    var lastProgress: Pair<Long, Long>? = null
    var screenOn: Boolean? = null
    var ticks = 0

    override fun onTimerStateChanged(running: Boolean, remainingMs: Long, durationMs: Long) {
        events += "state:$running"
        lastProgress = remainingMs to durationMs
    }

    override fun onTimerCompleted(soundId: String, volume: Float, playSound: Boolean) {
        events += "completed:$soundId:$playSound"
    }

    override fun onTimerDismissed() { events += "dismissed" }
    override fun haptic(kind: HapticKind) { events += "haptic:$kind" }
    override fun keepScreenOn(enabled: Boolean) { screenOn = enabled; events += "screen:$enabled" }
    override fun onTimerTick(remainingMs: Long, durationMs: Long) { ticks++ }
}

class RecordingSounds : SoundEngine {
    val played = mutableListOf<String>()
    var stops = 0
    override fun playPreview(soundId: String, volume: Float) { played += "preview:$soundId" }
    override fun stopPreview() { stops++ }
    override fun playCompletion(soundId: String, volume: Float) { played += "completion:$soundId" }
    override fun stopCompletion() { stops++ }
    override fun release() {}
}

object ControllerTests {

    private fun controller(
        time: ManualTimeSource = ManualTimeSource(),
        store: KeyValueStore = MemoryStore(),
        platform: PlatformBridge = NullPlatformBridge,
        sounds: SoundEngine = RecordingSounds(),
    ): Pair<AppController, ManualTimeSource> {
        val t = time
        return AppController(t, store, platform, sounds) to t
    }

    fun register() {
        Suite.test("AppController", "start, pause and reset drive the platform bridge") {
            val platform = RecordingPlatform()
            val (c, time) = controller(platform = platform)
            c.dispatch(Action.SetDuration(60_000))
            c.dispatch(Action.TimerStart)
            expect(c.state().timer.running, "timer runs after start")
            expect(platform.events.contains("state:true"), "bridge told about the start")
            expectEq(60_000L, platform.lastProgress?.second, "bridge received the duration")
            time.advance(10_000)
            c.dispatch(Action.TimerPause)
            expect(!c.state().timer.running, "paused")
            expect(platform.events.contains("state:false"), "bridge told about the pause")
            c.dispatch(Action.TimerReset)
            expectEq(60_000L, c.state().timer.remainingMs, "reset restores duration")
            expect(platform.events.contains("dismissed"), "reset dismisses any completion")
        }

        Suite.test("AppController", "completion fires once with the configured sound") {
            val platform = RecordingPlatform()
            val sounds = RecordingSounds()
            val (c, time) = controller(platform = platform, sounds = sounds)
            c.dispatch(Action.SetDuration(2_000))
            c.dispatch(Action.TimerStart)
            time.advance(2_100)
            expect(c.tick(), "tick reports the completion")
            expect(c.state().completion != null, "completion state exposed")
            expectEq(0L, c.state().timer.remainingMs, "remaining is zero")
            expectEq(1, platform.events.count { it.startsWith("completed:digital-chime") }, "bridge told exactly once")
            expect(platform.events.contains("completed:digital-chime:true"), "sound allowed by the settings")
            time.advance(5_000)
            expect(!c.tick(), "no second completion")
            expectEq(1, platform.events.count { it.startsWith("completed:") }, "still exactly one completion")
        }

        Suite.test("AppController", "repeat restarts the same duration after completion") {
            val (c, time) = controller()
            c.dispatch(Action.SetDuration(3_000))
            c.dispatch(Action.TimerStart)
            time.advance(3_100)
            c.tick()
            c.dispatch(Action.TimerRepeat)
            expect(c.state().completion == null, "overlay cleared")
            expect(c.state().timer.running, "running again")
            expectEq(3_000L, c.state().timer.durationMs, "same duration")
            time.advance(1_000)
            expectEq(2_000L, c.state().timer.remainingMs, "counting again")
        }

        Suite.test("AppController", "dismiss clears completion and restores the dial") {
            val (c, time) = controller()
            c.dispatch(Action.SetDuration(1_000))
            c.dispatch(Action.TimerStart)
            time.advance(1_100)
            c.tick()
            c.dispatch(Action.DismissCompletion)
            expect(c.state().completion == null, "completion dismissed")
            expect(!c.state().timer.running, "not running after dismiss")
            expectEq(1_000L, c.state().timer.remainingMs, "dial shows the full duration again")
        }

        Suite.test("AppController", "stepper carries between fields and clamps at the limits") {
            val (c, _) = controller()
            c.dispatch(Action.SetDuration(59 * 60_000L + 59_000L))
            c.dispatch(Action.StepPart(TimePart.Seconds, 1))
            expectEq(Triple(1, 0, 0), TimeFormat.parts(c.state().timer.durationMs), "59:59 + 1s carries into the hour")
            c.dispatch(Action.StepPart(TimePart.Seconds, -1))
            expectEq(Triple(0, 59, 59), TimeFormat.parts(c.state().timer.durationMs), "-1s borrows from the hour")
            c.dispatch(Action.SetDuration(99 * 3_600_000L + 59 * 60_000L + 59_000L))
            c.dispatch(Action.StepPart(TimePart.Hours, 1))
            expectEq(99, TimeFormat.parts(c.state().timer.durationMs).first, "hours clamp at 99")
            c.dispatch(Action.SetDuration(0))
            c.dispatch(Action.StepPart(TimePart.Minutes, -1))
            expectEq(0L, c.state().timer.durationMs, "cannot step below zero")
        }

        Suite.test("AppController", "presets: select, save, reject duplicate, delete") {
            val (c, _) = controller()
            c.dispatch(Action.SelectPreset(300))
            expectEq(300_000L, c.state().timer.durationMs, "preset applied")
            c.dispatch(Action.SetDuration(7 * 60_000L))
            c.dispatch(Action.SavePreset)
            val presets = c.state().settings.presets
            expectEq(5, presets.size, "custom preset added")
            expect(presets.any { it.seconds == 420L }, "saved with the right duration")
            c.dispatch(Action.SavePreset)
            expectEq(5, c.state().settings.presets.size, "duplicate preset is rejected")
            val id = c.state().settings.presets.last().id
            c.dispatch(Action.DeletePreset(id))
            expectEq(4, c.state().settings.presets.size, "preset deleted")
        }

        Suite.test("AppController", "presets are capped") {
            val (c, _) = controller()
            for (i in 1..20) {
                c.dispatch(Action.SetDuration(i * 37_000L + 1_000L))
                c.dispatch(Action.SavePreset)
            }
            expectEq(AppController.MAX_PRESETS, c.state().settings.presets.size, "preset cap respected")
        }

        Suite.test("AppController", "theme, language, sound and haptics persist across a restart") {
            val store = MemoryStore()
            val (c, _) = controller(store = store)
            c.dispatch(Action.SetTheme(ThemeId.Aurora))
            c.dispatch(Action.SetLanguage(Language.Persian))
            c.dispatch(Action.SelectSound("cinematic-impact"))
            c.dispatch(Action.SetVolume(0.42f))
            c.dispatch(Action.ToggleHaptics)
            c.dispatch(Action.ToggleFocusMode)
            c.dispatch(Action.SetDuration(11 * 60_000L))
            c.dispatch(Action.SavePreset)

            val (restarted, _) = controller(store = store)
            val s = restarted.state().settings
            expectEq(ThemeId.Aurora, s.theme, "theme restored")
            expectEq(Language.Persian, s.language, "language restored")
            expectEq("cinematic-impact", s.soundId, "sound restored")
            expectNear(0.42f, s.soundVolume, 0.001f, "volume restored")
            expect(!s.haptics, "haptics flag restored")
            expect(!s.focusMode, "focus flag restored")
            expect(s.presets.any { it.seconds == 660L }, "custom preset restored")
        }

        Suite.test("AppController", "a running timer survives process death with the correct remaining") {
            val store = MemoryStore()
            val time = ManualTimeSource()
            val (c, _) = controller(time = time, store = store)
            c.dispatch(Action.SetDuration(600_000))
            c.dispatch(Action.TimerStart)
            time.advance(45_000)

            val (restarted, _) = controller(time = time, store = store)
            expect(restarted.state().timer.running, "restored as running")
            expectEq(555_000L, restarted.state().timer.remainingMs, "remaining accounts for the gap")
        }

        Suite.test("AppController", "restoring after the deadline shows the completion state") {
            val store = MemoryStore()
            val time = ManualTimeSource()
            val (c, _) = controller(time = time, store = store)
            c.dispatch(Action.SetDuration(1_000))
            c.dispatch(Action.TimerStart)
            time.advance(5_000)
            val (restarted, _) = controller(time = time, store = store)
            expect(restarted.state().completion != null, "completion restored")
            expectEq(0L, restarted.state().timer.remainingMs, "zero remaining")
        }

        Suite.test("AppController", "stopwatch mode: start, lap, pause, reset") {
            val (c, time) = controller()
            c.dispatch(Action.StopwatchToggle)
            expectEq(Mode.Stopwatch, c.state().mode, "switched to stopwatch")
            expect(c.state().stopwatch.running, "stopwatch running")
            time.advance(2_000)
            c.dispatch(Action.StopwatchLap)
            time.advance(1_000)
            c.dispatch(Action.StopwatchLap)
            expectEq(2, c.state().stopwatch.laps.size, "two laps")
            c.dispatch(Action.StopwatchToggle)
            expect(!c.state().stopwatch.running, "paused")
            c.dispatch(Action.StopwatchReset)
            expectEq(0L, c.state().stopwatch.elapsedMs, "reset")
            expectEq(0, c.state().stopwatch.laps.size, "laps cleared")
        }

        Suite.test("AppController", "sound preview starts and expires on its own") {
            val sounds = RecordingSounds()
            val (c, time) = controller(sounds = sounds)
            c.dispatch(Action.PreviewSound("minimal-bell"))
            expectEq("minimal-bell", c.previewingSound(), "preview reported")
            expect(sounds.played.contains("preview:minimal-bell"), "engine asked to play")
            time.advance(4_000)
            expectEq(null, c.previewingSound(), "preview expires with the clip")
        }

        Suite.test("AppController", "haptics respect the setting") {
            val platform = RecordingPlatform()
            val (c, _) = controller(platform = platform)
            c.dispatch(Action.TimerStart)
            expect(platform.events.any { it.startsWith("haptic:") }, "haptic fired when enabled")
            platform.events.clear()
            c.dispatch(Action.ToggleHaptics)
            c.dispatch(Action.TimerPause)
            c.dispatch(Action.TimerStart)
            expect(platform.events.none { it.startsWith("haptic:") }, "no haptics when disabled")
        }

        Suite.test("AppController", "observers receive state and can unsubscribe") {
            val (c, _) = controller()
            var updates = 0
            val listener: (com.mrsadhr.core.model.AppState) -> Unit = { updates++ }
            c.observe(listener)
            expectEq(1, updates, "observer called immediately")
            c.dispatch(Action.TimerStart)
            expectEq(2, updates, "observer called on change")
            c.removeObserver(listener)
            c.dispatch(Action.TimerPause)
            expectEq(2, updates, "no updates after unsubscribe")
        }

        Suite.test("AppController", "settings are not rewritten when nothing changes") {
            val counting = object : KeyValueStore {
                private val inner = MemoryStore()
                var writes = 0
                var settingsWrites = 0
                override fun getString(key: String, fallback: String) = inner.getString(key, fallback)
                override fun putString(key: String, value: String) {
                    writes++
                    if (key.startsWith("settings.")) settingsWrites++
                    inner.putString(key, value)
                }
                override fun getLong(key: String, fallback: Long) = inner.getLong(key, fallback)
                override fun putLong(key: String, value: Long) {
                    writes++
                    if (key.startsWith("settings.")) settingsWrites++
                    inner.putLong(key, value)
                }
                override fun getFloat(key: String, fallback: Float) = inner.getFloat(key, fallback)
                override fun putFloat(key: String, value: Float) {
                    writes++
                    if (key.startsWith("settings.")) settingsWrites++
                    inner.putFloat(key, value)
                }
                override fun getBool(key: String, fallback: Boolean) = inner.getBool(key, fallback)
                override fun putBool(key: String, value: Boolean) {
                    writes++
                    if (key.startsWith("settings.")) settingsWrites++
                    inner.putBool(key, value)
                }
                override fun remove(key: String) { inner.remove(key) }
            }
            val (c, _) = controller(store = counting)
            counting.writes = 0
            counting.settingsWrites = 0
            c.dispatch(Action.TimerStart)
            c.dispatch(Action.TimerPause)
            expectEq(0, counting.settingsWrites, "timer actions must not rewrite settings")
            c.dispatch(Action.SetTheme(ThemeId.Aurora))
            expect(counting.settingsWrites > 0, "a settings change is persisted")
        }
    }
}
