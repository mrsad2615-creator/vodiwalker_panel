// NEUROX offline compile stubs - android.os: Bundle, Build, vibration.
package android.os

abstract class Bundle {
    open fun getString(key: String): String? = null
    open fun getString(key: String, defaultValue: String?): String? = defaultValue
    open fun getInt(key: String, defaultValue: Int): Int = defaultValue
    open fun getLong(key: String, defaultValue: Long): Long = defaultValue
    open fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    open fun containsKey(key: String): Boolean = false
}

object Build {
    object VERSION {
        const val SDK_INT: Int = 34
    }

    object VERSION_CODES {
        const val LOLLIPOP = 21
        const val M = 23
        const val N = 24
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
}

class VibratorManager {
    val defaultVibrator: Vibrator = Vibrator()
}

class Vibrator {
    fun hasVibrator(): Boolean = true
    fun vibrate(effect: VibrationEffect) {}
    fun vibrate(milliseconds: Long) {}
    fun cancel() {}
}

class VibrationEffect {
    companion object {
        const val DEFAULT_AMPLITUDE = -1
        fun createOneShot(milliseconds: Long, amplitude: Int): VibrationEffect = VibrationEffect()
        fun createWaveform(timings: LongArray, amplitudes: IntArray, repeat: Int): VibrationEffect = VibrationEffect()
    }
}
