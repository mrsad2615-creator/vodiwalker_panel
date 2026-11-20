package com.mrsad.ambient.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MrsadColors {
    val PureBlack = Color(0xFF000000)
    val GraphiteDark = Color(0xFF0D0D0D)
    val Graphite = Color(0xFF161616)
    val GraphiteElevated = Color(0xFF202020)
    val BorderSubtle = Color(0xFF2C2C2C)
    val BorderCrisp = Color(0xFF424242)
    val GrayMuted = Color(0xFF737373)
    val GraySecondary = Color(0xFFA3A3A3)
    val OffWhite = Color(0xFFE5E5E5)
    val PureWhite = Color(0xFFFFFFFF)
}

private val MrsadMonochromeScheme = darkColorScheme(
    primary = MrsadColors.PureWhite,
    onPrimary = MrsadColors.PureBlack,
    secondary = MrsadColors.OffWhite,
    onSecondary = MrsadColors.PureBlack,
    background = MrsadColors.PureBlack,
    onBackground = MrsadColors.PureWhite,
    surface = MrsadColors.GraphiteDark,
    onSurface = MrsadColors.PureWhite,
    surfaceVariant = MrsadColors.Graphite,
    onSurfaceVariant = MrsadColors.GraySecondary,
    outline = MrsadColors.BorderSubtle,
    outlineVariant = MrsadColors.BorderCrisp
)

@Composable
fun MrsadAmbientTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MrsadMonochromeScheme,
        typography = MrsadTypography,
        content = content
    )
}
