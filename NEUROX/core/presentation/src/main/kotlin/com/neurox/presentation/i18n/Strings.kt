package com.neurox.presentation.i18n

/**
 * The localization contract.
 *
 * WHY THIS EXISTS: the app ships exactly two languages, Persian (fa) and English
 * (en), and both must be complete. Instead of hiding missing translations in
 * resource files that only fail at runtime, the strings live in code where a
 * *test* can assert that both languages define the same keys with non-blank
 * values. A missing Persian string is therefore a test failure, not a bug the
 * user finds on a device.
 *
 * RULES
 * - Keys are stable identifiers, never sentences. Keys used by the domain
 *   (achievement.*, quest.*, recommendation reasons) are persisted in backups and
 *   preferences, so they must never be renamed.
 * - Both languages are complete peers. English is not a fallback for Persian.
 * - Digits are localized only where the product presentation layer decides they
 *   should be; see [decimal].
 */
class Strings private constructor(
    val languageTag: String,
    private val values: Map<String, String>,
) {

    val isRightToLeft: Boolean get() = languageTag == "fa"

    /** Direction of the layout, stated once so no screen invents its own rule. */
    val layoutDirection: LayoutDirection get() = if (isRightToLeft) LayoutDirection.RTL else LayoutDirection.LTR

    /** Returns the translation or the key itself, which is visible in QA builds. */
    fun raw(key: String): String = values[key] ?: key

    /** True when this key exists; used by parity tests and by QA. */
    fun has(key: String): Boolean = values.containsKey(key)

    /** Translates and substitutes `{0}`, `{1}`, ... placeholders. */
    fun format(key: String, vararg args: Any): String {
        var text = raw(key)
        args.forEachIndexed { index, arg ->
            val rendered = when (arg) {
                is Int -> decimal(arg.toLong())
                is Long -> decimal(arg)
                is Double -> Numbers.formatLocalized(arg, this, decimals = 1)
                is Float -> Numbers.formatLocalized(arg.toDouble(), this, decimals = 1)
                is Strings.Localized -> arg.text
                else -> arg.toString()
            }
            text = text.replace("{$index}", rendered)
        }
        return text
    }

    /** Wrapper for a value that must not be reformatted (for example a version). */
    data class Localized(val text: String)

    /**
     * Persian digits for social numbers (level, streak, XP). Measurements keep
     * Western digits because a stopwatch must be unambiguous.
     */
    fun decimal(value: Long, localize: Boolean = true): String {
        val text = value.toString()
        if (!localize || !isRightToLeft) return text
        return text.map { char ->
            when (char) {
                in '0'..'9' -> PERSIAN_DIGITS[char - '0']
                else -> char
            }
        }.joinToString("")
    }

    /** Milliseconds as a short, unit-labelled measurement (Western digits). */
    fun millis(value: Double): String = format("unit.millis", Localized(Numbers.formatLocalized(value, this, 0, localizeDigits = false)))

    /**
     * Percentage with one decimal.
     *
     * Rule: a value that would *render* as 100.0% shows "100%" instead. 99.9% stays
     * 99.9%, because silently rounding it up would claim a perfect session that did
     * not happen.
     */
    /**
     * Formats an instant for display, using the locale's own digit set.
     *
     * A real localized date needs a calendar system and a time zone, which this pure
     * module does not have. Rather than emit a wrong-looking date, the value is shown
     * as an unambiguous sortable stamp (YYYY-MM-DD HH:MM, UTC) labelled as such. The
     * label is part of the string, so nobody reads it as local time.
     */
    fun dateTime(millis: Long, utc: Boolean = true): String {
        val seconds = Math.floorDiv(millis, 1000L)
        val epochDay = Math.floorDiv(seconds, 86_400L)
        val secondOfDay = Math.floorMod(seconds, 86_400L)
        val (year, month, day) = CivilDate.fromEpochDay(epochDay)
        val hour = secondOfDay / 3600
        val minute = (secondOfDay % 3600) / 60
        val text = buildString {
            append(pad(year, 4)).append('-')
            append(pad(month, 2)).append('-')
            append(pad(day, 2)).append(' ')
            append(pad(hour, 2)).append(':')
            append(pad(minute, 2))
            if (utc) append(" UTC")
        }
        return localize(text)
    }

    private fun pad(value: Long, width: Int): String = value.toString().padStart(width, '0')
    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')

    /** Maps ASCII digits onto this locale's digit set, leaving separators untouched. */
    private fun localize(text: String): String {
        if (!isRightToLeft) return text
        val builder = StringBuilder(text.length)
        for (char in text) {
            builder.append(if (char in '0'..'9') PERSIAN_DIGITS[char - '0'] else char)
        }
        return builder.toString()
    }

    fun percent(fraction: Double): String {
        val clamped = fraction.coerceIn(0.0, 1.0)
        val percentValue = clamped * 100.0
        val roundedOneDecimal = kotlin.math.round(percentValue * 10.0) / 10.0
        return if (roundedOneDecimal >= 100.0) {
            "100%"
        } else {
            Numbers.formatLocalized(percentValue, this, 1) + "%"
        }
    }

    fun wordsPerMinute(value: Double): String =
        format("unit.wpm", Localized(Numbers.formatLocalized(value, this, 1, localizeDigits = false)))

    companion object {
        private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

        val SUPPORTED_LANGUAGES = listOf("fa", "en")
        const val DEFAULT_LANGUAGE = "fa"

        private val cache = mutableMapOf<String, Strings>()

        fun of(languageTag: String?): Strings {
            val normalized = when {
                languageTag == null -> DEFAULT_LANGUAGE
                languageTag.lowercase().startsWith("fa") -> "fa"
                languageTag.lowercase().startsWith("en") -> "en"
                else -> DEFAULT_LANGUAGE
            }
            return cache.getOrPut(normalized) {
                Strings(normalized, if (normalized == "fa") PersianStrings else EnglishStrings)
            }
        }

        /**
         * Every key defined by the English catalogue.
         *
         * Public on purpose: the parity test lives in a different compilation unit,
         * and a hidden catalogue is a catalogue nobody can audit.
         */
        fun englishKeys(): Set<String> = EnglishStrings.keys

        fun persianKeys(): Set<String> = PersianStrings.keys
    }
}

enum class LayoutDirection { LTR, RTL }

/**
 * Localized number rendering, kept next to [Strings] because the digit set is a
 * localization decision, not a formatting decision.
 */
object Numbers {

    /**
     * Formats a decimal number.
     *
     * [localizeDigits] = false keeps ASCII digits even in Persian, which is what
     * measured values (milliseconds, WPM) use so they can never be misread.
     */
    fun formatLocalized(
        value: Double,
        strings: Strings,
        decimals: Int,
        localizeDigits: Boolean = true,
    ): String {
        val negative = value < 0
        val absolute = kotlin.math.abs(value)
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        val rounded = kotlin.math.round(absolute * factor) / factor
        val whole = rounded.toLong()
        val fraction = kotlin.math.round((rounded - whole) * factor).toLong()
        val sign = if (negative) "-" else ""
        val wholeText = if (localizeDigits) strings.decimal(whole) else whole.toString()
        if (decimals == 0) return sign + wholeText
        val fractionText = fraction.toString().padStart(decimals, '0')
        val fractionRendered = if (localizeDigits) strings.decimal(fraction).padStart(decimals, '0') else fractionText
        return sign + wholeText + "." + fractionRendered
    }
}
