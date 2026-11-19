package dev.mrsad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.mrsad.app.ui.theme.MrsadTheme

/**
 * Real-data charts. Every chart renders exactly the list it is given — no
 * synthetic filler points, no decorative randomness. Empty input shows an
 * empty state instead of a fake curve.
 */
@Composable
fun SparkLine(
    values: List<Double>,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    label: String? = null,
) {
    val colors = MrsadTheme.colors
    val lineColor = tint ?: colors.metalMid

    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MrsadTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (values.size < 2) {
            Text(
                text = "برای نمودار به دادهٔ بیشتری نیاز است",
                style = MrsadTheme.type.caption,
                color = colors.textMuted,
            )
            return@Column
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val maxV = values.max()
            val minV = values.min()
            val range = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
            val stepX = size.width / (values.size - 1)
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = index * stepX,
                    y = (size.height - ((value - minV) / range * size.height * 0.86).toFloat() - size.height * 0.07f),
                )
            }
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            // soft wash under the curve
            val fill = Path().apply {
                addPath(path)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                )
            )
            drawPath(path, lineColor, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(lineColor, radius = 2.6.dp.toPx(), center = points.last())
        }
    }
}

/** Horizontal bar rows for category comparison (real values). */
@Composable
fun BarRow(
    label: String,
    value: Double,
    maxValue: Double,
    modifier: Modifier = Modifier,
    valueLabel: String,
    tint: Color? = null,
) {
    val colors = MrsadTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MrsadTheme.type.label, color = colors.textSecondary)
            Text(valueLabel, style = MrsadTheme.type.label, color = colors.textPrimary)
        }
        MrsadProgressBar(
            progress = if (maxValue <= 0) 0f else (value / maxValue).toFloat(),
            tint = tint,
            height = 5.dp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
