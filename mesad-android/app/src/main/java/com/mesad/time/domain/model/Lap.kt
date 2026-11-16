package com.mesad.time.domain.model

/**
 * Domain model for a stopwatch lap.
 */
data class Lap(
    val id: Long = 0,
    val stopwatchId: Long = 1, // Single stopwatch instance
    val lapNumber: Int,
    val totalElapsedMs: Long,  // Monotonic elapsed at lap moment
    val deltaMs: Long,         // Delta from previous lap
    val isBest: Boolean = false,
    val isWorst: Boolean = false
) {
    init {
        require(lapNumber > 0) { "Lap number must be positive" }
        require(totalElapsedMs >= 0) { "Elapsed cannot be negative" }
        require(deltaMs >= 0) { "Delta cannot be negative" }
    }
}
