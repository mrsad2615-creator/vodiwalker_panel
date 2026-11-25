package com.neurox.audio

import com.neurox.domain.model.AudioChannel
import com.neurox.domain.model.AudioSettings

/**
 * Audio playback policy, expressed without Android so it can be unit-tested.
 *
 * RULES (from the audio specification)
 * 1. Channels are independent: muting music must not mute training cues.
 * 2. Training cues always win: a cue interrupts interface sound and music.
 * 3. No overlapping playback of the same cue; a second request within
 *    [MIN_CUE_SPACING_MILLIS] is dropped, not queued.
 * 4. Nothing plays while the application is in the background.
 * 5. Silence mode and focus mode are honored at the routing layer, so a caller
 *    cannot accidentally bypass them.
 * 6. Audio focus: music requests focus and pauses when focus is lost. Cues do not
 *    hold focus, because they must never stop the user's own music for a beep.
 */
class AudioPolicy(
    private val settingsProvider: () -> AudioSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    // "Never played" is tracked separately from a timestamp, so the first cue of a
    // process is never dropped by a spacing rule.
    private var lastCueAt: Long? = null
    private var lastCue: Synth.Cue? = null
    private var lastInterfaceAt: Long? = null
    private var foreground = true

    /** True while the app is visible. Set by the platform lifecycle observer. */
    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
    }

    val isForeground: Boolean get() = foreground

    /**
     * Decides whether [cue] may play now and at what gain.
     * Returns 0 gain when the cue must not play.
     */
    fun decide(cue: Synth.Cue): PlaybackDecision {
        val settings = settingsProvider()
        val channel = cue.channel
        val gain = settings.effective(channel)
        if (gain == 0f) return PlaybackDecision.Silent(reason = "volume_zero")
        if (!foreground) return PlaybackDecision.Silent(reason = "background")
        val now = clock()
        // Spacing rules exist to prevent a stutter of identical or competing
        // feedback. A signal cue is part of the exercise protocol: a slow or blocked
        // cue would corrupt the measurement, so it is never delayed.
        val channelFloor = when (channel) {
            AudioChannel.TRAINING_CUE -> 0L
            AudioChannel.SUCCESS, AudioChannel.ERROR -> MIN_FEEDBACK_SPACING_MILLIS
            AudioChannel.INTERFACE -> MIN_INTERFACE_SPACING_MILLIS
            AudioChannel.MUSIC -> 0L
        }
        if (channel == AudioChannel.TRAINING_CUE && cue != lastCue) {
            // Repeat suppression does not apply between different signals, and the
            // same signal is guarded by the core engine, not by the audio layer.
            return PlaybackDecision.Play(gain = gain, interruptOthers = true)
        }
        val lastAt = when (channel) {
            AudioChannel.INTERFACE -> lastInterfaceAt
            else -> lastCueAt
        }
        if (lastAt != null && now - lastAt < channelFloor) {
            return PlaybackDecision.Silent(reason = "too_soon")
        }
        val lastRepeatAt = lastCueAt
        if (cue == lastCue && lastRepeatAt != null && now - lastRepeatAt < MIN_CUE_SPACING_MILLIS) {
            return PlaybackDecision.Silent(reason = "repeat_suppressed")
        }
        return PlaybackDecision.Play(gain = gain, interruptOthers = channel == AudioChannel.TRAINING_CUE)
    }

    /** Records that [cue] actually started, so the spacing rules work. */
    fun onPlayed(cue: Synth.Cue) {
        val now = clock()
        when (cue.channel) {
            AudioChannel.INTERFACE -> lastInterfaceAt = now
            else -> lastCueAt = now
        }
        lastCue = cue
    }

    /** Whether background music may run now. */
    fun shouldPlayMusic(): Boolean {
        val settings = settingsProvider()
        return foreground && !settings.silentMode && settings.musicEnabled &&
            settings.effective(AudioChannel.MUSIC) > 0f
    }

    /** Haptic strength for a cue, honoring silent mode and user intensity. */
    fun hapticStrength(cue: Synth.Cue): Float {
        val settings = settingsProvider()
        if (!foreground) return 0f
        val base = settings.hapticStrength()
        if (base == 0f) return 0f
        return when (cue) {
            Synth.Cue.STIMULUS, Synth.Cue.INHIBIT -> base
            Synth.Cue.HIT -> base * 0.8f
            Synth.Cue.MISS -> base * 1.0f
            Synth.Cue.STREAK, Synth.Cue.FINISH -> base
            Synth.Cue.TAP, Synth.Cue.TICK -> base * 0.4f
            Synth.Cue.START -> base * 0.6f
        }.coerceIn(0f, 1f)
    }

    sealed interface PlaybackDecision {
        val gain: Float

        data class Play(
            override val gain: Float,
            val interruptOthers: Boolean,
        ) : PlaybackDecision

        data class Silent(
            val reason: String,
            override val gain: Float = 0f,
        ) : PlaybackDecision
    }

    companion object {
        const val MIN_CUE_SPACING_MILLIS = 45L
        const val MIN_FEEDBACK_SPACING_MILLIS = 120L
        const val MIN_INTERFACE_SPACING_MILLIS = 60L
    }
}

/**
 * Describes one playable sound to the platform layer. The platform loads the PCM
 * into an AudioTrack; it never decides policy.
 */
data class SoundRequest(
    val cue: Synth.Cue,
    val gain: Float,
    val stream: StreamKind,
    val hapticStrength: Float,
) {
    enum class StreamKind {
        /** Uses the usage that Android associates with game media. */
        MEDIA,

        /** Uses the usage that Android associates with UI feedback. */
        UI,
    }
}

/**
 * Builds the complete routing table once, so the platform layer has a fixed list.
 * Cues are rendered lazily and cached per gain step.
 */
class SoundCatalog(
    private val policy: () -> AudioSettings,
) {
    /** The stream kind for a cue. Training cues are media, feedback is UI. */
    fun streamOf(cue: Synth.Cue): SoundRequest.StreamKind = when (cue.channel) {
        AudioChannel.TRAINING_CUE -> SoundRequest.StreamKind.MEDIA
        AudioChannel.INTERFACE -> SoundRequest.StreamKind.UI
        AudioChannel.SUCCESS, AudioChannel.ERROR -> SoundRequest.StreamKind.UI
        AudioChannel.MUSIC -> SoundRequest.StreamKind.MEDIA
    }

    /**
     * Volume quantization for caching: 20 steps from 0 to 1 inclusive.
     * Quantizing avoids rendering a new buffer for every possible amplitude.
     */
    fun gainStep(gain: Float): Int = (gain.coerceIn(0f, 1f) * GAIN_STEPS).toInt()

    /** PCM for a cue at a quantized gain. */
    fun pcm(cue: Synth.Cue, gain: Float): ShortArray =
        Synth.render(cue, gainStep(gain) / GAIN_STEPS.toFloat())

    val cueList: List<Synth.Cue> get() = Synth.Cue.entries

    private val settings: AudioSettings get() = policy()

    companion object {
        const val GAIN_STEPS = 20
    }
}
