package com.vodiwalker.gesture.core.model

/**
 * Immutable single touch sample captured from an edge overlay or Accessibility gesture stream.
 *
 * @property x Horizontal coordinate in screen pixels (or normalized unit space after preprocessing).
 * @property y Vertical coordinate in screen pixels (or normalized unit space after preprocessing).
 * @property timestampMillis Monotonic timestamp (`SystemClock.uptimeMillis`) in milliseconds.
 * @property pressure Normalized contact pressure in `[0.0f, 1.0f]`.
 * @property strokeId Zero-based stroke index within a multi-stroke gesture.
 */
data class GesturePoint(
    val x: Float,
    val y: Float,
    val timestampMillis: Long,
    val pressure: Float = 1.0f,
    val strokeId: Int = 0,
) {
    /** Returns true when coordinates and pressure are finite and timestamp is non-negative. */
    fun isValid(): Boolean =
        x.isFinite() && y.isFinite() && pressure.isFinite() && timestampMillis >= 0L
}

/**
 * Single contiguous finger trajectory from pointer-down to pointer-up.
 */
data class GestureStroke(
    val strokeId: Int,
    val points: List<GesturePoint>,
) {
    val durationMillis: Long
        get() = if (points.size < 2) {
            0L
        } else {
            (points.last().timestampMillis - points.first().timestampMillis).coerceAtLeast(0L)
        }
}

/**
 * Complete single-stroke or multi-stroke gesture utterance captured in one recognition window.
 */
data class GestureCapture(
    val strokes: List<GestureStroke>,
    val viewportWidthPx: Int = 1080,
    val viewportHeightPx: Int = 2400,
    val foregroundPackage: String? = null,
    val foregroundActivity: String? = null,
) {
    /**
     * Concatenates every stroke's samples in capture order (multi-stroke safe).
     */
    fun flattenPoints(): List<GesturePoint> = strokes.flatMap { it.points }
}

/**
 * Rubine-extended 14-dimensional geometric and kinematic descriptor for fast pre-filtering,
 * directional disambiguation, and neural context scoring.
 */
data class GestureFeatureVector(
    val boundingDiagonal: Float,
    val boundingAngleRad: Float,
    val aspectRatio: Float,
    val pathLength: Float,
    val startEndDistance: Float,
    val startEndCos: Float,
    val startEndSin: Float,
    val initialCos: Float,
    val initialSin: Float,
    val totalTurningAngleRad: Float,
    val absoluteTurningAngleRad: Float,
    val sharpnessSquaredRad: Float,
    val peakVelocityPxPerMs: Float,
    val meanPressure: Float,
    val inflectionCount: Int,
    val strokeCount: Int,
)

/**
 * Registered user or system gesture template persisted in Room and indexed by [HybridGestureEngine].
 */
data class GestureTemplate(
    val id: String,
    val name: String,
    val category: GestureCategory,
    val normalizedPoints: List<GesturePoint>,
    val featureVector: GestureFeatureVector,
    val orientationSensitive: Boolean = true,
    val minConfidence: Float = 0.72f,
    val hapticPatternId: String = HapticPreset.CONFIRM_CRISP.id,
    val isEnabled: Boolean = true,
    val sampleCount: Int = 1,
    val usageCount: Long = 0L,
    val createdAtMillis: Long = 0L,
)

/**
 * User-facing intent taxonomy used for grouping, theming, and prediction priors.
 */
enum class GestureCategory {
    NAVIGATION,
    SYSTEM_CONTROL,
    APP_LAUNCHER,
    MEDIA,
    AUTOMATION_MACRO,
    CUSTOM,
}

/**
 * Individual recognizer breakdown attached to every [RecognitionCandidate] for observability.
 */
data class AlgorithmScoreBreakdown(
    val protractorScore: Float,
    val pointCloudScore: Float,
    val bandedDtwScore: Float,
    val directionalScore: Float,
)

/**
 * Ranked recognition output produced by the hybrid multi-algorithm gesture engine.
 */
data class RecognitionCandidate(
    val templateId: String,
    val templateName: String,
    val confidence: Float,
    val breakdown: AlgorithmScoreBreakdown,
    val latencyMicros: Long,
)

/**
 * Outcome of recognizing a completed [GestureCapture].
 */
sealed interface RecognitionOutcome {
    /**
     * A confident winner with ranked alternatives and the runner-up margin.
     */
    data class Matched(
        val winner: RecognitionCandidate,
        val alternatives: List<RecognitionCandidate>,
        val marginOverRunnerUp: Float,
        val totalLatencyMicros: Long,
    ) : RecognitionOutcome

    /**
     * Explicit rejection with a machine-readable cause for telemetry and UX feedback.
     */
    data class Rejected(
        val reason: RejectionReason,
        val closestCandidate: RecognitionCandidate?,
        val totalLatencyMicros: Long,
    ) : RecognitionOutcome
}

/**
 * Why a capture was refused — drives haptics, messaging, and metric labels.
 */
enum class RejectionReason {
    EMPTY_OR_DEGENERATE_STROKE,
    NO_ENABLED_TEMPLATES,
    BELOW_CONFIDENCE_THRESHOLD,
    AMBIGUOUS_MARGIN,
    SYNTHETIC_LOOP_BLOCKED,
    PRIVACY_SHIELD_ACTIVE,
}

/**
 * Real-time AI prediction emitted mid-stroke or pre-stroke from contextual + prefix engines.
 */
data class PredictionResult(
    val templateId: String,
    val templateName: String,
    val fusedProbability: Float,
    val prefixSimilarity: Float,
    val contextualPrior: Float,
    val markovTransitionProb: Float,
    val completionRatio: Float,
    val ghostCompletionTrail: List<GesturePoint>,
    val shouldPrewarmAction: Boolean,
)

/**
 * Environmental snapshot supplied to the on-device AI prediction model (zero PII / zero text).
 */
data class PredictionContext(
    val foregroundPackage: String?,
    val appCategory: AppCategory,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val isLandscape: Boolean,
    val isHeadsetConnected: Boolean,
    val batteryPercent: Int,
    val previousGestureId: String?,
    val millisSincePreviousGesture: Long,
)

/**
 * Coarse foreground-app classification derived from package metadata only (no content).
 */
enum class AppCategory {
    LAUNCHER,
    BROWSER,
    MEDIA_VIDEO,
    MUSIC_AUDIO,
    SOCIAL_COMMUNICATION,
    PRODUCTIVITY,
    READING,
    CAMERA_GALLERY,
    GAMING,
    SENSITIVE_PROTECTED,
    OTHER,
}
