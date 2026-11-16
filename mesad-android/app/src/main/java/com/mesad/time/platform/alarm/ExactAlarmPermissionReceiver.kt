package com.mesad.time.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mesad.time.data.local.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Recreates exact alarm registrations immediately when Android reports permission grant. */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmManagerScheduler(context)
                DatabaseProvider.get(context).alarmDao().getEnabled().forEach { row ->
                    val days = row.repeatDays.split(",").filter(String::isNotBlank)
                        .mapNotNull { runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
                    scheduler.schedule(com.mesad.time.domain.model.Alarm(id=row.id,hour=row.hour,minute=row.minute,label=row.label,repeatDays=days,ringtoneUri=row.ringtoneUri,vibrate=row.vibrate,enabled=row.enabled,snoozeMinutes=row.snoozeMinutes))
                }
            } finally { pending.finish() }
        }
    }
}
