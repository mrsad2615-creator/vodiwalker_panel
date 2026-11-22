package dev.hgallery.core

/** How the Albums screen orders its tiles. */
enum class AlbumSort { NAME, DATE, COUNT }

/** One album tile: a MediaStore bucket or one of the synthetic albums. */
data class Album(
    val id: Long,
    val name: String,
    val count: Int,
    val coverUri: String?,
    /** Newest item in the album; drives DATE sorting. */
    val latestMs: Long,
    val isSynthetic: Boolean = false,
)

/**
 * Album bookkeeping that Huawei Gallery performs in its Albums tab.
 *
 * The synthetic entries (`All photos`, `Videos`, `Favourites`, `Recently
 * deleted`) are not MediaStore buckets, so they are derived here rather than
 * queried twice from the platform.
 */
object AlbumBuilder {

    const val ALL_PHOTOS: Long = -1L
    const val VIDEOS: Long = -2L
    const val FAVORITES: Long = -3L
    const val RECENTLY_DELETED: Long = -4L

    fun fromItems(items: List<MediaItem>): List<Album> {
        val byBucket = items.groupBy { it.bucketId }
        val buckets = byBucket.map { (bucketId, bucketItems) ->
            val newest = bucketItems.maxByOrNull { it.dateTakenMs }
            Album(
                id = bucketId,
                name = newest?.bucketName?.takeIf { it.isNotBlank() } ?: "Unknown",
                count = bucketItems.size,
                coverUri = newest?.uri,
                latestMs = newest?.dateTakenMs ?: 0L,
            )
        }

        val images = items.filter { it.isImage || it.isVideo }
        val videos = items.filter { it.isVideo }
        val favourites = items.filter { it.isFavorite }
        val newest = items.maxByOrNull { it.dateTakenMs }

        val synthetic = listOf(
            Album(
                ALL_PHOTOS,
                "All photos",
                images.size,
                newest?.uri,
                newest?.dateTakenMs ?: 0L,
            ),
            Album(
                VIDEOS,
                "Videos",
                videos.size,
                videos.maxByOrNull { it.dateTakenMs }?.uri,
                videos.maxByOrNull { it.dateTakenMs }?.dateTakenMs ?: 0L,
            ),
            Album(
                FAVORITES,
                "Favourites",
                favourites.size,
                favourites.maxByOrNull { it.dateTakenMs }?.uri,
                favourites.maxByOrNull { it.dateTakenMs }?.dateTakenMs ?: 0L,
            ),
        )
        return synthetic + buckets
    }

    fun sort(albums: List<Album>, by: AlbumSort): List<Album> = when (by) {
        AlbumSort.NAME -> albums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        AlbumSort.DATE -> albums.sortedByDescending { it.latestMs }
        AlbumSort.COUNT -> albums.sortedByDescending { it.count }
    }

    /** Huawei hides empty albums; an album with no media is not worth a tile. */
    fun visible(albums: List<Album>): List<Album> = albums.filter { it.count > 0 }

    /**
     * Items of one album. Synthetic albums are answered from the full list so
     * the caller never has to know which kind it is looking at.
     */
    fun itemsOf(albums: List<Album>, allItems: List<MediaItem>, albumId: Long): List<MediaItem> =
        when (albumId) {
            ALL_PHOTOS -> allItems
            VIDEOS -> allItems.filter { it.isVideo }
            FAVORITES -> allItems.filter { it.isFavorite }
            RECENTLY_DELETED -> emptyList()
            else -> allItems.filter { it.bucketId == albumId }
        }
}
