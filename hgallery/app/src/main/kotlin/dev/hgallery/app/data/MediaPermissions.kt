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

    /** Result of asking the platform whether the gallery may read media. */
    enum class State {
        GRANTED,
        PARTIAL,
        DENIED,
    }

    val required: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
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
        if (Build.VERSION.SDK_INT >= 34 && has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
            return State.PARTIAL
        }
        return State.DENIED
    }

    fun canReadAnything(context: Context): Boolean = state(context) != State.DENIED

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
