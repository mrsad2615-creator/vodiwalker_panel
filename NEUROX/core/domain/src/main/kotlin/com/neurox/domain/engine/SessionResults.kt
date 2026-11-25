package com.neurox.domain.engine

import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.scoring.Scoring
import kotlin.math.roundToInt

/**
 * Converts an engine [SessionSummary] into the durable [SessionRecord].
 *
 * This is the ONLY place where a summary becomes a stored record, so the rules
 * that decide "did this session count" exist once:
 * - USER_ABORTED and INTERRUPTED never become COMPLETED.
 * - No responses means no accuracy, no score and no XP.
 */
object SessionResults {

    fun toRecord(
        summary: SessionSummary,
        profileId: String,
        kind: SessionKind,
        referenceMillis: Double,
    ): SessionRecord {
        val status = when {
            // An empty session must never count as training, whatever ended it.
            summary.totalAnswered == 0 -> SessionStatus.ABANDONED
            summary.endReason == SessionEndReason.INTERRUPTED -> SessionStatus.ABANDONED
            summary.endReason == SessionEndReason.USER_ABORTED -> SessionStatus.ABANDONED
            summary.endReason == SessionEndReason.ROUNDS_COMPLETE -> SessionStatus.COMPLETED
            // A time limit only completes the session when work was actually done.
            summary.totalAnswered > 0 && summary.completion >= 0.25 -> SessionStatus.COMPLETED
            else -> SessionStatus.ABANDONED
        }
        // A response with no stimulus on screen is a commission error, so it is
        // counted with incorrect answers rather than ignored.
        val accuracy = Scoring.accuracy(
            summary.correct,
            summary.incorrect + summary.premature,
            summary.missed,
        )
        val speed = if (summary.responseTimesMillis.isEmpty()) {
            0.0
        } else {
            Scoring.speedFactor(Scoring.mean(summary.responseTimesMillis), referenceMillis)
        }
        val score = Scoring.compositeScore(accuracy, speed, summary.completion)
        val record = SessionRecord(
            id = summary.sessionId,
            profileId = profileId,
            exerciseId = summary.exerciseId,
            module = summary.module,
            kind = kind,
            status = status,
            startedAtMillis = summary.startedAtWallMillis,
            endedAtMillis = summary.endedAtWallMillis,
            activeDurationMillis = summary.activeDurationMillis,
            difficulty = summary.difficulty,
            roundCount = summary.plannedTrials,
            correctCount = summary.correct,
            incorrectCount = summary.incorrect,
            missedCount = summary.missed,
            prematureCount = summary.premature,
            responseTimeMeanMillis = if (summary.responseTimesMillis.isEmpty()) {
                0.0
            } else {
                Scoring.mean(summary.responseTimesMillis)
            },
            responseTimeMedianMillis = Scoring.median(summary.responseTimesMillis),
            responseTimeStdevMillis = Scoring.stdev(summary.responseTimesMillis),
            bestResponseMillis = (summary.responseTimesMillis.minOrNull() ?: 0.0).roundToInt().toLong(),
            accuracy = accuracy,
            score = score,
            xpAwarded = 0,
        )
        val xp = Scoring.xpFor(record)
        return record.copy(xpAwarded = xp)
    }

    /** True when this record may enter analytics aggregates. */
    fun countsForAnalytics(record: SessionRecord): Boolean =
        record.status == SessionStatus.COMPLETED && record.kind != SessionKind.BASELINE

    /** True when this record may be compared with [other] for trends. */
    fun comparable(a: SessionRecord, b: SessionRecord): Boolean =
        a.module == b.module &&
            a.kind == b.kind &&
            a.exerciseId == b.exerciseId &&
            a.status == SessionStatus.COMPLETED &&
            b.status == SessionStatus.COMPLETED
}
