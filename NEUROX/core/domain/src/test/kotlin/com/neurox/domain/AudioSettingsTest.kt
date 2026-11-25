package com.neurox.domain

import com.neurox.domain.model.AudioChannel
import com.neurox.domain.model.AudioSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Channel editing rules.
 *
 * The settings screen writes one channel at a time; these tests pin the mapping so
 * moving a slider can never silence a different channel.
 */
class AudioSettingsTest {

    @Test
    fun everyChannelTogglesIndependently() {
        for (channel in AudioChannel.entries) {
            val off = AudioSettings().withChannel(channel, false)
            assertEquals("disabling $channel must silence it", 0f, off.effective(channel), 0f)
            for (other in AudioChannel.entries) {
                if (other == channel || other == AudioChannel.MUSIC) continue
                assertTrue(
                    "disabling $channel must not silence $other",
                    off.effective(other) > 0f,
                )
            }
        }
    }

    @Test
    fun volumeEditsOnlyAffectTheirOwnChannel() {
        for (channel in AudioChannel.entries) {
            // MUSIC is off by default, so its effective volume is 0 whatever the
            // slider says. Enable it first: the point here is the mapping, not the flag.
            val quiet = AudioSettings(musicEnabled = true).withVolume(channel, 0.1f)
            assertEquals(0.1f, quiet.effective(channel), 0.0001f)
            for (other in AudioChannel.entries) {
                if (other == channel || other == AudioChannel.MUSIC) continue
                assertTrue(quiet.effective(other) > 0.1f)
            }
        }
    }

    @Test
    fun volumeIsClampedInsteadOfThrowing() {
        val loud = AudioSettings().withVolume(AudioChannel.SUCCESS, 5f)
        assertEquals(1f, loud.successVolume, 0f)
        val negative = AudioSettings().withVolume(AudioChannel.SUCCESS, -2f)
        assertEquals(0f, negative.successVolume, 0f)
    }

    @Test
    fun silentModeOverridesEveryChannelAndHaptics() {
        val silent = AudioSettings(silentMode = true)
        for (channel in AudioChannel.entries) {
            assertEquals(0f, silent.effective(channel), 0f)
        }
        assertEquals(0f, silent.hapticStrength(), 0f)
    }

    @Test
    fun focusModeKeepsOnlyTrainingCues() {
        val focus = AudioSettings(focusMode = true, musicEnabled = true)
        assertTrue(focus.effective(AudioChannel.TRAINING_CUE) > 0f)
        for (channel in AudioChannel.entries.filter { it != AudioChannel.TRAINING_CUE }) {
            assertEquals("focus mode must silence $channel", 0f, focus.effective(channel), 0f)
        }
    }

    @Test
    fun hapticsAreZeroWhenDisabledButNotOtherwise() {
        assertFalse(AudioSettings(hapticsEnabled = false).hapticStrength() > 0f)
        assertTrue(AudioSettings(hapticsEnabled = true).hapticStrength() > 0f)
    }

    @Test
    fun theChannelListCoversEveryChannel() {
        assertEquals(AudioChannel.entries.size, AudioSettings.CHANNELS.size)
    }
}
