package dev.mrsad.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.core.progression.ProgressionEngine

data class SparkPoint(val index: Int, val height: Float)

object SparkGeometry {

    fun height(value: Double, values: List<Double?>): Float {
        val present = values.filterNotNull()
        if (present.isEmpty()) return 0.5f
        val max = present.max()
        val min = present.min()
        val flat = max - min <= 0.0001
        val range = if (flat) 1.0 else max - min
        val fraction = if (flat) 0.5 else (value - min) / range
        return (fraction * 0.86 + 0.07).toFloat()
    }

    fun runs(values: List<Double?>): List<List<SparkPoint>> {
        val present = values.filterNotNull()
        if (present.size < 2) return emptyList()

        val out = ArrayList<List<SparkPoint>>()
        var run = ArrayList<SparkPoint>()
        values.forEachIndexed { index, value ->
            if (value == null) {
                if (run.size >= 2) out += run
                run = ArrayList()
            } else {
                run += SparkPoint(index, height(value, values))
            }
        }
        if (run.size >= 2) out += run
        return out
    }
}

/**
 * OBSIDIAN SPECTRUM — Real-Data Spectral SparkLine (Master Prompt §10).
 */
@Composable
fun SparkLine(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    label: String? = null,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val lineColor = tint ?: ObsidianSpectrum.BrightBlue
    val present = values.filterNotNull()

    val reveal = remember { Animatable(0f) }
    val signature = remember(values) { values.hashCode() }
    LaunchedEffect(signature) {
        if (motion.deterministic || motion.reducedMotion) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        reveal.snapTo(0f)
        reveal.animateTo(1f, motion.spatialDefault())
    }

    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MrsadTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (present.size < 2) {
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
                .height(68.dp),
        ) {
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            fun xOf(index: Int): Float = index * stepX
            fun yOf(height: Float): Float = size.height - height * size.height

            val revealX = size.width * reveal.value.coerceIn(0f, 1f)
            clipRect(right = revealX) {
                SparkGeometry.runs(values).forEach { run ->
                    val path = Path().apply {
                        moveTo(xOf(run.first().index), yOf(run.first().height))
                        run.drop(1).forEach { lineTo(xOf(it.index), yOf(it.height)) }
                    }
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xOf(run.last().index), size.height)
                        lineTo(xOf(run.first().index), size.height)
                        close()
                    }
                    drawPath(
                        fill,
                        Brush.verticalGradient(
                            listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                        ),
                    )
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }

                val lastIndex = values.indexOfLast { it != null }
                if (lastIndex >= 0) {
                    val value = values[lastIndex] ?: 0.0
                    val cx = xOf(lastIndex)
                    val cy = yOf(SparkGeometry.height(value, values))
                    drawCircle(
                        color = lineColor.copy(alpha = 0.30f),
                        radius = 6.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = ObsidianSpectrum.SoftWhite,
                        radius = 2.8.dp.toPx(),
                        center = Offset(cx, cy),
                    )
                }
            }
        }
    }
}

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
            height = 6.dp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * OBSIDIAN SPECTRUM — 28-Day Streak & Consistency Matrix (`تقویم استمرار`).
 */
@Composable
fun StreakCalendarMatrix(
    days: List<ProgressionEngine.CalendarDayCell>,
    modifier: Modifier = Modifier,
) {
    val colors = MrsadTheme.colors
    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                week.forEach { cell ->
                    val bgColor = when {
                        cell.active -> ObsidianSpectrum.ElectricBlue.copy(alpha = 0.55f)
                        cell.isToday -> colors.surfaceHigh
                        else -> colors.backgroundElevated
                    }
                    val borderColor = when {
                        cell.isToday -> ObsidianSpectrum.Aqua
                        cell.active -> ObsidianSpectrum.BrightBlue
                        else -> colors.border
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.4f)
                            .clip(shape)
                            .background(bgColor)
                            .border(1.dp, borderColor, shape),
                    )
                }
            }
        }
    }
}
