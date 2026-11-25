package com.neurox.presentation.session

import com.neurox.domain.engine.SessionEndReason
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus

/**
 * Result screen model.
 *
 * The one rule that matters here: a session either counts or it does not, and the
 * screen says which. A finished-but-aborted session keeps its partial numbers for
 * the user to read, and explicitly states that it awards no XP and sets no record.
 */
data class ResultPresentation(
    val titleKey: String,
    val countsForProgress: Boolean,
    val accuracy: Double,
    val score: Double,
    val xp: Int,
    val meanResponseMillis: Double,
    val bestResponseMillis: Double,
    val roundCount: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val missedCount: Int,
    val prematureCount: Int,
    val isPerfect: Boolean,
    val endReason: SessionEndReason?,
    val adaptation: AdaptationPresentation?,
    val activeMinutes: Int,
) {
    val hasReactionData: Boolean get() = meanResponseMillis > 0.0
    val xpMessageKey: String get() = if (countsForProgress) "session.result.xp" else "session.result.noXp"

    companion object {
        /** End reasons that still produce a counted session. */
        private val COUNTING_REASONS = setOf(
            SessionEndReason.ROUNDS_COMPLETE,
            SessionEndReason.TIME_LIMIT,
        )

        fun of(
            record: SessionRecord,
            endReason: SessionEndReason? = null,
            adaptation: AdaptationPresentation? = null,
        ): ResultPresentation {
            val counts = record.status == SessionStatus.COMPLETED &&
                (endReason == null || endReason in COUNTING_REASONS)
            return ResultPresentation(
                titleKey = if (counts) "session.result.title" else "session.result.aborted",
                countsForProgress = counts,
                accuracy = record.accuracy,
                score = record.score,
                xp = if (counts) record.xpAwarded else 0,
                meanResponseMillis = record.responseTimeMeanMillis,
                bestResponseMillis = record.bestResponseMillis.toDouble(),
                roundCount = record.roundCount,
                correctCount = record.correctCount,
                incorrectCount = record.incorrectCount,
                missedCount = record.missedCount,
                prematureCount = record.prematureCount,
                isPerfect = counts && record.correctCount > 0 &&
                    record.incorrectCount == 0 && record.missedCount == 0 && record.prematureCount == 0,
                endReason = endReason,
                adaptation = adaptation,
                activeMinutes = (record.activeDurationMillis / 60_000L).toInt(),
            )
        }
    }
}

/**
 * Adaptive advice as the user sees it: a sentence, the two levels, and an explicit
 * accept/dismiss choice. The domain recommends; the user decides.
 */
data class AdaptationPresentation(
    val explanationKey: String,
    val fromDifficulty: Int,
    val toDifficulty: Int,
    val confidence: Double,
    val samples: Int,
) {
    val changes: Boolean get() = fromDifficulty != toDifficulty

    companion object {
        fun of(decision: com.neurox.domain.adaptive.AdaptationDecision): AdaptationPresentation =
            AdaptationPresentation(
                explanationKey = decision.explanationKey,
                fromDifficulty = decision.from,
                toDifficulty = decision.to,
                confidence = decision.confidence,
                samples = decision.samples,
            )
    }
}
