package dev.mrsad.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * OBSIDIAN SPECTRUM — Unified Atmospheric Foundation (`Backgrounds.kt`).
 *
 * Every screen shares a unified, noise-free deep Obsidian foundation (`#090810` → `#100E18`)
 * with a subtle top spectral tint corresponding to the active module and automatic
 * `WindowInsets.statusBars` padding so headers never overlap the Android status bar.
 */
enum class PageEnvironment {
    HOME,
    TYPE,
    READ,
    EYE,
    MIND,
    FOCUS,
    ANALYTICS,
    AI,
    ARENA,
    STUDIO,
    SETTINGS,
    ;

    fun spectralAccent(): Color = when (this) {
        HOME -> ObsidianSpectrum.ElectricBlue
        TYPE -> ObsidianSpectrum.ElectricBlue
        READ -> ObsidianSpectrum.DeepViolet
        EYE -> ObsidianSpectrum.Teal
        MIND -> ObsidianSpectrum.Magenta
        FOCUS -> ObsidianSpectrum.Teal
        ANALYTICS -> ObsidianSpectrum.ElectricBlue
        AI -> ObsidianSpectrum.DeepViolet
        ARENA -> ObsidianSpectrum.Magenta
        STUDIO -> ObsidianSpectrum.DeepViolet
        SETTINGS -> ObsidianSpectrum.ElectricBlue
    }
}

@Composable
fun AmbientBackground(
    environment: PageEnvironment,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MrsadTheme.colors
    val spectral = environment.spectralAccent()
    val topColor = remember(colors.backgroundElevated, spectral) {
        lerp(colors.backgroundElevated, spectral, 0.12f)
    }
    val backgroundBrush = remember(topColor, colors.background) {
        Brush.verticalGradient(listOf(topColor, colors.background))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        content()
    }
}
