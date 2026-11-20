package com.mrsad.ambient.designsystem

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MrsadIconType {
    CLOCK, CALENDAR, WEATHER, BATTERY, MUSIC, NOTIFICATIONS, CALLS, CONNECTIVITY, ACTIVITY, SETTINGS, SENSORS
}

@Composable
fun MrsadIcon(
    type: MrsadIconType,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MrsadColors.PureWhite
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.08f
        val stroke = Stroke(width = sw, cap = StrokeCap.Square)

        when (type) {
            MrsadIconType.CLOCK -> {
                drawCircle(tint, radius = w * 0.40f, style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.25f), strokeWidth = sw, cap = StrokeCap.Square)
                drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.70f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Square)
            }
            MrsadIconType.CALENDAR -> {
                drawRoundRect(tint, topLeft = Offset(w * 0.14f, h * 0.20f), size = Size(w * 0.72f, h * 0.66f), cornerRadius = CornerRadius(w * 0.06f), style = stroke)
                drawLine(tint, Offset(w * 0.14f, h * 0.40f), Offset(w * 0.86f, h * 0.40f), strokeWidth = sw)
                drawLine(tint, Offset(w * 0.34f, h * 0.12f), Offset(w * 0.34f, h * 0.26f), strokeWidth = sw)
                drawLine(tint, Offset(w * 0.66f, h * 0.12f), Offset(w * 0.66f, h * 0.26f), strokeWidth = sw)
            }
            MrsadIconType.WEATHER -> {
                drawCircle(tint, radius = w * 0.20f, center = Offset(w * 0.44f, h * 0.44f), style = stroke)
                drawLine(tint, Offset(w * 0.24f, h * 0.76f), Offset(w * 0.80f, h * 0.76f), strokeWidth = sw)
                drawLine(tint, Offset(w * 0.34f, h * 0.64f), Offset(w * 0.72f, h * 0.64f), strokeWidth = sw)
            }
            MrsadIconType.BATTERY -> {
                drawRoundRect(tint, topLeft = Offset(w * 0.12f, h * 0.28f), size = Size(w * 0.68f, h * 0.44f), cornerRadius = CornerRadius(w * 0.05f), style = stroke)
                drawRect(tint, topLeft = Offset(w * 0.83f, h * 0.40f), size = Size(w * 0.07f, h * 0.20f))
                drawRect(tint, topLeft = Offset(w * 0.22f, h * 0.38f), size = Size(w * 0.36f, h * 0.24f))
            }
            MrsadIconType.MUSIC -> {
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.32f, h * 0.72f), style = stroke)
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.70f, h * 0.66f), style = stroke)
                val p = Path().apply {
                    moveTo(w * 0.44f, h * 0.72f)
                    lineTo(w * 0.44f, h * 0.24f)
                    lineTo(w * 0.82f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.66f)
                }
                drawPath(p, tint, style = stroke)
            }
            MrsadIconType.NOTIFICATIONS -> {
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.70f)
                    lineTo(w * 0.78f, h * 0.70f)
                    lineTo(w * 0.70f, h * 0.34f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.30f, h * 0.34f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.82f))
            }
            MrsadIconType.CALLS -> {
                val p = Path().apply {
                    moveTo(w * 0.24f, h * 0.22f)
                    lineTo(w * 0.40f, h * 0.22f)
                    lineTo(w * 0.44f, h * 0.40f)
                    lineTo(w * 0.34f, h * 0.48f)
                    lineTo(w * 0.52f, h * 0.66f)
                    lineTo(w * 0.60f, h * 0.56f)
                    lineTo(w * 0.78f, h * 0.60f)
                    lineTo(w * 0.78f, h * 0.76f)
                    lineTo(w * 0.24f, h * 0.76f)
                    close()
                }
                drawPath(p, tint, style = stroke)
            }
            MrsadIconType.CONNECTIVITY -> {
                drawArc(tint, startAngle = 225f, sweepAngle = 90f, useCenter = false, topLeft = Offset(w * 0.14f, h * 0.24f), size = Size(w * 0.72f, h * 0.72f), style = stroke)
                drawArc(tint, startAngle = 225f, sweepAngle = 90f, useCenter = false, topLeft = Offset(w * 0.30f, h * 0.42f), size = Size(w * 0.40f, h * 0.40f), style = stroke)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.74f))
            }
            MrsadIconType.ACTIVITY -> {
                val p = Path().apply {
                    moveTo(w * 0.10f, h * 0.54f)
                    lineTo(w * 0.30f, h * 0.54f)
                    lineTo(w * 0.42f, h * 0.24f)
                    lineTo(w * 0.58f, h * 0.76f)
                    lineTo(w * 0.70f, h * 0.46f)
                    lineTo(w * 0.90f, h * 0.46f)
                }
                drawPath(p, tint, style = stroke)
            }
            MrsadIconType.SETTINGS -> {
                drawCircle(tint, radius = w * 0.18f, style = stroke)
                drawCircle(tint, radius = w * 0.38f, style = stroke)
            }
            MrsadIconType.SENSORS -> {
                drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.5f, h * 0.5f))
                drawCircle(tint, radius = w * 0.26f, style = stroke)
                drawCircle(tint, radius = w * 0.42f, style = stroke)
            }
        }
    }
}
