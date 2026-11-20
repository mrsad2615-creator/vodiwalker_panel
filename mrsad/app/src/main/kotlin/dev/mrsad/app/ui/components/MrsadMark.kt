package dev.mrsad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.core.brand.MrsadMark

/**
 * Draws the MRSAD brand mark.
 *
 * The geometry comes from the domain core (`dev.mrsad.core.brand.MrsadMark`),
 * which is the same construction the launcher icon and the splash mark are
 * generated from, so the app and the icon are one mark rather than three
 * similar drawings.
 *
 * The vector drawables are not reused directly because the launcher artwork
 * is tuned for the 108dp adaptive-icon canvas, and the in-app mark takes its
 * tones from the active theme so it stays legible on the light surfaces too.
 *
 * @param size the height of the mark; the width follows the mark's own
 *   aspect ratio so it is never distorted.
 * @param monochrome draws a flat single-colour silhouette, the way the
 *   launcher renders its themed-icon layer.
 * @param contentDescription text for screen readers. The mark is decorative
 *   in most placements, so pass null there; where it carries meaning (the
 *   app wordmark) give it a real description.
 */
@Composable
fun MrsadMarkView(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    monochrome: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = MrsadTheme.colors

    // The metal ramp is sorted by luminance so the mark keeps the same
    // "outer plates catch the light, stems sit in shadow" hierarchy on both
    // the obsidian and the light palettes.
    val ramp = listOf(colors.metalLight, colors.metalMid, colors.metalDark)
        .sortedByDescending { it.luminance() }

    val plates = MrsadMark.plates()
    val silhouette = MrsadMark.silhouette()
    // Captured under a different name: inside `semantics` the unqualified
    // name `contentDescription` would resolve to the semantics property.
    val label = contentDescription

    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (label != null) {
                    Modifier.semantics { this.contentDescription = label }
                } else {
                    Modifier
                }
            )
    ) {
        val width = this.size.width
        val height = this.size.height
        // The mark's own box is 703 x 653; fit it into the canvas and centre
        // it, so the caller only has to choose a size.
        val scale = minOf(width / MrsadMark.WIDTH, height / MrsadMark.HEIGHT)
        val offsetX = (width - MrsadMark.WIDTH * scale) / 2f
        val offsetY = (height - MrsadMark.HEIGHT * scale) / 2f

        fun pathOf(points: List<MrsadMark.Point>) = Path().apply {
            points.forEachIndexed { index, point ->
                val x = point.x * scale
                val y = point.y * scale
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        translate(offsetX, offsetY) {
            if (monochrome) {
                drawPath(pathOf(silhouette), color = ramp[0])
                return@translate
            }

            // Tone groups, matching the generator: outer bands brightest,
            // inner bands a step darker, stems darkest.
            val tones = listOf(0, 1, 1, 0, 2, 2)
            plates.forEachIndexed { index, plate ->
                val tone = tones[index]
                val (start, end) = MrsadMark.gradientAxis(plate)
                val startColor = when (tone) {
                    0 -> ramp[0]
                    1 -> mix(ramp[0], ramp[1], 0.55f)
                    else -> ramp[1]
                }
                val endColor = when (tone) {
                    0 -> mix(ramp[0], ramp[1], 0.85f)
                    1 -> ramp[1]
                    else -> ramp[2]
                }
                drawPath(
                    path = pathOf(plate),
                    brush = Brush.linearGradient(
                        startColor,
                        endColor,
                        start = Offset(start.x * scale, start.y * scale),
                        end = Offset(end.x * scale, end.y * scale),
                    ),
                )
            }
        }
    }
}

/**
 * The mark with the app wordmark beside it, for the places that introduce
 * the product (Home header, Settings about block).
 */
@Composable
fun MrsadWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 34.dp,
    title: String = "MRSAD",
    subtitle: String? = null,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
    ) {
        MrsadMarkView(size = markSize)
        Column {
            Text(text = title, style = type.title, color = colors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = type.caption, color = colors.textMuted)
            }
        }
    }
}

/** Blends two colours; `amount` 0 returns [a], 1 returns [b]. */
private fun mix(a: Color, b: Color, amount: Float): Color = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount,
    alpha = a.alpha + (b.alpha - a.alpha) * amount,
)
