package app.kairos.productivity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Home-screen widget for the running session.
 *
 * It reads the same SharedPreferences mirror the foreground service writes, so it
 * needs no connection to the WebView: the widget is correct even when the page is
 * gone. Tapping anywhere opens KAIROS on the focus screen.
 *
 * Refresh cadence: the service ticks it once a minute while a session runs. The
 * system's own `updatePeriodMillis` cannot go below 30 minutes, which for a
 * countdown is the difference between a timer and a decoration.
 */
public final class ChronoWidget {

    static final String ACTION_TICK = "app.kairos.chrono.WIDGET_TICK";

    private ChronoWidget() {}

    /** Persian digits, because the widget is read, not parsed. */
    private static String fa(long value) {
        String digits = "۰۱۲۳۴۵۶۷۸۹";
        String latin = String.valueOf(value);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < latin.length(); i++) {
            char c = latin.charAt(i);
            out.append(c >= '0' && c <= '9' ? digits.charAt(c - '0') : c);
        }
        return out.toString();
    }

    static String clockText(long leftMs) {
        long total = Math.max(0, leftMs / 1000);
        long minutes = total / 60;
        long seconds = total % 60;
        return fa(minutes) + ":" + (seconds < 10 ? "۰" : "") + fa(seconds);
    }

    /** Rebuilds every placed widget from the shared mirror. */
    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, ChronoWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) manager.updateAppWidget(id, build(context));
    }

    private static RemoteViews build(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("kairos_chrono", Context.MODE_PRIVATE);
        String label = prefs.getString("label", "KAIROS");
        String subtitle = prefs.getString("subtitle", "");
        boolean running = prefs.getBoolean("running", false);
        long endAt = prefs.getLong("endAt", 0L);
        long left = running ? Math.max(0, endAt - System.currentTimeMillis())
                : Math.max(0, prefs.getInt("remaining", 0) * 1000L);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.chrono_widget);
        views.setTextViewText(R.id.widget_time, endAt > 0 ? clockText(left) : "۲۵:۰۰");

        String meta;
        if (endAt == 0L) {
            meta = "آمادهٔ شروع · برای باز کردن ضربه بزنید";
        } else if (running) {
            meta = (label == null ? "تمرکز" : label) + (subtitle.isEmpty() ? "" : " · " + subtitle);
        } else {
            meta = "متوقف شد · " + (label == null ? "تمرکز" : label);
        }
        views.setTextViewText(R.id.widget_meta, meta);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        views.setOnClickPendingIntent(
                R.id.widget_root, PendingIntent.getActivity(context, 10, open, flags));

        return views;
    }
}
