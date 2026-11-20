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
 * A point of a spark line: its slot in the window and its height, measured
 * from the bottom of the plot as a fraction between 0 and 1.
 */
data class SparkPoint(val index: Int, val height: Float)

/**
 * The geometry of a spark line, kept away from the drawing code so it can
 * be tested without a device.
 */
object SparkGeometry {

    /**
     * The normalised height of one measurement, from 0 (floor) to 1 (top).
     *
     * The plot keeps a small margin above and below the curve, and a flat
     * series sits in the middle: on the floor it would read as "almost
     * nothing happened".
     */
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

    /**
     * Splits a window series into the runs of consecutive measured days.
     *
     * A `null` slot is a day without a measurement, not a zero: the line
     * breaks there instead of dipping to the floor, so the chart never
     * invents a session the user did not have. A run needs at least two
     * points to be a line, so a lone measurement produces no run at all.
     */
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
 * Real-data charts. Every chart renders exactly the list it is given — no
 * synthetic filler points, no decorative randomness. Empty input shows an
 * empty state instead of a fake curve.
 *
 * A `null` slot is a day without a measurement, not a zero: the line breaks
 * there instead of dipping to the floor, so the chart never invents a
 * session the user did not have.
 */
@Composable
fun SparkLine(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    label: String? = null,
) {
    val colors = MrsadTheme.colors
    val lineColor = tint ?: colors.metalMid
    val present = values.filterNotNull()

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
                .height(64.dp)
        ) {
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            fun xOf(index: Int): Float = index * stepX
            fun yOf(height: Float): Float = size.height - height * size.height

            // Each run of consecutive measured days is drawn as its own
            // curve, so a gap breaks the line instead of bridging it.
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
                        listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                    )
                )
                drawPath(path, lineColor, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
            }

            // The most recent measurement is the one worth pointing at.
            val lastIndex = values.indexOfLast { it != null }
            if (lastIndex >= 0) {
                val value = values[lastIndex] ?: 0.0
                drawCircle(
                    lineColor,
                    radius = 2.6.dp.toPx(),
                    center = Offset(xOf(lastIndex), yOf(SparkGeometry.height(value, values))),
                )
            }
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
