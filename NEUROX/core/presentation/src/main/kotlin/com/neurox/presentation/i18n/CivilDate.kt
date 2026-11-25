package com.neurox.presentation.i18n

/**
 * Proleptic Gregorian calendar arithmetic.
 *
 * WHY THIS EXISTS: `java.time` is available on API 26+, but this module is pure JVM and
 * the Android layers must not depend on a desugared clock for something as small as
 * "which day is this". The algorithm below is Howard Hinnant's `civil_from_days`, which
 * is exact for every year in the proleptic Gregorian range and has no external state.
 *
 * The epoch day is counted from 1970-01-01, matching `millis / 86_400_000`.
 */
object CivilDate {

    /** Returns `Triple(year, month, day)`; month is 1..12. */
    fun fromEpochDay(epochDay: Long): Triple<Long, Int, Int> {
        val z = epochDay + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097                                    // [0, 146096]
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365 // [0, 399]
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)               // [0, 365]
        val mp = (5 * doy + 2) / 153                                    // [0, 11]
        val d = doy - (153 * mp + 2) / 5 + 1                            // [1, 31]
        val m = if (mp < 10) mp + 3 else mp - 9                         // [1, 12]
        val year = if (m <= 2) y + 1 else y
        return Triple(year, m.toInt(), d.toInt())
    }
}
