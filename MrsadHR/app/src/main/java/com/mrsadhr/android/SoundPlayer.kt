package com.mrsadhr.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.mrsadhr.platform.SoundEngine
import com.mrsadhr.sounds.SoundCatalog
import com.mrsadhr.sounds.WavDecoder

/**
 * Completion audio.
 *
 * The bundled clips are 16 bit mono PCM WAV, so they are decoded straight into memory and played on
 * a static [AudioTrack]: no streaming latency, no codec variance between devices, and the user's
 * chosen volume is applied to the track itself. Two independent slots are kept so a preview can
 * never cut off the completion alert.
 */
class SoundPlayer(private val context: Context) : SoundEngine {

    private val handler = Handler(Looper.getMainLooper())
    private val decoded = HashMap<String, ShortArray>()
    private var previewTrack: AudioTrack? = null
    private var completionTrack: AudioTrack? = null
    private var released = false

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun playPreview(soundId: String, volume: Float) {
        if (released) return
        stopPreview()
        previewTrack = start(soundId, volume)
    }

    override fun stopPreview() {
        release(previewTrack)
        previewTrack = null
    }

    override fun playCompletion(soundId: String, volume: Float) {
        if (released) return
        stopCompletion()
        completionTrack = start(soundId, volume)
    }

    override fun stopCompletion() {
        release(completionTrack)
        completionTrack = null
    }

    override fun release() {
        released = true
        stopPreview()
        stopCompletion()
        decoded.clear()
    }

    private fun start(soundId: String, volume: Float): AudioTrack? {
        val pcm = pcm(soundId) ?: return null
        val def = SoundCatalog.byId(soundId)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        } catch (_: Exception) {
            return null
        }
        return try {
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) {
                track.release()
                return null
            }
            val gain = (volume * def.gain).coerceIn(0f, 1f)
            if (gain <= 0.001f) {
                track.release()
                return null
            }
            track.setVolume(gain)
            track.play()
            // A static track holds the buffer; release it once the clip has certainly finished.
            handler.postDelayed({ release(track) }, (def.durationMs + 220).toLong())
            track
        } catch (_: Exception) {
            track.release()
            null
        }
    }

    private fun release(track: AudioTrack?) {
        if (track == null) return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        } catch (_: Exception) {
        }
        try {
            track.flush()
            track.release()
        } catch (_: Exception) {
        }
    }

    /** Decodes `sounds/<id>.wav` from assets. Returns null when the clip is missing or malformed. */
    private fun pcm(soundId: String): ShortArray? {
        decoded[soundId]?.let { return it }
        val bytes = try {
            context.assets.open(SoundCatalog.byId(soundId).asset).use { it.readBytes() }
        } catch (_: Exception) {
            return null
        }
        val samples = WavDecoder.decode(bytes) ?: return null
        decoded[soundId] = samples
        return samples
    }

    companion object {
        private const val SAMPLE_RATE = WavDecoder.SAMPLE_RATE
    }
}
