package app.kairos.productivity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.net.Uri;
import android.widget.RemoteViews;
import android.view.View;

/**
 * Home-screen widget for the running session.
 *
 * It reads the same SharedPreferences mirror the foreground service writes, so it
 * needs no connection to the WebView: the widget is correct even when the page is
 * gone. Tapping opens KAIROS.
 *
 * Refresh cadence: the service ticks it once a minute while a session runs. The
 * system's own `updatePeriodMillis` cannot go below 30 minutes, which for a
 * countdown is the difference between a timer and a decoration.
 */
public final class ChronoWidget {

    static final String ACTION_TICK = "app.kairos.chrono.WIDGET_TICK";

    private ChronoWidget() {}

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
        long now = System.currentTimeMillis();
        boolean present = prefs.getBoolean("present", false);
        int total = prefs.getInt("total", 0);
        long left = ChronoMirrorMath.remaining(running, endAt, prefs.getInt("remaining", 0), now);
        long elapsed = ChronoMirrorMath.elapsed(running, prefs.getLong("anchor", 0L), prefs.getInt("elapsed", 0), now);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.chrono_widget);
        views.setTextViewText(R.id.widget_time, present ? ChronoMirrorMath.clock(total > 0 ? left : elapsed) : "--:--");
        int progress = ChronoMirrorMath.progress(total, left);
        views.setViewVisibility(R.id.widget_progress, present && progress >= 0 ? View.VISIBLE : View.GONE);
        if (progress >= 0) views.setProgressBar(R.id.widget_progress, 100, progress, false);

        String meta;
        if (!present) {
            meta = "آمادهٔ شروع · برای باز کردن ضربه بزنید";
        } else if (running) {
            meta = (label == null ? "تمرکز" : label) + (subtitle.isEmpty() ? "" : " · " + subtitle);
        } else if (prefs.getBoolean("completed", false)) {
            meta = "این بازه به پایان رسید · " + (label == null ? "تمرکز" : label);
        } else {
            meta = "مکث · " + (label == null ? "تمرکز" : label);
        }
        views.setTextViewText(R.id.widget_meta, meta);

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("kairos-widget://open/focus"), context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        views.setOnClickPendingIntent(
                R.id.widget_root, PendingIntent.getActivity(context, 10, open, flags));

        return views;
    }
}
