package com.mrsadhr.android

import android.app.Application

/**
 * Process entry point.
 *
 * Two jobs, both of which have to happen before any activity exists: install the crash handler, so
 * a failure while the activity itself is being created or inflated is still recorded, and arm the
 * boot watchdog that decides whether the next launch starts in safe mode.
 */
class MrsadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        SafeMode.begin(this)
    }
}
