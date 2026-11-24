package app.kairos.productivity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

import androidx.core.content.ContextCompat;

/**
 * The single owner of the native soundscape.
 *
 * The plugin, the foreground service and the notification actions all talk to
 * this one object, so there is exactly one AudioTrack in the process no matter
 * how many times the studio is toggled. The service exists only to keep the app
 * alive and visible in the shade; the sound itself lives here.
 */
public final class SoundscapeBus {

    private static NativeSoundscape engine;
    private static final RecordedSoundscape recordings = new RecordedSoundscape();
    private static boolean serviceRunning = false;

    private SoundscapeBus() {
    }

    private static synchronized NativeSoundscape engine() {
        if (engine == null) engine = new NativeSoundscape();
        return engine;
    }

    private static Map<String, Float> generatedOnly(Map<String, Float> mix) {
        Map<String, Float> result = new HashMap<>();
        for (Map.Entry<String, Float> entry : mix.entrySet()) {
            if (RecordedSoundscape.fileFor(entry.getKey()) == null) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static synchronized void start(Context context, Map<String, Float> mix, float master) throws Exception {
        recordings.setMix(context, mix, master);
        NativeSoundscape sound = engine();
        sound.setMix(generatedOnly(mix), master);
        sound.start();

        if (context != null && !serviceRunning) {
            try {
                Intent intent = new Intent(context, SoundscapeService.class);
                intent.setAction(SoundscapeService.ACTION_START);
                if (Build.VERSION.SDK_INT >= 26) {
                    ContextCompat.startForegroundService(context, intent);
                } else {
                    context.startService(intent);
                }
                serviceRunning = true;
            } catch (Exception ignored) {
                // A missing background-start permission must not silence the app.
            }
        }
    }

    public static synchronized void update(Context context, Map<String, Float> mix, float master) throws Exception {
        recordings.setMix(context, mix, master);
        engine().setMix(generatedOnly(mix), master);
    }

    public static synchronized void pause() {
        recordings.pause();
        engine().pause();
    }

    public static synchronized void resume() {
        recordings.resume();
        engine().resume();
    }

    public static synchronized void stop() {
        recordings.stop();
        engine().stop();
        serviceRunning = false;
    }

    public static synchronized boolean isPlaying() {
        return recordings.isPlaying() || (engine != null && engine.isRunning());
    }

    public static synchronized boolean isStarted() {
        return engine != null;
    }

    /** Called by the service when it is destroyed by the system. */
    static synchronized void onServiceDestroyed() {
        serviceRunning = false;
        recordings.stop();
        if (engine != null) engine.stop();
    }

    public static void playBell() {
        NativeSoundscape.playBell();
    }

    /** Turns the JSON the page sends into the map the engine speaks. */
    public static Map<String, Float> parseMix(Map<String, Object> raw) {
        Map<String, Float> result = new HashMap<>();
        if (raw == null) return result;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number) {
                float level = ((Number) value).floatValue();
                if (level > 0.01f) result.put(entry.getKey(), Math.min(1f, level));
            }
        }
        return result;
    }
}
