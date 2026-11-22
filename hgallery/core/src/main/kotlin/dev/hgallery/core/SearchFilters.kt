package dev.hgallery.core

/** One mebibyte, the unit these thresholds are expressed in. */
private const val MIB = 1024L * 1024L

/**
 * The size buckets the search screen offers.
 *
 * Nobody searches a photo library for "larger than 524288 bytes", so the screen
 * asks in the terms people use — small, medium, large — and this maps those to
 * byte bounds. The thresholds are chosen from what a phone camera and a chat app
 * actually produce: a compressed screenshot and a shared sticker sit under 1 MiB,
 * a 12 MP camera frame is 2–5 MiB, and anything past 15 MiB is a long 4K video or
 * an edited panorama.
 */
enum class SizeBucket(val minBytes: Long?, val maxBytes: Long?) {
    SMALL(null, MIB),
    MEDIUM(MIB, 15L * MIB),
    LARGE(15L * MIB, null),
}

/** How far back the date buckets reach. */
enum class DateBucket(val spanMs: Long) {
    TODAY(24L * 60L * 60L * 1000L),
    WEEK(7L * 24L * 60L * 60L * 1000L),
    MONTH(31L * 24L * 60L * 60L * 1000L),
    YEAR(366L * 24L * 60L * 60L * 1000L),
}

/**
 * The query one can build on the search screen, as opposed to a full [SearchQuery].
 *
 * The screen offers one choice per axis, because that is what the reference's
 * chip row does: a user picks "Videos" or "Photos", not both. `SearchQuery` keeps
 * the richer model for callers that need it; this type exists so the screen cannot
 * construct a contradictory query in the first place, and so turning it into a
 * `SearchQuery` is one tested function rather than logic in a composable.
 */
data class SearchChips(
    val text: String = "",
    val videosOnly: Boolean = false,
    val imagesOnly: Boolean = false,
    val favouritesOnly: Boolean = false,
    val albumId: Long? = null,
    val size: SizeBucket? = null,
    val within: DateBucket? = null,
) {
    val isEmpty: Boolean
        get() = text.isBlank() && !videosOnly && !imagesOnly && !favouritesOnly &&
            albumId == null && size == null && within == null

    /** True when anything beyond the text field is set, which the chip row shows. */
    val hasFilters: Boolean
        get() = videosOnly || imagesOnly || favouritesOnly || albumId != null ||
            size != null || within != null

    /**
     * The byte and date bounds, resolved against a clock.
     *
     * `nowMs` is a parameter rather than a call to the system clock so the
     * conversion is testable at a fixed instant; a "last 7 days" query that reads
     * the clock itself can only be tested by waiting.
     */
    fun toQuery(nowMs: Long): SearchQuery = SearchQuery(
        text = text,
        imagesOnly = imagesOnly,
        videosOnly = videosOnly,
        minBytes = size?.minBytes,
        maxBytes = size?.maxBytes,
        fromMs = within?.let { nowMs - it.spanMs },
        albumId = albumId,
        favouritesOnly = favouritesOnly,
    )
}

/** The axes Huawei Gallery's search screen offers. */
data class SearchQuery(
    val text: String = "",
    val imagesOnly: Boolean = false,
    val videosOnly: Boolean = false,
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null,
    val albumId: Long? = null,
    val favouritesOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = text.isBlank() && !imagesOnly && !videosOnly && minBytes == null &&
            maxBytes == null && fromMs == null && toMs == null && albumId == null && !favouritesOnly
}

/**
 * Applies a search query to a list of media.
 *
 * Each axis is its own predicate function rather than one long expression. The
 * previous single `filter { ... }` had cyclomatic complexity 27, and the reason
 * that matters is not the number: a six-term boolean chain with no names is where
 * a "size AND date" query quietly becomes "size OR date" during a later edit.
 */
object SearchFilters {

    /**
     * The text axis: the display name and the album name, matched while typing.
     * A blank needle matches everything, so an empty query is not a filter.
     */
    fun matchesText(item: MediaItem, needle: String): Boolean =
        needle.isEmpty() ||
            item.displayName.contains(needle, ignoreCase = true) ||
            item.bucketName.contains(needle, ignoreCase = true)

    /** The type axis. Asking for both kinds is not a constraint. */
    fun matchesType(item: MediaItem, imagesOnly: Boolean, videosOnly: Boolean): Boolean = when {
        imagesOnly && videosOnly -> true
        imagesOnly -> item.isImage
        videosOnly -> item.isVideo
        else -> true
    }

    /** A null bound is an open end. */
    fun matchesSize(item: MediaItem, minBytes: Long?, maxBytes: Long?): Boolean =
        (minBytes == null || item.sizeBytes >= minBytes) &&
            (maxBytes == null || item.sizeBytes <= maxBytes)

    fun matchesDate(item: MediaItem, fromMs: Long?, toMs: Long?): Boolean =
        (fromMs == null || item.dateTakenMs >= fromMs) &&
            (toMs == null || item.dateTakenMs <= toMs)

    fun matchesAlbum(item: MediaItem, albumId: Long?): Boolean =
        albumId == null || item.bucketId == albumId

    fun matchesFavourite(item: MediaItem, favouritesOnly: Boolean): Boolean =
        !favouritesOnly || item.isFavorite

    /** Every axis is an AND: narrowing a search narrows the result. */
    fun apply(items: List<MediaItem>, query: SearchQuery): List<MediaItem> {
        if (query.isEmpty) return items
        val needle = query.text.trim()
        return items.filter { item -> matches(item, query, needle) }
    }

    private fun matches(item: MediaItem, query: SearchQuery, needle: String): Boolean =
        matchesText(item, needle) &&
            matchesType(item, query.imagesOnly, query.videosOnly) &&
            matchesSize(item, query.minBytes, query.maxBytes) &&
            matchesDate(item, query.fromMs, query.toMs) &&
            matchesAlbum(item, query.albumId) &&
            matchesFavourite(item, query.favouritesOnly)

    /** "12 results" style summary. */
    fun summary(count: Int): String = if (count == 1) "1 result" else "$count results"
}
