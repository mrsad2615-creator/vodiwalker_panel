package com.neurox.audio

import com.neurox.domain.model.AudioChannel

/**
 * NEUROX SENSE synthesis.
 *
 * WHY SYNTHESIS (decision DEC-0006): the product needs short, neutral cues that are
 * always available offline and free of third-party licences. Waveform synthesis
 * gives exact control of loudness, duration and timbre, produces no clipping
 * surprises, and adds no binary assets to the APK. Every sound below is original.
 *
 * The generator is pure Kotlin: it returns a PCM 16-bit mono buffer that the
 * platform layer loads into an AudioTrack. It is unit-tested for length, headroom
 * and absence of clicks at the boundaries.
 */
object Synth {

    const val SAMPLE_RATE = 44_100

    /** A cue that carries functional meaning in a training session. */
    enum class Cue(val channel: AudioChannel) {
        /** Neutral signal: the moment to act. */
        STIMULUS(AudioChannel.TRAINING_CUE),

        /** The signal is a NO-GO: do not act. */
        INHIBIT(AudioChannel.TRAINING_CUE),

        /** A correct answer. */
        HIT(AudioChannel.SUCCESS),

        /** A wrong answer. */
        MISS(AudioChannel.ERROR),

        /** A very accurate section, used sparingly. */
        STREAK(AudioChannel.SUCCESS),

        /** A countdown tick during practice. */
        TICK(AudioChannel.INTERFACE),

        /** Interface navigation confirm. */
        TAP(AudioChannel.INTERFACE),

        /** Session start. */
        START(AudioChannel.INTERFACE),

        /** Session complete. */
        FINISH(AudioChannel.SUCCESS),
    }

    /**
     * Renders a cue into 16-bit little-endian mono PCM.
     *
     * Headroom: peak amplitude is at most [PEAK], so a cue never clips even when an
     * envelope overlaps.
     */
    fun render(cue: Cue, gain: Float = 1f): ShortArray {
        val clampedGain = gain.coerceIn(0f, 1f)
        if (clampedGain == 0f) return ShortArray(0)
        return when (cue) {
            Cue.STIMULUS -> tone(frequency = 880.0, durationMs = 90, gain = clampedGain, wave = Wave.SINE, attack = 4, release = 40)
            Cue.INHIBIT -> tone(frequency = 330.0, durationMs = 140, gain = clampedGain, wave = Wave.TRIANGLE, attack = 6, release = 60)
            Cue.HIT -> chord(intArrayOf(660, 990), durationMs = 130, gain = clampedGain, wave = Wave.SINE)
            Cue.MISS -> tone(frequency = 180.0, durationMs = 180, gain = clampedGain, wave = Wave.SQUARE_SOFT, attack = 3, release = 90)
            Cue.STREAK -> arpeggio(intArrayOf(660, 880, 1320), stepMs = 70, gain = clampedGain)
            Cue.TICK -> tone(frequency = 1_200.0, durationMs = 40, gain = clampedGain, wave = Wave.SINE, attack = 2, release = 20)
            Cue.TAP -> tone(frequency = 1_500.0, durationMs = 28, gain = clampedGain, wave = Wave.SINE, attack = 2, release = 14)
            Cue.START -> arpeggio(intArrayOf(440, 660), stepMs = 90, gain = clampedGain)
            Cue.FINISH -> arpeggio(intArrayOf(523, 659, 784, 1046), stepMs = 95, gain = clampedGain)
        }
    }

    enum class Wave { SINE, TRIANGLE, SQUARE_SOFT }

    /**
     * A single tone with an attack/release envelope.
     *
     * The envelope removes the click that a hard start or stop would produce, which
     * is the most common audio defect in training apps.
     */
    fun tone(
        frequency: Double,
        durationMs: Int,
        gain: Float,
        wave: Wave = Wave.SINE,
        attack: Int = 4,
        release: Int = 40,
    ): ShortArray {
        require(frequency > 0)
        require(durationMs > 0)
        val samples = durationMs * SAMPLE_RATE / 1_000
        val out = ShortArray(samples)
        val angular = 2.0 * Math.PI * frequency / SAMPLE_RATE
        for (i in 0 until samples) {
            val timeMs = i * 1_000.0 / SAMPLE_RATE
            val envelope = envelope(timeMs, durationMs.toDouble(), attack.toDouble(), release.toDouble())
            val value = when (wave) {
                Wave.SINE -> Math.sin(angular * i)
                Wave.TRIANGLE -> 2.0 / Math.PI * Math.asin(Math.sin(angular * i))
                // A soft square keeps some harmonics without the harsh edge.
                Wave.SQUARE_SOFT -> Math.tanh(3.0 * Math.sin(angular * i))
            }
            out[i] = (value * envelope * gain * PEAK * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Two simultaneous tones, normalized so the sum cannot clip. */
    fun chord(frequencies: IntArray, durationMs: Int, gain: Float, wave: Wave = Wave.SINE): ShortArray {
        require(frequencies.isNotEmpty())
        val samples = durationMs * SAMPLE_RATE / 1_000
        val out = ShortArray(samples)
        val partials = frequencies.map { 2.0 * Math.PI * it / SAMPLE_RATE }
        val normalization = 1.0 / frequencies.size
        for (i in 0 until samples) {
            val timeMs = i * 1_000.0 / SAMPLE_RATE
            val envelope = envelope(timeMs, durationMs.toDouble(), 5.0, 45.0)
            var sum = 0.0
            for (angular in partials) {
                sum += when (wave) {
                    Wave.SINE -> Math.sin(angular * i)
                    Wave.TRIANGLE -> 2.0 / Math.PI * Math.asin(Math.sin(angular * i))
                    Wave.SQUARE_SOFT -> Math.tanh(3.0 * Math.sin(angular * i))
                }
            }
            out[i] = (sum * normalization * envelope * gain * PEAK * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** A short sequence of tones, used for reward cues. */
    fun arpeggio(frequencies: IntArray, stepMs: Int, gain: Float): ShortArray {
        require(frequencies.isNotEmpty())
        require(stepMs > 0)
        val total = frequencies.size * stepMs
        val samples = total * SAMPLE_RATE / 1_000
        val out = ShortArray(samples)
        frequencies.forEachIndexed { index, frequency ->
            val part = tone(
                frequency = frequency.toDouble(),
                durationMs = stepMs,
                gain = gain,
                wave = Wave.SINE,
                attack = 3,
                release = stepMs / 2,
            )
            val offset = index * part.size
            for (i in part.indices) {
                if (offset + i >= out.size) break
                val mixed = out[offset + i].toInt() + part[i].toInt()
                out[offset + i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    /** Linear attack, linear release, flat sustain. Never exceeds 1. */
    fun envelope(timeMs: Double, durationMs: Double, attackMs: Double, releaseMs: Double): Double {
        if (timeMs < 0 || timeMs > durationMs) return 0.0
        val attack = attackMs.coerceAtLeast(0.0)
        val release = releaseMs.coerceAtLeast(0.0)
        val attackPart = if (attack <= 0.0) 1.0 else (timeMs / attack).coerceAtMost(1.0)
        val remaining = durationMs - timeMs
        val releasePart = if (release <= 0.0) 1.0 else (remaining / release).coerceAtMost(1.0)
        return attackPart * releasePart
    }

    /** Peak amplitude used by every cue. Headroom of about 3 dB below full scale. */
    const val PEAK = 0.70

    /** Duration of a rendered cue in milliseconds, for UI timing. */
    fun durationMs(cue: Cue): Int = when (cue) {
        Cue.STIMULUS -> 90
        Cue.INHIBIT -> 140
        Cue.HIT -> 130
        Cue.MISS -> 180
        Cue.STREAK -> 210
        Cue.TICK -> 40
        Cue.TAP -> 28
        Cue.START -> 180
        Cue.FINISH -> 380
    }
}

/**
 * Maps a cue to its channel volume. The platform layer multiplies the PCM by the
 * channel gain, so muting a channel never requires re-rendering.
 */
object CueRouting {

    fun channelOf(cue: Synth.Cue): AudioChannel = cue.channel

    /** True when the cue must be silent under the current settings. */
    fun isSilent(cue: Synth.Cue, settings: com.neurox.domain.model.AudioSettings): Boolean =
        settings.effective(channelOf(cue)) == 0f
}
