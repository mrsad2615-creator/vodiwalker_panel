package com.neurox.domain

import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.model.Module
import com.neurox.domain.progression.Achievement
import com.neurox.domain.progression.Achievements
import com.neurox.domain.progression.Levels
import com.neurox.domain.progression.ProgressionSnapshot
import com.neurox.domain.progression.Quests
import com.neurox.domain.progression.Recommender
import com.neurox.domain.progression.Streaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun levelOneStartsAtZeroXp() {
        assertEquals(1, Levels.levelForXp(0))
        assertEquals(0, Levels.xpNeededForLevel(1))
        assertEquals(100, Levels.xpNeededForLevel(2))
        assertEquals(300, Levels.xpNeededForLevel(3))
        assertEquals(600, Levels.xpNeededForLevel(4))
    }

    @Test
    fun levelBoundariesAreExact() {
        assertEquals(1, Levels.levelForXp(99))
        assertEquals(2, Levels.levelForXp(100))
        assertEquals(2, Levels.levelForXp(299))
        assertEquals(3, Levels.levelForXp(300))
    }

    @Test
    fun negativeXpIsTreatedAsLevelOne() {
        assertEquals(1, Levels.levelForXp(-500))
    }

    @Test
    fun progressWithinLevelIsBounded() {
        // Level 2 starts at 100 XP, level 3 at 300 XP, so 150 XP is a quarter in.
        assertEquals(0.25, Levels.progressToNext(150), 1e-9)
        assertTrue(Levels.progressToNext(0) in 0.0..1.0)
        assertTrue(Levels.progressToNext(50_000) in 0.0..1.0)
    }
}

class AchievementsTest {

    private fun snapshot(
        sessions: Int = 0,
        streak: Int = 0,
        perfect: Boolean = false,
        accuracy: Double = 0.0,
        modules: Set<Module> = emptySet(),
        difficulty: Int = 0,
        baseline: Boolean = false,
    ) = ProgressionSnapshot(sessions, streak, perfect, accuracy, modules, difficulty, baseline)

    @Test
    fun newProfileEarnsNothing() {
        assertTrue(Achievements.earned(snapshot()).isEmpty())
    }

    @Test
    fun firstSessionUnlocksTheFirstAchievement() {
        val earned = Achievements.earned(snapshot(sessions = 1))
        assertEquals(listOf(Achievement.FIRST_SESSION), earned)
    }

    @Test
    fun thresholdsAreInclusiveAndCumulative() {
        val earned = Achievements.earned(snapshot(sessions = 50, streak = 7))
        assertTrue(earned.contains(Achievement.SESSIONS_10))
        assertTrue(earned.contains(Achievement.SESSIONS_50))
        assertFalse(earned.contains(Achievement.SESSIONS_200))
        assertTrue(earned.contains(Achievement.STREAK_7))
        assertFalse(earned.contains(Achievement.STREAK_30))
    }

    @Test
    fun allFourModulesRequiresEveryModule() {
        assertFalse(Achievements.earned(snapshot(modules = setOf(Module.VISION, Module.REFLEX, Module.READ)))
            .contains(Achievement.ALL_FOUR_MODULES))
        assertTrue(
            Achievements.earned(snapshot(modules = Module.entries.toSet()))
                .contains(Achievement.ALL_FOUR_MODULES),
        )
    }

    @Test
    fun rewardsArePaidOncePerStableId() {
        val ids = setOf(Achievement.FIRST_SESSION.id, Achievement.FIRST_SESSION.id, Achievement.STREAK_3.id)
        assertEquals(Achievement.FIRST_SESSION.xpReward + Achievement.STREAK_3.xpReward, Achievements.rewardXp(ids))
    }

    @Test
    fun newlyEarnedExcludesAlreadyGrantedIds() {
        val previous = setOf(Achievement.FIRST_SESSION.id)
        val new = Achievements.newlyEarned(previous, snapshot(sessions = 10))
        assertFalse(new.contains(Achievement.FIRST_SESSION))
        assertTrue(new.contains(Achievement.SESSIONS_10))
    }

    @Test
    fun stableIdsResolve() {
        Achievement.entries.forEach {
            assertEquals(it, Achievement.fromId(it.id))
        }
        assertEquals(null, Achievement.fromId("nope"))
    }

    @Test
    fun snapshotIsBuiltFromStoredRecords() {
        val records = listOf(
            Records.of(id = "a", module = Module.VISION, accuracy = 0.99, difficulty = 4),
            Records.of(id = "b", module = Module.REFLEX, accuracy = 0.96),
            Records.of(id = "c", status = com.neurox.domain.model.SessionStatus.ABANDONED),
        )
        val built = Achievements.snapshotOf(records, longestStreakDays = 3, baselineCompleted = true)
        assertEquals(2, built.completedSessions)
        assertTrue(built.modulesTrained.containsAll(listOf(Module.VISION, Module.REFLEX)))
        assertEquals(4, built.highestDifficultyCompleted)
        assertTrue(built.baselineCompleted)
    }
}

class StreaksTest {

    @Test
    fun currentStreakCountsBackwardsFromToday() {
        val days = setOf(100L, 101L, 102L, 103L)
        assertEquals(4, Streaks.currentStreak(days, today = 103))
        // Nothing trained today: the streak still stands until yesterday, so it
        // must not be broken before the day ends.
        assertEquals(4, Streaks.currentStreak(days, today = 104))
        assertEquals(0, Streaks.currentStreak(days, today = 106))
        assertEquals(0, Streaks.currentStreak(days, today = 110))
        assertEquals(0, Streaks.currentStreak(emptySet(), today = 5))
    }

    @Test
    fun longestStreakIgnoresGaps() {
        val days = setOf(1L, 2L, 3L, 10L, 11L, 20L)
        assertEquals(3, Streaks.longestStreak(days))
        assertEquals(0, Streaks.longestStreak(emptySet()))
        assertEquals(1, Streaks.longestStreak(setOf(42L)))
    }
}

class QuestsTest {

    @Test
    fun dailyMissionsAreDeterministicForTheSameDay() {
        val first = Quests.dailyMissions(dayKey = 20_000)
        val second = Quests.dailyMissions(dayKey = 20_000)
        assertEquals(first, second)
        assertEquals(3, first.size)
        assertTrue(first.all { it.target > 0 && it.xpReward > 0 })
        assertTrue(first.any { it.module != null })
    }

    @Test
    fun missionsAreScopedToTheDay() {
        val a = Quests.dailyMissions(dayKey = 20_000)
        val b = Quests.dailyMissions(dayKey = 20_001)
        assertEquals(3, a.map { it.id }.toSet().size)
        // Mission ids embed the day, so a new day always starts with no progress.
        assertTrue(a.none { mission -> b.any { other -> other.id == mission.id } })
        assertTrue(a.all { it.id.contains("20000") })
    }

    @Test
    fun progressCountsOnlyCompletedSessionsFromToday() {
        val missions = Quests.dailyMissions(dayKey = 1)
        val sessions = listOf(
            Records.of(id = "a", activeMillis = 120_000),
            Records.of(id = "b", activeMillis = 60_000, status = com.neurox.domain.model.SessionStatus.ABANDONED),
        )
        val progress = Quests.progress(missions, sessions)
        val sessionsMission = progress.first { it.mission.kind == Quests.MissionKind.COMPLETE_SESSIONS }
        assertEquals(1, sessionsMission.current)
        val minutesMission = progress.first { it.mission.kind == Quests.MissionKind.TRAIN_MINUTES }
        assertEquals(2, minutesMission.current)
        assertTrue(progress.all { it.fraction in 0.0..1.0 })
    }

    @Test
    fun emptyDayShowsZeroProgressNotCompletion() {
        val progress = Quests.progress(Quests.dailyMissions(1), emptyList())
        assertTrue(progress.none { it.complete })
        assertTrue(progress.all { it.current == 0 })
    }
}

class RecommenderTest {

    @Test
    fun aNewProfileIsAskedToStart() {
        val suggestion = Recommender.suggest(emptyList(), Module.entries.toSet(), nowMillis = 0)
        assertEquals("home.recommendedReason.start", suggestion.reasonKey)
        assertNotNull(suggestion.template)
    }

    @Test
    fun untrainedModulesArePrioritized() {
        val history = Records.series(count = 6, exerciseId = ExerciseTemplate.FOCUS_LOCK.id, accuracy = 0.99)
        val suggestion = Recommender.suggest(history, setOf(Module.TYPE), nowMillis = 0)
        assertEquals("home.recommendedReason.weak_skill", suggestion.reasonKey)
        assertNotNull(suggestion.template)
    }

    @Test
    fun suggestionDifficultyComesFromTheLastSession() {
        val history = Records.series(count = 4, exerciseId = ExerciseTemplate.FOCUS_LOCK.id, difficulty = 4)
        val suggestion = Recommender.suggest(history, emptySet(), nowMillis = 0)
        assertTrue(suggestion.difficulty in 1..5)
        assertTrue(suggestion.minutes > 0)
    }
}
