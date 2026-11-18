package com.vodiwalker.gesture.engine.gesture

import com.vodiwalker.gesture.core.model.GesturePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Signal-integrity suite for the [AdaptiveOneEuroFilter] velocity-adaptive jitter suppressor.
 *
 * Validates the three properties that matter on a real 120Hz digitizer:
 * 1. **Jitter attenuation** — high-frequency tremor is measurably reduced.
 * 2. **Phase fidelity** — ballistic swipes are not pulled behind the finger.
 * 3. **Determinism** — NaN/Infinity, reversed timestamps, and digitizer teleports are sanitized
 *    rather than propagated into the feature extractor.
 */
class AdaptiveOneEuroFilterTest {

    private fun tremorSeries(
        samples: Int,
        amplitudePx: Float = 7f,
        noiseHz: Float = 45f,
        baseY: Float = 900f,
    ): List<GesturePoint> = List(samples) { index ->
        val t = index * 8L
        GesturePoint(
            x = 160f + index * 6f + amplitudePx * sin(2.0 * Math.PI * noiseHz * index / 120.0).toFloat(),
            y = baseY,
            timestampMillis = t,
            strokeId = 0,
        )
    }

    /** Mean absolute second difference — a standard proxy for perceived high-frequency tremor. */
    private fun jitterMetric(points: List<GesturePoint>): Float {
        if (points.size < 3) return 0f
        var sum = 0f
        for (i in 2 until points.size) {
            val dx = points[i].x - 2f * points[i - 1].x + points[i - 2].x
            val dy = points[i].y - 2f * points[i - 1].y + points[i - 2].y
            sum += hypot(dx, dy)
        }
        return sum / (points.size - 2)
    }

    @Test
    fun filterAttenuatesHighFrequencyTremor() {
        val raw = tremorSeries(samples = 240)
        val filtered = AdaptiveOneEuroFilter().filterSequence(raw)

        assertEquals(raw.size, filtered.size, "no valid sample may be dropped")
        val rawJitter = jitterMetric(raw)
        val filteredJitter = jitterMetric(filtered)
        assertTrue(rawJitter > 0f, "synthetic tremor must be non-trivial")
        assertTrue(
            filteredJitter < rawJitter * 0.62f,
            "expected >=38% jitter reduction, got ${"%.1f".format(100 * (1 - filteredJitter / rawJitter))}%",
        )
    }

    @Test
    fun filterKeepsBallisticSwipeLagBelowOneFrame() {
        // 1 px/ms horizontal swipe at a 120Hz cadence (8ms between samples).
        val raw = List(120) { index ->
            GesturePoint(
                x = 100f + index * 8f,
                y = 1200f,
                timestampMillis = index * 8L,
                strokeId = 0,
            )
        }
        val filtered = AdaptiveOneEuroFilter().filterSequence(raw)
        assertEquals(raw.size, filtered.size)

        // Skip the filter priming region, then require the steady-state lag to stay sub-frame.
        val steadyState = 20 until filtered.size
        val meanLagPx = steadyState.map { abs(filtered[it].x - raw[it].x) }.average()
        assertTrue(
            meanLagPx < 8.0,
            "ballistic lag ${"%.2f".format(meanLagPx)}px must stay below one 8ms frame (8px at 1px/ms)",
        )

        // Monotonic ordering is preserved for a monotonic input.
        for (i in 1 until filtered.size) {
            assertTrue(filtered[i].x >= filtered[i - 1].x - 0.001f)
        }
    }

    @Test
    fun filterRejectsNonFiniteSamplesAndClampsPressure() {
        val filter = AdaptiveOneEuroFilter()
        assertNull(filter.filterSample(GesturePoint(Float.NaN, 0f, 0L)))
        assertNull(filter.filterSample(GesturePoint(0f, Float.POSITIVE_INFINITY, 0L)))
        assertNull(filter.filterSample(GesturePoint(0f, 0f, -1L)))

        val clamped = filter.filterSample(GesturePoint(10f, 10f, 0L, pressure = 4.5f))
        assertNotNull(clamped)
        assertEquals(1f, clamped.pressure)

        val clampedLow = filter.filterSample(GesturePoint(11f, 10f, 8L, pressure = -2f))
        assertNotNull(clampedLow)
        assertEquals(0f, clampedLow.pressure)
    }

    @Test
    fun filterClampsDigitizerTeleportSpikes() {
        val filter = AdaptiveOneEuroFilter(maxPlausibleVelocityPxPerMs = 45f)
        filter.filterSample(GesturePoint(0f, 0f, 0L))
        // 5000px in 8ms = 625 px/ms → physically impossible; must be clamped to the velocity cap.
        val teleport = filter.filterSample(GesturePoint(5_000f, 0f, 8L))
        assertNotNull(teleport)
        assertTrue(teleport.x < 5_000f, "impossible jump must be clamped")
        assertTrue(teleport.x.isFinite() && teleport.y.isFinite())
    }

    @Test
    fun filterHandlesReversedTimestampsWithoutProducingNaN() {
        val filter = AdaptiveOneEuroFilter()
        filter.filterSample(GesturePoint(10f, 10f, 500L))
        // Out-of-order / stale timestamp must not divide by zero.
        val stale = filter.filterSample(GesturePoint(20f, 20f, 100L))
        assertNotNull(stale)
        assertTrue(stale.x.isFinite() && stale.timestampMillis >= 500L)

        // Duplicate timestamps (dt == 0) must also be safe.
        val duplicate = filter.filterSample(GesturePoint(30f, 30f, stale.timestampMillis))
        assertNotNull(duplicate)
        assertTrue(duplicate.x.isFinite())
    }

    @Test
    fun filterResetsCleanlyBetweenStrokes() {
        val filter = AdaptiveOneEuroFilter()
        val firstStroke = (0 until 20).map { i ->
            GesturePoint(i * 10f, 0f, i * 8L, strokeId = 0)
        }
        val secondStroke = (0 until 20).map { i ->
            GesturePoint(1_000f + i * 10f, 500f, 1_000L + i * 8L, strokeId = 1)
        }

        val a = filter.filterSequence(firstStroke)
        val b = filter.filterSequence(secondStroke)
        assertEquals(firstStroke.size, a.size)
        assertEquals(secondStroke.size, b.size)

        // Re-running the same input must be bit-for-bit deterministic (no hidden residual state).
        val c = AdaptiveOneEuroFilter().filterSequence(secondStroke)
        assertEquals(b, c)
    }

    @Test
    fun filterThroughputSustains240HzTwoFingerSampling() {
        val filter = AdaptiveOneEuroFilter()
        val samples = (0 until 24_000).map { i ->
            val t = i * 4L
            GesturePoint(
                x = 500f + sin(i / 40.0).toFloat() * 200f,
                y = 900f + (i % 37) * 2f,
                timestampMillis = t,
            )
        }

        // Warm up JIT.
        filter.filterSequence(samples.take(2_000))

        val start = System.nanoTime()
        val output = filter.filterSequence(samples)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val perSampleUs = (elapsedMs * 1_000.0) / samples.size

        assertEquals(samples.size, output.size)
        // 240Hz × 2 fingers = 480 samples/s; a 1.5ms/sample ceiling leaves ~99.7% headroom.
        assertTrue(
            perSampleUs < 1_500.0,
            "filter cost ${"%.2f".format(perSampleUs)}µs/sample exceeds the 1.5ms interactive ceiling",
        )
    }
}
