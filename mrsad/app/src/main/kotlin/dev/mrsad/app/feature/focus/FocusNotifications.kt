package dev.mrsad.app.feature.focus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import dev.mrsad.app.R
import dev.mrsad.core.session.FocusSession

/**
 * Local notifications for Focus Space. Everything is local and permission
 * aware: on API 33+ a missing POST_NOTIFICATIONS grant simply means no
 * notification — the timer still runs correctly.
 */
object FocusNotifications {

    private const val CHANNEL_ID = "mrsad_focus"
    private const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus Space",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "وضعیت جلسات تمرکز مرصاد"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildStateNotification(
        context: Context,
        title: String,
        text: String,
        ongoing: Boolean = true,
    ): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyPhase(context: Context, phase: FocusSession.Phase, round: Int, rounds: Int) {
        if (!canNotify(context)) return
        val (title, text) = when (phase) {
            FocusSession.Phase.FOCUS -> "شروع تمرکز" to "دور $round از $rounds"
            FocusSession.Phase.BREAK -> "وقت استراحت" to "دور $round از $rounds تمام شد؛ چند دقیقه استراحت کنید"
            FocusSession.Phase.DONE -> "جلسهٔ تمرکز کامل شد" to "آفرین! جلسه در تاریخچه ثبت شد"
        }
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID, buildStateNotification(context, title, text, ongoing = phase != FocusSession.Phase.DONE))
        }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
