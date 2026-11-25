package com.neurox.audio

import com.neurox.domain.model.AudioChannel
import com.neurox.domain.model.AudioSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SynthTest {

    @Test
    fun everyCueRendersTheDocumentedLength() {
        Synth.Cue.entries.forEach { cue ->
            val pcm = Synth.render(cue)
            val expectedSamples = Synth.durationMs(cue) * Synth.SAMPLE_RATE / 1_000
            assertTrue("${cue.name} length", abs(pcm.size - expectedSamples) <= expectedSamples / 8 + 1)
            assertTrue("${cue.name} must not be empty", pcm.isNotEmpty())
        }
    }

    @Test
    fun noCueClips() {
        Synth.Cue.entries.forEach { cue ->
            val pcm = Synth.render(cue)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("${cue.name} peak $peak must stay inside 16-bit range", peak <= Short.MAX_VALUE)
            // The design keeps about 3 dB of headroom.
            assertTrue("${cue.name} peak $peak must respect headroom", peak <= (Short.MAX_VALUE * 0.75))
        }
    }

    @Test
    fun cuesStartAndEndSilentlyToAvoidClicks() {
        Synth.Cue.entries.forEach { cue ->
            val pcm = Synth.render(cue)
            assertTrue("${cue.name} must start at zero", abs(pcm.first().toInt()) < 200)
            assertTrue("${cue.name} must end at zero", abs(pcm.last().toInt()) < 200)
        }
    }

    @Test
    fun zeroGainProducesNothing() {
        assertEquals(0, Synth.render(Synth.Cue.START, gain = 0f).size)
    }

    @Test
    fun louderGainMeansLouderBuffer() {
        val quiet = Synth.render(Synth.Cue.STIMULUS, gain = 0.2f).maxOf { abs(it.toInt()) }
        val loud = Synth.render(Synth.Cue.STIMULUS, gain = 0.9f).maxOf { abs(it.toInt()) }
        assertTrue(loud > quiet)
    }

    @Test
    fun envelopeIsZeroOutsideTheSound() {
        assertEquals(0.0, Synth.envelope(-1.0, 100.0, 5.0, 20.0), 1e-9)
        assertEquals(0.0, Synth.envelope(101.0, 100.0, 5.0, 20.0), 1e-9)
        assertTrue(Synth.envelope(50.0, 100.0, 5.0, 20.0) > 0.9)
        assertTrue(Synth.envelope(0.5, 100.0, 5.0, 20.0) < 0.2)
        assertTrue(Synth.envelope(99.0, 100.0, 5.0, 20.0) < 0.2)
    }

    @Test
    fun chordAndArpeggioStayInsideRange() {
        val chord = Synth.chord(intArrayOf(440, 550, 660), 120, 1f)
        assertTrue(chord.maxOf { abs(it.toInt()) } <= Short.MAX_VALUE)
        val arpeggio = Synth.arpeggio(intArrayOf(440, 550, 660), 60, 1f)
        assertTrue(arpeggio.maxOf { abs(it.toInt()) } <= Short.MAX_VALUE)
        assertTrue(arpeggio.size > chord.size / 2)
    }

    @Test
    fun invalidParametersAreRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            Synth.tone(0.0, 100, 1f)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            Synth.tone(440.0, 0, 1f)
        }
    }

    @Test
    fun renderingIsDeterministic() {
        val a = Synth.render(Synth.Cue.FINISH)
        val b = Synth.render(Synth.Cue.FINISH)
        assertTrue(a.contentEquals(b))
    }
}

class AudioPolicyTest {

    private var settings = AudioSettings()
    private var now = 1_000L

    // A fresh policy per test: playback spacing is state, so sharing one instance
    // between tests would make the result depend on test order.
    private var policy = newPolicy()

    private fun newPolicy(): AudioPolicy {
        // Full reset: settings and time are shared by the test class, so a policy is
        // only meaningful together with a clean starting state.
        settings = AudioSettings()
        now = 1_000L
        return AudioPolicy(settingsProvider = { settings }, clock = { now })
    }

    @Test
    fun playsWhenConfiguredAndForeground() {
        policy = newPolicy()
        val decision = policy.decide(Synth.Cue.STIMULUS)
        assertTrue(decision is AudioPolicy.PlaybackDecision.Play)
    }

    @Test
    fun silentModeSuppressesEveryChannel() {
        policy = newPolicy()
        settings = settings.copy(silentMode = true)
        Synth.Cue.entries.forEach { cue ->
            assertTrue(
                "silent mode must suppress ${cue.name}",
                policy.decide(cue) is AudioPolicy.PlaybackDecision.Silent,
            )
        }
        assertFalse(policy.shouldPlayMusic())
    }

    @Test
    fun focusModeKeepsOnlyTrainingCues() {
        policy = newPolicy()
        settings = settings.copy(focusMode = true)
        assertTrue(policy.decide(Synth.Cue.STIMULUS) is AudioPolicy.PlaybackDecision.Play)
        assertTrue(policy.decide(Synth.Cue.TAP) is AudioPolicy.PlaybackDecision.Silent)
        assertTrue(policy.decide(Synth.Cue.HIT) is AudioPolicy.PlaybackDecision.Silent)
        assertFalse(policy.shouldPlayMusic())
    }

    @Test
    fun channelsAreIndependent() {
        policy = newPolicy()
        settings = settings.copy(musicEnabled = true, interfaceSoundsEnabled = false)
        assertTrue(policy.decide(Synth.Cue.STIMULUS) is AudioPolicy.PlaybackDecision.Play)
        assertTrue(policy.decide(Synth.Cue.TAP) is AudioPolicy.PlaybackDecision.Silent)
    }

    @Test
    fun nothingPlaysInTheBackground() {
        policy = newPolicy()
        policy.setForeground(false)
        assertTrue(policy.decide(Synth.Cue.HIT) is AudioPolicy.PlaybackDecision.Silent)
        assertFalse(policy.shouldPlayMusic())
    }

    @Test
    fun repeatedCuesWithinTheSpacingAreDropped() {
        policy = newPolicy()
        val first = policy.decide(Synth.Cue.HIT)
        assertTrue("first HIT must play but was $first", first is AudioPolicy.PlaybackDecision.Play)
        policy.onPlayed(Synth.Cue.HIT)
        now += 10
        val repeated = policy.decide(Synth.Cue.HIT)
        assertTrue(repeated is AudioPolicy.PlaybackDecision.Silent)
        assertEquals("too_soon", (repeated as AudioPolicy.PlaybackDecision.Silent).reason)
    }

    @Test
    fun cuesSpacedBeyondTheFloorPlayAgain() {
        policy = newPolicy()
        policy.onPlayed(Synth.Cue.HIT)
        now += AudioPolicy.MIN_FEEDBACK_SPACING_MILLIS + 1
        assertTrue(policy.decide(Synth.Cue.HIT) is AudioPolicy.PlaybackDecision.Play)
    }

    @Test
    fun trainingCueInterruptsOthers() {
        policy = newPolicy()
        val decision = policy.decide(Synth.Cue.STIMULUS)
        assertTrue(decision is AudioPolicy.PlaybackDecision.Play)
        assertTrue((decision as AudioPolicy.PlaybackDecision.Play).interruptOthers)
        policy.onPlayed(Synth.Cue.STIMULUS)
        now += AudioPolicy.MIN_FEEDBACK_SPACING_MILLIS + 1
        settings = settings.copy(errorEnabled = true)
        val feedback = policy.decide(Synth.Cue.HIT)
        assertTrue("HIT must play but was $feedback", feedback is AudioPolicy.PlaybackDecision.Play)
        assertFalse("feedback must not interrupt", (feedback as AudioPolicy.PlaybackDecision.Play).interruptOthers)
    }

    @Test
    fun hapticsFollowSilentModeAndIntensity() {
        policy = newPolicy()
        assertTrue(policy.hapticStrength(Synth.Cue.STIMULUS) > 0f)
        settings = settings.copy(hapticsEnabled = false)
        assertEquals(0f, policy.hapticStrength(Synth.Cue.STIMULUS), 1e-6f)
        settings = settings.copy(hapticsEnabled = true, silentMode = true)
        assertEquals(0f, policy.hapticStrength(Synth.Cue.MISS), 1e-6f)
        settings = settings.copy(silentMode = false, hapticIntensity = 1f)
        assertEquals(1f, policy.hapticStrength(Synth.Cue.STIMULUS), 1e-6f)
    }

    @Test
    fun hapticsAreZerosInTheBackground() {
        policy = newPolicy()
        policy.setForeground(false)
        assertEquals(0f, policy.hapticStrength(Synth.Cue.HIT), 1e-6f)
    }

    @Test
    fun musicRequiresEnabledChannelAndForeground() {
        policy = newPolicy()
        settings = AudioSettings(musicEnabled = true, musicVolume = 0.5f)
        assertEquals(0.5f, settings.effective(AudioChannel.MUSIC), 1e-6f)
        assertTrue("music must be allowed here", policy.shouldPlayMusic())
        policy.setForeground(false)
        assertFalse(policy.shouldPlayMusic())
    }

    @Test
    fun effectiveGainFollowsTheChannelControls() {
        val s = AudioSettings(musicVolume = 0.3f, trainingCueVolume = 0.9f, silentMode = false)
        assertEquals(0.9f, s.effective(AudioChannel.TRAINING_CUE), 1e-6f)
        assertEquals(0f, s.effective(AudioChannel.MUSIC), 1e-6f)
        assertEquals(0f, s.copy(silentMode = true).effective(AudioChannel.TRAINING_CUE), 1e-6f)
        assertEquals(
            0f,
            s.copy(focusMode = true).effective(AudioChannel.SUCCESS),
            1e-6f,
        )
        assertEquals(
            0.9f,
            s.copy(focusMode = true).effective(AudioChannel.TRAINING_CUE),
            1e-6f,
        )
    }
}

class SoundCatalogTest {

    @Test
    fun gainIsQuantizedToCacheableSteps() {
        val catalog = SoundCatalog { AudioSettings() }
        assertEquals(0, catalog.gainStep(0f))
        assertEquals(SoundCatalog.GAIN_STEPS, catalog.gainStep(1f))
        assertEquals(catalog.gainStep(0.55f), catalog.gainStep(0.57f))
    }

    @Test
    fun everyCueHasAStreamAndPcm() {
        val catalog = SoundCatalog { AudioSettings() }
        catalog.cueList.forEach { cue ->
            assertTrue(catalog.pcm(cue, 0.8f).isNotEmpty())
            assertEquals(
                if (cue.channel == AudioChannel.TRAINING_CUE) {
                    SoundRequest.StreamKind.MEDIA
                } else {
                    SoundRequest.StreamKind.UI
                },
                catalog.streamOf(cue),
            )
        }
    }

    @Test
    fun cueRoutingMatchesChannels() {
        assertEquals(AudioChannel.TRAINING_CUE, CueRouting.channelOf(Synth.Cue.STIMULUS))
        assertEquals(AudioChannel.ERROR, CueRouting.channelOf(Synth.Cue.MISS))
        assertEquals(AudioChannel.SUCCESS, CueRouting.channelOf(Synth.Cue.HIT))
        assertTrue(CueRouting.isSilent(Synth.Cue.HIT, AudioSettings(silentMode = true)))
    }
}
