package com.mesad.time.domain.usecase

import com.mesad.time.domain.model.Alarm
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Calculates the next alarm trigger from a list of enabled alarms.
 * Handles repeat weekdays, one-time alarms, DST transitions.
 * Pure Kotlin — fully testable.
 */
class NextAlarmCalculator {

    /**
     * Find the alarm with the earliest next trigger after [now].
     * Returns null if no enabled alarms exist.
     */
    fun findNextAlarm(
        alarms: List<Alarm>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Alarm? {
        val enabled = alarms.filter { it.enabled }
        if (enabled.isEmpty()) return null

        return enabled
            .mapNotNull { alarm ->
                val trigger = calculateNextTrigger(alarm, now, zone)
                trigger?.let { alarm to it }
            }
            .minByOrNull { (_, trigger) -> trigger }
            ?.let { (alarm, trigger) ->
                alarm.copy(
                    nextTriggerWallClock = trigger.toEpochMilli(),
                    nextTriggerElapsedRealtime = null // Calculated by platform scheduler
                )
            }
    }

    /**
     * Calculate the next trigger instant for a single alarm.
     * Returns null if the alarm has no future trigger (e.g., one-time alarm already passed).
     */
    fun calculateNextTrigger(
        alarm: Alarm,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Instant? {
        val nowZoned = now.atZone(zone)

        if (alarm.isOneTime) {
            return calculateOneTimeTrigger(alarm, nowZoned)
        }

        return calculateRepeatingTrigger(alarm, nowZoned)
    }

    private fun calculateOneTimeTrigger(alarm: Alarm, now: ZonedDateTime): Instant? {
        // Today at alarm time
        val todayTrigger = now
            .withHour(alarm.hour)
            .withMinute(alarm.minute)
            .withSecond(0)
            .withNano(0)

        return if (todayTrigger.isAfter(now)) {
            todayTrigger.toInstant()
        } else {
            // Tomorrow at alarm time
            todayTrigger.plusDays(1).toInstant()
        }
    }

    private fun calculateRepeatingTrigger(alarm: Alarm, now: ZonedDateTime): Instant? {
        // Check today first
        val todayTrigger = now
            .withHour(alarm.hour)
            .withMinute(alarm.minute)
            .withSecond(0)
            .withNano(0)

        if (todayTrigger.isAfter(now) && now.dayOfWeek in alarm.repeatDays) {
            return todayTrigger.toInstant()
        }

        // Check next 7 days
        for (daysAhead in 1..7) {
            val candidate = now.plusDays(daysAhead.toLong())
                .withHour(alarm.hour)
                .withMinute(alarm.minute)
                .withSecond(0)
                .withNano(0)

            if (candidate.dayOfWeek in alarm.repeatDays) {
                return candidate.toInstant()
            }
        }

        return null
    }

    /**
     * Calculate trigger for a snoozed alarm (one-time, N minutes from now).
     */
    fun calculateSnoozeTrigger(snoozeMinutes: Int, now: Instant = Instant.now()): Instant {
        return now.plusSeconds(snoozeMinutes.toLong() * 60)
    }

    /**
     * Check if an alarm should fire at the given instant (within 60-second window).
     */
    fun shouldFire(alarm: Alarm, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val trigger = calculateNextTrigger(alarm, now.minusSeconds(60), zone)
            ?: return false
        val windowStart = trigger.minusSeconds(60)
        val windowEnd = trigger.plusSeconds(60)
        return now.isAfter(windowStart) && now.isBefore(windowEnd)
    }
}
