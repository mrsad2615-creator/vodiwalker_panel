package dev.hgallery.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Theme entry point.
 *
 * Material's own colours are mapped onto the measured palette so that a stray
 * Material component cannot introduce an off-brand blue.
 */
@Composable
fun HgTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) HgDarkPalette else HgLightPalette

    val scheme = remember(palette) {
        if (palette.isLight) {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = Color.White,
                background = palette.page,
                onBackground = palette.textPrimary,
                surface = palette.page,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.chipBackground,
                onSurfaceVariant = palette.textSecondary,
                outline = palette.divider,
                error = palette.danger,
            )
        } else {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = Color.White,
                background = palette.page,
                onBackground = palette.textPrimary,
                surface = palette.page,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.chipBackground,
                onSurfaceVariant = palette.textSecondary,
                outline = palette.divider,
                error = palette.danger,
            )
        }
    }

    CompositionLocalProvider(LocalHgPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HgTypography,
            content = content,
        )
    }
}
