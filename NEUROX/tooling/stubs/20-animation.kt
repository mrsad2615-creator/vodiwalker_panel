// NEUROX offline compile stubs - animation: transitions, tween, easing.
package androidx.compose.animation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

interface Easing {
    fun transform(fraction: Float): Float
}

class CubicBezierEasing(val a: Float, val b: Float, val c: Float, val d: Float) : Easing {
    override fun transform(fraction: Float): Float = fraction
}

object LinearEasing : Easing {
    override fun transform(fraction: Float) = fraction
}

class DurationBasedAnimationSpec<T> : AnimationSpec<T>

interface AnimationSpec<T>

class TweenSpec<T>(
    val durationMillis: Int = 300,
    val delay: Int = 0,
    val easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
) : AnimationSpec<T>

fun <T> tween(
    durationMillis: Int = 300,
    delayMillis: Int = 0,
    easing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
): TweenSpec<T> = TweenSpec(durationMillis, delayMillis, easing)

@Composable
fun animateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float> = tween(),
    label: String = "Animation",
    finishedListener: ((Float) -> Unit)? = null,
): State<Float> = error("stub")

@Composable
fun animateDpAsState(
    targetValue: androidx.compose.ui.unit.Dp,
    animationSpec: AnimationSpec<androidx.compose.ui.unit.Dp> = tween(),
    label: String = "Animation",
    finishedListener: ((androidx.compose.ui.unit.Dp) -> Unit)? = null,
): State<androidx.compose.ui.unit.Dp> = error("stub")

@Composable
fun animateColorAsState(
    targetValue: androidx.compose.ui.graphics.Color,
    animationSpec: AnimationSpec<androidx.compose.ui.graphics.Color> = tween(),
    label: String = "Animation",
    finishedListener: ((androidx.compose.ui.graphics.Color) -> Unit)? = null,
): State<androidx.compose.ui.graphics.Color> = error("stub")
