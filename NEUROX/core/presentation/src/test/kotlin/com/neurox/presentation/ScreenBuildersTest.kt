package com.neurox.presentation

import com.neurox.domain.analytics.AnalyticsService
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.AnalyticsState
import com.neurox.presentation.screen.BuilderState
import com.neurox.presentation.screen.ScreenBuilders
import com.neurox.presentation.screen.ScreenMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen-state tests.
 *
 * The theme of these tests is honesty of representation: a module that was never
 * trained must not look like a module with 0% accuracy, a chart with two sessions
 * must not claim a trend, and a baseline comparison must not invent rows.
 */
class ScreenBuildersTest {

    private val strings = Strings.of("en")
    private val now = 1_700_000_000_000L

    private val profile = ProfileSummary(
        id = "p1",
        displayName = "Sara",
        isGuest = false,
        avatarId = 3,
        createdAtMillis = now - 86_400_000L * 30,
        hasPin = true,
        themeId = "neurox.obsidian",
        languageTag = "fa",
        active = true,
    )

    private fun record(
        id: String,
        template: ExerciseTemplate = ExerciseTemplate.FOCUS_LOCK,
        status: SessionStatus = SessionStatus.COMPLETED,
        kind: SessionKind = SessionKind.TRAINING,
        correct: Int = 10,
        incorrect: Int = 2,
        missed: Int = 0,
        rounds: Int = 12,
        accuracy: Double = 0.83,
        score: Double = 720.0,
        xp: Int = 40,
        endedAt: Long = now - 3_600_000L,
        difficulty: Int = 3,
        activeMillis: Long = 120_000L,
    ) = SessionRecord(
        id = id,
        profileId = profile.id,
        exerciseId = template.id,
        module = template.module,
        kind = kind,
        status = status,
        startedAtMillis = endedAt - activeMillis,
        endedAtMillis = endedAt,
        activeDurationMillis = activeMillis,
        difficulty = difficulty,
        roundCount = rounds,
        correctCount = correct,
        incorrectCount = incorrect,
        missedCount = missed,
        prematureCount = 0,
        responseTimeMeanMillis = 640.0,
        responseTimeMedianMillis = 620.0,
        responseTimeStdevMillis = 90.0,
        bestResponseMillis = 480L,
        accuracy = accuracy,
        score = score,
        xpAwarded = xp,
    )

    @Test
    fun aNewProfileGetsARealEmptyStateNotZeroFilledCards() {
        val home = ScreenBuilders.home(
            profile = profile,
            strings = strings,
            records = emptyList(),
            earnedAchievementIds = emptySet(),
            baselineComplete = false,
            hasCheckpoint = false,
            nowMillis = now,
        )
        assertTrue(home.isEmpty)
        assertFalse(home.hasAnySession)
        assertEquals(0, home.streakDays)
        assertEquals(1, home.level)
        // Every module card exists, but none of them claims data it does not have.
        assertEquals(4, home.moduleCards.size)
        assertTrue(home.moduleCards.none { it.hasData })
        assertEquals(0.0, home.moduleCards.first { it.module == Module.VISION }.accuracy, 0.0)
        // The first recommendation is the onboarding one, not a fabricated weakness.
        assertEquals("recommend.start", home.recommendation.reasonKey)
    }

    @Test
    fun theBaselinePromptAppearsOnlyAfterEnoughSessionsAndDisappearsWhenDone() {
        val two = listOf(record("a"), record("b", endedAt = now - 7_200_000L))
        val promptShown = ScreenBuilders.home(
            profile, strings, two, emptySet(), baselineComplete = false, hasCheckpoint = false, nowMillis = now,
        )
        assertTrue(promptShown.showBaselinePrompt)

        val completedBaseline = ScreenBuilders.home(
            profile, strings, two, emptySet(), baselineComplete = true, hasCheckpoint = false, nowMillis = now,
        )
        assertFalse(completedBaseline.showBaselinePrompt)
    }

    @Test
    fun streakAndLevelComeFromStoredRecords() {
        // Two sessions on consecutive days.
        val yesterday = now - 86_400_000L
        val records = listOf(
            record("a", endedAt = yesterday, xp = 50),
            record("b", endedAt = now, xp = 70),
        )
        val home = ScreenBuilders.home(
            profile, strings, records, emptySet(), baselineComplete = false, hasCheckpoint = false, nowMillis = now,
        )
        assertEquals(2, home.streakDays)
        assertEquals(120, home.totalXp)
        // Level 2 starts at 100 XP, level 3 at 300 XP, so 120 XP is level 2 with
        // 180 XP still to go.
        assertEquals(2, home.level)
        assertEquals(180, home.xpToNextLevel)
    }

    @Test
    fun abandonedSessionsDoNotContributeXpOrStreaks() {
        val records = listOf(
            record("abandoned", status = SessionStatus.ABANDONED, xp = 0, endedAt = now),
            record("running", status = SessionStatus.RUNNING, xp = 0, endedAt = now),
        )
        val home = ScreenBuilders.home(
            profile, strings, records, emptySet(), baselineComplete = false, hasCheckpoint = false, nowMillis = now,
        )
        assertEquals(0, home.totalXp)
        assertEquals(0, home.streakDays)
        assertFalse(home.hasAnySession.not())
        // The history still shows them, because the user should see what happened.
        assertEquals(2, home.recentSessions.size)
        assertTrue(home.recentSessions.none { it.counts })
    }

    @Test
    fun analyticsWithNoSessionsReportsNoTrend() {
        val analytics = ScreenBuilders.analytics(
            records = emptyList(),
            range = AnalyticsState.Range.WEEK,
            rangeStartMillis = now - 7 * 86_400_000L,
            rangeEndMillis = now,
        )
        assertFalse(analytics.hasData)
        assertFalse(analytics.hasTrend)
        assertEquals(0, analytics.completedCount)
        assertTrue(analytics.personalRecords.isEmpty())
        assertTrue(analytics.weakSkills.isEmpty())
        assertNull(analytics.baselineComparison)
    }

    @Test
    fun analyticsWithFewSessionsRefusesToClaimATrend() {
        val records = listOf(record("a"), record("b"))
        val analytics = ScreenBuilders.analytics(
            records, AnalyticsState.Range.WEEK, now - 7 * 86_400_000L, now,
        )
        assertTrue(analytics.hasData)
        assertFalse("two sessions are not a trend", analytics.hasTrend)
    }

    @Test
    fun analyticsOnlyCountsSessionsInsideTheSelectedRange() {
        val inside = record("inside", endedAt = now - 86_400_000L)
        val outside = record("outside", endedAt = now - 40 * 86_400_000L)
        val week = ScreenBuilders.analytics(
            listOf(inside, outside), AnalyticsState.Range.WEEK, now - 7 * 86_400_000L, now,
        )
        assertEquals(1, week.completedCount)
        assertEquals(1, week.sessionCount)
    }

    @Test
    fun baselineComparisonSkipsExercisesThatWereNotRetrained() {
        val baseline = listOf(
            record("base-1", template = ExerciseTemplate.FOCUS_LOCK, kind = SessionKind.BASELINE, score = 500.0),
            record("base-2", template = ExerciseTemplate.QUICK_TAP, kind = SessionKind.BASELINE, score = 400.0),
        )
        // Only FOCUS_LOCK was trained again.
        val current = listOf(record("now-1", template = ExerciseTemplate.FOCUS_LOCK, score = 620.0))
        val comparison = ScreenBuilders.baselineComparison(baseline, current)
        assertNotNull(comparison)
        assertEquals(1, comparison!!.rows.size)
        assertEquals(ExerciseTemplate.FOCUS_LOCK, comparison.rows.single().template)
        assertEquals(120.0, comparison.rows.single().delta(), 0.001)
        assertTrue(comparison.rows.single().improved)
    }

    @Test
    fun aBaselineComparisonWithoutSharedExercisesIsAbsent() {
        val baseline = listOf(record("base", template = ExerciseTemplate.QUICK_TAP, kind = SessionKind.BASELINE))
        val current = listOf(record("now", template = ExerciseTemplate.READING_COMPREHENSION))
        assertNull(ScreenBuilders.baselineComparison(baseline, current))
    }

    @Test
    fun personalRecordsAreTheBestScorePerExercise() {
        val records = listOf(
            record("low", score = 300.0),
            record("high", score = 900.0),
            record("mid", score = 600.0),
        )
        val analytics = ScreenBuilders.analytics(records, AnalyticsState.Range.ALL, 0L, now)
        val best = analytics.personalRecords.first { it.template == ExerciseTemplate.FOCUS_LOCK }
        assertEquals(900.0, best.bestScore, 0.001)
    }

    @Test
    fun librarySearchFindsExercisesByPersianNameAndByTechnicalId() {
        val persian = Strings.of("fa")
        val byId = ScreenBuilders.library(
            strings = persian, query = "focus", moduleFilter = null, onlyFavorites = false,
            favorites = emptySet(), custom = emptyList(), personalBests = emptyMap(), lastPlayed = emptyMap(),
        )
        val focusTemplate = ExerciseTemplate.FOCUS_LOCK
        assertTrue(byId.groups.any { group -> group.items.any { it.template == focusTemplate } })

        val persianName = persian.raw(focusTemplate.displayKey)
        val byName = ScreenBuilders.library(
            strings = persian, query = persianName, moduleFilter = null, onlyFavorites = false,
            favorites = emptySet(), custom = emptyList(), personalBests = emptyMap(), lastPlayed = emptyMap(),
        )
        assertEquals(1, byName.totalItems)
    }

    @Test
    fun librarySearchIgnoresPersianDiacriticsAndZeroWidthJoiners() {
        // "موردعلاقهها" contains a ZWNJ; a user cannot be expected to type it.
        val normalized = ScreenBuilders.normalize("موردعلاقه\u200cها")
        assertEquals("موردعلاقهها", normalized)
        // Persian digits normalize to ASCII so "۱۰" and "10" match.
        assertEquals("10", ScreenBuilders.normalize("۱۰"))
        assertEquals("2", ScreenBuilders.normalize("۲"))
    }

    @Test
    fun libraryModuleFilterAndFavoritesAreApplied() {
        val focusId = ExerciseTemplate.FOCUS_LOCK.id
        val filtered = ScreenBuilders.library(
            strings = strings, query = "", moduleFilter = Module.REFLEX, onlyFavorites = false,
            favorites = setOf(focusId), custom = emptyList(), personalBests = emptyMap(), lastPlayed = emptyMap(),
        )
        assertEquals(1, filtered.groups.size)
        assertEquals(Module.REFLEX, filtered.groups.single().module)
        assertTrue(filtered.groups.single().items.all { it.template.module == Module.REFLEX })

        val favoritesOnly = ScreenBuilders.library(
            strings = strings, query = "", moduleFilter = null, onlyFavorites = true,
            favorites = setOf(focusId), custom = emptyList(), personalBests = emptyMap(), lastPlayed = emptyMap(),
        )
        assertEquals(1, favoritesOnly.totalItems)
        assertEquals(focusId, favoritesOnly.groups.single().items.single().template.id)
    }

    @Test
    fun aSearchWithNoMatchesIsDistinguishableFromAnEmptyLibrary() {
        val state = ScreenBuilders.library(
            strings = strings, query = "zzzz-not-an-exercise", moduleFilter = null, onlyFavorites = false,
            favorites = emptySet(), custom = emptyList(), personalBests = emptyMap(), lastPlayed = emptyMap(),
        )
        assertTrue(state.isSearchEmpty)
        assertFalse(state.isEmptyQuery)
        assertEquals(0, state.totalItems)
    }

    @Test
    fun theBuilderRejectsAnInvalidConfigThroughTheDomainValidator() {
        // displayDurationMillis below the domain's minimum is not a valid exercise.
        val invalid = ExerciseConfig(displayDurationMillis = 10, rounds = 12)
        assertTrue("the fixture must actually be invalid", invalid.validate().isNotEmpty())
        val state = ScreenBuilders.builder(
            template = ExerciseTemplate.FOCUS_LOCK,
            name = "My drill",
            config = invalid,
            existingNames = emptyList(),
            existingCount = 0,
        )
        assertFalse(state.canSave)
        assertTrue(state.violations.isNotEmpty())
    }

    @Test
    fun theBuilderRejectsDuplicateNamesAndTheCustomExerciseLimit() {
        val valid = ExerciseTemplate.FOCUS_LOCK.defaultConfig()
        val duplicate = ScreenBuilders.builder(
            template = ExerciseTemplate.FOCUS_LOCK,
            name = "my drill",
            config = valid,
            existingNames = listOf("My Drill"),
            existingCount = 0,
        )
        assertFalse(duplicate.canSave)
        assertEquals("library.builder.duplicate_name", duplicate.nameErrorKey)

        val atLimit = ScreenBuilders.builder(
            template = ExerciseTemplate.FOCUS_LOCK,
            name = "Another",
            config = valid,
            existingNames = emptyList(),
            existingCount = BuilderState.MAX_CUSTOM_EXERCISES,
        )
        assertFalse(atLimit.canSave)
    }

    @Test
    fun theBuilderAcceptsAValidConfiguration() {
        val valid = ExerciseTemplate.FOCUS_LOCK.defaultConfig()
        val state = ScreenBuilders.builder(
            template = ExerciseTemplate.FOCUS_LOCK,
            name = "Evening drill",
            config = valid,
            existingNames = listOf("Morning drill"),
            existingCount = 3,
        )
        assertTrue(state.canSave)
        assertNull(state.nameErrorKey)
        assertTrue(state.violations.isEmpty())
    }

    @Test
    fun sessionRowsMapTheTemplateAndWhetherTheSessionCounts() {
        val done = ScreenMapper.sessionRow(record("a"))
        assertEquals(ExerciseTemplate.FOCUS_LOCK, done.template)
        assertTrue(done.counts)

        val aborted = ScreenMapper.sessionRow(record("b", status = SessionStatus.ABANDONED))
        assertFalse(aborted.counts)
    }

    @Test
    fun aRecordForAnUnknownExerciseStillRendersWithoutCrashing() {
        val unknown = record("weird").copy(exerciseId = "vision.deleted_exercise")
        val row = ScreenMapper.sessionRow(unknown)
        assertNull(row.template)
        assertEquals(Module.VISION, row.module)
    }

    @Test
    fun missionCardsUseStoredSessionsForProgress() {
        val todayKey = AnalyticsService.dayKey(now)
        val missions = com.neurox.domain.progression.Quests.dailyMissions(todayKey)
        val todaySessions = listOf(record("today", endedAt = now), record("today2", endedAt = now - 1_000))
        val cards = ScreenMapper.missionCards(missions, todaySessions, strings)
        assertEquals(missions.size, cards.size)
        val sessionsMission = cards.first { it.target == missions.first { m -> m.kind == com.neurox.domain.progression.Quests.MissionKind.COMPLETE_SESSIONS }.target }
        assertTrue(sessionsMission.current >= 2)
    }

    @Test
    fun anEmptyAnalyticsScreenAndAnEmptyHomeScreenExplainThemselves() {
        // Every empty state must carry real copy, never a blank box.
        for (key in listOf("home.empty.title", "home.empty.body", "analytics.empty.title", "analytics.empty.body")) {
            for (language in listOf("en", "fa")) {
                val text = Strings.of(language).raw(key)
                assertFalse("missing $key in $language", text == key)
                assertTrue("blank $key in $language", text.isNotBlank())
            }
        }
    }

    @Test
    fun recoveryHintAppearsOnlyAfterSeveralLongSessions() {
        val shortSessions = (1..3).map { record("short-$it", activeMillis = 60_000L, endedAt = now - it * 1_000L) }
        val shortHint = ScreenBuilders.home(
            profile, strings, shortSessions, emptySet(), true, false, now,
        )
        assertNull(shortHint.recoveryHintKey)

        val longSessions = (1..3).map { record("long-$it", activeMillis = 900_000L, endedAt = now - it * 1_000L) }
        val longHint = ScreenBuilders.home(
            profile, strings, longSessions, emptySet(), true, false, now,
        )
        assertEquals("home.recovery.reason", longHint.recoveryHintKey)
    }

    @Test
    fun moduleAccuracyIsComputedFromCountsNotFromAveragedPercentages() {
        // 1 correct out of 10 rounds, and 50 correct out of 50 rounds:
        // a mean of percentages would say 60%; counts say 85%.
        val small = record("small", correct = 1, incorrect = 9, rounds = 10, accuracy = 0.1)
        val large = record("large", correct = 50, incorrect = 0, rounds = 50, accuracy = 1.0,
            endedAt = now - 2_000L)
        val home = ScreenBuilders.home(
            profile, strings, listOf(small, large), emptySet(), true, false, now,
        )
        val vision = home.moduleCards.first { it.module == Module.VISION }
        assertEquals(51.0 / 60.0, vision.accuracy, 0.0001)
        assertEquals(2, vision.sessionCount)
    }

    @Test
    fun untouchedModulesAreMarkedAsHavingNoData() {
        val records = listOf(record("v1"), record("v2"))
        val home = ScreenBuilders.home(profile, strings, records, emptySet(), true, false, now)
        val reflex = home.moduleCards.first { it.module == Module.REFLEX }
        assertFalse(reflex.hasData)
        assertNull(reflex.lastTrainedMillis)
        val vision = home.moduleCards.first { it.module == Module.VISION }
        assertTrue(vision.hasData)
        assertNotNull(vision.lastTrainedMillis)
    }
}
