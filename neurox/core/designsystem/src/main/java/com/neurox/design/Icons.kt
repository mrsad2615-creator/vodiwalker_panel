package com.neurox.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { HOME, TRAINING, ANALYTICS, LIBRARY, PROFILE, SETTINGS, VISION, REFLEX, READ, TYPE }

@Composable
fun NeuroxIcon(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, label: String? = null) {
    Canvas(modifier.size(24.dp).semantics { if (label != null) contentDescription = label }) {
        val unit = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, p(x, y), p(x2, y2), 1.6f * unit, StrokeCap.Round)
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRect(tint, p(x, y), Size(w * unit, h * unit), style = Stroke(1.6f * unit))
        when (glyph) {
            Glyph.HOME -> { line(3f, 11f, 12f, 3f); line(12f, 3f, 21f, 11f); line(5f, 10f, 5f, 21f); line(19f, 10f, 19f, 21f); line(5f, 21f, 19f, 21f); box(10f, 14f, 4f, 7f) }
            Glyph.TRAINING -> { box(3f, 3f, 7f, 7f); box(14f, 3f, 7f, 7f); box(3f, 14f, 7f, 7f); box(14f, 14f, 7f, 7f) }
            Glyph.ANALYTICS -> { line(4f, 20f, 21f, 20f); line(5f, 16f, 5f, 12f); line(11f, 16f, 11f, 7f); line(17f, 16f, 17f, 3f) }
            Glyph.LIBRARY, Glyph.READ -> { box(3f, 4f, 18f, 16f); line(12f, 4f, 12f, 20f); line(6f, 8f, 9f, 8f); line(15f, 8f, 18f, 8f); line(6f, 12f, 9f, 12f); line(15f, 12f, 18f, 12f) }
            Glyph.PROFILE -> { drawCircle(tint, 4f * unit, p(12f, 7f), style = Stroke(1.6f * unit)); drawArc(tint, 180f, 180f, false, p(4f, 14f), Size(16f * unit, 13f * unit), style = Stroke(1.6f * unit)) }
            Glyph.SETTINGS -> { line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f); drawCircle(tint, 2f * unit, p(8f, 6f)); drawCircle(tint, 2f * unit, p(16f, 12f)); drawCircle(tint, 2f * unit, p(10f, 18f)) }
            Glyph.VISION -> { drawOval(tint, p(2f, 6f), Size(20f * unit, 12f * unit), style = Stroke(1.6f * unit)); drawCircle(tint, 3f * unit, p(12f, 12f), style = Stroke(1.6f * unit)) }
            Glyph.REFLEX -> { val path = Path().apply { moveTo(14f * unit, 2f * unit); lineTo(5f * unit, 14f * unit); lineTo(11f * unit, 14f * unit); lineTo(10f * unit, 22f * unit); lineTo(20f * unit, 9f * unit); lineTo(14f * unit, 9f * unit); close() }; drawPath(path, tint, style = Stroke(1.6f * unit)) }
            Glyph.TYPE -> { box(2f, 5f, 20f, 14f); for (x in listOf(6f, 10f, 14f, 18f)) { line(x, 9f, x, 10f); line(x, 13f, x, 14f) }; line(8f, 17f, 16f, 17f) }
        }
    }
}

@Composable
fun NeuroxEmblem(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val gold = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.size(size).semantics { contentDescription = "NEUROX" }) {
        val u = this.size.minDimension / 60
        val shape = Path().apply {
            moveTo(8*u, 49*u); lineTo(8*u, 12*u); lineTo(16*u, 7*u); lineTo(44*u, 40*u)
            lineTo(44*u, 12*u); lineTo(52*u, 7*u); lineTo(52*u, 49*u); lineTo(44*u, 54*u)
            lineTo(16*u, 21*u); lineTo(16*u, 49*u); close()
        }
        drawPath(shape, Brush.linearGradient(listOf(gold, highlight, gold)))
    }
}
