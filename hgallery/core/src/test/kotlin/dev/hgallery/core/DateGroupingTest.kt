package dev.hgallery.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DateGroupingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today: LocalDate = LocalDate.of(2026, 11, 21)

    private fun item(id: Long, at: LocalDateTime) = MediaItem(
        id = id,
        uri = "content://media/$id",
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1024,
        dateTakenMs = at.atZone(zone).toInstant().toEpochMilli(),
        width = 4000,
        height = 3000,
    )

    @Test
    fun `newest day comes first and days are distinct`() {
        val items = listOf(
            item(1, LocalDateTime.of(2026, 11, 19, 10, 0)),
            item(2, LocalDateTime.of(2026, 11, 21, 8, 0)),
            item(3, LocalDateTime.of(2026, 11, 20, 23, 59)),
        )

        val sections = DateGrouping.group(items, zone, today)

        assertEquals(listOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 19)), sections.map { it.date })
        assertEquals(listOf("Today", "Yesterday", "2026-11-19"), sections.map { it.header })
    }

    @Test
    fun `items inside a day are ordered newest first`() {
        val items = listOf(
            item(1, LocalDateTime.of(2026, 11, 21, 7, 0)),
            item(2, LocalDateTime.of(2026, 11, 21, 21, 30)),
            item(3, LocalDateTime.of(2026, 11, 21, 14, 0)),
        )

        val sections = DateGrouping.group(items, zone, today)

        assertEquals(1, sections.size)
        assertEquals(listOf(2L, 3L, 1L), sections.single().items.map { it.id })
    }

    @Test
    fun `identical timestamps fall back to id so the order is stable`() {
        val same = LocalDateTime.of(2026, 11, 21, 12, 0)
        val a = DateGrouping.group(listOf(item(1, same), item(9, same), item(5, same)), zone, today)
        val b = DateGrouping.group(listOf(item(5, same), item(1, same), item(9, same)), zone, today)

        assertEquals(listOf(9L, 5L, 1L), a.single().items.map { it.id })
        assertEquals(a.single().items.map { it.id }, b.single().items.map { it.id })
    }

    @Test
    fun `a day exactly at midnight in another zone is grouped by local date`() {
        // 23:30 UTC on the 20th is 00:30 on the 21st in Berlin.
        val utc = ZoneId.of("UTC")
        val itemUtc = MediaItem(
            id = 7,
            uri = "content://media/7",
            displayName = "IMG_7.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1,
            dateTakenMs = LocalDateTime.of(2026, 11, 20, 23, 30).atZone(utc).toInstant().toEpochMilli(),
            width = 1,
            height = 1,
        )

        val sections = DateGrouping.group(listOf(itemUtc), zone, today)
        assertEquals(LocalDate.of(2026, 11, 21), sections.single().date)
    }

    @Test
    fun `an empty library produces no sections`() {
        assertTrue(DateGrouping.group(emptyList(), zone, today).isEmpty())
    }
}
