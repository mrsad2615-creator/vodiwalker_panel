package ai.dana.ui

import ai.dana.R
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun faDigits(value: Number): String = value.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

internal enum class Mark { HOME, CHAT, ENGINES, ACCOUNT, NOTES, COMPARE, IMAGE, ACTION, BACK, ARROW }

@Composable
internal fun StudioHeader(tab: DashboardTab, offline: Boolean, secondary: Boolean, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (secondary) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    StudioMark(Mark.BACK, Ice, stringResource(R.string.studio_back))
                }
                Text(stringResource(tab.label), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            } else {
                Icon(painterResource(R.drawable.ic_dana), contentDescription = null, modifier = Modifier.size(38.dp), tint = Color.Unspecified)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.app_wordmark), style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.4.sp, textDirection = TextDirection.Ltr), color = Titanium, maxLines = 1)
                }
            }
            StudioPill(stringResource(if (offline) R.string.studio_mode_offline else R.string.studio_mode_online),
                color = if (offline) Titanium else Mint, dot = true)
        }
    }
}

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
internal fun StudioHero(onStart: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .background(Color(0xFF101817)).heightIn(min = 224.dp), contentAlignment = Alignment.CenterStart) {
        val split = maxWidth >= 320.dp && LocalDensity.current.fontScale < 1.24f
        Image(painterResource(R.drawable.dana_studio_art), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(if (split) Brush.horizontalGradient(0f to Color.Transparent, 0.38f to Color(0x20101817),
            0.56f to Color(0xBF101817), 1f to Color(0xFF101817)) else Brush.horizontalGradient(listOf(Color(0xAC101817), Color(0xEE101817)))))
        Column(Modifier.fillMaxWidth(if (split) 0.59f else 1f).padding(19.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            StudioPill(stringResource(R.string.studio_persian))
            Text(stringResource(R.string.studio_hello), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text(stringResource(R.string.studio_intro), style = MaterialTheme.typography.bodyMedium, color = Titanium)
            Button(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) {
                Text(stringResource(R.string.hub_begin_chat), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

internal class StudioAction(@StringRes val title: Int, @StringRes val hint: Int, val mark: Mark, val action: () -> Unit)

@Composable
internal fun ActionPair(first: StudioAction, second: StudioAction) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 318.dp && LocalDensity.current.fontScale < 1.28f) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(first, Modifier.weight(1f))
                ActionTile(second, Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(first, Modifier.fillMaxWidth())
            ActionTile(second, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ActionTile(action: StudioAction, modifier: Modifier) {
    Card(onClick = action.action, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 131.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                StudioMark(action.mark, Mint)
            }
            Text(stringResource(action.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(action.hint), color = Titanium, style = MaterialTheme.typography.bodySmall)
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
            Mark.HOME -> { path(3f to 11f, 12f to 3f, 21f to 11f, 21f to 20f, 3f to 20f, 3f to 11f); rect(10f, 14f, 4f, 6f, 1f) }
            Mark.CHAT -> { rect(3f, 4f, 18f, 13f, 3f); path(7f to 17f, 7f to 21f, 12f to 17f); segment(7f, 10.5f, 17f, 10.5f) }
            Mark.ENGINES -> { path(12f to 2.5f, 14.5f to 9.5f, 21.5f to 12f, 14.5f to 14.5f, 12f to 21.5f, 9.5f to 14.5f, 2.5f to 12f, 9.5f to 9.5f, 12f to 2.5f) }
            Mark.ACCOUNT -> {
                drawCircle(tint, 3.05f * u, p(12f, 8f), style = Stroke(width))
                drawArc(tint, 185f, 170f, false, p(5f, 14f), Size(14f * u, 11f * u), style = Stroke(width, cap = StrokeCap.Round))
            }
            Mark.NOTES -> { rect(5f, 3f, 14f, 18f, 2f); segment(8f, 9f, 16f, 9f); segment(8f, 14f, 14f, 14f) }
            Mark.COMPARE -> { rect(2.5f, 5f, 8f, 14f, 2f); rect(13.5f, 5f, 8f, 14f, 2f); segment(5f, 10f, 8f, 10f); segment(16f, 10f, 19f, 10f) }
            Mark.IMAGE -> { rect(3f, 4f, 18f, 16f, 3f); drawCircle(tint, 1.6f * u, p(8f, 9f)); path(5f to 17f, 10f to 12f, 13f to 15f, 16f to 11f, 21f to 16f) }
            Mark.ACTION -> path(13f to 2.5f, 4.5f to 13f, 11f to 13f, 10.5f to 21.5f, 19.5f to 10.5f, 13f to 10.5f, 13f to 2.5f)
            Mark.BACK -> { path(11f to 5f, 18f to 12f, 11f to 19f); segment(4f, 12f, 18f, 12f) }
            Mark.ARROW -> { path(12f to 5f, 5f to 12f, 12f to 19f); segment(5f, 12f, 20f, 12f) }
        }
    }
}
