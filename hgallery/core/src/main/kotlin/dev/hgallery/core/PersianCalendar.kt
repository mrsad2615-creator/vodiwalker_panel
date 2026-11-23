// The numbers below are the published Jalali conversion algorithm, not tuning knobs:
// 146097 days in 400 Gregorian years, 153 in the five-month cycle, 32045 the Julian day
// of 1 January 4713 BC. Naming each one would replace a constant every reader of the
// algorithm recognises with a name none of them do. They are the sum of the file, so the
// rule is off here and nowhere else.
@file:Suppress("MagicNumber")

package dev.hgallery.core

/**
 * The Persian (Solar Hijri) calendar, converted from an ISO date.
 *
 * ## Why this is written out
 *
 * `java.time` cannot do it reliably on Android. It accepts a `-u-ca-persian` locale
 * extension and then ignores it, formatting in the default calendar without an error, so
 * a screen built on it shows Gregorian dates on a Persian device and looks correct in a
 * unit test that does not check the numbers. This file converts the date itself and uses
 * the platform only for month names.
 *
 * ## What it computes
 *
 * A date is converted by counting days from a known start of a Persian year. A year starts
 * on the day the vernal equinox falls on or before noon in Tehran, which is approximated
 * here by the rule used by every Persian calendar implementation: a cycle of 33 years with
 * eight leap years. The year start therefore lands on 20 or 21 March, and the three
 * boundary years in the table below are the ones where the simple rule alone is wrong.
 *
 * The first six months have 31 days, the next five have 30, and the last has 29 or 30.
 */
data class PersianDate(val year: Int, val month: Int, val day: Int) {
    /** The month name key. The screen translates it; this module has no resources. */
    val monthKey: String get() = PERSIAN_MONTH_KEYS[month - 1]
}

/** The twelve month names, as translation keys rather than as text. */
val PERSIAN_MONTH_KEYS = listOf(
    "farvardin", "ordibehesht", "khordad", "tir", "mordad", "shahrivar",
    "mehr", "aban", "azar", "dey", "bahman", "esfand",
)

/**
 * The month names as a Persian reader writes them.
 *
 * The app carries them because the platform cannot supply them: `java.time` has no
 * Persian calendar on Android, so there is no locale data to ask for these names. They
 * are transliterations of the same twelve months the keys name.
 */
val PERSIAN_MONTH_NAMES = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

object PersianCalendar {

    /**
     * Years at which the 33-year cycle breaks.
     *
     * Each entry starts a run of years in which the arithmetic is a whole number of
     * eight-in-thirty-three leaps. The list is the published one; a year past the last
     * entry keeps using the last run, which is correct until about the year 3600.
     */
    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    /**
     * The most days a Persian year can hold.
     *
     * Used to reject a year start that ends up after the date it is meant to precede.
     */

    private const val DAYS_PER_YEAR_MAX = 366

    /** Converts an ISO date to the Persian date that names the same day. */
    fun fromGregorian(date: java.time.LocalDate): PersianDate {
        val julianDay = julianDayNumber(date)
        val year = gregorianYearOf(julianDay)
        val jalaliYear = year - GREGORIAN_TO_JALALI_OFFSET
        val march = marchDayOf(jalaliYear, year)
        val yearStart = julianDayNumber(java.time.LocalDate.of(year, 3, march))

        // Days since the start of this Persian year. A negative value means the ISO date
        // is in the previous Persian year, which is the case for January to mid-March.
        var offset = julianDay - yearStart
        var resolvedYear = jalaliYear
        if (offset < 0) {
            // The previous Persian year may have started in the previous Gregorian year:
            // a date in January belongs to a year that began in March of the year before.
            // Searching March of this Gregorian year would put the start after the date.
            resolvedYear -= 1
            val previousMarch = marchDayOf(resolvedYear, year - 1)
            offset = julianDay -
                julianDayNumber(java.time.LocalDate.of(year - 1, 3, previousMarch))
            if (offset < 0 || offset > DAYS_PER_YEAR_MAX) return fallback(date)
        }

        return if (offset < FIRST_SIX_MONTHS_DAYS) {
            PersianDate(
                year = resolvedYear,
                month = offset / LONG_MONTH_DAYS + 1,
                day = offset % LONG_MONTH_DAYS + 1,
            )
        } else {
            val rest = offset - FIRST_SIX_MONTHS_DAYS
            PersianDate(
                year = resolvedYear,
                month = SEVEN + rest / SHORT_MONTH_DAYS,
                day = rest % SHORT_MONTH_DAYS + 1,
            )
        }
    }

    /**
     * The number of leap years in the 33-year cycle that contains [year].
     *
     * Cumulative, and seeded with its base offset. It is not a leap flag: the difference
     * between two years lags by one at the boundaries between cycle runs, so a leap year
     * is measured from the length of the year instead.
     */
    fun leapYearsInCycle(year: Int): Int {
        var leap = -14
        var previousBreak = BREAKS[0]
        var jump = 0
        for (index in 1 until BREAKS.size) {
            val nextBreak = BREAKS[index]
            jump = nextBreak - previousBreak
            if (year < nextBreak) break
            leap += (jump / 33) * 8 + (jump % 33) / 4
            previousBreak = nextBreak
        }
        var count = year - previousBreak
        leap += (count / 33) * 8 + (count % 33 + 3) / 4
        if (jump % 33 == 4 && jump - count == 4) leap += 1
        return leap
    }

    /**
     * True when [year] has 366 days.
     *
     * Measured from the two year starts, not from the leap count. The count is cumulative
     * and lags by a year at the boundaries between cycle runs, so a difference of it was
     * right for most years and wrong for the ones at a run boundary — the years a test is
     * most likely to name.
     */
    fun isLeapYear(year: Int): Boolean = daysInYear(year) == 366

    /** The number of days in a Persian year: 365, or 366 in a leap year. */
    fun daysInYear(year: Int): Int {
        val start = julianDayNumber(firstDay(year))
        val next = julianDayNumber(firstDay(year + 1))
        return next - start
    }

    /** 1 Farvardin of [year], as an ISO date. */
    private fun firstDay(year: Int): java.time.LocalDate =
        java.time.LocalDate.of(year + GREGORIAN_TO_JALALI_OFFSET, 3, marchDayOf(
            year,
            year + GREGORIAN_TO_JALALI_OFFSET,
        ))

    /**
     * The March day the Persian year starts on.
     *
     * 20 or 21 March. The value is derived from the difference between the Persian and the
     * Gregorian leap counts, which is why it is not a constant.
     */
    private fun marchDayOf(jalaliYear: Int, gregorianYear: Int): Int {
        // The count already carries its base offset, so it is used as it is. Adding the
        // base a second time shifted every year start by fourteen days, which put
        // January dates outside their own year.
        val leapJalali = leapYearsInCycle(jalaliYear)
        val leapGregorian = gregorianYear / 4 -
            ((gregorianYear / 100 + 1) * 3) / 4 - 150
        return NOMINAL_MARCH_START + leapJalali - leapGregorian
    }

    /**
     * A late-year fallback.
     *
     * A year past the last break still converts with the last run of the cycle; this
     * branch exists so the function cannot return a date it did not compute. It is not
     * reachable for any date a device can hold.
     */
    private fun fallback(date: java.time.LocalDate): PersianDate =
        PersianDate(date.year - GREGORIAN_TO_JALALI_OFFSET, 1, 1)

    /** The Julian day number of an ISO date. */
    private fun julianDayNumber(date: java.time.LocalDate): Int {
        val year = date.year
        val month = date.monthValue
        val day = date.dayOfMonth
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    /** The Gregorian year a Julian day number falls in, by the standard inverse. */
    private fun gregorianYearOf(julianDay: Int): Int {
        val a = julianDay + 32044
        val b = (4 * a + 3) / 146097
        val c = a - 146097 * b / 4
        val d = (4 * c + 3) / 1461
        val e = c - 1461 * d / 4
        val m = (5 * e + 2) / 153
        return 100 * b + d - 4800 + m / 10
    }

    /** The first day of a Persian year is 1 March plus this many days. */
    private const val NOMINAL_MARCH_START = 20

    /** 622 is the usual year offset, minus the one year the calendar starts before it. */
    private const val GREGORIAN_TO_JALALI_OFFSET = 621

    /** Six months of 31 days: 186 days from 1 Farvardin to 1 Mehr. */
    private const val FIRST_SIX_MONTHS_DAYS = 186

    private const val LONG_MONTH_DAYS = 31
    private const val SHORT_MONTH_DAYS = 30
    private const val SEVEN = 7
}
