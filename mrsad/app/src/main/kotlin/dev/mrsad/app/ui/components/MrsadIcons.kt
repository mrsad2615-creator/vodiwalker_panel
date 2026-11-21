package dev.mrsad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.mrsad.app.ui.theme.MrsadTheme

/**
 * The MRSAD custom icon family: hybrid 3D geometry.
 *
 * Every module icon is built the same way — a small set of flat facets on
 * a 24dp grid, shaded with the metal ramp (light / mid / dark) so the
 * glyph reads as a machined object rather than a line drawing. The
 * facets are the identity; the Material glyphs they replace were not.
 *
 * The paths are built once and shared: an icon is a list of facets, and
 * [MetalIcon] only paints them. Nothing here allocates per frame.
 */

/** Which step of the metal ramp a facet is shaded with. */
enum class MetalTone { LIGHT, MID, DARK, ACCENT }

/** One facet of an icon: a path, its shading, and whether it is stroked. */
data class IconFacet(
    val path: Path,
    val tone: MetalTone,
    val stroke: Boolean = false,
    val strokeWidth: Float = 1.4f,
)

/** Builds a polygonal facet from x,y pairs on the 24dp grid. */
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

/** Builds a stroked facet (an orbit, a trend line) from x,y pairs. */
private fun strokeFacet(tone: MetalTone, width: Float, vararg xy: Float): IconFacet {
    val path = Path()
    var i = 0
    while (i < xy.size) {
        if (i == 0) path.moveTo(xy[i], xy[i + 1]) else path.lineTo(xy[i], xy[i + 1])
        i += 2
    }
    return IconFacet(path, tone, stroke = true, strokeWidth = width)
}

/** A filled circle facet. */
private fun disc(cx: Float, cy: Float, r: Float, tone: MetalTone): IconFacet {
    val path = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    return IconFacet(path, tone)
}

/** A ring facet: a disc with a disc taken out of it. */
private fun ring(cx: Float, cy: Float, rOuter: Float, rInner: Float, tone: MetalTone): IconFacet {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter))
        addOval(Rect(cx - rInner, cy - rInner, cx + rInner, cy + rInner))
    }
    return IconFacet(path, tone)
}

/** An elliptical orbit facet, stroked. */
private fun orbit(cx: Float, cy: Float, rx: Float, ry: Float, tone: MetalTone): IconFacet {
    val path = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
    return IconFacet(path, tone, stroke = true, strokeWidth = 1.1f)
}

/**
 * The module icon family. Each icon is a list of facets on the 24dp grid,
 * shaded light / mid / dark so it reads as a machined object.
 */
object MrsadIcons {

    /** Home: a chamfered house, roof lit from above. */
    val Home: List<IconFacet> = listOf(
        facet(MetalTone.MID, 12f, 3.5f, 4.5f, 10.5f, 7f, 10.5f, 12f, 6f),
        facet(MetalTone.LIGHT, 12f, 3.5f, 19.5f, 10.5f, 17f, 10.5f, 12f, 6f),
        facet(MetalTone.DARK, 7f, 10.5f, 12f, 10.5f, 12f, 20f, 7f, 20f),
        facet(MetalTone.MID, 12f, 10.5f, 17f, 10.5f, 17f, 20f, 12f, 20f),
        facet(MetalTone.LIGHT, 10.2f, 20f, 10.2f, 15.5f, 13.8f, 15.5f, 13.8f, 20f),
    )

    /** Type: a chamfered keyboard slab with a key grid and a spacebar. */
    val Type: List<IconFacet> = listOf(
        facet(
            MetalTone.MID,
            4f, 6.5f, 20f, 6.5f, 21.5f, 8f, 21.5f, 16f, 20f, 17.5f,
            4f, 17.5f, 2.5f, 16f, 2.5f, 8f,
        ),
        facet(MetalTone.LIGHT, 5.5f, 9f, 7.5f, 9f, 7.5f, 10.5f, 5.5f, 10.5f),
        facet(MetalTone.DARK, 9.5f, 9f, 11.5f, 9f, 11.5f, 10.5f, 9.5f, 10.5f),
        facet(MetalTone.LIGHT, 13.5f, 9f, 15.5f, 9f, 15.5f, 10.5f, 13.5f, 10.5f),
        facet(MetalTone.DARK, 17.5f, 9f, 19.5f, 9f, 19.5f, 10.5f, 17.5f, 10.5f),
        facet(MetalTone.LIGHT, 5.5f, 12f, 7.5f, 12f, 7.5f, 13.5f, 5.5f, 13.5f),
        facet(MetalTone.DARK, 9.5f, 12f, 11.5f, 12f, 11.5f, 13.5f, 9.5f, 13.5f),
        facet(MetalTone.LIGHT, 13.5f, 12f, 15.5f, 12f, 15.5f, 13.5f, 13.5f, 13.5f),
        facet(MetalTone.LIGHT, 8f, 14.8f, 16f, 14.8f, 16f, 16.2f, 8f, 16.2f),
    )

    /** Read: an open book, left page in shadow, right page in light. */
    val Read: List<IconFacet> = listOf(
        facet(MetalTone.MID, 12f, 5f, 5f, 6.5f, 5f, 18f, 12f, 16.5f),
        facet(MetalTone.LIGHT, 12f, 5f, 19f, 6.5f, 19f, 18f, 12f, 16.5f),
        facet(MetalTone.DARK, 11.6f, 5f, 12.4f, 5f, 12.4f, 16.5f, 11.6f, 16.5f),
        facet(MetalTone.LIGHT, 6.8f, 9f, 10.6f, 9.6f, 10.6f, 10.8f, 6.8f, 10.2f),
        facet(MetalTone.DARK, 13.4f, 9.6f, 17.2f, 9f, 17.2f, 10.2f, 13.4f, 10.8f),
        facet(MetalTone.LIGHT, 6.8f, 12f, 10.6f, 12.6f, 10.6f, 13.8f, 6.8f, 13.2f),
        facet(MetalTone.DARK, 13.4f, 12.6f, 17.2f, 12f, 17.2f, 13.2f, 13.4f, 13.8f),
    )

    /** Mind: a faceted diamond with an orbit and a satellite. */
    val Mind: List<IconFacet> = listOf(
        orbit(12f, 12f, 9.5f, 4.2f, MetalTone.MID),
        facet(MetalTone.LIGHT, 12f, 4f, 17f, 12f, 12f, 12f),
        facet(MetalTone.MID, 17f, 12f, 12f, 20f, 12f, 12f),
        facet(MetalTone.DARK, 12f, 20f, 7f, 12f, 12f, 12f),
        facet(MetalTone.MID, 7f, 12f, 12f, 4f, 12f, 12f),
        disc(18.6f, 8.6f, 1.5f, MetalTone.LIGHT),
    )

    /** Analytics: three metallic pillars under an accent trend line. */
    val Analytics: List<IconFacet> = listOf(
        facet(MetalTone.DARK, 4.5f, 13f, 7.5f, 13f, 7.5f, 20f, 4.5f, 20f),
        facet(MetalTone.MID, 10.5f, 9f, 13.5f, 9f, 13.5f, 20f, 10.5f, 20f),
        facet(MetalTone.LIGHT, 16.5f, 5f, 19.5f, 5f, 19.5f, 20f, 16.5f, 20f),
        strokeFacet(MetalTone.ACCENT, 1.6f, 4f, 11f, 9f, 7f, 13f, 9f, 20f, 4f),
    )

    /** Assistant: a four-pointed star with a satellite. */
    val Assistant: List<IconFacet> = listOf(
        facet(
            MetalTone.LIGHT,
            12f, 3f, 13.8f, 10.2f, 21f, 12f, 13.8f, 13.8f,
            12f, 21f, 10.2f, 13.8f, 3f, 12f, 10.2f, 10.2f,
        ),
        facet(MetalTone.MID, 12f, 3f, 13.8f, 10.2f, 12f, 12f, 10.2f, 10.2f),
        facet(MetalTone.MID, 18f, 16.5f, 19.2f, 18f, 18f, 19.5f, 16.8f, 18f),
    )

    /** Settings: a machined ring with four teeth and a lit hub. */
    val Settings: List<IconFacet> = listOf(
        IconFacet(
            Path().apply {
                fillType = PathFillType.EvenOdd
                moveTo(7f, 4f); lineTo(17f, 4f); lineTo(20f, 7f); lineTo(20f, 17f)
                lineTo(17f, 20f); lineTo(7f, 20f); lineTo(4f, 17f); lineTo(4f, 7f); close()
                moveTo(9f, 9f); lineTo(15f, 9f); lineTo(15f, 15f); lineTo(9f, 15f); close()
            },
            MetalTone.MID,
        ),
        facet(MetalTone.LIGHT, 11f, 1.2f, 13f, 1.2f, 13f, 4f, 11f, 4f),
        facet(MetalTone.LIGHT, 11f, 20f, 13f, 20f, 13f, 22.8f, 11f, 22.8f),
        facet(MetalTone.LIGHT, 1.2f, 11f, 4f, 11f, 4f, 13f, 1.2f, 13f),
        facet(MetalTone.LIGHT, 20f, 11f, 22.8f, 11f, 22.8f, 13f, 20f, 13f),
        disc(12f, 12f, 1.8f, MetalTone.LIGHT),
    )

    /** Focus: concentric rings around an energy core. */
    val Focus: List<IconFacet> = listOf(
        ring(12f, 12f, 8.5f, 6.3f, MetalTone.MID),
        disc(12f, 12f, 4.4f, MetalTone.LIGHT),
        disc(12f, 12f, 1.8f, MetalTone.ACCENT),
    )

    /** Trophy: a machined cup on a stem. */
    val Trophy: List<IconFacet> = listOf(
        facet(MetalTone.MID, 7f, 4f, 12f, 4f, 12f, 11f, 7f, 11f),
        facet(MetalTone.LIGHT, 12f, 4f, 17f, 4f, 17f, 11f, 12f, 11f),
        facet(MetalTone.DARK, 10.8f, 11f, 13.2f, 11f, 13.2f, 15f, 10.8f, 15f),
        facet(MetalTone.MID, 8.5f, 15f, 15.5f, 15f, 15.5f, 17.5f, 8.5f, 17.5f),
        facet(MetalTone.LIGHT, 7f, 4f, 4.5f, 5f, 4.5f, 9f, 7f, 10f),
        facet(MetalTone.MID, 17f, 4f, 19.5f, 5f, 19.5f, 9f, 17f, 10f),
    )

    /** Flame: a teardrop of energy with a lit core. */
    val Flame: List<IconFacet> = listOf(
        IconFacet(
            Path().apply {
                moveTo(12f, 3f)
                cubicTo(14.5f, 7f, 18f, 9.5f, 18f, 14f)
                cubicTo(18f, 17.5f, 15.5f, 20.5f, 12f, 20.5f)
                cubicTo(8.5f, 20.5f, 6f, 17.5f, 6f, 14f)
                cubicTo(6f, 10.5f, 9f, 8f, 10.5f, 5.5f)
                cubicTo(11.2f, 4.5f, 11.6f, 3.6f, 12f, 3f)
                close()
            },
            MetalTone.MID,
        ),
        IconFacet(
            Path().apply {
                moveTo(12f, 9f)
                cubicTo(13.5f, 11.5f, 15f, 12.5f, 15f, 15f)
                cubicTo(15f, 17.5f, 13.5f, 19f, 12f, 19f)
                cubicTo(10.5f, 19f, 9f, 17.5f, 9f, 15f)
                cubicTo(9f, 13f, 10.5f, 11.5f, 12f, 9f)
                close()
            },
            MetalTone.ACCENT,
        ),
    )

    /** Studio: a faceted gem. */
    val Studio: List<IconFacet> = listOf(
        facet(MetalTone.LIGHT, 6f, 5f, 18f, 5f, 15f, 11f, 9f, 11f),
        facet(MetalTone.MID, 6f, 5f, 9f, 11f, 12f, 20f),
        facet(MetalTone.DARK, 18f, 5f, 15f, 11f, 12f, 20f),
        facet(MetalTone.MID, 9f, 11f, 15f, 11f, 12f, 20f),
    )
}

/**
 * Paints a [MrsadIcons] facet list with the metal ramp of the active theme.
 *
 * @param dark paints the dark ramp instead, for icons that sit on a light
 *   pill (the selected navigation destination).
 */
@Composable
fun MetalIcon(
    facets: List<IconFacet>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    dark: Boolean = false,
) {
    val colors = MrsadTheme.colors
    val label = contentDescription
    // The caller owns the size (like `Icon`); the facets live on a 24dp
    // grid and are scaled to whatever box they are given.
    Canvas(
        modifier = modifier
            .then(
                if (label != null) {
                    Modifier.semantics { this.contentDescription = label }
                } else {
                    Modifier
                }
            )
    ) {
        val unit = size.minDimension / 24f
        fun toneColor(tone: MetalTone): Color = if (dark) {
            when (tone) {
                MetalTone.LIGHT -> Color(0xFF08090C)
                MetalTone.MID -> Color(0xFF3A3E47)
                MetalTone.DARK -> Color(0xFF777F8A)
                MetalTone.ACCENT -> colors.accent
            }
        } else {
            when (tone) {
                MetalTone.LIGHT -> colors.metalLight
                MetalTone.MID -> colors.metalMid
                MetalTone.DARK -> colors.metalDark
                MetalTone.ACCENT -> colors.accent
            }
        }
        scale(unit, unit) {
            facets.forEach { facet ->
                val color = toneColor(facet.tone)
                if (facet.stroke) {
                    drawPath(facet.path, color, style = Stroke(width = facet.strokeWidth))
                } else {
                    drawPath(facet.path, color)
                }
            }
        }
    }
}
