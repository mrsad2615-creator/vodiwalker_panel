package com.mrsadhr.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mrsadhr.R
import com.mrsadhr.core.format.TimeFormat
import com.mrsadhr.core.model.Language
import com.mrsadhr.sounds.SoundCatalog

/**
 * Notification surface.
 *
 * The running countdown uses a system chronometer with a count-down deadline: the status bar keeps
 * ticking without a single wake-up from the app, which is both perfectly accurate and battery
 * friendly. Completion gets its own, louder channel.
 */
class Notifications(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val active = NotificationChannel(
            CHANNEL_ACTIVE,
            "Running timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows the countdown while a timer is running"
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val done = NotificationChannel(
            CHANNEL_DONE,
            "Timer finished",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when a countdown reaches zero"
            enableVibration(true)
            // The completion sound is played by the app so the user's own choice and volume apply.
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(active, done))
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 100, intent, flags)
    }

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(context, TimerService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(context, action.hashCode(), intent, flags)
    }

    fun running(durationMs: Long, remainingMs: Long): Notification {
        val persian = AppGraph.peek()?.controller?.state()?.settings?.language == Language.Persian
        val deadline = android.os.SystemClock.elapsedRealtime() + remainingMs.coerceAtLeast(0L)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ACTIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (persian) "تایمر در حال اجرا" else "Timer running")
            .setContentText(
                if (persian) "برای توقف یا صفر کردن باز کنید"
                else "Tap to pause or reset",
            )
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(deadline)
            .setUsesChronometer(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (persian) "توقف" else "Pause",
                    serviceIntent(TimerService.ACTION_PAUSE),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (persian) "صفر" else "Reset",
                    serviceIntent(TimerService.ACTION_RESET),
                ).build(),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setChronometerCountDown(true)
        }
        return builder.build()
    }

    fun completed(durationMs: Long, language: Language, soundId: String): Notification {
        val persian = language == Language.Persian
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_DONE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val soundName = SoundCatalog.byId(soundId)
        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (persian) "زمان تمام شد" else "Time is up")
            .setContentText(
                (if (persian) "تایمر " else "Timer ") + TimeFormat.hmsAlways(durationMs) +
                    (if (persian) " به پایان رسید" else " finished") +
                    " · " + (if (persian) soundName.nameFa else soundName.nameEn),
            )
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        return builder.build()
    }

    fun notifyRunning(durationMs: Long, remainingMs: Long) {
        manager.notify(ID_RUNNING, running(durationMs, remainingMs))
    }

    fun notifyCompleted(durationMs: Long, language: Language, soundId: String) {
        manager.notify(ID_DONE, completed(durationMs, language, soundId))
    }

    fun cancelRunning() = manager.cancel(ID_RUNNING)

    fun cancelCompleted() = manager.cancel(ID_DONE)

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ACTIVE = "timer.active"
        const val CHANNEL_DONE = "timer.completed"
        const val ID_RUNNING = 1001
        const val ID_DONE = 1002
    }
}
