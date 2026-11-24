package com.neurox.app

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.neurox.domain.ReactionShape

@Composable
internal fun shapeName(shape: ReactionShape): String = when (shape) {
    ReactionShape.CIRCLE -> tr("دایره", "Circle")
    ReactionShape.TRIANGLE -> tr("مثلث", "Triangle")
    ReactionShape.SQUARE -> tr("مربع", "Square")
}

@Composable
internal fun ReactionShapeIcon(shape: ReactionShape, modifier: Modifier = Modifier) {
    val label = shapeName(shape)
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.semantics { contentDescription = label }) {
        val u = size.minDimension / 24f
        val stroke = Stroke(1.6f * u)
        when (shape) {
            ReactionShape.CIRCLE -> drawCircle(tint, 8f * u, Offset(12f * u, 12f * u), style = stroke)
            ReactionShape.SQUARE -> drawRect(tint, Offset(4f * u, 4f * u), Size(16f * u, 16f * u), style = stroke)
            ReactionShape.TRIANGLE -> drawPath(Path().apply {
                moveTo(12f * u, 3f * u); lineTo(22f * u, 21f * u); lineTo(2f * u, 21f * u); close()
            }, tint, style = stroke)
        }
    }
}

@Composable
internal fun PatternGrid(cells: Set<Int>, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val positions = cells.sorted().joinToString(", ") { (it + 1).toString() }
    val label = tr("خانه‌های پُر از بالا چپ، ردیف به ردیف: $positions", "Filled cells, from top left in row order: $positions")
    Canvas(modifier.semantics { contentDescription = label }) {
        val cell = size.minDimension / 3f
        val gap = cell * 0.12f
        repeat(9) { index ->
            val origin = Offset((index % 3) * cell + gap, (index / 3) * cell + gap)
            val dimensions = Size(cell - gap * 2, cell - gap * 2)
            if (index in cells) drawRect(fill, origin, dimensions)
            drawRect(outline, origin, dimensions, style = Stroke(1.5f))
        }
    }
}
