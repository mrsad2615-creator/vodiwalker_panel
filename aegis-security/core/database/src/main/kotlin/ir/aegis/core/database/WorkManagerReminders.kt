package ir.aegis.core.database

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.aegis.core.model.ReminderScheduler
import ir.aegis.core.model.SecurityReminder
import java.util.concurrent.TimeUnit

class SecurityReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Security Check"
        val category = inputData.getString(KEY_CATEGORY) ?: "Preparedness"
        val redacted = inputData.getBoolean(KEY_REDACT, true)
        val reminderId = inputData.getString(KEY_ID) ?: "reminder"

        ensureChannel(applicationContext)
        val hasPostPermission = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasPostPermission) {
            val publicNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("AegisGuard Reminder")
                .setContentText("Private preparedness task requires attention.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(if (redacted) "AegisGuard: Security Reminder" else title)
                .setContentText(if (redacted) "Open app safely to view protected details." else "Category: $category")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setAutoCancel(true)

            runCatching {
                NotificationManagerCompat.from(applicationContext).notify(reminderId.hashCode(), builder.build())
            }
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "aegis_offline_reminders"
        const val KEY_ID = "reminder_id"
        const val KEY_TITLE = "reminder_title"
        const val KEY_CATEGORY = "reminder_category"
        const val KEY_REDACT = "reminder_redact"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Offline Security Reminders",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "100% local offline reminders for security drills, kit rotation, and check-ins"
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                    }
                    mgr.createNotificationChannel(channel)
                }
            }
        }
    }
}

class WorkManagerReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun scheduleReminder(reminder: SecurityReminder, redactContent: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val uniqueName = "aegis_rem_${reminder.id}"
        if (!reminder.enabled || reminder.completed) {
            workManager.cancelUniqueWork(uniqueName)
            return
        }

        val data = Data.Builder()
            .putString(SecurityReminderWorker.KEY_ID, reminder.id)
            .putString(SecurityReminderWorker.KEY_TITLE, reminder.title)
            .putString(SecurityReminderWorker.KEY_CATEGORY, reminder.category)
            .putBoolean(SecurityReminderWorker.KEY_REDACT, redactContent)
            .build()

        val delayMillis = (reminder.dueAtMillis - System.currentTimeMillis()).coerceAtLeast(15_000L)
        if (reminder.recurrenceDays > 0) {
            val repeatHours = (reminder.recurrenceDays * 24L).coerceAtLeast(1L)
            val request = PeriodicWorkRequestBuilder<SecurityReminderWorker>(repeatHours, TimeUnit.HOURS)
                .setInputData(data)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            val request = OneTimeWorkRequestBuilder<SecurityReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override fun cancelReminder(reminderId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("aegis_rem_$reminderId")
    }
}
