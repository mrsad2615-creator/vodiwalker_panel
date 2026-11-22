package dev.hgallery.core

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthGroupingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    private fun item(id: Long, at: LocalDateTime) = MediaItem(
        id = id,
        uri = "content://media/$id",
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1024,
        dateTakenMs = at.atZone(zone).toInstant().toEpochMilli(),
        width = 100,
        height = 100,
    )

    @Test
    fun `items are sectioned by calendar month, newest first`() {
        val items = listOf(
            item(1, LocalDateTime.of(2026, 9, 14, 10, 0)),
            item(2, LocalDateTime.of(2026, 11, 3, 9, 0)),
            item(3, LocalDateTime.of(2026, 10, 30, 18, 0)),
            item(4, LocalDateTime.of(2026, 11, 20, 8, 0)),
        )

        val sections = MonthGrouping.group(items, zone) { it.format(formatter) }

        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9)),
            sections.map { it.month },
        )
        assertEquals(listOf("November 2026", "October 2026", "September 2026"), sections.map { it.header })
        assertEquals(listOf(4L, 2L), sections.first().items.map { it.id })
    }

    @Test
    fun `the same day across a month boundary lands in different months`() {
        val items = listOf(
            item(1, LocalDateTime.of(2026, 11, 30, 23, 50)),
            item(2, LocalDateTime.of(2026, 12, 1, 0, 10)),
        )
        val sections = MonthGrouping.group(items, zone) { it.format(formatter) }
        assertEquals(2, sections.size)
        assertEquals(YearMonth.of(2026, 12), sections.first().month)
    }

    @Test
    fun `years are distinguished`() {
        val items = listOf(
            item(1, LocalDateTime.of(2025, 11, 20, 12, 0)),
            item(2, LocalDateTime.of(2026, 11, 20, 12, 0)),
        )
        val sections = MonthGrouping.group(items, zone) { it.format(formatter) }
        assertEquals(listOf("November 2026", "November 2025"), sections.map { it.header })
    }

    @Test
    fun `an empty library has no month sections`() {
        assertTrue(MonthGrouping.group(emptyList(), zone) { it.format(formatter) }.isEmpty())
    }

    @Test
    fun `isSameDay compares in the given zone`() {
        val morning = LocalDateTime.of(2026, 11, 21, 1, 0).atZone(zone).toInstant().toEpochMilli()
        val evening = LocalDateTime.of(2026, 11, 21, 23, 0).atZone(zone).toInstant().toEpochMilli()
        val nextDay = LocalDateTime.of(2026, 11, 22, 1, 0).atZone(zone).toInstant().toEpochMilli()

        assertTrue(MonthGrouping.isSameDay(morning, evening, zone))
        assertTrue(!MonthGrouping.isSameDay(evening, nextDay, zone))
    }
}
