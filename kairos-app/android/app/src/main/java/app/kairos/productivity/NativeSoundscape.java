package app.kairos.productivity;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * KAIROS native soundscape engine.
 *
 * ── Why this exists ──────────────────────────────────────────────────────────
 * The 1.7 engine synthesised everything in the WebView with Web Audio. It sounded
 * fine on a desktop and failed on a phone for one structural reason: Android
 * suspends a backgrounded WebView. Lock the screen during a 45-minute block and
 * the audio graph was frozen with the page — no soundscape, and no bell at the
 * end of the session either. A focus timer whose sound dies at the exact moment
 * you stop looking at it is not a feature.
 *
 * So the sound moved into the app process: a foreground service drives one
 * AudioTrack at 48 kHz stereo, streaming, with the same eleven layers generated
 * from noise colours, biquad filters and LFOs. Nothing is downloaded, nothing is
 * licensed, and the loop is mathematically endless (there is no buffer to wrap).
 *
 * ── Design notes ─────────────────────────────────────────────────────────────
 *   · one track, one thread, fixed 1024-frame blocks: no allocation inside the
 *     audio loop, so the JVM never pauses to collect garbage mid-swell;
 *   · filters are re-coefficiented every 64 blocks, not every sample: a slow LFO
 *     does not need 48 000 filter updates per second;
 *   · a soft-clip curve instead of hard limiting: four layers at once stay loud
 *     without the ugly square edge of a clamp;
 *   · stop() joins the thread, so starting and stopping repeatedly (the studio's
 *     play/pause) cannot leak threads or tracks.
 */
public final class NativeSoundscape {

    private static final int SAMPLE_RATE = 48000;
    private static final int BLOCK = 1024;

    private final Map<String, Float> mix = new HashMap<>();
    private volatile float master = 0.8f;
    private volatile boolean running = false;
    private volatile boolean paused = false;

    private AudioTrack track;
    private Thread thread;

    /** Layer ids, in the same order the studio uses. */
    private static final String[] IDS = {
            "rain", "ocean", "wind", "fire", "forest", "cafe",
            "brown", "pink", "white", "drone", "binaural"
    };

    private Layer[] layers;

    public NativeSoundscape() {
        layers = new Layer[IDS.length];
        for (int i = 0; i < IDS.length; i++) {
            layers[i] = new Layer(i, presetFor(IDS[i]));
        }
    }

    public synchronized void setMix(Map<String, Float> next, float masterLevel) {
        mix.clear();
        if (next != null) {
            for (Map.Entry<String, Float> entry : next.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0.01f) {
                    mix.put(entry.getKey(), Math.min(1f, Math.max(0f, entry.getValue())));
                }
            }
        }
        master = Math.min(1f, Math.max(0f, masterLevel));
    }

    public boolean isRunning() {
        return running && !paused;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        paused = false;

        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minBuffer, BLOCK * 2 * 2 * 4);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();

        track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        track.play();

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                renderLoop();
            }
        }, "kairos-soundscape");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public synchronized void pause() {
        paused = true;
        if (track != null) {
            try {
                track.pause();
            } catch (Exception ignored) {
                // already stopped
            }
        }
    }

    public synchronized void resume() {
        paused = false;
        if (track != null) {
            try {
                track.play();
            } catch (Exception ignored) {
                // the track is gone; the next start() rebuilds it
            }
        }
    }

    public synchronized void stop() {
        running = false;
        paused = false;
        AudioTrack local = track;
        track = null;
        if (local != null) {
            try {
                local.pause();
                local.flush();
                local.stop();
            } catch (Exception ignored) {
                // already dead
            }
            try {
                local.release();
            } catch (Exception ignored) {
                // already released
            }
        }
        Thread localThread = thread;
        thread = null;
        if (localThread != null) {
            try {
                localThread.join(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Renders blocks until stopped. No allocation inside the loop. */
    private void renderLoop() {
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];
        short[] out = new short[BLOCK * 2];
        int frame = 0;

        while (running) {
            AudioTrack local = track;
            if (local == null) break;

            if (paused) {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            java.util.Arrays.fill(left, 0f);
            java.util.Arrays.fill(right, 0f);

            float masterNow = master;
            boolean anyActive = false;

            for (Layer layer : layers) {
                Float level = mix.get(IDS[layer.index]);
                if (level == null || level <= 0.01f) {
                    layer.gain = approach(layer.gain, 0f);
                    if (layer.gain < 0.0005f) continue;
                } else {
                    layer.gain = approach(layer.gain, level * masterNow);
                }
                if (layer.gain <= 0.0005f) continue;

                anyActive = true;
                layer.render(left, right, frame);
            }

            frame += BLOCK;

            if (!anyActive) {
                // Nothing selected: keep the track alive but silent.
                try {
                    Thread.sleep(30);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            for (int i = 0; i < BLOCK; i++) {
                float l = softClip(left[i]);
                float r = softClip(right[i]);
                out[i * 2] = (short) (l * 32767f);
                out[i * 2 + 1] = (short) (r * 32767f);
            }

            try {
                local.write(out, 0, out.length);
            } catch (Exception ignored) {
                break;
            }
        }
    }

    private static float approach(float current, float target) {
        // ~120 ms ramp at 48 kHz blocks — long enough to be a crossfade, short
        // enough that the slider still feels live.
        return current + (target - current) * 0.08f;
    }

    /** Cubic soft clip: transparent below 0.7, saturating instead of clipping. */
    private static float softClip(float x) {
        if (x > 3f) return 1f;
        if (x < -3f) return -1f;
        float x2 = x * x;
        return x * (27f + x2) / (27f + 9f * x2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layers
    // ─────────────────────────────────────────────────────────────────────────

    private static final int WHITE = 0, PINK = 1, BROWN = 2, TONES = 3;
    private static final int LOWPASS = 0, BANDPASS = 1, HIGHPASS = 2;

    private static final class Preset {
        int source;
        int filter;
        float cutoff;
        float q;
        float lfoRate;      // filter LFO, Hz
        float lfoDepth;     // filter LFO, Hz
        float ampRate;      // amplitude LFO, Hz
        float ampDepth;     // 0..1
        float toneA = 0f;
        float toneB = 0f;
        boolean wide;       // hard L/R split (binaural)
        boolean crackle;    // sparse transients (fire, birds)
        Preset(int source, int filter, float cutoff, float q) {
            this.source = source;
            this.filter = filter;
            this.cutoff = cutoff;
            this.q = q;
        }
    }

    private static Preset presetFor(String id) {
        switch (id) {
            case "rain": {
                Preset p = new Preset(WHITE, HIGHPASS, 1200f, 0.7f);
                p.lfoRate = 0.12f;
                p.lfoDepth = 200f;
                return p;
            }
            case "ocean": {
                Preset p = new Preset(BROWN, LOWPASS, 620f, 0.6f);
                p.ampRate = 0.075f;
                p.ampDepth = 0.45f;
                return p;
            }
            case "wind": {
                Preset p = new Preset(PINK, BANDPASS, 480f, 0.9f);
                p.lfoRate = 0.05f;
                p.lfoDepth = 300f;
                return p;
            }
            case "fire": {
                Preset p = new Preset(BROWN, LOWPASS, 1100f, 0.7f);
                p.crackle = true;
                p.ampRate = 0.35f;
                p.ampDepth = 0.12f;
                return p;
            }
            case "forest": {
                Preset p = new Preset(PINK, BANDPASS, 3200f, 0.7f);
                p.lfoRate = 0.09f;
                p.lfoDepth = 600f;
                p.crackle = true;
                return p;
            }
            case "cafe": {
                Preset p = new Preset(PINK, LOWPASS, 780f, 0.6f);
                p.ampRate = 0.21f;
                p.ampDepth = 0.22f;
                return p;
            }
            case "brown":
                return new Preset(BROWN, LOWPASS, 4200f, 0.5f);
            case "pink":
                return new Preset(PINK, LOWPASS, 16000f, 0.5f);
            case "white":
                return new Preset(WHITE, HIGHPASS, 20f, 0.5f);
            case "drone": {
                Preset p = new Preset(TONES, LOWPASS, 420f, 0.7f);
                p.toneA = 110f;
                p.toneB = 110.7f;
                return p;
            }
            case "binaural":
            default: {
                Preset p = new Preset(TONES, LOWPASS, 3000f, 0.7f);
                p.toneA = 200f;
                p.toneB = 210f;
                p.wide = true;
                return p;
            }
        }
    }

    /** Per-layer DSP state. */
    private static final class Layer {
        final int index;
        final Preset p;
        float gain = 0f;

        // noise / oscillator state
        private int rng = 0x9e3779b9;
        private final float[] pinkRows = new float[7];
        private float brown = 0f;
        private double phaseA = 0;
        private double phaseB = 0;

        // filter state (two biquads cascaded for a steeper slope where it helps)
        private final float[] b0 = new float[2], b1 = new float[2], b2 = new float[2];
        private final float[] a1 = new float[2], a2 = new float[2];
        private final float[] z1 = new float[2], z2 = new float[2];

        // modulation state
        private double lfoPhase = 0;
        private double ampPhase = 0;
        private float crackleEnv = 0f;
        private int crackleCountdown = 0;
        private int coeffCountdown = 0;

        Layer(int index, Preset preset) {
            this.index = index;
            this.p = preset;
            updateCoefficients(p.cutoff);
        }

        private float random() {
            rng ^= rng << 13;
            rng ^= rng >>> 17;
            rng ^= rng << 5;
            return ((rng & 0x7fffffff) / (float) 0x7fffffff) * 2f - 1f;
        }

        private float noise() {
            float white = random();
            switch (p.source) {
                case BROWN: {
                    brown = (brown + 0.02f * white) / 1.02f;
                    return brown * 3.2f;
                }
                case PINK: {
                    // Paul Kellet's economy pink filter.
                    pinkRows[0] = 0.99886f * pinkRows[0] + white * 0.0555179f;
                    pinkRows[1] = 0.99332f * pinkRows[1] + white * 0.0750759f;
                    pinkRows[2] = 0.96900f * pinkRows[2] + white * 0.1538520f;
                    pinkRows[3] = 0.86650f * pinkRows[3] + white * 0.3104856f;
                    pinkRows[4] = 0.55000f * pinkRows[4] + white * 0.5329522f;
                    pinkRows[5] = -0.7616f * pinkRows[5] - white * 0.0168980f;
                    float sum = pinkRows[0] + pinkRows[1] + pinkRows[2] + pinkRows[3]
                            + pinkRows[4] + pinkRows[5] + pinkRows[6] + white * 0.5362f;
                    pinkRows[6] = white * 0.115926f;
                    return sum * 0.11f;
                }
                case WHITE:
                default:
                    return white * 0.35f;
            }
        }

        private void updateCoefficients(float cutoff) {
            float f = Math.min(0.45f, cutoff / SAMPLE_RATE);
            float omega = (float) (2 * Math.PI * f);
            float sin = (float) Math.sin(omega);
            float cos = (float) Math.cos(omega);
            float alpha = sin / (2f * Math.max(0.4f, p.q));

            float nb0, nb1, nb2, na0, na1, na2;
            if (p.filter == LOWPASS) {
                nb0 = (1f - cos) / 2f;
                nb1 = 1f - cos;
                nb2 = (1f - cos) / 2f;
                na0 = 1f + alpha;
                na1 = -2f * cos;
                na2 = 1f - alpha;
            } else if (p.filter == HIGHPASS) {
                nb0 = (1f + cos) / 2f;
                nb1 = -(1f + cos);
                nb2 = (1f + cos) / 2f;
                na0 = 1f + alpha;
                na1 = -2f * cos;
                na2 = 1f - alpha;
            } else { // bandpass, constant skirt gain
                nb0 = alpha;
                nb1 = 0f;
                nb2 = -alpha;
                na0 = 1f + alpha;
                na1 = -2f * cos;
                na2 = 1f - alpha;
            }

            for (int s = 0; s < 2; s++) {
                b0[s] = nb0 / na0;
                b1[s] = nb1 / na0;
                b2[s] = nb2 / na0;
                a1[s] = na1 / na0;
                a2[s] = na2 / na0;
            }
        }

        void render(float[] left, float[] right, int frameBase) {
            float ampMod = 1f;
            if (p.ampRate > 0f) {
                ampPhase += 2 * Math.PI * p.ampRate * BLOCK / SAMPLE_RATE;
                ampMod = 1f - p.ampDepth + p.ampDepth * (float) (0.5 + 0.5 * Math.sin(ampPhase));
            }

            if (p.lfoRate > 0f) {
                lfoPhase += 2 * Math.PI * p.lfoRate * BLOCK / SAMPLE_RATE;
                if (coeffCountdown <= 0) {
                    float mod = p.lfoDepth * (float) Math.sin(lfoPhase);
                    updateCoefficients(Math.max(60f, p.cutoff + mod));
                    coeffCountdown = 24;
                }
                coeffCountdown -= 1;
            }

            float gainNow = gain * ampMod;

            if (p.source == TONES) {
                double incA = 2 * Math.PI * p.toneA / SAMPLE_RATE;
                double incB = 2 * Math.PI * p.toneB / SAMPLE_RATE;
                for (int i = 0; i < BLOCK; i++) {
                    float a = (float) Math.sin(phaseA) * 0.22f;
                    float b = (float) Math.sin(phaseB) * 0.22f;
                    phaseA += incA;
                    phaseB += incB;
                    if (p.wide) {
                        left[i] += a * gainNow;
                        right[i] += b * gainNow;
                    } else {
                        left[i] += (a + b) * 0.7f * gainNow;
                        right[i] += (a + b) * 0.7f * gainNow;
                    }
                }
                phaseA %= 2 * Math.PI;
                phaseB %= 2 * Math.PI;
                return;
            }

            for (int i = 0; i < BLOCK; i++) {
                float sample = noise();

                if (p.crackle) {
                    if (crackleCountdown-- <= 0) {
                        crackleCountdown = 4000 + (int) (Math.random() * 26000);
                        crackleEnv = 0.5f + (float) Math.random() * 0.5f;
                    }
                    if (crackleEnv > 0.0005f) {
                        sample += sample * crackleEnv * 1.8f;
                        crackleEnv *= 0.9955f;
                    }
                }

                // cascade the biquad twice for a cleaner skirt
                float y = filter(sample, 0);
                y = filter(y, 1);
                left[i] += y * gainNow;
                right[i] += y * gainNow;
            }
        }

        private float filter(float x, int stage) {
            float y = b0[stage] * x + z1[stage];
            z1[stage] = b1[stage] * x - a1[stage] * y + z2[stage];
            z2[stage] = b2[stage] * x - a2[stage] * y;
            return y;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // The bell
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The end-of-session bell, synthesised on demand: 264/396/528/792 Hz struck
     * low to high, 70 ms apart, each with its own exponential decay. Plays on a
     * one-shot track so it can ring over (or instead of) the soundscape, and it
     * works with the screen off because it lives in the service, not the page.
     */
    public static void playBell() {
        final float seconds = 3.2f;
        final int frames = (int) (SAMPLE_RATE * seconds);
        final short[] buffer = new short[frames * 2];

        final float[][] partials = {
                {264f, 0.00f, 0.32f, 2.6f},
                {396f, 0.07f, 0.23f, 2.1f},
                {528f, 0.13f, 0.16f, 1.7f},
                {792f, 0.20f, 0.10f, 1.2f},
        };

        for (int i = 0; i < frames; i++) {
            float t = i / (float) SAMPLE_RATE;
            float value = 0f;
            for (float[] partial : partials) {
                float start = partial[1];
                if (t < start) continue;
                float local = t - start;
                float envelope = (float) Math.exp(-local * 2.2f) * (1f - (float) Math.exp(-local * 260f));
                value += (float) Math.sin(2 * Math.PI * partial[0] * local) * partial[2] * envelope;
            }
            float clipped = softClip(value * 1.15f);
            buffer[i * 2] = (short) (clipped * 32767f);
            buffer[i * 2 + 1] = (short) (clipped * 32767f);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                AudioTrack bell = null;
                try {
                    AudioAttributes attributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    AudioFormat format = new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build();

                    bell = new AudioTrack.Builder()
                            .setAudioAttributes(attributes)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(buffer.length * 2)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build();
                    bell.write(buffer, 0, buffer.length);
                    bell.play();
                    Thread.sleep((long) (seconds * 1000) + 120);
                } catch (Exception ignored) {
                    // A blocked audio device must never break the session.
                } finally {
                    if (bell != null) {
                        try {
                            bell.stop();
                            bell.release();
                        } catch (Exception ignored) {
                            // already released
                        }
                    }
                }
            }
        }, "kairos-bell").start();
    }

    /** True when the device can build an output track at all. */
    public static boolean isSupported() {
        if (Build.VERSION.SDK_INT < 21) return false;
        return AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) > 0;
    }
}
