package com.mrsadhr.core.time

/**
 * Monotonic time source. On device this is [android.os.SystemClock.elapsedRealtime], which keeps
 * counting while the screen is off and the app is in the background — the only correct basis for a
 * countdown.
 */
interface TimeSource {
    fun monoMs(): Long
    fun wallMs(): Long
}

class ManualTimeSource(var mono: Long = 1_000L, var wall: Long = 1_700_000_000_000L) : TimeSource {
    override fun monoMs(): Long = mono
    override fun wallMs(): Long = wall
    fun advance(ms: Long) {
        mono += ms
        wall += ms
    }
}
