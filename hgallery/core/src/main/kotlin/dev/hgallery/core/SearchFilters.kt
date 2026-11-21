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

object SearchFilters {

    /**
     * Applies a query. The text matches the display name and the album name, as
     * the reference searches filenames while typing.
     */
    fun apply(items: List<MediaItem>, query: SearchQuery): List<MediaItem> {
        if (query.isEmpty) return items
        val needle = query.text.trim()

        return items.filter { item ->
            val matchesText = needle.isEmpty() ||
                item.displayName.contains(needle, ignoreCase = true) ||
                item.bucketName.contains(needle, ignoreCase = true)
            val matchesType = when {
                query.imagesOnly && query.videosOnly -> true
                query.imagesOnly -> item.isImage
                query.videosOnly -> item.isVideo
                else -> true
            }
            val matchesSize = (query.minBytes?.let { item.sizeBytes >= it } ?: true) &&
                (query.maxBytes?.let { item.sizeBytes <= it } ?: true)
            val matchesDate = (query.fromMs?.let { item.dateTakenMs >= it } ?: true) &&
                (query.toMs?.let { item.dateTakenMs <= it } ?: true)
            val matchesAlbum = query.albumId?.let { item.bucketId == it } ?: true
            val matchesFavourite = !query.favouritesOnly || item.isFavorite

            matchesText && matchesType && matchesSize && matchesDate && matchesAlbum && matchesFavourite
        }
    }

    /** "12 results" style summary. */
    fun summary(count: Int): String = if (count == 1) "1 result" else "$count results"
}
