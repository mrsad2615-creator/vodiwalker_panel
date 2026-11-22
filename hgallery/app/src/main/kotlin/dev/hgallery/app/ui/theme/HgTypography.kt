package dev.hgallery.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.hgallery.app.R

/**
 * Type system.
 *
 * ## Why two bundled families instead of the system font
 *
 * Huawei's Gallery is set in HarmonyOS Sans, which is proprietary and cannot be
 * redistributed. The previous build fell back to `FontFamily.SansSerif`, which on
 * Android means Roboto — a font Huawei never used, with narrower bowls and much
 * looser default tracking. That single choice is most of the reason the app read
 * as generic.
 *
 * Two OFL families replace it:
 *
 *  - **Inter** for Latin. Its x-height, open apertures and geometric-humanist
 *    skeleton sit within a few percent of HarmonyOS Sans, and it ships a
 *    variable axis, so weights are true weights rather than synthesised.
 *  - **Vazirmatn** for Persian/Arabic. Inter has no Arabic coverage at all; using
 *    it for Persian silently fell back to whatever the device had, which on a
 *    POCO is often a heavier, differently-metricked face.
 *
 * Both are declared in *one* `FontFamily` with Inter first. Compose walks the
 * family in order and takes the first font that has a glyph, so Latin resolves to
 * Inter and Arabic-script text resolves to Vazirmatn, in the same run of mixed
 * text, at the same line height.
 *
 * ## What changed in the ramp
 *
 *  - Large titles use negative tracking (`-0.02em`-ish). Huawei's big titles are
 *    optically tightened; Roboto's default 0 tracking is why the old "Photos"
 *    looked loose next to the reference.
 *  - The scale is a real scale: 34/28/22/17/15/13/11, not arbitrary values that
 *    happen to be near Material's.
 *  - Line heights are set as explicit values per style rather than a multiplier,
 *    because a 1.35× line height on a 34 sp title is far too airy and on an 11 sp
 *    label is far too tight.
 */
/**
 * The one family every style in the app uses.
 *
 * Inter is listed first and Vazirmatn immediately after, at each weight. Compose
 * resolves a glyph by walking this list, so:
 *
 *  - Latin text hits Inter, whose x-height, open apertures and humanist skeleton
 *    sit within a few percent of Huawei's HarmonyOS Sans;
 *  - Persian/Arabic text falls through to Vazirmatn, because Inter carries no
 *    Arabic-script coverage at all;
 *  - a single mixed-script string (a filename containing both, an album called
 *    "2024 سفر") resolves per glyph, in one family, at one line height.
 *
 * That last point is why this is a flat list rather than a per-locale branch: a
 * `Locale`-driven switch would pick one face for the whole string and leave the
 * other script to whatever the device happens to substitute.
 */
val HgFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    weight: FontWeight,
    line: Int,
    tracking: Float,
) = TextStyle(
    fontFamily = HgFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = trim,
)

val HgTypography = Typography(
    // The big screen titles: "Photos", "Albums".
    headlineLarge = style(32, FontWeight.SemiBold, line = 38, tracking = -0.6f),
    // Sheet and dialog titles.
    headlineSmall = style(21, FontWeight.SemiBold, line = 27, tracking = -0.2f),
    // Selection title, album detail header.
    titleLarge = style(18, FontWeight.SemiBold, line = 24, tracking = -0.1f),
    titleMedium = style(16, FontWeight.Medium, line = 22, tracking = -0.05f),
    // Day/month section headers.
    titleSmall = style(14, FontWeight.SemiBold, line = 19, tracking = 0f),
    bodyLarge = style(15, FontWeight.Normal, line = 22, tracking = 0.05f),
    bodyMedium = style(14, FontWeight.Normal, line = 20, tracking = 0.1f),
    bodySmall = style(12, FontWeight.Normal, line = 16, tracking = 0.15f),
    // Bottom-bar labels.
    labelSmall = style(10, FontWeight.Medium, line = 13, tracking = 0.2f),
    labelMedium = style(12, FontWeight.Medium, line = 16, tracking = 0.2f),
    // Upper-case section headers such as MORE ALBUMS.
    labelLarge = style(11, FontWeight.SemiBold, line = 15, tracking = 1.1f),
)

/** Secondary metadata: album counts, the "372 photos and 19 videos" line. */
val CountLabel = style(12, FontWeight.Normal, line = 16, tracking = 0.15f)

/** Album tile names, which sit directly under a cover. */
val AlbumTitle = style(13, FontWeight.Medium, line = 18, tracking = 0f)

/** Timecodes and the viewer's position counter: tabular, so digits do not jitter. */
val TimecodeLabel = TextStyle(
    fontFamily = HgFontFamily,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp,
    fontFeatureSettings = "tnum",
)

/** Large centred numeral in the video player, e.g. the seek indicator. */
val PlayerIndicatorLabel = style(13, FontWeight.SemiBold, line = 18, tracking = 0.3f)
