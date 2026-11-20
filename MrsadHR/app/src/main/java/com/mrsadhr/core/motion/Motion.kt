package com.mrsadhr.core.motion

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Motion Engine M1 — timing vocabulary.
 *
 * Every animation in MrsadHR is expressed with these durations and curves so that interaction
 * feels like one coherent product instead of a bag of effects.
 */
object Motion {
    // Interaction beats
    const val PRESS = 130        // 120–180ms: tactile button compression
    const val DIGIT = 210        // 180–250ms: kinetic digit roll
    const val SCREEN = 360       // 300–400ms: screen push / pop
    const val THEME = 560        // 450–650ms: whole-app material cross-fade
    const val COMPLETION = 1150   // 900–1400ms: cinematic zero-state bloom
    const val SHEET = 320
    const val CHIP = 240
}

enum class Easing {
    Linear,
    Standard,
    Decelerate,
    Accelerate,
    Emphasize,
    Overshoot,
    SoftSpring,
    ;

    fun transform(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (this) {
            Linear -> x
            Standard -> cubicBezier(0.4f, 0f, 0.2f, 1f, x)
            Decelerate -> cubicBezier(0.05f, 0.7f, 0.1f, 1f, x)
            Accelerate -> cubicBezier(0.3f, 0f, 0.8f, 0.15f, x)
            Emphasize -> cubicBezier(0.2f, 0f, 0f, 1f, x)
            Overshoot -> cubicBezier(0.18f, 1.3f, 0.3f, 1f, x)
            SoftSpring -> 1f - exp(-7f * x) * kotlin.math.cos(8.5f * x)
        }
    }

    private fun cubicBezier(p1x: Float, p1y: Float, p2x: Float, p2y: Float, t: Float): Float {
        // Newton-Raphson solve for x, then evaluate y — same maths as a CSS cubic-bezier().
        var guess = t
        repeat(6) {
            val x = bez(p1x, p2x, guess) - t
            val dx = bezDerivative(p1x, p2x, guess)
            if (abs(dx) < 1e-5f) return@repeat
            guess -= x / dx
        }
        return bez(p1y, p2y, guess.coerceIn(0f, 1f))
    }

    private fun bez(a: Float, b: Float, t: Float): Float {
        val mt = 1f - t
        return 3f * mt * mt * t * a + 3f * mt * t * t * b + t * t * t
    }

    private fun bezDerivative(a: Float, b: Float, t: Float): Float {
        val mt = 1f - t
        return 3f * mt * mt * a + 6f * mt * t * (b - a) + 3f * t * t * (1f - b)
    }
}

/**
 * Time driven value interpolation. Deterministic: the caller supplies the clock, which keeps the
 * UI trivially previewable and testable.
 */
class Animated(
    initial: Float = 0f,
    var durationMs: Int = Motion.SCREEN,
    var easing: Easing = Easing.Standard,
) {
    var value: Float = initial
        private set
    var target: Float = initial
        private set

    private var from: Float = initial
    private var startedAt: Long = Long.MIN_VALUE
    private var running = false

    fun setTarget(newTarget: Float, nowMs: Long, duration: Int = durationMs) {
        if (newTarget == target && running) return
        if (newTarget == target && !running) return
        from = value
        target = newTarget
        durationMs = duration
        startedAt = nowMs
        running = true
    }

    fun snap(newValue: Float) {
        value = newValue
        from = newValue
        target = newValue
        running = false
    }

    /** Returns true while still animating (callers use it to decide whether to request a frame). */
    fun update(nowMs: Long): Boolean {
        if (!running) return false
        if (durationMs <= 0) {
            value = target
            running = false
            return false
        }
        val t = ((nowMs - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
        value = from + (target - from) * easing.transform(t)
        if (t >= 1f) {
            value = target
            running = false
        }
        return running
    }

    val isRunning: Boolean get() = running

    /** Convenience: 0..1 progress of the current transition. */
    fun progress(nowMs: Long): Float = if (!running) 1f else ((nowMs - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
}

/** Frame independent exponential smoothing used for parallax and soft light responses. */
class Smoothed(initial: Float = 0f, private val halfLifeMs: Float = 120f) {
    var value: Float = initial
        private set
    private var targetValue: Float = initial
    private var lastUpdate: Long = -1L

    fun setTarget(t: Float) {
        targetValue = t
    }

    fun snap(v: Float) {
        value = v
        targetValue = v
    }

    fun update(nowMs: Long): Boolean {
        if (lastUpdate < 0L) {
            lastUpdate = nowMs
            value = targetValue
            return false
        }
        val dt = (nowMs - lastUpdate).toFloat()
        lastUpdate = nowMs
        if (dt <= 0f) return false
        val k = 1f - 0.5f.pow(dt / halfLifeMs)
        value += (targetValue - value) * k.coerceIn(0f, 1f)
        return abs(targetValue - value) > 0.0005f
    }
}
