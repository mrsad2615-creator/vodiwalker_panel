package com.mrsadhr.ui

import com.mrsadhr.core.motion.Easing
import com.mrsadhr.core.motion.Motion
import com.mrsadhr.core.render.Rect

enum class TouchAction { Down, Move, Up, Cancel }

class TouchEvent(val x: Float, val y: Float, val action: TouchAction)

/**
 * Declarative hit zone. Screens register one per interactive element while laying out; the shared
 * hit layer takes care of tap / long press / press feedback bookkeeping.
 */
class HitArea(
    val id: String,
    val rect: Rect,
    val onTap: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val onDrag: ((Float, Float) -> Unit)? = null,
    val enabled: Boolean = true,
)

class HitLayer {
    private val areas = ArrayList<HitArea>(32)
    val pressed = HashMap<String, PressState>()

    fun clear() = areas.clear()

    fun add(
        id: String,
        rect: Rect,
        enabled: Boolean = true,
        onLongPress: (() -> Unit)? = null,
        onDrag: ((Float, Float) -> Unit)? = null,
        onTap: (() -> Unit)? = null,
    ): HitArea {
        val area = HitArea(id, rect, onTap, onLongPress, onDrag, enabled)
        areas.add(area)
        return area
    }

    fun find(x: Float, y: Float): HitArea? {
        for (i in areas.indices.reversed()) {
            val a = areas[i]
            if (a.enabled && a.rect.contains(x, y)) return a
        }
        return null
    }

    fun press(id: String): PressState = pressed.getOrPut(id) { PressState() }

    fun anyAnimating(now: Long): Boolean = pressed.values.any { it.isAnimating(now) }
}

/**
 * Tactile compression + light response for any control, driven by explicit time so the animation
 * is identical on device and in previews.
 */
class PressState {
    var down = false
        private set
    var value = 0f
        private set
    var downAt = 0L
        private set

    fun onDown(now: Long) {
        down = true
        downAt = now
    }

    fun onUp(now: Long) {
        down = false
    }

    fun update(now: Long): Boolean {
        val target = if (down) 1f else 0f
        val duration = if (down) Motion.PRESS else Motion.PRESS + 60
        val elapsed = (now - downAt).coerceAtLeast(0L)
        val t = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
        val v = Easing.Decelerate.transform(t)
        value = if (down) v else 1f - Easing.Standard.transform(t)
        return value > 0.001f && value < 0.999f
    }

    fun isAnimating(now: Long): Boolean {
        val target = if (down) 1f else 0f
        return kotlin.math.abs(value - target) > 0.001f
    }

    fun press(): Float = 1f - 0.045f * value * value

    fun lift(): Float = value
}
