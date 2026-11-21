package dev.hgallery.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A dated section of the timeline, as the Photos screen renders it. */
data class DateSection(
    val date: LocalDate,
    val items: List<MediaItem>,
    /** `Today`, `Yesterday`, or a formatted date. */
    val header: String,
)

/**
 * Timeline grouping.
 *
 * Huawei Gallery groups by calendar day and labels the newest days relatively.
 * Ties inside a day are broken by date-taken descending, then id descending so
 * that the order is stable across reloads.
 */
object DateGrouping {

    fun group(
        items: List<MediaItem>,
        zone: ZoneId,
        today: LocalDate = LocalDate.now(zone),
        headerFormatter: (LocalDate, String) -> String = { _, fallback -> fallback },
    ): List<DateSection> {
        val sorted = items.sortedWith(
            compareByDescending<MediaItem> { it.dateTakenMs }.thenByDescending { it.id },
        )

        val buckets = LinkedHashMap<LocalDate, MutableList<MediaItem>>()
        for (item in sorted) {
            val day = Instant.ofEpochMilli(item.dateTakenMs).atZone(zone).toLocalDate()
            buckets.getOrPut(day) { mutableListOf() }.add(item)
        }

        return buckets.entries
            .sortedByDescending { it.key }
            .map { (day, dayItems) ->
                DateSection(
                    date = day,
                    items = dayItems,
                    header = label(day, today, headerFormatter),
                )
            }
    }

    private fun label(
        day: LocalDate,
        today: LocalDate,
        headerFormatter: (LocalDate, String) -> String,
    ): String = when (ChronoUnit.DAYS.between(day, today)) {
        0L -> headerFormatter(day, "Today")
        1L -> headerFormatter(day, "Yesterday")
        else -> headerFormatter(day, day.toString())
    }
}
