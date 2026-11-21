package app.kairos.productivity;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.Nullable;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Capacitor bridge for the Chrono Core foreground service.
 *
 * JS owns the clock; this plugin only mirrors it into the notification shade and
 * reports back when the reader presses pause/resume on the notification itself.
 * Every method is safe to call from any screen: the service is idempotent, so a
 * repeated `start` simply updates the countdown.
 */
@CapacitorPlugin(name = "Chrono")
public class ChronoPlugin extends Plugin {

    /** Broadcast action the service sends when its notification is tapped. */
    static final String EVENT_CONTROL = "app.kairos.chrono.CONTROL";

    @Nullable
    private BroadcastReceiver controlReceiver;

    // `Plugin.load()` is public in Capacitor's API; overriding it with `protected`
    // is a compile error ("weaker access privileges").
    @Override
    public void load() {
        controlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                JSObject payload = new JSObject();
                payload.put("action", intent.getStringExtra("action"));
                notifyListeners("control", payload);
            }
        };

        IntentFilter filter = new IntentFilter(EVENT_CONTROL);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                getContext().registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(controlReceiver, filter);
            }
        } catch (Exception ignored) {
            controlReceiver = null;
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (controlReceiver != null) {
            try {
                getContext().unregisterReceiver(controlReceiver);
            } catch (Exception ignored) {
                // already gone
            }
            controlReceiver = null;
        }
        super.handleOnDestroy();
    }

    @PluginMethod
    public void start(PluginCall call) {
        push(call, ChronoService.ACTION_START);
    }

    @PluginMethod
    public void update(PluginCall call) {
        push(call, ChronoService.ACTION_UPDATE);
    }

    /** Is the app allowed to change the interruption filter at all? */
    @PluginMethod
    public void dndStatus(PluginCall call) {
        JSObject result = new JSObject();
        NotificationManager manager =
                (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        boolean granted = false;
        boolean active = false;
        if (manager != null && Build.VERSION.SDK_INT >= 23) {
            try {
                granted = manager.isNotificationPolicyAccessGranted();
                active = manager.getCurrentInterruptionFilter()
                        != NotificationManager.INTERRUPTION_FILTER_ALL;
            } catch (Exception ignored) {
                // Treat an unreadable policy as "not granted".
            }
        }
        result.put("granted", granted);
        result.put("active", active);
        call.resolve(result);
    }

    /**
     * Turns "do not disturb" on for the length of a focus session.
     *
     * Only the priority filter is touched — alarms and the user's starred contacts
     * still come through — and it is always restored to ALL when the session ends.
     * Without the policy grant this is a no-op that reports failure, never a silent
     * lie in the UI.
     */
    @PluginMethod
    public void setDnd(PluginCall call) {
        boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        NotificationManager manager =
                (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || Build.VERSION.SDK_INT < 23) {
            call.reject("dnd_unsupported");
            return;
        }
        try {
            if (!manager.isNotificationPolicyAccessGranted()) {
                call.reject("dnd_not_granted");
                return;
            }
            manager.setInterruptionFilter(
                    enabled
                            ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            : NotificationManager.INTERRUPTION_FILTER_ALL);
            call.resolve();
        } catch (Exception error) {
            call.reject("dnd_failed", error);
        }
    }

    /** Sends the reader straight to the system screen that grants the access. */
    @PluginMethod
    public void openDndSettings(PluginCall call) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception error) {
            call.reject("dnd_settings_failed", error);
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Intent intent = new Intent(getContext(), ChronoService.class);
        intent.setAction(ChronoService.ACTION_STOP);
        try {
            getContext().startService(intent);
        } catch (Exception ignored) {
            // Service not running: nothing to stop.
        }
        call.resolve();
    }

    private void push(PluginCall call, String action) {
        Intent intent = new Intent(getContext(), ChronoService.class);
        intent.setAction(action);
        intent.putExtra(ChronoService.EXTRA_LABEL, call.getString("label", "KAIROS"));
        intent.putExtra(ChronoService.EXTRA_SUBTITLE, call.getString("subtitle", ""));
        intent.putExtra(ChronoService.EXTRA_REMAINING, call.getInt("remainingSeconds", 0));
        intent.putExtra(ChronoService.EXTRA_TOTAL, call.getInt("totalSeconds", 0));
        intent.putExtra(ChronoService.EXTRA_RUNNING, call.getBoolean("running", true));
        try {
            ChronoService.push(getContext(), intent);
            call.resolve();
        } catch (Exception error) {
            call.reject("chrono_service_failed", error);
        }
    }
}
