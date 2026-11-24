package app.kairos.productivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Map;

/**
 * Capacitor bridge for native audio.
 *
 * The page keeps owning the mixer UI, the mix itself and the persisted settings;
 * this plugin only carries the mix across the bridge and reports whether the
 * native engine is actually playing. Everything it exposes maps 1:1 to
 * {@link SoundscapeBus}, so there is no second source of truth.
 *
 * Methods are intentionally tolerant: calling stop() when nothing plays, or
 * update() before start(), is a no-op rather than an error, because the studio
 * calls them from React effects that run in unpredictable orders.
 */
@CapacitorPlugin(name = "KairosAudio")
public class KairosAudioPlugin extends Plugin {

    private Map<String, Float> readMix(PluginCall call) {
        JSObject mixObject = call.getObject("mix");
        if (mixObject == null) return null;
        java.util.Map<String, Object> raw = new java.util.HashMap<>();
        for (java.util.Iterator<String> keys = mixObject.keys(); keys.hasNext(); ) {
            String key = keys.next();
            raw.put(key, mixObject.opt(key));
        }
        return SoundscapeBus.parseMix(raw);
    }

    private float readMaster(PluginCall call) {
        Double value = call.getDouble("master", 0.8);
        float master = value == null ? 0.8f : value.floatValue();
        if (master < 0f) master = 0f;
        if (master > 1f) master = 1f;
        return master;
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (!NativeSoundscape.isSupported()) {
            call.reject("audio_unsupported");
            return;
        }
        Map<String, Float> mix = readMix(call);
        if (mix == null) mix = new java.util.HashMap<>();
        try {
            SoundscapeBus.start(getContext(), mix, readMaster(call));
            resolveState(call);
        } catch (Exception error) {
            SoundscapeBus.stop();
            call.reject("recorded_audio_start_failed", error);
        }
    }

    @PluginMethod
    public void update(PluginCall call) {
        Map<String, Float> mix = readMix(call);
        if (mix == null) mix = new java.util.HashMap<>();
        try {
            SoundscapeBus.update(getContext(), mix, readMaster(call));
            resolveState(call);
        } catch (Exception error) {
            SoundscapeBus.stop();
            call.reject("recorded_audio_update_failed", error);
        }
    }

    @PluginMethod
    public void pause(PluginCall call) {
        SoundscapeBus.pause();
        resolveState(call);
    }

    @PluginMethod
    public void resume(PluginCall call) {
        SoundscapeBus.resume();
        resolveState(call);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        SoundscapeBus.stop();
        android.content.Intent intent = new android.content.Intent(getContext(), SoundscapeService.class);
        intent.setAction(SoundscapeService.ACTION_STOP);
        try {
            getContext().stopService(intent);
        } catch (Exception ignored) {
            // service already gone
        }
        resolveState(call);
    }

    @PluginMethod
    public void playBell(PluginCall call) {
        SoundscapeBus.playBell();
        JSObject result = new JSObject();
        result.put("ok", true);
        call.resolve(result);
    }

    @PluginMethod
    public void state(PluginCall call) {
        resolveState(call);
    }

    private void resolveState(PluginCall call) {
        JSObject result = new JSObject();
        result.put("playing", SoundscapeBus.isPlaying());
        result.put("supported", NativeSoundscape.isSupported());
        call.resolve(result);
    }
}
