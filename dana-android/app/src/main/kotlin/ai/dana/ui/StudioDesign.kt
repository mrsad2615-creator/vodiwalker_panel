package ai.dana.ui

import ai.dana.R
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal fun faDigits(value: Number): String = value.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

internal enum class Mark { CHAT, MESSAGE, ENGINES, IMAGE, PHONE, ACTION, BACK, ARROW, MORE, ADD, MIC, STOP, SETTINGS }

@Composable
internal fun StudioPill(text: String, color: Color = Mint, dot: Boolean = false) {
    Surface(color = color.copy(alpha = 0.10f), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dot) Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
internal fun OfflinePanel(@StringRes body: Int, enabled: Boolean, onAllow: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        StudioPill(stringResource(R.string.studio_mode_offline), Titanium, dot = true)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = Titanium)
        OutlinedButton(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp), enabled = enabled) {
            Text(stringResource(R.string.studio_allow_network))
        }
    }
}

@Composable
internal fun StudioMark(mark: Mark, tint: Color, description: String? = null) {
    Glyph(mark, tint, if (description == null) Modifier else Modifier.semantics { contentDescription = description })
}

@Composable
private fun Glyph(mark: Mark, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val width = 1.65f * u
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun segment(x: Float, y: Float, ex: Float, ey: Float) = drawLine(tint, p(x, y), p(ex, ey), width, StrokeCap.Round)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(tint, p(x, y), Size(w * u, h * u), CornerRadius(r * u),
            style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        fun path(vararg xy: Pair<Float, Float>) {
            val route = Path().apply {
                moveTo(xy.first().first * u, xy.first().second * u)
                xy.drop(1).forEach { (x, y) -> lineTo(x * u, y * u) }
            }
            drawPath(route, tint, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        when (mark) {
            Mark.CHAT -> { rect(3f, 4f, 18f, 13f, 3f); path(7f to 17f, 7f to 21f, 12f to 17f); segment(7f, 10.5f, 17f, 10.5f) }
            Mark.MESSAGE -> { rect(2.5f, 5f, 19f, 14f, 2.5f); path(3.5f to 6.5f, 12f to 13f, 20.5f to 6.5f) }
            Mark.ENGINES -> { path(12f to 2.5f, 14.5f to 9.5f, 21.5f to 12f, 14.5f to 14.5f, 12f to 21.5f, 9.5f to 14.5f, 2.5f to 12f, 9.5f to 9.5f, 12f to 2.5f) }
            Mark.PHONE -> { rect(6f, 2.5f, 12f, 19f, 2.7f); segment(10.5f, 18.5f, 13.5f, 18.5f) }
            Mark.ADD -> { segment(12f, 5f, 12f, 19f); segment(5f, 12f, 19f, 12f) }
            Mark.MORE -> listOf(5f, 12f, 19f).forEach { x -> drawCircle(tint, 1.55f * u, p(x, 12f)) }
            Mark.MIC -> {
                rect(9f, 3f, 6f, 12f, 3f)
                drawArc(tint, 0f, 180f, false, p(5.5f, 7.5f), Size(13f * u, 12f * u), style = Stroke(width, cap = StrokeCap.Round))
                segment(12f, 20f, 12f, 22f)
            }
            Mark.STOP -> rect(6f, 6f, 12f, 12f, 2f)
            Mark.SETTINGS -> {
                segment(4f, 7f, 20f, 7f); segment(4f, 17f, 20f, 17f)
                segment(9f, 4f, 9f, 10f); segment(15f, 14f, 15f, 20f)
            }
            Mark.IMAGE -> { rect(3f, 4f, 18f, 16f, 3f); drawCircle(tint, 1.6f * u, p(8f, 9f)); path(5f to 17f, 10f to 12f, 13f to 15f, 16f to 11f, 21f to 16f) }
            Mark.ACTION -> path(13f to 2.5f, 4.5f to 13f, 11f to 13f, 10.5f to 21.5f, 19.5f to 10.5f, 13f to 10.5f, 13f to 2.5f)
            Mark.BACK -> { path(11f to 5f, 18f to 12f, 11f to 19f); segment(4f, 12f, 18f, 12f) }
            Mark.ARROW -> { path(12f to 5f, 5f to 12f, 12f to 19f); segment(5f, 12f, 20f, 12f) }
        }
    }
}
