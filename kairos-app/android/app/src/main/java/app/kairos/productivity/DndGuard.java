package app.kairos.productivity;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Temporary, opt-in DND ownership. The marker lives separately from the timer
 * mirror: clearing the latter must not forget a filter the app needs to restore.
 * A user/system change away from PRIORITY is never overwritten on release.
 * Android cannot identify who last selected the same PRIORITY value, and a
 * killed process or revoked permission can prevent automatic restoration.
 */
final class DndGuard {
    private static final String PREFS = "kairos_dnd_guard";
    private static final String OWNED = "owned";
    private static final String PREVIOUS = "previous";

    private DndGuard() {}

    static synchronized void acquire(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 23 || manager == null || !manager.isNotificationPolicyAccessGranted()) {
            throw new IllegalStateException("dnd_not_granted");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int current = manager.getCurrentInterruptionFilter();
        if (!DndPolicy.shouldAcquire(prefs.getBoolean(OWNED, false), current,
                NotificationManager.INTERRUPTION_FILTER_ALL)) return;
        // Persist before changing the device so process death doesn't forget the
        // prior filter. If persistence fails, leave system settings untouched.
        if (!prefs.edit().putBoolean(OWNED, true).putInt(PREVIOUS, current).commit()) {
            throw new IllegalStateException("dnd_persist_failed");
        }
        try {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        } catch (RuntimeException error) {
            prefs.edit().remove(OWNED).remove(PREVIOUS).commit();
            throw error;
        }
    }

    static synchronized void release(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 23 || manager == null || !manager.isNotificationPolicyAccessGranted()) {
            throw new IllegalStateException("dnd_not_granted");
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(OWNED, false)) return;
        int current = manager.getCurrentInterruptionFilter();
        if (DndPolicy.shouldRestore(true, current, NotificationManager.INTERRUPTION_FILTER_PRIORITY)) {
            int previous = prefs.getInt(PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL);
            manager.setInterruptionFilter(previous);
        }
        // If the user changed the mode in the meantime, keep their choice.
        if (!prefs.edit().remove(OWNED).remove(PREVIOUS).commit()) {
            throw new IllegalStateException("dnd_persist_failed");
        }
    }

    /** Used by the service when the first native phase ends without WebView. */
    static void releaseBestEffort(Context context) {
        try {
            release(context, (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        } catch (RuntimeException ignored) {
            // Keep the ownership marker for a future retry if restoration failed.
        }
    }
}
