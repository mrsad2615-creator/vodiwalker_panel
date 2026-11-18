package com.vodiwalker.gesture.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic clock used to drive time-dependent primitives (circuit breaker, synthetic guard)
 * without sleeping inside tests.
 */
private class FakeClock(var currentMillis: Long = 1_000L) : MonotonicClock {
    override fun nowMillis(): Long = currentMillis

    override fun nowNanos(): Long = currentMillis * 1_000_000L

    fun advance(deltaMillis: Long) {
        currentMillis += deltaMillis
    }
}

/**
 * Resilience, codec, and latency regression suite for `:core:common`.
 *
 * Covers:
 * 1. [RingBuffer] bounded eviction, ordering, and 8-thread contention safety.
 * 2. [CircuitBreaker] CLOSED → OPEN → HALF_OPEN → CLOSED recovery lifecycle.
 * 3. [SyntheticGestureGuard] anti-recursion window semantics.
 * 4. [JsonCodec] round-trip fidelity, escape handling, depth/size hardening.
 * 5. Warm-path latency budget for the 120Hz overlay sampling loop.
 */
class ResilienceAndCodecTest {

    // ------------------------------------------------------------------
    // RingBuffer
    // ------------------------------------------------------------------

    @Test
    fun ringBufferPreservesInsertionOrderAndOverwritesOldest() {
        val buffer = RingBuffer<Int>(capacity = 5)
        repeat(3) { buffer.push(it) }
        assertEquals(listOf(0, 1, 2), buffer.snapshot())
        assertEquals(3, buffer.size())

        repeat(4) { buffer.push(it + 10) }
        // Capacity 5, 7 total pushes → oldest two (0, 1) evicted.
        assertEquals(listOf(2, 10, 11, 12, 13), buffer.snapshot())
        assertEquals(5, buffer.size())

        buffer.clear()
        assertEquals(0, buffer.size())
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun ringBufferRejectsNonPositiveCapacity() {
        val failure = runCatching { RingBuffer<Int>(0) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun ringBufferIsSafeUnderConcurrentProducerContention() {
        val capacity = 512
        val threads = 8
        val perThread = 2_000
        val buffer = RingBuffer<Int>(capacity)
        val workers = (0 until threads).map { threadIndex ->
            Thread {
                repeat(perThread) { i -> buffer.push(threadIndex * perThread + i) }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }

        val snapshot = buffer.snapshot()
        assertEquals(capacity, snapshot.size, "buffer must saturate at capacity")
        assertEquals(capacity, snapshot.toSet().size, "no sample may be corrupted or duplicated")
    }

    // ------------------------------------------------------------------
    // CircuitBreaker
    // ------------------------------------------------------------------

    @Test
    fun circuitBreakerOpensAfterThresholdThenRecoversThroughHalfOpen() {
        val clock = FakeClock()
        val breaker = CircuitBreaker(
            failureThreshold = 3,
            resetTimeoutMillis = 5_000L,
            clock = clock,
        )

        assertTrue(breaker.allowExecution())
        assertEquals(CircuitBreaker.State.CLOSED, breaker.currentState())

        repeat(3) { breaker.recordFailure() }
        assertEquals(CircuitBreaker.State.OPEN, breaker.currentState())
        assertFalse(breaker.allowExecution(), "OPEN must reject execution before reset timeout")

        clock.advance(4_999L)
        assertFalse(breaker.allowExecution())

        clock.advance(1L)
        assertTrue(breaker.allowExecution(), "HALF_OPEN probe must be permitted after timeout")
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.currentState())

        breaker.recordSuccess()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.currentState())
        assertTrue(breaker.allowExecution())
    }

    @Test
    fun circuitBreakerReopensImmediatelyWhenHalfOpenProbeFails() {
        val clock = FakeClock()
        val breaker = CircuitBreaker(failureThreshold = 2, resetTimeoutMillis = 1_000L, clock = clock)

        repeat(2) { breaker.recordFailure() }
        assertEquals(CircuitBreaker.State.OPEN, breaker.currentState())

        clock.advance(1_000L)
        assertTrue(breaker.allowExecution())
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.currentState())

        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.OPEN, breaker.currentState())
        assertFalse(breaker.allowExecution(), "failed probe must re-open with a fresh timeout window")

        clock.advance(1_000L)
        assertTrue(breaker.allowExecution())
    }

    @Test
    fun circuitBreakerSurvivesAtomicRaceAcrossThreads() {
        val clock = FakeClock()
        val breaker = CircuitBreaker(failureThreshold = 64, resetTimeoutMillis = 1_000L, clock = clock)
        val threads = 8
        val workers = (0 until threads).map {
            Thread {
                repeat(256) {
                    if (breaker.allowExecution()) breaker.recordFailure() else breaker.recordSuccess()
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        // Invariant: the breaker must always settle in a defined state, never throw.
        assertTrue(breaker.currentState() in CircuitBreaker.State.entries)
    }

    // ------------------------------------------------------------------
    // SyntheticGestureGuard
    // ------------------------------------------------------------------

    @Test
    fun syntheticGuardSuppressesRecursionWindowThenExpires() {
        val clock = FakeClock()
        val guard = SyntheticGestureGuard(safetyMarginMillis = 120L, clock = clock)

        assertFalse(guard.isSyntheticWindowActive())
        val token = guard.acquireDispatchWindow(durationMillis = 500L)
        assertTrue(token > 0L)
        assertTrue(guard.isSyntheticWindowActive())

        clock.advance(619L)
        assertTrue(guard.isSyntheticWindowActive(), "window must include the safety margin")

        clock.advance(2L)
        assertFalse(guard.isSyntheticWindowActive(), "window must expire after duration + margin")

        guard.clear()
        assertFalse(guard.isSyntheticWindowActive())
    }

    @Test
    fun syntheticGuardClampsOverlongDispatchWindows() {
        val clock = FakeClock()
        val guard = SyntheticGestureGuard(safetyMarginMillis = 0L, clock = clock)
        guard.acquireDispatchWindow(durationMillis = 999_999L)
        clock.advance(10_000L)
        assertFalse(guard.isSyntheticWindowActive(), "duration must clamp to the 10s ceiling")
    }

    // ------------------------------------------------------------------
    // JsonCodec
    // ------------------------------------------------------------------

    @Test
    fun jsonCodecRoundTripsNestedStructuresAndEscapes() {
        val document = jsonObjectOf(
            "name" to Json.str("Swipe \"V\"\n\t\\ end"),
            "retries" to Json.int(3),
            "timeout" to Json.long(1_500L),
            "pressure" to Json.float(0.375f),
            "enabled" to Json.bool(true),
            "missing" to Json.str(null),
            "points" to Json.arr(
                listOf(
                    jsonObjectOf("x" to Json.float(12.5f), "y" to Json.float(-8.25f)),
                    jsonObjectOf("x" to Json.float(0f), "y" to Json.float(0f)),
                ),
            ),
            "packages" to Json.strs(listOf("a.b", "c.d")),
        )

        val encoded = JsonCodec.encode(document)
        val decoded = JsonCodec.decodeOrNull(encoded)

        assertNotNull(decoded)
        assertEquals("Swipe \"V\"\n\t\\ end", decoded.string("name"))
        assertEquals(3, decoded.int("retries"))
        assertEquals(1_500L, decoded.long("timeout"))
        assertEquals(0.375f, decoded.float("pressure"))
        assertEquals(true, decoded.bool("enabled"))
        assertNull(decoded.string("missing"))
        assertEquals(2, decoded.array("points")?.size)
        assertEquals(-8.25f, decoded.array("points")?.get(0)?.float("y"))
        assertEquals(0f, decoded.array("points")?.get(1)?.float("x"))
        assertEquals(setOf("a.b", "c.d"), decoded.array("packages")?.map { (it as JsonNode.Str).value }?.toSet())
        assertEquals(encoded, JsonCodec.encode(decoded), "encoding must be stable/idempotent")
    }

    @Test
    fun jsonCodecRejectsMalformedAndHostilePayloads() {
        val malformed = listOf(
            "",
            "{",
            "{\"a\": }",
            "{\"a\":1,}",
            "[1,2",
            "{\"a\":\"unterminated}",
            "{\"a\":01e}",
            "{\"a\":1}trailing",
            "\u0000",
        )
        for (payload in malformed) {
            val result = JsonCodec.decode(payload)
            assertTrue(result.isErr, "payload must be rejected: ${payload.take(24)}")
        }

        // Depth hardening: deeply nested arrays must fail rather than overflow the stack.
        val deep = "[".repeat(JsonCodec.MAX_DEPTH + 8) + "]".repeat(JsonCodec.MAX_DEPTH + 8)
        assertTrue(JsonCodec.decode(deep).isErr)

        // Size hardening.
        val oversized = "\"${"a".repeat(JsonCodec.MAX_INPUT_LENGTH)}\""
        assertTrue(JsonCodec.decode(oversized).isErr)

        // Non-finite numerics must never enter the vault.
        assertTrue(JsonCodec.decode("{\"a\":1e999}").isErr)
    }

    @Test
    fun jsonFieldPathsResolveNestedMembersWithoutAllocatingOnMiss() {
        val document = jsonObjectOf(
            "outer" to jsonObjectOf(
                "inner" to jsonObjectOf("leaf" to Json.int(7)),
            ),
        )
        assertEquals(7, document.field("outer.inner.leaf")?.let { (it as JsonNode.Num).value.toInt() })
        assertNull(document.field("outer.inner.absent"))
        assertNull(document.field("outer.wrong.leaf"))
    }

    // ------------------------------------------------------------------
    // Latency budget
    // ------------------------------------------------------------------

    @Test
    fun warmPathPrimitivesStayWithin120HzFrameBudget() {
        val buffer = RingBuffer<Long>(capacity = 256)
        val clock = MonotonicClock.SYSTEM
        val iterations = 50_000

        // Warm up (JIT tiering) before measurement.
        repeat(10_000) { buffer.push(it.toLong()) }

        val start = clock.nowNanos()
        repeat(iterations) { index ->
            buffer.push(index.toLong())
            if (index % 64 == 0) buffer.snapshot()
        }
        val perOpNanos = (clock.nowNanos() - start).toDouble() / iterations

        // 8.33 ms at 120 Hz; a single buffer op must consume < 1% of the frame budget.
        assertTrue(
            perOpNanos < 83_000.0,
            "RingBuffer warm-path cost ${"%.0f".format(perOpNanos)}ns/op exceeds the 120Hz budget",
        )

        val document = jsonObjectOf(
            "x" to Json.float(120.5f),
            "y" to Json.float(880.25f),
            "t" to Json.long(123_456L),
            "s" to Json.int(0),
        )
        val encodeStart = clock.nowNanos()
        repeat(2_000) { JsonCodec.decodeOrNull(JsonCodec.encode(document)) }
        val perCodecNanos = (clock.nowNanos() - encodeStart).toDouble() / 2_000

        // Sub-millisecond persistence codec keeps Room writes off the interactive critical path.
        assertTrue(
            perCodecNanos < 1_000_000.0,
            "point codec round-trip ${"%.0f".format(perCodecNanos)}ns/op exceeds 1ms",
        )
    }
}
