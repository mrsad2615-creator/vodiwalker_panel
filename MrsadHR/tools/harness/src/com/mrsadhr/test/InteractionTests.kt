package com.mrsadhr.test

import com.mrsadhr.app.Action
import com.mrsadhr.core.model.Language
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.model.TimePart
import com.mrsadhr.themes.ThemeId
import com.mrsadhr.ui.ScreenId

/**
 * Interaction tests: synthetic touches against the real interface, plus render level assertions on
 * the frames those touches produce. Nothing here is mocked except the clock, the platform bridge and
 * the audio engine.
 */
object InteractionTests {

    fun register() {
        Suite.test("Interaction: timer", "the primary button starts and pauses the countdown") {
            val h = StageHarness()
            expect(h.tap("primary"), "primary button must be tappable")
            expect(h.controller.state().timer.running, "tap started the timer")
            h.settle(4)
            expect(h.tap("primary"), "primary button still tappable while running")
            expect(!h.controller.state().timer.running, "second tap paused it")
        }

        Suite.test("Interaction: timer", "the reset button restores the dial") {
            val h = StageHarness()
            h.tap("primary")
            h.settle(30)
            h.tap("reset")
            val s = h.controller.state().timer
            expect(!s.running, "reset stopped the timer")
            expectEq(s.durationMs, s.remainingMs, "remaining back to the duration")
        }

        Suite.test("Interaction: timer", "steppers adjust hours, minutes and seconds") {
            val h = StageHarness()
            h.controller.dispatch(Action.SetDuration(0))
            h.settle(6)
            h.tap("st-m-plus")
            h.settle(2)
            expectEq(Triple(0, 1, 0), com.mrsadhr.core.format.TimeFormat.parts(h.controller.state().timer.durationMs), "minute added")
            h.tap("st-s-plus")
            h.settle(2)
            expectEq(Triple(0, 1, 1), com.mrsadhr.core.format.TimeFormat.parts(h.controller.state().timer.durationMs), "second added")
            h.tap("st-h-plus")
            h.settle(2)
            expectEq(Triple(1, 1, 1), com.mrsadhr.core.format.TimeFormat.parts(h.controller.state().timer.durationMs), "hour added")
        }

        Suite.test("Interaction: timer", "quick presets select durations") {
            val h = StageHarness()
            h.tap("preset-p2")
            expectEq(300_000L, h.controller.state().timer.durationMs, "5m preset applied")
        }

        Suite.test("Interaction: timer", "the mode switch reaches the stopwatch and back") {
            val h = StageHarness()
            expect(h.tap("mode-stopwatch"), "stopwatch segment tappable")
            h.settle(20)
            expectEq(Mode.Stopwatch, h.controller.state().mode, "mode switched")
            expect(h.tap("primary"), "stopwatch primary tappable")
            expect(h.controller.state().stopwatch.running, "stopwatch started")
            h.settle(10)
            expect(h.tap("lap"), "lap button tappable")
            expectEq(1, h.controller.state().stopwatch.laps.size, "lap recorded")
            h.tap("mode-timer")
            h.settle(20)
            expectEq(Mode.Timer, h.controller.state().mode, "back to the timer")
        }

        Suite.test("Interaction: navigation", "header opens themes, sounds and settings and back returns home") {
            val h = StageHarness()
            expect(h.tap("hdr-0"), "themes shortcut tappable")
            h.settle(30)
            expectEq(ScreenId.Themes, h.stage.screen, "themes screen open")
            expect(h.stage.back(h.time.monoMs()), "can navigate back")
            h.settle(30)
            expectEq(ScreenId.Timer, h.stage.screen, "back on the timer")

            h.tap("hdr-1")
            h.settle(30)
            expectEq(ScreenId.Sounds, h.stage.screen, "sounds screen open")
            h.stage.back(h.time.monoMs())
            h.settle(30)

            h.tap("hdr-2")
            h.settle(30)
            expectEq(ScreenId.Settings, h.stage.screen, "settings screen open")
        }

        Suite.test("Interaction: themes", "selecting a theme card applies and persists it") {
            val h = StageHarness()
            h.tap("hdr-0")
            h.settle(40)
            expect(h.tap("theme-pure"), "pure theme card tappable")
            h.settle(40)
            expectEq(ThemeId.Pure, h.controller.state().settings.theme, "theme applied")
            // The light theme must actually repaint the whole surface, not just swap a token.
            val lightMean = meanLuma(h)
            expect(lightMean > 150f, "light theme paints a light surface (mean luma $lightMean)")
            val restarted = StageHarness(existingStore = h.store, existingTime = h.time)
            expectEq(ThemeId.Pure, restarted.controller.state().settings.theme, "theme persisted for the next launch")
        }

        Suite.test("Interaction: sounds", "a row selects the sound and its play button previews it") {
            val h = StageHarness()
            h.tap("hdr-1")
            h.settle(40)
            expect(h.tap("snd-preview-cinematic-impact"), "preview button tappable")
            expectEq("cinematic-impact", h.controller.previewingSound(), "preview started")
            expect(h.sounds.played.any { it == "preview:cinematic-impact" }, "engine told to play it")
            h.settle(10)
            expect(h.tap("snd-select-cinematic-impact"), "row tappable")
            expectEq("cinematic-impact", h.controller.state().settings.soundId, "sound selected")
            h.settle(20)
            expectEq("cinematic-impact", h.controller.state().settings.soundId, "selection persisted in state")
        }

        Suite.test("Interaction: settings", "language, focus and reduced motion toggle from the UI") {
            val h = StageHarness()
            h.tap("hdr-2")
            h.settle(40)
            expect(h.tap("lang-fa"), "persian segment tappable")
            h.settle(20)
            expectEq(Language.Persian, h.controller.state().settings.language, "language switched to Persian")
            expect(h.tap("row-focus"), "focus row tappable")
            h.settle(10)
            expect(!h.controller.state().settings.focusMode, "focus mode disabled")
            expect(h.tap("row-motion"), "reduced motion row tappable")
            h.settle(10)
            expect(h.controller.state().settings.reducedMotion, "reduced motion enabled")
        }

        Suite.test("Interaction: focus mode", "hides everything but the instrument while running") {
            val h = StageHarness()
            h.controller.dispatch(Action.SetDuration(300_000))
            h.settle(4)
            h.tap("primary")
            h.settle(40)
            expect(h.stage.focusActive, "focus mode active")
            val visible = h.targets().keys
            expect(!visible.contains("st-m-plus"), "steppers hidden in focus mode")
            expect(!visible.contains("preset-p2"), "presets hidden in focus mode")
            expect(visible.contains("focus-exit"), "focus exit control available")
            expect(h.tap("focus-exit"), "focus exit tappable")
            h.settle(40)
            expect(!h.stage.focusActive, "focus mode exited")
        }

        Suite.test("Interaction: completion", "the overlay appears at zero and both actions work") {
            val h = StageHarness()
            h.controller.dispatch(Action.SetDuration(1_000))
            h.settle(4)
            h.tap("primary")
            h.time.advance(1_200)
            h.settle(80)
            expect(h.controller.state().completion != null, "controller reached completion")
            expect(h.stage.completionOverlay.visible, "overlay visible")
            val targets = h.targets()
            expect(targets.containsKey("cmp-again"), "again button published")
            expect(targets.containsKey("cmp-done"), "done button published")
            h.tap("cmp-done")
            h.settle(30)
            expect(h.controller.state().completion == null, "done dismissed the overlay")
            expect(!h.stage.completionOverlay.visible, "overlay hidden again")
        }

        Suite.test("Interaction: completion", "again restarts the same countdown") {
            val h = StageHarness()
            h.controller.dispatch(Action.SetDuration(2_000))
            h.settle(4)
            h.tap("primary")
            h.time.advance(2_200)
            h.settle(80)
            h.tap("cmp-again")
            h.settle(10)
            val s = h.controller.state()
            expect(s.completion == null, "overlay dismissed by again")
            expect(s.timer.running, "countdown running again")
            expectEq(2_000L, s.timer.durationMs, "same duration")
        }

        Suite.test("Interaction: scrolling", "a short screen scrolls to reach the rows below the fold") {
            // A deliberately small viewport: the behaviour that matters on compact phones.
            val h = StageHarness(width = 1080, height = 1500)
            h.tap("hdr-2")
            h.settle(40)
            val before = h.targets(step = 7).keys
            expect(!before.contains("row-notif"), "the last row starts below the fold on a short screen")
            var after = emptySet<String>()
            for (attempt in 0 until 6) {
                // Swipe along the card margin so the gesture cannot land on a slider.
                h.swipe(20f, 1200f, 20f, 700f)
                h.settle(12)
                after = h.targets(step = 7).keys
                if (after.contains("row-notif")) break
            }
            expect(after.contains("row-notif"), "dragging up revealed the last settings row (now $after)")
            expect(h.tap("row-notif"), "the revealed row is tappable")
            h.settle(5)
            expect(!h.controller.state().settings.notifications, "tapping it toggled notifications")
        }

        Suite.test("Rendering", "every theme paints its own surface") {
            val h = StageHarness()
            val obsidian = meanColor(h)
            h.controller.dispatch(Action.SetTheme(ThemeId.Pure))
            h.settle(90)
            val pure = meanColor(h)
            h.controller.dispatch(Action.SetTheme(ThemeId.Aurora))
            h.settle(90)
            val aurora = meanColor(h)
            expect(distance(obsidian, pure) > 120, "obsidian and pure differ ($obsidian vs $pure)")
            expect(distance(pure, aurora) > 120, "pure and aurora differ")
            expect(distance(obsidian, aurora) > 8, "obsidian and aurora differ ($obsidian vs $aurora)")
        }

        Suite.test("Rendering", "the running ring paints progress pixels") {
            val h = StageHarness()
            h.controller.dispatch(Action.SetDuration(600_000))
            h.settle(10)
            val idleSample = sampleRing(h)
            h.tap("primary")
            h.time.advance(240_000)
            h.settle(20)
            val runningSample = sampleRing(h)
            expect(idleSample != runningSample, "ring pixels change while the countdown runs")
        }

        Suite.test("Rendering", "Persian renders glyphs, not empty boxes") {
            val h = StageHarness()
            h.controller.dispatch(Action.SetLanguage(Language.Persian))
            h.settle(60)
            // Count dark-on-light pixels in the readout band: tofu boxes would still paint, so the
            // stronger check is that the Persian typeface produced a different glyph shape than the
            // Latin one for the same digits.
            val persian = inkProfile(h, 640f, 880f)
            h.controller.dispatch(Action.SetLanguage(Language.English))
            h.settle(60)
            val latin = inkProfile(h, 640f, 880f)
            val diff = inkProfileDiff(persian, latin)
            expect(diff > 0.02f, "Persian glyphs differ from Latin glyphs (diff $diff)")
        }
    }

    /** Mean colour of a coarse grid over the whole surface. */
    private fun meanColor(h: StageHarness): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        var y = 0
        while (y < h.height) {
            var x = 0
            while (x < h.width) {
                val c = h.colorAt(x, y)
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
                x += 24
            }
            y += 24
        }
        return ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private fun meanLuma(h: StageHarness): Float {
        val c = meanColor(h)
        return ((c shr 16 and 0xFF) * 0.2126f + (c shr 8 and 0xFF) * 0.7152f + (c and 0xFF) * 0.0722f)
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
        return dr + dg + db
    }

    /** Samples a band across the ring so progress colour changes are measurable. */
    private fun sampleRing(h: StageHarness): Int {
        var sum = 0L
        var count = 0
        for (y in 520..1200 step 40) {
            for (x in 60..1020 step 40) {
                val c = h.colorAt(x, y)
                sum += ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)).toLong()
                count++
            }
        }
        return (sum / count).toInt()
    }

    /** Coarse ink profile of a horizontal band: how much contrast each column carries. */
    private fun inkProfile(h: StageHarness, top: Float, bottom: Float): FloatArray {
        val width = 1080
        val profile = FloatArray(width / 20)
        var y = top.toInt()
        while (y < bottom.toInt() && y < h.height) {
            var x = 0
            while (x < width) {
                val c = h.colorAt(x, y)
                val lum = ((c shr 16 and 0xFF) * 0.2126f + (c shr 8 and 0xFF) * 0.7152f + (c and 0xFF) * 0.0722f)
                profile[x / 20] += lum
                x += 3
            }
            y += 3
        }
        val total = profile.sum().coerceAtLeast(1f)
        return FloatArray(profile.size) { profile[it] / total }
    }

    private fun inkProfileDiff(a: FloatArray, b: FloatArray): Float {
        var diff = 0f
        for (i in a.indices) diff += kotlin.math.abs(a[i] - b[i])
        return diff
    }
}
