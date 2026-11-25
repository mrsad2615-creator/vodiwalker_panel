// NEUROX offline compile stubs - Compose graphics, geometry, shapes, draw scope.
package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp

/**
 * Color.
 *
 * The real type is ULong-backed and has a large helper surface. The app constructs
 * colors from packed Longs, mixes them by alpha, and compares them, so those three
 * operations are modelled for real and the rest are declared for compatibility.
 */
class Color(val argb: Long) {
    constructor(red: Int, green: Int, blue: Int, alpha: Int = 255) :
        this(((alpha and 0xFF).toLong() shl 24) or ((red and 0xFF).toLong() shl 16) or ((green and 0xFF).toLong() shl 8) or (blue and 0xFF).toLong())

    constructor(red: Float, green: Float, blue: Float, alpha: Float = 1f) :
        this((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

    val alpha: Float get() = ((argb shr 24) and 0xFF).toFloat() / 255f
    val red: Float get() = ((argb shr 16) and 0xFF).toFloat() / 255f
    val green: Float get() = ((argb shr 8) and 0xFF).toFloat() / 255f
    val blue: Float get() = (argb and 0xFF).toFloat() / 255f

    fun copy(alpha: Float = this.alpha, red: Float = this.red, green: Float = this.green, blue: Float = this.blue): Color =
        Color(red, green, blue, alpha)

    override fun equals(other: Any?) = other is Color && other.argb == argb
    override fun hashCode() = argb.hashCode()

    companion object {
        val Unspecified = Color(0L)
        val Black = Color(0xFF000000)
        val White = Color(0xFFFFFFFF)
        val Transparent = Color(0x00000000)
        val Red = Color(0xFFFF0000)
        val Green = Color(0xFF00FF00)
        val Blue = Color(0xFF0000FF)
        val Gray = Color(0xFF888888)
        val DarkGray = Color(0xFF444444)
        val LightGray = Color(0xFFCCCCCC)
        val Yellow = Color(0xFFFFFF00)
        val Cyan = Color(0xFF00FFFF)
        val Magenta = Color(0xFFFF00FF)
    }
}

/** A brush is opaque; the app only passes one around. */
interface Brush {
    companion object {
        fun horizontalGradient(vararg colors: Color, startX: Float = 0f, endX: Float = Float.POSITIVE_INFINITY): Brush =
            object : Brush {}

        fun horizontalGradient(colors: List<Color>, startX: Float = 0f, endX: Float = Float.POSITIVE_INFINITY): Brush =
            object : Brush {}

        fun verticalGradient(colors: List<Color>, startY: Float = 0f, endY: Float = Float.POSITIVE_INFINITY): Brush =
            object : Brush {}

        fun linearGradient(colors: List<Color>, start: Offset = Offset.Zero, end: Offset = Offset.Infinite): Brush =
            object : Brush {}

        fun verticalGradient(vararg colors: Color, startY: Float = 0f, endY: Float = Float.POSITIVE_INFINITY): Brush =
            object : Brush {}

        fun linearGradient(
            vararg colors: Color,
            start: Offset = Offset.Zero,
            end: Offset = Offset.Infinite,
        ): Brush = object : Brush {}
    }
}

interface Shape

class SolidColor(val value: Color) : Brush
