package com.mesad.time.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.launch

/**
 * Receives exact alarm triggers from AlarmManager.
 * Starts AlarmRingingService as a ForegroundService.
 * Reschedules repeating alarms for the next occurrence.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_ALARM_HOUR = "alarm_hour"
        const val EXTRA_ALARM_MINUTE = "alarm_minute"
        const val EXTRA_REPEAT_DAYS = "repeat_days"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId == -1L) return

        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Alarm"
        val hour = intent.getIntExtra(EXTRA_ALARM_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_ALARM_MINUTE, 0)

        // One-shot AlarmManager entries must be recreated after they fire; repeat days are evaluated
        // against the current local timezone, so DST/timezone changes remain correct.
        val repeatDays = intent.getStringExtra(EXTRA_REPEAT_DAYS).orEmpty()
        if (repeatDays.isNotBlank()) {
            val days = repeatDays.split(",").mapNotNull { runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
            AlarmManagerScheduler(context).schedule(
                com.mesad.time.domain.model.Alarm(id = alarmId, hour = hour, minute = minute, label = label, repeatDays = days)
            )
        } else {
            // One-time alarms are consumed after firing; reboot recovery must not recreate them.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.mesad.time.data.local.DatabaseProvider.get(context).alarmDao().setEnabled(alarmId, false)
            }
        }

        // Start ringing service
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_LABEL, label)
            putExtra(AlarmRingingService.EXTRA_HOUR, hour)
            putExtra(AlarmRingingService.EXTRA_MINUTE, minute)
            putExtra(AlarmRingingService.EXTRA_REPEAT_DAYS, repeatDays)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Note: Repeating alarm rescheduling is handled by AlarmRingingService
        // after user dismisses/snoozes, or by BootReceiver on reboot.
    }
}
