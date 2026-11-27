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
 * session alive and shows the countdown in the shade. Timer controls live in
 * KAIROS, where the authoritative state and session record can be saved together.
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
    public static final String ACTION_STOP_MIRROR = "app.kairos.chrono.STOP_MIRROR";

    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_SUBTITLE = "subtitle";
    public static final String EXTRA_REMAINING = "remainingSeconds";
    public static final String EXTRA_TOTAL = "totalSeconds";
    public static final String EXTRA_ELAPSED = "elapsedSeconds";
    public static final String EXTRA_RUNNING = "running";

    private static final String CHANNEL_ID = "kairos_chrono";
    private static final String ALERT_CHANNEL_ID = "kairos_chrono_alert";
    private static final String PREFS = "kairos_chrono";
    private static final int NOTIFICATION_ID = 4417;
    private static final int ALERT_NOTIFICATION_ID = 4419;

    private static boolean sRunning = false;

    /** Fires when the countdown reaches zero, whatever the page is doing. */
    private final Handler endHandler = new Handler(Looper.getMainLooper());
    private final Runnable endRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sRunning) return;
            sRunning = false;
            DndGuard.releaseBestEffort(ChronoService.this);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("running", false).putInt("remaining", 0).putBoolean("completed", true).apply();
            ChronoWidget.refresh(ChronoService.this);
            // Fail silent until the app has synced the user's preference.
            if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("soundEnabled", false)) {
                SoundscapeBus.playBell();
            }
            announceCompletion();
        }
    };

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
            scheduleEnd();
            return START_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP_MIRROR.equals(action)) {
            endHandler.removeCallbacks(endRunnable);
            sRunning = false;
            // JS releases DND according to the authoritative focus state. A
            // delayed mirror-stop must not release DND for a newer session.
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
            ChronoWidget.refresh(this);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Old notification PendingIntents can outlive an upgrade. Do not mutate
        // the mirror without updating the authoritative IndexedDB clock.
        if (ACTION_TOGGLE.equals(action) || ACTION_STOP.equals(action)) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (!prefs.getBoolean("present", false)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            sRunning = prefs.getBoolean("running", false);
            publish();
            scheduleEnd();
            return START_STICKY;
        }

        String label = intent.getStringExtra(EXTRA_LABEL);
        String subtitle = intent.getStringExtra(EXTRA_SUBTITLE);
        int remaining = intent.getIntExtra(EXTRA_REMAINING, 0);
        int total = intent.getIntExtra(EXTRA_TOTAL, 0);
        int elapsed = intent.getIntExtra(EXTRA_ELAPSED, 0);
        boolean running = intent.getBooleanExtra(EXTRA_RUNNING, true);

        sRunning = running;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("label", label == null ? "KAIROS" : label)
                .putString("subtitle", subtitle == null ? "" : subtitle)
                .putInt("remaining", remaining)
                .putInt("total", total)
                .putInt("elapsed", elapsed)
                .putLong("anchor", System.currentTimeMillis())
                .putBoolean("present", true)
                .putBoolean("completed", false)
                .putLong("endAt", System.currentTimeMillis() + remaining * 1000L)
                .putBoolean("running", running)
                .apply();

        publish();
        scheduleEnd();
        return START_STICKY;
    }

    /**
     * The bell has to ring at the end of the session, not at the end of the
     * notification. The shade's chronometer is drawn by the system and tells us
     * nothing, so the service arms its own timer against the saved end time —
     * and because this is a foreground service, that timer survives a locked
     * screen and a backgrounded WebView.
     */
    private void scheduleEnd() {
        endHandler.removeCallbacks(endRunnable);
        if (!sRunning) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getInt("total", 0) <= 0) return;
        long delay = prefs.getLong("endAt", 0L) - System.currentTimeMillis();
        endHandler.postDelayed(endRunnable, Math.max(0L, delay));
    }

    /** A high-importance "time is up" card, separate from the running mirror. */
    private void announceCompletion() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel alert = new NotificationChannel(
                        ALERT_CHANNEL_ID,
                        "پایان جلسهٔ تمرکز",
                        NotificationManager.IMPORTANCE_HIGH);
                alert.setDescription("اعلان پایان جلسه با زنگ کایروس");
                // The bell is played by the audio engine, so the channel itself
                // stays silent: two bells at the same instant sound like a bug.
                alert.setSound(null, null);
                alert.enableVibration(true);
                manager.createNotificationChannel(alert);
            }
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 3, open, flags);

        Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("زمان تمرکز تمام شد")
                .setContentText("کایروس زنگ زد — چند لحظه نفس بکش، بعد ادامه بده.")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .build();

        try {
            NotificationManagerCompat.from(this).notify(ALERT_NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // no notification permission: the bell already rang
        }

        // The page may still be alive and wants to move to the break phase.
        Intent control = new Intent(ChronoPlugin.EVENT_CONTROL);
        control.setPackage(getPackageName());
        control.putExtra("action", "complete");
        sendBroadcast(control);

        stopForeground(true);
        stopSelf();
    }

    /** Pushes the current mirror state into the notification shade. */
    private void publish() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String label = prefs.getString("label", "KAIROS");
        String subtitle = prefs.getString("subtitle", "");
        int total = prefs.getInt("total", 0);
        long endAt = prefs.getLong("endAt", System.currentTimeMillis());
        long now = System.currentTimeMillis();
        long left = ChronoMirrorMath.remaining(sRunning, endAt, prefs.getInt("remaining", 0), now);
        long elapsed = ChronoMirrorMath.elapsed(sRunning, prefs.getLong("anchor", 0L), prefs.getInt("elapsed", 0), now);

        Notification notification = buildNotification(label, subtitle, total > 0 ? left : elapsed, total, sRunning);

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
        endHandler.removeCallbacks(endRunnable);
        super.onDestroy();
    }

    private Notification buildNotification(
            String label, String subtitle, long leftMs, int total, boolean running) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(label)
                .setContentText(running ? subtitle + " — برای کنترل، کایروس را باز کنید" : "متوقف شد — برای ادامه کایروس را باز کنید")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + (total > 0 ? leftMs : -leftMs))
                .setUsesChronometer(running)
                .setChronometerCountDown(total > 0)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH);

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
