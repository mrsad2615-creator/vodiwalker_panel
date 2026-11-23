package dev.hgallery.core

/**
 * Builds the content of the Discover tab.
 *
 * ## What Discover is, and what it is not
 *
 * Discover answers one question: *what is actually in my library?* It is built only from
 * rows that exist on the device. There is no seed data, no suggestion service, no
 * placeholder tile, and no network call — the app has no network permission at all.
 *
 * ## Why "on this day" is a section and not a notification
 *
 * It is the one grouping a user cannot produce by scrolling, because it needs a date
 * match across years. It costs one pass over the library.
 *
 * ## Why the sections are ordered this way
 *
 * Most-browsed first: a device with four years of media has more to say about its years
 * than about one day. A device with two weeks of media has nothing to say about its years
 * and everything to say about today, so an empty section is dropped rather than shown as
 * a heading with no content.
 */
object DiscoverBuilder {

    /** A named group of items, with the photograph that represents it. */
    data class Section(
        val key: String,
        val itemCount: Int,
        /** The newest item in the group, used as its cover. */
        val cover: MediaItem?,
        /** Items in the order they should be shown, newest first. */
        val items: List<MediaItem>,
    )

    /** Everything Discover knows how to show, before empty sections are removed. */
    data class Content(
        val onThisDay: List<MediaItem>,
        val favourites: List<MediaItem>,
        val years: List<Section>,
        val largest: List<MediaItem>,
    ) {
        /** True when there is nothing at all to show, so the screen can say so. */
        val isEmpty: Boolean
            get() = onThisDay.isEmpty() && favourites.isEmpty() && years.isEmpty() &&
                largest.isEmpty()
    }

    /** How many years back Discover looks. Beyond this the section is noise. */
    const val MAX_YEARS = 8

    /** How many items Discover shows per row. Enough to scroll, small enough to load. */
    const val MAX_ITEMS_PER_SECTION = 24

    /**
     * The size a file must reach to appear under "Largest".
     *
     * One megabyte: below it every thumbnail on the device would be listed, and the
     * section would stop answering the question it exists for.
     */
    const val LARGE_FILE_BYTES = 1_000_000L

    /**
     * Builds Discover from the library.
     *
     * [today] is passed in rather than read from the clock so the result is testable and
     * so a device with a wrong date cannot produce a section that changes between two
     * compositions of the same screen.
     *
     * [zone] is needed because "on this day" is a local-calendar question: an item taken
     * at 23:00 in one zone is the next day in another.
     */
    fun build(
        items: List<MediaItem>,
        today: java.time.LocalDate,
        zone: java.time.ZoneId,
    ): Content {
        if (items.isEmpty()) return Content(emptyList(), emptyList(), emptyList(), emptyList())

        val newestFirst = items.sortedWith(
            compareByDescending<MediaItem> { it.dateTakenMs }.thenByDescending { it.id },
        )

        val onThisDay = newestFirst
            .filter { item ->
                val date = java.time.Instant.ofEpochMilli(item.dateTakenMs)
                    .atZone(zone)
                    .toLocalDate()
                date.month == today.month && date.dayOfMonth == today.dayOfMonth &&
                    date.year < today.year
            }
            .take(MAX_ITEMS_PER_SECTION)

        val favourites = newestFirst.filter { it.isFavorite }.take(MAX_ITEMS_PER_SECTION)

        val years = newestFirst
            .groupBy { java.time.Instant.ofEpochMilli(it.dateTakenMs).atZone(zone).year }
            .entries
            .sortedByDescending { it.key }
            .take(MAX_YEARS)
            .map { (year, yearItems) ->
                Section(
                    key = "year-$year",
                    itemCount = yearItems.size,
                    cover = yearItems.firstOrNull(),
                    items = yearItems.take(MAX_ITEMS_PER_SECTION),
                )
            }

        // Largest files, which is the one list that answers "what is taking my storage".
        // Only files over a megabyte: listing every tiny thumbnail as "large" is noise.
        val largest = items
            .filter { it.sizeBytes >= LARGE_FILE_BYTES }
            .sortedByDescending { it.sizeBytes }
            .take(MAX_ITEMS_PER_SECTION)

        return Content(onThisDay, favourites, years, largest)
    }
}
