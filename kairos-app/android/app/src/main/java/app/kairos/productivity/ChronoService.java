package app.kairos.productivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * KAIROS Chrono Core — foreground service.
 *
 * Why this exists: a WebView-only timer dies with the page. Android reclaims a
 * backgrounded app within minutes, so a 45-minute deep-work block could be
 * silently erased by switching to another app. A foreground service keeps the
 * session alive, shows the countdown in the shade, and lets the reader pause it
 * without reopening KAIROS.
 *
 * Two deliberate choices:
 *  - the countdown in the notification is a **chronometer** (`setUsesChronometer`
 *    + `setChronometerCountDown`), drawn by the system UI. It stays correct with
 *    zero wakeups, so the service costs no measurable battery and cannot drift;
 *  - the state is mirrored into SharedPreferences, so if the process is killed
 *    the service restarts and still knows the target time.
 *
 * The JS clock remains the source of truth. This service is a mirror: JS pushes
 * a target end time, and the notification renders it.
 */
public class ChronoService extends Service {

    public static final String ACTION_START = "app.kairos.chrono.START";
    public static final String ACTION_UPDATE = "app.kairos.chrono.UPDATE";
    public static final String ACTION_TOGGLE = "app.kairos.chrono.TOGGLE";
    public static final String ACTION_STOP = "app.kairos.chrono.STOP";

    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_SUBTITLE = "subtitle";
    public static final String EXTRA_REMAINING = "remainingSeconds";
    public static final String EXTRA_TOTAL = "totalSeconds";
    public static final String EXTRA_RUNNING = "running";

    private static final String CHANNEL_ID = "kairos_chrono";
    private static final String PREFS = "kairos_chrono";
    private static final int NOTIFICATION_ID = 4417;

    private static boolean sRunning = false;

    /** Refreshes the home-screen widget once a minute while the session runs. */
    private final Handler widgetHandler = new Handler(Looper.getMainLooper());
    private final Runnable widgetTick = new Runnable() {
        @Override
        public void run() {
            ChronoWidget.refresh(ChronoService.this);
            if (sRunning) widgetHandler.postDelayed(this, 60_000L);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Restarted by the system after being killed: rebuild from prefs so the
            // countdown the reader saw is still there.
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            sRunning = prefs.getBoolean("running", false);
            publish();
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_TOGGLE.equals(action)) {
            sRunning = !sRunning;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("running", sRunning).apply();
            publish();
            // Let the page know, if it is alive. When it is not, the clock is
            // re-anchored from the saved run the next time KAIROS opens.
            Intent control = new Intent(ChronoPlugin.EVENT_CONTROL);
            control.setPackage(getPackageName());
            control.putExtra("action", sRunning ? "resume" : "pause");
            sendBroadcast(control);
            return START_STICKY;
        }

        String label = intent.getStringExtra(EXTRA_LABEL);
        String subtitle = intent.getStringExtra(EXTRA_SUBTITLE);
        int remaining = intent.getIntExtra(EXTRA_REMAINING, 0);
        int total = intent.getIntExtra(EXTRA_TOTAL, 0);
        boolean running = intent.getBooleanExtra(EXTRA_RUNNING, true);

        sRunning = running;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("label", label == null ? "KAIROS" : label)
                .putString("subtitle", subtitle == null ? "" : subtitle)
                .putInt("remaining", remaining)
                .putInt("total", total)
                .putLong("endAt", System.currentTimeMillis() + remaining * 1000L)
                .putBoolean("running", running)
                .apply();

        publish();
        return START_STICKY;
    }

    /** Pushes the current mirror state into the notification shade. */
    private void publish() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String label = prefs.getString("label", "KAIROS");
        String subtitle = prefs.getString("subtitle", "");
        int total = prefs.getInt("total", 0);
        long endAt = prefs.getLong("endAt", System.currentTimeMillis());
        long left = Math.max(0, endAt - System.currentTimeMillis());

        Notification notification = buildNotification(label, subtitle, left, total, sRunning);

        try {
            // The typed overload is only required — and only legal — from Android
            // 14 on, where a foreground service must declare what it is for.
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {
            // A denied notification permission must not crash the timer.
        }

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted (Android 13+): the session still runs.
        }

        // The shade counts down by itself; the widget cannot, so it gets a tick.
        ChronoWidget.refresh(this);
        widgetHandler.removeCallbacks(widgetTick);
        if (sRunning) widgetHandler.postDelayed(widgetTick, 60_000L);
    }

    @Override
    public void onDestroy() {
        widgetHandler.removeCallbacks(widgetTick);
        super.onDestroy();
    }

    private Notification buildNotification(
            String label, String subtitle, long leftMs, int total, boolean running) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open, flags);

        Intent toggleIntent = new Intent(this, ChronoService.class).setAction(ACTION_TOGGLE);
        PendingIntent togglePending = PendingIntent.getService(this, 1, toggleIntent, flags);

        Intent stopIntent = new Intent(this, ChronoService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 2, stopIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(label)
                .setContentText(running ? subtitle : "متوقف شد — برای ادامه ضربه بزنید")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + leftMs)
                .setUsesChronometer(running)
                .setChronometerCountDown(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .addAction(0, running ? "توقف" : "ادامه", togglePending)
                .addAction(0, "پایان", stopPending);

        if (total > 0) {
            int leftSeconds = (int) Math.max(0, leftMs / 1000);
            builder.setProgress(total, Math.max(0, total - leftSeconds), false);
        }

        return builder.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "زمان‌سنج تمرکز",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("شمارش معکوس جلسهٔ تمرکز کایروس");
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    /** Starts or updates the mirror from a plugin call. */
    static void push(Context context, Intent intent) {
        ContextCompat.startForegroundService(context, intent);
    }
}
