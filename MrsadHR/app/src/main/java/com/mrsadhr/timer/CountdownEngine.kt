package com.mrsadhr.timer

import com.mrsadhr.core.model.TimerSnapshot
import com.mrsadhr.core.time.TimeSource

/**
 * Countdown engine.
 *
 * Accuracy rule: the engine never decrements a counter. While running it stores an absolute
 * monotonic deadline and derives the remaining time from the clock, so dropped frames, doze,
 * screen-off and process pauses cannot accumulate drift.
 */
class CountdownEngine(private val time: TimeSource) {

    var durationMs: Long = DEFAULT_DURATION_MS
        private set

    private var running: Boolean = false
    private var deadlineMono: Long = 0L
    private var frozenRemaining: Long = durationMs
    private var completed: Boolean = false

    /** Monotonic stamp of the most recent state change, used by persistence. */
    var lastChangeMono: Long = time.monoMs()
        private set
    var lastChangeWall: Long = time.wallMs()
        private set

    fun snapshot(): TimerSnapshot {
        val remaining = remainingMs()
        return TimerSnapshot(durationMs, remaining, running, completed)
    }

    fun setDuration(ms: Long) {
        val clamped = ms.coerceIn(0L, MAX_DURATION_MS)
        durationMs = clamped
        frozenRemaining = clamped
        running = false
        completed = false
        markChanged()
    }

    fun start(): Boolean {
        if (running) return false
        if (completed || frozenRemaining <= 0L) {
            // Starting again after completion restarts the whole duration.
            frozenRemaining = durationMs
            completed = false
        }
        if (durationMs <= 0L) return false
        deadlineMono = time.monoMs() + frozenRemaining
        running = true
        markChanged()
        return true
    }

    fun pause(): Boolean {
        if (!running) return false
        frozenRemaining = remainingMs()
        running = false
        if (frozenRemaining <= 0L) completed = true
        markChanged()
        return true
    }

    fun reset() {
        running = false
        completed = false
        frozenRemaining = durationMs
        markChanged()
    }

    /** Called by the controller when the deadline has been reached. */
    fun finish() {
        running = false
        completed = true
        frozenRemaining = 0L
        markChanged()
    }

    fun dismissCompletion() {
        completed = false
        frozenRemaining = durationMs
        markChanged()
    }

    fun remainingMs(): Long {
        if (!running) return frozenRemaining.coerceAtLeast(0L)
        return (deadlineMono - time.monoMs()).coerceAtLeast(0L)
    }

    fun hasExpired(): Boolean = running && remainingMs() <= 0L

    val isRunning: Boolean get() = running

    // ---------------------------------------------------------------- persistence

    class Persisted(
        val durationMs: Long,
        val remainingMs: Long,
        val running: Boolean,
        val completed: Boolean,
        val deadlineMono: Long,
        val savedMono: Long,
        val savedWall: Long,
    )

    fun persist(): Persisted = Persisted(
        durationMs = durationMs,
        remainingMs = remainingMs(),
        running = running,
        completed = completed,
        deadlineMono = deadlineMono,
        savedMono = time.monoMs(),
        savedWall = time.wallMs(),
    )

    /**
     * Restores a countdown after process death — and after a reboot, where the monotonic clock has
     * been reset. In that case the wall clock is used and the result is clamped so a reboot can
     * never resurrect a timer with a negative or phantom remaining time.
     */
    fun restore(p: Persisted) {
        durationMs = p.durationMs.coerceIn(0L, MAX_DURATION_MS)
        completed = p.completed
        if (!p.running) {
            frozenRemaining = p.remainingMs.coerceIn(0L, durationMs)
            running = false
            markChanged()
            return
        }
        val nowMono = time.monoMs()
        val nowWall = time.wallMs()
        val monoDelta = nowMono - p.savedMono
        val elapsed = if (monoDelta >= 0L && monoDelta < p.remainingMs + REBOOT_WINDOW_MS) {
            // Same boot: the monotonic clock is authoritative.
            monoDelta
        } else {
            // Different boot (or absurd gap): fall back to wall clock.
            (nowWall - p.savedWall).coerceAtLeast(0L)
        }
        val remaining = (p.remainingMs - elapsed).coerceAtLeast(0L)
        if (remaining <= 0L) {
            running = false
            frozenRemaining = 0L
            completed = true
        } else {
            running = true
            frozenRemaining = remaining
            deadlineMono = nowMono + remaining
        }
        markChanged()
    }

    private fun markChanged() {
        lastChangeMono = time.monoMs()
        lastChangeWall = time.wallMs()
    }

    companion object {
        const val DEFAULT_DURATION_MS = 5 * 60_000L
        const val MAX_DURATION_MS = 99L * 3600_000L + 59L * 60_000L + 59_000L
        private const val REBOOT_WINDOW_MS = 30L * 24 * 3600_000L
    }
}
