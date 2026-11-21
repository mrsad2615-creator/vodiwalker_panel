package dev.mrsad.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flare
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mrsad.app.ui.theme.MrsadTheme

/**
 * Which step of the metal ramp a facet is painted with.
 */
enum class MetalTone { LIGHT, MID, DARK, ACCENT }

/**
 * Icon descriptor carrying both high-precision professional vector glyphs for
 * OBSIDIAN (3D Metallic Rounded Vector) and NOIR (Precision Architectural Outlined Vector).
 */
class IconFacet(
    val path: Path = Path(),
    val tone: MetalTone = MetalTone.LIGHT,
    val stroke: Boolean = false,
    val strokeWidth: Float = 0f,
    val vectorObsidian: ImageVector = Icons.Rounded.SpaceDashboard,
    val vectorNoir: ImageVector = Icons.Outlined.SpaceDashboard,
)

/**
 * Dual-System Professional Vector Iconography (Master Prompt §7):
 *
 * - **THEME 01 — OBSIDIAN**: High-detail rounded vector glyphs rendered with
 *   a 3-stop brushed-titanium & ice-silver shader gradient (`BlendMode.SrcAtop`)
 *   and subtle 3D depth.
 * - **THEME 02 — NOIR**: Precision architectural linear outlined vector glyphs
 *   rendered in pure monochrome (`#FFFFFF` / `#000000`).
 */
object MrsadIcons {

    private fun icon(
        obsidian: ImageVector,
        noir: ImageVector,
    ): List<IconFacet> = listOf(
        IconFacet(
            vectorObsidian = obsidian,
            vectorNoir = noir,
        ),
    )

    val Home: List<IconFacet> = icon(Icons.Rounded.SpaceDashboard, Icons.Outlined.SpaceDashboard)
    val Type: List<IconFacet> = icon(Icons.Rounded.Keyboard, Icons.Outlined.Keyboard)
    val Read: List<IconFacet> = icon(Icons.Rounded.AutoStories, Icons.Outlined.AutoStories)
    val Eye: List<IconFacet> = icon(Icons.Rounded.Visibility, Icons.Outlined.Visibility)
    val Mind: List<IconFacet> = icon(Icons.Rounded.Psychology, Icons.Outlined.Psychology)
    val Analytics: List<IconFacet> = icon(Icons.Rounded.Insights, Icons.Outlined.Insights)
    val Assistant: List<IconFacet> = icon(Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome)
    val Settings: List<IconFacet> = icon(Icons.Rounded.Tune, Icons.Outlined.Tune)
    val Focus: List<IconFacet> = icon(Icons.Rounded.CenterFocusStrong, Icons.Outlined.CenterFocusStrong)
    val Trophy: List<IconFacet> = icon(Icons.Rounded.EmojiEvents, Icons.Outlined.EmojiEvents)
    val Flame: List<IconFacet> = icon(Icons.Rounded.LocalFireDepartment, Icons.Outlined.LocalFireDepartment)
    val Studio: List<IconFacet> = icon(Icons.Rounded.Palette, Icons.Outlined.Palette)
    val Memory: List<IconFacet> = icon(Icons.Rounded.Memory, Icons.Outlined.Memory)
    val Bolt: List<IconFacet> = icon(Icons.Rounded.Bolt, Icons.Outlined.Bolt)
    val Calendar: List<IconFacet> = icon(Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth)
    val Target: List<IconFacet> = icon(Icons.Rounded.TrackChanges, Icons.Outlined.TrackChanges)
    val Spark: List<IconFacet> = icon(Icons.Rounded.Flare, Icons.Outlined.Flare)
}

/**
 * Renders a professional icon in the active theme's visual language:
 * - In **OBSIDIAN**, applies a brushed-titanium & specular-silver vertical
 *   gradient shader (`BlendMode.SrcAtop`) over the high-detail vector glyph.
 * - In **NOIR**, renders the precision outlined vector glyph in pure monochrome.
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
    val primary = facets.firstOrNull()
    val vector = if (isNoir) {
        primary?.vectorNoir ?: Icons.Outlined.SpaceDashboard
    } else {
        primary?.vectorObsidian ?: Icons.Rounded.SpaceDashboard
    }

    val semanticsModifier = if (label != null) {
        Modifier.semantics { this.contentDescription = label }
    } else {
        Modifier
    }

    if (isNoir) {
        val noirTint = if (dark) colors.background else colors.textPrimary
        Box(
            modifier = modifier
                .size(22.dp)
                .then(semanticsModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = noirTint,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        if (dark) {
            Box(
                modifier = modifier
                    .size(22.dp)
                    .then(semanticsModifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = Color(0xFF07080B),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            val topColor = Color(0xFFF6F9FF)
            val midColor = colors.metalLight
            val bottomColor = colors.metalDark
            Box(
                modifier = modifier
                    .size(22.dp)
                    .then(semanticsModifier)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        val brush = Brush.verticalGradient(
                            colors = listOf(topColor, midColor, bottomColor),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
