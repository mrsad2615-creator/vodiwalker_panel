package com.neurox.domain.model

/**
 * NEUROX SENSE settings. One object, stored per profile.
 *
 * Every channel is independently controllable, as required by the audio
 * specification. [focusMode] silences everything except training cues, which is
 * the only channel that carries functional information.
 */
data class AudioSettings(
    val musicEnabled: Boolean = false,
    val musicVolume: Float = 0.35f,
    val interfaceSoundsEnabled: Boolean = true,
    val interfaceVolume: Float = 0.6f,
    val trainingCuesEnabled: Boolean = true,
    val trainingCueVolume: Float = 0.8f,
    val successEnabled: Boolean = true,
    val successVolume: Float = 0.7f,
    val errorEnabled: Boolean = true,
    val errorVolume: Float = 0.6f,
    val hapticsEnabled: Boolean = true,
    val hapticIntensity: Float = 0.6f,
    val soundPackId: String = "neurox.classic",
    val focusMode: Boolean = false,
    val silentMode: Boolean = false,
) {
    init {
        require(musicVolume in 0f..1f)
        require(interfaceVolume in 0f..1f)
        require(trainingCueVolume in 0f..1f)
        require(successVolume in 0f..1f)
        require(errorVolume in 0f..1f)
        require(hapticIntensity in 0f..1f)
    }

    /** True when a channel should actually produce sound right now. */
    fun effective(channel: AudioChannel): Float = when {
        silentMode -> 0f
        focusMode && channel != AudioChannel.TRAINING_CUE -> 0f
        else -> when (channel) {
            AudioChannel.MUSIC -> if (musicEnabled) musicVolume else 0f
            AudioChannel.INTERFACE -> if (interfaceSoundsEnabled) interfaceVolume else 0f
            AudioChannel.TRAINING_CUE -> if (trainingCuesEnabled) trainingCueVolume else 0f
            AudioChannel.SUCCESS -> if (successEnabled) successVolume else 0f
            AudioChannel.ERROR -> if (errorEnabled) errorVolume else 0f
        }
    }

    fun hapticStrength(): Float = if (silentMode || !hapticsEnabled) 0f else hapticIntensity

    /**
     * Returns a copy with one channel switched on or off.
     *
     * The settings screen edits channels one at a time, so this mapping is the one
     * place that knows which flag belongs to which channel. Doing it in the UI would
     * duplicate the mapping across five sliders.
     */
    fun withChannel(channel: AudioChannel, enabled: Boolean): AudioSettings = when (channel) {
        AudioChannel.MUSIC -> copy(musicEnabled = enabled)
        AudioChannel.INTERFACE -> copy(interfaceSoundsEnabled = enabled)
        AudioChannel.TRAINING_CUE -> copy(trainingCuesEnabled = enabled)
        AudioChannel.SUCCESS -> copy(successEnabled = enabled)
        AudioChannel.ERROR -> copy(errorEnabled = enabled)
    }

    /** Returns a copy with one channel's volume set. Out-of-range values are clamped. */
    fun withVolume(channel: AudioChannel, volume: Float): AudioSettings {
        val safe = volume.coerceIn(0f, 1f)
        return when (channel) {
            AudioChannel.MUSIC -> copy(musicVolume = safe)
            AudioChannel.INTERFACE -> copy(interfaceVolume = safe)
            AudioChannel.TRAINING_CUE -> copy(trainingCueVolume = safe)
            AudioChannel.SUCCESS -> copy(successVolume = safe)
            AudioChannel.ERROR -> copy(errorVolume = safe)
        }
    }

    companion object {
        /** Every channel in a stable order, used by the settings screen and tests. */
        val CHANNELS: List<AudioChannel> = AudioChannel.entries
    }
}

/**
 * The five audio channels.
 *
 * [id] is the stable name used in string keys (`audio.<id>.volume`) and in stored
 * preferences. It is declared, not derived from [name], so renaming a constant can
 * never silently orphan a translated string.
 */
enum class AudioChannel(val id: String) {
    MUSIC("music"),
    INTERFACE("interface"),
    TRAINING_CUE("trainingCue"),
    SUCCESS("success"),
    ERROR("error"),
}

/** Theme definition, persisted per profile. Accent changes never affect logic. */
data class ThemeDefinition(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    /** ARGB packed color for the primary accent. */
    val accentArgb: Long,
    /** ARGB packed color for the secondary accent. */
    val secondaryArgb: Long,
    /** True when the surface palette is light. */
    val lightSurfaces: Boolean,
)
