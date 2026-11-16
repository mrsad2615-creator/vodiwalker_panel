package com.mesad.time.platform.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.app.Service
import com.mesad.time.MainActivity

/**
 * Foreground service that rings the alarm with crescendo volume and vibration.
 * - MediaPlayer with gradual volume ramp (crescendo over 30 seconds).
 * - Vibration pattern (customizable in future).
 * - Full-screen notification with Dismiss / Snooze actions.
 * - Auto-stops after 5 minutes if user doesn't interact.
 */
class AlarmRingingService : Service() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_REPEAT_DAYS = "repeat_days"
        const val ACTION_DISMISS = "com.mesad.time.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.mesad.time.ACTION_SNOOZE"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mesad_alarm_channel"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var alarmId: Long = -1
    private var label: String = "Alarm"
    private var repeatDays: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1) ?: -1
        label = intent?.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        repeatDays = intent?.getStringExtra(EXTRA_REPEAT_DAYS).orEmpty()
        if (intent?.action == ACTION_DISMISS) { stopRinging(); return START_NOT_STICKY }
        if (intent?.action == ACTION_SNOOZE) { scheduleSnooze(intent); stopRinging(); return START_NOT_STICKY }
        val hour = intent?.getIntExtra(EXTRA_HOUR, 0) ?: 0
        val minute = intent?.getIntExtra(EXTRA_MINUTE, 0) ?: 0

        val timeStr = String.format("%02d:%02d", hour, minute)
        val notification = buildNotification(timeStr)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        startRinging()
        startVibration()

        // Auto-stop after 5 minutes
        android.os.Handler(mainLooper).postDelayed({
            stopRinging()
        }, 5 * 60 * 1000L)

        return START_NOT_STICKY
    }

    private fun startRinging() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmRingingService, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(0.1f, 0.1f) // Start quiet for crescendo
                prepare()
                start()

                // Crescendo: ramp volume from 0.1 to 1.0 over 30 seconds
                val handler = android.os.Handler(mainLooper)
                var step = 0
                val steps = 30
                handler.post(object : Runnable {
                    override fun run() {
                        if (step >= steps) return
                        step++
                        val volume = 0.1f + (0.9f * step / steps)
                        mediaPlayer?.setVolume(volume, volume)
                        handler.postDelayed(this, 1000)
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 200, 500, 200, 1000)
            val effect = VibrationEffect.createWaveform(pattern, 0)
            vibrator?.vibrate(effect)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleSnooze(source: Intent) {
        val manager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val trigger = System.currentTimeMillis() + 5 * 60_000L
        val fire = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ALARM_HOUR, source.getIntExtra(EXTRA_HOUR, 0))
            putExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, source.getIntExtra(EXTRA_MINUTE, 0))
            putExtra(AlarmReceiver.EXTRA_REPEAT_DAYS, repeatDays)
        }
        val pi = PendingIntent.getBroadcast(this, (alarmId xor 0x5A5A).toInt(), fire, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            manager.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(trigger, pi), pi)
        }
    }

    private fun stopRinging() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(timeStr: String): Notification {
        val dismissIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AlarmRingingService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ALARM_ID, alarmId).putExtra(EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmRingingService::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_ALARM_ID, alarmId).putExtra(EXTRA_LABEL, label).putExtra(EXTRA_HOUR, timeStr.substringBefore(":").toIntOrNull() ?: 0).putExtra(EXTRA_MINUTE, timeStr.substringAfter(":").toIntOrNull() ?: 0).putExtra(EXTRA_REPEAT_DAYS, repeatDays),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(label)
            .setContentText(timeStr)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(openIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissIntent)
            .addAction(android.R.drawable.ic_menu_send, "Snooze 5 min", snoozeIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MESAD Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications"
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}
