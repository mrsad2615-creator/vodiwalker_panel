package com.neurox.feature.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptics backed by the platform vibrator.
 *
 * POLICY: the caller (AudioPolicy) decides *whether* a haptic may play and at what
 * strength. This class only converts that decision into platform calls, so silent
 * mode and the haptics switch are enforced in one tested place, not here.
 *
 * DURATION: 12-32 ms, scaled by strength. Longer buzzes stop being feedback and
 * become a distraction from the next stimulus.
 */
class AndroidHaptics(context: Context) : Haptics {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    override fun play(strength: Float) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        val safe = strength.coerceIn(0f, 1f)
        if (safe <= 0f) return
        val durationMillis = (MIN_MILLIS + (MAX_MILLIS - MIN_MILLIS) * safe).toLong()
        val amplitude = (1 + 254 * safe).toInt().coerceIn(1, 255)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createOneShot(durationMillis, amplitude))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(durationMillis)
            }
        }
    }

    private companion object {
        const val MIN_MILLIS = 12f
        const val MAX_MILLIS = 32f
    }
}
