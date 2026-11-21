package dev.hgallery.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Application entry point.
 *
 * The gallery follows the phone's own locale for its strings, but the layout
 * direction and the type ramp are handled by the design system, so nothing is
 * forced here beyond an explicit default for the very first frame.
 */
class HGalleryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: HGalleryApplication
            private set
    }
}
