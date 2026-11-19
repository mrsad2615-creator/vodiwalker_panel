package com.vodiwalker.gesture.core.designsystem.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for the Persian number and bidi formatting used across the interface.
 */
class PersianTextTest {

    /** Latin digits must always be converted; non-digits must survive untouched. */
    @Test
    fun convertsLatinDigitsOnly() {
        assertEquals("۱۲۳۴۵۶۷۸۹۰", PersianText.toPersianDigits("1234567890"))
        assertEquals("ژست ۳ از ۵", PersianText.toPersianDigits("ژست 3 از 5"))
        // Latin letters, dots and the version separator survive; only the digits change.
        assertEquals("VodiGesture ۱.۰.۰", PersianText.toPersianDigits("VodiGesture 1.0.0"))
        assertEquals("", PersianText.toPersianDigits(""))
    }

    /** Thousands grouping uses the Persian separator (U+066C), not a Latin comma. */
    @Test
    fun groupsThousandsWithPersianSeparator() {
        assertEquals("۰", PersianText.formatInteger(0))
        assertEquals("۹۹۹", PersianText.formatInteger(999))
        assertEquals("۱٬۰۰۰", PersianText.formatInteger(1_000))
        assertEquals("۱۲٬۳۴۵", PersianText.formatInteger(12_345))
        assertEquals("۱٬۲۳۴٬۵۶۷", PersianText.formatInteger(1_234_567))
    }

    /** A negative sign must stay ASCII so the bidi algorithm keeps it before the number. */
    @Test
    fun keepsSignDirectional() {
        assertEquals("-۱٬۲۰۰", PersianText.formatInteger(-1_200))
    }

    /** Percentages are rounded to whole numbers and carry the Persian percent sign. */
    @Test
    fun formatsPercentages() {
        assertEquals("۰٪", PersianText.formatPercent(0f))
        assertEquals("۸۷٪", PersianText.formatPercent(0.874f))
        assertEquals("۱۰۰٪", PersianText.formatPercent(1f))
        // Out-of-range input is clamped, never rendered as 150%.
        assertEquals("۱۰۰٪", PersianText.formatPercent(1.5f))
    }

    /** Durations switch unit at the natural boundaries. */
    @Test
    fun formatsDurations() {
        assertEquals("۴۵ ثانیه", PersianText.formatDurationSeconds(45))
        assertEquals("۲ دقیقه", PersianText.formatDurationSeconds(120))
        assertEquals("۳ ساعت", PersianText.formatDurationSeconds(10_800))
    }

    /** Persian quotations use guillemets and LTR fragments are isolated. */
    @Test
    fun quotesAndIsolates() {
        assertEquals("«ژست من»", PersianText.quote("ژست من"))
        assertEquals("\u2066com.example\u2069", PersianText.isolateLtr("com.example"))
    }

    /** Localisation detection is used by the UI audit. */
    @Test
    fun detectsLocalisedNumbers() {
        assertTrue(PersianText.isLocalised("۱۲٪"))
        assertFalse(PersianText.isLocalised("12٪"))
        assertTrue(PersianText.isLocalised("بدون عدد"))
    }

    /** Formatting must be allocation-cheap enough for list rows: 10k calls stay < 50 ms. */
    @Test
    fun formattingIsCheap() {
        val start = System.nanoTime()
        var total = 0
        repeat(10_000) { index ->
            total += PersianText.formatInteger(index.toLong()).length
        }
        val elapsedMillis = (System.nanoTime() - start) / 1_000_000.0
        assertTrue(total > 0)
        assertTrue(elapsedMillis < 50.0, "formatInteger too slow: ${elapsedMillis}ms for 10k calls")
    }
}
