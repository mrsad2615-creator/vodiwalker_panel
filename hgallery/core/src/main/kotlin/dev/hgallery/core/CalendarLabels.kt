package dev.hgallery.core

/**
 * Date labels for the timeline, in either calendar the app supports.
 *
 * ## Why this is a rule and not a formatter call
 *
 * The app ships English and Persian. A Persian user reads a Persian calendar, and a date
 * header that reads "29 August 2020" on a Persian device is a date they have to convert in
 * their head. The platform can render a Persian date through a locale, but the *decision*
 * — which calendar, which fields, which order — is a rule, and it belongs somewhere it can
 * be tested without a device.
 *
 * ## What is not here
 *
 * Month names, day names and numerals. Those come from the platform's own locale data,
 * which is already correct for every language and is a great deal of text to reproduce by
 * hand. This file chooses the pattern; the platform fills it in.
 */
enum class HgCalendar {
    /** Georgian months and years, used for English and for everything else. */
    GREGORIAN,

    /** Persian months and years, used when the device language is Persian. */
    PERSIAN,
}

/**
 * The header patterns, and the year rule.
 *
 * A header inside the current year omits the year: "29 August" is unambiguous when the
 * timeline is sorted and the section above it is from the same year. Outside it, the year
 * is the one fact the reader needs, so it is shown.
 */
object CalendarLabels {

    /** The calendar a language tag asks for. */
    fun calendarFor(languageTag: String): HgCalendar =
        if (languageTag.lowercase().startsWith("fa")) HgCalendar.PERSIAN else HgCalendar.GREGORIAN

    /**
     * The pattern for a section header.
     *
     * The shapes are the platform's, not invented here: `d MMMM` for a day in the current
     * year, `d MMMM yyyy` outside it, and `MMMM yyyy` for the month level. With the
     * Persian calendar those same shapes render Persian months and Persian year numbers.
     */
    fun dayPattern(includeYear: Boolean): String =
        if (includeYear) "d MMMM yyyy" else "d MMMM"

    /** The month-level header, which always carries its year. */
    const val MONTH_PATTERN = "MMMM yyyy"

    /**
     * The locale to format with.
     *
     * A Persian calendar needs a locale that asks for it, so a Persian device gets a
     * locale that renders the Persian calendar even when the device language is not
     * Persian — for example an English phone read by a Persian speaker. The reverse is
     * not done: a Persian phone in English is only possible if the app is set to English,
     * and then its dates follow the language.
     */
    /**
     * Whether two ISO dates are in the same year of [calendar].
     *
     * Not `LocalDate.year == LocalDate.year`: a Persian year starts around 21 March, so
     * two dates in one Gregorian year can sit in two Persian years. The comparison is made
     * on the formatted year, which is the year the header would print.
     *
     * The platform's own calendar system does the arithmetic, so a leap year and the
     * month lengths are its problem, not this file's.
     */
    fun sameYear(a: java.time.LocalDate, b: java.time.LocalDate, calendar: HgCalendar): Boolean {
        val locale = localeFor(calendar, java.util.Locale.ROOT)
        val formatted = java.time.format.DateTimeFormatter.ofPattern("yyyy", locale)
        return a.format(formatted) == b.format(formatted)
    }

    fun localeFor(calendar: HgCalendar, base: java.util.Locale): java.util.Locale =
        when (calendar) {
            HgCalendar.GREGORIAN -> base
            HgCalendar.PERSIAN -> java.util.Locale.forLanguageTag("fa-IR-u-ca-persian")
        }
}
