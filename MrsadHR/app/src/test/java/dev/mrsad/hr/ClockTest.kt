package dev.mrsad.hr

import org.junit.Assert.*
import org.junit.Test

class ClockTest {
    @Test
    fun delayedFramesDoNotAccumulateError() {
        val clock = Clock(goal = 12000).start(200)
        assertEquals(3701, clock.value(8499))
        assertEquals(0, clock.value(99999))
    }

    @Test
    fun pauseResumeAndReset() {
        val paused = Clock(goal = 1500).start(0).pause(500)
        assertEquals(1000, paused.value(700000))
        val resumed = paused.start(900000)
        assertEquals(99, resumed.value(900901))
        assertEquals(Clock(goal = 1500), resumed.reset())
    }

    @Test
    fun noZeroCountdownAndCompletionIsIdempotent() {
        assertEquals(Phase.READY, Clock(goal = 0).start(500).phase)
        val done = Clock(goal = 1).start(100).settle(101)
        assertEquals(Phase.COMPLETE, done.phase)
        assertEquals(done, done.settle(102))
        assertEquals(1, done.start(300).value(300))
    }

    @Test
    fun stopwatchAndLapHistory() {
        val s = Clock(watch = true).start(1234).lap(1567).pause(2000).start(4500).lap(4750)
        assertEquals(listOf(333L, 1016L), s.laps)
        assertEquals(1017, s.value(4751))
        assertTrue(s.reset().laps.isEmpty())
    }

    @Test
    fun reconstructWithoutTicks() {
        val s = Clock(goal = 45000, bank = 1000, anchor = 3000, phase = Phase.RUNNING)
        assertEquals(24000, s.value(23000))
        assertEquals(Phase.COMPLETE, s.settle(100000).phase)
    }
}
