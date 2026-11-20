package com.mrsad.ambient

import com.mrsad.ambient.designsystem.NumeralFormatter
import com.mrsad.ambient.domain.model.BatteryState
import com.mrsad.ambient.domain.model.ProfileType
import com.mrsad.ambient.domain.recommendation.RecommendationEngine
import com.mrsad.ambient.system.PersianCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianCalendarAndRecommendationTest {

    @Test
    fun testGregorianToPersianConversion() {
        val persian = PersianCalendarHelper.fromGregorian(2026, 3, 21)
        assertEquals(1405, persian.year)
        assertEquals(1, persian.month)
        assertEquals(1, persian.day)
        assertEquals("فروردین", persian.monthNameFa)
    }

    @Test
    fun testPersianNumeralFormatting() {
        assertEquals("۱۲:۳۴", NumeralFormatter.format("12:34", usePersianNumerals = true))
        assertEquals("12:34", NumeralFormatter.format("12:34", usePersianNumerals = false))
    }

    @Test
    fun testLowBatteryRecommendation() {
        val battery = BatteryState(percentage = 15, isCharging = false, chargingSource = "Battery", isLowBattery = true)
        val recs = RecommendationEngine.evaluate(battery, ProfileType.DAY)
        assertTrue(recs.any { it.id == "low_battery" })
    }
}
