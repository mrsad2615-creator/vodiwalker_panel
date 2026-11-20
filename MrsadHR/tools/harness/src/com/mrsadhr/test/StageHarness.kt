package com.mrsadhr.test

import com.mrsadhr.app.AppController
import com.mrsadhr.core.time.ManualTimeSource
import com.mrsadhr.data.MemoryStore
import com.mrsadhr.platform.NullPlatformBridge
import com.mrsadhr.preview.AwtFontLoader
import com.mrsadhr.preview.AwtPainter
import com.mrsadhr.ui.Stage
import com.mrsadhr.ui.TouchAction
import com.mrsadhr.ui.TouchEvent
import com.mrsadhr.ui.Typefaces
import java.awt.image.BufferedImage
import java.io.File

/**
 * Drives the production [Stage] off device.
 *
 * The interface publishes the same hit areas on the desktop harness that it publishes on a phone,
 * so a synthetic touch here exercises the real layout, the real touch dispatcher and the real
 * controller — the closest thing to an instrumented UI test that this environment can run.
 */
class StageHarness(
    fontDir: File = File("app/src/main/assets/fonts"),
    val width: Int = 1080,
    val height: Int = 2280,
    existingStore: MemoryStore? = null,
    existingTime: ManualTimeSource? = null,
    val sounds: RecordingSounds = RecordingSounds(),
) {
    val time: ManualTimeSource = existingTime ?: ManualTimeSource(mono = 20_000)
    val store: MemoryStore = existingStore ?: MemoryStore()
    val controller = AppController(time, store, NullPlatformBridge, sounds)
    val stage = Stage(controller, Typefaces(AwtFontLoader(fontDir)))

    /** Last rendered frame; useful for pixel level assertions. */
    var image: BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        private set

    init {
        stage.setViewport(width.toFloat(), height.toFloat(), DENSITY)
        stage.safeTop = 74f
        stage.safeBottom = 60f
        frame(2)
    }

    fun frame(count: Int = 1, stepMs: Long = 16) {
        repeat(count) {
            time.advance(stepMs)
            stage.update(time.monoMs())
            render()
        }
    }

    fun render() {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE)
        stage.draw(AwtPainter(g, width.toFloat(), height.toFloat()))
        g.dispose()
        image = img
    }

    /** Settles every animation so assertions are not made mid-transition. */
    fun settle(frames: Int = 90) = frame(frames, 16)

    fun colorAt(x: Int, y: Int): Int = image.getRGB(x, y)

    /** Discovered tap targets: id -> a point inside the published hit rect. */
    fun targets(step: Int = 5): Map<String, Pair<Float, Float>> {
        val map = HashMap<String, Pair<Float, Float>>()
        var y = 0f
        while (y < height) {
            var x = 0f
            while (x < width) {
                val hit = stage.findHit(x, y)
                if (hit != null) {
                    val area = hit.second
                    if (!map.containsKey(area.id)) {
                        map[area.id] = area.rect.centerX to area.rect.centerY
                    }
                }
                x += step
            }
            y += step
        }
        return map
    }

    fun tap(id: String): Boolean {
        val point = targets()[id] ?: return false
        touch(point.first, point.second, TouchAction.Down)
        touch(point.first, point.second, TouchAction.Up)
        frame(3)
        return true
    }

    fun tapAt(x: Float, y: Float) {
        touch(x, y, TouchAction.Down)
        touch(x, y, TouchAction.Up)
        frame(3)
    }

    fun drag(id: String, toX: Float, toY: Float) {
        val point = targets()[id] ?: return
        touch(point.first, point.second, TouchAction.Down)
        frame(1)
        touch(point.first + 4f, point.second + 4f, TouchAction.Move)
        frame(1)
        touch(toX, toY, TouchAction.Move)
        frame(1)
        touch(toX, toY, TouchAction.Up)
        frame(3)
    }

    /** A full press-drag-release gesture between two arbitrary points. */
    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        touch(fromX, fromY, TouchAction.Down)
        frame(1)
        touch(fromX + 6f, fromY + 6f, TouchAction.Move)
        frame(1)
        touch(toX, toY, TouchAction.Move)
        frame(1)
        touch(toX, toY, TouchAction.Up)
        frame(3)
    }

    private fun touch(x: Float, y: Float, action: TouchAction) {
        time.advance(8)
        stage.onTouch(TouchEvent(x, y, action), time.monoMs())
    }

    companion object {
        const val DENSITY = 2.75f
    }
}
