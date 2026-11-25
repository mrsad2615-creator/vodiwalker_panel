package com.neurox.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.neurox.core.designsystem.R
import com.neurox.designsystem.tokens.NxType

/**
 * Typography.
 *
 * FONT: Vazirmatn, bundled as a static family in res/font. It covers Persian and
 * Latin glyphs in one file, so mixed-language text renders with one font and no
 * fallback jump. Licensed under the SIL Open Font License 1.1; the license text is
 * shipped in assets/licenses.
 *
 * The weights are real static instances (400/500/600/700), not synthesized.
 */
object NxTypography {

    /** The single application font family. Changing it is a one-line change. */
    val family: FontFamily = FontFamily(
        androidx.compose.ui.text.font.Font(R.font.vazirmatn_regular, FontWeight.Normal),
        androidx.compose.ui.text.font.Font(R.font.vazirmatn_medium, FontWeight.Medium),
        androidx.compose.ui.text.font.Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
        androidx.compose.ui.text.font.Font(R.font.vazirmatn_bold, FontWeight.Bold),
    )

    val display = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = NxType.display,
        lineHeight = (NxType.DISPLAY_SP * 1.15f).sp,
        letterSpacing = (-0.4).sp,
    )

    val headline = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = NxType.headline,
        lineHeight = (NxType.HEADLINE_SP * 1.2f).sp,
        letterSpacing = (-0.2).sp,
    )

    val title = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = NxType.title,
        lineHeight = (NxType.TITLE_SP * 1.3f).sp,
    )

    val body = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = NxType.body,
        lineHeight = (NxType.BODY_SP * 1.45f).sp,
    )

    val label = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = NxType.label,
        lineHeight = (NxType.LABEL_SP * 1.3f).sp,
    )

    val caption = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = NxType.caption,
        lineHeight = (NxType.CAPTION_SP * 1.3f).sp,
    )

    /** Numeric display: tabular figures, so changing digits do not shift layout. */
    val number = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = NxType.headline,
        fontFeatureSettings = "tnum",
    )

    /**
     * Reading style for the READ module. Line height and letter spacing are user
     * settings, so the studio builds this style instead of using a constant.
     */
    fun readingStyle(fontSizeSp: Int, lineHeightMultiplier: Double, rtl: Boolean): TextStyle = TextStyle(
        fontFamily = family,
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * NxType.lineHeight(lineHeightMultiplier, rtl)).toFloat().sp,
        letterSpacing = 0.2.sp,
        textDirection = TextDirection.ContentOrRtl,
    )

    /** Maps NEUROX roles onto the Material 3 slots the components expect. */
    fun materialTypography(light: Boolean): Typography = Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = headline,
        headlineLarge = headline,
        headlineMedium = headline,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = title,
        titleSmall = label,
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = label,
        labelLarge = label,
        labelMedium = label,
        labelSmall = caption,
    )
}
