// NEUROX offline compile stubs - Path.
package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset

/** The app draws one polyline; these are the operations it uses. */
class Path {
    fun moveTo(x: Float, y: Float) {}
    fun moveTo(offset: Offset) {}
    fun lineTo(x: Float, y: Float) {}
    fun lineTo(offset: Offset) {}
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {}
    fun close() {}
    fun reset() {}
}

/** DrawStyle: Fill and Stroke both live in this package in the real library. */
sealed class DrawStyle

object Fill : DrawStyle()

/**
 * Stroke.
 *
 * The real library declares it in androidx.compose.ui.graphics; drawscope re-exports
 * it. Because our DrawStyle is sealed, the subclass must live here, so the drawscope
 * package gets a typealias (see 05c).
 */
class Stroke(
    val width: Float = 0f,
    val miter: Float = 4f,
    val cap: StrokeCap = StrokeCap.Butt,
    val join: StrokeJoin = StrokeJoin.Miter,
    val pathEffect: PathEffect? = null,
) : DrawStyle()

enum class StrokeCap { Butt, Round, Square }

enum class StrokeJoin { Miter, Round, Bevel }

interface PathEffect
