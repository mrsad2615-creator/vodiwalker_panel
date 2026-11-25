package com.neurox.domain.adaptive

import com.neurox.common.format.SampleWeight
import com.neurox.domain.model.DifficultyRecommendation
import com.neurox.domain.model.DifficultyLevel
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.scoring.Scoring

/** Adaptive tuning limits. Changing a limit is a product decision, not a dev tweak. */
data class AdaptiveSettings(
    val enabled: Boolean = true,
    val minSamplesForChange: Int = 3,
    val maxSamplesConsidered: Int = 8,
    /** Mean accuracy above this suggests the level is too easy. */
    val promoteAccuracy: Double = 0.92,
    /** Mean accuracy below this suggests the level is too hard. */
    val demoteAccuracy: Double = 0.68,
    /** A single consecutive hard failure count that forces a step down. */
    val demoteAfterConsecutiveFailures: Int = 2,
    val explanationKeyPromote: String = "adaptive.promote.accuracy_high",
    val explanationKeyDemote: String = "adaptive.demote.accuracy_low",
    val explanationKeySpeedyDemote: String = "adaptive.demote.speed",
    val explanationKeyHold: String = "adaptive.hold.stable",
    val explanationKeyInsufficient: String = "adaptive.hold.insufficient_data",
    val explanationKeyDisabled: String = "adaptive.hold.disabled",
) {
    init {
        require(minSamplesForChange >= 1)
        require(maxSamplesConsidered >= minSamplesForChange)
        require(promoteAccuracy > demoteAccuracy)
        require(demoteAfterConsecutiveFailures >= 1)
    }
}

/** Result of one adaptation decision, including the evidence it used. */
data class AdaptationDecision(
    val exerciseId: String,
    val module: Module,
    val from: Int,
    val to: Int,
    val explanationKey: String,
    val meanAccuracy: Double,
    val meanSpeedFactor: Double,
    val samples: Int,
    val confidence: Double,
) {
    val changed: Boolean get() = from != to
}

/**
 * Offline, deterministic adaptive engine. No language model, no randomness.
 *
 * The engine looks at the most recent completed sessions of ONE exercise and
 * changes difficulty by at most one step per decision. It requires several
 * samples, so a single bad day never changes the plan.
 */
class AdaptiveEngine(private val settings: AdaptiveSettings = AdaptiveSettings()) {

    /** History must be oldest-first. Non-comparable records are ignored. */
    fun decideNext(
        exerciseId: String,
        module: Module,
        currentDifficulty: Int,
        history: List<SessionRecord>,
        consecutiveFailures: Int = 0,
    ): AdaptationDecision {
        val usable = history
            .asSequence()
            .filter { it.exerciseId == exerciseId && it.kind != com.neurox.domain.model.SessionKind.BASELINE }
            .filter { it.status == SessionStatus.COMPLETED }
            .toList()

        if (!settings.enabled) {
            return decision(exerciseId, module, currentDifficulty, currentDifficulty, settings.explanationKeyDisabled, usable, 0.0, 0)
        }
        if (usable.size < settings.minSamplesForChange) {
            return decision(exerciseId, module, currentDifficulty, currentDifficulty, settings.explanationKeyInsufficient, usable, 0.0, usable.size)
        }

        val window = usable.takeLast(settings.maxSamplesConsidered)
        val meanAccuracy = window.map { it.accuracy }.average()
        val meanSpeed = window.map { speedOf(it) }.average()
        val weight = SampleWeight.of(window.size)
        val atCeiling = currentDifficulty >= DifficultyLevel.MAX
        val atFloor = currentDifficulty <= DifficultyLevel.MIN

        val promote = meanAccuracy >= settings.promoteAccuracy && !atCeiling
        val demote = (meanAccuracy <= settings.demoteAccuracy || consecutiveFailures >= settings.demoteAfterConsecutiveFailures) && !atFloor

        val target = when {
            promote -> currentDifficulty + 1
            demote -> currentDifficulty - 1
            else -> currentDifficulty
        }
        val key = when {
            promote -> settings.explanationKeyPromote
            demote && consecutiveFailures >= settings.demoteAfterConsecutiveFailures ->
                settings.explanationKeyDemote
            demote -> settings.explanationKeyDemote
            else -> settings.explanationKeyHold
        }
        val confidence = if (target == currentDifficulty) 0.5 + 0.4 * weight else weight
        return decision(exerciseId, module, currentDifficulty, target, key, window, meanSpeed, window.size, confidence)
    }

    /** Convenience for the UI card on Home. */
    fun recommendation(
        exerciseId: String,
        module: Module,
        currentDifficulty: Int,
        history: List<SessionRecord>,
    ): DifficultyRecommendation {
        val d = decideNext(exerciseId, module, currentDifficulty, history)
        return DifficultyRecommendation(
            exerciseId = exerciseId,
            currentDifficulty = currentDifficulty,
            recommendedDifficulty = d.to,
            confidence = d.confidence,
            explanationKey = d.explanationKey,
            evidenceSamples = d.samples,
        )
    }

    /** Session duration recommendation in minutes, rounded to 5. */
    fun recommendDurationMinutes(recentSessions: List<SessionRecord>): Int {
        val completed = recentSessions.filter { it.status == SessionStatus.COMPLETED }
        if (completed.size < 3) return DEFAULT_MINUTES
        val meanMinutes = completed.takeLast(8).map { it.activeDurationMillis / 60_000.0 }.average()
        val target = when {
            meanMinutes < 2.0 -> 3
            meanMinutes < 4.0 -> 5
            meanMinutes < 7.0 -> 8
            meanMinutes < 12.0 -> 12
            else -> 15
        }
        return target.coerceIn(3, 20)
    }

    /**
     * Recovery recommendation: after a heavy or failing streak, suggest a
     * lighter session or a rest day. Never blocks training.
     */
    fun recoveryRecommendation(recentSessions: List<SessionRecord>, nowMillis: Long): RecoveryAdvice {
        val week = 7L * 24 * 60 * 60 * 1000
        val recent = recentSessions.filter { nowMillis - it.startedAtMillis <= week }
        val completed = recent.count { it.status == SessionStatus.COMPLETED }
        val abandoned = recent.count { it.status == SessionStatus.ABANDONED }
        // Abandoned sessions still carry evidence of failure, so they count here.
        // Only sessions without any response are ignored.
        val accuracy = recent.filter { it.totalResponses > 0 }.map { it.accuracy }
        val meanAccuracy = if (accuracy.isEmpty()) 1.0 else accuracy.average()
        return when {
            abandoned >= 3 && meanAccuracy < 0.7 -> RecoveryAdvice(
                kind = RecoveryKind.REST_SUGGESTED,
                explanationKey = "recovery.rest_suggested",
                suggestedMinutes = 0,
            )
            completed >= 6 -> RecoveryAdvice(
                kind = RecoveryKind.LIGHTER_SESSION,
                explanationKey = "recovery.lighter_session",
                suggestedMinutes = 3,
            )
            else -> RecoveryAdvice(
                kind = RecoveryKind.NONE,
                explanationKey = "recovery.none",
                suggestedMinutes = recommendDurationMinutes(recentSessions),
            )
        }
    }

    private fun speedOf(record: SessionRecord): Double {
        val reference = com.neurox.domain.exercise.ExerciseTemplate.fromId(record.exerciseId)
            ?.referenceMillis(record.difficulty) ?: record.responseTimeMeanMillis.coerceAtLeast(1.0)
        return Scoring.speedFactor(record.responseTimeMeanMillis, reference)
    }

    private fun decision(
        exerciseId: String,
        module: Module,
        from: Int,
        to: Int,
        key: String,
        usable: List<SessionRecord>,
        meanSpeed: Double,
        samples: Int,
        confidenceOverride: Double? = null,
    ): AdaptationDecision {
        val window = if (usable.isEmpty()) emptyList() else usable.takeLast(settings.maxSamplesConsidered)
        val accuracy = if (window.isEmpty()) 0.0 else window.map { it.accuracy }.average()
        val confidence = confidenceOverride ?: if (to != from) SampleWeight.of(samples) else 0.5
        return AdaptationDecision(
            exerciseId = exerciseId,
            module = module,
            from = from,
            to = to.coerceIn(DifficultyLevel.MIN, DifficultyLevel.MAX),
            explanationKey = key,
            meanAccuracy = accuracy,
            meanSpeedFactor = meanSpeed,
            samples = samples,
            confidence = confidence,
        )
    }

    companion object {
        const val DEFAULT_MINUTES = 8
    }
}

enum class RecoveryKind { NONE, LIGHTER_SESSION, REST_SUGGESTED }

data class RecoveryAdvice(
    val kind: RecoveryKind,
    val explanationKey: String,
    val suggestedMinutes: Int,
)

/**
 * Weak-skill analysis for Analytics and the Training Planner.
 */
object WeakSkillAnalyzer {

    /** Aggregates the weakest skills from stored sessions, most urgent first. */
    fun analyze(history: List<SessionRecord>, limit: Int = 3): List<WeakSkill> {
        if (limit <= 0) return emptyList()
        val byExercise = history
            .filter { it.status == SessionStatus.COMPLETED }
            .groupBy { it.exerciseId }
        val scored = byExercise.mapNotNull { (exerciseId, records) ->
            val template = com.neurox.domain.exercise.ExerciseTemplate.fromId(exerciseId) ?: return@mapNotNull null
            if (records.size < 2) return@mapNotNull null
            val accuracy = records.map { it.accuracy }.average()
            val reference = template.referenceMillis(records.last().difficulty)
            val speed = records.map { Scoring.speedFactor(it.responseTimeMeanMillis, reference) }.average()
            WeakSkill(
                template = template,
                module = template.module,
                accuracy = accuracy,
                speed = speed,
                samples = records.size,
                deficit = (1.0 - accuracy) * 0.7 + (1.0 - speed) * 0.3,
            )
        }
        return scored.sortedByDescending { it.deficit }.take(limit)
    }
}

data class WeakSkill(
    val template: com.neurox.domain.exercise.ExerciseTemplate,
    val module: Module,
    val accuracy: Double,
    val speed: Double,
    val samples: Int,
    val deficit: Double,
)
