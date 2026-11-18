package ai.dana.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** The camera gets access only to this random, temporary cache file, not to the rest of app storage. */
internal class CameraPhotoFile private constructor(private val file: File, val uri: Uri) {
    fun hasContent(): Boolean = file.length() in 1..32_000_000L

    fun discard(context: Context) {
        runCatching { context.revokeUriPermission(uri, GRANTS) }
        runCatching { file.delete() }
    }

    companion object {
        private const val DIRECTORY = "camera-input"
        internal const val GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        fun reserve(context: Context): CameraPhotoFile {
            val dir = File(context.cacheDir, DIRECTORY)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Camera cache unavailable")
            val file = File.createTempFile("shot-", ".jpg", dir)
            return try {
                CameraPhotoFile(file, FileProvider.getUriForFile(context, "${context.packageName}.camera-photo", file))
            } catch (error: RuntimeException) {
                file.delete()
                throw error
            }
        }

        /** A killed process cannot recover a private preview; delete its orphaned temporary inputs. */
        fun discardOrphans(context: Context) {
            File(context.cacheDir, DIRECTORY).listFiles()?.filter { it.name.startsWith("shot-") && it.extension == "jpg" }?.forEach {
                runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera-photo", it)
                    CameraPhotoFile(it, uri).discard(context)
                }
            }
        }
    }
}

internal class FullPicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        clipData = ClipData.newRawUri("camera output", input)
        addFlags(CameraPhotoFile.GRANTS)
    }
}
