package app.kairos.productivity;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Local Ogg recordings, mixed with conservative headroom. No network access. */
final class RecordedSoundscape {
    private final Map<String, MediaPlayer> players = new HashMap<>();
    private boolean paused = false;

    static String fileFor(String id) {
        switch (id) {
            case "rain": return "rain.ogg";
            case "ocean": return "waves.ogg";
            case "wind": return "wind.ogg";
            case "fire": return "fireplace.ogg";
            case "forest": return "birds.ogg";
            case "cafe": return "coffee-shop.ogg";
            default: return null;
        }
    }

    synchronized void setMix(Context context, Map<String, Float> mix, float master) throws Exception {
        Iterator<Map.Entry<String, MediaPlayer>> iterator = players.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, MediaPlayer> entry = iterator.next();
            Float level = mix.get(entry.getKey());
            if (level == null || level <= 0.01f) {
                entry.getValue().release();
                iterator.remove();
            }
        }
        float total = 0f;
        for (Float level : mix.values()) if (level != null) total += Math.max(0f, level);
        float divisor = Math.max(1f, total);
        for (Map.Entry<String, Float> entry : mix.entrySet()) {
            String file = fileFor(entry.getKey());
            float level = entry.getValue() == null ? 0f : entry.getValue();
            if (file == null || level <= 0.01f) continue;
            MediaPlayer player = players.get(entry.getKey());
            if (player == null) {
                player = new MediaPlayer();
                try (AssetFileDescriptor asset = context.getAssets().openFd("public/sounds/" + file)) {
                    player.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                    player.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
                    player.setLooping(true);
                    player.prepare();
                    players.put(entry.getKey(), player);
                } catch (Exception e) {
                    player.release();
                    throw e;
                }
                float volume = Math.max(0f, Math.min(1f, master * level * 0.7f / divisor));
                player.setVolume(volume, volume);
                if (!paused) player.start();
            } else {
                float volume = Math.max(0f, Math.min(1f, master * level * 0.7f / divisor));
                player.setVolume(volume, volume);
            }
        }
    }

    synchronized void pause() {
        paused = true;
        for (MediaPlayer player : players.values()) if (player.isPlaying()) player.pause();
    }

    synchronized void resume() {
        paused = false;
        for (MediaPlayer player : players.values()) if (!player.isPlaying()) player.start();
    }

    synchronized void stop() {
        for (MediaPlayer player : players.values()) player.release();
        players.clear();
        paused = false;
    }

    synchronized boolean isPlaying() {
        for (MediaPlayer player : players.values()) if (player.isPlaying()) return true;
        return false;
    }
}
