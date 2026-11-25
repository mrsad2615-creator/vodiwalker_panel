package com.neurox.presentation.screen

import com.neurox.common.result.DomainError
import com.neurox.domain.adaptive.AdaptationDecision
import com.neurox.domain.analytics.ActivityBucket
import com.neurox.domain.analytics.ModuleAnalytics
import com.neurox.domain.analytics.SeriesPoint
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.Skill
import com.neurox.domain.progression.Achievement
import com.neurox.domain.model.Module
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.ThemeDefinition
import com.neurox.domain.progression.Quests
import com.neurox.domain.progression.Recommender
import com.neurox.domain.progression.Streaks
import com.neurox.domain.progression.Levels
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.session.AdaptationPresentation

/**
 * Screen state models.
 *
 * Every screen has exactly one state object and the same four guarantees:
 * 1. A loading state exists.
 * 2. An empty state exists with a real explanation, never a zero-filled chart.
 * 3. An error state exists and carries the domain error code.
 * 4. A populated state carries only stored data - nothing is invented to look
 *    complete.
 *
 * The models are pure data so the Compose layer stays trivial and the logic
 * behind them is unit tested.
 */
sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>

    data class Empty(
        val titleKey: String,
        val bodyKey: String,
        val actionLabelKey: String? = null,
    ) : ScreenState<Nothing>

    data class Error(
        val error: DomainError,
        val retryable: Boolean = true,
    ) : ScreenState<Nothing> {
        val code: String get() = error.code
        val messageKey: String get() = "error.${error.code}"
    }

    data class Ready<T>(val value: T) : ScreenState<T>

    /** True when there is nothing to draw yet. */
    val isEmpty: Boolean get() = this is Empty

    val isReady: Boolean get() = this is Ready
}

// ---------------------------------------------------------------- home

data class HomeState(
    val profile: ProfileSummary,
    val strings: Strings,
    val totalXp: Int,
    val level: Int,
    val xpToNextLevel: Int,
    val streakDays: Int,
    val longestStreakDays: Int,
    val recommendation: RecommendationCard,
    val missions: List<MissionCard>,
    val moduleCards: List<ModuleCard>,
    val recentSessions: List<SessionRow>,
    val baselineComplete: Boolean,
    val showBaselinePrompt: Boolean,
    val recoveryHintKey: String?,
    val hasAnySession: Boolean,
    val hasCheckpoint: Boolean,
) {
    val isEmpty: Boolean get() = !hasAnySession
    val levelProgress: Double get() = Levels.progressToNext(totalXp)
}

data class RecommendationCard(
    val template: ExerciseTemplate,
    val difficulty: Int,
    val reasonKey: String,
    val minutes: Int,
) {
    val nameKey: String get() = template.displayKey
}

data class MissionCard(
    val id: String,
    val titleKey: String,
    val current: Int,
    val target: Int,
    val xpReward: Int,
    val complete: Boolean,
    val module: Module?,
) {
    val fraction: Double get() = if (target <= 0) 1.0 else (current.toDouble() / target).coerceIn(0.0, 1.0)
}

data class ModuleCard(
    val module: Module,
    val sessionCount: Int,
    val accuracy: Double,
    val hasData: Boolean,
    val lastTrainedMillis: Long?,
)

data class SessionRow(
    val id: String,
    val template: ExerciseTemplate?,
    val module: Module,
    val accuracy: Double,
    val score: Double,
    val endedAtMillis: Long,
    val xp: Int,
    val counts: Boolean,
)

// ---------------------------------------------------------------- analytics

data class AnalyticsState(
    val range: Range,
    val sessionCount: Int,
    val completedCount: Int,
    val abandonedCount: Int,
    val activeMinutes: Int,
    val totalXp: Int,
    val meanAccuracy: Double,
    val activity: List<ActivityBucket>,
    val perModule: List<ModuleAnalytics>,
    val dailySeries: List<SeriesPoint>,
    val streakDays: Int,
    val longestStreakDays: Int,
    val personalRecords: List<RecordRow>,
    val weakSkills: List<WeakSkillRow>,
    val errorBreakdown: List<ErrorRow>,
    val baselineComparison: BaselineComparison?,
) {
    enum class Range(val labelKey: String, val days: Int) {
        WEEK("analytics.range.week", 7),
        MONTH("analytics.range.month", 30),
        ALL("analytics.range.all", 0),
    }

    val hasData: Boolean get() = completedCount > 0

    /**
     * True when there is not enough data for a trend line. The UI shows an empty
     * state instead of drawing a line through two points and calling it a trend.
     */
    val hasTrend: Boolean get() = completedCount >= MIN_SESSIONS_FOR_TREND

    companion object {
        const val MIN_SESSIONS_FOR_TREND = 4
    }
}

data class RecordRow(
    val template: ExerciseTemplate,
    val module: Module,
    val bestScore: Double,
    val bestAccuracy: Double,
    val difficulty: Int,
    val achievedAtMillis: Long,
)

data class WeakSkillRow(
    val template: ExerciseTemplate,
    val module: Module,
    val accuracy: Double,
    val trend: Double,
    val sessions: Int,
)

data class ErrorRow(
    val template: ExerciseTemplate,
    val module: Module,
    val errorCount: Int,
    val share: Double,
)

/**
 * Baseline comparison. Only exercises present in the baseline are compared; a
 * missing baseline row is absent, not zero, because a zero would look like a
 * catastrophic drop.
 */
data class BaselineComparison(
    val rows: List<BaselineRow>,
    val rangeStartMillis: Long,
) {
    data class BaselineRow(
        val template: ExerciseTemplate,
        val baselineScore: Double,
        val currentScore: Double,
        val baselineAtMillis: Long,
    ) {
        fun delta(): Double = currentScore - baselineScore

        val improved: Boolean get() = delta() > 0.0
    }
}

// ---------------------------------------------------------------- library

data class LibraryState(
    val query: String,
    val moduleFilter: Module?,
    val onlyFavorites: Boolean,
    val favorites: Set<String>,
    val custom: List<CustomExerciseRow>,
    val groups: List<LibraryGroup>,
) {
    val isEmptyQuery: Boolean get() = query.isBlank()
    val totalItems: Int get() = groups.sumOf { it.items.size }

    val isSearchEmpty: Boolean get() = !isEmptyQuery && totalItems == 0
}

data class LibraryGroup(
    val module: Module,
    val items: List<LibraryItem>,
)

data class LibraryItem(
    val template: ExerciseTemplate,
    val favorite: Boolean,
    val custom: Boolean,
    val bestScore: Double?,
    val lastPlayedMillis: Long?,
    val locked: Boolean = false,
    val lockReasonKey: String? = null,
)

data class CustomExerciseRow(
    val id: String,
    val name: String,
    val template: ExerciseTemplate,
    val difficulty: Int,
    val rounds: Int,
)

/**
 * The Exercise Builder is a *validated form*, not a code editor. Everything the
 * builder can produce is an [ExerciseConfig], which the domain validates.
 */
data class BuilderState(
    val template: ExerciseTemplate,
    val name: String,
    val config: com.neurox.domain.exercise.ExerciseConfig,
    val violations: List<com.neurox.domain.exercise.ConfigViolation>,
    val savedId: String?,
    val existingCount: Int,
    val nameTaken: Boolean = false,
) {
    val canSave: Boolean
        get() = name.isNotBlank() &&
            name.length <= MAX_NAME_LENGTH &&
            violations.isEmpty() &&
            existingCount < MAX_CUSTOM_EXERCISES &&
            !nameTaken

    val nameErrorKey: String?
        get() = when {
            name.isBlank() -> "error.profile_name_blank"
            name.length > MAX_NAME_LENGTH -> "error.profile_name_too_long"
            nameTaken -> "library.builder.duplicate_name"
            else -> null
        }

    companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_CUSTOM_EXERCISES = 24
    }
}

// ---------------------------------------------------------------- profile

data class ProfileListState(
    val profiles: List<ProfileSummary>,
    val activeId: String?,
    val maxReached: Boolean,
    val pinPromptProfileId: String?,
) {
    val hasMoreThanOne: Boolean get() = profiles.size > 1
    val canCreate: Boolean get() = !maxReached
}

data class ProfileEditState(
    val profile: ProfileSummary?,
    val draftName: String,
    val draftAvatar: Int,
    val draftLanguage: String,
    val draftThemeId: String,
    val pinEntered: String,
    val pinConfirm: String,
    val errorKey: String?,
    val saved: Boolean,
) {
    val isNew: Boolean get() = profile == null
    val pinValid: Boolean get() = pinEntered.isEmpty() || (pinEntered.length in 4..8 && pinEntered == pinConfirm)
}

// ---------------------------------------------------------------- settings

data class SettingsState(
    val languageTag: String,
    val themeId: String,
    val availableThemes: List<ThemeDefinition>,
    val motionMode: String,
    val reducedMotion: Boolean,
    val textScale: Float,
    val adaptiveEnabled: Boolean,
    val adaptiveMinSamples: Int,
    val inputLatencyMillis: Float,
    val displayLatencyMillis: Float,
    val audio: com.neurox.domain.model.AudioSettings,
    val weeklyGoalMinutes: Int,
    val scheduledBackup: Boolean,
    val lastExportAtMillis: Long,
    val appVersion: String,
) {
    val hasEverExported: Boolean get() = lastExportAtMillis > 0L
}

// ---------------------------------------------------------------- onboarding

data class OnboardingState(
    val step: Step,
    val languageTag: String,
    val profileName: String,
    val isGuest: Boolean,
    val profileCreated: Boolean,
) {
    enum class Step { WELCOME, LANGUAGE, PROFILE, BASELINE, PRIVACY, ACCESSIBILITY, DONE }

    val isLast: Boolean get() = step == Step.DONE
    val canContinue: Boolean
        get() = when (step) {
            Step.PROFILE -> profileCreated || profileName.isNotBlank() || isGuest
            Step.DONE -> true
            else -> true
        }
}

// ---------------------------------------------------------------- vault

data class VaultExportState(
    val selection: Set<String>,
    val password: String,
    val passwordConfirm: String,
    val busy: Boolean,
    val progressKey: String?,
    val done: Boolean,
    val bytes: Int,
    val errorKey: String?,
) {
    val passwordValid: Boolean get() = password.length >= MIN_PASSWORD_LENGTH
    val passwordsMatch: Boolean get() = password == passwordConfirm
    val canExport: Boolean
        get() = selection.isNotEmpty() && passwordValid && passwordsMatch && !busy

    val validationKey: String?
        get() = when {
            selection.isEmpty() -> "vault.export.choose"
            !passwordValid && password.isNotEmpty() -> "vault.export.passwordShort"
            !passwordsMatch && passwordConfirm.isNotEmpty() -> "vault.export.passwordMismatch"
            else -> null
        }

    companion object {
        const val MIN_PASSWORD_LENGTH = 10
    }
}

data class VaultImportState(
    val fileName: String?,
    val header: ImportHeader?,
    val password: String,
    val step: Step,
    val busy: Boolean,
    val resultKey: String?,
    val errorKey: String?,
    val restoredSessions: Int,
    val restoredSettings: Int,
) {
    enum class Step { CHOOSE_FILE, PREVIEW, CONFIRM, PASSWORD, RUNNING, DONE, FAILED }

    val canStart: Boolean get() = header != null && password.isNotBlank() && !busy
}

/**
 * What a backup file tells the user BEFORE they type their password.
 *
 * There is deliberately no profile name and no session count here: the envelope on
 * disk carries neither, so a backup file that leaks identifies nobody. Showing a name
 * would require storing it in plaintext next to the ciphertext.
 */
data class ImportHeader(
    val createdAtMillis: Long,
    val formatVersion: Int,
    val kdfAlgorithm: String,
    val kdfIterations: Int,
    val usesFallbackKdf: Boolean,
    val selection: List<String>,
)

// ---------------------------------------------------------------- helpers

/**
 * Converts stored records into the rows the UI shows.
 *
 * Kept as an object (not private methods) so tests can pin the mapping, and so no
 * screen can invent a row shape of its own.
 */
object ScreenMapper {

    fun sessionRow(record: SessionRecord): SessionRow = SessionRow(
        id = record.id,
        template = ExerciseTemplate.fromId(record.exerciseId),
        module = record.module,
        accuracy = record.accuracy,
        score = record.score,
        endedAtMillis = record.endedAtMillis,
        xp = record.xpAwarded,
        counts = record.status == com.neurox.domain.model.SessionStatus.COMPLETED,
    )

    fun missionCards(
        missions: List<Quests.Mission>,
        todaySessions: List<SessionRecord>,
        strings: Strings,
    ): List<MissionCard> {
        val progress = Quests.progress(missions, todaySessions)
        return progress.map { item ->
            MissionCard(
                id = item.mission.id,
                titleKey = item.mission.titleKey,
                current = item.current,
                target = item.mission.target,
                xpReward = item.mission.xpReward,
                complete = item.complete,
                module = item.mission.module,
            )
        }
    }

    fun recommendation(
        history: List<SessionRecord>,
        trainedModules: Set<Module>,
        nowMillis: Long,
    ): RecommendationCard {
        val untrained = Module.entries.filter { it !in trainedModules }.toSet()
        val suggestion: Recommender.Suggestion = Recommender.suggest(history, untrained, nowMillis)
        return RecommendationCard(
            template = suggestion.template,
            difficulty = suggestion.difficulty,
            reasonKey = suggestion.reasonKey,
            minutes = suggestion.minutes,
        )
    }

    fun streak(dayKeys: Set<Long>, todayDayKey: Long): Int = Streaks.currentStreak(dayKeys, todayDayKey)

    fun longestStreak(dayKeys: Set<Long>): Int = Streaks.longestStreak(dayKeys)

    fun achievementCards(
        all: Set<String>,
        earned: Set<String>,
    ): List<AchievementCard> = all.mapNotNull { id ->
        val achievement = Achievement.fromId(id) ?: return@mapNotNull null
        AchievementCard(
            id = achievement.id,
            titleKey = achievement.titleKey,
            descriptionKey = achievement.descriptionKey,
            xpReward = achievement.xpReward,
            tier = achievement.tier.name,
            earned = earned.contains(achievement.id),
        )
    }

    fun skillsOf(template: ExerciseTemplate): List<Skill> = template.skills

    fun adaptationOf(decision: AdaptationDecision): AdaptationPresentation =
        AdaptationPresentation(
            explanationKey = decision.explanationKey,
            fromDifficulty = decision.from,
            toDifficulty = decision.to,
            confidence = decision.confidence,
            samples = decision.samples,
        )
}

data class AchievementCard(
    val id: String,
    val titleKey: String,
    val descriptionKey: String,
    val xpReward: Int,
    val tier: String,
    val earned: Boolean,
)
