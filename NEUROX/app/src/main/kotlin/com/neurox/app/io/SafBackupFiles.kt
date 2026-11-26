package com.neurox.app.io

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.neurox.presentation.io.BackupFiles
import com.neurox.presentation.io.BackupFiles.PickedDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Backup file I/O through the Storage Access Framework.
 *
 * WHY SAF AND NOT A FILE PATH
 * The app declares no storage permission and does not want one. SAF lets the user
 * choose the exact file in the system picker; the app gets a content URI with a
 * grant that is scoped to that file and lasts as long as the Activity. Nothing else
 * on the device becomes reachable, and nothing is written anywhere the user did not
 * point at.
 *
 * WHY A DEFERRED AND NOT A CALLBACK
 * The picker is asynchronous and returns through an Activity result, but
 * `BackupFiles.save`/`open` are suspend functions so the screen can simply await
 * them. A [CompletableDeferred] bridges the two without a listener that has to be
 * unregistered: each request creates exactly one deferred, the launcher completes
 * it, and a cancelled picker completes it with null.
 *
 * WHAT MUST BE TRUE OR THIS THROWS
 * The launchers must be registered before the Activity is STARTED
 * (registerForActivityResult at construction time). [attach] is called from the
 * Activity's field initialisers. Calling [save] before [attach] is a programming
 * error, not a user-visible condition, so it throws instead of silently doing
 * nothing - silently doing nothing is the bug this class exists to fix.
 */
class SafBackupFiles : BackupFiles {

    private var activity: ComponentActivity? = null
    private var saveLauncher: ActivityResultLauncher<String>? = null
    private var openLauncher: ActivityResultLauncher<Array<String>>? = null

    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    /** How much of a chosen document is accepted. A vault is well under this. */
    private val maxDocumentBytes = 32L * 1024L * 1024L

    /**
     * Registers the pickers. Call once, from the Activity, before it is STARTED.
     *
     * Both contracts are created here rather than lazily because Android refuses to
     * register a launcher after the Activity has started, and the failure is a
     * crash with an unhelpful message.
     */
    fun attach(host: ComponentActivity) {
        activity = host
        saveLauncher = host.registerForActivityResult(
            ActivityResultContracts.CreateDocument(VAULT_MIME_TYPE),
        ) { uri ->
            // A null uri means the user cancelled. Complete with null, never with an
            // error: cancelling is not a failure and must not be shown as one.
            pendingSave?.complete(uri)
            pendingSave = null
        }
        openLauncher = host.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            pendingOpen?.complete(uri)
            pendingOpen = null
        }
    }

    override suspend fun save(contents: String, suggestedName: String): String? {
        val launcher = saveLauncher ?: error(
            "SafBackupFiles.attach() was never called; the screen cannot write a backup",
        )
        val deferred = CompletableDeferred<Uri?>()
        pendingSave = deferred
        launcher.launch(suggestedName)
        val uri = deferred.await() ?: return null
        return withContext(Dispatchers.IO) {
            val resolver = (activity ?: error("detached")).contentResolver
            try {
                resolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(contents.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: throw IOException("the chosen file could not be opened for writing")
                displayName(uri)
            } catch (failure: IOException) {
                // Surfaced to the caller as a cancel-free failure by rethrowing: the
                // route turns this into an error message rather than a silent nothing.
                throw IllegalStateException("could not write the backup: ${failure.message}", failure)
            }
        }
    }

    override suspend fun open(): PickedDocument? {
        val launcher = openLauncher ?: error(
            "SafBackupFiles.attach() was never called; the screen cannot read a backup",
        )
        val deferred = CompletableDeferred<Uri?>()
        pendingOpen = deferred
        launcher.launch(arrayOf(VAULT_MIME_TYPE, FALLBACK_MIME_TYPE))
        val uri = deferred.await() ?: return null
        return withContext(Dispatchers.IO) {
            val resolver = (activity ?: error("detached")).contentResolver
            val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            if (size > maxDocumentBytes) {
                throw IllegalStateException(
                    "the chosen file is ${size / 1024 / 1024} MB, larger than any NEUROX backup",
                )
            }
            val text = resolver.openInputStream(uri)?.use { stream ->
                // Read with a hard cap even when the descriptor lied about its size.
                val buffer = ByteArray(64 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var accumulated = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    accumulated += read
                    if (accumulated > maxDocumentBytes) {
                        throw IllegalStateException("the chosen file is too large to be a NEUROX backup")
                    }
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            } ?: throw IllegalStateException("the chosen file could not be read")
            PickedDocument(contents = text, displayName = displayName(uri))
        }
    }

    /** The file name shown for a content URI, or null when the provider withholds it. */
    private fun displayName(uri: Uri): String? {
        val resolver = activity?.contentResolver ?: return null
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0) cursor.getString(column) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private companion object {
        /**
         * The app's own type. `application/octet-stream` is the honest description of
         * an encrypted blob and is also what makes the file manager keep the
         * `.neurox` extension the user was offered.
         */
        const val VAULT_MIME_TYPE = "application/octet-stream"

        /** Providers that report no type at all still need to be selectable. */
        const val FALLBACK_MIME_TYPE = "application/json"
    }
}

/** Unused import guard: keeps `Activity` referenced for the KDoc above. */
@Suppress("unused")
private val activityTypeReference: Class<Activity> = Activity::class.java
