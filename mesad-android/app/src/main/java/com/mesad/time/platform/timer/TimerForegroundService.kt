package com.mesad.time.platform.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mesad.time.MainActivity
import com.mesad.time.data.local.DatabaseProvider
import com.mesad.time.domain.model.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One foreground observer owns any number of concurrent countdown timers.
 * Each timer is persisted in Room with monotonic and wall-clock deadlines; this service derives
 * remaining time from elapsedRealtime (never decrements a counter), marks every completion, and
 * keeps the nearest active deadline visible in the notification shade.
 */
class TimerForegroundService : Service() {
    companion object {
        const val ACTION_SYNC = "com.mesad.time.timer.SYNC"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "mesad_timer_channel"
    }
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification("MESAD timers", "Preparing countdowns"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (job?.isActive != true) job = scope.launch { monitorTimers() }
        return START_STICKY
    }

    private suspend fun monitorTimers() {
        val dao = DatabaseProvider.get(this).timerDao()
        while (scope.isActive) {
            val now = SystemClock.elapsedRealtime()
            val running = dao.getRunning()
            val live = mutableListOf<Pair<com.mesad.time.data.local.TimerEntity, Long>>()
            for (timer in running) {
                val remaining = ((timer.deadlineElapsedRealtime ?: now) - now).coerceAtLeast(0L)
                if (remaining == 0L) {
                    dao.update(timer.copy(state = TimerState.FINISHED.name, remainingMs = 0, deadlineElapsedRealtime = null, deadlineWallClock = null))
                    playCompletionTone()
                } else live += timer to remaining
            }
            if (live.isEmpty()) {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            val nearest = live.minBy { it.second }
            val remaining = nearest.second
            val time = formatDuration(remaining)
            val title = if (live.size == 1) nearest.first.label.ifBlank { "MESAD timer" } else "${live.size} active timers"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(title, time))
            delay(250L.coerceAtMost(remaining))
        }
    }

    private fun playCompletionTone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val player = android.media.MediaPlayer()
            player.setDataSource(this, uri)
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            player.setOnCompletionListener { it.release() }
            player.prepare(); player.start()
        } catch (_: Exception) { /* Notification remains the reliable completion signal. */ }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "MESAD Timers", NotificationManager.IMPORTANCE_LOW).apply { description = "Active countdown timer status and completion" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() { job?.cancel(); super.onDestroy() }
    private fun formatDuration(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format("%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
        else String.format("%02d:%02d", seconds / 60, seconds % 60)
    }
}
