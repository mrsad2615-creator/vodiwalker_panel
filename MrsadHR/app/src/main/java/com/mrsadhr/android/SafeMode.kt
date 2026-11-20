package com.mrsadhr.android

import android.content.Context

/** Version stamped into reports and shown in the error screen. Kept next to the build number. */
object AppVersion {
    const val NAME = "1.0.2"
    const val CODE = 3
}

/**
 * Boot watchdog and safe mode.
 *
 * `boot_ok` is armed (false) at the very start of a launch and cleared (true) once the first frame
 * is on screen. If the process dies in between — an uncaught exception, a native abort, anything
 * the handler cannot see — the flag is still false on the next start, and the app boots in **safe
 * mode**: no audio, no notifications, no foreground service, no haptics, and no bundled fonts.
 *
 * That is the smallest configuration that can still draw the interface, and it turns an unfixable
 * "it keeps stopping" report into a usable app plus a machine-readable answer about which subsystem
 * the device rejects. Nothing is disabled permanently: a successful launch re-arms the watchdog and
 * the next start is full again.
 */
object SafeMode {

    private const val PREFS = "mrsadhr.boot"
    private const val KEY_BOOT_OK = "boot_ok"

    @Volatile
    var enabled: Boolean = false
        private set

    /** Non-fatal failures collected during this launch (a subsystem that refused to start). */
    private val notes = mutableListOf<String>()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var started = false

    /** Arms the watchdog. Called from `Application.onCreate`, before anything touches the graph. */
    fun begin(context: Context) {
        if (started) return
        started = true
        enabled = try {
            !prefs(context).getBoolean(KEY_BOOT_OK, true)
        } catch (_: Throwable) {
            false
        }
        try {
            prefs(context).edit().putBoolean(KEY_BOOT_OK, false).apply()
        } catch (_: Throwable) {
        }
    }

    /** The first frame is on screen: this launch is good, so disarm the watchdog. */
    fun markBooted(context: Context) {
        try {
            prefs(context).edit().putBoolean(KEY_BOOT_OK, true).apply()
        } catch (_: Throwable) {
        }
    }

    /** Forgets a recorded crash so the next launch is a normal one (used by "delete data" paths). */
    fun forget(context: Context) {
        try {
            prefs(context).edit().putBoolean(KEY_BOOT_OK, true).apply()
        } catch (_: Throwable) {
        }
        enabled = false
    }

    fun note(text: String) {
        if (notes.size < 8) notes.add(text)
    }

    val notesText: String get() = notes.joinToString("; ")

    /**
     * Builds an optional subsystem, degrading to [fallback] instead of failing the whole launch.
     * The reason is kept so it reaches the report.
     */
    inline fun <T> attempt(what: String, fallback: T, build: () -> T): T = try {
        build()
    } catch (error: Throwable) {
        note("$what failed: ${error.javaClass.simpleName}: ${error.message}")
        fallback
    }
}
