package com.mrsadhr.stopwatch

import com.mrsadhr.core.model.Lap
import com.mrsadhr.core.model.StopwatchSnapshot
import com.mrsadhr.core.time.TimeSource

/**
 * Stopwatch built on the same monotonic authority as the countdown engine: an absolute start stamp
 * plus accumulated time from previous runs. No per-frame accumulation, so it stays exact.
 */
class StopwatchEngine(private val time: TimeSource) {

    private var running = false
    private var startedAtMono = 0L
    private var accumulated = 0L
    private val laps = mutableListOf<Lap>()
    private var lapsView: List<Lap> = emptyList()

    fun snapshot(): StopwatchSnapshot = StopwatchSnapshot(elapsedMs(), running, lapsView)

    fun elapsedMs(): Long = if (running) accumulated + (time.monoMs() - startedAtMono) else accumulated

    fun start() {
        if (running) return
        startedAtMono = time.monoMs()
        running = true
    }

    fun pause() {
        if (!running) return
        accumulated += time.monoMs() - startedAtMono
        running = false
    }

    fun reset() {
        running = false
        accumulated = 0L
        startedAtMono = time.monoMs()
        laps.clear()
        lapsView = emptyList()
    }

    fun lap(): Lap? {
        val total = elapsedMs()
        if (total <= 0L) return null
        val previous = laps.lastOrNull()?.totalMs ?: 0L
        val lap = Lap(laps.size + 1, total, total - previous)
        laps.add(lap)
        lapsView = java.util.Collections.unmodifiableList(laps.toList())
        return lap
    }

    fun clearLaps() {
        laps.clear()
        lapsView = emptyList()
    }

    val lapCount: Int get() = laps.size

    class Persisted(
        val running: Boolean,
        val elapsed: Long,
        val startedMono: Long,
        val laps: List<Lap>,
        val savedMono: Long = 0L,
    )

    fun persist(): Persisted = Persisted(running, elapsedMs(), startedAtMono, laps.toList(), time.monoMs())

    fun restore(p: Persisted) {
        laps.clear()
        laps.addAll(p.laps)
        lapsView = java.util.Collections.unmodifiableList(laps.toList())
        if (p.running) {
            // Keep counting through the gap the app was not running.
            val since = if (p.savedMono > 0L) (time.monoMs() - p.savedMono).coerceAtLeast(0L) else 0L
            running = true
            startedAtMono = time.monoMs()
            accumulated = p.elapsed + since
        } else {
            running = false
            accumulated = p.elapsed
        }
    }
}
