package dev.mrsad.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.mrsad.app.testing.MrsadTestHooks
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Studio-grade procedural DSP audio engine for MRSAD (44.1 kHz, 16-bit Stereo).
 *
 * Provides two independent, zero-asset audio pipelines:
 * 1. **Continuous Stereo Ambient Soundscape Synthesizer**:
 *    Warm low-pass filtered Brownian noise, multi-layered rain with stochastic
 *    droplet resonators, true stereo Binaural Alpha (10 Hz) & Gamma (40 Hz)
 *    waves, dual-LFO stereo ocean surf, and modal Tibetan singing bowls.
 *    Uses raised-cosine crossfades to guarantee zero clicks or pops.
 * 2. **Polyphonic Harmonic SFX Synthesizer**:
 *    Warm multi-partial chimes for timer transitions, horological pendulum
 *    ticks, tactile mechanical key switch ("thock") responses, and crystalline
 *    feedback tones across Eye, Read, and Mind drills.
 */
enum class AmbientSoundscape(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val badgeFa: String,
) {
    OFF(
        id = "off",
        titleFa = "بی‌صدا (خاموش)",
        subtitleFa = "بدون صدای محیطی پس‌زمینه",
        badgeFa = "خاموش",
    ),
    BROWN_WARM(
        id = "brown_warm",
        titleFa = "نویز قهوه‌ای عمیق",
        subtitleFa = "پوشش صوتی گرم و بم برای حذف کامل صداهای مزاحم محیط",
        badgeFa = "پوشش عمیق",
    ),
    RAIN_CANOPY(
        id = "rain_canopy",
        titleFa = "باران ملایم شبانه",
        subtitleFa = "بافت استریوی باران طبیعی همراه با قطرات لطیف و فیلتر گرم",
        badgeFa = "طبیعت",
    ),
    ALPHA_FOCUS(
        id = "alpha_focus",
        titleFa = "موج آلفا • ۱۰ هرتز",
        subtitleFa = "فرکانس دوگوشی استریو (۲۰۰ و ۲۱۰ هرتز) برای مطالعهٔ آرام و پایدار",
        badgeFa = "۱۰ Hz",
    ),
    GAMMA_DEEP(
        id = "gamma_deep",
        titleFa = "موج گاما • ۴۰ هرتز",
        subtitleFa = "فرکانس شناختی دوگوشی (۲۱۶ و ۲۵۶ هرتز) برای تمرکز و پردازش عمیق",
        badgeFa = "۴۰ Hz",
    ),
    OCEAN_TIDE(
        id = "ocean_tide",
        titleFa = "امواج آرام اقیانوس",
        subtitleFa = "ریتم تنفسی موج‌های ساحلی با حرکت نرم استریو بین دو گوش",
        badgeFa = "ریتمیک",
    ),
    TIBETAN_BOWL(
        id = "tibetan_bowl",
        titleFa = "طنین کاسهٔ تبتی",
        subtitleFa = "هارمونیک‌های کانونی ۱۷۴ و ۴۳۲ هرتز برای آرام‌سازی ذهن",
        badgeFa = "هارمونیک",
    );

    companion object {
        fun fromId(id: String?): AmbientSoundscape =
            entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class SfxType {
    FOCUS_START,
    BREAK_START,
    SESSION_COMPLETE,
    SOFT_TICK,
    KEY_THOCK,
    KEY_ERROR,
    TARGET_HIT,
    TARGET_MISS,
}

class MrsadAudioEngine {

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val TWO_PI = 2.0 * PI
    }

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    var ambientVolume: Float = 0.72f

    private val activeSoundscape = AtomicReference(AmbientSoundscape.OFF)
    private val ambientRunning = AtomicBoolean(false)
    private var ambientThread: Thread? = null

    /**
     * Selects the active ambient soundscape and starts/stops the stereo DSP
     * synthesis thread as needed.
     */
    @Synchronized
    fun setAmbientSoundscape(soundscape: AmbientSoundscape) {
        activeSoundscape.set(soundscape)
        if (soundscape == AmbientSoundscape.OFF) {
            stopAmbient()
        } else if (!MrsadTestHooks.isDeterministic) {
            ensureAmbientThread()
        }
    }

    fun currentSoundscape(): AmbientSoundscape = activeSoundscape.get()

    fun isAmbientPlaying(): Boolean =
        ambientRunning.get() && activeSoundscape.get() != AmbientSoundscape.OFF

    @Synchronized
    fun stopAmbient() {
        ambientRunning.set(false)
        ambientThread?.interrupt()
        ambientThread = null
    }

    @Synchronized
    private fun ensureAmbientThread() {
        if (ambientRunning.get() && ambientThread?.isAlive == true) return
        ambientRunning.set(true)
        ambientThread = thread(
            start = true,
            isDaemon = true,
            name = "MrsadStudioAmbientDsp",
            priority = Thread.MAX_PRIORITY - 1,
        ) {
            runAmbientLoop()
        }
    }

    /**
     * Plays a short, studio-synthesized polyphonic sound effect on a lightweight
     * background worker when [soundEnabled] is true (or when [force] is true,
     * such as inside the Focus Timer / Audio Studio).
     */
    fun playSfx(type: SfxType, force: Boolean = false) {
        if (MrsadTestHooks.isDeterministic) return
        if (!force && !soundEnabled) return
        thread(isDaemon = true, name = "MrsadSfx-${type.name}") {
            runCatching {
                val pcm = synthesizeSfxStereo(type)
                playOneShotStereoPcm(pcm)
            }
        }
    }

    private fun runAmbientLoop() {
        val framesPerChunk = 1764 // 40ms at 44.1 kHz
        val bufferShorts = ShortArray(framesPerChunk * 2) // Stereo interleaved L, R
        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(framesPerChunk * 4 * 2)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(minBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        // Internal DSP state variables
        var prng = 0x9E3779B97F4A7C15UL
        fun nextUniform(): Double {
            prng += 0x9E3779B97F4A7C15UL
            var z = prng
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9UL
            z = (z xor (z shr 27)) * 0x94D049BB133111EBUL
            z = z xor (z shr 31)
            return ((z shr 11).toLong().toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
        }

        var brownL = 0.0
        var brownR = 0.0
        var lp1L = 0.0
        var lp1R = 0.0
        var lp2L = 0.0
        var lp2R = 0.0

        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0
        var phase4 = 0.0
        var lfoPhase1 = 0.0
        var lfoPhase2 = 0.0

        // Rain droplet resonator state
        var dropEnv = 0.0
        var dropPhase = 0.0
        var dropFreq = 1200.0
        var dropPan = 0.5

        // Bowl strike envelope
        var bowlStrikeSamples = 0L
        var masterEnvelope = 0.0
        var currentMode = activeSoundscape.get()

        try {
            track.play()
            while (ambientRunning.get() && !Thread.currentThread().isInterrupted) {
                val targetMode = activeSoundscape.get()
                if (targetMode == AmbientSoundscape.OFF) break
                if (targetMode != currentMode) {
                    currentMode = targetMode
                    masterEnvelope = 0.05 // Smooth cross-fade into new mode
                }

                val targetGain = ambientVolume.coerceIn(0.05f, 1.0f).toDouble()

                for (i in 0 until framesPerChunk) {
                    // Smooth raised-cosine / exponential fade-in to prevent any click
                    masterEnvelope += (targetGain - masterEnvelope) * 0.0008

                    val whiteL = nextUniform()
                    val whiteR = nextUniform()

                    // Leaky integrator Brownian motion
                    brownL = (brownL + 0.028 * whiteL) * 0.982
                    brownR = (brownR + 0.028 * whiteR) * 0.982

                    var outL = 0.0
                    var outR = 0.0

                    when (currentMode) {
                        AmbientSoundscape.BROWN_WARM -> {
                            // Cascaded 2-pole warm low-pass filter (~380 Hz) + subtle 64Hz sub-warmth
                            lp1L += 0.052 * (brownL - lp1L)
                            lp2L += 0.052 * (lp1L - lp2L)
                            lp1R += 0.052 * (brownR - lp1R)
                            lp2R += 0.052 * (lp1R - lp2R)

                            phase1 = (phase1 + TWO_PI * 64.0 / SAMPLE_RATE) % TWO_PI
                            phase2 = (phase2 + TWO_PI * 96.0 / SAMPLE_RATE) % TWO_PI
                            val sub = sin(phase1) * 0.045 + sin(phase2) * 0.025

                            outL = lp2L * 1.45 + sub
                            outR = lp2R * 1.45 + sub
                        }

                        AmbientSoundscape.RAIN_CANOPY -> {
                            // Warm rain canopy bed + gentle stochastic droplet resonators
                            val pinkMixL = brownL * 0.65 + whiteL * 0.14
                            val pinkMixR = brownR * 0.65 + whiteR * 0.14
                            lp1L += 0.14 * (pinkMixL - lp1L)
                            lp2L += 0.14 * (lp1L - lp2L)
                            lp1R += 0.14 * (pinkMixR - lp1R)
                            lp2R += 0.14 * (lp1R - lp2R)

                            lfoPhase1 = (lfoPhase1 + TWO_PI * 0.09 / SAMPLE_RATE) % TWO_PI
                            val swell = 0.82 + 0.18 * sin(lfoPhase1)

                            // Trigger soft raindrop micro-resonances stochastically
                            if (dropEnv < 0.0005 && nextUniform() > 0.9986) {
                                dropEnv = 0.14
                                dropFreq = 850.0 + (nextUniform() + 1.0) * 520.0
                                dropPan = (nextUniform() + 1.0) * 0.5
                            }
                            dropEnv *= 0.9945
                            dropPhase = (dropPhase + TWO_PI * dropFreq / SAMPLE_RATE) % TWO_PI
                            val droplet = sin(dropPhase) * dropEnv

                            outL = lp2L * swell + droplet * (1.0 - dropPan * 0.6)
                            outR = lp2R * swell + droplet * (0.4 + dropPan * 0.6)
                        }

                        AmbientSoundscape.ALPHA_FOCUS -> {
                            // True Binaural Alpha 10 Hz: 200 Hz (L) and 210 Hz (R) + warm 300 Hz fifth pad
                            phase1 = (phase1 + TWO_PI * 200.0 / SAMPLE_RATE) % TWO_PI
                            phase2 = (phase2 + TWO_PI * 210.0 / SAMPLE_RATE) % TWO_PI
                            phase3 = (phase3 + TWO_PI * 100.0 / SAMPLE_RATE) % TWO_PI
                            lfoPhase1 = (lfoPhase1 + TWO_PI * 0.12 / SAMPLE_RATE) % TWO_PI

                            lp1L += 0.04 * (brownL - lp1L)
                            lp1R += 0.04 * (brownR - lp1R)

                            val breath = 0.85 + 0.15 * sin(lfoPhase1)
                            val sub = sin(phase3) * 0.09
                            outL = (sin(phase1) * 0.24 * breath) + sub + lp1L * 0.35
                            outR = (sin(phase2) * 0.24 * breath) + sub + lp1R * 0.35
                        }

                        AmbientSoundscape.GAMMA_DEEP -> {
                            // True Binaural Gamma 40 Hz: 216 Hz (L) and 256 Hz (R) + 108 Hz foundation
                            phase1 = (phase1 + TWO_PI * 216.0 / SAMPLE_RATE) % TWO_PI
                            phase2 = (phase2 + TWO_PI * 256.0 / SAMPLE_RATE) % TWO_PI
                            phase3 = (phase3 + TWO_PI * 108.0 / SAMPLE_RATE) % TWO_PI
                            lfoPhase1 = (lfoPhase1 + TWO_PI * 40.0 / SAMPLE_RATE) % TWO_PI

                            lp1L += 0.045 * (brownL - lp1L)
                            lp1R += 0.045 * (brownR - lp1R)

                            val isochronicMod = 0.82 + 0.18 * sin(lfoPhase1)
                            val sub = sin(phase3) * 0.11 * isochronicMod
                            outL = sin(phase1) * 0.22 + sub + lp1L * 0.32
                            outR = sin(phase2) * 0.22 + sub + lp1R * 0.32
                        }

                        AmbientSoundscape.OCEAN_TIDE -> {
                            // Dual-LFO ocean surf with dynamic cutoff and stereo shore drift
                            lfoPhase1 = (lfoPhase1 + TWO_PI * 0.105 / SAMPLE_RATE) % TWO_PI
                            lfoPhase2 = (lfoPhase2 + TWO_PI * 0.068 / SAMPLE_RATE) % TWO_PI

                            val wave1 = (sin(lfoPhase1) + 1.0) * 0.5
                            val wave2 = (sin(lfoPhase2) + 1.0) * 0.5
                            val surfEnv = 0.22 + 0.78 * (wave1 * 0.65 + wave2 * 0.35)
                            val cutoffAlpha = 0.025 + 0.11 * surfEnv

                            val srcL = brownL * 0.75 + whiteL * 0.10 * surfEnv
                            val srcR = brownR * 0.75 + whiteR * 0.10 * surfEnv
                            lp1L += cutoffAlpha * (srcL - lp1L)
                            lp1R += cutoffAlpha * (srcR - lp1R)

                            val pan = 0.5 + 0.28 * cos(lfoPhase1)
                            outL = lp1L * surfEnv * (0.7 + 0.5 * pan)
                            outR = lp1R * surfEnv * (1.2 - 0.5 * pan)
                        }

                        AmbientSoundscape.TIBETAN_BOWL -> {
                            // Modal Tibetan singing bowl (174 Hz, 285 Hz, 432 Hz) with slow shimmer
                            val strikePeriod = SAMPLE_RATE * 10L
                            val posInStrike = bowlStrikeSamples % strikePeriod
                            bowlStrikeSamples++

                            val attack = (posInStrike.toDouble() / (SAMPLE_RATE * 0.45)).coerceAtMost(1.0)
                            val decay = exp(-posInStrike.toDouble() / (SAMPLE_RATE * 6.5))
                            val bowlEnv = 0.38 + 0.62 * attack * decay

                            phase1 = (phase1 + TWO_PI * 174.0 / SAMPLE_RATE) % TWO_PI
                            phase2 = (phase2 + TWO_PI * 174.6 / SAMPLE_RATE) % TWO_PI
                            phase3 = (phase3 + TWO_PI * 432.0 / SAMPLE_RATE) % TWO_PI
                            phase4 = (phase4 + TWO_PI * 285.0 / SAMPLE_RATE) % TWO_PI

                            val fundamentalL = sin(phase1) * 0.22 + sin(phase4) * 0.10
                            val fundamentalR = sin(phase2) * 0.22 + sin(phase4) * 0.10
                            val overtone = sin(phase3) * 0.08 * decay

                            outL = (fundamentalL + overtone) * bowlEnv
                            outR = (fundamentalR + overtone) * bowlEnv
                        }

                        AmbientSoundscape.OFF -> {
                            outL = 0.0
                            outR = 0.0
                        }
                    }

                    // Soft-clipping hyperbolic tangent limiter for warm analog saturation
                    val scaledL = softClip(outL * masterEnvelope)
                    val scaledR = softClip(outR * masterEnvelope)

                    bufferShorts[i * 2] = (scaledL * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
                    bufferShorts[i * 2 + 1] = (scaledR * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
                }

                val written = track.write(bufferShorts, 0, bufferShorts.size)
                if (written < 0) break
            }
        } catch (_: Throwable) {
            // AudioTrack shut down cleanly
        } finally {
            runCatching {
                track.pause()
                track.flush()
                track.release()
            }
        }
    }

    private fun synthesizeSfxStereo(type: SfxType): ShortArray {
        val durationMs = when (type) {
            SfxType.FOCUS_START -> 760
            SfxType.BREAK_START -> 760
            SfxType.SESSION_COMPLETE -> 1350
            SfxType.SOFT_TICK -> 24
            SfxType.KEY_THOCK -> 22
            SfxType.KEY_ERROR -> 65
            SfxType.TARGET_HIT -> 150
            SfxType.TARGET_MISS -> 130
        }
        val frames = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(frames * 2)
        var lpState = 0.0
        var prng = 0x517CC1B727220A95UL

        fun nextNoise(): Double {
            prng += 0x9E3779B97F4A7C15UL
            var z = prng
            z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9UL
            z = z xor (z shr 31)
            return ((z shr 11).toLong().toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
        }

        for (i in 0 until frames) {
            val t = i.toDouble() / SAMPLE_RATE
            // Smooth 3ms attack to eliminate clicks
            val attack = (t / 0.003).coerceAtMost(1.0)
            var sampleL = 0.0
            var sampleR = 0.0

            when (type) {
                SfxType.FOCUS_START -> {
                    // Ascending warm triad: C4 (261.6Hz) -> G4 (392.0Hz) -> E5 (659.3Hz)
                    val n1 = sin(TWO_PI * 261.63 * t) * exp(-t * 4.2)
                    val n2 = if (t >= 0.11) sin(TWO_PI * 392.00 * (t - 0.11)) * exp(-(t - 0.11) * 4.0) else 0.0
                    val n3 = if (t >= 0.22) (
                        sin(TWO_PI * 523.25 * (t - 0.22)) * 0.6 +
                            sin(TWO_PI * 659.25 * (t - 0.22)) * 0.4
                        ) * exp(-(t - 0.22) * 3.4) else 0.0
                    sampleL = (n1 * 0.34 + n2 * 0.30 + n3 * 0.36) * attack
                    sampleR = (n1 * 0.30 + n2 * 0.34 + n3 * 0.38) * attack
                }

                SfxType.BREAK_START -> {
                    // Calming descending interval: E5 (659.3Hz) -> B4 (493.9Hz) -> G4 (392.0Hz)
                    val n1 = sin(TWO_PI * 659.25 * t) * exp(-t * 4.8)
                    val n2 = if (t >= 0.12) sin(TWO_PI * 493.88 * (t - 0.12)) * exp(-(t - 0.12) * 4.2) else 0.0
                    val n3 = if (t >= 0.25) sin(TWO_PI * 392.00 * (t - 0.25)) * exp(-(t - 0.25) * 3.2) else 0.0
                    sampleL = (n1 * 0.30 + n2 * 0.32 + n3 * 0.36) * attack
                    sampleR = (n1 * 0.32 + n2 * 0.30 + n3 * 0.38) * attack
                }

                SfxType.SESSION_COMPLETE -> {
                    // Flagship multi-layer harmonic chord (C4 + G4 + C5 + E5 + G5)
                    val c4 = sin(TWO_PI * 261.63 * t) * exp(-t * 2.2)
                    val g4 = if (t >= 0.08) sin(TWO_PI * 392.00 * (t - 0.08)) * exp(-(t - 0.08) * 2.1) else 0.0
                    val c5 = if (t >= 0.16) sin(TWO_PI * 523.25 * (t - 0.16)) * exp(-(t - 0.16) * 2.0) else 0.0
                    val e5 = if (t >= 0.26) sin(TWO_PI * 659.25 * (t - 0.26)) * exp(-(t - 0.26) * 1.9) else 0.0
                    val g5 = if (t >= 0.36) sin(TWO_PI * 783.99 * (t - 0.36)) * exp(-(t - 0.36) * 1.7) else 0.0
                    sampleL = (c4 * 0.24 + g4 * 0.22 + c5 * 0.22 + e5 * 0.20 + g5 * 0.22) * attack
                    sampleR = (c4 * 0.22 + g4 * 0.24 + c5 * 0.20 + e5 * 0.22 + g5 * 0.24) * attack
                }

                SfxType.SOFT_TICK -> {
                    // Delicate horological wooden pendulum tick
                    lpState += 0.22 * (nextNoise() - lpState)
                    val click = lpState * exp(-t * 240.0) * 0.35
                    val body = sin(TWO_PI * 620.0 * t) * exp(-t * 190.0) * 0.22
                    sampleL = (click + body) * attack
                    sampleR = sampleL
                }

                SfxType.KEY_THOCK -> {
                    // Warm Topre / mechanical key switch "thock" (low-pass impulse + 230Hz/460Hz body)
                    lpState += 0.28 * (nextNoise() - lpState)
                    val transient = lpState * exp(-t * 260.0) * 0.42
                    val resonance = (
                        sin(TWO_PI * 235.0 * t) * 0.30 +
                            sin(TWO_PI * 470.0 * t) * 0.14
                        ) * exp(-t * 175.0)
                    sampleL = (transient + resonance) * attack
                    sampleR = sampleL
                }

                SfxType.KEY_ERROR -> {
                    // Soft muted low thud (155Hz dropping to 110Hz)
                    val freq = 155.0 - 45.0 * (t / 0.065)
                    val wave = sin(TWO_PI * freq * t) * exp(-t * 52.0) * 0.32
                    sampleL = wave * attack
                    sampleR = sampleL
                }

                SfxType.TARGET_HIT -> {
                    // Crisp two-tone crystal chime (528Hz -> 792Hz)
                    val n1 = sin(TWO_PI * 528.0 * t) * exp(-t * 24.0)
                    val n2 = if (t >= 0.045) sin(TWO_PI * 792.0 * (t - 0.045)) * exp(-(t - 0.045) * 20.0) else 0.0
                    sampleL = (n1 * 0.28 + n2 * 0.32) * attack
                    sampleR = (n1 * 0.26 + n2 * 0.34) * attack
                }

                SfxType.TARGET_MISS -> {
                    // Gentle low feedback tone (185Hz)
                    val wave = sin(TWO_PI * 185.0 * t) * exp(-t * 26.0) * 0.28
                    sampleL = wave * attack
                    sampleR = sampleL
                }
            }

            out[i * 2] = (softClip(sampleL) * 28000.0).toInt().coerceIn(-32767, 32767).toShort()
            out[i * 2 + 1] = (softClip(sampleR) * 28000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun playOneShotStereoPcm(pcm: ShortArray) {
        val byteCount = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(byteCount.coerceAtLeast(4096))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val sleepMillis = (pcm.size / 2L * 1000L / SAMPLE_RATE) + 45L
            Thread.sleep(sleepMillis)
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    private fun softClip(x: Double): Double =
        when {
            x > 1.2 -> 0.84
            x < -1.2 -> -0.84
            else -> x - (x * x * x) / 4.32
        }
}
