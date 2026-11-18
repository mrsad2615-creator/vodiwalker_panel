package com.vodiwalker.gesture.engine.gesture

import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.common.GestureError
import com.vodiwalker.gesture.core.common.MonotonicClock
import com.vodiwalker.gesture.core.common.SyntheticGestureGuard
import com.vodiwalker.gesture.core.model.GestureCapture
import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureStroke
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.RecognitionOutcome
import com.vodiwalker.gesture.core.model.RejectionReason
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HybridGestureEngineTest {

    private fun buildLibrary(engine: HybridGestureEngine): List<GestureTemplate> {
        val list = mutableListOf<GestureTemplate>()
        fun add(id: String, name: String, capture: GestureCapture, orientSensitive: Boolean = true) {
            val res = engine.createCalibratedTemplate(
                id = id,
                name = name,
                category = GestureCategory.CUSTOM,
                calibrationCaptures = listOf(capture),
                existingTemplates = list,
                orientationSensitive = orientSensitive,
            )
            assertIs<AppResult.Ok<GestureTemplate>>(res, "Failed creating template $id: $res")
            list.add(res.value)
        }
        add("swipe_right", "Swipe Right", lineGesture(100f, 500f, 700f, 510f))
        add("swipe_left", "Swipe Left", lineGesture(700f, 500f, 100f, 495f))
        add("chevron_v", "V Check", polylineGesture(listOf(120f to 300f, 360f to 760f, 620f to 280f)))
        add("caret_up", "Caret Up", polylineGesture(listOf(120f to 760f, 360f to 280f, 620f to 760f)))
        add("circle_cw", "Circle CW", circleGesture(400f, 600f, 220f, clockwise = true))
        add("z_lightning", "Z Bolt", polylineGesture(listOf(150f to 250f, 650f to 250f, 150f to 750f, 650f to 750f)))
        add("cross_x", "Multi-Stroke X", multiStrokeX(400f, 500f, 220f), orientSensitive = false)
        return list
    }

    @Test
    fun recognizesSingleAndMultiStrokeTemplatesWithNoisyInput() {
        val engine = HybridGestureEngine()
        val library = buildLibrary(engine)
        val rng = Random(2026)

        val testCases = listOf(
            "swipe_right" to addJitter(lineGesture(180f, 900f, 820f, 915f), rng, 5f),
            "swipe_left" to addJitter(lineGesture(840f, 620f, 190f, 610f), rng, 5f),
            "chevron_v" to addJitter(
                polylineGesture(listOf(200f to 400f, 420f to 830f, 670f to 380f)),
                rng,
                6f,
            ),
            "caret_up" to addJitter(
                polylineGesture(listOf(180f to 820f, 410f to 340f, 650f to 810f)),
                rng,
                6f,
            ),
            "circle_cw" to addJitter(circleGesture(520f, 740f, 175f, clockwise = true), rng, 5f),
            "z_lightning" to addJitter(
                polylineGesture(listOf(200f to 300f, 580f to 305f, 210f to 690f, 600f to 695f)),
                rng,
                5f,
            ),
            // Draw X with reversed stroke order to verify $P+ multi-stroke order invariance
            "cross_x" to multiStrokeX(500f, 700f, 180f, reverseOrder = true),
        )

        for ((expectedId, capture) in testCases) {
            val outcome = engine.recognize(capture, library, fullEnsemble = true)
            assertIs<RecognitionOutcome.Matched>(outcome, "Expected match for $expectedId, got $outcome")
            assertEquals(expectedId, outcome.winner.templateId, "Misclassified $expectedId as ${outcome.winner}")
            assertTrue(outcome.winner.confidence >= 0.72f, "Low confidence: ${outcome.winner.confidence}")
        }
    }

    @Test
    fun rejectsDuplicateTemplateCollisionDuringCalibration() {
        val engine = HybridGestureEngine()
        val library = buildLibrary(engine)
        val duplicateV = polylineGesture(listOf(130f to 310f, 365f to 755f, 615f to 285f))

        val result = engine.createCalibratedTemplate(
            id = "duplicate_v",
            name = "Another V",
            category = GestureCategory.CUSTOM,
            calibrationCaptures = listOf(duplicateV),
            existingTemplates = library,
        )

        assertIs<AppResult.Err<GestureError>>(result)
        val err = assertIs<GestureError.TemplateCollision>(result.error)
        assertEquals("chevron_v", err.conflictingTemplateId)
    }

    @Test
    fun blocksSyntheticGestureFeedbackLoopAndPrivacySensitiveWindows() {
        var now = 1_000L
        val fakeClock = MonotonicClock { now }
        val guard = SyntheticGestureGuard(safetyMarginMillis = 100L, clock = fakeClock)
        val engine = HybridGestureEngine(syntheticGuard = guard, clock = fakeClock)
        val library = buildLibrary(engine)
        val swipe = lineGesture(100f, 500f, 700f, 500f)

        // Privacy shield blocks recognition unconditionally
        val privacyOutcome = engine.recognize(swipe, library, isPrivacySensitiveWindow = true)
        assertIs<RecognitionOutcome.Rejected>(privacyOutcome)
        assertEquals(RejectionReason.PRIVACY_SHIELD_ACTIVE, privacyOutcome.reason)

        // Synthetic gesture dispatch suppresses recognition during active window
        guard.acquireDispatchWindow(durationMillis = 200L)
        now = 1_250L
        val loopBlocked = engine.recognize(swipe, library)
        assertIs<RecognitionOutcome.Rejected>(loopBlocked)
        assertEquals(RejectionReason.SYNTHETIC_LOOP_BLOCKED, loopBlocked.reason)

        // After dispatch + safety margin expires, genuine user swipe succeeds immediately
        now = 1_350L
        val resumed = engine.recognize(swipe, library)
        assertIs<RecognitionOutcome.Matched>(resumed)
        assertEquals("swipe_right", resumed.winner.templateId)
    }

    @Test
    fun survivesCorruptedNanInfinityEmptyAndConcurrentChaosInputsWithoutCrashing() {
        val engine = HybridGestureEngine()
        val library = buildLibrary(engine)

        val corrupted = GestureCapture(
            strokes = listOf(
                GestureStroke(
                    strokeId = 0,
                    points = listOf(
                        GesturePoint(Float.NaN, 10f, 0L),
                        GesturePoint(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -50L),
                        GesturePoint(100f, 100f, 10L, pressure = Float.NaN),
                        GesturePoint(102f, 101f, 10L, pressure = 99f),
                    ),
                ),
            ),
        )
        val outcome = engine.recognize(corrupted, library)
        assertIs<RecognitionOutcome.Rejected>(outcome)
        assertEquals(RejectionReason.EMPTY_OR_DEGENERATE_STROKE, outcome.reason)

        // 8-thread concurrent stress test + latency gate (< 15ms p99 on JVM)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 64).map { idx ->
                Callable {
                    val sample = if (idx % 2 == 0) {
                        circleGesture(400f, 600f, 200f, clockwise = true)
                    } else {
                        polylineGesture(listOf(150f to 250f, 650f to 250f, 150f to 750f, 650f to 750f))
                    }
                    engine.recognize(sample, library, fullEnsemble = idx % 3 != 0)
                }
            }
            val results = pool.invokeAll(tasks).map { it.get() }
            assertEquals(64, results.count { it is RecognitionOutcome.Matched })
        } finally {
            pool.shutdownNow()
        }
    }

    companion object {
        fun lineGesture(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int = 36): GestureCapture {
            val pts = (0..steps).map { i ->
                val t = i.toFloat() / steps
                GesturePoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, i * 8L)
            }
            return GestureCapture(listOf(GestureStroke(0, pts)))
        }

        fun polylineGesture(anchors: List<Pair<Float, Float>>, stepsPerSeg: Int = 20): GestureCapture {
            val pts = mutableListOf<GesturePoint>()
            var time = 0L
            for (s in 0 until anchors.lastIndex) {
                val (x0, y0) = anchors[s]
                val (x1, y1) = anchors[s + 1]
                val start = if (s == 0) 0 else 1
                for (i in start..stepsPerSeg) {
                    val t = i.toFloat() / stepsPerSeg
                    pts.add(GesturePoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, time))
                    time += 8L
                }
            }
            return GestureCapture(listOf(GestureStroke(0, pts)))
        }

        fun circleGesture(cx: Float, cy: Float, radius: Float, clockwise: Boolean, steps: Int = 48): GestureCapture {
            val dir = if (clockwise) 1f else -1f
            val pts = (0..steps).map { i ->
                val theta = dir * (2.0 * PI * i / steps).toFloat()
                GesturePoint(cx + radius * cos(theta), cy + radius * sin(theta), i * 8L)
            }
            return GestureCapture(listOf(GestureStroke(0, pts)))
        }

        fun multiStrokeX(
            cx: Float,
            cy: Float,
            halfSize: Float,
            reverseOrder: Boolean = false,
        ): GestureCapture {
            val s1 = (0..20).map { i ->
                val t = i / 20f
                val sid = if (reverseOrder) 1 else 0
                GesturePoint(
                    x = (cx - halfSize) + 2f * halfSize * t,
                    y = (cy - halfSize) + 2f * halfSize * t,
                    timestampMillis = i * 8L,
                    strokeId = sid,
                )
            }
            val s2 = (0..20).map { i ->
                val t = i / 20f
                val sid = if (reverseOrder) 0 else 1
                GesturePoint(
                    x = (cx + halfSize) - 2f * halfSize * t,
                    y = (cy - halfSize) + 2f * halfSize * t,
                    timestampMillis = 220L + i * 8L,
                    strokeId = sid,
                )
            }
            val strokes = if (reverseOrder) {
                listOf(GestureStroke(0, s2), GestureStroke(1, s1))
            } else {
                listOf(GestureStroke(0, s1), GestureStroke(1, s2))
            }
            return GestureCapture(strokes)
        }

        fun addJitter(capture: GestureCapture, rng: Random, amplitudePx: Float): GestureCapture =
            capture.copy(
                strokes = capture.strokes.map { stroke ->
                    stroke.copy(
                        points = stroke.points.map { p ->
                            p.copy(
                                x = p.x + (rng.nextFloat() * 2f - 1f) * amplitudePx,
                                y = p.y + (rng.nextFloat() * 2f - 1f) * amplitudePx,
                            )
                        },
                    )
                },
            )
    }
}
