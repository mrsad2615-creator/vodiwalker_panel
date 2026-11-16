package com.mesad.time.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// MESAD Brand Colors
val Obsidian = Color(0xFF06070B)
val Graphite = Color(0xFF12161F)
val Titanium = Color(0xFFC8D0DC)
val TitaniumHi = Color(0xFFE8EDF5)
val Midnight = Color(0xFF0B1A2E)
val ElectricCyan = Color(0xFF00E5FF)
val Champagne = Color(0xFFE6C79C)
val Amber = Color(0xFFFFB347)
val Emerald = Color(0xFF2EE6A8)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Obsidian,
    primaryContainer = Color(0xFF003A44),
    onPrimaryContainer = Color(0xFF00E5FF),
    secondary = Champagne,
    onSecondary = Obsidian,
    secondaryContainer = Color(0xFF3D2E1A),
    onSecondaryContainer = Champagne,
    tertiary = Titanium,
    onTertiary = Obsidian,
    background = Obsidian,
    onBackground = TitaniumHi,
    surface = Graphite,
    onSurface = TitaniumHi,
    surfaceVariant = Color(0xFF1A1F2A),
    onSurfaceVariant = Titanium,
    surfaceContainerHighest = Color(0xFF232936),
    outline = Color(0xFF2A3040),
    outlineVariant = Color(0xFF1E2432),
    error = Color(0xFFFF6B6B),
    onError = Obsidian
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4FA),
    onPrimaryContainer = Color(0xFF003A44),
    secondary = Color(0xFF8A6D3B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E6C8),
    onSecondaryContainer = Color(0xFF3D2E1A),
    tertiary = Color(0xFF4A5568),
    onTertiary = Color.White,
    background = Color(0xFFF2F4F7),
    onBackground = Obsidian,
    surface = Color.White,
    onSurface = Obsidian,
    surfaceVariant = Color(0xFFE8ECF1),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    outline = Color(0xFFC8D0DC),
    outlineVariant = Color(0xFFE0E6EE),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun MesadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MesadTypography,
        content = content
    )
}
