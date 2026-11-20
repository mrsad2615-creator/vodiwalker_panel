package com.mrsadhr.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.mrsadhr.app.Action
import com.mrsadhr.core.model.Language

/**
 * Foreground service that keeps the countdown alive and visible while the app is in the background.
 *
 * The service is deliberately dumb: the engine owns the deadline, the notification owns the
 * self-updating chronometer, and this class only (a) keeps the process in the foreground while a
 * timer runs and (b) wakes exactly once, at the deadline, to play the completion alert.
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var notifications: Notifications? = null
    private var deadlineAt: Long = 0L

    private val deadlineRunnable = Runnable { onDeadline() }

    override fun onCreate() {
        super.onCreate()
        notifications = Notifications(this).also { it.ensureChannels() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> start(intent)
            ACTION_PAUSE -> {
                val controller = AppGraph.peek()?.controller
                controller?.dispatch(Action.TimerPause)
                stopOngoing()
            }
            ACTION_RESET -> {
                val controller = AppGraph.peek()?.controller
                controller?.dispatch(Action.TimerReset)
                stopOngoing()
            }
            ACTION_STOP -> stopOngoing()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent?) {
        val durationMs = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L
        val remainingMs = intent?.getLongExtra(EXTRA_REMAINING, 0L) ?: 0L
        if (durationMs <= 0L) {
            stopOngoing()
            return
        }
        val notification = notifications!!.running(durationMs, remainingMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_RUNNING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifications.ID_RUNNING, notification)
        }
        // One precise wake-up at zero: no polling, no drift, no battery cost in between.
        deadlineAt = SystemClock.elapsedRealtime() + remainingMs.coerceAtLeast(0L)
        handler.removeCallbacks(deadlineRunnable)
        handler.postDelayed(deadlineRunnable, remainingMs.coerceAtLeast(0L) + 20L)
    }

    private fun onDeadline() {
        val graph = AppGraph.peek() ?: run { stopOngoing(); return }
        // The controller detects the expired deadline, fires the haptic, the sound and its own
        // notification through the bridge, and moves the UI into the completion state.
        graph.controller.tick()
        stopOngoing()
    }

    private fun stopOngoing() {
        handler.removeCallbacks(deadlineRunnable)
        deadlineAt = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notifications?.cancelRunning()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(deadlineRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.mrsadhr.timer.START"
        const val ACTION_PAUSE = "com.mrsadhr.timer.PAUSE"
        const val ACTION_RESET = "com.mrsadhr.timer.RESET"
        const val ACTION_STOP = "com.mrsadhr.timer.STOP"

        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_REMAINING = "remaining"

        fun start(context: Context, durationMs: Long, remainingMs: Long) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DURATION, durationMs)
                .putExtra(EXTRA_REMAINING, remainingMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stops the service without starting a new one (always allowed, even in the background). */
        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }

        fun languageOf(context: Context): Language =
            AppGraph.peek()?.controller?.state()?.settings?.language ?: Language.English
    }
}
