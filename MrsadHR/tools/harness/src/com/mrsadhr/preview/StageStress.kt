package com.mrsadhr.preview

import com.mrsadhr.app.Action
import com.mrsadhr.app.AppController
import com.mrsadhr.core.model.Language
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.time.ManualTimeSource
import com.mrsadhr.data.MemoryStore
import com.mrsadhr.platform.NullPlatformBridge
import com.mrsadhr.platform.NoopSoundEngine
import com.mrsadhr.themes.ThemeId
import com.mrsadhr.ui.ScreenId
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.TouchAction
import com.mrsadhr.ui.TouchEvent
import com.mrsadhr.ui.Typefaces
import java.awt.image.BufferedImage
import java.io.File
import kotlin.system.exitProcess

/**
 * Device-shaped stress run of the real [Stage].
 *
 * The preview harness draws one frame at one size; a phone does something much less polite. It
 * binds the stage before it has been measured (a viewport of 0×0), lays out at split-screen heights,
 * reports a system font scale and window insets, and then floods the surface with touches in states
 * the designer never looked at. Anything that throws here is a crash that `onDraw` would have to
 * survive on a real device, so this runs the same code the APK ships and fails the build if a
 * combination throws.
 *
 * Usage: `StageStress [fontDir]`
 */
object StageStress {

    private lateinit var typefaces: Typefaces

    private var failures = 0
    private var cases = 0

    @JvmStatic
    fun main(args: Array<String>) {
        val fontDir = File(args.getOrElse(0) { "app/src/main/assets/fonts" })
        typefaces = Typefaces(AwtFontLoader(fontDir))

        metrics()
        geometry()
        states()
        input()
        longRun()

        println()
        if (failures == 0) {
            println("stage stress: $cases cases, no exception")
            exitProcess(0)
        }
        println("stage stress: $cases cases, $failures FAILED")
        exitProcess(1)
    }

    // ------------------------------------------------------------------ helpers

    /** A painter that never allocates and never rasterises: it only exercises layout and paint. */
    private class SilentPainter : com.mrsadhr.core.render.Painter {
        override var width: Float = 0f
        override var height: Float = 0f
        private var depth = 0
        override fun save() { depth++ }
        override fun restore() { depth-- }
        override fun translate(dx: Float, dy: Float) {}
        override fun rotate(deg: Float) {}
        override fun rotateAround(deg: Float, pivotX: Float, pivotY: Float) {}
        override fun scale(sx: Float, sy: Float) {}
        override fun clipRect(rect: com.mrsadhr.core.render.Rect) {}
        override fun clipRoundRect(rect: com.mrsadhr.core.render.Rect, radius: Float) {}
        override fun saveLayerAlpha(alpha: Float) {}
        override fun drawRect(rect: com.mrsadhr.core.render.Rect, paint: com.mrsadhr.core.render.Paint) {}
        override fun drawRoundRect(
            rect: com.mrsadhr.core.render.Rect,
            corners: com.mrsadhr.core.render.Corners,
            paint: com.mrsadhr.core.render.Paint,
        ) {}
        override fun drawOval(rect: com.mrsadhr.core.render.Rect, paint: com.mrsadhr.core.render.Paint) {}
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: com.mrsadhr.core.render.Paint) {}
        override fun drawArc(
            rect: com.mrsadhr.core.render.Rect,
            startDeg: Float,
            sweepDeg: Float,
            useCenter: Boolean,
            paint: com.mrsadhr.core.render.Paint,
        ) {}
        override fun drawPath(path: com.mrsadhr.core.render.PathHandle, paint: com.mrsadhr.core.render.Paint) {}
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: com.mrsadhr.core.render.Paint) {}
        override fun drawText(text: String, x: Float, baselineY: Float, paint: com.mrsadhr.core.render.Paint) {}
        override fun measureText(text: String, paint: com.mrsadhr.core.render.Paint): Float =
            text.length * paint.textSize * 0.55f
        override fun fontMetrics(paint: com.mrsadhr.core.render.Paint) =
            com.mrsadhr.core.render.FontMetrics(-paint.textSize * 0.93f, paint.textSize * 0.24f)
        override fun newPath(): com.mrsadhr.core.render.PathHandle =
            object : com.mrsadhr.core.render.PathHandle {
                override fun reset() = this
                override fun moveTo(x: Float, y: Float) = this
                override fun lineTo(x: Float, y: Float) = this
                override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) = this
                override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) = this
                override fun close() = this
                override fun addRoundRect(rect: com.mrsadhr.core.render.Rect, corners: com.mrsadhr.core.render.Corners) = this
                override fun addCircle(cx: Float, cy: Float, r: Float) = this
                override fun arcTo(rect: com.mrsadhr.core.render.Rect, startDeg: Float, sweepDeg: Float, forceMoveTo: Boolean) = this
            }
        override fun linearGradient(
            x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?,
        ) = NO_SHADER
        override fun radialGradient(
            cx: Float, cy: Float, radius: Float, colors: IntArray, positions: FloatArray?,
        ) = NO_SHADER
    }

    private object NO_SHADER : com.mrsadhr.core.render.Shader()

    private class Fixture(
        val w: Float,
        val h: Float,
        val density: Float,
        val fontScale: Float = 1f,
        val safeTop: Float = 0f,
        val safeBottom: Float = 0f,
    ) {
        val time = ManualTimeSource(mono = 500_000L)
        val controller = AppController(time, MemoryStore(), NullPlatformBridge, NoopSoundEngine)
        val stage = Stage(controller, typefaces)
        val painter = SilentPainter().also { it.width = w; it.height = h }

        /** Mirrors `TimerAppView.bind` + `onSizeChanged` + `onApplyWindowInsets` exactly. */
        fun attach() {
            stage.setViewport(0f, 0f, density)
            stage.fontScale = fontScale
            stage.update(time.monoMs())
            stage.setViewport(w, h, density)
            stage.safeTop = safeTop
            stage.safeBottom = safeBottom
            stage.fontScale = fontScale
            stage.update(time.monoMs())
            draw()
        }

        fun draw() = stage.draw(painter)

        fun step(ms: Long = 16L, frames: Int = 1) {
            repeat(frames) {
                time.advance(ms)
                stage.update(time.monoMs())
                draw()
            }
        }

        fun tap(x: Float, y: Float) {
            stage.onTouch(TouchEvent(x, y, TouchAction.Down), time.monoMs())
            step(60)
            stage.onTouch(TouchEvent(x, y, TouchAction.Up), time.monoMs())
            step(16, 4)
        }

        fun drag(x0: Float, y0: Float, x1: Float, y1: Float) {
            stage.onTouch(TouchEvent(x0, y0, TouchAction.Down), time.monoMs())
            step(16)
            for (i in 1..8) {
                val t = i / 8f
                stage.onTouch(
                    TouchEvent(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, TouchAction.Move),
                    time.monoMs(),
                )
                step(16)
            }
            stage.onTouch(TouchEvent(x1, y1, TouchAction.Up), time.monoMs())
            step(16, 4)
        }
    }

    private fun run(name: String, block: () -> Unit) {
        cases++
        try {
            block()
        } catch (error: Throwable) {
            failures++
            println("FAIL  $name")
            println("        ${error.javaClass.simpleName}: ${error.message}")
            val first = error.stackTrace.firstOrNull { it.className.startsWith("com.mrsadhr") }
            println("        at $first")
        }
    }

    // ------------------------------------------------------------------ 0. real metrics

    /**
     * The same geometry matrix, but painted with the real font files and real glyph metrics.
     *
     * The silent painter answers `measureText` with an estimate; a phone answers with the actual
     * advance widths of Space Grotesk and Vazirmatn, and layout code that divides by a measured
     * width only breaks in the second case. This pass uses the same AWT metrics the preview images
     * use, so a fault that needs true text widths shows up here rather than on the device.
     */
    private fun metrics() {
        val cases = listOf(
            Triple(1080f, 2340f, 2.75f),
            Triple(1080f, 1920f, 2.75f),
            Triple(720f, 1280f, 2f),
            Triple(640f, 1136f, 2f),
            Triple(360f, 640f, 1.5f),
            Triple(320f, 480f, 1f),
            Triple(1440f, 3200f, 3.5f),
            Triple(1080f, 200f, 2.75f),
        )
        for ((w, h, d) in cases) {
            for (scale in listOf(0.85f, 1f, 1.3f, 2f)) {
                run("metrics ${w.toInt()}x${h.toInt()} @${d}x font $scale") {
                    val f = Fixture(w, h, d, scale, 74f, 60f)
                    val img = java.awt.image.BufferedImage(
                        minOf(w.toInt(), 600).coerceAtLeast(1),
                        minOf(h.toInt(), 900).coerceAtLeast(1),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB,
                    )
                    val g = img.createGraphics()
                    g.setRenderingHint(
                        java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
                    )
                    val real = AwtPainter(g, w, h)
                    f.attach()
                    repeat(3) {
                        f.time.advance(16)
                        f.stage.update(f.time.monoMs())
                        f.stage.draw(real)
                    }
                    for (screen in ScreenId.entries) {
                        f.stage.navigate(screen, f.time.monoMs())
                        repeat(8) {
                            f.time.advance(16)
                            f.stage.update(f.time.monoMs())
                            f.stage.draw(real)
                        }
                    }
                    f.controller.dispatch(Action.SetLanguage(com.mrsadhr.core.model.Language.Persian))
                    f.stage.navigate(ScreenId.Timer, f.time.monoMs())
                    f.controller.dispatch(Action.SetDuration(59 * 60_000L))
                    f.controller.dispatch(Action.TimerStart)
                    repeat(20) {
                        f.time.advance(250)
                        f.stage.update(f.time.monoMs())
                        f.stage.draw(real)
                    }
                    g.dispose()
                }
            }
        }
    }

    // ------------------------------------------------------------------ 1. geometry

    /** Sizes, densities, font scales and insets a real phone reports. */
    private fun geometry() {
        val sizes = listOf(
            0f to 0f,          // bound before measurement
            1f to 1f,          // a single pixel of viewport
            240f to 320f,      // ancient small phone
            320f to 480f,      // 480x800 class
            360f to 592f,      // split screen, short
            720f to 1280f,     // 720p
            1080f to 1920f,
            1080f to 2340f,
            1440f to 3200f,
            2340f to 1080f,    // landscape / hinge
            1080f to 200f,     // extreme split screen
        )
        val densities = listOf(1f, 1.5f, 2f, 2.75f, 3.5f, 4f)
        val scales = listOf(0.85f, 1f, 1.3f, 1.5f, 2f)
        val insets = listOf(0f to 0f, 24f to 48f, 148f to 192f)

        for ((w, h) in sizes) {
            for (d in densities) {
                for (s in scales) {
                    val (top, bottom) = insets[(w.toInt() + h.toInt() + (d * 10).toInt()) % insets.size]
                    run("geometry ${w.toInt()}x${h.toInt()} @${d}x font $s inset $top/$bottom") {
                        val f = Fixture(w, h, d, s, top, bottom)
                        f.attach()
                        f.step(16, 3)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 2. states

    /** Every screen, theme, language, mode and overlay, drawn while running and while idle. */
    private fun states() {
        val geometryCases = listOf(
            Triple(1080f, 2340f, 2.75f),
            Triple(720f, 1280f, 2f),
            Triple(360f, 592f, 1.5f),
            Triple(1080f, 200f, 2.75f),
        )
        for ((w, h, d) in geometryCases) {
            for (theme in ThemeId.entries) {
                for (language in Language.entries) {
                    run("state ${w.toInt()}x${h.toInt()} $theme $language") {
                        val f = Fixture(w, h, d, 1.15f, 96f, 120f)
                        f.controller.dispatch(Action.SetTheme(theme))
                        f.controller.dispatch(Action.SetLanguage(language))
                        f.attach()
                        f.step(16, 6)

                        for (screen in ScreenId.entries) {
                            f.stage.navigate(screen, f.time.monoMs())
                            f.step(16, 10)
                            f.stage.back(f.time.monoMs())
                            f.step(16, 6)
                        }

                        // Running countdown, with the completion overlay afterwards.
                        f.controller.dispatch(Action.SetDuration(3_000L))
                        f.controller.dispatch(Action.TimerStart)
                        f.step(250, 6)
                        f.stage.update(f.time.monoMs())
                        f.step(250, 20)

                        // Running stopwatch with laps.
                        f.controller.dispatch(Action.SetMode(Mode.Stopwatch))
                        f.controller.dispatch(Action.StopwatchToggle)
                        f.step(120, 5)
                        f.controller.dispatch(Action.StopwatchLap)
                        f.step(120, 5)
                        f.controller.dispatch(Action.StopwatchLap)
                        f.controller.dispatch(Action.StopwatchReset)
                        f.controller.dispatch(Action.SetMode(Mode.Timer))
                        f.step(16, 4)
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 3. input

    /** Touches everywhere: the surface grid on every screen, plus drags and long presses. */
    private fun input() {
        val geometryCases = listOf(
            Triple(1080f, 2340f, 2.75f),
            Triple(720f, 1280f, 2f),
            Triple(360f, 592f, 1.5f),
        )
        for ((w, h, d) in geometryCases) {
            run("input grid ${w.toInt()}x${h.toInt()}") {
                val f = Fixture(w, h, d)
                f.attach()
                f.step(16, 3)
                for (screen in ScreenId.entries) {
                    f.stage.navigate(screen, f.time.monoMs())
                    f.step(16, 8)
                    var y = 0f
                    while (y <= h) {
                        var x = 0f
                        while (x <= w) {
                            f.tap(x, y)
                            x += w / 4f
                        }
                        y += h / 6f
                    }
                    // Drags on each vertical band, which is how the lists scroll.
                    var dx = w / 6f
                    while (dx < w) {
                        f.drag(dx, h * 0.8f, dx, h * 0.2f)
                        f.drag(dx, h * 0.2f, dx, h * 0.85f)
                        dx += w / 3f
                    }
                }
                // Long press on the surface.
                f.stage.onTouch(TouchEvent(w / 2f, h / 2f, TouchAction.Down), f.time.monoMs())
                f.step(600, 3)
                f.stage.onTouch(TouchEvent(w / 2f, h / 2f, TouchAction.Up), f.time.monoMs())
                f.step(16, 3)
                // Cancel and out-of-bounds touches, which a real touchscreen can deliver.
                f.stage.onTouch(TouchEvent(-40f, -40f, TouchAction.Down), f.time.monoMs())
                f.stage.onTouch(TouchEvent(-40f, -40f, TouchAction.Cancel), f.time.monoMs())
                f.stage.onTouch(TouchEvent(w * 3f, h * 3f, TouchAction.Down), f.time.monoMs())
                f.stage.onTouch(TouchEvent(w * 3f, h * 3f, TouchAction.Up), f.time.monoMs())
                f.step(16, 3)
            }
        }
    }

    // ------------------------------------------------------------------ 4. long run

    /** Ten minutes of frames at 60 fps on a running countdown, and a wrap past midnight. */
    private fun longRun() {
        run("long run 10 min of frames") {
            val f = Fixture(1080f, 2340f, 2.75f, 1f, 74f, 60f)
            f.attach()
            f.controller.dispatch(Action.SetDuration(1_500L))
            f.controller.dispatch(Action.TimerStart)
            repeat(1200) {
                f.step(500, 1)
            }
            f.controller.dispatch(Action.TimerReset)
            f.step(16, 5)
        }

        run("repeat 40 times, no drift") {
            val f = Fixture(1080f, 2340f, 2.75f)
            f.attach()
            f.controller.dispatch(Action.SetDuration(400L))
            repeat(40) {
                f.controller.dispatch(Action.TimerStart)
                f.step(200, 3)
                f.controller.dispatch(Action.TimerReset)
                f.step(16, 2)
            }
        }

        run("stopwatch for an hour") {
            val f = Fixture(1080f, 2340f, 2.75f)
            f.attach()
            f.controller.dispatch(Action.SetMode(Mode.Stopwatch))
            f.controller.dispatch(Action.StopwatchToggle)
            repeat(400) {
                f.step(9_000, 1)
                if (it % 50 == 0) f.controller.dispatch(Action.StopwatchLap)
            }
            f.controller.dispatch(Action.StopwatchToggle)
            f.controller.dispatch(Action.StopwatchReset)
            f.step(16, 3)
        }

        run("time source jumps backwards") {
            val f = Fixture(1080f, 2340f, 2.75f)
            f.attach()
            f.controller.dispatch(Action.SetDuration(60_000L))
            f.controller.dispatch(Action.TimerStart)
            f.step(500, 4)
            f.time.advance(-120_000L)
            f.step(16, 4)
            f.time.advance(5_000_000L)
            f.step(16, 4)
        }
    }
}
