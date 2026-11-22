package dev.hgallery.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
 *     platform offers `createTrashRequest` (recoverable, API 30+) and
 *     `createDeleteRequest` (permanent, API 29+). Both return a `PendingIntent`
 *     that the activity must launch; the app cannot simply delete. Rows the app
 *     owns can be deleted directly, but those are only the rows this app inserted.
 *  2. **Sharing needs a grant.** A `content://` URI handed to another app is
 *     useless without `FLAG_GRANT_READ_URI_PERMISSION`, and a batch share needs
 *     every URI in the clip data or only the first item arrives.
 */
class MediaActions(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    /**
     * The request code for the system delete/trash dialog.
     *
     * Fresh on every call: two pending requests launched with the same code are
     * indistinguishable when the result comes back, and applying the wrong batch
     * to the wrong outcome is how an item gets deleted that the user cancelled.
     */
    private val nextRequestCode = AtomicInteger(0x1F00)

    fun newRequestCode(): Int = nextRequestCode.incrementAndGet()

    /** Which mechanism this device offers for a delete. */
    fun pathFor(): DeletePath = deletePathFor(Build.VERSION.SDK_INT, canWriteDirectly = false)

    /**
     * Builds the intent that shows the system's own trash or delete dialog.
     *
     * Returns `null` when the platform has no such API (API < 29), in which case
     * the caller falls back to a direct delete.
     */
    fun buildDeleteRequest(uris: List<Uri>, path: DeletePath, requestCode: Int): Intent? {
        if (uris.isEmpty()) return null
        val pending = when (path) {
            DeletePath.SYSTEM_TRASH -> {
                if (Build.VERSION.SDK_INT < 30) return null
                android.provider.MediaStore.createTrashRequest(resolver, uris, true)
            }
            DeletePath.SYSTEM_DELETE_REQUEST -> {
                if (Build.VERSION.SDK_INT < 29) return null
                android.provider.MediaStore.createDeleteRequest(resolver, uris)
            }
            DeletePath.DIRECT_DELETE -> return null
        }
        return IntentSenderLauncher.wrap(pending, requestCode)
    }

    /**
     * Applies a delete after the system (or the app's own dialog) has approved it.
     *
     * On API 30+ the trash request has already done the work by the time the
     * result arrives, so this reports the batch as trashed. Below that, the rows
     * are removed here and counted as permanent.
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
     * `ClipData` carries every URI so a multi-selection shares all of it; the
     * read grant is what lets the receiving app actually open the files.
     */
    fun buildShareIntent(uris: List<Uri>, mimeTypes: List<String>): Intent? {
        if (uris.isEmpty()) return null
        val mime = sharedMimeType(mimeTypes)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newUri(resolver, "media", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).apply {
            // The chooser itself has to be launchable from a non-activity context.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * One MIME type for the whole batch.
     *
     * A mixed batch has to be advertised as `*​/*` or many targets refuse it,
     * while a single-type batch should keep its precise type so the right apps
     * appear in the sheet.
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

/**
 * `PendingIntent` → `IntentSender` without pulling `androidx.core` into the
 * signature of every caller.
 */
internal object IntentSenderLauncher {
    fun wrap(pending: android.app.PendingIntent, @Suppress("UNUSED_PARAMETER") requestCode: Int): Intent =
        android.content.Intent().apply {
            // The sender travels as an extra because an Intent cannot carry a
            // bare IntentSender; the activity pulls it back out by this key.
            putExtra(EXTRA_SENDER, pending.intentSender)
        }

    const val EXTRA_SENDER = "dev.hgallery.app.extra.SENDER"
}
