package com.mrsad.ambient.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.mrsad.ambient.domain.model.BatteryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BatteryMonitor(private val context: Context) {

    private val _state = MutableStateFlow(readCurrentBatteryState())
    val state: StateFlow<BatteryState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            _state.value = parseIntent(intent)
        }
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun readCurrentBatteryState(): BatteryState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return if (intent != null) parseIntent(intent) else BatteryState(0, false, "Unknown")
    }

    private fun parseIntent(intent: Intent): BatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }

        // Honest estimate based on remaining percentage when charging
        val estMinutes = if (isCharging && pct < 100) ((100 - pct) * 1.1f).toInt().coerceAtLeast(1) else null

        return BatteryState(
            percentage = pct,
            isCharging = isCharging,
            chargingSource = source,
            estimatedMinutesToFull = estMinutes,
            isLowBattery = pct <= 20
        )
    }
}
