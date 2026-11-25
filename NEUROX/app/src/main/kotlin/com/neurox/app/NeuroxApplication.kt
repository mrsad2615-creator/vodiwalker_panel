package com.neurox.app

import android.app.Application
import com.neurox.app.di.AppContainer

/**
 * Process entry point.
 *
 * Deliberately minimal: build the container, expose it, and do nothing else. Startup
 * work is the most common source of jank and of "why does my first frame take 900
 * ms" reports, and this app's first screen is a measurement surface - a slow start
 * makes the whole product feel unreliable.
 *
 * Crash recovery is NOT done here. Sessions left RUNNING by a crash are closed by the
 * startup task in the repository layer, because that needs the active profile, which
 * is not known until the profile screen (or the restore path) has run.
 */
class NeuroxApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
