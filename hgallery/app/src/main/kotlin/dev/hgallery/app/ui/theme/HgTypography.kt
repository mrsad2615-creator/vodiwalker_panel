package dev.hgallery.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Type ramp taken from the reference measurements.
 *
 * HarmonyOS Sans is not redistributable, so the system sans is used with the
 * same metrics. Persian resolves through the platform's own UI font at the same
 * sizes, which keeps the two scripts on one baseline rhythm.
 */
private val sans = FontFamily.SansSerif

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.35f).toInt(), spacing: Float = 0f) =
    TextStyle(
        fontFamily = sans,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = line.sp,
        letterSpacing = spacing.sp,
        lineHeightStyle = lineHeightStyle,
    )

val HgTypography = Typography(
    // Large screen title: "Photos", "Albums"
    headlineLarge = style(30, FontWeight.SemiBold, line = 36),
    // Sheet and dialog titles
    headlineSmall = style(20, FontWeight.SemiBold, line = 26),
    // Selection title, album detail header
    titleLarge = style(17, FontWeight.Medium, line = 24),
    titleMedium = style(15, FontWeight.Medium, line = 21),
    // Date section headers
    titleSmall = style(15, FontWeight.Medium, line = 20),
    bodyLarge = style(15, FontWeight.Normal, line = 22),
    bodyMedium = style(14, FontWeight.Normal, line = 20),
    bodySmall = style(12, FontWeight.Normal, line = 16),
    // Bottom-bar labels
    labelSmall = style(10, FontWeight.Normal, line = 12),
    labelMedium = style(12, FontWeight.Medium, line = 16),
    // Upper-case section headers such as MORE ALBUMS
    labelLarge = style(12, FontWeight.Medium, line = 16, spacing = 0.7f),
)

/** The two sizes that are not on the Material ramp. */
val CountLabel = style(12, FontWeight.Normal, line = 16)
val AlbumTitle = style(14, FontWeight.Normal, line = 19)
