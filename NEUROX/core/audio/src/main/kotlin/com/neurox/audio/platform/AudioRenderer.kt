package com.neurox.audio.platform

import com.neurox.audio.AudioPolicy
import com.neurox.audio.SoundCatalog
import com.neurox.audio.Synth
import com.neurox.domain.model.AudioSettings
import java.util.concurrent.LinkedBlockingQueue

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread

/**
 * Plays a synthesized cue through a short-lived AudioTrack.
 *
 * PLATFORM CODE. This is the only Android dependency in core:audio, and it lives in a
 * `platform` package so the offline verifier can exclude it (android.jar is not
 * available in the sandbox). Everything it calls - Synth, SoundCatalog, AudioPolicy -
 * is pure Kotlin and covered by tests. This class is the thin edge that needs a device,
 * and it is kept deliberately small.
 *
 * WHY A TRACK PER CUE: cues are 90-160 ms. A persistent track would need a buffer
 * scheduler, a mixing loop and teardown for every lifecycle edge, and would still play
 * one sound at a time. A short MODE_STATIC track per cue is cheap at this length and
 * allows overlap.
 *
 * THREADING: writes block, so they happen on one dedicated thread. That single thread
 * is also what guarantees cues are written in the order they were requested - two taps
 * can never arrive reversed.
 *
 * QUEUING: a bounded [LinkedBlockingQueue]. `take()` blocks the worker without any
 * waiting/notification protocol, so there is no missed wakeup and no interrupt to
 * ignore. When the queue is full the OLDEST cue is dropped: if the device cannot keep
 * up, replaying feedback from ten seconds ago is worse than silence.
 *
 * FAILURE BEHAVIOUR: audio is decorative except for signal cues, and a device can
 * refuse a track at any time. Every failure is reported once through [onError] and
 * swallowed; a session must never crash because a beep could not play.
 */
class AudioRenderer(
    /**
     * Policy gate. Every request goes through it, so silent mode, focus mode and the
     * spacing rules cannot be bypassed by a caller that forgets to check them. Audio
     * that is not gated is not "off" - it is decoration that only looks off in the UI.
     */
    private val policy: AudioPolicy,
    settingsProvider: () -> AudioSettings = { AudioSettings() },
    private val onError: (String) -> Unit = {},
) {
    private val catalog = SoundCatalog(settingsProvider)


    private val queue = LinkedBlockingQueue<Cue>(MAX_QUEUED)

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var released = false

    /** A cue paired with its gain, so the worker does not re-derive one. */
    private class Cue(val pcm: ShortArray, val gain: Float)

    /**
     * Requests [cue]. Returns true when it was queued.
     *
     * The caller does not choose the gain: the policy derives it from the user's
     * per-channel settings, which is what makes the volume sliders real.
     */
    fun play(cue: Synth.Cue): Boolean {
        if (released) return false
        val decision = policy.decide(cue)
        if (decision !is AudioPolicy.PlaybackDecision.Play) return false
        val gain = decision.gain
        val pcm = runCatching { catalog.pcm(cue, gain) }.getOrElse { error ->
            onError("render ${cue.name}: ${error.message}")
            return false
        }
        // offer() is non-blocking; a full queue means we are behind, so make room by
        // discarding the oldest cue rather than the newest.
        if (!queue.offer(Cue(pcm, gain))) {
            queue.poll()
            queue.offer(Cue(pcm, gain))
        }
        // Recorded only after the cue is actually queued, so a dropped request cannot
        // suppress the next legitimate one.
        policy.onPlayed(cue)
        ensureWorker()
        return true
    }

    private fun ensureWorker() {
        if (worker?.isAlive == true) return
        synchronized(this) {
            if (worker?.isAlive == true) return
            worker = thread(name = "neurox-audio", isDaemon = true) {
                while (!released) {
                    // Blocks until a cue arrives. The 1s poll exists only so `release()`
                    // is observed promptly; it is not a spin.
                    val next = try {
                        queue.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        null
                    } ?: continue
                    if (released) break
                    writeBlocking(next.pcm)
                }
            }
        }
    }

    private fun writeBlocking(pcm: ShortArray) {
        var track: AudioTrack? = null
        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            // A negative value means the configuration is unsupported on this device.
            if (minBuffer <= 0) {
                onError("playback: unsupported output configuration ($minBuffer)")
                return
            }
            val bufferBytes = maxOf(minBuffer, pcm.size * BYTES_PER_SAMPLE)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            // A static track stops by itself once its buffer is consumed. Waiting for
            // that duration is what lets one thread play cues in sequence; releasing the
            // track earlier would cut the sound off.
            val durationMillis = pcm.size * 1_000L / SAMPLE_RATE
            try {
                Thread.sleep(durationMillis + TAIL_MILLIS)
            } catch (interrupted: InterruptedException) {
                // A release during playback: stop the cue immediately, keep the flag.
                Thread.currentThread().interrupt()
            }
        } catch (failure: Exception) {
            onError("playback: ${failure.javaClass.simpleName}: ${failure.message}")
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
        }
    }

    /** Stops the worker and drops anything queued. Safe to call more than once. */
    fun release() {
        released = true
        queue.clear()
        // Interrupting the worker makes its 1s poll return immediately; the track in
        // flight is stopped by its own finally block.
        worker?.interrupt()
        worker = null
    }

    companion object {
        /** Standard CD-quality rate: every device supports it, and cues are tiny. */
        const val SAMPLE_RATE = 44_100
        private const val TAIL_MILLIS = 24L
        private const val BYTES_PER_SAMPLE = 2
        private const val MAX_QUEUED = 4

        /**
         * True when the device reports a usable output.
         *
         * This is a capability check, not a playback check: a positive minimum buffer
         * means the platform accepts 44.1 kHz mono 16-bit. It says nothing about the
         * volume, the route, or whether the user will hear anything.
         */
        fun hasOutput(): Boolean = runCatching {
            AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ) > 0
        }.getOrDefault(false)
    }
}
