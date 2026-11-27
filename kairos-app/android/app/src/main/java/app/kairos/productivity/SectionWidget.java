package app.kairos.productivity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import androidx.core.graphics.ColorUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only projections of the local database. Writes always happen in the app. */
final class SectionWidget {
    private static final int[] ROWS = { R.id.daily_row_1, R.id.daily_row_2, R.id.daily_row_3 };
    private SectionWidget() {}

    static void refresh(Context context) {
        update(context, DailyWidgetProvider.class, true);
        update(context, AnnualWidgetProvider.class, false);
    }
    private static void update(Context context, Class<?> provider, boolean daily) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int id : ids) {
            Bundle options = manager.getAppWidgetOptions(id);
            int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            manager.updateAppWidget(id, build(context, daily, id, height));
        }
    }

    private static RemoteViews build(Context context, boolean daily, int widgetId, int height) {
        RemoteViews views = new RemoteViews(context.getPackageName(), daily ? R.layout.daily_widget : R.layout.annual_widget);
        String stored = context.getSharedPreferences("kairos_widgets", Context.MODE_PRIVATE).getString("snapshot", "");
        JSONObject data = null;
        try { data = new JSONObject(stored); } catch (Exception ignored) { }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean stale = data == null || !WidgetPolicy.fresh(data.optString("day"), today);
        int summary = daily ? R.id.daily_summary : R.id.annual_summary;
        int updated = daily ? R.id.daily_updated : R.id.annual_updated;
        views.setTextColor(summary, safeAccent(data));
        views.setOnClickPendingIntent(daily ? R.id.daily_root : R.id.annual_root,
                open(context, daily ? "tasks" : "analytics", null, widgetId * 8));

        if (stale) {
            views.setTextViewText(summary, "برای تازه‌سازی برنامه را باز کن");
            views.setTextViewText(updated, "آخرین تصویر مربوط به امروز نیست");
            if (daily) {
                views.setViewVisibility(R.id.daily_progress, View.GONE);
                views.setTextViewText(R.id.daily_message, "اطلاعات امروز هنوز همگام نشده است.");
                for (int row : ROWS) views.setViewVisibility(row, View.GONE);
            } else {
                views.setViewVisibility(R.id.annual_graph, View.GONE);
                views.setViewVisibility(R.id.annual_axis, View.GONE);
            }
            return views;
        }
        long time = data.optLong("updatedAt", 0);
        String sync = time > 0 && time <= System.currentTimeMillis() + 60000
                ? "آخرین همگام‌سازی " + faClock(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(time)))
                : "برای تازه‌سازی برنامه را باز کن";
        views.setTextViewText(updated, sync);
        if (daily) renderDaily(context, views, data, widgetId, height);
        else renderAnnual(views, data, height);
        return views;
    }

    private static void renderDaily(Context context, RemoteViews views, JSONObject data, int widgetId, int height) {
        int total = Math.max(0, data.optInt("todayTotal"));
        int done = Math.max(0, Math.min(total, data.optInt("todayDone")));
        views.setTextViewText(R.id.daily_summary, fa(done) + " از " + fa(total) + " کار امروز انجام شد");
        views.setViewVisibility(R.id.daily_progress, total > 0 ? View.VISIBLE : View.GONE);
        if (total > 0) views.setProgressBar(R.id.daily_progress, 100, (int) (100L * done / total), false);
        JSONArray tasks = data.optJSONArray("tasks");
        int count = tasks == null ? 0 : Math.min(ROWS.length, tasks.length());
        int visible = Math.min(count, WidgetPolicy.dailyRows(height));
        boolean showTitles = data.optBoolean("showTitles", false);
        for (int i = 0; i < ROWS.length; i++) {
            JSONObject task = i < count ? tasks.optJSONObject(i) : null;
            if (!showTitles || i >= visible || task == null) {
                views.setViewVisibility(ROWS[i], View.GONE);
                continue;
            }
            String status = task.optString("status", "todo");
            String marker = status.equals("completed") ? "✓ " : status.equals("in_progress") ? "◐ " : "○ ";
            String text = marker + task.optString("title", "") + (task.optBoolean("sample") ? " · نمونه" : "");
            views.setTextViewText(ROWS[i], text);
            views.setContentDescription(ROWS[i], "باز کردن وظیفه: " + text);
            String taskId = task.optString("id", "");
            views.setOnClickPendingIntent(ROWS[i], open(context, WidgetPolicy.safeTaskId(taskId) ? "task" : "tasks", taskId, widgetId * 8 + i + 1));
            views.setViewVisibility(ROWS[i], View.VISIBLE);
        }
        String message = !showTitles ? "عنوان‌ها در تنظیمات پنهان شده‌اند" : total == 0 ? "امروز وظیفه‌ای ثبت نشده است" : visible == 0 ? "برای دیدن کارها برنامه را باز کن" : "";
        views.setTextViewText(R.id.daily_message, message);
        views.setViewVisibility(R.id.daily_message, message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static void renderAnnual(RemoteViews views, JSONObject data, int height) {
        JSONArray months = data.optJSONArray("monthlyDone");
        int max = 0; long sum = 0;
        int[] values = new int[12];
        for (int i = 0; i < 12; i++) {
            values[i] = months == null ? 0 : Math.max(0, months.optInt(i));
            max = Math.max(max, values[i]); sum += values[i];
        }
        views.setTextViewText(R.id.annual_summary, "سال " + fa(data.optInt("year")) + " · " + fa(sum) + " تکمیل به تاریخ ثبت");
        if (max == 0 || height < 140) {
            views.setViewVisibility(R.id.annual_graph, View.GONE);
            views.setTextViewText(R.id.annual_axis, max == 0 ? "هنوز وظیفهٔ تکمیل‌شده‌ای ثبت نشده است" : "برای جزئیات گزارش را باز کن");
            return;
        }
        String[] levels = {"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
        StringBuilder graph = new StringBuilder();
        StringBuilder accessible = new StringBuilder("از فروردین تا اسفند: ");
        for (int i = 0; i < 12; i++) {
            graph.append(levels[(int) (7L * values[i] / max)]).append(' ');
            if (i > 0) accessible.append("، ");
            accessible.append(fa(values[i]));
        }
        views.setTextViewText(R.id.annual_graph, "\u200e" + graph);
        views.setContentDescription(R.id.annual_graph, accessible.toString());
    }

    private static PendingIntent open(Context context, String destination, String id, int request) {
        Uri.Builder uri = new Uri.Builder().scheme("kairos-widget").authority("open").appendPath(destination);
        if (destination.equals("task") && WidgetPolicy.safeTaskId(id)) uri.appendPath(id);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri.build(), context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static int safeAccent(JSONObject data) {
        String value = data == null ? "" : data.optString("accent", "");
        try {
            if (value.matches("#[0-9a-fA-F]{6}")) {
                int color = Color.parseColor(value);
                if (ColorUtils.calculateContrast(color, Color.rgb(16, 16, 24)) >= 4.5) return color;
            }
        } catch (Exception ignored) { }
        return Color.WHITE;
    }
    private static String fa(long number) { return faClock(String.valueOf(number)); }
    private static String faClock(String value) {
        String digits = "۰۱۲۳۴۵۶۷۸۹";
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) out.append(c >= '0' && c <= '9' ? digits.charAt(c - '0') : c);
        return out.toString();
    }
}
