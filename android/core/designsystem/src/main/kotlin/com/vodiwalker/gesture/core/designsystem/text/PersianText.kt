package com.vodiwalker.gesture.core.designsystem.text

import java.util.Locale

/**
 * Persian (fa-IR) text formatting for the interface.
 *
 * Persian UI copy uses **native Persian digits** (۰۱۲۳۴۵۶۷۸۹, U+06F0–U+06F9) — not the
 * Arabic-Indic set and not Latin digits. Mixed Persian/Latin runs are the main source of bidi
 * scrambling in RTL layouts, so every number that reaches the screen goes through [formatNumber]
 * and every quoted value through [quote].
 *
 * All functions are pure, allocation-bounded and safe on the accessibility binder thread.
 */
object PersianText {

    private const val LATIN_DIGITS = "0123456789"
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

    /** Persian decimal separator (U+066B) and thousands separator (U+066C). */
    private const val DECIMAL_SEPARATOR = '٫'
    private const val THOUSANDS_SEPARATOR = '٬'

    /**
     * Converts every Latin digit in [input] to its Persian counterpart, leaving the rest untouched.
     */
    fun toPersianDigits(input: String): String {
        if (input.isEmpty()) return input
        val builder = StringBuilder(input.length)
        for (char in input) {
            val index = LATIN_DIGITS.indexOf(char)
            builder.append(if (index >= 0) PERSIAN_DIGITS[index] else char)
        }
        return builder.toString()
    }

    /**
     * Formats [value] with Persian digits and grouped thousands separators.
     *
     * `12345` → `۱۲٬۳۴۵`; negatives keep a leading ASCII minus so bidi never reorders the sign.
     */
    fun formatInteger(value: Long): String {
        val negative = value < 0
        val digits = value.toString().removePrefix("-")
        val grouped = StringBuilder(digits.length + digits.length / 3)
        for ((position, char) in digits.withIndex()) {
            if (position > 0 && (digits.length - position) % 3 == 0) {
                grouped.append(THOUSANDS_SEPARATOR)
            }
            grouped.append(char)
        }
        val persian = toPersianDigits(grouped.toString())
        return if (negative) "-$persian" else persian
    }

    /**
     * Formats a ratio in `0f..1f` as a Persian percentage with no decimal noise: `0.87` → `۸۷٪`.
     */
    fun formatPercent(ratio: Float): String {
        val clamped = (ratio.coerceIn(0f, 1f) * 100f).toInt()
        return "${toPersianDigits(clamped.toString())}٪"
    }

    /**
     * Formats a non-negative duration as Persian, e.g. `۹۰` ثانیه or `۴` دقیقه.
     */
    fun formatDurationSeconds(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        return when {
            safe < 60L -> "${formatInteger(safe)} ثانیه"
            safe < 3_600L -> "${formatInteger(safe / 60L)} دقیقه"
            else -> "${formatInteger(safe / 3_600L)} ساعت"
        }
    }

    /**
     * Wraps [value] in Persian guillemets («»), the standard quotation marks in Persian copy.
     */
    fun quote(value: String): String = "«$value»"

    /**
     * Isolates an inherently left-to-right fragment (a URL, package name, or version string) with
     * Unicode isolates so it cannot reorder neighbouring Persian text.
     */
    fun isolateLtr(value: String): String = "\u2066$value\u2069"

    /**
     * True when [value] already uses Persian digits only (no Latin digit present).
     */
    fun isLocalised(value: String): Boolean = value.none { it in LATIN_DIGITS }

    /**
     * Locale used for `String.format` on user-visible numbers so grouping matches Persian norms.
     */
    val numberLocale: Locale = Locale.forLanguageTag("fa-IR")
}
