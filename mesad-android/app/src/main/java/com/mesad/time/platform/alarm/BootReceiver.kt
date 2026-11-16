package com.mesad.time.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mesad.time.data.local.MesadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules all enabled alarms after device reboot.
 * Critical for alarm reliability — without this, alarms are lost on reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.mesad.time.data.local.DatabaseProvider.get(context)
                val enabledAlarms = db.alarmDao().getEnabled()
                val scheduler = AlarmManagerScheduler(context)

                enabledAlarms.forEach { entity ->
                    val alarm = com.mesad.time.domain.model.Alarm(
                        id = entity.id,
                        hour = entity.hour,
                        minute = entity.minute,
                        label = entity.label,
                        repeatDays = entity.repeatDays.split(",").filter { it.isNotBlank() }
                            .mapNotNull { runCatching { java.time.DayOfWeek.valueOf(it.trim()) }.getOrNull() }.toSet(),
                        ringtoneUri = entity.ringtoneUri,
                        vibrate = entity.vibrate,
                        enabled = entity.enabled,
                        snoozeMinutes = entity.snoozeMinutes
                    )
                    scheduler.schedule(alarm)
                }

                // elapsedRealtime resets on reboot, so recover running timer durations from the
                // persisted wall-clock deadline, then establish a fresh monotonic deadline.
                val timerDao = db.timerDao()
                val timers = timerDao.getRunning()
                val wallNow = System.currentTimeMillis()
                val elapsedNow = android.os.SystemClock.elapsedRealtime()
                var hasLiveTimer = false
                timers.forEach { timer ->
                    val remaining = ((timer.deadlineWallClock ?: wallNow) - wallNow).coerceAtLeast(0L)
                    if (remaining == 0L) timerDao.update(timer.copy(state = com.mesad.time.domain.model.TimerState.FINISHED.name, remainingMs = 0, deadlineElapsedRealtime = null, deadlineWallClock = null))
                    else {
                        hasLiveTimer = true
                        timerDao.update(timer.copy(deadlineElapsedRealtime = elapsedNow + remaining))
                    }
                }
                if (hasLiveTimer && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(Intent(context, com.mesad.time.platform.timer.TimerForegroundService::class.java).setAction(com.mesad.time.platform.timer.TimerForegroundService.ACTION_SYNC))
                }
            } catch (e: Exception) {
                // Log but don't crash — alarms will be rescheduled on next app launch
                e.printStackTrace()
            }
        }
    }
}
