package com.mrsadhr.core.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Immutable rectangle in pixel space. */
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun inset(dx: Float, dy: Float = dx) = Rect(left + dx, top + dy, right - dx, bottom - dy)

    fun offset(dx: Float, dy: Float) = Rect(left + dx, top + dy, right + dx, bottom + dy)

    fun scale(fx: Float, fy: Float = fx) = Rect(left * fx, top * fy, right * fx, bottom * fy)

    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom

    fun minSize(): Float = min(width, height)

    companion object {
        val Zero = Rect(0f, 0f, 0f, 0f)
        fun ofSize(w: Float, h: Float) = Rect(0f, 0f, w, h)
        fun centered(cx: Float, cy: Float, w: Float, h: Float = w) =
            Rect(cx - w * 0.5f, cy - h * 0.5f, cx + w * 0.5f, cy + h * 0.5f)

        fun lerp(a: Rect, b: Rect, t: Float) = Rect(
            Lerp.f(a.left, b.left, t),
            Lerp.f(a.top, b.top, t),
            Lerp.f(a.right, b.right, t),
            Lerp.f(a.bottom, b.bottom, t),
        )
    }
}

data class Offset(val x: Float, val y: Float) {
    companion object {
        val Zero = Offset(0f, 0f)
    }
}

object Lerp {
    fun f(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    fun i(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    fun map(value: Float, inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
        if (abs(inMax - inMin) < 1e-6f) return outMin
        val t = ((value - inMin) / (inMax - inMin)).coerceIn(0f, 1f)
        return outMin + (outMax - outMin) * t
    }
}

object Geo {
    fun pointOnCircle(cx: Float, cy: Float, radius: Float, angleDeg: Float): Offset {
        val rad = Math.toRadians(angleDeg.toDouble())
        return Offset((cx + radius * cos(rad)).toFloat(), (cy + radius * sin(rad)).toFloat())
    }

    fun distance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
}

/** Rectangle with independent corner radii, mirroring how the painters round corners. */
data class Corners(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
) {
    fun maxRadius() = max(max(topLeft, topRight), max(bottomRight, bottomLeft))

    fun scaled(f: Float) = Corners(topLeft * f, topRight * f, bottomRight * f, bottomLeft * f)

    companion object {
        fun all(r: Float) = Corners(r, r, r, r)
    }
}
