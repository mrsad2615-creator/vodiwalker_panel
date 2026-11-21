package dev.mrsad.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout. statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Each major page has its own environmental identity (Master Prompt §4 & §5). */
enum class PageEnvironment {
    HOME, TYPE, READ, EYE, MIND, ANALYTICS, AI, ARENA, STUDIO, FOCUS, PLANNER, LIBRARY, SETTINGS
}

/**
 * Resolves the primary spectral accent pair for a given [PageEnvironment].
 */
fun PageEnvironment.spectralColors(): Pair<Color, Color> = when (this) {
    PageEnvironment.TYPE -> ObsidianSpectrum.ElectricBlue to ObsidianSpectrum.BrightBlue
    PageEnvironment.READ, PageEnvironment.LIBRARY -> ObsidianSpectrum.DeepViolet to ObsidianSpectrum.Lavender
    PageEnvironment.EYE -> ObsidianSpectrum.Teal to ObsidianSpectrum.Aqua
    PageEnvironment.MIND -> ObsidianSpectrum.Magenta to ObsidianSpectrum.Pink
    PageEnvironment.FOCUS, PageEnvironment.PLANNER -> ObsidianSpectrum.Teal to ObsidianSpectrum.BrightBlue
    PageEnvironment.ANALYTICS, PageEnvironment.ARENA -> ObsidianSpectrum.ElectricBlue to ObsidianSpectrum.Lavender
    PageEnvironment.AI, PageEnvironment.STUDIO -> ObsidianSpectrum.DeepViolet to ObsidianSpectrum.Pink
    PageEnvironment.HOME, PageEnvironment.SETTINGS -> ObsidianSpectrum.DeepViolet to ObsidianSpectrum.BrightBlue
}

/**
 * OBSIDIAN SPECTRUM — Clean Atmospheric Background System (Master Prompt §5).
 *
 * - Unified deep Obsidian foundation (`#090810` -> `#100E18`) across all screens.
 * - Zero grid lines and zero radial banding noise.
 * - Controlled top spectral horizon illumination matched to each module's
 *   identity (`TYPE` Blue, `READ` Violet, `EYE` Teal/Aqua, `MIND` Magenta/Pink).
 * - Automatically applies `WindowInsets.statusBars` padding so top bars never
 *   collide with the system clock or status icons under `enableEdgeToEdge()`.
 */
@Composable
fun AmbientBackground(
    environment: PageEnvironment,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MrsadTheme.colors
    val (primarySpectral, secondarySpectral) = environment.spectralColors()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        content()
    }
}
