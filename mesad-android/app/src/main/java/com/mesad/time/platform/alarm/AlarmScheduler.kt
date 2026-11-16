package com.mesad.time.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.mesad.time.domain.model.Alarm
import com.mesad.time.domain.usecase.NextAlarmCalculator
import java.time.Instant
import java.time.ZoneId

/**
 * Platform interface for alarm scheduling.
 */
interface AlarmScheduler {
    fun schedule(alarm: Alarm)
    fun cancel(alarmId: Long)
    fun canScheduleExactAlarms(): Boolean
    fun requestExactAlarmPermission()
}

/**
 * AlarmManager implementation using setAlarmClock() for exact, Doze-proof alarms.
 * - setAlarmClock() implies RTC_WAKEUP and shows in system clock UI.
 * - Allows starting ForegroundService from background (Android 12+).
 * - Survives Doze, app kill, and (via BootReceiver) reboot.
 */
class AlarmManagerScheduler(
    private val context: Context,
    private val calculator: NextAlarmCalculator = NextAlarmCalculator()
) : AlarmScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(alarm: Alarm) {
        if (!alarm.enabled) return

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val trigger = calculator.calculateNextTrigger(alarm, now, zone) ?: return
        val triggerMillis = trigger.toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(AlarmReceiver.EXTRA_ALARM_MINUTE, alarm.minute)
            putExtra(AlarmReceiver.EXTRA_REPEAT_DAYS, alarm.repeatDays.joinToString(",") { it.name })
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setAlarmClock for exact alarms (shows in system UI, wakes device)
        // Falls back to setExactAndAllowWhileIdle if exact alarm permission denied
        if (canScheduleExactAlarms()) {
            val showIntent = PendingIntent.getActivity(
                context,
                alarm.id.toInt() + 100000,
                Intent(context, com.mesad.time.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
                pendingIntent
            )
        } else {
            // Inexact fallback — still wakes device, may be delayed by Doze
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ exact-alarm access is special app access; never call an exact API without it.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    override fun cancel(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Pre-Android 12, exact alarms always allowed
        }
    }

    override fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
