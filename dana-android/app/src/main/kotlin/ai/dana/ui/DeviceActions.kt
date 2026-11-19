package ai.dana.ui

import ai.dana.R
import ai.dana.domain.LocalAction
import ai.dana.domain.LocalCommands
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

internal class DeviceOutcome(@StringRes val message: Int, val percent: Int? = null, val ok: Boolean = true)

/** Platform-only, typed and bounded. No Accessibility, calls, SMS transmission, or privileged settings writes. */
internal object DeviceActions {
    private val dialable = Regex("\\+?[0-9]{5,15}")

    fun execute(context: Context, action: LocalAction): DeviceOutcome = when (action) {
        is LocalAction.Note -> failure() // Room write is handled by ChatViewModel.
        LocalAction.Battery -> battery(context)
        LocalAction.MediaVolume -> mediaVolume(context, null)
        is LocalAction.SetVolume -> mediaVolume(context, action.percent)
        is LocalAction.VolumeStep -> mediaVolume(context, null,
            if (action == LocalAction.VolumeStep.RAISE) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
        is LocalAction.Torch -> torch(context, action.on)
        is LocalAction.Timer, is LocalAction.Alarm, is LocalAction.WifiReminder -> clockIntent(action)?.let { intent ->
            if (action is LocalAction.WifiReminder) intent.putExtra(AlarmClock.EXTRA_MESSAGE,
                context.getString(if (action.turnOn) R.string.phone_wifi_label_on else R.string.phone_wifi_label_off))
            open(context, intent, unavailable = R.string.phone_clock_unavailable,
                success = if (action is LocalAction.WifiReminder) R.string.phone_wifi_clock_opened else R.string.phone_clock_opened)
        } ?: failure(R.string.phone_invalid_time)
        LocalAction.Clock -> open(context, Intent(AlarmClock.ACTION_SHOW_ALARMS), unavailable = R.string.phone_clock_unavailable)
        LocalAction.Wifi -> wifiPanel(context)
        is LocalAction.WifiChange -> wifiPanel(context).let { if (it.ok) DeviceOutcome(R.string.phone_wifi_manual_opened) else it }
        LocalAction.Internet -> settingsPanel(context, if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else null, Settings.ACTION_WIRELESS_SETTINGS)
        LocalAction.Sound -> settingsPanel(context, if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else null, Settings.ACTION_SOUND_SETTINGS)
        LocalAction.Bluetooth -> open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        is LocalAction.Panel -> open(context, when (action) {
            LocalAction.Panel.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
            LocalAction.Panel.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            LocalAction.Panel.APPS -> Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            LocalAction.Panel.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            LocalAction.Panel.CALCULATOR -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        })
        is LocalAction.Dial -> if (dialable.matches(action.number)) open(context,
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null)), unavailable = R.string.phone_dial_unavailable) else failure()
        is LocalAction.CalendarDraft -> calendarIntent(action)?.let {
            open(context, it, unavailable = R.string.phone_calendar_unavailable, success = R.string.phone_calendar_opened)
        } ?: failure(R.string.phone_invalid_time)
        is LocalAction.LaunchApp -> launchApp(context, action.label)
        is LocalAction.SmsDraft -> if (dialable.matches(action.number) && action.text.length in 1..500) open(context,
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.number, null)).putExtra("sms_body", action.text),
            unavailable = R.string.phone_sms_unavailable) else failure()
        is LocalAction.Share -> open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, action.text), null))
        is LocalAction.WebSearch -> if (action.query.length in 1..240) open(context, Intent(Intent.ACTION_VIEW,
            Uri.Builder().scheme("https").authority("www.google.com").path("search").appendQueryParameter("q", action.query).build())
            .addCategory(Intent.CATEGORY_BROWSABLE)) else failure()
    }

    /** Official Android Clock intents. Let the receiving clock display its UI, never request silent setup. */
    internal fun clockIntent(action: LocalAction): Intent? = when (action) {
        is LocalAction.Timer -> timerIntent(action.seconds)
        is LocalAction.WifiReminder -> timerIntent(action.seconds)
        is LocalAction.Alarm -> if (action.hour in 0..23 && action.minute in 0..59) Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, action.hour).putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false) else null
        else -> null
    }

    private fun timerIntent(seconds: Int): Intent? = if (seconds in 1..86_400) Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, false) else null

    private fun wifiPanel(context: Context): DeviceOutcome = settingsPanel(context,
        if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else null, Settings.ACTION_WIFI_SETTINGS)

    internal fun calendarIntent(action: LocalAction.CalendarDraft): Intent? {
        if (action.title.length !in 1..160) return null
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, action.title)
        val time = action.start ?: return intent
        if (time.daysFromToday !in 0..1 || time.hour !in 0..23 || time.minute !in 0..59) return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).plusDays(time.daysFromToday.toLong()).atTime(time.hour, time.minute)
            .atZone(zone).toInstant().toEpochMilli()
        return intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 1_800_000L)
    }

    private fun launchApp(context: Context, label: String): DeviceOutcome {
        if (!LocalCommands.validAppLabel(label)) return failure(R.string.phone_app_missing)
        val pm = context.packageManager
        val name = LocalCommands.appLabel(label)
        val matches = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .asSequence().filter { it.activityInfo.packageName != context.packageName && LocalCommands.appLabel(it.loadLabel(pm).toString()) == name }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.distinct().take(2).toList()
        if (matches.isEmpty()) return failure(R.string.phone_app_missing)
        if (matches.size != 1) return failure(R.string.phone_app_ambiguous)
        return open(context, Intent.makeMainActivity(matches.single()))
    }

    private fun settingsPanel(context: Context, panel: String?, fallback: String): DeviceOutcome {
        if (panel != null) open(context, Intent(panel)).takeIf { it.ok }?.let { return it }
        return open(context, Intent(fallback))
    }

    private fun battery(context: Context): DeviceOutcome {
        val battery = context.getSystemService(BatteryManager::class.java) ?: return failure(R.string.phone_battery_unavailable)
        return try {
            val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (percent !in 0..100) failure(R.string.phone_battery_unavailable)
            else DeviceOutcome(if (battery.isCharging) R.string.phone_battery_charging else R.string.phone_battery_result, percent)
        } catch (_: RuntimeException) { failure(R.string.phone_battery_unavailable) }
    }

    private fun mediaVolume(context: Context, value: Int?, step: Int? = null): DeviceOutcome {
        if (value != null && value !in 0..100) return failure(R.string.phone_volume_unavailable)
        val audio = context.getSystemService(AudioManager::class.java) ?: return failure(R.string.phone_volume_unavailable)
        val stream = AudioManager.STREAM_MUSIC
        return try {
            val min = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(stream) else 0
            val max = audio.getStreamMaxVolume(stream)
            if (max <= min || ((value != null || step != null) && audio.isVolumeFixed)) return failure(R.string.phone_volume_unavailable)
            if (step != null) audio.adjustStreamVolume(stream, step, AudioManager.FLAG_SHOW_UI)
            if (value != null) audio.setStreamVolume(stream, min + (value * (max - min) / 100.0).roundToInt(), AudioManager.FLAG_SHOW_UI)
            val actual = ((audio.getStreamVolume(stream) - min) * 100.0 / (max - min)).roundToInt().coerceIn(0, 100)
            DeviceOutcome(R.string.phone_volume_result, actual)
        } catch (_: RuntimeException) { failure(R.string.phone_volume_unavailable) }
    }

    private fun torch(context: Context, on: Boolean): DeviceOutcome {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return failure(R.string.phone_torch_unavailable)
        val camera = context.getSystemService(CameraManager::class.java) ?: return failure(R.string.phone_torch_unavailable)
        return try {
            val flashes = camera.cameraIdList.mapNotNull { id ->
                val details = camera.getCameraCharacteristics(id)
                if (details.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) id to details.get(CameraCharacteristics.LENS_FACING) else null
            }
            val id = (flashes.firstOrNull { it.second == CameraCharacteristics.LENS_FACING_BACK } ?: flashes.firstOrNull())?.first
                ?: return failure(R.string.phone_torch_unavailable)
            camera.setTorchMode(id, on)
            DeviceOutcome(if (on) R.string.phone_torch_on_result else R.string.phone_torch_off_result)
        } catch (_: CameraAccessException) { failure(R.string.phone_torch_unavailable) }
        catch (_: RuntimeException) { failure(R.string.phone_torch_unavailable) }
    }

    private fun open(context: Context, intent: Intent, @StringRes unavailable: Int = R.string.chat_action_unavailable,
        @StringRes success: Int = R.string.chat_action_opened): DeviceOutcome = try {
        context.startActivity(intent)
        DeviceOutcome(success)
    } catch (_: ActivityNotFoundException) { failure(unavailable) }
    catch (_: SecurityException) { failure(unavailable) }

    private fun failure(@StringRes message: Int = R.string.chat_action_unavailable) = DeviceOutcome(message, ok = false)
}
