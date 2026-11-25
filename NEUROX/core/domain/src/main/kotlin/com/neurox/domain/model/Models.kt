package com.neurox.domain.model

/**
 * The four training systems. All four are first-class; no module is a preview.
 */
enum class Module(
    val id: String,
    val displayOrder: Int,
) {
    VISION("vision", 0),
    REFLEX("reflex", 1),
    READ("read", 2),
    TYPE("type", 3),
    ;

    companion object {
        fun fromId(id: String): Module? = entries.firstOrNull { it.id == id }
    }
}

/** Difficulty is a 1..5 scale shared by every exercise. */
@JvmInline
value class DifficultyLevel(val value: Int) {
    init {
        require(value in MIN..MAX) { "difficulty must be in $MIN..$MAX" }
    }

    fun up(): DifficultyLevel = if (value == MAX) this else DifficultyLevel(value + 1)
    fun down(): DifficultyLevel = if (value == MIN) this else DifficultyLevel(value - 1)

    companion object {
        const val MIN = 1
        const val MAX = 5
        val EASY = DifficultyLevel(1)
        val MEDIUM = DifficultyLevel(3)
        val HARD = DifficultyLevel(5)
    }
}

/** How a completed session was produced. Used to keep data sets comparable. */
enum class SessionKind {
    /** User-started ordinary training session. */
    TRAINING,

    /** Baseline assessment run (NEUROX BASELINE). Stored and analyzed separately. */
    BASELINE,

    /** Custom exercise created in the Exercise Builder. */
    CUSTOM,
}

/** Whether a session counts for adaptive trends, XP and personal records. */
enum class SessionStatus {
    RUNNING,
    COMPLETED,
    ABANDONED,
}

/**
 * Canonical outcome record for one finished training session.
 *
 * Every engine produces this record. Fields not applicable to a module stay 0,
 * never -1, and [isApplicable] documents what each module measures.
 */
data class SessionRecord(
    val id: String,
    val profileId: String,
    val exerciseId: String,
    val module: Module,
    val kind: SessionKind,
    val status: SessionStatus,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val activeDurationMillis: Long,
    val difficulty: Int,
    val roundCount: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val missedCount: Int,
    val prematureCount: Int,
    val responseTimeMeanMillis: Double,
    val responseTimeMedianMillis: Double,
    val responseTimeStdevMillis: Double,
    val bestResponseMillis: Long,
    val accuracy: Double,
    val score: Double,
    val xpAwarded: Int,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    init {
        require(difficulty in DifficultyLevel.MIN..DifficultyLevel.MAX) {
            "difficulty out of range: $difficulty"
        }
        require(roundCount >= 0)
        require(correctCount >= 0 && incorrectCount >= 0 && missedCount >= 0 && prematureCount >= 0)
        require(activeDurationMillis >= 0)
    }

    val totalResponses: Int get() = correctCount + incorrectCount + missedCount + prematureCount

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Per-module aggregate used by Home, Analytics and the adaptive engine.
 */
data class ModuleStats(
    val module: Module,
    val sessionCount: Int,
    val totalActiveMillis: Long,
    val meanAccuracy: Double,
    val meanResponseMillis: Double,
    val bestScore: Double,
    val lastTrainedAtMillis: Long?,
)

/** Immutable profile snapshot used by domain logic. */
data class ProfileSummary(
    val id: String,
    val displayName: String,
    val isGuest: Boolean,
    val avatarId: Int,
    val createdAtMillis: Long,
    val hasPin: Boolean,
    val themeId: String,
    val languageTag: String,
    val active: Boolean,
)

/** Adaptive engine output; every field is a recommendation, never a mandate. */
data class DifficultyRecommendation(
    val exerciseId: String,
    val currentDifficulty: Int,
    val recommendedDifficulty: Int,
    val confidence: Double,
    val explanationKey: String,
    val evidenceSamples: Int,
) {
    val changed: Boolean get() = currentDifficulty != recommendedDifficulty
}
