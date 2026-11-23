package dev.hgallery.app

import android.app.Application
import dev.hgallery.app.data.AppContainer

/**
 * Application entry point.
 *
 * No cloud component, no account, no network permission: the gallery reads the
 * device's own MediaStore and stores its own bookkeeping locally.
 *
 * The container is owned here rather than by a screen. Preferences are a single
 * DataStore file, and a second instance on the same file throws, so there must be
 * exactly one for the process.
 */
class HGalleryApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
