package dev.mrsad.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single clock abstraction for the whole app.
 *
 * All features must obtain "now" and "today" from here: this keeps ISO date
 * keys, session timestamps and day rollover consistent, and makes tests able
 * to override time later without touching UI code.
 */
object MrsadClock {

    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun nowEpochMillis(): Long = System.currentTimeMillis()

    fun todayIso(): String = LocalDate.now().format(isoFormatter)

    fun isoFor(epochMillis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .toLocalDate()
            .format(isoFormatter)

    /** Persian weekday label for a date, used by planner and analytics. */
    fun persianWeekday(dateIso: String): String {
        val day = LocalDate.parse(dateIso, isoFormatter).dayOfWeek.value
        return when (day) {
            1 -> "دوشنبه"
            2 -> "سه‌شنبه"
            3 -> "چهارشنبه"
            4 -> "پنجشنبه"
            5 -> "جمعه"
            6 -> "شنبه"
            else -> "یکشنبه"
        }
    }
}
