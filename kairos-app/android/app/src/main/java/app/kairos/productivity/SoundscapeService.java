package app.kairos.productivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * Foreground service that keeps the soundscape alive.
 *
 * Two jobs:
 *   1. keep the process out of Android's reach while audio is playing, so the
 *      soundscape (and the bell at the end of a session) survives a locked screen;
 *   2. put transport controls where the reader already is — the notification
 *      shade and the headset buttons, via a framework MediaSession.
 *
 * The audio graph itself lives in {@link SoundscapeBus}; this class never touches
 * samples. That separation is why the notification and the engine cannot drift.
 */
public class SoundscapeService extends Service {

    public static final String ACTION_START = "app.kairos.sound.START";
    public static final String ACTION_PAUSE = "app.kairos.sound.PAUSE";
    public static final String ACTION_RESUME = "app.kairos.sound.RESUME";
    public static final String ACTION_STOP = "app.kairos.sound.STOP";

    private static final String CHANNEL_ID = "kairos_soundscape";
    private static final int NOTIFICATION_ID = 4418;

    private MediaSession session;
    private boolean paused = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        createSession();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            SoundscapeBus.stop();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            paused = true;
            SoundscapeBus.pause();
        } else if (ACTION_RESUME.equals(action)) {
            paused = false;
            SoundscapeBus.resume();
        } else {
            paused = false;
        }

        publish();
        return START_STICKY;
    }

    private void publish() {
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {
            // Denied notification permission: audio still needs to play.
        }
        updateSessionState();
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(this, 0, open, pendingFlags);

        PendingIntent toggle = PendingIntent.getService(
                this,
                1,
                new Intent(this, SoundscapeService.class)
                        .setAction(paused ? ACTION_RESUME : ACTION_PAUSE),
                pendingFlags);

        PendingIntent stop = PendingIntent.getService(
                this, 2, new Intent(this, SoundscapeService.class).setAction(ACTION_STOP), pendingFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("فضای صوتی تمرکز")
                .setContentText(paused ? "متوقف شد" : "در حال پخش")
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, paused ? "ادامه" : "توقف", toggle)
                .addAction(0, "پایان", stop)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "فضای صوتی تمرکز",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("پخش صداهای محیطی کایروس");
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }

    /**
     * Headset buttons. A focus app spends its life in a pocket with wired
     * headphones, so play/pause on the cable must work without opening the app.
     */
    private void createSession() {
        try {
            session = new MediaSession(this, "KAIROS");
            session.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    paused = false;
                    SoundscapeBus.resume();
                    publish();
                }

                @Override
                public void onPause() {
                    paused = true;
                    SoundscapeBus.pause();
                    publish();
                }

                @Override
                public void onStop() {
                    SoundscapeBus.stop();
                    stopSelf();
                }
            });
            session.setActive(true);
        } catch (Exception ignored) {
            // Some OEM builds restrict MediaSession; audio keeps working.
        }
    }

    private void updateSessionState() {
        if (session == null) return;
        try {
            PlaybackState state = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY
                            | PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY_PAUSE
                            | PlaybackState.ACTION_STOP)
                    .setState(paused ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build();
            session.setPlaybackState(state);
        } catch (Exception ignored) {
            // ignore
        }
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            try {
                session.setActive(false);
                session.release();
            } catch (Exception ignored) {
                // already released
            }
            session = null;
        }
        SoundscapeBus.onServiceDestroyed();
        super.onDestroy();
    }
}
