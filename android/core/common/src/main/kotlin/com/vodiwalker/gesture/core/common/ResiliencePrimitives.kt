package com.vodiwalker.gesture.core.common

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Allocation-light typed result wrapper ensuring explicit error handling without throwing
 * unchecked exceptions across Accessibility or gesture processing boundaries.
 */
sealed interface AppResult<out T, out E> {
    /**
     * Successful outcome carrying [value].
     */
    data class Ok<T>(val value: T) : AppResult<T, Nothing>
    /**
     * Failed outcome carrying a typed [error].
     */
    data class Err<E>(val error: E) : AppResult<Nothing, E>

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    /**
     * Returns the payload, or null when this is an [Err].
     */
    fun getOrNull(): T? = (this as? Ok)?.value
    /**
     * Returns the error, or null when this is an [Ok].
     */
    fun errorOrNull(): E? = (this as? Err)?.error
}

/**
 * Maps the success payload while preserving the typed error channel.
 */
inline fun <T, E, R> AppResult<T, E>.map(transform: (T) -> R): AppResult<R, E> = when (this) {
    is AppResult.Ok -> AppResult.Ok(transform(value))
    is AppResult.Err -> this
}

/**
 * Exhaustive domain error taxonomy across gesture recording, recognition, and DAG automation.
 */
sealed interface GestureError {
    /**
     * Caller-supplied data failed validation.
     */
    data class InvalidInput(val message: String) : GestureError
    /**
     * A new template is indistinguishable from an existing one.
     */
    data class TemplateCollision(val conflictingTemplateId: String, val similarity: Float) : GestureError
    /**
     * The DAG contains a cycle and cannot be executed.
     */
    data class CyclicActionGraph(val involvedNodeIds: List<String>) : GestureError
    /**
     * An edge points at a node that does not exist.
     */
    data class MissingNodeReference(val fromNodeId: String, val missingTargetId: String) : GestureError
    /**
     * The graph exceeds the maximum supported depth.
     */
    data class DepthLimitExceeded(val depth: Int, val maxAllowed: Int) : GestureError
    /**
     * A required platform permission is missing.
     */
    data class PermissionDenied(val permissionName: String) : GestureError
    /**
     * A node or graph exceeded its execution budget.
     */
    data class TimeoutExceeded(val nodeId: String, val limitMillis: Long) : GestureError
    /**
     * An underlying platform call or action handler failed.
     */
    data class PlatformFailure(val code: String, val message: String) : GestureError
}

/**
 * Injectable monotonic clock abstraction for deterministic unit testing.
 */
fun interface MonotonicClock {
    /**
     * Current time in milliseconds.
     */
    fun nowMillis(): Long

    /**
     * Current time in nanoseconds (defaults to millisecond resolution).
     */
    fun nowNanos(): Long = nowMillis() * 1_000_000L

    companion object {
        val SYSTEM: MonotonicClock = object : MonotonicClock {
            override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
            override fun nowNanos(): Long = System.nanoTime()
        }
    }
}

/**
 * Bounded, pre-allocated ring buffer for high-frequency 120Hz/240Hz touch points and service
 * telemetry. Prevents garbage-collection pauses on the interactive overlay path.
 */
class RingBuffer<T>(val capacity: Int) {
    init {
        require(capacity > 0) { "RingBuffer capacity must be positive, got $capacity" }
    }

    private val storage = arrayOfNulls<Any>(capacity)
    private var head = 0
    private var count = 0

    /**
     * Appends [element], overwriting the oldest entry once capacity is reached.
     */
    @Synchronized
    fun push(element: T) {
        val writeIndex = (head + count) % capacity
        storage[writeIndex] = element
        if (count == capacity) {
            head = (head + 1) % capacity
        } else {
            count++
        }
    }

    /**
     * All live entries in insertion order as an immutable copy.
     */
    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun snapshot(): List<T> {
        if (count == 0) return emptyList()
        val result = ArrayList<T>(count)
        for (i in 0 until count) {
            result.add(storage[(head + i) % capacity] as T)
        }
        return result
    }

    /**
     * Number of live entries.
     */
    @Synchronized
    fun size(): Int = count

    /**
     * Discards every entry.
     */
    @Synchronized
    fun clear() {
        storage.fill(null)
        head = 0
        count = 0
    }
}

/**
 * Lock-free self-healing Circuit Breaker isolating faulty platform actions so a failing target app
 * or transient binder timeout never cascades into an ANR or service crash.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val resetTimeoutMillis: Long = 10_000L,
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
) {
    /**
     * Breaker states: CLOSED (healthy), OPEN (rejecting), HALF_OPEN (probing).
     */
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private data class Snapshot(
        val state: State,
        val consecutiveFailures: Int,
        val openedAtMillis: Long,
    )

    private val ref = AtomicReference(Snapshot(State.CLOSED, 0, 0L))

    init {
        require(failureThreshold >= 1) { "failureThreshold must be >= 1" }
        require(resetTimeoutMillis >= 1L) { "resetTimeoutMillis must be >= 1" }
    }

    /**
     * Returns true when a call may proceed; promotes OPEN → HALF_OPEN after the cooldown.
     */
    fun allowExecution(): Boolean {
        while (true) {
            val current = ref.get()
            when (current.state) {
                State.CLOSED -> return true
                State.HALF_OPEN -> return true
                State.OPEN -> {
                    val elapsed = clock.nowMillis() - current.openedAtMillis
                    if (elapsed < resetTimeoutMillis) return false
                    val next = current.copy(state = State.HALF_OPEN)
                    if (ref.compareAndSet(current, next)) return true
                }
            }
        }
    }

    /**
     * Records a healthy call and closes the breaker.
     */
    fun recordSuccess() {
        ref.set(Snapshot(State.CLOSED, 0, 0L))
    }

    /**
     * Records a failure; opens the breaker at the threshold or on a failed probe.
     */
    fun recordFailure() {
        while (true) {
            val current = ref.get()
            val failures = current.consecutiveFailures + 1
            val shouldOpen = current.state == State.HALF_OPEN || failures >= failureThreshold
            val next = if (shouldOpen) {
                Snapshot(State.OPEN, failures, clock.nowMillis())
            } else {
                Snapshot(State.CLOSED, failures, 0L)
            }
            if (ref.compareAndSet(current, next)) return
        }
    }

    /**
     * Current breaker state (lock-free snapshot).
     */
    fun currentState(): State = ref.get().state
}

/**
 * Anti-recursion guard tracking programmatically dispatched `GestureDescription` windows so
 * synthetic gestures never re-trigger user gesture recognition in an infinite feedback loop.
 */
class SyntheticGestureGuard(
    private val safetyMarginMillis: Long = 120L,
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
) {
    private val suppressUntilMillis = AtomicLong(0L)
    private val tokenCounter = AtomicLong(1L)

    /** Marks a synthetic gesture window active for [durationMillis] and returns a trace token. */
    fun acquireDispatchWindow(durationMillis: Long): Long {
        val safeDuration = durationMillis.coerceIn(0L, 10_000L)
        val deadline = clock.nowMillis() + safeDuration + safetyMarginMillis
        suppressUntilMillis.updateAndGet { existing -> maxOf(existing, deadline) }
        return tokenCounter.getAndIncrement()
    }

    /** Returns true if incoming touch events fall within a synthetic gesture window. */
    fun isSyntheticWindowActive(): Boolean = clock.nowMillis() < suppressUntilMillis.get()

    /**
     * Cancels any active suppression window immediately.
     */
    fun clear() {
        suppressUntilMillis.set(0L)
    }
}

/**
 * Abstract pause primitive used by the Smart-Action engine for `WaitDelay` nodes and retry
 * backoff.
 *
 * The domain layer never calls `Thread.sleep` directly: the AccessibilityService supplies an
 * interruptible, coroutine-aware implementation running off the main thread while tests inject
 * a no-op recorder, keeping the DAG engine fully deterministic and free of real wall-clock waits.
 */
fun interface Sleeper {
    /** Pauses the calling (background) thread for [durationMillis], returning false if interrupted. */
    fun sleep(durationMillis: Long): Boolean

    companion object {
        /** Production implementation; restores the interrupt flag instead of swallowing it. */
        val BLOCKING: Sleeper = Sleeper { durationMillis ->
            if (durationMillis <= 0L) {
                true
            } else {
                try {
                    Thread.sleep(durationMillis)
                    true
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
            }
        }

        /** Deterministic no-op sleeper for unit tests and latency benchmarks. */
        val NO_OP: Sleeper = Sleeper { true }
    }
}
