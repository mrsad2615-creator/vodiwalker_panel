package ai.dana.ui

import ai.dana.domain.LocalAction
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings

/** Accepts only typed actions from a local parser or a UI control, after human approval. */
internal object DeviceActions {
    fun open(context: Context, action: LocalAction): Boolean {
        val intent = when (action) {
            is LocalAction.Note -> return false // A private Room write, not an external intent.
            is LocalAction.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            is LocalAction.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            LocalAction.Clock -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            LocalAction.Wifi -> Intent(Settings.ACTION_WIFI_SETTINGS)
            LocalAction.Bluetooth -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            LocalAction.Sound -> Intent(Settings.ACTION_SOUND_SETTINGS)
            is LocalAction.CalendarDraft -> Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, action.title)
            is LocalAction.SmsDraft -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.number, null))
                .putExtra("sms_body", action.text)
            is LocalAction.Share -> Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, action.text), null)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
