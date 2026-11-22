package dev.hgallery.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.hgallery.core.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's own media through MediaStore.
 *
 * Only the columns the UI actually renders are projected: a full row on a
 * 20 000-photo library costs measurable time and memory on this class of
 * hardware, and none of the extra columns are used.
 */
class MediaRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    private val projection = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.DATE_TAKEN)
        add(MediaStore.MediaColumns.DATE_ADDED)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        add(MediaStore.MediaColumns.BUCKET_ID)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        add(MediaStore.MediaColumns.DURATION)
        add(MediaStore.MediaColumns.RELATIVE_PATH)
        if (Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.IS_FAVORITE)
    }.toTypedArray()

    /** Images and videos, newest first, with metadata already normalised. */
    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= API_Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val mediaType = MediaStore.Files.FileColumns.MEDIA_TYPE
        val selection = "($mediaType=? OR $mediaType=?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val items = ArrayList<MediaItem>(INITIAL_CAPACITY)
        resolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns._ID} DESC",
        )
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val favCol = if (Build.VERSION.SDK_INT >= API_R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    // DATE_TAKEN is 0 for plenty of files (screenshots, downloads,
                    // anything copied without EXIF); DATE_ADDED is the sane fallback.
                    val taken = cursor.getLong(takenCol).takeIf { it > 0L }
                        ?: (cursor.getLong(addedCol) * MILLIS_PER_SECOND)

                    items += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        displayName = cursor.getString(nameCol) ?: "unknown",
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        sizeBytes = cursor.getLong(sizeCol),
                        dateTakenMs = taken,
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        durationMs = cursor.getLong(durationCol),
                        bucketId = cursor.getLong(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol) ?: "Other",
                        relativePath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else "",
                        isFavorite = favCol >= 0 && cursor.getInt(favCol) == 1,
                    )
                }
            }
        items
    }

    suspend fun delete(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var removed = 0
        for (uri in uris) {
            val row = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            if (row > 0) removed++
        }
        removed
    }

    suspend fun setFavorite(uri: Uri, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < API_R) return@withContext false
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
        }
        runCatching { resolver.update(uri, values, null, null) > 0 }.getOrDefault(false)
    }

    private companion object {
        /** Android 10: scoped storage and the `VOLUME_EXTERNAL` content URI. */
        const val API_Q = 29

        /** Android 11: `IS_FAVORITE`, trash, and no write permission needed. */
        const val API_R = 30

        /** `DATE_ADDED` is in seconds; every other column here is milliseconds. */
        const val MILLIS_PER_SECOND = 1000L

        /** Typical library size, so the list does not reallocate while filling. */
        const val INITIAL_CAPACITY = 512
    }
}
