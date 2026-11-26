package app.kairos.productivity;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only task and annual widgets, rendered from the last app snapshot. */
final class SectionWidget {
    private SectionWidget() {}
    static void refresh(Context context) {
        update(context, DailyWidgetProvider.class, true);
        update(context, AnnualWidgetProvider.class, false);
    }
    private static void update(Context context, Class<?> provider, boolean daily) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int id : ids) manager.updateAppWidget(id, build(context, daily));
    }
    private static RemoteViews build(Context context, boolean daily) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.section_widget);
        String stored = context.getSharedPreferences("kairos_widgets", Context.MODE_PRIVATE).getString("snapshot", "");
        JSONObject data = null;
        try { data = new JSONObject(stored); } catch (Exception ignored) { }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean stale = data == null || !today.equals(data.optString("day"));
        views.setTextViewText(R.id.section_title, daily ? "کارهای امروز" : "کارهای تکمیل‌شدهٔ سال");
        if (stale) {
            views.setTextViewText(R.id.section_summary, "برای تازه‌سازی برنامه را باز کن");
            views.setTextViewText(R.id.section_body, "دادهٔ امروز هنوز همگام نشده است.");
        } else if (daily) {
            int total = Math.max(0, data.optInt("todayTotal"));
            int done = Math.max(0, data.optInt("todayDone"));
            views.setTextViewText(R.id.section_summary, fa(done) + " از " + fa(total) + " تکمیل شده");
            JSONArray tasks = data.optJSONArray("tasks");
            StringBuilder body = new StringBuilder();
            if (!data.optBoolean("showTitles", true)) body.append("عنوان‌ها در تنظیمات پنهان شده‌اند");
            else if (tasks == null || tasks.length() == 0) body.append("برای امروز وظیفه‌ای ثبت نشده است");
            else for (int i = 0; i < Math.min(3, tasks.length()); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null) continue;
                if (body.length() > 0) body.append('\n');
                String status = task.optString("status", "todo");
                body.append(status.equals("completed") ? "✓ " : status.equals("in_progress") ? "◐ " : "○ ")
                    .append(task.optString("title", ""))
                    .append(task.optBoolean("sample") ? " · نمونه" : "");
            }
            views.setTextViewText(R.id.section_body, body.toString());
        } else {
            JSONArray months = data.optJSONArray("monthlyDone");
            int max = 0, sum = 0;
            int[] values = new int[12];
            for (int i = 0; i < 12; i++) {
                values[i] = months == null ? 0 : Math.max(0, months.optInt(i));
                max = Math.max(max, values[i]); sum += values[i];
            }
            String[] levels = {"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"};
            StringBuilder graph = new StringBuilder();
            StringBuilder accessible = new StringBuilder("از فروردین تا اسفند: ");
            for (int i = 0; i < 12; i++) {
                graph.append(levels[max == 0 ? 0 : (int) (7L * values[i] / max)]).append(' ');
                if (i > 0) accessible.append("، ");
                accessible.append(fa(values[i]));
            }
            views.setTextViewText(R.id.section_summary, "سال " + fa(data.optInt("year")) + " · " + fa(sum) + " وظیفه");
            views.setTextViewText(R.id.section_body, "\u200e" + graph.toString() + "\nفروردین ← اسفند");
            views.setContentDescription(R.id.section_body, accessible.toString());
        }
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse("kairos-widget://open/" + (daily ? "tasks" : "analytics")), context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        views.setOnClickPendingIntent(R.id.section_root, PendingIntent.getActivity(context, daily ? 80 : 81, open, flags));
        return views;
    }
    private static String fa(int number) {
        String digits = "۰۱۲۳۴۵۶۷۸۹";
        StringBuilder out = new StringBuilder();
        for (char c : String.valueOf(number).toCharArray()) out.append(c >= '0' && c <= '9' ? digits.charAt(c - '0') : c);
        return out.toString();
    }
}
