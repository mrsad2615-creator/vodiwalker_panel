package com.neurox.domain.progression

import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Global and per-module leveling.
 *
 * Curve: level N requires 100 * N * (N+1) / 2 accumulated XP beyond the previous
 * level, i.e. cumulative thresholds grow quadratically and are easy to verify.
 */
object Levels {

    fun xpNeededForLevel(level: Int): Int {
        require(level >= 1) { "level starts at 1" }
        if (level == 1) return 0
        val previous = level - 1
        return 100 * previous * (previous + 1) / 2
    }

    fun levelForXp(totalXp: Int): Int {
        if (totalXp <= 0) return 1
        var level = 1
        while (xpNeededForLevel(level + 1) <= totalXp) level += 1
        return level
    }

    /** Progress within the current level in 0..1. */
    fun progressToNext(totalXp: Int): Double {
        val level = levelForXp(totalXp)
        val current = xpNeededForLevel(level)
        val next = xpNeededForLevel(level + 1)
        if (next <= current) return 1.0
        return ((totalXp - current).toDouble() / (next - current).toDouble()).coerceIn(0.0, 1.0)
    }
}

/**
 * Achievement catalog. Ids are stable forever; they are persisted in backups.
 *
 * [evaluate] is a pure function of stored aggregates, therefore a reward can be
 * recomputed after restore and duplicates are impossible with an
 * `INSERT OR IGNORE` on (profileId, achievementId).
 */
enum class Achievement(
    val id: String,
    val titleKey: String,
    val descriptionKey: String,
    val xpReward: Int,
    val tier: Tier,
) {
    FIRST_SESSION("first_session", "achievement.first_session.title", "achievement.first_session.desc", 20, Tier.BRONZE),
    SESSIONS_10("sessions_10", "achievement.sessions_10.title", "achievement.sessions_10.desc", 40, Tier.BRONZE),
    SESSIONS_50("sessions_50", "achievement.sessions_50.title", "achievement.sessions_50.desc", 120, Tier.SILVER),
    SESSIONS_200("sessions_200", "achievement.sessions_200.title", "achievement.sessions_200.desc", 300, Tier.GOLD),
    STREAK_3("streak_3", "achievement.streak_3.title", "achievement.streak_3.desc", 30, Tier.BRONZE),
    STREAK_7("streak_7", "achievement.streak_7.title", "achievement.streak_7.desc", 80, Tier.SILVER),
    STREAK_30("streak_30", "achievement.streak_30.title", "achievement.streak_30.desc", 250, Tier.GOLD),
    PERFECT_SESSION("perfect_session", "achievement.perfect_session.title", "achievement.perfect_session.desc", 60, Tier.SILVER),
    ACCURACY_95("accuracy_95", "achievement.accuracy_95.title", "achievement.accuracy_95.desc", 100, Tier.SILVER),
    ALL_FOUR_MODULES("all_four_modules", "achievement.all_four.title", "achievement.all_four.desc", 80, Tier.SILVER),
    DIFFICULTY_5("difficulty_5", "achievement.difficulty_5.title", "achievement.difficulty_5.desc", 150, Tier.GOLD),
    BASELINE_DONE("baseline_done", "achievement.baseline_done.title", "achievement.baseline_done.desc", 50, Tier.BRONZE),
    ;

    enum class Tier { BRONZE, SILVER, GOLD }

    companion object {
        fun fromId(id: String): Achievement? = entries.firstOrNull { it.id == id }
    }
}

/** Aggregates needed to evaluate achievements for one profile. */
data class ProgressionSnapshot(
    val completedSessions: Int,
    val longestStreakDays: Int,
    val hasPerfectSession: Boolean,
    val bestAccuracy: Double,
    val modulesTrained: Set<Module>,
    val highestDifficultyCompleted: Int,
    val baselineCompleted: Boolean,
)

object Achievements {

    /** Deterministic list of achievements earned for a snapshot. */
    fun earned(snapshot: ProgressionSnapshot): List<Achievement> = buildList {
        if (snapshot.completedSessions >= 1) add(Achievement.FIRST_SESSION)
        if (snapshot.completedSessions >= 10) add(Achievement.SESSIONS_10)
        if (snapshot.completedSessions >= 50) add(Achievement.SESSIONS_50)
        if (snapshot.completedSessions >= 200) add(Achievement.SESSIONS_200)
        if (snapshot.longestStreakDays >= 3) add(Achievement.STREAK_3)
        if (snapshot.longestStreakDays >= 7) add(Achievement.STREAK_7)
        if (snapshot.longestStreakDays >= 30) add(Achievement.STREAK_30)
        if (snapshot.hasPerfectSession) add(Achievement.PERFECT_SESSION)
        if (snapshot.bestAccuracy >= 0.95) add(Achievement.ACCURACY_95)
        if (snapshot.modulesTrained.size == Module.entries.size) add(Achievement.ALL_FOUR_MODULES)
        if (snapshot.highestDifficultyCompleted >= 5) add(Achievement.DIFFICULTY_5)
        if (snapshot.baselineCompleted) add(Achievement.BASELINE_DONE)
    }

    /** Achievements newly earned after an update, in catalog order. */
    fun newlyEarned(previous: Set<String>, snapshot: ProgressionSnapshot): List<Achievement> =
        earned(snapshot).filter { it.id !in previous }

    /** Total XP from achievement rewards, counted once per id. */
    fun rewardXp(earnedIds: Set<String>): Int =
        earnedIds.sumOf { id -> Achievement.fromId(id)?.xpReward ?: 0 }

    /** Builds a snapshot from stored records. */
    fun snapshotOf(
        records: List<SessionRecord>,
        longestStreakDays: Int,
        baselineCompleted: Boolean,
    ): ProgressionSnapshot {
        val completed = records.filter { it.status == SessionStatus.COMPLETED }
        return ProgressionSnapshot(
            completedSessions = completed.size,
            longestStreakDays = longestStreakDays,
            hasPerfectSession = completed.any { it.accuracy >= 1.0 && it.totalResponses >= 8 },
            bestAccuracy = completed.maxOfOrNull { it.accuracy } ?: 0.0,
            modulesTrained = completed.map { it.module }.toSet(),
            highestDifficultyCompleted = completed.maxOfOrNull { it.difficulty } ?: 0,
            baselineCompleted = baselineCompleted,
        )
    }
}

/**
 * Streaks are computed from calendar days in the user's timezone.
 *
 * The caller supplies day keys (for example "2026-11-24") already localized to
 * the profile timezone, so this object stays timezone-free and testable.
 */
object Streaks {

    /** Current streak: consecutive day keys ending at [todayKey] or yesterday. */
    fun currentStreak(dayKeys: Set<Long>, today: Long): Int {
        if (dayKeys.isEmpty()) return 0
        var cursor = if (today in dayKeys) today else today - 1
        var count = 0
        while (cursor in dayKeys) {
            count += 1
            cursor -= 1
        }
        return count
    }

    fun longestStreak(dayKeys: Set<Long>): Int {
        if (dayKeys.isEmpty()) return 0
        val sorted = dayKeys.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1] + 1) {
                run += 1
                if (run > best) best = run
            } else if (sorted[i] != sorted[i - 1]) {
                run = 1
            }
        }
        return best
    }
}

/**
 * NEUROX QUEST: daily missions and weekly challenges.
 *
 * Missions are generated deterministically from the day key, so the same day
 * gives the same missions after a process restart.
 */
object Quests {

    enum class MissionKind(val id: String) {
        COMPLETE_SESSIONS("complete_sessions"),
        TRAIN_MINUTES("train_minutes"),
        MODULE_FOCUS("module_focus"),
        ACCURACY_TARGET("accuracy_target"),
        DIFFERENT_MODULES("different_modules"),
    }

    data class Mission(
        val id: String,
        val kind: MissionKind,
        val target: Int,
        val module: Module?,
        val xpReward: Int,
        val titleKey: String,
    )

    fun dailyMissions(dayKey: Long): List<Mission> {
        val random = kotlin.random.Random(dayKey * 31L + 7L)
        val module = Module.entries[random.nextInt(Module.entries.size)]
        val sessions = 2 + random.nextInt(2)
        val minutes = 6 + random.nextInt(3) * 3
        return listOf(
            Mission(
                id = "daily_$dayKey" + "_sessions",
                kind = MissionKind.COMPLETE_SESSIONS,
                target = sessions,
                module = null,
                xpReward = 25,
                titleKey = "quest.daily.sessions",
            ),
            Mission(
                id = "daily_$dayKey" + "_minutes",
                kind = MissionKind.TRAIN_MINUTES,
                target = minutes,
                module = null,
                xpReward = 30,
                titleKey = "quest.daily.minutes",
            ),
            Mission(
                id = "daily_$dayKey" + "_module",
                kind = MissionKind.MODULE_FOCUS,
                target = 1,
                module = module,
                xpReward = 35,
                titleKey = "quest.daily.module",
            ),
        )
    }

    data class QuestProgress(
        val mission: Mission,
        val current: Int,
    ) {
        val complete: Boolean get() = current >= mission.target
        val fraction: Double get() = (current.toDouble() / mission.target.toDouble()).coerceIn(0.0, 1.0)
    }

    /** Evaluates mission progress from today's completed sessions. */
    fun progress(missions: List<Mission>, todaySessions: List<SessionRecord>): List<QuestProgress> {
        val completed = todaySessions.filter { it.status == SessionStatus.COMPLETED }
        return missions.map { mission ->
            val current = when (mission.kind) {
                MissionKind.COMPLETE_SESSIONS -> completed.size
                MissionKind.TRAIN_MINUTES -> (completed.sumOf { it.activeDurationMillis } / 60_000L).toInt()
                MissionKind.MODULE_FOCUS -> completed.count { it.module == mission.module }
                MissionKind.ACCURACY_TARGET -> (completed.map { it.accuracy }.maxOrNull()?.times(100))?.toInt() ?: 0
                MissionKind.DIFFERENT_MODULES -> completed.map { it.module }.distinct().size
            }
            QuestProgress(mission, current)
        }
    }
}

/**
 * Deterministic recommendation for "what should I train now".
 */
object Recommender {

    /**
     * Catalogue keys for the four explanations.
     *
     * Declared as constants rather than built inline so the contract test can pin
     * them against both catalogues, and so a rename is one edit.
     */
    const val REASON_START = "home.recommendedReason.start"
    const val REASON_WEAK_SKILL = "home.recommendedReason.weak_skill"
    const val REASON_NEW_MODULE = "home.recommendedReason.new_module"
    const val REASON_ROTATION = "home.recommendedReason.rotation"

    data class Suggestion(
        val template: com.neurox.domain.exercise.ExerciseTemplate,
        val difficulty: Int,
        /**
         * Full catalogue key for the explanation shown with the suggestion.
         *
         * It is the whole key on purpose: a short suffix ("start") forces every
         * caller to re-add a prefix, and a caller that guesses the prefix wrongly
         * shows the key itself to the user. See Strings parity tests.
         */
        val reasonKey: String,
        val minutes: Int,
    )

    fun suggest(
        history: List<SessionRecord>,
        untrainedModules: Set<Module>,
        nowMillis: Long,
    ): Suggestion {
        val completed = history.filter { it.status == SessionStatus.COMPLETED }
        val weak = WeakSkillShortcut.weakest(completed)
        val reasonKey = when {
            completed.isEmpty() -> REASON_START
            weak != null -> REASON_WEAK_SKILL
            untrainedModules.isNotEmpty() -> REASON_NEW_MODULE
            else -> REASON_ROTATION
        }
        val template = weak
            ?: untrainedModules.minByOrNull { it.displayOrder }
                ?.let { module ->
                    com.neurox.domain.exercise.ExerciseTemplate.forModule(module)
                        .minByOrNull { t -> completed.count { it.exerciseId == t.id } }
                }
            ?: leastPracticed(completed)
            ?: com.neurox.domain.exercise.ExerciseTemplate.FOCUS_LOCK
        val last = completed.lastOrNull { it.exerciseId == template.id }
        val difficulty = last?.difficulty ?: 2
        val minutes = if (completed.isEmpty()) 5 else 8
        // nowMillis is part of the signature so callers must pass a stable clock.
        @Suppress("UNUSED_EXPRESSION") nowMillis
        return Suggestion(template, difficulty, reasonKey, minutes)
    }

    private fun leastPracticed(history: List<SessionRecord>): com.neurox.domain.exercise.ExerciseTemplate? {
        val counts = com.neurox.domain.exercise.ExerciseTemplate.entries.associateWith { t ->
            history.count { it.exerciseId == t.id }
        }
        return counts.entries.minByOrNull { it.value }?.key
    }

    private object WeakSkillShortcut {
        fun weakest(history: List<SessionRecord>): com.neurox.domain.exercise.ExerciseTemplate? {
            if (history.size < 4) return null
            val byExercise = history.groupBy { it.exerciseId }
            return byExercise.entries
                .filter { it.value.size >= 2 }
                .minByOrNull { entry -> entry.value.map { it.accuracy }.average() }
                ?.let { com.neurox.domain.exercise.ExerciseTemplate.fromId(it.key) }
        }
    }
}

/** Small helper used by analytics cards to format a level title key. */
fun levelTitleKey(level: Int): String = "level.title.$level"

/** Hermite easing used by the presentation layer for progress bars. */
fun smoothProgress(value: Double): Double {
    val t = value.coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

/** Integer square helper retained for calibration curves. */
internal fun squared(x: Double): Double = x * x

/** Rounds a score to one decimal for display. */
fun roundedScore(value: Double): Double = floor(value * 10.0 + 0.5) / 10.0

/** Sanity bound used by tests to detect accidental infinite level loops. */
internal fun safeLevelForXp(xp: Int): Int = sqrt(xp.coerceAtLeast(0).toDouble()).toInt().coerceAtLeast(1)
