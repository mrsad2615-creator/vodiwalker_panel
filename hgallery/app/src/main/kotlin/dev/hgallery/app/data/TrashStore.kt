package dev.hgallery.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.hgallery.core.MediaItem
import dev.hgallery.core.TrashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's own trash, on the platforms that have no system one.
 *
 * ## Why this exists at all
 *
 * From Android 11 the platform keeps a trash, and asking it to delete moves the file there
 * instead of removing it. Below Android 11 there is no trash: a delete is immediate. This
 * store gives those devices the same promise, by moving the file out of the media folders
 * into a private directory instead of deleting it.
 *
 * ## What it deliberately does not claim
 *
 * The copy in the private directory is in the app's own storage. It is not encrypted, it
 * is not hidden from the device's file manager, and uninstalling the app removes it with
 * the rest of the app's data. The Trash screen says so, because "moved to the trash" on a
 * phone where the app was uninstalled tomorrow must not be read as "safe forever".
 *
 * ## Consent
 *
 * Nothing here is skipped because it is convenient. Below Android 11, deleting media that
 * came from another app needs `WRITE_EXTERNAL_STORAGE`, which the manifest declares up to
 * API 29 for exactly this reason. When the write fails, the file is left alone and the
 * caller reports a failure: a trash that silently kept a file the user believed was gone
 * is worse than an error message.
 */
class TrashStore(private val context: Context) {

    /**
     * Moves media into the app's trash.
     *
     * Returns the entries that were stored. A file that could not be read is not in the
     * result, so the caller can count what actually happened instead of assuming.
     */
    suspend fun moveToTrash(items: List<MediaItem>): List<TrashEntry> = withContext(Dispatchers.IO) {
        items.mapNotNull { item -> runCatching { storeOne(item) }.getOrNull() }
    }

    private fun storeOne(item: MediaItem): TrashEntry? {
        val resolver = context.contentResolver
        val source = Uri.parse(item.uri)

        val folder = java.io.File(context.filesDir, TRASH_DIR).apply { mkdirs() }
        val target = java.io.File(folder, "${item.id}-${item.displayName}")

        // Copy first, then remove the original through MediaStore. The order matters: a
        // failure between the two leaves two copies, which the user can fix; the reverse
        // order would leave none.
        resolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        val rows = runCatching { resolver.delete(source, null, null) }.getOrDefault(0)
        if (rows <= 0) {
            // The copy is useless without the removal: keeping it would show an item in
            // Trash that is still in the library.
            target.delete()
            return null
        }

        return TrashEntry(
            itemId = item.id,
            fileName = item.displayName,
            storedPath = target.absolutePath,
            mimeType = item.mimeType,
            sizeBytes = target.length(),
            deletedAtMs = System.currentTimeMillis(),
            originalRelativePath = item.relativePath,
        )
    }

    /**
     * Puts an entry back into the library.
     *
     * The item is re-inserted through MediaStore so the platform indexes it and other apps
     * see it again. Returns true only when the insert produced a row; a partial copy is
     * removed so a failed restore does not leave a stray file behind.
     */
    suspend fun restore(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < API_Q) return@withContext false
        val file = java.io.File(entry.storedPath)
        if (!file.exists()) return@withContext false

        val target = MediaStore.MediaColumns
        val values = ContentValues().apply {
            put(target.DISPLAY_NAME, entry.fileName)
            put(target.MIME_TYPE, entry.mimeType)
            put(target.RELATIVE_PATH, entry.originalRelativePath.ifBlank { DEFAULT_FOLDER })
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = if (entry.mimeType.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val resolver = context.contentResolver
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
            ?: return@withContext false

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)

        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return@withContext false
        }

        runCatching {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
        file.delete()
        true
    }

    /** Removes one entry from the trash for good. */
    suspend fun deleteForever(entry: TrashEntry): Boolean = withContext(Dispatchers.IO) {
        java.io.File(entry.storedPath).delete()
    }

    /** Every stored entry, newest first, after dropping files that have gone missing. */
    suspend fun entries(): List<TrashEntry> = withContext(Dispatchers.IO) {
        val folder = java.io.File(context.filesDir, TRASH_DIR)
        folder.listFiles()
            ?.mapNotNull { file ->
                val name = file.name
                val id = name.substringBefore('-').toLongOrNull() ?: return@mapNotNull null
                TrashEntry(
                    itemId = id,
                    fileName = name.substringAfter('-', name),
                    storedPath = file.absolutePath,
                    mimeType = mimeTypeOf(file),
                    sizeBytes = file.length(),
                    deletedAtMs = file.lastModified(),
                    originalRelativePath = "",
                )
            }
            ?.sortedByDescending { it.deletedAtMs }
            .orEmpty()
    }

    /**
     * Guesses a type from the extension.
     *
     * The stored name keeps the original extension, so this is enough to re-insert the
     * file into the right collection. A file with no extension is treated as an image,
     * which is the common case; a wrong guess shows up as a failed restore, not as data
     * loss, because the copy is kept until the insert succeeds.
     */
    private fun mimeTypeOf(file: java.io.File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "mp4", "m4v", "mkv", "webm", "3gp", "mov" -> "video/$extension"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }
    }

    private companion object {
        /** The private directory. Named in one place so a cleanup script can find it. */
        const val TRASH_DIR = "trash"

        /** Android 10 and later can insert into MediaStore with `IS_PENDING`. */
        const val API_Q = 29

        /** Where a file goes back when its original folder is not known. */
        const val DEFAULT_FOLDER = "Pictures/HGallery/"
    }
}
