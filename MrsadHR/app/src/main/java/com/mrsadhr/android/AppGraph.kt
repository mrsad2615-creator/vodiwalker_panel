package com.mrsadhr.android

import android.content.Context
import com.mrsadhr.app.AppController
import com.mrsadhr.core.time.TimeSource

/** Monotonic device clock: never affected by the wall clock changing. */
class AndroidTimeSource : TimeSource {
    override fun monoMs(): Long = android.os.SystemClock.elapsedRealtime()
    override fun wallMs(): Long = System.currentTimeMillis()
}

/**
 * Process wide dependency graph. The activity owns the UI, but the foreground service needs the
 * very same controller instance to pause, reset or finish the countdown from a notification action,
 * so the graph is a singleton created once per process.
 */
class AppGraph private constructor(context: Context) {

    val controller: AppController
    val bridge: AndroidBridge
    val sounds: SoundPlayer
    val prefs: PrefsStore
    val haptics: Haptics

    init {
        val app = context.applicationContext
        prefs = PrefsStore(app)
        haptics = Haptics(app)
        sounds = SoundPlayer(app)
        bridge = AndroidBridge(app, sounds, haptics)
        controller = AppController(AndroidTimeSource(), prefs, bridge, sounds)
        bridge.attach(controller)
    }

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context.applicationContext).also { instance = it }
            }

        fun peek(): AppGraph? = instance
    }

    /** Persists everything immediately; called when the activity goes to the background. */
    fun flush() {
        prefs.flush()
    }
}
