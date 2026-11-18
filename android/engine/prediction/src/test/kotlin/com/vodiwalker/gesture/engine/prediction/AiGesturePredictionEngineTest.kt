package com.vodiwalker.gesture.engine.prediction

import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.model.AppCategory
import com.vodiwalker.gesture.core.model.GestureCapture
import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureStroke
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.PredictionContext
import com.vodiwalker.gesture.engine.gesture.HybridGestureEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiGesturePredictionEngineTest {

    private val gestureEngine = HybridGestureEngine()

    private fun template(id: String, name: String, anchors: List<Pair<Float, Float>>): GestureTemplate {
        val pts = mutableListOf<GesturePoint>()
        var t = 0L
        for (s in 0 until anchors.lastIndex) {
            val (x0, y0) = anchors[s]
            val (x1, y1) = anchors[s + 1]
            for (i in 0..20) {
                val f = i / 20f
                pts.add(GesturePoint(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, t))
                t += 8L
            }
        }
        val res = gestureEngine.createCalibratedTemplate(
            id = id,
            name = name,
            category = GestureCategory.CUSTOM,
            calibrationCaptures = listOf(GestureCapture(listOf(GestureStroke(0, pts)))),
        )
        return assertIs<AppResult.Ok<GestureTemplate>>(res).value
    }

    @Test
    fun onlineNeuralAndMarkovPersonalizationBoostsContextualAppGesture() {
        val predictor = AiGesturePredictionEngine()
        val templates = listOf(
            template("next_track", "Next Track", listOf(100f to 400f, 650f to 400f)),
            template("screenshot_z", "Screenshot Z", listOf(100f to 200f, 600f to 200f, 100f to 700f, 600f to 700f)),
            template("split_v", "Split Screen V", listOf(100f to 250f, 350f to 750f, 600f to 250f)),
        )

        val musicContext = PredictionContext(
            foregroundPackage = "com.spotify.music",
            appCategory = AppCategory.MUSIC_AUDIO,
            hourOfDay = 19,
            dayOfWeek = 5,
            isLandscape = false,
            isHeadsetConnected = true,
            batteryPercent = 82,
            previousGestureId = "split_v",
            millisSincePreviousGesture = 4_000L,
        )

        // Train online on 28 music-session confirmations of "next_track"
        repeat(28) {
            predictor.learnFromExecution(musicContext, "next_track", templates)
        }

        // Pre-stroke prediction in MUSIC_AUDIO context ranks "next_track" #1 with high confidence
        val ranked = predictor.predict(emptyList(), templates, musicContext, topK = 3)
        assertEquals(3, ranked.size)
        assertEquals("next_track", ranked.first().templateId)
        assertTrue(ranked.first().contextualPrior > 0.60f, "Expected learned prior > 0.60, got ${ranked.first()}")
        assertTrue(ranked.first().markovTransitionProb > 0.85f)

        // Snapshot weights remain completely finite (no exploding gradients)
        val snapshot = predictor.exportModelSnapshot()
        assertEquals(28L, snapshot.totalUpdates)
        assertTrue(snapshot.w1.all { it.isFinite() } && snapshot.w2.all { it.isFinite() })
    }

    @Test
    fun predictsPartialMidStrokeAndProjectsGhostCompletionTrailAndRespectsPrivacy() {
        val predictor = AiGesturePredictionEngine()
        val zTemplate = template(
            "screenshot_z",
            "Screenshot Z",
            listOf(100f to 200f, 600f to 200f, 100f to 700f, 600f to 700f),
        )
        val vTemplate = template("split_v", "Split Screen V", listOf(100f to 250f, 350f to 750f, 600f to 250f))
        val templates = listOf(zTemplate, vTemplate)

        // Draw first 62% of "Z" (top bar + diagonal)
        val partialZ = buildList {
            for (i in 0..15) add(GesturePoint(100f + i * 33f, 200f, i * 8L))
            for (i in 1..14) add(GesturePoint(600f - i * 32f, 200f + i * 32f, (15 + i) * 8L))
        }
        val browserCtx = PredictionContext(
            foregroundPackage = "org.mozilla.firefox",
            appCategory = AppCategory.BROWSER,
            hourOfDay = 14,
            dayOfWeek = 3,
            isLandscape = false,
            isHeadsetConnected = false,
            batteryPercent = 70,
            previousGestureId = null,
            millisSincePreviousGesture = 0L,
        )

        val preds = predictor.predict(partialZ, templates, browserCtx, topK = 2)
        assertEquals("screenshot_z", preds.first().templateId)
        assertTrue(preds.first().ghostCompletionTrail.isNotEmpty(), "Expected projected ghost trail")

        // Sensitive protected context refuses to predict or learn
        val bankingCtx = browserCtx.copy(appCategory = AppCategory.SENSITIVE_PROTECTED)
        assertTrue(predictor.predict(partialZ, templates, bankingCtx).isEmpty())
        predictor.learnFromExecution(bankingCtx, "screenshot_z", templates)
        assertEquals(0L, predictor.exportModelSnapshot().totalUpdates)
    }
}
