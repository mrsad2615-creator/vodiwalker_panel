package com.typflow.app.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.theme.TypflowTheme

fun tr(lang: String, fa: String, en: String): String = if (lang == "fa") fa else en

/** Full-screen cinematic backdrop: deep background + glowing orbs + vignette. */
@Composable
fun CinematicBackdrop(theme: TypflowTheme, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(theme.background)
            // Orb 1 — primary glow (top)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(theme.primary.copy(alpha = 0.20f), Color.Transparent),
                    center = center.copy(y = size.height * 0.08f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = center.copy(y = size.height * 0.08f)
            )
            // Orb 2 — accent glow (bottom)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(theme.accent.copy(alpha = 0.16f), Color.Transparent),
                    center = center.copy(y = size.height * 0.95f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = center.copy(y = size.height * 0.95f)
            )
            // Orb 3 — secondary glow (middle side)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(theme.secondary.copy(alpha = 0.10f), Color.Transparent),
                    center = center.copy(x = size.width * 0.9f, y = size.height * 0.5f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = center.copy(x = size.width * 0.9f, y = size.height * 0.5f)
            )
            // Vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                    center = center,
                    radius = size.width * 0.9f
                )
            )
        }
        // keep references used (avoid warnings)
        @Suppress("UNUSED_EXPRESSION") run { w; h }
    }
}

@Composable
fun GlassCard(
    theme: TypflowTheme,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(theme.surface.copy(alpha = theme.glassAlpha + 0.55f))
            .border(1.dp, theme.primary.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        content()
    }
}

@Composable
fun GradientButton(
    theme: TypflowTheme,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .shadow(18.dp, shape, ambientColor = theme.primary, spotColor = theme.primary)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(theme.primary, theme.secondary, theme.accent)
                )
            )
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF050B12),
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun GhostButton(
    theme: TypflowTheme,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = theme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun AnimatedNumber(value: Int, theme: TypflowTheme, fontSize: Int = 34) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(700),
        label = "animatedNumber"
    )
    Text(
        text = animated.toString(),
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Black,
        color = theme.onBackground
    )
}

@Composable
fun StatCard(
    theme: TypflowTheme,
    label: String,
    value: Int,
    unit: String,
    modifier: Modifier = Modifier
) {
    GlassCard(theme, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = theme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedNumber(value = value, theme = theme, fontSize = 34)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = unit,
                        color = theme.primary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(theme: TypflowTheme, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(theme.gradient))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = theme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HoloText(text: String, theme: TypflowTheme, fontSize: Int, fontWeight: FontWeight = FontWeight.Black) {
    Text(
        text = text,
        style = TextStyle(
            brush = Brush.horizontalGradient(theme.gradient),
            fontSize = fontSize.sp,
            fontWeight = fontWeight
        )
    )
}

@Composable
fun ProgressRing(
    theme: TypflowTheme,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 110.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(size)) {
        val stroke = 10.dp.toPx()
        drawArc(
            color = theme.surfaceVariant,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            brush = Brush.sweepGradient(theme.gradient),
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun Chip(
    theme: TypflowTheme,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.horizontalGradient(theme.gradient)
                else Brush.linearGradient(listOf(theme.surfaceVariant, theme.surfaceVariant))
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else theme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
