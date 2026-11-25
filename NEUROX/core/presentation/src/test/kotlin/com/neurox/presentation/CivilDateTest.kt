package com.neurox.presentation

import com.neurox.presentation.i18n.CivilDate
import com.neurox.presentation.i18n.Strings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Calendar arithmetic.
 *
 * A wrong day in a backup header is not a crash; it is a user deciding their backup is
 * from the wrong week. These tests pin the edges (epoch, leap day, century rule) rather
 * than trusting the formula.
 */
class CivilDateTest {

    @Test
    fun epochIsTheFirstOfJanuary1970() {
        assertEquals(Triple(1970L, 1, 1), CivilDate.fromEpochDay(0))
    }

    @Test
    fun dayBeforeTheEpochIsTheLastOf1969() {
        assertEquals(Triple(1969L, 12, 31), CivilDate.fromEpochDay(-1))
    }

    @Test
    fun leapDay2024Exists_and2023HasNo29thFebruary() {
        // 2024-02-29 is 19782 days after the epoch.
        assertEquals(Triple(2024L, 2, 29), CivilDate.fromEpochDay(19_782))
        assertEquals(Triple(2023L, 3, 1), CivilDate.fromEpochDay(19_782 - 365))
    }

    @Test
    fun centuryRuleIsApplied() {
        // 2000 was a leap year, 1900 was not: the classic off-by-one-day trap.
        assertEquals(Triple(2000L, 2, 29), CivilDate.fromEpochDay(11_016))
        // 2100-03-01 is the day after a non-leap February.
        val feb28 = CivilDate.fromEpochDay(47_540)
        val mar01 = CivilDate.fromEpochDay(47_541)
        assertEquals(2100, feb28.first.toInt())
        assertEquals(3, mar01.second)
        assertEquals(1, mar01.third)
    }

    @Test
    fun dateTimeIsUnambiguousAndLabelledUtc() {
        val strings = Strings.of("en")
        // 2024-03-05T06:07:00Z
        val text = strings.dateTime(1_709_618_820_000)
        assertEquals("2024-03-05 06:07 UTC", text)
    }

    @Test
    fun dateTimeUsesPersianDigitsInPersian() {
        val text = Strings.of("fa").dateTime(1_709_618_820_000)
        assertEquals("۲۰۲۴-۰۳-۰۵ ۰۶:۰۷ UTC", text)
    }

    @Test
    fun dateTimeOfTheEpochMarksTheUtcLabel() {
        assertEquals("1970-01-01 00:00 UTC", Strings.of("en").dateTime(0))
    }
}
