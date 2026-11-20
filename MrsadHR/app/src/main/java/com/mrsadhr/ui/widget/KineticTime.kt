package com.mrsadhr.ui.widget

import com.mrsadhr.core.motion.Easing
import com.mrsadhr.core.motion.Motion
import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.Painter
import com.mrsadhr.core.render.TextAlign
import kotlin.math.abs

/**
 * Kinetic typography for the timer readout.
 *
 * Every glyph owns a fixed cell (measured from a template string) so the layout can never jump,
 * and a changed glyph rolls vertically with a cross-fade — the "digit roll" of the motion engine.
 */
class KineticTime {

    private class Cell(var ch: Char, var prev: Char, var startMs: Long)

    private var cells: MutableList<Cell> = mutableListOf()
    private var template: String = ""
    private var lastText: String = ""

    var rollDurationMs: Int = Motion.DIGIT

    fun setText(text: String, nowMs: Long) {
        if (text == lastText) return
        if (template.length != text.length) {
            template = text.map { if (it.isDigit()) '8' else it }.joinToString("")
            cells = text.map { Cell(it, it, nowMs) }.toMutableList()
        } else {
            for (i in text.indices) {
                val c = cells[i]
                if (c.ch != text[i]) {
                    c.prev = c.ch
                    c.ch = text[i]
                    c.startMs = nowMs
                }
            }
        }
        lastText = text
    }

    fun snap(text: String) {
        template = text.map { if (it.isDigit()) '8' else it }.joinToString("")
        cells = text.map { Cell(it, it, 0L) }.toMutableList()
        lastText = text
    }

    private fun templateWidth(p: Painter, paint: Paint): Float {
        if (template.isEmpty()) return 0f
        return p.measureText(template, paint)
    }

    fun draw(p: Painter, centerX: Float, centerY: Float, paint: Paint, nowMs: Long, alpha: Float = 1f) {
        if (cells.isEmpty()) return
        val fm = p.fontMetrics(paint)
        val baseline = centerY - (fm.ascent + fm.descent) * 0.5f
        val digitW = p.measureText("0", paint)
        val colonW = p.measureText(":", paint)
        val total = cells.sumOf { c -> (if (c.ch.isDigit()) digitW else if (c.ch == ':') colonW else p.measureText(c.ch.toString(), paint)).toDouble() }.toFloat()
        var x = centerX - total * 0.5f
        val roll = paint.textSize * 0.30f

        for (c in cells) {
            val w = if (c.ch.isDigit()) digitW else if (c.ch == ':') colonW else p.measureText(c.ch.toString(), paint)
            val elapsed = (nowMs - c.startMs).toFloat()
            val rolling = c.prev != c.ch && elapsed < rollDurationMs && !com.mrsadhr.core.AppFlags.reducedMotion
            if (!rolling) {
                val cp = paint.copy()
                cp.alpha = alpha
                cp.textAlign = TextAlign.LEFT
                p.drawText(c.ch.toString(), x + (w - p.measureText(c.ch.toString(), cp)) * 0.5f, baseline, cp)
            } else {
                val t = (elapsed / rollDurationMs).coerceIn(0f, 1f)
                val e = Easing.Emphasize.transform(t)
                val oldPaint = paint.copy().apply {
                    this.alpha = alpha * (1f - e)
                    textAlign = TextAlign.LEFT
                }
                val newPaint = paint.copy().apply {
                    this.alpha = alpha * e
                    textAlign = TextAlign.LEFT
                }
                val oldY = baseline - roll * e
                val newY = baseline + roll * (1f - e)
                p.drawText(c.prev.toString(), x + (w - p.measureText(c.prev.toString(), oldPaint)) * 0.5f, oldY, oldPaint)
                p.drawText(c.ch.toString(), x + (w - p.measureText(c.ch.toString(), newPaint)) * 0.5f, newY, newPaint)
            }
            x += w
        }
    }

    /** Width of the stable template — callers use it for centring captions under the digits. */
    fun widthOf(p: Painter, paint: Paint): Float = templateWidth(p, paint)

    fun hasPendingAnimation(nowMs: Long): Boolean =
        cells.any { it.prev != it.ch && abs(nowMs - it.startMs) < rollDurationMs }
}
