package com.mesad.time.domain

import com.google.common.truth.Truth.assertThat
import com.mesad.time.domain.model.JalaliDate
import org.junit.Test
import java.time.LocalDate

class JalaliDateTest {
    @Test fun nowruz2025IsFirstOfFarvardin1404() {
        val j = JalaliDate.fromGregorian(LocalDate.of(2025, 3, 21))
        assertThat(listOf(j.year, j.month, j.day)).containsExactly(1404, 1, 1).inOrder()
    }
    @Test fun leapDay2024MapsToEsfand1402() {
        val j = JalaliDate.fromGregorian(LocalDate.of(2024, 2, 29))
        assertThat(listOf(j.year, j.month, j.day)).containsExactly(1402, 12, 10).inOrder()
    }
    @Test fun jalaliDatesRoundTripGregorian() {
        listOf(LocalDate.of(2000,1,1), LocalDate.of(2024,3,20), LocalDate.of(2026,11,16), LocalDate.of(2030,12,31)).forEach { g ->
            assertThat(JalaliDate.toGregorian(JalaliDate.fromGregorian(g))).isEqualTo(g)
        }
    }
    @Test fun monthLengthsIncludeLeapEsfand() {
        assertThat(JalaliDate.daysInJalaaliMonth(1404,1)).isEqualTo(31)
        assertThat(JalaliDate.daysInJalaaliMonth(1404,7)).isEqualTo(30)
        assertThat(JalaliDate.daysInJalaaliMonth(1403,12)).isEqualTo(30)
    }
}
