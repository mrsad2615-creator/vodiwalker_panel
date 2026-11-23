package dev.hgallery.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Scoped-storage permission handling.
 *
 * Three regimes have to be supported:
 *  - API 26–32: `READ_EXTERNAL_STORAGE`
 *  - API 33+:   granular `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`
 *  - API 34+:   the user may grant access to *selected* items only, which is a
 *               legitimate state, not an error, and must not be treated as one.
 */
object MediaPermissions {

    /** Android 14: partial ("selected photos") access became a real state. */
    private const val API_UPSIDE_DOWN_CAKE = 34

    /** Android 13: the granular READ_MEDIA_* permissions replaced one grant. */
    private const val API_TIRAMISU = 33

    /** Android 11: writes through `MediaStore` stopped needing a storage permission. */
    private const val API_R = 30

    /** Result of asking the platform whether the gallery may read media. */
    enum class State {
        GRANTED,
        PARTIAL,
        DENIED,
    }

    val required: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= API_UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= API_TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun state(context: Context): State {
        val granted = required.filter { has(context, it) }

        if (granted.size == required.size) return State.GRANTED

        // A partial selection on Android 14+ still lets the app show something,
        // so it is reported separately instead of as a flat denial.
        if (Build.VERSION.SDK_INT >= API_UPSIDE_DOWN_CAKE &&
            has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            return State.PARTIAL
        }
        return State.DENIED
    }

    fun canReadAnything(context: Context): Boolean = state(context) != State.DENIED

    /**
     * Whether the app may write to the library.
     *
     * Two different answers, for a reason. From Android 11 a `MediaStore` update needs no
     * permission for media the app did not create only when the write is done through the
     * platform's own consent dialog; a direct update without that dialog needs the
     * library to allow it. The gallery therefore asks this before offering a rotate and
     * reports a failure rather than promising an edit that the platform refuses.
     *
     * Below Android 11 a write needs `WRITE_EXTERNAL_STORAGE`, which the manifest declares
     * up to API 29.
     */
    fun canWrite(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= API_R) {
            canReadAnything(context)
        } else {
            has(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
