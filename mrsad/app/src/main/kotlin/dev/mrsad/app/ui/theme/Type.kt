package dev.mrsad.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.mrsad.app.R
import dev.mrsad.core.text.PersianText

/**
 * Font families.
 * - Vazirmatn (OFL): the primary UI typeface — excellent Persian shaping.
 * - Estedad (OFL): a geometric, display-friendly alternative used for
 *   headlines and the reading environment (user selectable later).
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val Estedad = FontFamily(
    Font(R.font.estedad_medium, FontWeight.Medium),
    Font(R.font.estedad_semibold, FontWeight.SemiBold),
    Font(R.font.estedad_bold, FontWeight.Bold),
)

/**
 * MRSAD typography scale. All sizes derive from one base so the user font
 * scale setting scales the whole app consistently.
 */
@Immutable
data class MrsadTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val metric: TextStyle,
    val mono: TextStyle,
    val reading: TextStyle,
) {
    companion object {
        val default: MrsadTypography = build(1f, false)

        @Composable
        fun remember(fontScale: Float, readingFont: Boolean): MrsadTypography =
            remember(fontScale, readingFont) { build(fontScale, readingFont) }

        fun build(fontScale: Float, readingFont: Boolean): MrsadTypography {
            fun s(value: Float) = (value * fontScale).sp
            val displayFamily = if (readingFont) Estedad else Vazirmatn
            return MrsadTypography(
                display = TextStyle(
                    fontFamily = displayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = s(34f),
                    lineHeight = s(46f),
                    letterSpacing = 0.sp,
                ),
                headline = TextStyle(
                    fontFamily = displayFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = s(24f),
                    lineHeight = s(34f),
                ),
                title = TextStyle(
                    fontFamily = Vazirmatn,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = s(18f),
                    lineHeight = s(26f),
                ),
                bodyLarge = TextStyle(
                    fontFamily = Vazirmatn,
                    fontWeight = FontWeight.Normal,
                    fontSize = s(17f),
                    lineHeight = s(30f),
                ),
                body = TextStyle(
                    fontFamily = Vazirmatn,
                    fontWeight = FontWeight.Normal,
                    fontSize = s(15f),
                    lineHeight = s(26f),
                ),
                label = TextStyle(
                    fontFamily = Vazirmatn,
                    fontWeight = FontWeight.Medium,
                    fontSize = s(13f),
                    lineHeight = s(22f),
                ),
                caption = TextStyle(
                    fontFamily = Vazirmatn,
                    fontWeight = FontWeight.Normal,
                    fontSize = s(12f),
                    lineHeight = s(19f),
                ),
                // Persian digits carry descenders (۴, ۶, ۷) and dots that sit
                // below the baseline, so the metric line needs room: a 34dp
                // line for a 30dp face clips them.
                metric = TextStyle(
                    fontFamily = Estedad,
                    fontWeight = FontWeight.Bold,
                    fontSize = s(30f),
                    lineHeight = s(40f),
                    textAlign = TextAlign.Center,
                    // Tabular figures keep live metrics from jittering.
                    fontFeatureSettings = "tnum",
                ),
                mono = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = s(14f),
                    lineHeight = s(22f),
                ),
                reading = TextStyle(
                    fontFamily = if (readingFont) Estedad else Vazirmatn,
                    fontWeight = FontWeight.Normal,
                    fontSize = s(21f),
                    lineHeight = s(38f),
                ),
            )
        }
    }
}

/** Formats numbers respecting the user's Persian/Latin numeral preference. */
@Composable
fun formatNumber(value: String, persianNumerals: Boolean): String =
    if (persianNumerals) PersianText.toPersianDigits(value) else PersianText.toLatinDigits(value)

@Composable
fun formatInt(value: Int, persianNumerals: Boolean): String =
    formatNumber(value.toString(), persianNumerals)

@Composable
fun formatPercent(value: Double, persianNumerals: Boolean, decimals: Int = 0): String {
    val rounded = if (decimals == 0) {
        value.toInt().toString()
    } else {
        val factor = when (decimals) { 1 -> 10; 2 -> 100; else -> 1 }
        val scaled = (value * factor).toInt()
        "${scaled / factor}.${scaled % factor}"
    }
    return formatNumber(rounded, persianNumerals) + "٪"
}
