package dev.hgallery.core

/**
 * Builds the content of the Highlights tab.
 *
 * ## What a highlight is here
 *
 * A highlight is a *place and time*: one calendar day, in one place, that produced enough
 * photographs to be worth revisiting. That is the grouping a user recognises, because it
 * matches how a trip or an evening actually happened.
 *
 * ## Where the place comes from
 *
 * The album (bucket) name, which MediaStore already carries. Nothing is reverse-geocoded
 * and no coordinate leaves the device — the app has no network permission to do it with.
 * When a bucket name is missing, the day still forms a highlight, and the title falls back
 * to the date.
 *
 * ## Why a minimum count
 *
 * A single photograph is a memory, not a highlight. Two is a comparison. Three in one day
 * in one place is an occasion, and that is the threshold used.
 */
object HighlightsBuilder {

    /** Days with fewer items than this do not form a highlight. */
    const val MIN_ITEMS = 3

    /** How many highlights are shown. Beyond this the tab stops being a summary. */
    const val MAX_HIGHLIGHTS = 12

    /** One day in one place. */
    data class Highlight(
        /** Stable key: the day and the bucket, so recomposition cannot reshuffle it. */
        val key: String,
        val title: String,
        val subtitle: String,
        /** Newest item, used as the cover. */
        val cover: MediaItem?,
        val itemCount: Int,
        val items: List<MediaItem>,
        /** Epoch millis of the day, for ordering. */
        val dayMs: Long,
    )

    /**
     * Groups the library into highlights, newest first.
     *
     * [zone] is passed in rather than read from the clock inside: a day boundary is a local
     * question, and a test must be able to fix it.
     */
    fun build(items: List<MediaItem>, zone: java.time.ZoneId, limit: Int = MAX_HIGHLIGHTS): List<Highlight> {
        if (items.isEmpty()) return emptyList()

        val groups = items.groupBy { item ->
            val date = java.time.Instant.ofEpochMilli(item.dateTakenMs).atZone(zone).toLocalDate()
            date to item.bucketId
        }

        return groups.entries
            .filter { (_, groupItems) -> groupItems.size >= MIN_ITEMS }
            .map { (key, groupItems) ->
                val (date, bucketId) = key
                val newest = groupItems.maxByOrNull { it.dateTakenMs }
                val place = newest?.bucketName?.takeIf { it.isNotBlank() }
                Highlight(
                    key = "$date-$bucketId",
                    title = place ?: date.toString(),
                    subtitle = date.toString(),
                    cover = newest,
                    itemCount = groupItems.size,
                    items = groupItems.sortedWith(
                        compareByDescending<MediaItem> { it.dateTakenMs }.thenByDescending { it.id },
                    ),
                    dayMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                )
            }
            .sortedByDescending { it.dayMs }
            .take(limit)
    }
}
