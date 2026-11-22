package dev.hgallery.app.data

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.hgallery.core.DeleteOutcome
import dev.hgallery.core.DeletePath
import dev.hgallery.core.deletePathFor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Everything that needs a `Context` to act on the device's media, kept out of the
 * UI so the screens stay testable.
 *
 * Two platform realities drive the shape of this class:
 *
 *  1. **Deleting other apps' media needs a user decision.** From API 29 the
 *     platform offers a trash request (recoverable, API 30+) and a delete request
 *     (permanent, API 29+). Both hand back a `PendingIntent` that only the
 *     activity can launch; the app cannot quietly delete someone else's photo.
 *  2. **Sharing needs a grant.** A `content://` URI handed to another app is
 *     useless without the read grant, and a batch share needs every URI in the
 *     clip data or only the first item arrives.
 */
class MediaActions(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    /**
     * Request codes for the system dialogs.
     *
     * Fresh on every call: two pending requests launched under the same code are
     * indistinguishable when the result comes back, and applying the wrong batch
     * to an outcome is how an item gets deleted that the user cancelled.
     */
    private val nextRequestCode = AtomicInteger(0x1F00)

    fun newRequestCode(): Int = nextRequestCode.incrementAndGet()

    /** Which mechanism this device offers for a delete. */
    fun pathFor(): DeletePath = deletePathFor(Build.VERSION.SDK_INT, canWriteDirectly = false)

    /**
     * The `IntentSender` that shows Android's own trash or delete dialog.
     *
     * Returns `null` when the platform has no such API (below 29), in which case
     * the caller falls back to deleting directly after its own confirmation.
     */
    fun buildDeleteRequest(uris: List<Uri>, path: DeletePath): IntentSender? {
        val pending: PendingIntent? = when {
            uris.isEmpty() -> null
            path == DeletePath.SYSTEM_TRASH && Build.VERSION.SDK_INT >= API_TRASH ->
                MediaStore.createTrashRequest(resolver, uris, true)
            path == DeletePath.SYSTEM_DELETE_REQUEST && Build.VERSION.SDK_INT >= API_DELETE_REQUEST ->
                MediaStore.createDeleteRequest(resolver, uris)
            else -> null
        }
        return pending?.intentSender
    }

    private companion object {
        /** `MediaStore.createDeleteRequest` arrived in Android 10 (API 29). */
        const val API_DELETE_REQUEST = 29

        /** `MediaStore.createTrashRequest` arrived in Android 11 (API 30). */
        const val API_TRASH = 30
    }

    /**
     * Applies a delete after the system (or the app's own dialog) has approved it.
     *
     * On API 30+ the trash request has already moved the items by the time the
     * result arrives, so the batch is reported as trashed — which is what lets the
     * UI say "can be restored". Below that the rows are removed here and counted
     * as permanent.
     */
    suspend fun applyDelete(uris: List<Uri>, path: DeletePath): DeleteOutcome =
        withContext(Dispatchers.IO) {
            when (path) {
                DeletePath.SYSTEM_TRASH -> DeleteOutcome(trashed = uris.size)
                else -> {
                    var done = 0
                    var failed = 0
                    for (uri in uris) {
                        val rows = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
                        if (rows > 0) done++ else failed++
                    }
                    DeleteOutcome(deleted = done, failed = failed)
                }
            }
        }

    /**
     * The system share sheet for a batch of media.
     *
     * The clip data carries every URI so a multi-selection shares all of it, and
     * the read flag is what lets the receiving app open the files at all.
     */
    fun buildShareIntent(uris: List<Uri>, mimeTypes: List<String>): Intent? {
        if (uris.isEmpty()) return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = sharedMimeType(mimeTypes)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newUri(resolver, "media", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).apply {
            // Launched from the app's own activity, so no NEW_TASK is needed; it is
            // set anyway for the case where a caller hands this to a service.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * One MIME type for the whole batch.
     *
     * A mixed batch has to be advertised as a wildcard or many targets refuse it,
     * while a single-family batch keeps its family so the right apps appear.
     */
    private fun sharedMimeType(mimeTypes: List<String>): String {
        val families = mimeTypes.map { it.substringBefore('/') + "/*" }.toSet()
        return when {
            families.isEmpty() -> "*/*"
            families.size == 1 -> families.first()
            else -> "*/*"
        }
    }
}
