// NEUROX offline compile stubs - Stroke, in androidx.compose.ui.graphics.drawscope.
package androidx.compose.ui.graphics.drawscope

import androidx.compose.ui.graphics.DrawStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

class Stroke(
    val width: Float = 0f,
    val miter: Float = 4f,
    val cap: StrokeCap = StrokeCap.Butt,
    val join: StrokeJoin = StrokeJoin.Miter,
    val pathEffect: PathEffect? = null,
) : DrawStyle()
