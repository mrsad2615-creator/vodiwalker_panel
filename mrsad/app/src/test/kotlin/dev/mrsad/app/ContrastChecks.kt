package dev.mrsad.app

import androidx.compose.ui.graphics.Color
import dev.mrsad.app.data.settings.ThemeVariant
import dev.mrsad.app.ui.theme.colorsFor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrast audit for the semantic colour system.
 *
 * The palette is built by hand, so a colour that looks right on one surface
 * can quietly fall below the accessibility floor on another. These checks
 * make the floor permanent: body text has to clear 4.5:1 against every
 * surface it can appear on, and muted or semantic colours have to clear
 * 3:1, which is the WCAG 2.1 threshold for non-body text and for the
 * meaningful parts of a user interface.
 */
class ContrastChecks {

    @Test
    fun everyThemeKeepsBodyTextReadable() {
        ThemeVariant.entries.forEach { variant ->
            val colors = colorsFor(variant)
            listOf(colors.background, colors.backgroundElevated, colors.surface, colors.surfaceHigh)
                .forEach { background ->
                    listOf(colors.textPrimary, colors.textSecondary).forEach { foreground ->
                        val ratio = contrast(foreground, background)
                        assertTrue(
                            "${variant.name}: ${foreground.toHex()} on ${background.toHex()} " +
                                "is $ratio:1, below the 4.5:1 body-text floor",
                            ratio >= 4.5,
                        )
                    }
                }
        }
    }

    @Test
    fun everyThemeKeepsMutedAndSemanticColoursVisible() {
        ThemeVariant.entries.forEach { variant ->
            val colors = colorsFor(variant)
            listOf(colors.background, colors.surface, colors.surfaceHigh).forEach { background ->
                listOf(
                    colors.textMuted,
                    colors.accent,
                    colors.success,
                    colors.warning,
                    colors.error,
                ).forEach { foreground ->
                    val ratio = contrast(foreground, background)
                    assertTrue(
                        "${variant.name}: ${foreground.toHex()} on ${background.toHex()} " +
                            "is $ratio:1, below the 3:1 non-text floor",
                        ratio >= 3.0,
                    )
                }
            }
        }
    }

    @Test
    fun everyThemeKeepsBordersVisible() {
        ThemeVariant.entries.forEach { variant ->
            val colors = colorsFor(variant)
            val ratio = contrast(colors.borderStrong, colors.background)
            assertTrue(
                "${variant.name}: the strong border is only $ratio:1 against the background",
                ratio >= 1.4,
            )
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun Color.toHex(): String {
        fun hex(value: Float): String =
            (value * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#${hex(red)}${hex(green)}${hex(blue)}".uppercase()
    }
}
