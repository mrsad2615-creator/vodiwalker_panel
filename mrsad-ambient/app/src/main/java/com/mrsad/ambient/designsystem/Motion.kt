package com.mrsad.ambient.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object MrsadMotion {
    private val PrecisionEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    fun <T> transitionSpec(reducedMotion: Boolean): TweenSpec<T> {
        val duration = if (reducedMotion) 0 else 220
        return tween(durationMillis = duration, easing = PrecisionEasing)
    }

    fun <T> digitSpec(reducedMotion: Boolean): TweenSpec<T> {
        val duration = if (reducedMotion) 0 else 160
        return tween(durationMillis = duration, easing = PrecisionEasing)
    }
}
