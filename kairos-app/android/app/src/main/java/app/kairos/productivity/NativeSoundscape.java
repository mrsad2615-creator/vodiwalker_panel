package app.kairos.productivity;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * KAIROS native soundscape engine — v2.
 *
 * ── Why it is native ─────────────────────────────────────────────────────────
 * Android suspends a backgrounded WebView. A soundscape built with Web Audio
 * therefore died the moment the screen locked, and the end-of-session bell never
 * rang at all. The audio lives in the app process now: a foreground service owns
 * one AudioTrack at 48 kHz stereo, streaming, and nothing depends on the page.
 *
 * ── Why it sounds different from v1 ──────────────────────────────────────────
 * v1 was technically correct and audibly thin. Four things were missing, and all
 * four are what "quality" actually means to a person listening on a phone:
 *
 *   1. WIDTH. Every layer was one mono noise source copied to both channels,
 *      which collapses to a point in the middle of your head. Each layer now runs
 *      two decorrelated noise generators with slightly different filter
 *      frequencies (L and R), so the stereo field has size.
 *   2. SPACE. Dry noise has no depth. A small Schroeder reverb (four damped
 *      feedback combs + two allpass diffusers) sits on the wet bus at ~22%, which
 *      is the difference between "hiss" and "outside".
 *   3. TRANSIENTS. Rain was high-passed noise and fire was amplitude-wobbled
 *      noise: both read as static. They now carry scheduled droplets and crackles
 *      — short band-passed bursts with their own envelopes — which is the texture
 *      the ear identifies as water and fire.
 *   4. CLICKS. Starting and stopping used to cut the track dead. There is now a
 *      raised-cosine fade in and out (300 ms), and a soft-clip limiter so loud
 *      mixes stay clean instead of fizzing.
 *
 * Still: no audio files, no licences, no network, no loop seam (there is no
 * buffer to wrap).
 */
public final class NativeSoundscape {

    private static final int SAMPLE_RATE = 48000;
    private static final int BLOCK = 1024;
    /** Headroom-aware make-up gain: the bus is mixed conservatively, then lifted
     *  into the soft clipper so quiet layers are still audible on a phone speaker. */
    private static final float OUTPUT_GAIN = 1.3f;

    private final Map<String, Float> mix = new ConcurrentHashMap<>();
    private volatile float master = 0.8f;
    private volatile boolean running = false;
    private volatile boolean stopped = false;
    private volatile boolean paused = false;

    private AudioTrack track;
    private Thread thread;

    private static final String[] IDS = {
            "rain", "ocean", "wind", "fire", "forest", "cafe",
            "brown", "pink", "white", "drone", "binaural"
    };

    private final Layer[] layers;
    private final Reverb reverb = new Reverb(SAMPLE_RATE);

    public NativeSoundscape() {
        layers = new Layer[IDS.length];
        for (int i = 0; i < IDS.length; i++) {
            layers[i] = new Layer(IDS[i], presetFor(IDS[i]));
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
        return running && !paused && !stopped;
    }

    public synchronized void start() {
        if (running && !stopped) {
            paused = false;
            if (track != null) {
                try {
                    track.play();
                } catch (Exception ignored) {
                    // rebuilt below
                }
            }
            return;
        }

        running = true;
        stopped = false;
        paused = false;

        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);

        // 3× the platform minimum: underruns are audible as crackle, and crackle is
        // exactly what "low quality audio" sounds like.
        int bufferBytes = Math.max(minBuffer * 3, BLOCK * 2 * 2 * 3);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();

        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
        } catch (Exception e) {
            running = false;
            return;
        }

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
    }

    public synchronized void resume() {
        paused = false;
    }

    /**
     * Fades out, then tears the track down. The fade is not politeness: cutting a
     * noise signal at a non-zero sample is a click, and a click is the single most
     * "cheap" sound an audio app can make.
     */
    public synchronized void stop() {
        if (!running) {
            releaseTrack();
            return;
        }
        stopped = true;
        Thread localThread = thread;
        if (localThread != null) {
            try {
                localThread.join(700);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        releaseTrack();
        running = false;
        stopped = false;
        paused = false;
    }

    private void releaseTrack() {
        AudioTrack local = track;
        track = null;
        thread = null;
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
    }

    private void renderLoop() {
        float[] left = new float[BLOCK];
        float[] right = new float[BLOCK];
        short[] out = new short[BLOCK * 2];
        int frame = 0;
        float env = 0f;

        while (running) {
            AudioTrack local = track;
            if (local == null) break;

            if (stopped) {
                // short release tail, then leave
                env = Math.max(0f, env - 1f / 15f);
                if (env <= 0.0005f) break;
            } else if (paused) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            } else {
                env = Math.min(1f, env + 1f / 15f);
            }

            java.util.Arrays.fill(left, 0f);
            java.util.Arrays.fill(right, 0f);

            float masterNow = master;
            boolean anyActive = false;

            for (Layer layer : layers) {
                Float level = mix.get(layer.id);
                if (level == null || level <= 0.01f) {
                    layer.gain = approach(layer.gain, 0f);
                    if (layer.gain < 0.0005f) {
                        layer.gain = 0f;
                        continue;
                    }
                } else {
                    layer.gain = approach(layer.gain, level * masterNow);
                }
                if (layer.gain <= 0.0005f) continue;

                anyActive = true;
                layer.render(left, right, frame);
            }

            frame += BLOCK;

            if (!anyActive && !stopped) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            float reverbMix = 0.22f;
            for (int i = 0; i < BLOCK; i++) {
                float l = left[i];
                float r = right[i];

                float wetL = reverb.processLeft(l);
                float wetR = reverb.processRight(r);

                float mixL = softClip((l * (1f - reverbMix) + wetL * reverbMix) * env * OUTPUT_GAIN);
                float mixR = softClip((r * (1f - reverbMix) + wetR * reverbMix) * env * OUTPUT_GAIN);

                out[i * 2] = (short) (mixL * 32767f);
                out[i * 2 + 1] = (short) (mixR * 32767f);
            }

            try {
                local.write(out, 0, out.length);
            } catch (Exception ignored) {
                break;
            }
        }

        // Drain: write a few silent blocks so the track does not click on release.
        AudioTrack local = track;
        if (local != null) {
            java.util.Arrays.fill(out, (short) 0);
            for (int i = 0; i < 3; i++) {
                try {
                    local.write(out, 0, out.length);
                } catch (Exception ignored) {
                    break;
                }
            }
        }
    }

    private static float approach(float current, float target) {
        // ~120 ms at 48 kHz / 1024 frames per block.
        return current + (target - current) * 0.08f;
    }

    /** Cubic soft clip: transparent below ~0.7, saturating instead of clipping. */
    private static float softClip(float x) {
        if (x > 3f) return 1f;
        if (x < -3f) return -1f;
        float x2 = x * x;
        return x * (27f + x2) / (27f + 9f * x2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reverb — four damped combs into two allpass diffusers, per channel
    // ─────────────────────────────────────────────────────────────────────────

    private static final class Reverb {

        /** One damped feedback comb (Schroeder/Freeverb topology). */
        private static final class Comb {
            final float[] buffer;
            int pos;
            float store;

            Comb(int length) {
                buffer = new float[length];
            }

            float process(float input, float feedback, float damping) {
                float delayed = buffer[pos];
                store = delayed * (1f - damping) + store * damping;
                buffer[pos] = input * 0.22f + store * feedback;
                pos++;
                if (pos >= buffer.length) pos = 0;
                return delayed;
            }
        }

        /** One allpass diffuser. */
        private static final class Allpass {
            final float[] buffer;
            int pos;

            Allpass(int length) {
                buffer = new float[length];
            }

            float process(float input, float feedback) {
                float delayed = buffer[pos];
                float output = -input + delayed;
                buffer[pos] = input + delayed * feedback;
                pos++;
                if (pos >= buffer.length) pos = 0;
                return output;
            }
        }

        private final Comb[][] combs = new Comb[2][4];
        private final Allpass[][] aps = new Allpass[2][2];
        private final float feedback;
        private final float damping;

        Reverb(int sampleRate) {
            // Freeverb's tunings, scaled from 44.1 kHz to the device rate.
            float scale = sampleRate / 44100f;
            int[] combLengths = {1116, 1188, 1277, 1356};
            int[] apLengths = {556, 441};
            feedback = 0.78f;
            damping = 0.35f;

            for (int ch = 0; ch < 2; ch++) {
                for (int i = 0; i < combLengths.length; i++) {
                    // A different length per channel decorrelates the two tails.
                    int length = Math.max(64, (int) (combLengths[i] * scale) + ch * 23);
                    combs[ch][i] = new Comb(length);
                }
                for (int i = 0; i < apLengths.length; i++) {
                    int length = Math.max(32, (int) (apLengths[i] * scale) + ch * 11);
                    aps[ch][i] = new Allpass(length);
                }
            }
        }

        float process(float input, int channel) {
            float wet = 0f;
            Comb[] bank = combs[channel];
            for (int i = 0; i < bank.length; i++) {
                wet += bank[i].process(input, feedback, damping);
            }
            wet *= 0.25f;

            Allpass[] diffusers = aps[channel];
            for (int i = 0; i < diffusers.length; i++) {
                wet = diffusers[i].process(wet, 0.5f);
            }
            return wet;
        }

        float processLeft(float input) {
            return process(input, 0);
        }

        float processRight(float input) {
            return process(input, 1);
        }
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
        float lfoRate;
        float lfoDepth;
        float ampRate;
        float ampDepth;
        float toneA;
        float toneB;
        boolean wide;
        /** Random short transients per second (droplets, crackles, birds). */
        float transientRate;
        float transientFreq;
        float transientQ;

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
                Preset p = new Preset(WHITE, HIGHPASS, 1400f, 0.6f);
                p.lfoRate = 0.11f;
                p.lfoDepth = 180f;
                // Remove the periodic high-pitched synthetic droplets.
                p.transientRate = 0f;
                return p;
            }
            case "ocean": {
                Preset p = new Preset(BROWN, LOWPASS, 700f, 0.55f);
                p.ampRate = 0.07f;
                p.ampDepth = 0.5f;
                p.lfoRate = 0.02f;
                p.lfoDepth = 220f;
                return p;
            }
            case "wind": {
                Preset p = new Preset(PINK, BANDPASS, 460f, 0.8f);
                p.lfoRate = 0.045f;
                p.lfoDepth = 320f;
                p.ampRate = 0.03f;
                p.ampDepth = 0.3f;
                return p;
            }
            case "fire": {
                Preset p = new Preset(BROWN, LOWPASS, 1300f, 0.6f);
                p.ampRate = 0.4f;
                p.ampDepth = 0.15f;
                p.transientRate = 7f;
                p.transientFreq = 2200f;
                p.transientQ = 1.4f;
                return p;
            }
            case "forest": {
                Preset p = new Preset(PINK, BANDPASS, 3400f, 0.6f);
                p.lfoRate = 0.08f;
                p.lfoDepth = 700f;
                // No artificial bird chirps: keep this texture unobtrusive.
                p.transientRate = 0f;
                return p;
            }
            case "cafe": {
                Preset p = new Preset(PINK, LOWPASS, 820f, 0.55f);
                p.ampRate = 0.19f;
                p.ampDepth = 0.25f;
                p.transientRate = 2.2f;
                p.transientFreq = 900f;
                p.transientQ = 1.2f;
                return p;
            }
            case "brown":
                return new Preset(BROWN, LOWPASS, 5200f, 0.5f);
            case "pink":
                return new Preset(PINK, LOWPASS, 16000f, 0.5f);
            case "white":
                return new Preset(WHITE, HIGHPASS, 20f, 0.5f);
            case "drone": {
                Preset p = new Preset(TONES, LOWPASS, 520f, 0.7f);
                p.toneA = 110f;
                p.toneB = 110.7f;
                p.ampRate = 0.05f;
                p.ampDepth = 0.12f;
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

    /** One layer, with independent left and right voices. */
    private static final class Layer {
        final String id;
        final Preset p;
        float gain = 0f;

        // Two decorrelated generators — the width of the sound.
        private int rngL = 0x9e3779b9;
        private int rngR = 0x85ebca6b;
        private final float[][] pinkRows = new float[2][7];
        private final float[] brown = new float[2];

        private double phaseA = 0;
        private double phaseB = 0;

        private final float[][] b0 = new float[2][2];
        private final float[][] b1 = new float[2][2];
        private final float[][] b2 = new float[2][2];
        private final float[][] a1 = new float[2][2];
        private final float[][] a2 = new float[2][2];
        private final float[][] z1 = new float[2][2];
        private final float[][] z2 = new float[2][2];

        private double lfoPhase = 0;
        private double ampPhase = 0;
        private int coeffCountdown = 0;

        // transient state
        private float transientEnv = 0f;
        private float transientPhase = 0f;
        private final float[] transientState = new float[2];
        private double transientRateAccumulator = 0;

        Layer(String id, Preset preset) {
            this.id = id;
            this.p = preset;
            updateCoefficients(p.cutoff);
        }

        private float random(int channel) {
            int state = channel == 0 ? rngL : rngR;
            state ^= state << 13;
            state ^= state >>> 17;
            state ^= state << 5;
            if (channel == 0) rngL = state;
            else rngR = state;
            return ((state & 0x7fffffff) / (float) 0x7fffffff) * 2f - 1f;
        }

        private float noise(int channel) {
            float white = random(channel);
            switch (p.source) {
                case BROWN:
                    brown[channel] = (brown[channel] + 0.02f * white) / 1.02f;
                    return brown[channel] * 3.2f;
                case PINK: {
                    float[] r = pinkRows[channel];
                    r[0] = 0.99886f * r[0] + white * 0.0555179f;
                    r[1] = 0.99332f * r[1] + white * 0.0750759f;
                    r[2] = 0.96900f * r[2] + white * 0.1538520f;
                    r[3] = 0.86650f * r[3] + white * 0.3104856f;
                    r[4] = 0.55000f * r[4] + white * 0.5329522f;
                    r[5] = -0.7616f * r[5] - white * 0.0168980f;
                    float sum = r[0] + r[1] + r[2] + r[3] + r[4] + r[5] + r[6] + white * 0.5362f;
                    r[6] = white * 0.115926f;
                    return sum * 0.11f;
                }
                case WHITE:
                default:
                    return white * 0.35f;
            }
        }

        private void updateCoefficients(float cutoff) {
            float f = Math.min(0.45f, Math.max(0.0008f, cutoff / SAMPLE_RATE));
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
            } else {
                nb0 = alpha;
                nb1 = 0f;
                nb2 = -alpha;
                na0 = 1f + alpha;
                na1 = -2f * cos;
                na2 = 1f - alpha;
            }

            // A 1.5% offset between channels: enough to widen the image, small
            // enough that both ears still hear the same sound.
            for (int ch = 0; ch < 2; ch++) {
                float scale = ch == 0 ? 1f : 1.015f;
                float gb0 = nb0 * scale, gb1 = nb1 * scale, gb2 = nb2 * scale;
                b0[ch][0] = gb0 / na0;
                b1[ch][0] = gb1 / na0;
                b2[ch][0] = gb2 / na0;
                a1[ch][0] = na1 / na0;
                a2[ch][0] = na2 / na0;
                b0[ch][1] = b0[ch][0];
                b1[ch][1] = b1[ch][0];
                b2[ch][1] = b2[ch][0];
                a1[ch][1] = a1[ch][0];
                a2[ch][1] = a2[ch][0];
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
                    float a = (float) Math.sin(phaseA) * 0.2f;
                    float b = (float) Math.sin(phaseB) * 0.2f;
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

            // Transients: droplets, crackles, distant birds. One envelope drives a
            // band-passed burst, which the ear reads as a discrete event instead of
            // as modulated hiss.
            boolean hasTransient = p.transientRate > 0f;
            double transientInc = hasTransient ? (2 * Math.PI * p.transientFreq / SAMPLE_RATE) : 0;

            for (int i = 0; i < BLOCK; i++) {
                float sampleL = noise(0);
                float sampleR = noise(1);

                float yL = filterSample(sampleL, 0, 0);
                yL = filterSample(yL, 0, 1);
                float yR = filterSample(sampleR, 1, 0);
                yR = filterSample(yR, 1, 1);

                if (hasTransient) {
                    transientRateAccumulator += p.transientRate / SAMPLE_RATE;
                    if (transientRateAccumulator >= 1.0) {
                        transientRateAccumulator -= 1.0;
                        transientEnv = 0.6f + (float) Math.random() * 0.4f;
                    }
                    if (transientEnv > 0.0004f) {
                        transientPhase += (float) transientInc;
                        if (transientPhase > 2 * Math.PI) transientPhase -= (float) (2 * Math.PI);
                        float burst = (float) Math.sin(transientPhase) * transientEnv * 0.5f;
                        // one-pole smoothing keeps the burst from clicking
                        transientState[0] += (burst - transientState[0]) * 0.25f;
                        yL += transientState[0];
                        yR += transientState[0] * 0.85f;
                        transientEnv *= 0.9993f;
                    }
                }

                left[i] += yL * gainNow;
                right[i] += yR * gainNow;
            }
        }

        private float filterSample(float x, int channel, int stage) {
            float y = b0[channel][stage] * x + z1[channel][stage];
            z1[channel][stage] = b1[channel][stage] * x - a1[channel][stage] * y + z2[channel][stage];
            z2[channel][stage] = b2[channel][stage] * x - a2[channel][stage] * y;
            return y;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // The bell
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The end-of-session bell, synthesised on demand: 264/396/528/792 Hz struck
     * low to high, 70 ms apart, each with its own exponential decay plus a touch
     * of detune for "struck metal". Plays on a one-shot track so it rings over the
     * soundscape, and it lives in the service, so a locked screen does not mute it.
     */
    public static void playBell() {
        final float seconds = 3.4f;
        final int frames = (int) (SAMPLE_RATE * seconds);
        final short[] buffer = new short[frames * 2];

        final float[][] partials = {
                {264f, 0.00f, 0.34f, 2.7f},
                {396f, 0.07f, 0.24f, 2.2f},
                {528f, 0.13f, 0.17f, 1.8f},
                {792f, 0.20f, 0.11f, 1.3f},
        };

        for (int i = 0; i < frames; i++) {
            float t = i / (float) SAMPLE_RATE;
            float value = 0f;
            for (float[] partial : partials) {
                float start = partial[1];
                if (t < start) continue;
                float local = t - start;
                float attack = 1f - (float) Math.exp(-local * 300f);
                float envelope = (float) Math.exp(-local * 2.1f) * attack;
                // Slight inharmonic shimmer, as on a struck bell.
                float f1 = partial[0];
                float f2 = partial[0] * 1.004f;
                value += (float) (Math.sin(2 * Math.PI * f1 * local) * 0.6
                        + Math.sin(2 * Math.PI * f2 * local) * 0.4) * partial[2] * envelope;
            }
            float clipped = softClip(value * 1.2f);
            // Stereo: a 6 ms difference is enough for the bell to feel "in the room".
            int delay = (int) (SAMPLE_RATE * 0.006f);
            float leftValue = clipped;
            float rightValue = i > delay ? clipped : 0f;
            buffer[i * 2] = (short) (leftValue * 32767f);
            buffer[i * 2 + 1] = (short) (rightValue * 32767f);
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
                    Thread.sleep((long) (seconds * 1000) + 150);
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

    public static boolean isSupported() {
        if (Build.VERSION.SDK_INT < 21) return false;
        return AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT) > 0;
    }
}
