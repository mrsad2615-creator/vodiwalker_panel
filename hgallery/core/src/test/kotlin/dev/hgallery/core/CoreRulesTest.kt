package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DurationFormatterTest {

    @Test
    fun `badges use minutes below an hour`() {
        assertEquals("0:00", DurationFormatter.badge(0))
        assertEquals("0:07", DurationFormatter.badge(7_400))
        assertEquals("1:42", DurationFormatter.badge(102_000))
    }

    @Test
    fun `badges gain an hour field past sixty minutes`() {
        assertEquals("1:02:03", DurationFormatter.badge(3_723_000))
    }

    @Test
    fun `detail rows read naturally`() {
        assertEquals("42 s", DurationFormatter.detail(42_000))
        assertEquals("1 min 42 s", DurationFormatter.detail(102_000))
        assertEquals("1 h 02 min", DurationFormatter.detail(3_723_000))
    }
}

class SimilarityDetectorTest {

    @Test
    fun `identical hashes have no distance`() {
        assertEquals(0, SimilarityDetector.hammingDistance(0b1011L, 0b1011L))
    }

    @Test
    fun `distance counts differing bits`() {
        assertEquals(3, SimilarityDetector.hammingDistance(0b0000L, 0b1011L))
    }

    @Test
    fun `near-identical pictures group together and unrelated ones do not`() {
        val hashes = mapOf(
            1L to 0b00000000L,
            2L to 0b00000001L, // 1 bit away from 1
            3L to -1L, // all 64 bits set: 63-64 bits away from 1 and 2
        )

        val groups = SimilarityDetector.groupSimilar(hashes, threshold = 6)

        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().toSet())
    }

    @Test
    fun `a burst of three collapses into one group rather than pairs`() {
        val hashes = mapOf(1L to 0b0000L, 2L to 0b0001L, 3L to 0b0011L)
        val groups = SimilarityDetector.groupSimilar(hashes, threshold = 2)
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().size)
    }

    @Test
    fun `a unique library yields no groups`() {
        assertTrue(SimilarityDetector.groupSimilar(mapOf(1L to 0L, 2L to -1L)).isEmpty())
    }

    @Test
    fun `exact duplicates group by content hash`() {
        val groups = SimilarityDetector.groupExact(
            mapOf(1L to "aaa", 2L to "aaa", 3L to "bbb"),
        )
        assertEquals(listOf(listOf(1L, 2L)), groups)
    }
}

class SearchFiltersTest {

    private fun item(
        id: Long,
        name: String = "IMG_$id.jpg",
        bucket: String = "Camera",
        mime: String = "image/jpeg",
        size: Long = 1000,
        date: Long = 1_000_000,
        favourite: Boolean = false,
        duration: Long = 0,
    ) = MediaItem(
        id = id,
        uri = "content://media/$id",
        displayName = name,
        mimeType = mime,
        sizeBytes = size,
        dateTakenMs = date,
        width = 100,
        height = 100,
        durationMs = duration,
        bucketId = bucket.hashCode().toLong(),
        bucketName = bucket,
        isFavorite = favourite,
    )

    @Test
    fun `an empty query returns everything`() {
        val items = listOf(item(1), item(2))
        assertEquals(items, SearchFilters.apply(items, SearchQuery()))
    }

    @Test
    fun `text matches the file name case-insensitively`() {
        val items = listOf(item(1, name = "Beach.JPG"), item(2, name = "dog.jpg"))
        assertEquals(listOf(1L), SearchFilters.apply(items, SearchQuery(text = "beach")).map { it.id })
    }

    @Test
    fun `text also matches the album name`() {
        val items = listOf(item(1, bucket = "Screenshots"), item(2, bucket = "Camera"))
        assertEquals(listOf(1L), SearchFilters.apply(items, SearchQuery(text = "screen")).map { it.id })
    }

    @Test
    fun `type, size and favourites filters compose`() {
        val items = listOf(
            item(1, mime = "video/mp4", duration = 5000, size = 9_000),
            item(2, mime = "image/jpeg", size = 9_000, favourite = true),
            item(3, mime = "image/jpeg", size = 10),
        )
        val videos = SearchFilters.apply(items, SearchQuery(videosOnly = true))
        assertEquals(listOf(1L), videos.map { it.id })

        val largeFavourites = SearchFilters.apply(items, SearchQuery(minBytes = 5000, favouritesOnly = true))
        assertEquals(listOf(2L), largeFavourites.map { it.id })
    }

    @Test
    fun `date window is inclusive on both ends`() {
        val items = listOf(item(1, date = 100), item(2, date = 200), item(3, date = 300))
        val hit = SearchFilters.apply(items, SearchQuery(fromMs = 100, toMs = 200))
        assertEquals(listOf(1L, 2L), hit.map { it.id })
    }

    @Test
    fun `summary pluralises`() {
        assertEquals("1 result", SearchFilters.summary(1))
        assertEquals("0 results", SearchFilters.summary(0))
    }
}

class AlbumBuilderTest {

    private fun item(id: Long, bucket: Long, name: String, date: Long, video: Boolean = false, fav: Boolean = false) =
        MediaItem(
            id = id,
            uri = "content://media/$id",
            displayName = "f$id",
            mimeType = if (video) "video/mp4" else "image/jpeg",
            sizeBytes = 10,
            dateTakenMs = date,
            width = 1,
            height = 1,
            durationMs = if (video) 1000 else 0,
            bucketId = bucket,
            bucketName = name,
            isFavorite = fav,
        )

    private val items = listOf(
        item(1, 10, "Camera", 300),
        item(2, 10, "Camera", 100),
        item(3, 20, "Screenshots", 200, video = true),
        item(4, 20, "Screenshots", 250, fav = true),
    )

    @Test
    fun `synthetic albums count what they say they count`() {
        val albums = AlbumBuilder.fromItems(items).associateBy { it.id }
        assertEquals(4, albums.getValue(AlbumBuilder.ALL_PHOTOS).count)
        assertEquals(1, albums.getValue(AlbumBuilder.VIDEOS).count)
        assertEquals(1, albums.getValue(AlbumBuilder.FAVORITES).count)
    }

    @Test
    fun `an album cover is its newest item`() {
        val camera = AlbumBuilder.fromItems(items).first { it.id == 10L }
        assertEquals("content://media/1", camera.coverUri)
        assertEquals(2, camera.count)
    }

    @Test
    fun `sorting by name is case-insensitive and by count is descending`() {
        val albums = AlbumBuilder.fromItems(items)
        assertEquals(listOf("All photos", "Camera", "Favourites", "Screenshots", "Videos"),
            AlbumBuilder.sort(albums, AlbumSort.NAME).map { it.name })
        assertEquals(4, AlbumBuilder.sort(albums, AlbumSort.COUNT).first().count)
    }

    @Test
    fun `empty albums are hidden`() {
        val albums = AlbumBuilder.fromItems(items)
        assertTrue(AlbumBuilder.visible(albums).none { it.count == 0 })
    }

    @Test
    fun `itemsOf answers synthetic and real albums alike`() {
        val albums = AlbumBuilder.fromItems(items)
        // "All photos" includes videos, exactly as the reference album does:
        // it is every item on the device, not only the stills.
        assertEquals(4, AlbumBuilder.itemsOf(albums, items, AlbumBuilder.ALL_PHOTOS).size)
        assertEquals(listOf(3L), AlbumBuilder.itemsOf(albums, items, AlbumBuilder.VIDEOS).map { it.id })
        assertEquals(listOf(4L), AlbumBuilder.itemsOf(albums, items, AlbumBuilder.FAVORITES).map { it.id })
        assertEquals(listOf(1L, 2L), AlbumBuilder.itemsOf(albums, items, 10L).map { it.id })
    }
}
