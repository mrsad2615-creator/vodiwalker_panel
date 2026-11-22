package dev.hgallery.core

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
