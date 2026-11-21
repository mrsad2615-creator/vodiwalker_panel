package dev.hgallery.core

/**
 * One row of media, as the gallery needs it.
 *
 * This is deliberately a plain value type: the Android layer maps a
 * `MediaStore` cursor into it, and every rule about ordering, grouping,
 * filtering and formatting then lives in this module, where it can be tested
 * without a device.
 */
data class MediaItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Epoch milliseconds in local time. */
    val dateTakenMs: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0L,
    val bucketId: Long = 0L,
    val bucketName: String = "",
    /** Contents of `MediaStore.MediaColumns.RELATIVE_PATH`, e.g. `DCIM/Camera/`. */
    val relativePath: String = "",
    val isFavorite: Boolean = false,
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/") || durationMs > 0L

    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    /** Aspect ratio guarded against degenerate metadata (some encoders report 0). */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}
