package dev.hgallery.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A month section of the timeline. */
data class MonthSection(
    val month: YearMonth,
    val items: List<MediaItem>,
    /** `September 2026`, or the localised equivalent. */
    val header: String,
)

/**
 * The month level of the Photos timeline.
 *
 * Huawei's own Gallery manual states, for the Photos tab: *"zoom in or out to
 * switch between month and day view"*. This is the month half of that pair —
 * day grouping stays in [DateGrouping].
 *
 * Month view packs more items per row, so it keeps the same ordering rules as the
 * day view rather than inventing its own.
 */
object MonthGrouping {

    fun group(
        items: List<MediaItem>,
        zone: ZoneId,
        headerFormatter: (YearMonth) -> String,
    ): List<MonthSection> {
        val buckets = LinkedHashMap<YearMonth, MutableList<MediaItem>>()
        val sorted = items.sortedWith(
            compareByDescending<MediaItem> { it.dateTakenMs }.thenByDescending { it.id },
        )
        for (item in sorted) {
            val month = YearMonth.from(
                Instant.ofEpochMilli(item.dateTakenMs).atZone(zone).toLocalDate(),
            )
            buckets.getOrPut(month) { mutableListOf() }.add(item)
        }
        return buckets.entries
            .sortedByDescending { it.key }
            .map { (month, monthItems) ->
                MonthSection(month = month, items = monthItems, header = headerFormatter(month))
            }
    }

    /** Convenience for callers that only need the date, not the sectioning. */
    fun monthOf(item: MediaItem, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(item.dateTakenMs).atZone(zone).toLocalDate())

    fun isSameDay(a: Long, b: Long, zone: ZoneId): Boolean {
        val dayA: LocalDate = Instant.ofEpochMilli(a).atZone(zone).toLocalDate()
        val dayB: LocalDate = Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
        return dayA == dayB
    }
}
