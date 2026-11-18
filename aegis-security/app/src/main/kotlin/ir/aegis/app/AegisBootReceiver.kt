package ir.aegis.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AegisBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val app = context.applicationContext as? AegisApplication
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val prefs = app.container.preferencesRepository.preferences.first()
                val reminders = app.container.repository.observeReminders().first()
                reminders.forEach { rem ->
                    app.container.reminderScheduler.scheduleReminder(rem, prefs.sensitiveNotificationRedacted)
                }
            }
            pendingResult.finish()
        }
    }
}
