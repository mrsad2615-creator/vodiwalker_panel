package dev.mrsad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mrsad.app.ui.theme.MrsadTheme

/**
 * Dual-System Precision Vector Icon Architecture (Master Prompt §7):
 *
 * - **OBSIDIAN (`isNoir == false`)**: Multi-layered precision vector glyphs with
 *   rounded geometry, specular highlights, and brushed-titanium tones
 *   (`LIGHT`, `MID`, `DARK`, `ACCENT`), rendered directly on `Canvas` with
 *   zero offscreen FBO texture allocations.
 * - **NOIR (`isNoir == true`)**: Pure geometric linear vector glyphs with uniform
 *   `1.6f` stroke weight on the 24dp grid, zero filled facets, and zero
 *   decorative shading.
 */

enum class MetalTone { LIGHT, MID, DARK, ACCENT }

data class IconFacet(
    val path: Path,
    val tone: MetalTone,
    val stroke: Boolean = false,
    val strokeWidth: Float = 1.6f,
)

private fun roundRectFacet(
    tone: MetalTone,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float = 2.2f,
    stroke: Boolean = false,
    strokeWidth: Float = 1.6f,
): IconFacet {
    val path = Path().apply {
        addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(radius, radius)))
    }
    return IconFacet(path, tone, stroke = stroke, strokeWidth = strokeWidth)
}

private fun disc(cx: Float, cy: Float, r: Float, tone: MetalTone): IconFacet {
    val path = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    return IconFacet(path, tone)
}

private fun circleStroke(
    cx: Float,
    cy: Float,
    r: Float,
    tone: MetalTone,
    width: Float = 1.7f,
): IconFacet {
    val path = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    return IconFacet(path, tone, stroke = true, strokeWidth = width)
}

private fun ring(cx: Float, cy: Float, rOuter: Float, rInner: Float, tone: MetalTone): IconFacet {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter))
        addOval(Rect(cx - rInner, cy - rInner, cx + rInner, cy + rInner))
    }
    return IconFacet(path, tone)
}

private fun facet(tone: MetalTone, vararg xy: Float): IconFacet {
    val path = Path()
    var i = 0
    while (i < xy.size) {
        if (i == 0) path.moveTo(xy[i], xy[i + 1]) else path.lineTo(xy[i], xy[i + 1])
        i += 2
    }
    path.close()
    return IconFacet(path, tone)
}

private fun strokeFacet(tone: MetalTone, width: Float, vararg xy: Float): IconFacet {
    val path = Path()
    var i = 0
    while (i < xy.size) {
        if (i == 0) path.moveTo(xy[i], xy[i + 1]) else path.lineTo(xy[i], xy[i + 1])
        i += 2
    }
    return IconFacet(path, tone, stroke = true, strokeWidth = width)
}

private fun linearPoly(closed: Boolean = true, vararg xy: Float): Path = Path().apply {
    var i = 0
    while (i < xy.size) {
        if (i == 0) moveTo(xy[i], xy[i + 1]) else lineTo(xy[i], xy[i + 1])
        i += 2
    }
    if (closed) close()
}

private fun linearCircle(cx: Float, cy: Float, r: Float): Path = Path().apply {
    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
}

object MrsadIcons {

    /** Home: Precision 4-tile spatial Bento dashboard grid with active core tile. */
    val Home: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.LIGHT, 3.5f, 3.5f, 11f, 11f, radius = 2.4f),
        roundRectFacet(MetalTone.ACCENT, 13f, 3.5f, 20.5f, 11f, radius = 2.4f),
        roundRectFacet(MetalTone.MID, 3.5f, 13f, 11f, 20.5f, radius = 2.4f),
        roundRectFacet(MetalTone.LIGHT, 13f, 13f, 20.5f, 20.5f, radius = 2.4f),
    )

    /** Type: Precision rounded mechanical keyboard deck with sculpted keycaps & spacebar. */
    val Type: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.DARK, 2.5f, 5.5f, 21.5f, 18.5f, radius = 3.0f),
        roundRectFacet(MetalTone.MID, 2.5f, 5.5f, 21.5f, 18.5f, radius = 3.0f, stroke = true, strokeWidth = 1.5f),
        roundRectFacet(MetalTone.LIGHT, 5.2f, 8.2f, 7.8f, 10.6f, radius = 0.9f),
        roundRectFacet(MetalTone.LIGHT, 9.0f, 8.2f, 11.6f, 10.6f, radius = 0.9f),
        roundRectFacet(MetalTone.LIGHT, 12.8f, 8.2f, 15.4f, 10.6f, radius = 0.9f),
        roundRectFacet(MetalTone.ACCENT, 16.4f, 8.2f, 18.8f, 10.6f, radius = 0.9f),
        roundRectFacet(MetalTone.LIGHT, 5.8f, 11.6f, 8.6f, 13.8f, radius = 0.9f),
        roundRectFacet(MetalTone.LIGHT, 9.8f, 11.6f, 14.2f, 13.8f, radius = 0.9f),
        roundRectFacet(MetalTone.LIGHT, 15.4f, 11.6f, 18.2f, 13.8f, radius = 0.9f),
        roundRectFacet(MetalTone.ACCENT, 7.2f, 14.8f, 16.8f, 16.6f, radius = 1.0f),
    )

    /** Read: Curved open book wings with crisp typography lines. */
    val Read: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.MID, 3.0f, 5.2f, 11.6f, 18.5f, radius = 2.0f),
        roundRectFacet(MetalTone.LIGHT, 12.4f, 5.2f, 21.0f, 18.5f, radius = 2.0f),
        strokeFacet(MetalTone.DARK, 1.4f, 5.5f, 9.2f, 9.5f, 9.2f),
        strokeFacet(MetalTone.DARK, 1.4f, 5.5f, 12.2f, 9.5f, 12.2f),
        strokeFacet(MetalTone.DARK, 1.4f, 5.5f, 15.2f, 8.5f, 15.2f),
        strokeFacet(MetalTone.ACCENT, 1.5f, 14.5f, 9.2f, 18.5f, 9.2f),
        strokeFacet(MetalTone.DARK, 1.4f, 14.5f, 12.2f, 18.5f, 12.2f),
        strokeFacet(MetalTone.DARK, 1.4f, 14.5f, 15.2f, 17.5f, 15.2f),
    )

    /** Eye: Smooth curved ocular contour with iris ring and corneal highlight. */
    val Eye: List<IconFacet> = listOf(
        IconFacet(
            path = Path().apply {
                moveTo(2.2f, 12f)
                quadraticTo(12f, 3.8f, 21.8f, 12f)
                quadraticTo(12f, 20.2f, 2.2f, 12f)
                close()
            },
            tone = MetalTone.DARK,
        ),
        IconFacet(
            path = Path().apply {
                moveTo(2.2f, 12f)
                quadraticTo(12f, 3.8f, 21.8f, 12f)
                quadraticTo(12f, 20.2f, 2.2f, 12f)
                close()
            },
            tone = MetalTone.LIGHT,
            stroke = true,
            strokeWidth = 1.7f,
        ),
        ring(12f, 12f, 4.2f, 1.8f, MetalTone.ACCENT),
        disc(10.6f, 10.6f, 1.1f, MetalTone.LIGHT),
    )

    /** Mind: Neural network node graph with glowing central cortex hub. */
    val Mind: List<IconFacet> = listOf(
        strokeFacet(MetalTone.MID, 1.6f, 12f, 4.5f, 18.8f, 8.5f, 18.8f, 15.5f, 12f, 19.5f, 5.2f, 15.5f, 5.2f, 8.5f, 12f, 4.5f),
        strokeFacet(MetalTone.MID, 1.3f, 12f, 4.5f, 12f, 12f, 18.8f, 8.5f),
        strokeFacet(MetalTone.MID, 1.3f, 5.2f, 15.5f, 12f, 12f, 18.8f, 15.5f),
        disc(12f, 4.5f, 1.8f, MetalTone.LIGHT),
        disc(18.8f, 8.5f, 1.8f, MetalTone.LIGHT),
        disc(18.8f, 15.5f, 1.8f, MetalTone.LIGHT),
        disc(12f, 19.5f, 1.8f, MetalTone.LIGHT),
        disc(5.2f, 15.5f, 1.8f, MetalTone.LIGHT),
        disc(5.2f, 8.5f, 1.8f, MetalTone.LIGHT),
        disc(12f, 12f, 2.8f, MetalTone.ACCENT),
    )

    /** Analytics: Precision rounded bar columns with ascending telemetry trend line. */
    val Analytics: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.DARK, 4.0f, 13.5f, 7.2f, 19.5f, radius = 1.2f),
        roundRectFacet(MetalTone.MID, 9.0f, 10.0f, 12.2f, 19.5f, radius = 1.2f),
        roundRectFacet(MetalTone.LIGHT, 14.0f, 6.5f, 17.2f, 19.5f, radius = 1.2f),
        strokeFacet(MetalTone.ACCENT, 2.0f, 4.5f, 11.2f, 9.8f, 7.2f, 14.2f, 9.0f, 19.5f, 4.2f),
        disc(19.5f, 4.2f, 1.8f, MetalTone.ACCENT),
    )

    /** Assistant: 4-point curved vector star with satellite spark. */
    val Assistant: List<IconFacet> = listOf(
        IconFacet(
            path = Path().apply {
                moveTo(11.5f, 2.8f)
                quadraticTo(11.8f, 10.2f, 19.2f, 10.5f)
                quadraticTo(11.8f, 10.8f, 11.5f, 18.2f)
                quadraticTo(11.2f, 10.8f, 3.8f, 10.5f)
                quadraticTo(11.2f, 10.2f, 11.5f, 2.8f)
                close()
            },
            tone = MetalTone.LIGHT,
        ),
        disc(11.5f, 10.5f, 2.0f, MetalTone.ACCENT),
        disc(18.5f, 17.8f, 1.8f, MetalTone.ACCENT),
    )

    /** Settings: Precision horizontal slider tracks with metallic thumb knobs. */
    val Settings: List<IconFacet> = listOf(
        strokeFacet(MetalTone.MID, 1.8f, 4f, 7f, 20f, 7f),
        strokeFacet(MetalTone.MID, 1.8f, 4f, 12f, 20f, 12f),
        strokeFacet(MetalTone.MID, 1.8f, 4f, 17f, 20f, 17f),
        disc(9.5f, 7f, 2.3f, MetalTone.LIGHT),
        disc(15.5f, 12f, 2.3f, MetalTone.ACCENT),
        disc(11.0f, 17f, 2.3f, MetalTone.LIGHT),
    )

    /** Focus: Precision concentric target reticle with crosshair ticks. */
    val Focus: List<IconFacet> = listOf(
        circleStroke(12f, 12f, 7.5f, MetalTone.LIGHT, width = 1.7f),
        disc(12f, 12f, 2.6f, MetalTone.ACCENT),
        strokeFacet(MetalTone.LIGHT, 1.7f, 12f, 2.2f, 12f, 5.2f),
        strokeFacet(MetalTone.LIGHT, 1.7f, 12f, 18.8f, 12f, 21.8f),
        strokeFacet(MetalTone.LIGHT, 1.7f, 2.2f, 12f, 5.2f, 12f),
        strokeFacet(MetalTone.LIGHT, 1.7f, 18.8f, 12f, 21.8f, 12f),
    )

    /** Trophy: Sculpted chalice cup, stem, and pedestal. */
    val Trophy: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.LIGHT, 6.2f, 4.0f, 17.8f, 11.8f, radius = 3.2f),
        roundRectFacet(MetalTone.ACCENT, 10.6f, 11.5f, 13.4f, 16.8f, radius = 0.8f),
        roundRectFacet(MetalTone.MID, 7.2f, 16.5f, 16.8f, 19.5f, radius = 1.2f),
    )

    /** Flame: Smooth curved dual-layer vector flame. */
    val Flame: List<IconFacet> = listOf(
        IconFacet(
            path = Path().apply {
                moveTo(12f, 2.5f)
                quadraticTo(19.2f, 9.5f, 17.5f, 15.2f)
                quadraticTo(16.0f, 20.5f, 12f, 20.8f)
                quadraticTo(8.0f, 20.5f, 6.5f, 15.2f)
                quadraticTo(4.8f, 9.5f, 12f, 2.5f)
                close()
            },
            tone = MetalTone.LIGHT,
        ),
        IconFacet(
            path = Path().apply {
                moveTo(12f, 9.2f)
                quadraticTo(15.5f, 13.0f, 14.5f, 16.5f)
                quadraticTo(13.6f, 19.2f, 12f, 19.4f)
                quadraticTo(10.4f, 19.2f, 9.5f, 16.5f)
                quadraticTo(8.5f, 13.0f, 12f, 9.2f)
                close()
            },
            tone = MetalTone.ACCENT,
        ),
    )

    /** Studio: Overlapping rounded theme swatches. */
    val Studio: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.DARK, 3.8f, 4.5f, 14.2f, 18.5f, radius = 2.4f),
        roundRectFacet(MetalTone.LIGHT, 9.8f, 5.5f, 20.2f, 19.5f, radius = 2.4f),
        disc(15.0f, 12.5f, 2.4f, MetalTone.ACCENT),
    )

    /** Memory: 2x2 rounded matrix cells with active highlight tile. */
    val Memory: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.LIGHT, 4.2f, 4.2f, 10.8f, 10.8f, radius = 1.8f),
        roundRectFacet(MetalTone.ACCENT, 13.2f, 4.2f, 19.8f, 10.8f, radius = 1.8f),
        roundRectFacet(MetalTone.MID, 4.2f, 13.2f, 10.8f, 19.8f, radius = 1.8f),
        roundRectFacet(MetalTone.LIGHT, 13.2f, 13.2f, 19.8f, 19.8f, radius = 1.8f),
    )

    /** Bolt: Crisp high-voltage lightning vector. */
    val Bolt: List<IconFacet> = listOf(
        facet(MetalTone.LIGHT, 13.6f, 2.5f, 5.5f, 13.2f, 11.8f, 13.2f, 10.4f, 21.5f, 18.5f, 10.8f, 12.2f, 10.8f),
    )

    /** Calendar: Rounded schedule header & active day cells. */
    val Calendar: List<IconFacet> = listOf(
        roundRectFacet(MetalTone.DARK, 3.8f, 5.0f, 20.2f, 20.0f, radius = 2.6f),
        roundRectFacet(MetalTone.LIGHT, 3.8f, 5.0f, 20.2f, 9.5f, radius = 2.0f),
        roundRectFacet(MetalTone.LIGHT, 6.8f, 11.8f, 9.8f, 14.6f, radius = 0.8f),
        roundRectFacet(MetalTone.ACCENT, 10.6f, 11.8f, 13.6f, 14.6f, radius = 0.8f),
        roundRectFacet(MetalTone.MID, 14.4f, 11.8f, 17.4f, 14.6f, radius = 0.8f),
        roundRectFacet(MetalTone.MID, 6.8f, 15.6f, 9.8f, 18.2f, radius = 0.8f),
        roundRectFacet(MetalTone.LIGHT, 10.6f, 15.6f, 13.6f, 18.2f, radius = 0.8f),
    )

    internal fun noirPathsFor(facets: List<IconFacet>): List<Path> = when {
        facets === Home -> NoirHome
        facets === Type -> NoirType
        facets === Read -> NoirRead
        facets === Eye -> NoirEye
        facets === Mind -> NoirMind
        facets === Analytics -> NoirAnalytics
        facets === Assistant -> NoirAssistant
        facets === Settings -> NoirSettings
        facets === Focus -> NoirFocus
        facets === Trophy -> NoirTrophy
        facets === Flame -> NoirFlame
        facets === Studio -> NoirStudio
        facets === Memory -> NoirMemory
        facets === Bolt -> NoirBolt
        facets === Calendar -> NoirCalendar
        else -> NoirStudio
    }

    val Target: List<IconFacet> get() = Focus
    val Spark: List<IconFacet> get() = Assistant

    internal fun spectralAccentFor(facets: List<IconFacet>, fallback: Color): Color = when {
        facets === Type -> dev.mrsad.app.ui.theme.ObsidianSpectrum.BrightBlue
        facets === Read -> dev.mrsad.app.ui.theme.ObsidianSpectrum.Lavender
        facets === Eye || facets === Focus -> dev.mrsad.app.ui.theme.ObsidianSpectrum.Aqua
        facets === Mind || facets === Memory || facets === Flame || facets === Bolt -> dev.mrsad.app.ui.theme.ObsidianSpectrum.Pink
        facets === Analytics || facets === Calendar -> dev.mrsad.app.ui.theme.ObsidianSpectrum.BrightBlue
        facets === Assistant || facets === Studio || facets === Trophy -> dev.mrsad.app.ui.theme.ObsidianSpectrum.Lavender
        else -> fallback
    }

    private val NoirHome = listOf(
        linearPoly(true, 3.8f, 3.8f, 10.8f, 3.8f, 10.8f, 10.8f, 3.8f, 10.8f),
        linearPoly(true, 13.2f, 3.8f, 20.2f, 3.8f, 20.2f, 10.8f, 13.2f, 10.8f),
        linearPoly(true, 3.8f, 13.2f, 10.8f, 13.2f, 10.8f, 20.2f, 3.8f, 20.2f),
        linearPoly(true, 13.2f, 13.2f, 20.2f, 13.2f, 20.2f, 20.2f, 13.2f, 20.2f),
    )

    private val NoirType = listOf(
        linearPoly(true, 3f, 6.5f, 21f, 6.5f, 21f, 17.5f, 3f, 17.5f),
        linearPoly(false, 6.5f, 10f, 8.5f, 10f),
        linearPoly(false, 11f, 10f, 13f, 10f),
        linearPoly(false, 15.5f, 10f, 17.5f, 10f),
        linearPoly(false, 7.5f, 14.2f, 16.5f, 14.2f),
    )

    private val NoirRead = listOf(
        linearPoly(true, 4f, 5.5f, 12f, 5.5f, 12f, 18.5f, 4f, 18.5f),
        linearPoly(true, 12f, 5.5f, 20f, 5.5f, 20f, 18.5f, 12f, 18.5f),
        linearPoly(false, 6.5f, 9.5f, 9.5f, 9.5f),
        linearPoly(false, 14.5f, 9.5f, 17.5f, 9.5f),
        linearPoly(false, 6.5f, 13f, 9.5f, 13f),
        linearPoly(false, 14.5f, 13f, 17.5f, 13f),
    )

    private val NoirEye = listOf(
        linearPoly(true, 2.5f, 12f, 8f, 6.5f, 16f, 6.5f, 21.5f, 12f, 16f, 17.5f, 8f, 17.5f),
        linearCircle(12f, 12f, 2.8f),
    )

    private val NoirMind = listOf(
        linearPoly(true, 12f, 3.5f, 20.5f, 12f, 12f, 20.5f, 3.5f, 12f),
        linearCircle(12f, 12f, 2.6f),
    )

    private val NoirAnalytics = listOf(
        linearPoly(false, 4f, 19.5f, 20f, 19.5f),
        linearPoly(false, 4.5f, 15.5f, 9.5f, 10.5f, 13.5f, 13.5f, 19.5f, 6f),
        linearPoly(true, 18f, 4.5f, 21f, 4.5f, 21f, 7.5f, 18f, 7.5f),
    )

    private val NoirAssistant = listOf(
        linearPoly(true, 12f, 3.5f, 14.5f, 9.5f, 20.5f, 12f, 14.5f, 14.5f, 12f, 20.5f, 9.5f, 14.5f, 3.5f, 12f, 9.5f, 9.5f),
    )

    private val NoirSettings = listOf(
        linearPoly(false, 4f, 7f, 20f, 7f),
        linearPoly(false, 4f, 12f, 20f, 12f),
        linearPoly(false, 4f, 17f, 20f, 17f),
        linearCircle(9f, 7f, 2.0f),
        linearCircle(15f, 12f, 2.0f),
        linearCircle(11f, 17f, 2.0f),
    )

    private val NoirFocus = listOf(
        linearCircle(12f, 12f, 8f),
        linearCircle(12f, 12f, 2.5f),
        linearPoly(false, 12f, 2f, 12f, 4f),
        linearPoly(false, 12f, 20f, 12f, 22f),
        linearPoly(false, 2f, 12f, 4f, 12f),
        linearPoly(false, 20f, 12f, 22f, 12f),
    )

    private val NoirTrophy = listOf(
        linearPoly(true, 6.5f, 4.5f, 17.5f, 4.5f, 16f, 12f, 8f, 12f),
        linearPoly(false, 12f, 12f, 12f, 17.5f),
        linearPoly(false, 8f, 17.5f, 16f, 17.5f),
    )

    private val NoirFlame = listOf(
        linearPoly(true, 12f, 3f, 17.5f, 13f, 12f, 20.5f, 6.5f, 13f),
        linearPoly(false, 12f, 9f, 12f, 16f),
    )

    private val NoirStudio = listOf(
        linearPoly(true, 4f, 4.5f, 11f, 4.5f, 11f, 19.5f, 4f, 19.5f),
        linearPoly(true, 13f, 4.5f, 20f, 4.5f, 20f, 19.5f, 13f, 19.5f),
    )

    private val NoirMemory = listOf(
        linearPoly(true, 4.5f, 4.5f, 10.8f, 4.5f, 10.8f, 10.8f, 4.5f, 10.8f),
        linearPoly(true, 13.2f, 4.5f, 19.5f, 4.5f, 19.5f, 10.8f, 13.2f, 10.8f),
        linearPoly(true, 4.5f, 13.2f, 10.8f, 13.2f, 10.8f, 19.5f, 4.5f, 19.5f),
        linearPoly(true, 13.2f, 13.2f, 19.5f, 13.2f, 19.5f, 19.5f, 13.2f, 19.5f),
    )

    private val NoirBolt = listOf(
        linearPoly(true, 13.5f, 3f, 6f, 13f, 12f, 13f, 10.5f, 21f, 18f, 11f, 12f, 11f),
    )

    private val NoirCalendar = listOf(
        linearPoly(true, 4f, 5f, 20f, 5f, 20f, 20f, 4f, 20f),
        linearPoly(false, 4f, 9.5f, 20f, 9.5f),
        linearPoly(false, 9.3f, 9.5f, 9.3f, 20f),
        linearPoly(false, 14.6f, 9.5f, 14.6f, 20f),
    )
}

@Composable
fun MetalIcon(
    facets: List<IconFacet>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    dark: Boolean = false,
    accentOverride: Color? = null,
) {
    val colors = MrsadTheme.colors
    val label = contentDescription
    val resolvedAccent = accentOverride ?: MrsadIcons.spectralAccentFor(facets, colors.accent)
    Canvas(
        modifier = modifier
            .then(
                if (label != null) {
                    Modifier.semantics { this.contentDescription = label }
                } else {
                    Modifier
                },
            ),
    ) {
        val unit = size.minDimension / 24f
        fun toneColor(tone: MetalTone): Color = if (dark) {
            when (tone) {
                MetalTone.LIGHT -> Color(0xFF090810)
                MetalTone.MID -> Color(0xFF282236)
                MetalTone.DARK -> Color(0xFF564D69)
                MetalTone.ACCENT -> resolvedAccent
            }
        } else {
            when (tone) {
                MetalTone.LIGHT -> colors.metalLight
                MetalTone.MID -> colors.textSecondary
                MetalTone.DARK -> colors.metalDark
                MetalTone.ACCENT -> resolvedAccent
            }
        }
        scale(unit, unit) {
            facets.forEach { facet ->
                val color = toneColor(facet.tone)
                if (facet.stroke) {
                    drawPath(
                        path = facet.path,
                        color = color,
                        style = Stroke(
                            width = facet.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                } else {
                    drawPath(facet.path, color)
                }
            }
        }
    }
}
