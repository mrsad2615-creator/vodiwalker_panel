package dev.hgallery.app.data

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import dev.hgallery.core.ExifFormat
import dev.hgallery.core.ExifSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the camera facts of one photograph.
 *
 * ## Why it is not part of the media query
 *
 * EXIF lives inside the file, not in the media index. Reading it means opening the file,
 * which is tens of milliseconds per item and cannot be done for a whole library. It is
 * therefore read on demand, when the details sheet opens, on the IO dispatcher.
 *
 * ## Why failure is normal here
 *
 * A PNG has no EXIF. A screenshot has none. A file may have been written by an app that
 * stored none. None of these is an error, so a missing or unreadable block returns an
 * empty summary and the sheet says the file has no camera information.
 */
class ExifReader(private val context: Context) {

    /**
     * Reads [uri].
     *
     * `ExifInterface` needs a file path or a stream. A MediaStore URI has no path the app
     * may open directly on modern Android, so the stream form is used.
     */
    suspend fun read(uri: String): ExifSummary = withContext(Dispatchers.IO) {
        val stream = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))
        }.getOrNull() ?: return@withContext ExifSummary()

        stream.use { input ->
            runCatching {
                val exif = ExifInterface(input)
                ExifSummary(
                    camera = ExifFormat.camera(
                        exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty(),
                        exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
                    ),
                    // Files write a lens model, not a lens maker. Where only the maker
                    // exists, showing it is better than showing nothing, but the model is
                    // what a photographer recognises.
                    lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)
                        ?.takeIf { it.isNotBlank() }
                        ?: exif.getAttribute(ExifInterface.TAG_LENS_MAKE).orEmpty(),
                    aperture = ExifFormat.aperture(
                        exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0),
                    ),
                    shutter = ExifFormat.shutter(
                        exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0),
                    ),
                    iso = ExifFormat.iso(
                        exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED, 0),
                    ),
                    focalLength = ExifFormat.focalLength(
                        exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0),
                    ),
                    orientationDegrees = orientationDegrees(exif),
                )
            }.getOrDefault(ExifSummary())
        }
    }

    /**
     * The rotation the viewer must apply, in degrees clockwise.
     *
     * `ExifInterface` reports orientation as one of eight named constants; only the four
     * rotations are needed here. A mirrored value is treated as its rotation, which is
     * what the viewer can render without a flip transform.
     */
    private fun orientationDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
            ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
            ExifInterface.ORIENTATION_ROTATE_270 -> THREE_QUARTERS_TURN
            else -> 0
        }

    private companion object {
        /** The three rotations EXIF can name, in degrees clockwise. */
        const val QUARTER_TURN = 90
        const val HALF_TURN = 180
        const val THREE_QUARTERS_TURN = 270
    }
}
