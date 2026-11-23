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

class GridDensityTest {

    @Test
    fun `a spread shows fewer columns and a pinch shows more`() {
        assertEquals(3, GridDensity.afterPinch(startColumns = 4, scale = 1.4f))
        assertEquals(6, GridDensity.afterPinch(startColumns = 4, scale = 0.7f))
    }

    @Test
    fun `no movement keeps the current density`() {
        assertEquals(4, GridDensity.afterPinch(startColumns = 4, scale = 1f))
    }

    @Test
    fun `the gesture cannot leave the supported range`() {
        assertEquals(2, GridDensity.afterPinch(startColumns = 2, scale = 8f))
        assertEquals(6, GridDensity.afterPinch(startColumns = 6, scale = 0.1f))
    }

    @Test
    fun `a broken scale does not change the grid`() {
        assertEquals(4, GridDensity.afterPinch(startColumns = 4, scale = 0f))
        assertEquals(4, GridDensity.afterPinch(startColumns = 4, scale = Float.NaN))
    }

    @Test
    fun `a stored value outside the range is clamped`() {
        assertEquals(2, GridDensity.clamp(1))
        assertEquals(6, GridDensity.clamp(9))
        assertEquals(4, GridDensity.clamp(4))
    }
}

class HighlightsBuilderTest {

    private val zone = java.time.ZoneId.of("UTC")

    private fun item(
        id: Long,
        dayMs: Long,
        bucket: Long = 10L,
        name: String = "Camera",
        size: Long = 2_000_000L,
        favourite: Boolean = false,
    ) = MediaItem(
        id = id,
        uri = "content://media/external/images/media/$id",
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = size,
        dateTakenMs = dayMs,
        width = 4000,
        height = 3000,
        bucketId = bucket,
        bucketName = name,
        isFavorite = favourite,
    )

    private fun millisAt(day: Int): Long =
        java.time.LocalDate.of(2025, 3, day).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `a day with three items becomes a highlight`() {
        val items = listOf(item(1, millisAt(4)), item(2, millisAt(4) + 1), item(3, millisAt(4) + 2))
        val highlights = HighlightsBuilder.build(items, zone)
        assertEquals(1, highlights.size)
        assertEquals(3, highlights.single().itemCount)
        assertEquals("Camera", highlights.single().title)
    }

    @Test
    fun `a day with two items does not`() {
        val items = listOf(item(1, millisAt(4)), item(2, millisAt(4) + 1))
        assertTrue(HighlightsBuilder.build(items, zone).isEmpty())
    }

    @Test
    fun `two places on one day stay apart`() {
        val items = listOf(
            item(1, millisAt(4), bucket = 10L, name = "Camera"),
            item(2, millisAt(4) + 1, bucket = 10L, name = "Camera"),
            item(3, millisAt(4) + 2, bucket = 10L, name = "Camera"),
            item(4, millisAt(4) + 3, bucket = 20L, name = "Screenshots"),
            item(5, millisAt(4) + 4, bucket = 20L, name = "Screenshots"),
            item(6, millisAt(4) + 5, bucket = 20L, name = "Screenshots"),
        )
        val highlights = HighlightsBuilder.build(items, zone)
        assertEquals(2, highlights.size)
        assertEquals(setOf("Camera", "Screenshots"), highlights.map { it.title }.toSet())
    }

    @Test
    fun `the newest highlight comes first`() {
        val old = (1L..3L).map { item(it, millisAt(2) + it) }
        val recent = (4L..6L).map { item(it, millisAt(9) + it) }
        val highlights = HighlightsBuilder.build(old + recent, zone)
        assertEquals("2025-03-09", highlights.first().subtitle)
    }

    @Test
    fun `a nameless bucket still forms a highlight`() {
        val items = (1L..3L).map { item(it, millisAt(4) + it, bucket = 0L, name = "") }
        val highlights = HighlightsBuilder.build(items, zone)
        assertEquals("2025-03-04", highlights.single().title)
    }
}

class DiscoverBuilderTest {

    private val zone = java.time.ZoneId.of("UTC")
    private val today = java.time.LocalDate.of(2025, 3, 4)

    private fun item(
        id: Long,
        date: java.time.LocalDate,
        year: Int = 2025,
        size: Long = 500_000L,
        favourite: Boolean = false,
    ) = MediaItem(
        id = id,
        uri = "content://media/external/images/media/$id",
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = size,
        dateTakenMs = java.time.LocalDate.of(year, date.month, date.dayOfMonth)
            .atStartOfDay(zone).toInstant().toEpochMilli(),
        width = 100,
        height = 100,
        isFavorite = favourite,
    )

    @Test
    fun `on this day only looks at earlier years`() {
        val items = listOf(
            item(1, today),
            item(2, today, year = 2024),
            item(3, java.time.LocalDate.of(2024, 3, 5)),
        )
        val content = DiscoverBuilder.build(items, today, zone)
        assertEquals(listOf(2L), content.onThisDay.map { it.id })
    }

    @Test
    fun `sections with nothing in them are dropped`() {
        val content = DiscoverBuilder.build(emptyList(), today, zone)
        assertTrue(content.isEmpty)
    }

    @Test
    fun `years are newest first and counted`() {
        val items = listOf(
            item(1, today, year = 2025),
            item(2, today, year = 2024),
            item(3, today, year = 2024),
        )
        val years = DiscoverBuilder.build(items, today, zone).years
        assertEquals(listOf("year-2025", "year-2024"), years.map { it.key })
        assertEquals(2, years.first { it.key == "year-2024" }.itemCount)
    }

    @Test
    fun `small files are not called large`() {
        val items = listOf(item(1, today, size = 900_000L), item(2, today, size = 4_000_000L))
        val largest = DiscoverBuilder.build(items, today, zone).largest
        assertEquals(listOf(2L), largest.map { it.id })
    }
}

class VideoQueueTest {

    private fun video(id: Long, day: Long) = MediaItem(
        id = id,
        uri = "content://media/external/video/media/$id",
        displayName = "VID_$id.mp4",
        mimeType = "video/mp4",
        sizeBytes = 1_000_000L,
        dateTakenMs = day,
        width = 1920,
        height = 1080,
        durationMs = 60_000L,
    )

    private fun image(id: Long, day: Long) = MediaItem(
        id = id,
        uri = "content://media/external/images/media/$id",
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1_000L,
        dateTakenMs = day,
        width = 100,
        height = 100,
    )

    @Test
    fun `only videos enter the queue, newest first`() {
        val queue = VideoQueue.build(listOf(video(1, 100), image(9, 500), video(2, 300)))
        assertEquals(listOf(2L, 1L), queue.map { it.id })
    }

    @Test
    fun `next and previous follow the queue`() {
        // Newest first, so the queue is 3, 2, 1. The neighbour of 2 is 3 behind it and
        // 1 in front of it; the first version of this test had the two swapped.
        val queue = VideoQueue.build(listOf(video(1, 100), video(2, 200), video(3, 300)))
        assertEquals(2L, VideoQueue.next(queue, 3L)?.id)
        assertEquals(3L, VideoQueue.previous(queue, 2L)?.id)
        assertEquals(1L, VideoQueue.next(queue, 2L)?.id)
    }

    @Test
    fun `the ends of the queue do not wrap`() {
        val queue = VideoQueue.build(listOf(video(1, 100), video(2, 200)))
        assertEquals(null, VideoQueue.next(queue, 1L))
        assertEquals(null, VideoQueue.previous(queue, 2L))
    }

    @Test
    fun `an unknown id has no neighbours`() {
        val queue = VideoQueue.build(listOf(video(1, 100)))
        assertEquals(null, VideoQueue.next(queue, 99L))
        assertEquals(-1, VideoQueue.indexOf(queue, 99L))
    }
}

class AlbumRulesTest {

    private fun album(id: Long, name: String, ids: List<Long> = emptyList()) =
        CustomAlbum(id = id, name = name, itemIds = ids)

    @Test
    fun `a name is trimmed and inner runs of spaces collapse`() {
        assertEquals("Lake trip", AlbumRules.clean("  Lake    trip  "))
    }

    @Test
    fun `a name of spaces is not valid`() {
        assertFalse(AlbumRules.isValidName("   "))
        assertTrue(AlbumRules.isValidName("Trips"))
    }

    @Test
    fun `a long name is cut to the limit`() {
        val long = "a".repeat(80)
        assertEquals(AlbumRules.MAX_NAME_LENGTH, AlbumRules.clean(long).length)
    }

    @Test
    fun `a duplicate name is refused and the album keeps its own`() {
        val albums = listOf(album(1, "Trips"), album(2, "Work"))
        assertTrue(AlbumRules.isTaken("trips", albums))
        assertTrue(AlbumRules.isTaken("  TRIPS ", albums))
        assertFalse(AlbumRules.isTaken("trips", albums, exceptId = 1))
        assertFalse(AlbumRules.isTaken("Family", albums))
    }

    @Test
    fun `ids are not reused after a delete`() {
        val albums = listOf(album(1, "One"), album(7, "Seven"))
        assertEquals(8L, AlbumRules.nextId(albums))
        assertEquals(1L, AlbumRules.nextId(emptyList()))
    }

    @Test
    fun `adding the same item twice keeps one entry`() {
        val once = AlbumRules.addItem(album(1, "Trips"), 5L)
        val twice = AlbumRules.addItem(once, 5L)
        assertEquals(listOf(5L), twice.itemIds)
    }

    @Test
    fun `pruning drops ids that are no longer in the library`() {
        val album = album(1, "Trips", listOf(1L, 2L, 3L))
        assertEquals(listOf(1L, 3L), AlbumRules.prune(album, setOf(1L, 3L)).itemIds)
    }
}

class TrashPolicyTest {

    private fun entry(deletedAtMs: Long) = TrashEntry(
        itemId = 1L,
        fileName = "IMG_1.jpg",
        storedPath = "/data/user/0/dev.hgallery.app/files/trash/1-IMG_1.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 1_000L,
        deletedAtMs = deletedAtMs,
    )

    private val now = 1_800_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `an entry inside the period is active`() {
        val entries = listOf(entry(now - 5 * day))
        assertEquals(1, TrashPolicy.active(entries, now).size)
        assertTrue(TrashPolicy.expired(entries, now).isEmpty())
    }

    @Test
    fun `an entry past thirty days has expired`() {
        val entries = listOf(entry(now - 31 * day))
        assertEquals(1, TrashPolicy.expired(entries, now).size)
        assertTrue(TrashPolicy.active(entries, now).isEmpty())
    }

    @Test
    fun `exactly thirty days counts as expired`() {
        val entries = listOf(entry(now - 30 * day))
        assertEquals(1, TrashPolicy.expired(entries, now).size)
    }

    @Test
    fun `days left counts part of a day as a day`() {
        assertEquals(30, TrashPolicy.daysLeft(entry(now), now))
        assertEquals(2, TrashPolicy.daysLeft(entry(now - 28 * day - 1), now))
        assertEquals(0, TrashPolicy.daysLeft(entry(now - 40 * day), now))
    }

    @Test
    fun `active entries come newest first`() {
        val entries = listOf(entry(now - 9 * day), entry(now - 2 * day))
        assertEquals(
            listOf(now - 2 * day, now - 9 * day),
            TrashPolicy.active(entries, now).map { it.deletedAtMs },
        )
    }
}

class ExifFormatTest {

    @Test
    fun `aperture has one decimal below ten and none above`() {
        assertEquals("f/1.8", ExifFormat.aperture(1.8))
        assertEquals("f/11", ExifFormat.aperture(11.0))
        assertEquals("", ExifFormat.aperture(0.0))
    }

    @Test
    fun `a short exposure reads as a fraction and a long one as seconds`() {
        assertEquals("1/125 s", ExifFormat.shutter(1.0 / 125))
        assertEquals("2 s", ExifFormat.shutter(2.0))
        assertEquals("", ExifFormat.shutter(0.0))
    }

    @Test
    fun `sensitivity and focal length omit missing values`() {
        assertEquals("ISO 100", ExifFormat.iso(100))
        assertEquals("", ExifFormat.iso(0))
        assertEquals("26 mm", ExifFormat.focalLength(26.0))
        assertEquals("1.5 mm", ExifFormat.focalLength(1.5))
        assertEquals("", ExifFormat.focalLength(0.0))
    }

    @Test
    fun `the maker is not repeated when the model already starts with it`() {
        assertEquals("Canon EOS R6", ExifFormat.camera("Canon", "Canon EOS R6"))
        assertEquals("Canon EOS R6", ExifFormat.camera("", "Canon EOS R6"))
        assertEquals("Canon", ExifFormat.camera("Canon", ""))
        assertEquals("Sony A7", ExifFormat.camera("Sony", "A7"))
    }

    @Test
    fun `an empty summary reports itself empty`() {
        assertTrue(ExifSummary().isEmpty)
        assertFalse(ExifSummary(camera = "Canon").isEmpty)
    }
}

class ExifOrientationTest {

    @Test
    fun `stored values map to the rotation the viewer applies`() {
        assertEquals(0, ExifOrientation.degrees(1))
        assertEquals(90, ExifOrientation.degrees(6))
        assertEquals(180, ExifOrientation.degrees(3))
        assertEquals(270, ExifOrientation.degrees(8))
    }

    @Test
    fun `a quarter turn moves to the next stored value`() {
        assertEquals(6, ExifOrientation.rotate(1, 90))
        assertEquals(3, ExifOrientation.rotate(6, 90))
        assertEquals(8, ExifOrientation.rotate(3, 90))
        assertEquals(1, ExifOrientation.rotate(8, 90))
    }

    @Test
    fun `four quarter turns come back to the start`() {
        assertEquals(1, ExifOrientation.rotate(1, 360))
        assertEquals(6, ExifOrientation.rotate(6, 360))
    }

    @Test
    fun `a mirrored value is treated as its rotation`() {
        // 5 is a mirrored 90: the viewer cannot flip, so the rotation is kept.
        assertEquals(6, ExifOrientation.rotate(5, 0))
    }
}

class CalendarLabelsTest {

    @Test
    fun `persian asks for the persian calendar and everything else for gregorian`() {
        assertEquals(HgCalendar.PERSIAN, CalendarLabels.calendarFor("fa"))
        assertEquals(HgCalendar.PERSIAN, CalendarLabels.calendarFor("fa-IR"))
        assertEquals(HgCalendar.GREGORIAN, CalendarLabels.calendarFor("en-US"))
        assertEquals(HgCalendar.GREGORIAN, CalendarLabels.calendarFor("de"))
    }

    @Test
    fun `a header inside the year omits the year and one outside keeps it`() {
        assertEquals("d MMMM", CalendarLabels.dayPattern(includeYear = false))
        assertEquals("d MMMM yyyy", CalendarLabels.dayPattern(includeYear = true))
    }

    @Test
    fun `a persian date is converted by the app, not by a locale extension`() {
        // `java.time` accepts `-u-ca-persian` and then ignores it, so the conversion is
        // done by the app. This asserts the locale carries no calendar promise it cannot
        // keep, and that the conversion gives the right numbers.
        val locale = CalendarLabels.localeFor("fa-IR")
        assertEquals("fa", locale.language)
        assertFalse(locale.toLanguageTag().contains("ca-persian"))
        val persian = PersianCalendar.fromGregorian(java.time.LocalDate.of(2025, 3, 21))
        assertEquals(1404, persian.year)
        assertEquals(1, persian.month)
        assertEquals(1, persian.day)
    }

    @Test
    fun `a gregorian language keeps its own locale`() {
        // The language and the region, both: `Locale.GERMAN` is `de` with no country, so
        // comparing against it would fail on a locale that is right.
        val german = CalendarLabels.localeFor("de-DE")
        assertEquals("de", german.language)
        assertEquals("DE", german.country)
    }
}

class CalendarYearTest {

    // Persian year 1404 began on 21 March 2025: the boundary is between these two days.
    private val lastDayOf1403 = java.time.LocalDate.of(2025, 3, 20)
    private val firstDayOf1404 = java.time.LocalDate.of(2025, 3, 21)

    @Test
    fun `two dates in one gregorian year share a gregorian year`() {
        assertTrue(CalendarLabels.sameYear(lastDayOf1403, firstDayOf1404, HgCalendar.GREGORIAN))
    }

    @Test
    fun `the same two dates fall in different persian years`() {
        // A header that compared `LocalDate.year` would print both without a year and
        // hide the turn of the year.
        assertFalse(CalendarLabels.sameYear(lastDayOf1403, firstDayOf1404, HgCalendar.PERSIAN))
    }

    @Test
    fun `a date is always in its own year`() {
        assertTrue(CalendarLabels.sameYear(lastDayOf1403, lastDayOf1403, HgCalendar.PERSIAN))
        assertTrue(CalendarLabels.sameYear(firstDayOf1404, firstDayOf1404, HgCalendar.GREGORIAN))
    }
}

/**
 * The Persian calendar, checked against dates whose Persian year is known.
 *
 * Each case is a year boundary or a well-known day, so a failure names the rule that
 * broke rather than only a number: a wrong March start shifts every date in the year.
 */
class PersianCalendarTest {

    private fun p(date: java.time.LocalDate): PersianDate = PersianCalendar.fromGregorian(date)

    @Test
    fun `the new year falls on the day the year starts`() {
        assertEquals(PersianDate(1403, 1, 1), p(java.time.LocalDate.of(2024, 3, 20)))
        assertEquals(PersianDate(1404, 1, 1), p(java.time.LocalDate.of(2025, 3, 21)))
        assertEquals(PersianDate(1405, 1, 1), p(java.time.LocalDate.of(2026, 3, 21)))
        assertEquals(PersianDate(1400, 1, 1), p(java.time.LocalDate.of(2021, 3, 21)))
    }

    @Test
    fun `the last day of a year is the day before the next new year`() {
        assertEquals(PersianDate(1403, 12, 30), p(java.time.LocalDate.of(2025, 3, 20)))
    }

    @Test
    fun `a date in january belongs to the year that began the march before`() {
        // The bug this catches: searching March of the same Gregorian year put the year
        // start after the date, and the conversion returned the wrong year.
        assertEquals(PersianDate(1378, 10, 11), p(java.time.LocalDate.of(2000, 1, 1)))
        assertEquals(PersianDate(1405, 9, 1), p(java.time.LocalDate.of(2026, 11, 22)))
    }

    @Test
    fun `a historic date keeps its well-known persian day`() {
        assertEquals(PersianDate(1357, 11, 22), p(java.time.LocalDate.of(1979, 2, 11)))
    }

    @Test
    fun `the month lengths follow the rule`() {
        // 1 Farvardin plus 186 days is 1 Mehr; the first six months are 31 days each.
        val mehr = p(java.time.LocalDate.of(2024, 3, 20).plusDays(186))
        assertEquals(7, mehr.month)
        assertEquals(1, mehr.day)
    }

    @Test
    fun `a persian year is leap when the count of leaps changes`() {
        // 1403 is a leap year: it has 366 days, and 30 Esfand exists in it.
        assertTrue(PersianCalendar.isLeapYear(1403))
        assertFalse(PersianCalendar.isLeapYear(1404))
    }

    @Test
    fun `every day of a year round-trips through the conversion`() {
        // A full year of dates, every day, checked against the day count of the year it
        // lands in. A one-day shift in the year start shows up here on day one, and it is
        // the check that would have caught the doubled base offset before a build.
        var date = java.time.LocalDate.of(2024, 3, 20)
        val end = java.time.LocalDate.of(2025, 3, 20)
        var previous = PersianCalendar.fromGregorian(date)
        var seen = 1
        date = date.plusDays(1)
        while (!date.isAfter(end)) {
            val current = PersianCalendar.fromGregorian(date)
            // The next day is the same month's next day, or the next month's first day.
            val sameMonthNextDay = current.month == previous.month &&
                current.day == previous.day + 1
            val nextMonth = (current.month == previous.month + 1 && current.day == 1) ||
                (previous.month == 12 && current.month == 1 && current.day == 1)
            assertTrue(
                sameMonthNextDay || nextMonth,
                "day broke at $date: $previous then $current",
            )
            assertTrue(current.day in 1..31, "day out of range at $date: $current")
            previous = current
            seen += 1
            date = date.plusDays(1)
        }
        // 20 March 2024 to 20 March 2025 inclusive is one full Persian year plus one day.
        assertEquals(366, seen)
    }

    @Test
    fun `every month name has a key`() {
        val names = PERSIAN_MONTH_KEYS
        assertEquals(12, names.size)
        assertEquals("farvardin", names.first())
        assertEquals("esfand", names.last())
        assertEquals("esfand", PersianDate(1403, 12, 1).monthKey)
    }
}
