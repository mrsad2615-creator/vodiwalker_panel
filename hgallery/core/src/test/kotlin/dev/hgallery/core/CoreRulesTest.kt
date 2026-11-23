package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * A test fixture builder rather than a method with eight default parameters.
     *
     * Eight parameters crossed the project's limit, and the limit was the right
     * call: `item(1, size = 5)` and `item(1, mime = "video/mp4")` compiled happily
     * while testing something nobody had intended.
     */
    /**
     * A fixed instant, so a "last 7 days" assertion does not depend on when the
     * suite happens to run.
     */
    private val nowMs = 1_767_225_600_000L

    private class ItemBuilder(val id: Long) {
        var name: String = "IMG_$id.jpg"
        var bucket: String = "Camera"
        var mime: String = "image/jpeg"
        var size: Long = 1000
        var date: Long = 1_000_000
        var favourite: Boolean = false
        var duration: Long = 0
    }

    private fun item(id: Long, build: ItemBuilder.() -> Unit = {}): MediaItem {
        val b = ItemBuilder(id).apply(build)
        return MediaItem(
            id = b.id,
            uri = "content://media/${b.id}",
            displayName = b.name,
            mimeType = b.mime,
            sizeBytes = b.size,
            dateTakenMs = b.date,
            width = 100,
            height = 100,
            durationMs = b.duration,
            bucketId = 7,
            bucketName = b.bucket,
            isFavorite = b.favourite,
        )
    }

    @Test
    fun `an empty query returns everything`() {
        val items = listOf(item(1), item(2))
        assertEquals(items, SearchFilters.apply(items, SearchQuery()))
    }

    @Test
    fun `text matches the file name case-insensitively`() {
        val items = listOf(item(1) { name = "Beach.JPG" }, item(2) { name = "dog.jpg" })
        assertEquals(listOf(1L), SearchFilters.apply(items, SearchQuery(text = "beach")).map { it.id })
    }

    @Test
    fun `text also matches the album name`() {
        val items = listOf(item(1) { bucket = "Screenshots" }, item(2) { bucket = "Camera" })
        assertEquals(listOf(1L), SearchFilters.apply(items, SearchQuery(text = "screen")).map { it.id })
    }

    @Test
    fun `type, size and favourites filters compose`() {
        val items = listOf(
            item(1) {
                mime = "video/mp4"
                duration = 5_000L
                size = 9_000L
            },
            item(2) {
                size = 9_000L
                favourite = true
            },
            item(3) { size = 10L },
        )
        val videos = SearchFilters.apply(items, SearchQuery(videosOnly = true))
        assertEquals(listOf(1L), videos.map { it.id })

        val largeFavourites = SearchFilters.apply(items, SearchQuery(minBytes = 5000, favouritesOnly = true))
        assertEquals(listOf(2L), largeFavourites.map { it.id })
    }

    @Test
    fun `date window is inclusive on both ends`() {
        val items = listOf(item(1) { date = 100L }, item(2) { date = 200L }, item(3) { date = 300L })
        val hit = SearchFilters.apply(items, SearchQuery(fromMs = 100, toMs = 200))
        assertEquals(listOf(1L, 2L), hit.map { it.id })
    }

    @Test
    fun `details name the file in terms a person uses`() {
        val photo = item(1) { mime = "image/jpeg"; size = 2L * 1024L * 1024L }
        val rows = detailRows(photo) { w, h -> "$w x $h" }.associate { it.label to it.value }

        assertEquals("IMG_1.jpg", rows["Name"])
        assertEquals("2.00 MB", rows["Size"])
        assertEquals("100 x 100", rows["Dimensions"])
        assertEquals("JPEG image", rows["Type"])
        // A JPEG is not a video, so it must not claim a duration.
        assertFalse(rows.containsKey("Duration"))
    }

    @Test
    fun `details say nothing rather than nothing-useful`() {
        // A row that reads "0.0 MP" or "Folder " is worse than an absent row: the
        // sheet is a list of facts, and a placeholder is not a fact.
        val unknown = item(2) { mime = "image/heic" }
        val rows = detailRows(unknown) { w, h -> "$w x $h" }

        assertTrue(rows.none { it.value.isBlank() })
        assertEquals("HEIC image", typeLabel("image/heic"))
        assertFalse(megapixels(unknown).startsWith("0.0"))
    }

    @Test
    fun `a video reports its duration and a still does not`() {
        val clip = item(3) { mime = "video/mp4"; duration = 6_000L }
        val rows = detailRows(clip) { w, h -> "$w x $h" }.associate { it.label to it.value }
        // detail() is the sheet's form ("6 s"), not the tile badge's ("0:06").
        assertEquals("6 s", rows["Duration"])
        assertEquals("MP4 video", rows["Type"])
    }

    @Test
    fun `summary pluralises`() {
        assertEquals("1 result", SearchFilters.summary(1))
        assertEquals("0 results", SearchFilters.summary(0))
    }

    @Test
    fun `size buckets are contiguous and leave nothing out`() {
        // A photo is in exactly one bucket. If the bounds disagreed by a byte, a
        // file could vanish from a search that asked for its own size.
        assertEquals(SizeBucket.SMALL.maxBytes, SizeBucket.MEDIUM.minBytes)
        assertEquals(SizeBucket.MEDIUM.maxBytes, SizeBucket.LARGE.minBytes)
        val query = SearchChips(size = SizeBucket.LARGE).toQuery(nowMs)
        val large = item(1) { size = 40L * 1024L * 1024L }
        val small = item(2) { size = 200L * 1024L }
        assertEquals(listOf(1L), SearchFilters.apply(listOf(large, small), query).map { it.id })
    }

    @Test
    fun `a date bucket reaches back from the clock it is given`() {
        // The conversion takes `now` rather than reading the system clock, which is
        // the only way "last 7 days" can be asserted at all.
        val weekAgo = nowMs - 7L * 24L * 60L * 60L * 1000L
        val query = SearchChips(within = DateBucket.WEEK).toQuery(nowMs)
        assertEquals(weekAgo, query.fromMs)
        val recent = item(1) { date = nowMs - 60_000L }
        val old = item(2) { date = weekAgo - 1L }
        assertEquals(listOf(1L), SearchFilters.apply(listOf(recent, old), query).map { it.id })
    }

    @Test
    fun `a chip query is empty until a chip is chosen`() {
        assertTrue(SearchChips().isEmpty)
        assertFalse(SearchChips().hasFilters)
        // Text alone is not a "filter" for the chip row, but it does make the query
        // non-empty, so typing shows results without claiming to be filtering.
        assertFalse(SearchChips(text = "lake").isEmpty)
        assertFalse(SearchChips(text = "lake").hasFilters)
        assertTrue(SearchChips(videosOnly = true).hasFilters)
        assertFalse(SearchChips(videosOnly = true).isEmpty)
    }

    @Test
    fun `chips narrow together rather than replace one another`() {
        // A favourites-only, video-only, recent query has to satisfy all three.
        val query = SearchChips(
            videosOnly = true,
            favouritesOnly = true,
            within = DateBucket.WEEK,
        ).toQuery(nowMs)
        val yes = item(1) {
            mime = "video/mp4"
            favourite = true
            date = nowMs - 1_000L
        }
        val notFavourite = item(2) {
            mime = "video/mp4"
            favourite = false
            date = nowMs - 1_000L
        }
        val tooOld = item(3) {
            mime = "video/mp4"
            favourite = true
            date = nowMs - 30L * 24L * 60L * 60L * 1000L
        }
        assertEquals(
            listOf(1L),
            SearchFilters.apply(listOf(yes, notFavourite, tooOld), query).map { it.id },
        )
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
        // "All photos" includes videos, exactly as the reference album does:
        // it is every item on the device, not only the stills.
        assertEquals(4, AlbumBuilder.itemsOf(items, AlbumBuilder.ALL_PHOTOS).size)
        assertEquals(listOf(3L), AlbumBuilder.itemsOf(items, AlbumBuilder.VIDEOS).map { it.id })
        assertEquals(listOf(4L), AlbumBuilder.itemsOf(items, AlbumBuilder.FAVORITES).map { it.id })
        assertEquals(listOf(1L, 2L), AlbumBuilder.itemsOf(items, 10L).map { it.id })
    }
}
