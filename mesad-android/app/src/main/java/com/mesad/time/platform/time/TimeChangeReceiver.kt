package com.mesad.time.platform.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mesad.time.data.local.DatabaseProvider
import com.mesad.time.domain.model.Alarm
import com.mesad.time.platform.alarm.AlarmManagerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** Reschedules wall-clock alarms after manual clock/date or timezone updates. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val scheduler = AlarmManagerScheduler(context)
                        DatabaseProvider.get(context).alarmDao().getEnabled().forEach { row ->
                            val repeats = row.repeatDays.split(",").filter(String::isNotBlank)
                                .mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
                            scheduler.schedule(Alarm(id=row.id,hour=row.hour,minute=row.minute,label=row.label,repeatDays=repeats,ringtoneUri=row.ringtoneUri,vibrate=row.vibrate,enabled=row.enabled,snoozeMinutes=row.snoozeMinutes))
                        }
                    } finally { pending.finish() }
                }
            }
        }
    }
}
