package com.mesad.time.domain.model

/**
 * Domain model for a countdown timer.
 * Uses monotonic deadline math — never decrementing counters.
 */
data class TimerSession(
    val id: Long = 0,
    val label: String = "",
    val durationMs: Long,
    val deadlineElapsedRealtime: Long? = null, // Monotonic: elapsedRealtime() + duration
    val deadlineWallClock: Long? = null,       // Wall-clock backup for reboot recovery
    val state: TimerState = TimerState.IDLE,
    val remainingMs: Long = durationMs,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(durationMs > 0) { "Duration must be positive" }
        require(durationMs <= 24 * 60 * 60 * 1000L) { "Duration max 24 hours" }
    }

    val isRunning: Boolean get() = state == TimerState.RUNNING
    val isPaused: Boolean get() = state == TimerState.PAUSED
    val isFinished: Boolean get() = state == TimerState.FINISHED
    val isIdle: Boolean get() = state == TimerState.IDLE

    /**
     * Calculate remaining time using monotonic clock.
     * @param nowElapsedRealtime Current SystemClock.elapsedRealtime()
     */
    fun calculateRemaining(nowElapsedRealtime: Long): Long {
        return when (state) {
            TimerState.RUNNING -> {
                val deadline = deadlineElapsedRealtime ?: return remainingMs
                (deadline - nowElapsedRealtime).coerceAtLeast(0L)
            }
            TimerState.PAUSED, TimerState.IDLE -> remainingMs
            TimerState.FINISHED -> 0L
        }
    }

    /**
     * Progress 0.0 (just started) to 1.0 (finished).
     */
    fun calculateProgress(nowElapsedRealtime: Long): Float {
        val remaining = calculateRemaining(nowElapsedRealtime)
        return if (durationMs == 0L) 1f else 1f - (remaining.toFloat() / durationMs.toFloat())
    }
}

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }
