package com.vodiwalker.gesture.engine.gesture

import com.vodiwalker.gesture.core.model.GestureFeatureVector
import com.vodiwalker.gesture.core.model.GesturePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Stroke-aware spatial normalization & Rubine 14-dimensional kinematic feature extraction.
 *
 * Preserves aspect ratio for nearly linear (1D) gestures (ratio < [LINE_ASPECT_THRESHOLD]) so
 * horizontal swipes, vertical swipes, and diagonal slashes never distort into isotropic squares.
 */
object GestureGeometry {
    const val DEFAULT_RESAMPLE_COUNT: Int = 64
    const val MIN_VALID_PATH_LENGTH_PX: Float = 24f
    private const val LINE_ASPECT_THRESHOLD: Float = 0.28f
    private const val EPSILON: Float = 1e-6f

    /** Euclidean distance between two points. */
    fun distance(a: GesturePoint, b: GesturePoint): Float = hypot(b.x - a.x, b.y - a.y)

    /** Total intra-stroke arc length, ignoring inter-stroke pen-up jumps. */
    fun pathLength(points: List<GesturePoint>): Float {
        if (points.size < 2) return 0f
        var sum = 0f
        for (i in 1 until points.size) {
            if (points[i].strokeId == points[i - 1].strokeId) {
                sum += distance(points[i - 1], points[i])
            }
        }
        return sum
    }

    /** Arithmetic centroid of [points]. */
    fun centroid(points: List<GesturePoint>): Pair<Float, Float> {
        if (points.isEmpty()) return 0f to 0f
        var sx = 0f
        var sy = 0f
        for (p in points) {
            sx += p.x
            sy += p.y
        }
        val inv = 1f / points.size
        return (sx * inv) to (sy * inv)
    }

    /**
     * Resamples single or multi-stroke trajectories to exactly [targetCount] equidistant points
     * while preserving stroke boundaries (`strokeId`).
     */
    fun resample(points: List<GesturePoint>, targetCount: Int = DEFAULT_RESAMPLE_COUNT): List<GesturePoint> {
        require(targetCount >= 8) { "targetCount must be >= 8" }
        if (points.isEmpty()) return emptyList()
        val totalLen = pathLength(points)
        if (totalLen <= EPSILON) {
            return List(targetCount) { points.first() }
        }

        val interval = totalLen / (targetCount - 1).toFloat()
        val working = ArrayList<GesturePoint>(points.size + targetCount).apply { addAll(points) }
        val resampled = ArrayList<GesturePoint>(targetCount)
        resampled.add(working.first())

        var accumulated = 0f
        var i = 1
        while (i < working.size && resampled.size < targetCount) {
            val prev = working[i - 1]
            val curr = working[i]
            if (curr.strokeId == prev.strokeId) {
                val segLen = distance(prev, curr)
                if (segLen > EPSILON && accumulated + segLen >= interval) {
                    val t = ((interval - accumulated) / segLen).coerceIn(0f, 1f)
                    val interpolated = GesturePoint(
                        x = prev.x + t * (curr.x - prev.x),
                        y = prev.y + t * (curr.y - prev.y),
                        timestampMillis = prev.timestampMillis +
                            ((curr.timestampMillis - prev.timestampMillis) * t).toLong(),
                        pressure = prev.pressure + t * (curr.pressure - prev.pressure),
                        strokeId = curr.strokeId,
                    )
                    resampled.add(interpolated)
                    working.add(i, interpolated)
                    accumulated = 0f
                } else {
                    accumulated += segLen
                }
            } else {
                accumulated = 0f
            }
            i++
        }

        while (resampled.size < targetCount) {
            resampled.add(working.last())
        }
        return if (resampled.size > targetCount) resampled.subList(0, targetCount) else resampled
    }

    /**
     * Scales points into unit space `[-0.5, +0.5]` and translates their centroid to `(0, 0)`.
     * Applies uniform scaling for 1D strokes and bounded aspect normalization for 2D shapes.
     */
    fun normalizeToUnitSpace(
        points: List<GesturePoint>,
        targetCount: Int = DEFAULT_RESAMPLE_COUNT,
        rotateToZero: Boolean = false,
    ): List<GesturePoint> {
        val resampled = resample(points, targetCount)
        if (resampled.isEmpty()) return emptyList()

        val oriented = if (rotateToZero) {
            val (cx, cy) = centroid(resampled)
            val indicativeAngle = atan2(cy - resampled.first().y, cx - resampled.first().x)
            rotateBy(resampled, -indicativeAngle, cx, cy)
        } else {
            resampled
        }

        val bounds = boundingBox(oriented)
        val width = bounds.width
        val height = bounds.height
        val maxDim = max(width, height).coerceAtLeast(EPSILON)
        val minDim = min(width, height)
        val isLinear = (minDim / maxDim) < LINE_ASPECT_THRESHOLD

        val scaled = oriented.map { pt ->
            val sx = if (isLinear) pt.x / maxDim else pt.x / width.coerceAtLeast(EPSILON)
            val sy = if (isLinear) pt.y / maxDim else pt.y / height.coerceAtLeast(EPSILON)
            pt.copy(x = sx, y = sy)
        }

        val (cx, cy) = centroid(scaled)
        return scaled.map { pt -> pt.copy(x = pt.x - cx, y = pt.y - cy) }
    }

    /** Rotates all points by [radians] around `(pivotX, pivotY)`. */
    fun rotateBy(
        points: List<GesturePoint>,
        radians: Float,
        pivotX: Float,
        pivotY: Float,
    ): List<GesturePoint> {
        val c = cos(radians)
        val s = sin(radians)
        return points.map { p ->
            val dx = p.x - pivotX
            val dy = p.y - pivotY
            p.copy(
                x = dx * c - dy * s + pivotX,
                y = dx * s + dy * c + pivotY,
            )
        }
    }

    /** Computes intra-stroke turning angles in `[0, 1]` as required by Vatavu's $P+ recognizer. */
    fun computeNormalizedTurningAngles(points: List<GesturePoint>): FloatArray {
        val n = points.size
        val angles = FloatArray(n) { 0.5f }
        if (n < 3) return angles
        for (i in 1 until n - 1) {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            if (a.strokeId == b.strokeId && b.strokeId == c.strokeId) {
                val dx1 = b.x - a.x
                val dy1 = b.y - a.y
                val dx2 = c.x - b.x
                val dy2 = c.y - b.y
                val denom = (hypot(dx1, dy1) * hypot(dx2, dy2)).coerceAtLeast(EPSILON)
                val cosTheta = ((dx1 * dx2 + dy1 * dy2) / denom).coerceIn(-1f, 1f)
                angles[i] = (kotlin.math.acos(cosTheta) / PI.toFloat()).coerceIn(0f, 1f)
            }
        }
        return angles
    }

    /** Extracts a 14+2 dimensional Rubine geometric and kinematic descriptor from filtered points. */
    fun extractFeatures(points: List<GesturePoint>): GestureFeatureVector {
        if (points.size < 2) {
            return GestureFeatureVector(
                boundingDiagonal = 0f,
                boundingAngleRad = 0f,
                aspectRatio = 1f,
                pathLength = 0f,
                startEndDistance = 0f,
                startEndCos = 1f,
                startEndSin = 0f,
                initialCos = 1f,
                initialSin = 0f,
                totalTurningAngleRad = 0f,
                absoluteTurningAngleRad = 0f,
                sharpnessSquaredRad = 0f,
                peakVelocityPxPerMs = 0f,
                meanPressure = points.firstOrNull()?.pressure ?: 1f,
                inflectionCount = 0,
                strokeCount = if (points.isEmpty()) 0 else 1,
            )
        }

        val bounds = boundingBox(points)
        val diag = hypot(bounds.width, bounds.height)
        val boxAngle = atan2(bounds.height, bounds.width.coerceAtLeast(EPSILON))
        val aspect = bounds.width / bounds.height.coerceAtLeast(EPSILON)
        val totalPath = pathLength(points)

        val first = points.first()
        val last = points.last()
        val seDist = distance(first, last)
        val seDenom = seDist.coerceAtLeast(EPSILON)
        val seCos = (last.x - first.x) / seDenom
        val seSin = (last.y - first.y) / seDenom

        val initAnchor = points[min(4, points.lastIndex)]
        val initDist = distance(first, initAnchor).coerceAtLeast(EPSILON)
        val initCos = (initAnchor.x - first.x) / initDist
        val initSin = (initAnchor.y - first.y) / initDist

        var totalTurn = 0f
        var absTurn = 0f
        var sqTurn = 0f
        var inflections = 0
        var prevSign = 0
        var peakVel = 0f
        var pressureSum = first.pressure
        val strokeIds = HashSet<Int>().apply { add(first.strokeId) }

        for (i in 1 until points.size) {
            val p = points[i]
            val prev = points[i - 1]
            pressureSum += p.pressure
            strokeIds.add(p.strokeId)
            if (p.strokeId == prev.strokeId) {
                val dt = (p.timestampMillis - prev.timestampMillis).coerceAtLeast(1L)
                val v = distance(prev, p) / dt.toFloat()
                if (v > peakVel) peakVel = v
            }
            if (i < points.lastIndex) {
                val next = points[i + 1]
                if (prev.strokeId == p.strokeId && p.strokeId == next.strokeId) {
                    val dx1 = p.x - prev.x
                    val dy1 = p.y - prev.y
                    val dx2 = next.x - p.x
                    val dy2 = next.y - p.y
                    if (hypot(dx1, dy1) > EPSILON && hypot(dx2, dy2) > EPSILON) {
                        val cross = dx1 * dy2 - dy1 * dx2
                        val dot = dx1 * dx2 + dy1 * dy2
                        val theta = atan2(cross, dot)
                        totalTurn += theta
                        absTurn += abs(theta)
                        sqTurn += theta * theta
                        if (abs(theta) > 0.18f) {
                            val sign = if (theta > 0f) 1 else -1
                            if (prevSign != 0 && sign != prevSign) inflections++
                            prevSign = sign
                        }
                    }
                }
            }
        }

        return GestureFeatureVector(
            boundingDiagonal = diag,
            boundingAngleRad = boxAngle,
            aspectRatio = aspect,
            pathLength = totalPath,
            startEndDistance = seDist,
            startEndCos = seCos,
            startEndSin = seSin,
            initialCos = initCos,
            initialSin = initSin,
            totalTurningAngleRad = totalTurn,
            absoluteTurningAngleRad = absTurn,
            sharpnessSquaredRad = sqTurn,
            peakVelocityPxPerMs = peakVel,
            meanPressure = pressureSum / points.size,
            inflectionCount = inflections,
            strokeCount = strokeIds.size,
        )
    }

    /**
     * Axis-aligned bounding box of a point cloud.
     */
    data class Bounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
        val width: Float get() = (maxX - minX).coerceAtLeast(0f)
        val height: Float get() = (maxY - minY).coerceAtLeast(0f)
    }

    /**
     * Computes the axis-aligned bounding box, or a zero box for an empty list.
     */
    fun boundingBox(points: List<GesturePoint>): Bounds {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return Bounds(minX, maxX, minY, maxY)
    }
}
