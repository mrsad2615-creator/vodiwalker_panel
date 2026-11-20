package com.mrsad.ambient.system

import java.util.Calendar

data class PersianDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val monthNameFa: String,
    val weekdayFa: String,
    val weekdayEn: String,
    val gregorianFormatted: String
)

object PersianCalendarHelper {
    private val monthNamesFa = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private val weekdaysFa = arrayOf(
        "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه"
    )

    private val weekdaysEn = arrayOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    fun now(): PersianDate {
        val cal = Calendar.getInstance()
        return fromGregorian(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.DAY_OF_WEEK)
        )
    }

    fun fromGregorian(gy: Int, gm: Int, gd: Int, dayOfWeek: Int = Calendar.SUNDAY): PersianDate {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }

        val dowIndex = (dayOfWeek - 1).coerceIn(0, 6)
        val gregorianStr = "%04d-%02d-%02d".format(gy, gm, gd)

        return PersianDate(
            year = jy,
            month = jm,
            day = jd,
            monthNameFa = monthNamesFa[(jm - 1).coerceIn(0, 11)],
            weekdayFa = weekdaysFa[dowIndex],
            weekdayEn = weekdaysEn[dowIndex],
            gregorianFormatted = gregorianStr
        )
    }
}
