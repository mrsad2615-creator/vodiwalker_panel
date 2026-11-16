package com.mesad.time.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Jalali (Persian) calendar date.
 * Pure Kotlin implementation of the Jalaali algorithm (Birashk 33-year cycle).
 * Verified against official Iranian calendar.
 */
data class JalaliDate(
    val year: Int,   // e.g., 1404
    val month: Int,  // 1-12 (Farvardin..Esfand)
    val day: Int,    // 1-29/30/31
    val weekday: DayOfWeek
) {
    companion object {
        private val JALAALI_MONTHS = listOf(
            "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
        )

        private val JALAALI_MONTHS_FA = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )

        private val WEEKDAYS_FA = listOf(
            "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
        )

        /**
         * Convert Gregorian LocalDate to JalaliDate using Jalaali algorithm.
         * Based on the algorithm by Kazimierz M. Borkowski & the Jalaali calendar team.
         */
        fun fromGregorian(gregorian: LocalDate): JalaliDate {
            val gy = gregorian.year
            val gm = gregorian.monthValue
            val gd = gregorian.dayOfMonth

            val j = gregorianToJalaali(gy, gm, gd)
            return JalaliDate(
                year = j.first,
                month = j.second,
                day = j.third,
                weekday = gregorian.dayOfWeek
            )
        }

        /**
         * Convert JalaliDate back to Gregorian LocalDate.
         */
        fun toGregorian(jalali: JalaliDate): LocalDate {
            val g = jalaaliToGregorian(jalali.year, jalali.month, jalali.day)
            return LocalDate.of(g.first, g.second, g.third)
        }

        fun monthName(month: Int, persian: Boolean = false): String {
            val list = if (persian) JALAALI_MONTHS_FA else JALAALI_MONTHS
            return list.getOrNull(month - 1) ?: ""
        }

        fun weekdayName(weekday: DayOfWeek, persian: Boolean = false): String {
            if (!persian) return weekday.name.lowercase().replaceFirstChar { it.uppercase() }
            // Saturday-first Persian week: Saturday=0 in Persian calendar
            val persianIndex = when (weekday) {
                DayOfWeek.SATURDAY -> 0
                DayOfWeek.SUNDAY -> 1
                DayOfWeek.MONDAY -> 2
                DayOfWeek.TUESDAY -> 3
                DayOfWeek.WEDNESDAY -> 4
                DayOfWeek.THURSDAY -> 5
                DayOfWeek.FRIDAY -> 6
            }
            return WEEKDAYS_FA[persianIndex]
        }

        // ── Jalaali Algorithm (Birashk) ──────────────────────────────────────

        private fun gregorianToJalaali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
            val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

            val gy2 = gy - 1600
            val gm2 = gm - 1
            val gd2 = gd - 1

            var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
            for (i in 0 until gm2) gDayNo += gDaysInMonth[i]
            if (gm2 > 1 && isGregorianLeap(gy)) gDayNo++
            gDayNo += gd2

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461

            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            var jm = 0
            while (jm < 11 && jDayNo >= jDaysInMonth[jm]) {
                jDayNo -= jDaysInMonth[jm]
                jm++
            }

            return Triple(jy, jm + 1, jDayNo + 1)
        }

        private fun jalaaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
            require(jy in 979..9378) { "Jalali year is outside the supported Gregorian range" }
            require(jm in 1..12) { "Month must be 1-12" }
            require(jd in 1..31) { "Day must be 1-31" }

            // Resolve through the forward converter to keep both directions consistent with the
            // tested Jalaali conversion table. A Jalali year occupies at most 366 Gregorian days.
            var date = LocalDate.of(jy + 621, 1, 1)
            repeat(400) {
                val converted = gregorianToJalaali(date.year, date.monthValue, date.dayOfMonth)
                if (converted.first == jy && converted.second == jm && converted.third == jd) {
                    return Triple(date.year, date.monthValue, date.dayOfMonth)
                }
                date = date.plusDays(1)
            }
            throw IllegalArgumentException("Invalid Jalali date: $jy/$jm/$jd")
        }

        private fun isGregorianLeap(year: Int): Boolean {
            return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
        }

        fun isJalaaliLeap(year: Int): Boolean {
            // Birashk 33-year cycle: leap years are those where (year % 33) in {1,5,9,13,17,22,26,30}
            val r = year % 33
            return r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30
        }

        fun daysInJalaaliMonth(year: Int, month: Int): Int {
            return when (month) {
                in 1..6 -> 31
                in 7..11 -> 30
                12 -> if (isJalaaliLeap(year)) 30 else 29
                else -> throw IllegalArgumentException("Month must be 1-12")
            }
        }
    }
}
