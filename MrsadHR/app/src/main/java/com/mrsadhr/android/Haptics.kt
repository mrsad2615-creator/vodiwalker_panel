package com.mrsadhr.android

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mrsadhr.platform.HapticKind

/**
 * Tactile vocabulary: five short, distinguishable patterns. Every call is a no-op when the device
 * has no vibrator or the user turned haptics off (the controller already filters that).
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val supported: Boolean = vibrator?.hasVibrator() == true

    fun play(kind: HapticKind) {
        val v = vibrator ?: return
        if (!supported) return
        val (timings, amplitudes) = when (kind) {
            HapticKind.Light -> longArrayOf(0, 9) to intArrayOf(0, 70)
            HapticKind.Medium -> longArrayOf(0, 16) to intArrayOf(0, 140)
            HapticKind.Heavy -> longArrayOf(0, 26) to intArrayOf(0, 210)
            HapticKind.Success -> longArrayOf(0, 14, 48, 22) to intArrayOf(0, 150, 0, 230)
            HapticKind.Warning -> longArrayOf(0, 30, 60, 30) to intArrayOf(0, 200, 0, 200)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        } catch (_: Exception) {
            // A device that rejects the pattern must never break interaction.
        }
    }
}
