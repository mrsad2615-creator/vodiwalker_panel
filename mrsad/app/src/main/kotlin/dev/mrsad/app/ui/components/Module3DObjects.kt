package dev.mrsad.app.ui.components

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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.mrsad.app.ui.theme.ModulePalettes
import dev.mrsad.app.ui.theme.ModuleSpectralIdentity
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import kotlin.math.cos
import kotlin.math.sin

/**
 * OBSIDIAN SPECTRUM — 3D-Inspired Module Sculptures (Master Prompt §5 & §10).
 *
 * Each training pillar has its own dimensional dark-metallic + glass icon
 * illuminated by its module-specific spectral identity:
 * - [ModuleSculptureType.TYPING]  → Electric Blue (`#176ED4` / `#54B8FF`)
 * - [ModuleSculptureType.READING] → Deep Violet & Lavender (`#7040B9` / `#C69CFF`)
 * - [ModuleSculptureType.EYE]     → Teal & Aqua (`#138F91` / `#67E7DA`)
 * - [ModuleSculptureType.BRAIN]   → Magenta & Pink (`#B53D91` / `#F18ACD`)
 */
enum class ModuleSculptureType {
    TYPING,
    READING,
    EYE,
    BRAIN,
    ;

    fun spectralIdentity(): ModuleSpectralIdentity = when (this) {
        TYPING -> ModulePalettes.Typing
        READING -> ModulePalettes.Reading
        EYE -> ModulePalettes.Eye
        BRAIN -> ModulePalettes.Brain
    }
}

@Composable
fun ModuleSculpture3D(
    type: ModuleSculptureType,
    modifier: Modifier = Modifier,
    intensity: Float = 0.90f,
    animated: Boolean = false,
    accentOverride: Color? = null,
) {
    val identity = type.spectralIdentity()
    val primary = identity.primary
    val highlight = accentOverride ?: identity.highlight

    Canvas(modifier = modifier.size(56.dp)) {
        val w = size.width
        val h = size.height
        val r = w.coerceAtMost(h) * 0.46f
        val c = Offset(w / 2f, h / 2f)

        // 1. Dimensional obsidian + spectral glass pedestal.
        drawRoundRect(
            color = ObsidianSpectrum.DeepBlack,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            cornerRadius = CornerRadius(r * 0.36f, r * 0.36f),
        )
        drawRoundRect(
            color = primary.copy(alpha = 0.22f),
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            cornerRadius = CornerRadius(r * 0.36f, r * 0.36f),
        )

        // 2. Internal spectral halo ring.
        drawCircle(
            color = highlight.copy(alpha = 0.14f),
            radius = r * 0.78f,
            center = c,
        )

        // 3. Top-lit spectral rim border.
        drawRoundRect(
            color = highlight.copy(alpha = 0.62f),
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            cornerRadius = CornerRadius(r * 0.36f, r * 0.36f),
            style = Stroke(width = 1.3.dp.toPx()),
        )

        when (type) {
            ModuleSculptureType.TYPING -> drawTypingSculpture(
                c = c,
                r = r * 0.78f,
                primary = primary,
                highlight = highlight,
            )
            ModuleSculptureType.READING -> drawReadingSculpture(
                c = c,
                r = r * 0.78f,
                primary = primary,
                highlight = highlight,
            )
            ModuleSculptureType.EYE -> drawEyeSculpture(
                c = c,
                r = r * 0.78f,
                primary = primary,
                highlight = highlight,
            )
            ModuleSculptureType.BRAIN -> drawBrainSculpture(
                c = c,
                r = r * 0.78f,
                primary = primary,
                highlight = highlight,
            )
        }
    }
}

private fun DrawScope.drawTypingSculpture(
    c: Offset,
    r: Float,
    primary: Color,
    highlight: Color,
) {
    val deckW = r * 1.62f
    val deckH = r * 1.06f
    val topLeft = Offset(c.x - deckW / 2f, c.y - deckH / 2f + r * 0.08f)

    drawRoundRect(
        color = ObsidianSpectrum.ObsidianBlack,
        topLeft = Offset(topLeft.x, topLeft.y + 2.5.dp.toPx()),
        size = Size(deckW, deckH),
        cornerRadius = CornerRadius(r * 0.22f, r * 0.22f),
    )
    drawRoundRect(
        color = primary.copy(alpha = 0.36f),
        topLeft = topLeft,
        size = Size(deckW, deckH),
        cornerRadius = CornerRadius(r * 0.22f, r * 0.22f),
    )
    drawRoundRect(
        color = highlight.copy(alpha = 0.72f),
        topLeft = topLeft,
        size = Size(deckW, deckH),
        cornerRadius = CornerRadius(r * 0.22f, r * 0.22f),
        style = Stroke(width = 1.3.dp.toPx()),
    )

    val cols = 4
    val rows = 2
    val padX = deckW * 0.11f
    val padY = deckH * 0.16f
    val gapX = deckW * 0.06f
    val gapY = deckH * 0.10f
    val keyW = (deckW - padX * 2f - gapX * (cols - 1)) / cols
    val keyH = (deckH - padY * 2f - gapY * (rows - 1)) / rows

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val kx = topLeft.x + padX + col * (keyW + gapX)
            val ky = topLeft.y + padY + row * (keyH + gapY)
            val isHomeKey = row == 0 && (col == 1 || col == 2)
            drawRoundRect(
                color = if (isHomeKey) highlight else primary.copy(alpha = 0.52f),
                topLeft = Offset(kx, ky),
                size = Size(keyW, keyH * 0.72f),
                cornerRadius = CornerRadius(2.2.dp.toPx(), 2.2.dp.toPx()),
            )
        }
    }

    val barW = deckW * 0.56f
    val barH = deckH * 0.14f
    drawRoundRect(
        color = ObsidianSpectrum.SoftWhite,
        topLeft = Offset(c.x - barW / 2f, topLeft.y + deckH - padY - barH * 0.35f),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
    )

    drawArc(
        color = highlight,
        startAngle = 205f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(c.x - r * 0.75f, c.y - r * 0.90f),
        size = Size(r * 1.50f, r * 0.70f),
        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawReadingSculpture(
    c: Offset,
    r: Float,
    primary: Color,
    highlight: Color,
) {
    val w = r * 0.78f
    val h = r * 0.62f
    val lift = r * 0.16f

    val leftWing = Path().apply {
        moveTo(c.x, c.y - h * 0.32f)
        lineTo(c.x - w, c.y - h * 0.85f - lift)
        lineTo(c.x - w, c.y + h * 0.68f - lift)
        lineTo(c.x, c.y + h * 0.92f)
        close()
    }
    val rightWing = Path().apply {
        moveTo(c.x, c.y - h * 0.32f)
        lineTo(c.x + w, c.y - h * 0.85f - lift)
        lineTo(c.x + w, c.y + h * 0.68f - lift)
        lineTo(c.x, c.y + h * 0.92f)
        close()
    }

    drawPath(leftWing, color = primary.copy(alpha = 0.52f))
    drawPath(rightWing, color = highlight.copy(alpha = 0.28f))
    drawPath(leftWing, color = highlight.copy(alpha = 0.78f), style = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round))
    drawPath(rightWing, color = highlight, style = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round))

    drawLine(
        color = ObsidianSpectrum.SoftWhite,
        start = Offset(c.x, c.y - h * 0.42f),
        end = Offset(c.x, c.y + h * 0.92f),
        strokeWidth = 1.8.dp.toPx(),
        cap = StrokeCap.Round,
    )
    for (i in 1..2) {
        val ratio = i / 3.2f
        val yLeft = c.y - h * 0.32f + (h * 1.24f) * ratio
        drawLine(
            color = highlight.copy(alpha = 0.72f),
            start = Offset(c.x - w * 0.22f, yLeft - lift * 0.3f),
            end = Offset(c.x - w * 0.76f, yLeft - lift * 0.85f),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ObsidianSpectrum.SoftWhite.copy(alpha = 0.75f),
            start = Offset(c.x + w * 0.22f, yLeft - lift * 0.3f),
            end = Offset(c.x + w * 0.76f, yLeft - lift * 0.85f),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawEyeSculpture(
    c: Offset,
    r: Float,
    primary: Color,
    highlight: Color,
) {
    val w = r * 0.98f
    val h = r * 0.58f
    val contour = Path().apply {
        moveTo(c.x - w, c.y)
        quadraticTo(c.x, c.y - h * 1.35f, c.x + w, c.y)
        quadraticTo(c.x, c.y + h * 1.35f, c.x - w, c.y)
        close()
    }
    drawPath(contour, color = primary.copy(alpha = 0.42f))
    drawPath(contour, color = highlight, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))

    drawCircle(
        color = highlight.copy(alpha = 0.55f),
        radius = r * 0.42f,
        center = c,
        style = Stroke(width = 1.3.dp.toPx()),
    )
    drawCircle(
        color = primary.copy(alpha = 0.78f),
        radius = r * 0.26f,
        center = c,
    )
    drawCircle(
        color = highlight,
        radius = r * 0.13f,
        center = c,
    )
    drawCircle(
        color = ObsidianSpectrum.SoftWhite,
        radius = r * 0.055f,
        center = Offset(c.x - r * 0.09f, c.y - r * 0.09f),
    )
}

private fun DrawScope.drawBrainSculpture(
    c: Offset,
    r: Float,
    primary: Color,
    highlight: Color,
) {
    val outerRadius = r * 0.86f
    val innerRadius = r * 0.36f
    val outer = (0 until 6).map { i ->
        val a = Math.toRadians((i * 60.0) - 90.0)
        Offset(c.x + cos(a).toFloat() * outerRadius, c.y + sin(a).toFloat() * outerRadius)
    }
    val inner = (0 until 6).map { i ->
        val a = Math.toRadians((i * 60.0) - 60.0)
        Offset(c.x + cos(a).toFloat() * innerRadius, c.y + sin(a).toFloat() * innerRadius)
    }

    val hull = Path().apply {
        moveTo(outer[0].x, outer[0].y)
        for (i in 1 until outer.size) lineTo(outer[i].x, outer[i].y)
        close()
    }
    drawPath(hull, color = primary.copy(alpha = 0.36f))
    drawPath(hull, color = highlight.copy(alpha = 0.85f), style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))

    for (i in 0 until 6) {
        drawLine(
            color = highlight.copy(alpha = 0.62f),
            start = outer[i],
            end = inner[i],
            strokeWidth = 1.2.dp.toPx(),
        )
        drawLine(
            color = primary.copy(alpha = 0.72f),
            start = inner[i],
            end = inner[(i + 1) % 6],
            strokeWidth = 1.2.dp.toPx(),
        )
        drawCircle(
            color = highlight,
            radius = 2.4.dp.toPx(),
            center = outer[i],
        )
    }

    drawCircle(
        color = primary.copy(alpha = 0.85f),
        radius = r * 0.22f,
        center = c,
    )
    drawCircle(
        color = ObsidianSpectrum.SoftWhite,
        radius = r * 0.10f,
        center = c,
    )
}
