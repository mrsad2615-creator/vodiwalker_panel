package dev.mrsad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
 * Dual-System Icon Architecture (Master Prompt §7):
 *
 * - **OBSIDIAN (`isNoir == false`)**: Machined 3D metallic & smoked-glass
 *   faceted icons shaded with the 3-step metal ramp (`LIGHT`, `MID`, `DARK`, `ACCENT`).
 * - **NOIR (`isNoir == true`)**: Pure geometric linear icons with uniform `1.5f`
 *   stroke weight on the 24dp grid, zero filled facets, and zero decorative shading.
 *
 * The two icon systems are strictly isolated and never mixed within a theme.
 */

enum class MetalTone { LIGHT, MID, DARK, ACCENT }

data class IconFacet(
    val path: Path,
    val tone: MetalTone,
    val stroke: Boolean = false,
    val strokeWidth: Float = 1.4f,
)

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

private fun disc(cx: Float, cy: Float, r: Float, tone: MetalTone): IconFacet {
    val path = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    return IconFacet(path, tone)
}

private fun ring(cx: Float, cy: Float, rOuter: Float, rInner: Float, tone: MetalTone): IconFacet {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter))
        addOval(Rect(cx - rInner, cy - rInner, cx + rInner, cy + rInner))
    }
    return IconFacet(path, tone)
}

private fun orbit(cx: Float, cy: Float, rx: Float, ry: Float, tone: MetalTone): IconFacet {
    val path = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
    return IconFacet(path, tone, stroke = true, strokeWidth = 1.1f)
}

/** Builds a closed linear polygon path for NOIR. */
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

    /** Home: chamfered architectural shelter. */
    val Home: List<IconFacet> = listOf(
        facet(MetalTone.MID, 12f, 3.5f, 4.5f, 10.5f, 7f, 10.5f, 12f, 6f),
        facet(MetalTone.LIGHT, 12f, 3.5f, 19.5f, 10.5f, 17f, 10.5f, 12f, 6f),
        facet(MetalTone.DARK, 7f, 10.5f, 12f, 10.5f, 12f, 20f, 7f, 20f),
        facet(MetalTone.MID, 12f, 10.5f, 17f, 10.5f, 17f, 20f, 12f, 20f),
        facet(MetalTone.LIGHT, 10.2f, 20f, 10.2f, 15.5f, 13.8f, 15.5f, 13.8f, 20f),
    )

    /** Type: chamfered keyboard slab with metallic keycaps and spacebar. */
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

    /** Read: open prism book with word lines. */
    val Read: List<IconFacet> = listOf(
        facet(MetalTone.MID, 12f, 5f, 5f, 6.5f, 5f, 18f, 12f, 16.5f),
        facet(MetalTone.LIGHT, 12f, 5f, 19f, 6.5f, 19f, 18f, 12f, 16.5f),
        facet(MetalTone.DARK, 11.6f, 5f, 12.4f, 5f, 12.4f, 16.5f, 11.6f, 16.5f),
        facet(MetalTone.LIGHT, 6.8f, 9f, 10.6f, 9.6f, 10.6f, 10.8f, 6.8f, 10.2f),
        facet(MetalTone.DARK, 13.4f, 9.6f, 17.2f, 9f, 17.2f, 10.2f, 13.4f, 10.8f),
        facet(MetalTone.LIGHT, 6.8f, 12f, 10.6f, 12.6f, 10.6f, 13.8f, 6.8f, 13.2f),
        facet(MetalTone.DARK, 13.4f, 12.6f, 17.2f, 12f, 17.2f, 13.2f, 13.4f, 13.8f),
    )

    /** Eye: sculpted ocular aperture with metallic iris facets and focus core. */
    val Eye: List<IconFacet> = listOf(
        facet(MetalTone.MID, 2.5f, 12f, 7.5f, 6.8f, 12f, 6f, 12f, 12f),
        facet(MetalTone.LIGHT, 12f, 6f, 16.5f, 6.8f, 21.5f, 12f, 12f, 12f),
        facet(MetalTone.DARK, 2.5f, 12f, 7.5f, 17.2f, 12f, 18f, 12f, 12f),
        facet(MetalTone.MID, 12f, 18f, 16.5f, 17.2f, 21.5f, 12f, 12f, 12f),
        ring(12f, 12f, 4.6f, 2.4f, MetalTone.LIGHT),
        disc(12f, 12f, 1.6f, MetalTone.ACCENT),
    )

    /** Mind: faceted neural polyhedron with orbital ring. */
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

    /** Assistant: four-pointed star with a satellite. */
    val Assistant: List<IconFacet> = listOf(
        facet(
            MetalTone.LIGHT,
            12f, 3f, 13.8f, 10.2f, 21f, 12f, 13.8f, 13.8f,
            12f, 21f, 10.2f, 13.8f, 3f, 12f, 10.2f, 10.2f,
        ),
        facet(MetalTone.MID, 12f, 3f, 13.8f, 10.2f, 12f, 12f, 10.2f, 10.2f),
        facet(MetalTone.MID, 18f, 16.5f, 19.2f, 18f, 18f, 19.5f, 16.8f, 18f),
    )

    /** Settings: machined ring with four teeth and a lit hub. */
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

    /** Trophy: machined cup on a stem. */
    val Trophy: List<IconFacet> = listOf(
        facet(MetalTone.MID, 7f, 4f, 12f, 4f, 12f, 11f, 7f, 11f),
        facet(MetalTone.LIGHT, 12f, 4f, 17f, 4f, 17f, 11f, 12f, 11f),
        facet(MetalTone.DARK, 10.8f, 11f, 13.2f, 11f, 13.2f, 15f, 10.8f, 15f),
        facet(MetalTone.MID, 8.5f, 15f, 15.5f, 15f, 15.5f, 17.5f, 8.5f, 17.5f),
        facet(MetalTone.LIGHT, 7f, 4f, 4.5f, 5f, 4.5f, 9f, 7f, 10f),
        facet(MetalTone.MID, 17f, 4f, 19.5f, 5f, 19.5f, 9f, 17f, 10f),
    )

    /** Flame: faceted energy crystal / streak spark. */
    val Flame: List<IconFacet> = listOf(
        facet(MetalTone.MID, 12f, 2.5f, 6.5f, 13f, 12f, 21f),
        facet(MetalTone.LIGHT, 12f, 2.5f, 17.5f, 13f, 12f, 21f),
        facet(MetalTone.ACCENT, 12f, 8f, 15f, 14f, 12f, 19.5f, 9f, 14f),
    )

    /** Studio / Theme Manager: dualfaceted prism. */
    val Studio: List<IconFacet> = listOf(
        facet(MetalTone.LIGHT, 6f, 5f, 18f, 5f, 15f, 11f, 9f, 11f),
        facet(MetalTone.MID, 6f, 5f, 9f, 11f, 12f, 20f),
        facet(MetalTone.DARK, 18f, 5f, 15f, 11f, 12f, 20f),
        facet(MetalTone.MID, 9f, 11f, 15f, 11f, 12f, 20f),
    )

    /** Memory: 3D memory chip matrix. */
    val Memory: List<IconFacet> = listOf(
        facet(MetalTone.MID, 5f, 5f, 19f, 5f, 19f, 19f, 5f, 19f),
        facet(MetalTone.LIGHT, 8f, 8f, 12f, 8f, 12f, 12f, 8f, 12f),
        facet(MetalTone.DARK, 12f, 8f, 16f, 8f, 16f, 12f, 12f, 12f),
        facet(MetalTone.DARK, 8f, 12f, 12f, 12f, 12f, 16f, 8f, 16f),
        facet(MetalTone.ACCENT, 12f, 12f, 16f, 12f, 16f, 16f, 12f, 16f),
    )

    /** Bolt / Reaction Speed: faceted lightning prism. */
    val Bolt: List<IconFacet> = listOf(
        facet(MetalTone.LIGHT, 13.5f, 2.5f, 5.5f, 13f, 12f, 13f),
        facet(MetalTone.MID, 12f, 11f, 18.5f, 11f, 10.5f, 21.5f),
    )

    /** Calendar / Streak Grid: faceted plate with grid cells. */
    val Calendar: List<IconFacet> = listOf(
        facet(MetalTone.MID, 4f, 5f, 20f, 5f, 20f, 20f, 4f, 20f),
        facet(MetalTone.LIGHT, 4f, 5f, 20f, 5f, 20f, 9f, 4f, 9f),
        facet(MetalTone.LIGHT, 7f, 11.5f, 10f, 11.5f, 10f, 14.5f, 7f, 14.5f),
        facet(MetalTone.ACCENT, 14f, 11.5f, 17f, 11.5f, 17f, 14.5f, 14f, 14.5f),
        facet(MetalTone.DARK, 7f, 16f, 10f, 16f, 10f, 18.5f, 7f, 18.5f),
    )

    /**
     * Maps every [MrsadIcons] facet list to its corresponding pure 1.5dp
     * geometric linear icon for THEME 02 — NOIR.
     */
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

    private val NoirHome = listOf(
        linearPoly(true, 3.5f, 10.5f, 12f, 3.5f, 20.5f, 10.5f, 20.5f, 20f, 3.5f, 20f),
        linearPoly(false, 9.5f, 20f, 9.5f, 14f, 14.5f, 14f, 14.5f, 20f),
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
        linearPoly(true, 6f, 4.5f, 18f, 4.5f, 18f, 19.5f, 6f, 19.5f),
        linearPoly(false, 9f, 9f, 15f, 9f),
        linearPoly(false, 9f, 12f, 15f, 12f),
        linearPoly(false, 9f, 15f, 13f, 15f),
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

/**
 * Renders an icon according to the active theme's icon system:
 * - In **OBSIDIAN**, paints 3D metallic facets with the metal ramp.
 * - In **NOIR**, paints pure uniform `1.5f` geometric linear strokes.
 */
@Composable
fun MetalIcon(
    facets: List<IconFacet>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    dark: Boolean = false,
) {
    val colors = MrsadTheme.colors
    val isNoir = colors.isNoir
    val label = contentDescription
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
        if (isNoir) {
            val strokeColor = if (dark) colors.background else colors.textPrimary
            val paths = MrsadIcons.noirPathsFor(facets)
            val strokeStyle = Stroke(
                width = 1.55f,
                cap = StrokeCap.Square,
                join = StrokeJoin.Miter,
            )
            scale(unit, unit) {
                paths.forEach { path ->
                    drawPath(path = path, color = strokeColor, style = strokeStyle)
                }
            }
        } else {
            fun toneColor(tone: MetalTone): Color = if (dark) {
                when (tone) {
                    MetalTone.LIGHT -> Color(0xFF07080B)
                    MetalTone.MID -> Color(0xFF343A46)
                    MetalTone.DARK -> Color(0xFF6B7485)
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
}
