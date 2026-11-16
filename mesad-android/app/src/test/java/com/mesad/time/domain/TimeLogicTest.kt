package com.mesad.time.domain

import com.google.common.truth.Truth.assertThat
import com.mesad.time.domain.model.Alarm
import com.mesad.time.domain.model.TimerSession
import com.mesad.time.domain.model.TimerState
import com.mesad.time.domain.usecase.NextAlarmCalculator
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeLogicTest {
    @Test fun stopwatchDeadlineStyleDurationDoesNotTickDrift() {
        val startElapsed = 10_000L
        val accumulated = 2_500L
        assertThat(25_000L - startElapsed + accumulated).isEqualTo(17_500L)
    }
    @Test fun timerUsesMonotonicDeadlineAndClampsAtZero() {
        val timer = TimerSession(durationMs=60_000, deadlineElapsedRealtime=90_000, state=TimerState.RUNNING, remainingMs=60_000)
        assertThat(timer.calculateRemaining(30_000)).isEqualTo(60_000)
        assertThat(timer.calculateRemaining(90_002)).isEqualTo(0)
        assertThat(timer.calculateProgress(90_002)).isEqualTo(1f)
    }
    @Test fun pausedTimerKeepsPersistedRemainingTime() {
        val timer = TimerSession(durationMs=90_000, state=TimerState.PAUSED, remainingMs=42_500)
        assertThat(timer.calculateRemaining(1_000_000)).isEqualTo(42_500)
    }
    @Test fun oneTimeAlarmMovesToTomorrowAfterItsTime() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 11, 16, 8, 30, 0, 0, zone).toInstant()
        val alarm = Alarm(hour=7, minute=0)
        val next = NextAlarmCalculator().calculateNextTrigger(alarm, now, zone)!!
        assertThat(next.atZone(zone).dayOfMonth).isEqualTo(17)
        assertThat(next.atZone(zone).hour).isEqualTo(7)
    }
    @Test fun repeatingAlarmHonorsSelectedWeekdaysAndDstZoneRules() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 7, 23, 30, 0, 0, zone).toInstant()
        val alarm = Alarm(hour=7, minute=30, repeatDays=setOf(DayOfWeek.SUNDAY))
        val next = NextAlarmCalculator().calculateNextTrigger(alarm, now, zone)!!.atZone(zone)
        assertThat(next.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(next.hour).isEqualTo(7)
        assertThat(next.offset.totalSeconds).isEqualTo(-4 * 3600) // spring DST transition
    }
    @Test fun snoozeTriggerIsExactMinuteOffset() {
        val start = Instant.parse("2026-11-16T12:00:00Z")
        assertThat(NextAlarmCalculator().calculateSnoozeTrigger(10,start)).isEqualTo(start.plusSeconds(600))
    }
}
