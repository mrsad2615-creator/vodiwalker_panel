// NEUROX offline compile stubs - Size helpers used by drawing code.
package androidx.compose.ui.geometry

val Size.minDimension: Float get() = minOf(width, height)
val Size.maxDimension: Float get() = maxOf(width, height)
