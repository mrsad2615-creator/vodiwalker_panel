// NEUROX offline compile stubs - the Android framework surface this app uses.
//
// These mirror android.jar well enough to type-check. Nothing here ever runs.
package android.media

class AudioFormat private constructor() {
    class Builder {
        fun setEncoding(encoding: Int): Builder = this
        fun setSampleRate(sampleRate: Int): Builder = this
        fun setChannelMask(channelMask: Int): Builder = this
        fun build(): AudioFormat = AudioFormat()
    }

    companion object {
        const val ENCODING_PCM_16BIT = 2
        const val ENCODING_PCM_FLOAT = 4
        const val CHANNEL_OUT_MONO = 4
        const val CHANNEL_OUT_STEREO = 12
        const val CHANNEL_IN_MONO = 16
    }
}

class AudioAttributes {
    class Builder {
        fun setUsage(usage: Int): Builder = this
        fun setContentType(contentType: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }

    companion object {
        const val USAGE_MEDIA = 1
        const val USAGE_GAME = 14
        const val USAGE_ASSISTANCE_SONIFICATION = 13
        const val CONTENT_TYPE_SONIFICATION = 4
        const val CONTENT_TYPE_MUSIC = 2
    }
}

open class AudioTrack {
    class Builder {
        fun setAudioAttributes(attributes: AudioAttributes): Builder = this
        fun setAudioFormat(format: AudioFormat): Builder = this
        fun setTransferMode(mode: Int): Builder = this
        fun setBufferSizeInBytes(size: Int): Builder = this
        fun build(): AudioTrack = AudioTrack()
    }

    var playState: Int = 0

    fun play() {}
    fun pause() {}
    fun stop() {}
    fun flush() {}
    fun release() {}
    fun write(buffer: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int = sizeInShorts
    fun write(buffer: FloatArray, offsetInFloats: Int, sizeInFloats: Int, writeMode: Int): Int = sizeInFloats

    companion object {
        const val PLAYSTATE_PLAYING = 3
        const val PLAYSTATE_PAUSED = 2
        const val PLAYSTATE_STOPPED = 1
        const val MODE_STATIC = 0
        const val MODE_STREAM = 1
        const val WRITE_BLOCKING = 0
        const val STATE_INITIALIZED = 1
        const val ERROR_BAD_VALUE = -2
        fun getMinBufferSize(sampleRateInHz: Int, channelConfig: Int, audioFormat: Int): Int = 4096
    }
}

class AudioManager {
    companion object {
        const val STREAM_MUSIC = 3
        const val AUDIOFOCUS_GAIN = 1
        const val AUDIOFOCUS_LOSS = -1
        const val AUDIOFOCUS_REQUEST_GRANTED = 1
    }
}

