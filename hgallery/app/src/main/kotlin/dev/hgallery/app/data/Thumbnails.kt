package dev.hgallery.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A small, explicit thumbnail store.
 *
 * Deliberately not an image-loading library: on a 3 GB POCO X3 Pro what matters
 * is that the decoded bitmap budget is capped in *bytes* and that each bitmap is
 * decoded at the size of its cell. A 4-column grid at 1200 px wide needs ~280 px
 * cells, so asking the platform for 256 px thumbnails keeps a screenful near
 * 12 MB instead of hundreds.
 *
 * The platform's own thumbnail cache does the disk work, so no second disk cache
 * is maintained here.
 */
class ThumbnailStore(
    private val context: Context,
    private val cellPx: Int = 256,
    maxBytes: Int = defaultBudget(),
) {

    private val memory = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun get(uri: String, isVideo: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri@$cellPx@$isVideo"
        memory.get(key)?.let { return@withContext it }

        val bitmap = runCatching { decode(uri) }.getOrNull()
        if (bitmap != null) memory.put(key, bitmap)
        bitmap
    }

    private fun decode(uri: String): Bitmap? {
        val parsed = Uri.parse(uri)
        if (Build.VERSION.SDK_INT >= 29) {
            // loadThumbnail handles both kinds and honours the requested size.
            return context.contentResolver.loadThumbnail(parsed, Size(cellPx, cellPx), null)
        }

        return context.contentResolver.openInputStream(parsed)?.use { stream ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, cellPx)
            context.contentResolver.openInputStream(parsed)?.use { second ->
                BitmapFactory.decodeStream(
                    second,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    },
                )
            }
        }
    }

    fun clear() = memory.evictAll()

    fun sizeBytes(): Int = memory.size()

    companion object {
        /** A quarter of the heap: enough for a screen and a half, never a crash. */
        fun defaultBudget(): Int {
            val heap = Runtime.getRuntime().maxMemory()
            return (heap / 4).coerceAtMost(48L * 1024 * 1024).toInt()
        }

        /**
         * Largest power of two that still leaves the decoded image at least
         * [target] pixels on its shorter side.
         */
        internal fun sampleSize(width: Int, height: Int, target: Int): Int {
            if (width <= 0 || height <= 0 || target <= 0) return 1
            var sample = 1
            while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
                sample *= 2
            }
            return sample
        }
    }
}
