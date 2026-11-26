// NEUROX offline compile stubs - ContentResolver, used by SafBackupFiles.
package android.content

import android.database.Cursor
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import android.os.ParcelFileDescriptor

open class ContentResolver {
    open fun openInputStream(uri: Uri): InputStream? = null
    open fun openOutputStream(uri: Uri, mode: String = "w"): OutputStream? = null
    open fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? = null
    open fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null
}
