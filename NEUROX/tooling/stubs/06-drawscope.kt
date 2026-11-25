// NEUROX offline compile stubs - DrawScope.
package androidx.compose.ui.graphics.drawscope

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.minDimension
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DrawStyle
import androidx.compose.ui.graphics.Path

interface DrawScope {
    val size: Size

    fun drawLine(
        color: Color,
        start: Offset,
        end: Offset,
        strokeWidth: Float = 0f,
        cap: androidx.compose.ui.graphics.StrokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
        pathEffect: androidx.compose.ui.graphics.PathEffect? = null,
        alpha: Float = 1f,
        colorFilter: ColorFilter? = null,
        blendMode: BlendMode = BlendMode.SrcOver,
    )

    fun drawCircle(
        color: Color,
        radius: Float = size.minDimension / 2f,
        center: Offset = this.center,
        alpha: Float = 1f,
        style: DrawStyle = androidx.compose.ui.graphics.Fill,
        colorFilter: ColorFilter? = null,
        blendMode: BlendMode = BlendMode.SrcOver,
    )

    fun drawRect(
        color: Color,
        topLeft: Offset = Offset.Zero,
        size: Size = this.size,
        alpha: Float = 1f,
        style: DrawStyle = androidx.compose.ui.graphics.Fill,
        colorFilter: ColorFilter? = null,
        blendMode: BlendMode = BlendMode.SrcOver,
    )

    fun drawPath(
        path: Path,
        color: Color,
        alpha: Float = 1f,
        style: DrawStyle = androidx.compose.ui.graphics.Fill,
        colorFilter: ColorFilter? = null,
        blendMode: BlendMode = BlendMode.SrcOver,
    )

    fun drawRoundRect(
        color: Color,
        topLeft: Offset = Offset.Zero,
        size: Size = this.size,
        cornerRadius: androidx.compose.ui.geometry.CornerRadius = androidx.compose.ui.geometry.CornerRadius.Zero,
        style: DrawStyle = androidx.compose.ui.graphics.Fill,
        alpha: Float = 1f,
        colorFilter: ColorFilter? = null,
        blendMode: BlendMode = BlendMode.SrcOver,
    )

    val center: Offset get() = Offset(size.width / 2f, size.height / 2f)
}

interface ColorFilter

/** BlendMode is an inline value class in the real library; a nominal type suffices here. */
class BlendMode private constructor() {
    companion object {
        val SrcOver = BlendMode()
        val Clear = BlendMode()
        val Src = BlendMode()
        val Dst = BlendMode()
        val DstOver = BlendMode()
        val SrcIn = BlendMode()
        val DstIn = BlendMode()
        val Multiply = BlendMode()
        val Screen = BlendMode()
        val Overlay = BlendMode()
    }
}
