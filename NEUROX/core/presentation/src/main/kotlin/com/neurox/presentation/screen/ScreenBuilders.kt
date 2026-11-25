package com.neurox.presentation.screen

import com.neurox.common.result.Outcome
import com.neurox.domain.analytics.AnalyticsService
import com.neurox.domain.analytics.AnalyticsSnapshot
import com.neurox.domain.analytics.ModuleAnalytics
import com.neurox.domain.adaptive.WeakSkillAnalyzer
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.progression.Achievements
import com.neurox.domain.progression.Levels
import com.neurox.domain.progression.ProgressionSnapshot
import com.neurox.domain.progression.Quests
import com.neurox.domain.progression.Streaks
import com.neurox.presentation.i18n.Strings

/**
 * Builds screen states from stored data.
 *
 * These functions are pure: records in, state out. No database, no clock, no
 * coroutines. That makes the "what does the user actually see" question testable,
 * including the empty and barely-populated cases that are usually discovered on a
 * device instead of in a test.
 *
 * THE RULE FOR ZEROS: a missing measurement is never rendered as 0. A module the
 * user has not trained has no accuracy - not 0% accuracy - and a chart with fewer
 * than [AnalyticsState.MIN_SESSIONS_FOR_TREND] sessions does not claim a trend.
 */
object ScreenBuilders {

    /**
     * Home.
     *
     * @param records every stored session of the active profile, oldest first.
     * @param nowMillis used only for day bucketing and "today" logic.
     */
    fun home(
        profile: ProfileSummary,
        strings: Strings,
        records: List<SessionRecord>,
        earnedAchievementIds: Set<String>,
        baselineComplete: Boolean,
        hasCheckpoint: Boolean,
        nowMillis: Long,
    ): HomeState {
        val completed = records.filter { it.status == SessionStatus.COMPLETED }
        val totalXp = completed.sumOf { it.xpAwarded } + Achievements.rewardXp(earnedAchievementIds)
        val dayKeys = completed.map { AnalyticsService.dayKey(it.endedAtMillis) }.toSet()
        val todayKey = AnalyticsService.dayKey(nowMillis)
        val trainedModules = completed.map { it.module }.toSet()

        val missions = Quests.dailyMissions(todayKey)
        val todaySessions = completed.filter { AnalyticsService.dayKey(it.endedAtMillis) == todayKey }

        val recommendation = ScreenMapper.recommendation(completed, trainedModules, nowMillis)

        return HomeState(
            profile = profile,
            strings = strings,
            totalXp = totalXp,
            level = Levels.levelForXp(totalXp),
            xpToNextLevel = xpToNextLevel(totalXp),
            streakDays = Streaks.currentStreak(dayKeys, todayKey),
            longestStreakDays = Streaks.longestStreak(dayKeys),
            recommendation = recommendation,
            missions = ScreenMapper.missionCards(missions, todaySessions, strings),
            moduleCards = moduleCards(completed),
            recentSessions = records
                .sortedByDescending { it.endedAtMillis }
                .take(RECENT_SESSION_LIMIT)
                .map(ScreenMapper::sessionRow),
            baselineComplete = baselineComplete,
            // The baseline prompt is offered once there is something to compare,
            // and never again after the user finishes or skips it.
            showBaselinePrompt = !baselineComplete && completed.size >= BASELINE_PROMPT_AFTER_SESSIONS,
            recoveryHintKey = recoveryHint(completed),
            hasAnySession = records.isNotEmpty(),
            hasCheckpoint = hasCheckpoint,
        )
    }

    private fun xpToNextLevel(totalXp: Int): Int {
        val level = Levels.levelForXp(totalXp)
        return (Levels.xpNeededForLevel(level + 1) - totalXp).coerceAtLeast(0)
    }

    /**
     * Offers a lighter day after two long sessions in a row. It is a hint, never a
     * restriction: the user can always train more.
     */
    private fun recoveryHint(completed: List<SessionRecord>): String? {
        val recent = completed.takeLast(RECOVERY_WINDOW)
        if (recent.size < RECOVERY_WINDOW) return null
        val averageMinutes = recent.sumOf { it.activeDurationMillis }.toDouble() / recent.size / 60_000.0
        return if (averageMinutes >= LONG_SESSION_MINUTES) "home.recovery.reason" else null
    }

    /**
     * Per-module cards. A module with no completed session reports
     * [ModuleCard.hasData] = false, and the UI shows "not trained yet" instead of a
     * row of zeros.
     */
    fun moduleCards(completed: List<SessionRecord>): List<ModuleCard> =
        Module.entries.map { module ->
            val moduleRecords = completed.filter { it.module == module }
            val denominator = moduleRecords.sumOf { it.roundCount }
            ModuleCard(
                module = module,
                sessionCount = moduleRecords.size,
                accuracy = if (denominator == 0) {
                    0.0
                } else {
                    moduleRecords.sumOf { it.correctCount }.toDouble() / denominator.toDouble()
                },
                hasData = moduleRecords.isNotEmpty(),
                lastTrainedMillis = moduleRecords.maxOfOrNull { it.endedAtMillis },
            )
        }

    /**
     * Analytics for a range.
     *
     * @param rangeStartMillis inclusive; pass [AnalyticsState.Range.ALL] resolved by
     *   the caller to the first stored session.
     */
    fun analytics(
        records: List<SessionRecord>,
        range: AnalyticsState.Range,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        baseline: List<SessionRecord> = emptyList(),
    ): AnalyticsState {
        val snapshot: AnalyticsSnapshot = AnalyticsService.snapshot(records, rangeStartMillis, rangeEndMillis)
        val completed = records.filter {
            it.status == SessionStatus.COMPLETED &&
                it.endedAtMillis in rangeStartMillis..rangeEndMillis
        }
        val dayKeys = records
            .filter { it.status == SessionStatus.COMPLETED }
            .map { AnalyticsService.dayKey(it.endedAtMillis) }
            .toSet()
        val todayKey = AnalyticsService.dayKey(rangeEndMillis)

        val personalRecords = AnalyticsService.personalRecords(completed).mapNotNull { (exerciseId, record) ->
            val template = ExerciseTemplate.fromId(exerciseId) ?: return@mapNotNull null
            RecordRow(
                template = template,
                module = template.module,
                bestScore = record.score,
                bestAccuracy = record.accuracy,
                difficulty = record.difficulty,
                achievedAtMillis = record.endedAtMillis,
            )
        }.sortedByDescending { it.bestScore }

        val weak = WeakSkillAnalyzer.analyze(completed, limit = WEAK_SKILL_LIMIT).map { skill ->
            WeakSkillRow(
                template = skill.template,
                module = skill.module,
                accuracy = skill.accuracy,
                // Speed deficit, in reference units: how far behind the exercise's
                // own speed reference this skill is. Lower is better.
                trend = -skill.deficit,
                sessions = skill.samples,
            )
        }

        val errorCounts = Module.entries.flatMap { module -> AnalyticsService.errorProfile(completed, module) }
        val totalErrors = errorCounts.sumOf { it.second }

        return AnalyticsState(
            range = range,
            sessionCount = snapshot.sessionCount,
            completedCount = snapshot.completedCount,
            abandonedCount = snapshot.abandonedCount,
            activeMinutes = snapshot.activeMinutes,
            totalXp = snapshot.totalXp,
            meanAccuracy = snapshot.meanAccuracy,
            activity = snapshot.activity,
            perModule = snapshot.perModule,
            dailySeries = snapshot.dailySeries,
            streakDays = Streaks.currentStreak(dayKeys, todayKey),
            longestStreakDays = Streaks.longestStreak(dayKeys),
            personalRecords = personalRecords,
            weakSkills = weak,
            errorBreakdown = errorCounts.mapNotNull { (exerciseId, count) ->
                val template = ExerciseTemplate.fromId(exerciseId) ?: return@mapNotNull null
                ErrorRow(
                    template = template,
                    module = template.module,
                    errorCount = count,
                    share = if (totalErrors == 0) 0.0 else count.toDouble() / totalErrors.toDouble(),
                )
            }.sortedByDescending { it.errorCount }.take(ERROR_ROW_LIMIT),
            baselineComparison = baselineComparison(baseline, completed),
        )
    }

    /**
     * Compares current bests with the BASELINE sessions.
     *
     * Only exercises that exist in both sets are compared. A baseline for an
     * exercise the user has not retrained is not a -100% drop; it is simply absent.
     */
    fun baselineComparison(
        baselineSessions: List<SessionRecord>,
        current: List<SessionRecord>,
    ): BaselineComparison? {
        val baselineBest = baselineSessions
            .filter { it.kind == SessionKind.BASELINE && it.status == SessionStatus.COMPLETED }
            .groupBy { it.exerciseId }
            .mapValues { (_, list) -> list.maxByOrNull { it.score } }
        if (baselineBest.isEmpty()) return null

        val currentBest = current
            .filter { it.status == SessionStatus.COMPLETED && it.kind != SessionKind.BASELINE }
            .groupBy { it.exerciseId }
            .mapValues { (_, list) -> list.maxByOrNull { it.score } }

        val rows = baselineBest.mapNotNull { (exerciseId, base) ->
            val template = ExerciseTemplate.fromId(exerciseId) ?: return@mapNotNull null
            val baseRecord = base ?: return@mapNotNull null
            val now = currentBest[exerciseId] ?: return@mapNotNull null
            BaselineComparison.BaselineRow(
                template = template,
                baselineScore = baseRecord.score,
                currentScore = now.score,
                baselineAtMillis = baseRecord.endedAtMillis,
            )
        }.sortedBy { it.template.module.displayOrder }

        if (rows.isEmpty()) return null
        return BaselineComparison(rows = rows, rangeStartMillis = rows.minOf { it.baselineAtMillis })
    }

    /**
     * Library with search, module filter and favorites applied.
     *
     * Search is case- and diacritic-insensitive over the localized name and the
     * exercise id, so a Persian user can find "قفل تمرکز" and a power user can type
     * "focus".
     */
    fun library(
        strings: Strings,
        query: String,
        moduleFilter: Module?,
        onlyFavorites: Boolean,
        favorites: Set<String>,
        custom: List<CustomExerciseRow>,
        personalBests: Map<String, Double>,
        lastPlayed: Map<String, Long>,
    ): LibraryState {
        val normalizedQuery = normalize(query)
        val customTemplateIds = custom.map { it.template.id }.toSet()

        val groups = Module.entries
            .filter { moduleFilter == null || it == moduleFilter }
            .mapNotNull { module ->
                val items = ExerciseTemplate.forModule(module).mapNotNull { template ->
                    val name = strings.raw(template.displayKey)
                    val matches = normalizedQuery.isEmpty() ||
                        normalize(name).contains(normalizedQuery) ||
                        normalize(template.id).contains(normalizedQuery)
                    if (!matches) return@mapNotNull null
                    val isFavorite = favorites.contains(template.id)
                    if (onlyFavorites && !isFavorite) return@mapNotNull null
                    LibraryItem(
                        template = template,
                        favorite = isFavorite,
                        custom = customTemplateIds.contains(template.id),
                        bestScore = personalBests[template.id],
                        lastPlayedMillis = lastPlayed[template.id],
                    )
                }
                if (items.isEmpty()) null else LibraryGroup(module = module, items = items)
            }

        return LibraryState(
            query = query,
            moduleFilter = moduleFilter,
            onlyFavorites = onlyFavorites,
            favorites = favorites,
            custom = custom,
            groups = groups,
        )
    }

    /** Lowercases and strips Persian/Arabic diacritics and the zero-width joiner. */
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text) {
            when {
                char == '\u200c' || char == '\u200d' -> Unit
                char in '\u064B'..'\u065F' -> Unit
                char == '\u0640' -> Unit
                char in '\u0660'..'\u0669' -> builder.append('0' + (char - '\u0660'))
                char in '\u06F0'..'\u06F9' -> builder.append('0' + (char - '\u06F0'))
                char.isUpperCase() -> builder.append(char.lowercaseChar())
                else -> builder.append(char)
            }
        }
        return builder.toString().trim()
    }

    /** Builder state from an existing config, with the domain's own validation. */
    fun builder(
        template: ExerciseTemplate,
        name: String,
        config: com.neurox.domain.exercise.ExerciseConfig,
        existingNames: List<String>,
        existingCount: Int,
    ): BuilderState = BuilderState(
        template = template,
        name = name,
        config = config,
        violations = config.validate(),
        savedId = null,
        existingCount = existingCount,
        nameTaken = existingNames.any { it.trim().equals(name.trim(), ignoreCase = true) },
    )

    private const val RECENT_SESSION_LIMIT = 5
    private const val BASELINE_PROMPT_AFTER_SESSIONS = 2
    private const val RECOVERY_WINDOW = 3
    private const val LONG_SESSION_MINUTES = 12.0
    private const val WEAK_SKILL_LIMIT = 3
    private const val ERROR_ROW_LIMIT = 6
}

/** Progress snapshot for achievements, from stored records only. */
fun progressionSnapshot(
    records: List<SessionRecord>,
    earnedAchievementIds: Set<String>,
    baselineCompleted: Boolean,
    dayKeys: Set<Long>,
): ProgressionSnapshot {
    val completed = records.filter { it.status == SessionStatus.COMPLETED }
    return ProgressionSnapshot(
        completedSessions = completed.size,
        longestStreakDays = Streaks.longestStreak(dayKeys),
        hasPerfectSession = completed.any {
            it.correctCount > 0 && it.incorrectCount == 0 && it.missedCount == 0 && it.prematureCount == 0
        },
        bestAccuracy = completed.maxOfOrNull { it.accuracy } ?: 0.0,
        modulesTrained = completed.map { it.module }.toSet(),
        highestDifficultyCompleted = completed.maxOfOrNull { it.difficulty } ?: 0,
        baselineCompleted = baselineCompleted,
    )
}
