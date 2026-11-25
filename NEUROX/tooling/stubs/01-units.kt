// NEUROX offline compile stubs - Compose units.
package androidx.compose.ui.unit

class Dp(val raw: Float) : Comparable<Dp> {
    operator fun plus(other: Dp) = Dp(raw + other.raw)
    operator fun minus(other: Dp) = Dp(raw - other.raw)
    operator fun times(other: Float) = Dp(raw * other)
    operator fun div(other: Float) = Dp(raw / other)
    override fun compareTo(other: Dp) = raw.compareTo(other.raw)
    override fun equals(other: Any?) = other is Dp && other.raw == raw
    override fun hashCode() = raw.hashCode()
    override fun toString() = "${raw}dp"
}

class TextUnit(val raw: Float) {
    operator fun times(other: Float) = TextUnit(raw * other)
    override fun equals(other: Any?) = other is TextUnit && other.raw == raw
    override fun hashCode() = raw.hashCode()
    override fun toString() = "${raw}sp"
}

val Int.dp: Dp get() = Dp(toFloat())
val Float.dp: Dp get() = Dp(this)
val Double.dp: Dp get() = Dp(toFloat())

val Int.sp: TextUnit get() = TextUnit(toFloat())
val Float.sp: TextUnit get() = TextUnit(this)
val Double.sp: TextUnit get() = TextUnit(toFloat())

enum class LayoutDirection { Ltr, Rtl }

/** Density: the app reads fontScale for text scaling. */
class Density(val density: Float = 1f, val fontScale: Float = 1f)


/** IntOffset: pixel offset used by slide transitions. */
class IntOffset(val x: Int, val y: Int)

val Dp.value: Float get() = raw
val TextUnit.value: Float get() = raw
