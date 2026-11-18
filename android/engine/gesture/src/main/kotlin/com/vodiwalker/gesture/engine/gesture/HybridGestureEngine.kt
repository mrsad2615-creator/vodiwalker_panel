package com.vodiwalker.gesture.engine.gesture

import com.vodiwalker.gesture.core.common.AppResult
import com.vodiwalker.gesture.core.common.GestureError
import com.vodiwalker.gesture.core.common.MonotonicClock
import com.vodiwalker.gesture.core.common.SyntheticGestureGuard
import com.vodiwalker.gesture.core.model.AlgorithmScoreBreakdown
import com.vodiwalker.gesture.core.model.GestureCapture
import com.vodiwalker.gesture.core.model.GestureCategory
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureTemplate
import com.vodiwalker.gesture.core.model.RecognitionCandidate
import com.vodiwalker.gesture.core.model.RecognitionOutcome
import com.vodiwalker.gesture.core.model.RejectionReason

/**
 * Primary hybrid gesture recognition & custom calibration engine.
 *
 * Combines four orthogonal algorithms:
 * 1. [ProtractorRecognizer] — closed-form angular alignment (single-stroke precision)
 * 2. [PointCloudRecognizer] — $P+ curvature-weighted cloud (multi-stroke invariance)
 * 3. [BandedDtwRecognizer] — Sakoe-Chiba DTW (temporal-elastic kinematic alignment)
 * 4. [DirectionalGuard] — Rubine orientation, closure, and winding discriminator
 *
 * Supports a battery-saving fast-path (`fullEnsemble = false`) orchestrated by `:engine:power`.
 */
class HybridGestureEngine(
    private val syntheticGuard: SyntheticGestureGuard = SyntheticGestureGuard(),
    private val clock: MonotonicClock = MonotonicClock.SYSTEM,
    private val minDisambiguationMargin: Float = 0.045f,
    private val collisionSimilarityThreshold: Float = 0.89f,
) {
    private val filter = AdaptiveOneEuroFilter()

    /**
     * Recognizes [capture] against [templates], enforcing anti-recursion and privacy guards.
     */
    fun recognize(
        capture: GestureCapture,
        templates: List<GestureTemplate>,
        fullEnsemble: Boolean = true,
        isPrivacySensitiveWindow: Boolean = false,
    ): RecognitionOutcome {
        val startNanos = clock.nowNanos()

        if (isPrivacySensitiveWindow) {
            return RecognitionOutcome.Rejected(
                reason = RejectionReason.PRIVACY_SHIELD_ACTIVE,
                closestCandidate = null,
                totalLatencyMicros = elapsedMicros(startNanos),
            )
        }

        if (syntheticGuard.isSyntheticWindowActive()) {
            return RecognitionOutcome.Rejected(
                reason = RejectionReason.SYNTHETIC_LOOP_BLOCKED,
                closestCandidate = null,
                totalLatencyMicros = elapsedMicros(startNanos),
            )
        }

        val enabledTemplates = templates.filter { it.isEnabled && it.normalizedPoints.size >= 8 }
        if (enabledTemplates.isEmpty()) {
            return RecognitionOutcome.Rejected(
                reason = RejectionReason.NO_ENABLED_TEMPLATES,
                closestCandidate = null,
                totalLatencyMicros = elapsedMicros(startNanos),
            )
        }

        val rawPoints = capture.flattenPoints()
        val smoothed = synchronized(filter) { filter.filterSequence(rawPoints) }
        val features = GestureGeometry.extractFeatures(smoothed)
        if (smoothed.size < 3 || features.pathLength < GestureGeometry.MIN_VALID_PATH_LENGTH_PX) {
            return RecognitionOutcome.Rejected(
                reason = RejectionReason.EMPTY_OR_DEGENERATE_STROKE,
                closestCandidate = null,
                totalLatencyMicros = elapsedMicros(startNanos),
            )
        }

        val normalizedFixed = GestureGeometry.normalizeToUnitSpace(smoothed, rotateToZero = false)
        val normalizedRotInv = GestureGeometry.normalizeToUnitSpace(smoothed, rotateToZero = true)
        val isMultiStroke = features.strokeCount > 1

        val ranked = enabledTemplates.map { template ->
            val tStart = clock.nowNanos()
            val candidatePts = if (template.orientationSensitive) normalizedFixed else normalizedRotInv
            val breakdown = evaluateTemplate(
                candidatePts = candidatePts,
                candidateFeatures = features,
                template = template,
                isMultiStroke = isMultiStroke,
                fullEnsemble = fullEnsemble,
            )
            val fused = fuseScores(breakdown, isMultiStroke, template.orientationSensitive, fullEnsemble)
            RecognitionCandidate(
                templateId = template.id,
                templateName = template.name,
                confidence = fused,
                breakdown = breakdown,
                latencyMicros = elapsedMicros(tStart),
            )
        }.sortedByDescending { it.confidence }

        val best = ranked.first()
        val bestTemplate = enabledTemplates.first { it.id == best.templateId }
        val totalMicros = elapsedMicros(startNanos)

        if (best.confidence < bestTemplate.minConfidence) {
            return RecognitionOutcome.Rejected(
                reason = RejectionReason.BELOW_CONFIDENCE_THRESHOLD,
                closestCandidate = best,
                totalLatencyMicros = totalMicros,
            )
        }

        val runnerUp = ranked.getOrNull(1)
        val margin = if (runnerUp != null) best.confidence - runnerUp.confidence else 1f
        if (runnerUp != null && margin < minDisambiguationMargin && best.confidence < 0.93f) {
            return RecognitionOutcome.Rejected(
                reason = RejectionReason.AMBIGUOUS_MARGIN,
                closestCandidate = best,
                totalLatencyMicros = totalMicros,
            )
        }

        return RecognitionOutcome.Matched(
            winner = best,
            alternatives = ranked.drop(1).take(3),
            marginOverRunnerUp = margin,
            totalLatencyMicros = totalMicros,
        )
    }

    /**
     * Synthesizes a calibrated [GestureTemplate] from 1–5 recorded user captures via pointwise
     * barycenter averaging, and rejects templates that collide with existing gestures.
     */
    fun createCalibratedTemplate(
        id: String,
        name: String,
        category: GestureCategory,
        calibrationCaptures: List<GestureCapture>,
        existingTemplates: List<GestureTemplate> = emptyList(),
        orientationSensitive: Boolean = true,
        minConfidence: Float = 0.72f,
        createdAtMillis: Long = 0L,
    ): AppResult<GestureTemplate, GestureError> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return AppResult.Err(GestureError.InvalidInput("Template name must not be blank."))
        }
        if (calibrationCaptures.isEmpty()) {
            return AppResult.Err(GestureError.InvalidInput("At least one calibration stroke is required."))
        }

        val smoothedSets = calibrationCaptures.map { cap ->
            synchronized(filter) { filter.filterSequence(cap.flattenPoints()) }
        }.filter { pts ->
            pts.size >= 4 && GestureGeometry.pathLength(pts) >= GestureGeometry.MIN_VALID_PATH_LENGTH_PX
        }

        if (smoothedSets.isEmpty()) {
            return AppResult.Err(
                GestureError.InvalidInput("Recorded stroke is too short or contains insufficient samples."),
            )
        }

        val normalizedSets = smoothedSets.map { pts ->
            GestureGeometry.normalizeToUnitSpace(pts, rotateToZero = !orientationSensitive)
        }
        val pointCount = normalizedSets.first().size
        val averagedPoints = List(pointCount) { idx ->
            var sx = 0f
            var sy = 0f
            var sp = 0f
            for (set in normalizedSets) {
                sx += set[idx].x
                sy += set[idx].y
                sp += set[idx].pressure
            }
            val inv = 1f / normalizedSets.size
            GesturePoint(
                x = sx * inv,
                y = sy * inv,
                timestampMillis = idx * 8L,
                pressure = sp * inv,
                strokeId = normalizedSets.first()[idx].strokeId,
            )
        }

        val repFeatures = GestureGeometry.extractFeatures(smoothedSets.first())
        val isMultiStroke = repFeatures.strokeCount > 1

        // Collision gate: prevent users from accidentally saving two indistinguishable gestures
        for (existing in existingTemplates) {
            if (existing.id == id || !existing.isEnabled) continue
            if (existing.orientationSensitive != orientationSensitive) continue
            val breakdown = evaluateTemplate(
                candidatePts = averagedPoints,
                candidateFeatures = repFeatures,
                template = existing,
                isMultiStroke = isMultiStroke,
                fullEnsemble = true,
            )
            val similarity = fuseScores(breakdown, isMultiStroke, orientationSensitive, fullEnsemble = true)
            if (similarity >= collisionSimilarityThreshold) {
                return AppResult.Err(
                    GestureError.TemplateCollision(
                        conflictingTemplateId = existing.id,
                        similarity = similarity,
                    ),
                )
            }
        }

        return AppResult.Ok(
            GestureTemplate(
                id = id,
                name = trimmedName,
                category = category,
                normalizedPoints = averagedPoints,
                featureVector = repFeatures,
                orientationSensitive = orientationSensitive,
                minConfidence = minConfidence.coerceIn(0.50f, 0.95f),
                sampleCount = smoothedSets.size,
                createdAtMillis = createdAtMillis,
            ),
        )
    }

    private fun evaluateTemplate(
        candidatePts: List<GesturePoint>,
        candidateFeatures: com.vodiwalker.gesture.core.model.GestureFeatureVector,
        template: GestureTemplate,
        isMultiStroke: Boolean,
        fullEnsemble: Boolean,
    ): AlgorithmScoreBreakdown {
        val protractor = ProtractorRecognizer.score(
            candidate = candidatePts,
            template = template.normalizedPoints,
            orientationSensitive = template.orientationSensitive,
        )
        val directional = DirectionalGuard.score(
            candidate = candidateFeatures,
            template = template.featureVector,
            orientationSensitive = template.orientationSensitive,
        )

        // ECO fast-path skips secondary cloud & DTW passes unless stroke is multi-stroke or borderline
        if (!fullEnsemble && !isMultiStroke && (protractor < 0.45f || protractor > 0.92f)) {
            return AlgorithmScoreBreakdown(
                protractorScore = protractor,
                pointCloudScore = protractor,
                bandedDtwScore = protractor,
                directionalScore = directional,
            )
        }

        val cloud = PointCloudRecognizer.score(candidatePts, template.normalizedPoints)
        val dtw = BandedDtwRecognizer.score(candidatePts, template.normalizedPoints)
        return AlgorithmScoreBreakdown(
            protractorScore = protractor,
            pointCloudScore = cloud,
            bandedDtwScore = dtw,
            directionalScore = directional,
        )
    }

    private fun fuseScores(
        b: AlgorithmScoreBreakdown,
        isMultiStroke: Boolean,
        orientationSensitive: Boolean,
        fullEnsemble: Boolean,
    ): Float {
        val shapeScore = when {
            !fullEnsemble && !isMultiStroke -> 0.70f * b.protractorScore + 0.30f * b.directionalScore
            isMultiStroke -> 0.52f * b.pointCloudScore + 0.25f * b.bandedDtwScore +
                0.13f * b.protractorScore + 0.10f * b.directionalScore
            else -> 0.36f * b.protractorScore + 0.34f * b.bandedDtwScore +
                0.18f * b.pointCloudScore + 0.12f * b.directionalScore
        }
        // Apply multiplicative penalty when orientation diverges sharply on directional templates
        val orientPenalty = if (orientationSensitive && b.directionalScore < 0.45f) {
            (b.directionalScore / 0.45f).coerceIn(0.15f, 1f)
        } else {
            1f
        }
        return (shapeScore * orientPenalty).coerceIn(0f, 1f)
    }

    private fun elapsedMicros(startNanos: Long): Long =
        ((clock.nowNanos() - startNanos) / 1_000L).coerceAtLeast(0L)
}
