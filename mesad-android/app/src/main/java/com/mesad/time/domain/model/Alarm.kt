package com.mesad.time.domain.model

import java.time.DayOfWeek

/**
 * Domain model for a smart alarm.
 * Pure Kotlin — no Android dependencies.
 */
data class Alarm(
    val id: Long = 0,
    val hour: Int,                    // 0-23
    val minute: Int,                  // 0-59
    val label: String = "",
    val repeatDays: Set<DayOfWeek> = emptySet(), // empty = one-time
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val nextTriggerElapsedRealtime: Long? = null, // Monotonic deadline
    val nextTriggerWallClock: Long? = null,       // Wall-clock backup for reboot
    val createdAt: Long = System.currentTimeMillis()
) {
    val isRepeating: Boolean get() = repeatDays.isNotEmpty()
    val isOneTime: Boolean get() = repeatDays.isEmpty()

    init {
        require(hour in 0..23) { "Hour must be 0-23, was $hour" }
        require(minute in 0..59) { "Minute must be 0-59, was $minute" }
        require(snoozeMinutes in 1..60) { "Snooze must be 1-60 minutes" }
    }
}
