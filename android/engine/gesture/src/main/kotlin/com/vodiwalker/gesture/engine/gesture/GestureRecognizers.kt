package com.vodiwalker.gesture.engine.gesture

import com.vodiwalker.gesture.core.model.GestureFeatureVector
import com.vodiwalker.gesture.core.model.GesturePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Recognizer #1: Protractor (Li, CHI 2010) — closed-form O(N) inverse-cosine angular matcher.
 * Supports both orientation-sensitive (bounded `±22.5°` rotation) and orientation-invariant modes.
 */
object ProtractorRecognizer {
    private const val MAX_ORIENTATION_SENSITIVE_RAD: Float = (PI / 8.0).toFloat()
    private const val EPSILON: Float = 1e-7f

    /**
     * Angular similarity in `[0,1]` between two resampled stroke clouds.
     */
    fun score(
        candidate: List<GesturePoint>,
        template: List<GesturePoint>,
        orientationSensitive: Boolean,
    ): Float {
        val n = min(candidate.size, template.size)
        if (n < 4) return 0f

        var a = 0f
        var b = 0f
        var normCand = 0f
        var normTemp = 0f
        for (i in 0 until n) {
            val cx = candidate[i].x
            val cy = candidate[i].y
            val tx = template[i].x
            val ty = template[i].y
            a += cx * tx + cy * ty
            b += cx * ty - cy * tx
            normCand += cx * cx + cy * cy
            normTemp += tx * tx + ty * ty
        }

        val denom = sqrt((normCand * normTemp).coerceAtLeast(EPSILON))
        val rawOptimalAngle = atan2(b, a)
        val appliedAngle = if (orientationSensitive) {
            rawOptimalAngle.coerceIn(-MAX_ORIENTATION_SENSITIVE_RAD, MAX_ORIENTATION_SENSITIVE_RAD)
        } else {
            rawOptimalAngle
        }

        val alignedDot = a * cos(appliedAngle) + b * sin(appliedAngle)
        val cosineSim = (alignedDot / denom).coerceIn(-1f, 1f)
        val angularDist = acos(cosineSim)
        // Map angular distance [0, pi/2] smoothly onto confidence [1.0, 0.0]
        return (1f - (angularDist / (PI.toFloat() * 0.5f))).coerceIn(0f, 1f)
    }
}

/**
 * Recognizer #2: $P+ Point-Cloud Matcher (Vatavu, Anthony & Wobbrock, ICMI 2017).
 * Order- and stroke-direction-invariant matching combining Euclidean distance with turning-angle
 * curvature similarity, essential for multi-stroke glyphs (`X`, `+`, `★`, `H`, custom symbols).
 */
object PointCloudRecognizer {
    /**
     * Curvature-weighted point-cloud similarity in `[0,1]` for multi-stroke gestures.
     */
    fun score(
        candidate: List<GesturePoint>,
        template: List<GesturePoint>,
    ): Float {
        // Subsample to 32 points for deterministic <0.25ms execution on mobile cores
        val c = subsample(candidate, 32)
        val t = subsample(template, 32)
        if (c.size < 4 || t.size < 4) return 0f

        val cAngles = GestureGeometry.computeNormalizedTurningAngles(c)
        val tAngles = GestureGeometry.computeNormalizedTurningAngles(t)

        val dForward = directedCloudDistance(c, cAngles, t, tAngles)
        val dReverse = directedCloudDistance(t, tAngles, c, cAngles)
        val meanDist = 0.5f * (dForward + dReverse)

        // Exponential kernel calibrated on unit-normalized [-0.5, 0.5] clouds
        return exp(-3.4f * meanDist).coerceIn(0f, 1f)
    }

    private fun directedCloudDistance(
        src: List<GesturePoint>,
        srcAngles: FloatArray,
        dst: List<GesturePoint>,
        dstAngles: FloatArray,
    ): Float {
        var total = 0f
        for (i in src.indices) {
            val sp = src[i]
            val sa = srcAngles[i]
            var best = Float.POSITIVE_INFINITY
            for (j in dst.indices) {
                val dp = dst[j]
                val dx = sp.x - dp.x
                val dy = sp.y - dp.y
                val da = sa - dstAngles[j]
                val distSq = dx * dx + dy * dy + 0.35f * da * da
                if (distSq < best) best = distSq
            }
            total += sqrt(best)
        }
        return total / src.size.toFloat()
    }

    private fun subsample(points: List<GesturePoint>, count: Int): List<GesturePoint> {
        if (points.size <= count) return points
        val last = points.lastIndex
        return List(count) { idx ->
            val mapped = ((idx.toFloat() / (count - 1)) * last).toInt().coerceIn(0, last)
            points[mapped]
        }
    }
}

/**
 * Recognizer #3: Sakoe-Chiba Banded Dynamic Time Warping (DTW).
 * Handles non-linear execution speed variations while enforcing temporal causality and stroke order.
 */
object BandedDtwRecognizer {
    /**
     * Sakoe-Chiba banded DTW similarity in `[0,1]` capturing temporal elasticity.
     */
    fun score(
        candidate: List<GesturePoint>,
        template: List<GesturePoint>,
        bandFraction: Float = 0.18f,
    ): Float {
        val n = candidate.size
        val m = template.size
        if (n < 4 || m < 4) return 0f

        val band = max(4, (max(n, m) * bandFraction).toInt())
        var prevRow = FloatArray(m + 1) { Float.POSITIVE_INFINITY }
        var currRow = FloatArray(m + 1) { Float.POSITIVE_INFINITY }
        prevRow[0] = 0f

        for (i in 1..n) {
            currRow.fill(Float.POSITIVE_INFINITY)
            val jStart = max(1, i - band)
            val jEnd = min(m, i + band)
            val cp = candidate[i - 1]
            for (j in jStart..jEnd) {
                val tp = template[j - 1]
                val localCost = hypot(cp.x - tp.x, cp.y - tp.y)
                val bestPred = min(prevRow[j], min(currRow[j - 1], prevRow[j - 1]))
                currRow[j] = localCost + bestPred
            }
            val swap = prevRow
            prevRow = currRow
            currRow = swap
        }

        val normalizedCost = prevRow[m] / (n + m).toFloat()
        if (!normalizedCost.isFinite()) return 0f
        return exp(-4.8f * normalizedCost).coerceIn(0f, 1f)
    }
}

/**
 * Recognizer #4: Rubine Directional & Topology Guard.
 * Disambiguates mirrored/rotated pairs (e.g., Swipe Left vs Swipe Right, `V` vs `^`, CW vs CCW circle).
 */
object DirectionalGuard {
    /**
     * Directional/orientation discriminator score in `[0,1]`.
     */
    fun score(
        candidate: GestureFeatureVector,
        template: GestureFeatureVector,
        orientationSensitive: Boolean,
    ): Float {
        if (!orientationSensitive) {
            val turnRatioDiff = abs(candidate.absoluteTurningAngleRad - template.absoluteTurningAngleRad) /
                (PI.toFloat() * 2f)
            return (1f - 0.35f * turnRatioDiff).coerceIn(0.25f, 1f)
        }

        // Closed shapes (circles, loops) have small start-end displacement relative to path length
        val candClosedness = 1f - (candidate.startEndDistance / candidate.pathLength.coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        val tempClosedness = 1f - (template.startEndDistance / template.pathLength.coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        val closedAgreement = 1f - abs(candClosedness - tempClosedness)

        // Initial heading alignment [-1, 1] -> [0, 1]
        val initDot = (candidate.initialCos * template.initialCos +
            candidate.initialSin * template.initialSin).coerceIn(-1f, 1f)
        val initScore = 0.5f * (initDot + 1f)

        // Start-to-end vector alignment (weighted down when gesture is a closed loop)
        val seDot = (candidate.startEndCos * template.startEndCos +
            candidate.startEndSin * template.startEndSin).coerceIn(-1f, 1f)
        val seScore = 0.5f * (seDot + 1f)
        val openWeight = (1f - 0.5f * (candClosedness + tempClosedness)).coerceIn(0.15f, 1f)

        // Signed winding alignment for loops (distinguishes clockwise vs counter-clockwise)
        val windingDiff = abs(candidate.totalTurningAngleRad - template.totalTurningAngleRad)
        val windingScore = exp(-0.32f * windingDiff)

        val blended = openWeight * (0.55f * seScore + 0.30f * initScore + 0.15f * closedAgreement) +
            (1f - openWeight) * (0.50f * windingScore + 0.30f * closedAgreement + 0.20f * initScore)
        return blended.coerceIn(0f, 1f)
    }
}
