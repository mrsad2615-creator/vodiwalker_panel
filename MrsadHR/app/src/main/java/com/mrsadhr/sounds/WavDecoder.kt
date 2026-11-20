package com.mrsadhr.sounds

/**
 * Minimal RIFF/WAVE reader for the 16 bit PCM assets this project generates.
 *
 * Kept free of platform imports so it can be unit tested on a plain JVM and reused by the Android
 * audio engine. Chunks are walked instead of assumed, so a writer that adds metadata still decodes.
 */
object WavDecoder {

    const val SAMPLE_RATE = 44100

    fun decode(bytes: ByteArray): ShortArray? {
        if (bytes.size < 44) return null
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return null
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) return null
        var offset = 12
        var channels = 1
        var bits = 16
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = leInt(bytes, offset + 4)
            val body = offset + 8
            if (size < 0 || body + size > bytes.size) {
                if (id == "data") {
                    return pcmSlice(bytes, body, bytes.size - body, channels, bits)
                }
                return null
            }
            when (id) {
                "fmt " -> {
                    channels = leShort(bytes, body + 2)
                    bits = leShort(bytes, body + 14)
                }
                "data" -> return pcmSlice(bytes, body, size, channels, bits)
            }
            offset = body + size + (size and 1)
        }
        return null
    }

    private fun pcmSlice(bytes: ByteArray, offset: Int, size: Int, channels: Int, bits: Int): ShortArray? {
        if (bits != 16 || channels < 1) return null
        val total = size / 2
        if (total <= 0) return null
        val frames = total / channels
        if (frames <= 0) return null
        val out = ShortArray(frames)
        val view = java.nio.ByteBuffer.wrap(bytes, offset, size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            var acc = 0
            for (c in 0 until channels) acc += view.short.toInt()
            out[i] = (acc / channels).toShort()
        }
        return out
    }

    private fun leInt(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun leShort(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
}
