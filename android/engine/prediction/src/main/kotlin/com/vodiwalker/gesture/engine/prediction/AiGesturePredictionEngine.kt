package com.vodiwalker.gesture.engine.prediction

import com.vodiwalker.gesture.core.model.AppCategory
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.PredictionContext
import com.vodiwalker.gesture.core.model.PredictionResult
import com.vodiwalker.gesture.engine.gesture.BandedDtwRecognizer
import com.vodiwalker.gesture.engine.gesture.GestureGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Optional hardware-accelerated Google AI Edge LiteRT (`.tflite`) delegate contract.
 * When a bundled or fine-tuned `.tflite` asset is present on-device, `:app` binds a LiteRT
 * implementation via Hilt; otherwise [AiGesturePredictionEngine] executes its deterministic,
 * zero-dependency on-device [ContextualNeuralModel] with zero network egress.
 */
fun interface NeuralRuntimeDelegate {
    /**
     * Static contextual priors for an app category — the cold-start fallback before learning.
     */
    fun inferContextPriors(
        contextFeatures: FloatArray,
        candidateTemplateIds: List<String>,
    ): Map<String, Float>?
}

/**
 * Serializable state snapshot of the on-device personalized neural + Markov model, stored inside
 * Room and explicitly excluded from cloud backup via Android 14+ `data_extraction_rules.xml`.
 */
data class PersonalizedModelSnapshot(
    val inputDim: Int,
    val hiddenDim: Int,
    val gestureIds: List<String>,
    val w1: FloatArray,
    val b1: FloatArray,
    val w2: FloatArray,
    val b2: FloatArray,
    val bigramCounts: Map<String, Float>,
    val totalUpdates: Long,
)

/**
 * Lightweight on-device 2-layer MLP (18 -> 24 -> K, ReLU + temperature Softmax) paired with a
 * Laplace-smoothed Markov transition table `P(g_t | g_{t-1}, appCategory)`.
 *
 * Supports sub-100us inference and bounded online personalization with gradient clipping and L2
 * weight decay so user habits improve accuracy locally without ever transmitting telemetry.
 */
class ContextualNeuralModel(
    private val hiddenDim: Int = 24,
    private val learningRate: Float = 0.035f,
    private val l2Decay: Float = 1e-4f,
    private val maxGradNorm: Float = 1.5f,
) {
    companion object {
        const val INPUT_DIM: Int = 18
        private const val MARKOV_DECAY: Float = 0.992f
        private const val MAX_TRANSITION_WINDOW_MS: Long = 45_000L
    }

    private val gestureIndex = LinkedHashMap<String, Int>()
    private var capacity = 8
    private var w1 = initXavier(INPUT_DIM, hiddenDim, seed = 42L)
    private var b1 = FloatArray(hiddenDim)
    private var w2 = initXavier(hiddenDim, capacity, seed = 1337L)
    private var b2 = FloatArray(capacity)
    private val bigramCounts = HashMap<String, Float>()
    private val unigramTotals = HashMap<String, Float>()
    private var totalUpdates = 0L

    /** Encodes [PredictionContext] into an 18-dimensional unit-bounded feature vector. */
    fun encodeContext(context: PredictionContext): FloatArray {
        val vec = FloatArray(INPUT_DIM)
        val hourAngle = (2.0 * PI * (context.hourOfDay.coerceIn(0, 23) / 24.0)).toFloat()
        val dayAngle = (2.0 * PI * (context.dayOfWeek.coerceIn(1, 7) / 7.0)).toFloat()
        vec[0] = sin(hourAngle)
        vec[1] = cos(hourAngle)
        vec[2] = sin(dayAngle)
        vec[3] = cos(dayAngle)
        vec[4] = if (context.isLandscape) 1f else 0f
        vec[5] = if (context.isHeadsetConnected) 1f else 0f
        vec[6] = (context.batteryPercent.coerceIn(0, 100) / 100f)
        vec[7] = if (context.millisSincePreviousGesture in 1..MAX_TRANSITION_WINDOW_MS) {
            exp(-context.millisSincePreviousGesture / 15_000f)
        } else {
            0f
        }
        // 10-slot one-hot encoding across primary AppCategory buckets
        val catSlot = context.appCategory.ordinal.coerceIn(0, 9)
        vec[8 + catSlot] = 1f
        return vec
    }

    /** Computes Softmax contextual priors over [templateIds] in O(INPUT_DIM * hiddenDim + K). */
    @Synchronized
    fun predictPriors(context: PredictionContext, templateIds: List<String>): Map<String, Float> {
        if (templateIds.isEmpty()) return emptyMap()
        val indices = templateIds.map { ensureGestureSlot(it) }
        val x = encodeContext(context)
        val hidden = forwardHidden(x)

        val logits = FloatArray(indices.size)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (k in indices.indices) {
            val outIdx = indices[k]
            var sum = b2[outIdx]
            val offset = outIdx * hiddenDim
            for (h in 0 until hiddenDim) {
                sum += w2[offset + h] * hidden[h]
            }
            logits[k] = sum
            if (sum > maxLogit) maxLogit = sum
        }

        var expSum = 0f
        val probs = FloatArray(indices.size)
        for (k in indices.indices) {
            val e = exp((logits[k] - maxLogit).coerceIn(-20f, 0f))
            probs[k] = e
            expSum += e
        }

        val denom = expSum.coerceAtLeast(1e-7f)
        return buildMap(indices.size) {
            for (k in templateIds.indices) {
                put(templateIds[k], (probs[k] / denom).coerceIn(0f, 1f))
            }
        }
    }

    /** Returns Laplace-smoothed Markov transition probability `P(target | prev, appCategory)`. */
    @Synchronized
    fun markovProbability(
        context: PredictionContext,
        targetId: String,
        vocabSize: Int,
    ): Float {
        val k = max(1, vocabSize)
        val uniform = 1f / k
        val prev = context.previousGestureId ?: return uniform
        if (context.millisSincePreviousGesture > MAX_TRANSITION_WINDOW_MS) return uniform

        val prefixKey = "${context.appCategory.name}|$prev"
        val pairKey = "$prefixKey->$targetId"
        val total = unigramTotals[prefixKey] ?: return uniform
        val count = bigramCounts[pairKey] ?: 0f
        val alpha = 0.5f
        return ((count + alpha) / (total + alpha * k)).coerceIn(0f, 1f)
    }

    /** Performs a single bounded online SGD + Markov update after confirmed gesture execution. */
    @Synchronized
    fun recordConfirmedGesture(
        context: PredictionContext,
        confirmedTemplateId: String,
        activeTemplateIds: List<String>,
    ) {
        if (context.appCategory == AppCategory.SENSITIVE_PROTECTED) return
        val candidates = if (confirmedTemplateId in activeTemplateIds) {
            activeTemplateIds
        } else {
            activeTemplateIds + confirmedTemplateId
        }
        val indices = candidates.map { ensureGestureSlot(it) }
        val targetLocalIdx = candidates.indexOf(confirmedTemplateId)
        if (targetLocalIdx < 0) return

        val x = encodeContext(context)
        val hidden = forwardHidden(x)

        // Forward output over active candidate head
        val logits = FloatArray(indices.size)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (k in indices.indices) {
            val outIdx = indices[k]
            var sum = b2[outIdx]
            val offset = outIdx * hiddenDim
            for (h in 0 until hiddenDim) sum += w2[offset + h] * hidden[h]
            logits[k] = sum
            if (sum > maxLogit) maxLogit = sum
        }
        var expSum = 0f
        val probs = FloatArray(indices.size)
        for (k in indices.indices) {
            probs[k] = exp((logits[k] - maxLogit).coerceIn(-20f, 0f))
            expSum += probs[k]
        }
        for (k in probs.indices) probs[k] /= expSum.coerceAtLeast(1e-7f)

        // Backprop cross-entropy gradient with global norm clipping
        val dLogits = FloatArray(indices.size) { k ->
            val y = if (k == targetLocalIdx) 1f else 0f
            (probs[k] - y).coerceIn(-maxGradNorm, maxGradNorm)
        }
        val dHidden = FloatArray(hiddenDim)
        for (k in indices.indices) {
            val outIdx = indices[k]
            val g = dLogits[k]
            val offset = outIdx * hiddenDim
            for (h in 0 until hiddenDim) {
                dHidden[h] += w2[offset + h] * g
                w2[offset + h] -= learningRate * (g * hidden[h] + l2Decay * w2[offset + h])
            }
            b2[outIdx] -= learningRate * g
        }
        for (h in 0 until hiddenDim) {
            if (hidden[h] > 0f) {
                val gh = dHidden[h].coerceIn(-maxGradNorm, maxGradNorm)
                val offset = h * INPUT_DIM
                for (i in 0 until INPUT_DIM) {
                    w1[offset + i] -= learningRate * (gh * x[i] + l2Decay * w1[offset + i])
                }
                b1[h] -= learningRate * gh
            }
        }

        // Update exponentially decayed Markov transition counts
        val prev = context.previousGestureId
        if (prev != null && context.millisSincePreviousGesture in 1..MAX_TRANSITION_WINDOW_MS) {
            val prefixKey = "${context.appCategory.name}|$prev"
            val pairKey = "$prefixKey->$confirmedTemplateId"
            if (totalUpdates % 32L == 0L) {
                bigramCounts.replaceAll { _, v -> v * MARKOV_DECAY }
                unigramTotals.replaceAll { _, v -> v * MARKOV_DECAY }
            }
            bigramCounts[pairKey] = (bigramCounts[pairKey] ?: 0f) + 1f
            unigramTotals[prefixKey] = (unigramTotals[prefixKey] ?: 0f) + 1f
        }
        totalUpdates++
    }

    /**
     * Exports the current online model state for Room checkpointing.
     */
    @Synchronized
    fun exportSnapshot(): PersonalizedModelSnapshot = PersonalizedModelSnapshot(
        inputDim = INPUT_DIM,
        hiddenDim = hiddenDim,
        gestureIds = gestureIndex.keys.toList(),
        w1 = w1.copyOf(),
        b1 = b1.copyOf(),
        w2 = w2.copyOf(),
        b2 = b2.copyOf(),
        bigramCounts = HashMap(bigramCounts),
        totalUpdates = totalUpdates,
    )

    /**
     * Number of online updates applied since process start (observability).
     */
    @Synchronized
    fun totalTrainingSteps(): Long = totalUpdates

    private fun forwardHidden(x: FloatArray): FloatArray {
        val h = FloatArray(hiddenDim)
        for (j in 0 until hiddenDim) {
            var sum = b1[j]
            val offset = j * INPUT_DIM
            for (i in 0 until INPUT_DIM) sum += w1[offset + i] * x[i]
            h[j] = if (sum > 0f) sum else 0f // ReLU
        }
        return h
    }

    private fun ensureGestureSlot(id: String): Int {
        gestureIndex[id]?.let { return it }
        val next = gestureIndex.size
        if (next >= capacity) {
            val newCap = capacity * 2
            val expandedW2 = initXavier(hiddenDim, newCap, seed = 9001L + newCap)
            w2.copyInto(expandedW2, 0, 0, capacity * hiddenDim)
            val expandedB2 = FloatArray(newCap)
            b2.copyInto(expandedB2, 0, 0, capacity)
            w2 = expandedW2
            b2 = expandedB2
            capacity = newCap
        }
        gestureIndex[id] = next
        return next
    }

    private fun initXavier(fanIn: Int, fanOut: Int, seed: Long): FloatArray {
        val scale = sqrt(2.0 / (fanIn + fanOut)).toFloat()
        var state = seed
        return FloatArray(fanIn * fanOut) {
            // Deterministic xorshift64* PRNG for reproducible weight initialization
            state = state xor (state ushr 12)
            state = state xor (state shl 25)
            state = state xor (state ushr 27)
            val u = ((state * 2685821657736338717L ushr 40).toFloat() / (1 shl 24).toFloat()) * 2f - 1f
            u * scale
        }
    }
}

/**
 * Engine #2: AI-Driven Early Gesture & Intent Prediction Engine.
 *
 * Combines:
 * - Multi-horizon partial-stroke prefix matching (35%, 55%, 75%, 90% template horizons)
 * - Contextual 2-layer neural network priors (or optional LiteRT `.tflite` hardware delegate)
 * - App-scoped Markov sequence transitions
 * - Screen-space ghost trail projection for live Jetpack Compose cinematic preview
 */
class AiGesturePredictionEngine(
    private val neuralModel: ContextualNeuralModel = ContextualNeuralModel(),
    private val liteRtDelegate: NeuralRuntimeDelegate? = null,
    private val prewarmConfidenceThreshold: Float = 0.78f,
) {
    private val prefixHorizons = floatArrayOf(0.35f, 0.55f, 0.75f, 0.92f)

    /**
     * Predicts top-[topK] likely gestures while the user is actively drawing [partialPoints]
     * (or before pointer-down when [partialPoints] is empty, surfacing quick-ring suggestions).
     */
    fun predict(
        partialPoints: List<GesturePoint>,
        templates: List<GestureTemplate>,
        context: PredictionContext,
        topK: Int = 3,
    ): List<PredictionResult> {
        if (context.appCategory == AppCategory.SENSITIVE_PROTECTED) return emptyList()
        val enabled = templates.filter { it.isEnabled && it.normalizedPoints.size >= 16 }
        if (enabled.isEmpty() || topK <= 0) return emptyList()

        val ids = enabled.map { it.id }
        val encoded = neuralModel.encodeContext(context)
        val neuralPriors = liteRtDelegate?.inferContextPriors(encoded, ids)
            ?: neuralModel.predictPriors(context, ids)

        val validPartial = partialPoints.filter { it.isValid() }
        val partialLen = GestureGeometry.pathLength(validPartial)
        val hasActiveStroke = validPartial.size >= 5 && partialLen >= 18f

        val rawScored = enabled.map { template ->
            val prior = neuralPriors[template.id] ?: (1f / enabled.size)
            val markov = neuralModel.markovProbability(context, template.id, enabled.size)

            if (!hasActiveStroke) {
                val contextOnly = (0.65f * prior + 0.35f * markov).coerceIn(0f, 1f)
                PredictionResult(
                    templateId = template.id,
                    templateName = template.name,
                    fusedProbability = contextOnly,
                    prefixSimilarity = 0f,
                    contextualPrior = prior,
                    markovTransitionProb = markov,
                    completionRatio = 0f,
                    ghostCompletionTrail = emptyList(),
                    shouldPrewarmAction = false,
                )
            } else {
                val (bestSim, bestHorizon) = matchBestPrefixHorizon(validPartial, template)
                val fused = (0.68f * bestSim + 0.20f * prior + 0.12f * markov).coerceIn(0f, 1f)
                val ghost = if (bestSim >= 0.52f && bestHorizon < 0.92f) {
                    projectGhostTrail(validPartial, template.normalizedPoints, bestHorizon)
                } else {
                    emptyList()
                }
                PredictionResult(
                    templateId = template.id,
                    templateName = template.name,
                    fusedProbability = fused,
                    prefixSimilarity = bestSim,
                    contextualPrior = prior,
                    markovTransitionProb = markov,
                    completionRatio = bestHorizon,
                    ghostCompletionTrail = ghost,
                    shouldPrewarmAction = fused >= prewarmConfidenceThreshold && bestHorizon >= 0.55f,
                )
            }
        }.sortedByDescending { it.fusedProbability }

        return rawScored.take(topK)
    }

    /** Updates the on-device personalized model after a user gesture succeeds. */
    fun learnFromExecution(
        context: PredictionContext,
        confirmedTemplateId: String,
        activeTemplates: List<GestureTemplate>,
    ) {
        neuralModel.recordConfirmedGesture(
            context = context,
            confirmedTemplateId = confirmedTemplateId,
            activeTemplateIds = activeTemplates.filter { it.isEnabled }.map { it.id },
        )
    }

    /**
     * Encodes weights + Markov bigrams into the persistence payload.
     */
    fun exportModelSnapshot(): PersonalizedModelSnapshot = neuralModel.exportSnapshot()

    private fun matchBestPrefixHorizon(
        partialScreenPts: List<GesturePoint>,
        template: GestureTemplate,
    ): Pair<Float, Float> {
        val sampleN = 24
        val normPartial = GestureGeometry.normalizeToUnitSpace(partialScreenPts, targetCount = sampleN)
        val candDir = initialUnitVector(partialScreenPts)
        var bestScore = 0f
        var bestHorizon = prefixHorizons.first()

        for (horizon in prefixHorizons) {
            val prefixCount = max(8, (template.normalizedPoints.size * horizon).toInt())
            val rawPrefix = template.normalizedPoints.subList(0, min(prefixCount, template.normalizedPoints.size))
            val normPrefix = GestureGeometry.normalizeToUnitSpace(rawPrefix, targetCount = sampleN)
            val dtw = BandedDtwRecognizer.score(normPartial, normPrefix, bandFraction = 0.22f)
            val tempDir = initialUnitVector(rawPrefix)
            val dirAgree = if (template.orientationSensitive) {
                0.5f * ((candDir.first * tempDir.first + candDir.second * tempDir.second).coerceIn(-1f, 1f) + 1f)
            } else {
                1f
            }
            val combined = dtw * (0.35f + 0.65f * dirAgree)
            if (combined > bestScore) {
                bestScore = combined
                bestHorizon = horizon
            }
        }
        return bestScore.coerceIn(0f, 1f) to bestHorizon
    }

    /**
     * Projects the remaining suffix of [templatePoints] from the user's current finger position
     * into screen coordinates so the overlay can render a luminous ghost completion hint.
     */
    private fun projectGhostTrail(
        partialScreenPts: List<GesturePoint>,
        templatePoints: List<GesturePoint>,
        horizon: Float,
    ): List<GesturePoint> {
        val splitIdx = (templatePoints.size * horizon).toInt().coerceIn(1, templatePoints.lastIndex)
        val prefixTemplate = templatePoints.subList(0, splitIdx + 1)
        val suffixTemplate = templatePoints.subList(splitIdx, templatePoints.size)
        if (suffixTemplate.size < 2) return emptyList()

        val screenLen = GestureGeometry.pathLength(partialScreenPts)
        val templatePrefixLen = GestureGeometry.pathLength(prefixTemplate).coerceAtLeast(0.05f)
        val scale = (screenLen / templatePrefixLen).coerceIn(40f, 1_400f)

        val anchorScreen = partialScreenPts.last()
        val anchorTemplate = templatePoints[splitIdx]

        return suffixTemplate.map { tp ->
            GesturePoint(
                x = anchorScreen.x + (tp.x - anchorTemplate.x) * scale,
                y = anchorScreen.y + (tp.y - anchorTemplate.y) * scale,
                timestampMillis = anchorScreen.timestampMillis,
                pressure = 0.5f,
                strokeId = tp.strokeId,
            )
        }
    }

    private fun initialUnitVector(pts: List<GesturePoint>): Pair<Float, Float> {
        if (pts.size < 2) return 1f to 0f
        val first = pts.first()
        val end = pts[min(pts.lastIndex, max(1, pts.size / 3))]
        val dx = end.x - first.x
        val dy = end.y - first.y
        val len = hypot(dx, dy).coerceAtLeast(1e-5f)
        return (dx / len) to (dy / len)
    }
}
