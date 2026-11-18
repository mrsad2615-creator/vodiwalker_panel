package ir.aegis.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.aegis.core.model.AegisModule
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AegisModuleIcon(
    module: AegisModule,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (module) {
            AegisModule.HOME -> {
                val p = Path().apply {
                    moveTo(w * 0.16f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.48f)
                    lineTo(w * 0.84f, h * 0.84f)
                    lineTo(w * 0.16f, h * 0.84f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                drawRect(tint, topLeft = Offset(w * 0.40f, h * 0.55f), size = Size(w * 0.20f, h * 0.29f), style = stroke)
            }
            AegisModule.PERSONAL -> {
                val shield = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.82f, h * 0.24f)
                    lineTo(w * 0.82f, h * 0.52f)
                    quadraticTo(w * 0.82f, h * 0.76f, w * 0.50f, h * 0.88f)
                    quadraticTo(w * 0.18f, h * 0.76f, w * 0.18f, h * 0.52f)
                    lineTo(w * 0.18f, h * 0.24f)
                    close()
                }
                drawPath(shield, tint, style = stroke)
                drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.50f, h * 0.43f), style = stroke)
                drawArc(tint, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(w * 0.32f, h * 0.56f), size = Size(w * 0.36f, h * 0.20f), style = stroke)
            }
            AegisModule.CRISIS -> {
                val tri = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.82f)
                    lineTo(w * 0.12f, h * 0.82f)
                    close()
                }
                drawPath(tri, tint, style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.39f), Offset(w * 0.50f, h * 0.59f), strokeWidth = w * 0.095f, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.048f, center = Offset(w * 0.50f, h * 0.70f))
            }
            AegisModule.MAPS -> {
                val fold = Path().apply {
                    moveTo(w * 0.14f, h * 0.25f)
                    lineTo(w * 0.38f, h * 0.16f)
                    lineTo(w * 0.62f, h * 0.26f)
                    lineTo(w * 0.86f, h * 0.17f)
                    lineTo(w * 0.86f, h * 0.76f)
                    lineTo(w * 0.62f, h * 0.85f)
                    lineTo(w * 0.38f, h * 0.75f)
                    lineTo(w * 0.14f, h * 0.84f)
                    close()
                }
                drawPath(fold, tint, style = stroke)
                drawLine(tint, Offset(w * 0.38f, h * 0.16f), Offset(w * 0.38f, h * 0.75f), strokeWidth = w * 0.07f)
                drawLine(tint, Offset(w * 0.62f, h * 0.26f), Offset(w * 0.62f, h * 0.85f), strokeWidth = w * 0.07f)
            }
            AegisModule.EMERGENCY -> {
                drawCircle(tint, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint.copy(alpha = 0.28f), radius = w * 0.23f, center = Offset(w * 0.50f, h * 0.50f))
                drawLine(tint, Offset(w * 0.50f, h * 0.31f), Offset(w * 0.50f, h * 0.69f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.31f, h * 0.50f), Offset(w * 0.69f, h * 0.50f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
            }
            AegisModule.AI -> {
                drawCircle(tint, radius = w * 0.14f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                for (deg in listOf(0, 60, 120, 180, 240, 300)) {
                    val rad = Math.toRadians(deg.toDouble())
                    val sx = w * 0.50f + (w * 0.16f * cos(rad)).toFloat()
                    val sy = h * 0.50f + (h * 0.16f * sin(rad)).toFloat()
                    val ex = w * 0.50f + (w * 0.36f * cos(rad)).toFloat()
                    val ey = h * 0.50f + (h * 0.36f * sin(rad)).toFloat()
                    drawLine(tint, Offset(sx, sy), Offset(ex, ey), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                    drawCircle(tint, radius = w * 0.055f, center = Offset(ex, ey))
                }
            }
            AegisModule.FAMILY -> {
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.34f, h * 0.32f), style = stroke)
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.66f, h * 0.32f), style = stroke)
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.52f), style = stroke)
                drawArc(tint, 190f, 160f, false, Offset(w * 0.14f, h * 0.48f), Size(w * 0.40f, h * 0.34f), style = stroke)
                drawArc(tint, 190f, 160f, false, Offset(w * 0.46f, h * 0.48f), Size(w * 0.40f, h * 0.34f), style = stroke)
            }
            AegisModule.ACADEMY -> {
                val cap = Path().apply {
                    moveTo(w * 0.50f, h * 0.20f)
                    lineTo(w * 0.90f, h * 0.40f)
                    lineTo(w * 0.50f, h * 0.60f)
                    lineTo(w * 0.10f, h * 0.40f)
                    close()
                }
                drawPath(cap, tint, style = stroke)
                drawArc(tint, 20f, 140f, false, Offset(w * 0.25f, h * 0.48f), Size(w * 0.50f, h * 0.32f), style = stroke)
                drawLine(tint, Offset(w * 0.85f, h * 0.43f), Offset(w * 0.85f, h * 0.72f), strokeWidth = w * 0.075f, cap = StrokeCap.Round)
            }
            AegisModule.ANALYTICS -> {
                drawLine(tint, Offset(w * 0.15f, h * 0.82f), Offset(w * 0.86f, h * 0.82f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.52f), Size(w * 0.14f, h * 0.30f), CornerRadius(w * 0.04f))
                drawRoundRect(tint, Offset(w * 0.43f, h * 0.32f), Size(w * 0.14f, h * 0.50f), CornerRadius(w * 0.04f))
                drawRoundRect(tint, Offset(w * 0.66f, h * 0.18f), Size(w * 0.14f, h * 0.64f), CornerRadius(w * 0.04f))
            }
            AegisModule.PRIVACY -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.44f), Size(w * 0.56f, h * 0.40f), CornerRadius(w * 0.09f), style = stroke)
                drawArc(tint, 180f, 180f, false, Offset(w * 0.32f, h * 0.16f), Size(w * 0.36f, h * 0.40f), style = stroke)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.63f))
            }
            AegisModule.UTILITIES -> {
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.25f), Offset(w * 0.58f, h * 0.50f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.75f), Offset(w * 0.42f, h * 0.50f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.50f))
            }
        }
    }
}
