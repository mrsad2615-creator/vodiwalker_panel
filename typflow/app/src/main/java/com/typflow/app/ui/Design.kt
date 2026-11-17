package com.typflow.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.theme.TypflowTheme

fun tr(lang: String, fa: String, en: String): String = if (lang == "fa") fa else en

/** Pure Latin/numeric strings must render LTR even inside an RTL layout. */
private fun isLatinOnly(text: String): Boolean =
    text.none { it.code in 0x0590..0x08FF }

fun textDirFor(text: String): TextDirection =
    if (isLatinOnly(text)) TextDirection.Ltr else TextDirection.ContentOrRtl

fun uiFont(theme: TypflowTheme): FontFamily =
    if (theme.mono) FontFamily.Monospace else FontFamily.SansSerif

fun labelStyle(theme: TypflowTheme, size: Int = 10): TextStyle = TextStyle(
    fontFamily = uiFont(theme),
    fontSize = size.sp,
    letterSpacing = 1.6.sp,
    fontWeight = FontWeight.Medium,
    color = theme.dim
)

/** Hairline grid + vignette backdrop. */
@Composable
fun RawBackdrop(theme: TypflowTheme) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(theme.bg)
        if (theme.gridStep > 0) {
            val step = theme.gridStep.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(theme.grid, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(theme.grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        // top light falloff for depth, still monochrome
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    theme.ink.copy(alpha = 0.035f),
                    Color.Transparent
                ),
                endY = size.height * 0.45f
            )
        )
    }
}

@Composable
fun Hairline(theme: TypflowTheme, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(theme.line)
    )
}

@Composable
fun MicroLabel(
    theme: TypflowTheme,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = theme.dim,
    size: Int = 10
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = uiFont(theme),
            fontSize = size.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            textDirection = textDirFor(text)
        )
    )
}

/** Screen masthead: index, title, hairline. */
@Composable
fun ScreenHeader(
    theme: TypflowTheme,
    index: String,
    title: String,
    subtitle: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicroLabel(theme, index, color = theme.ink, size = 10)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(24.dp).height(1.dp).background(theme.line))
            Spacer(Modifier.width(10.dp))
            MicroLabel(theme, "TYPFLOW", size = 10)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = theme.ink,
            fontFamily = uiFont(theme),
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = theme.dim,
                fontSize = 12.sp,
                fontFamily = uiFont(theme),
                lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        Hairline(theme)
    }
}

/** Bordered monochrome panel (sharp corners, no shadows). */
@Composable
fun Panel(
    theme: TypflowTheme,
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(theme.radius)
    Column(
        modifier = modifier
            .background(theme.surface, shape)
            .border(1.dp, theme.line, shape)
            .padding(padding),
        content = content
    )
}

/** Large monochrome figure with a small unit. */
@Composable
fun BigFigure(
    theme: TypflowTheme,
    value: String,
    unit: String? = null,
    size: Int = 40,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value,
            color = theme.ink,
            fontFamily = uiFont(theme),
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            maxLines = 1,
            textAlign = align,
            style = TextStyle(textDirection = TextDirection.Ltr)
        )
        if (unit != null) {
            Text(
                text = unit,
                color = theme.dim,
                fontFamily = uiFont(theme),
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(bottom = (size / 6).dp)
            )
        }
    }
}

/** Solid inverted button — the only "loud" element in the system. */
@Composable
fun SolidButton(
    theme: TypflowTheme,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(theme.radius)
    Box(
        modifier = modifier
            .height(52.dp)
            .background(theme.ink, shape)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = theme.bg,
            fontFamily = uiFont(theme),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            maxLines = 1
        )
    }
}

/** 1px outlined button. */
@Composable
fun LineButton(
    theme: TypflowTheme,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val shape = RoundedCornerShape(theme.radius)
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (active) theme.ink else Color.Transparent, shape)
            .border(1.dp, if (active) theme.ink else theme.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) theme.bg else theme.ink,
            fontFamily = uiFont(theme),
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
    }
}

/** Small selectable chip used for modes / filters. */
@Composable
fun MonoChip(
    theme: TypflowTheme,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(theme.radius)
    Box(
        modifier = modifier
            .height(36.dp)
            .background(if (selected) theme.ink else Color.Transparent, shape)
            .border(1.dp, if (selected) theme.ink else theme.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) theme.bg else theme.dim,
            fontFamily = uiFont(theme),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            style = TextStyle(textDirection = textDirFor(text))
        )
    }
}

/** Labelled metric cell. */
@Composable
fun StatBlock(
    theme: TypflowTheme,
    label: String,
    value: Int,
    unit: String,
    modifier: Modifier = Modifier,
    size: Int = 30
) {
    Column(modifier = modifier) {
        MicroLabel(theme, label, size = 9)
        Spacer(Modifier.height(6.dp))
        BigFigure(theme, value.toString(), unit, size = size)
    }
}

/** 1px-track progress bar. */
@Composable
fun Bar(
    theme: TypflowTheme,
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(theme.line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(theme.ink)
        )
    }
}

@Composable
fun SectionLabel(theme: TypflowTheme, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = theme.ink,
            fontFamily = uiFont(theme),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(theme.line))
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.height(8.dp))

@Composable
fun Spacer16() = Spacer(Modifier.height(16.dp))

@Composable
fun Spacer24() = Spacer(Modifier.height(24.dp))

@Composable
fun SquareDot(theme: TypflowTheme, size: Dp = 6.dp) {
    Box(Modifier.size(size).background(theme.ink))
}
