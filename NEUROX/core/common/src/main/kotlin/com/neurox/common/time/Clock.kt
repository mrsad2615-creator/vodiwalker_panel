package com.neurox.common.time

/**
 * Abstract source of time.
 *
 * All session timing MUST use a monotonic source [nanoTime]. Wall-clock time is
 * used only to label a session with a calendar instant (see [wallClockMillis]).
 *
 * The interface exists so the timing logic is deterministic under test and so
 * the platform layer can inject `System.nanoTime` / `System.currentTimeMillis`.
 */
interface Clock {
    /** Monotonic nanoseconds since an arbitrary origin. Never decreases. */
    fun nanoTime(): Long

    /** Wall-clock UTC milliseconds. Can move backwards. Never use for elapsed time. */
    fun wallClockMillis(): Long
}

/** Production clock backed by the JVM/Android system clocks. */
object SystemClock : Clock {
    override fun nanoTime(): Long = System.nanoTime()
    override fun wallClockMillis(): Long = System.currentTimeMillis()
}

/**
 * Deterministic clock for tests and for the golden-trace replay.
 *
 * Time does not advance on its own: the harness calls [advanceMillis].
 */
class ManualClock(
    startNanos: Long = 1_000_000_000L,
    startWallMillis: Long = 1_735_689_600_000L, // 2025-01-01T00:00:00Z
) : Clock {
    private var nanos: Long = startNanos
    private var wall: Long = startWallMillis

    override fun nanoTime(): Long = nanos
    override fun wallClockMillis(): Long = wall

    /** Advances both clocks. [millis] must be non negative. */
    fun advanceMillis(millis: Long) {
        require(millis >= 0) { "advanceMillis requires a non-negative value" }
        nanos += millis * 1_000_000L
        wall += millis
    }

    /** Advances only the monotonic clock. Simulates a wall-clock jump. */
    fun advanceNanosOnly(nanosDelta: Long) {
        require(nanosDelta >= 0) { "advanceNanosOnly requires a non-negative value" }
        nanos += nanosDelta
    }
}

/**
 * Which timing mode a session used.
 *
 * Measurements between the two modes are NOT comparable; analytics must keep
 * them apart (see domain AnalyticsService and docs/explanation of metrics).
 */
enum class TimingMode {
    /** Responses timed from an audio cue through the audio path. */
    AUDIO,

    /** Responses timed from a visual frame through the display path. */
    VISUAL,
}

/**
 * Estimates a per-platform input/render offset that is subtracted from raw
 * measured intervals. Values come from device-class defaults and are adjustable
 * by the user in Settings; they never change exercise logic, only presentation
 * of the measured value.
 */
data class TimingCalibration(
    val inputLatencyMillis: Double = 0.0,
    val displayLatencyMillis: Double = 0.0,
) {
    init {
        require(inputLatencyMillis >= 0.0) { "inputLatencyMillis must be >= 0" }
        require(displayLatencyMillis >= 0.0) { "displayLatencyMillis must be >= 0" }
    }

    /** Converts a raw measured interval into a corrected value, clamped at zero. */
    fun correctedMillis(rawMillis: Long, mode: TimingMode): Double {
        val offset = when (mode) {
            TimingMode.AUDIO -> inputLatencyMillis
            TimingMode.VISUAL -> inputLatencyMillis + displayLatencyMillis
        }
        return (rawMillis - offset).coerceAtLeast(0.0)
    }

    companion object {
        val NONE = TimingCalibration()
    }
}
