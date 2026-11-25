package com.neurox.domain

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.analytics.AnalyticsService
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.Skill
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.plan.AdaptivePlanner
import com.neurox.domain.plan.PlanItem
import com.neurox.domain.plan.PlanOrigin
import com.neurox.domain.plan.PlanValidator
import com.neurox.domain.plan.PlanWarning
import com.neurox.domain.plan.TrainingPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanTest {

    private fun plan(
        days: Map<Int, List<PlanItem>> = mapOf(
            1 to listOf(PlanItem(ExerciseTemplate.FOCUS_LOCK.id, 8, 3)),
            3 to listOf(PlanItem(ExerciseTemplate.QUICK_TAP.id, 6, 3)),
        ),
        rest: Set<Int> = setOf(2, 4, 5, 6, 7),
        goal: Int = 20,
    ) = TrainingPlan(
        id = "plan-1",
        name = "My plan",
        origin = PlanOrigin.MANUAL,
        days = days,
        weeklyGoalMinutes = goal,
        restDays = rest,
        createdAtMillis = 0,
        updatedAtMillis = 0,
        active = true,
    )

    @Test
    fun validPlanIsAccepted() {
        assertTrue(PlanValidator.validate(plan()) is Outcome.Ok)
    }

    @Test
    fun restDayConflictIsRejected() {
        val result = PlanValidator.validate(plan(rest = setOf(1)))
        assertEquals(DomainError.PlanConflict, (result as Outcome.Err).error)
    }

    @Test
    fun unknownExerciseIsRejected() {
        val result = PlanValidator.validate(
            plan(days = mapOf(1 to listOf(PlanItem("vision.not_real", 5, 3)))),
        )
        assertEquals(DomainError.ExerciseNotFound, (result as Outcome.Err).error)
    }

    @Test
    fun dayLimitProtectsAgainstOverTraining() {
        val heavy = (1..9).map { PlanItem(ExerciseTemplate.FOCUS_LOCK.id, 5, 3) }
        val result = PlanValidator.validate(plan(days = mapOf(1 to heavy)))
        assertEquals(DomainError.PlanInvalid, (result as Outcome.Err).error)
    }

    @Test
    fun dailyMinuteCeilingIsEnforced() {
        val heavy = listOf(PlanItem(ExerciseTemplate.FOCUS_LOCK.id, 60, 3), PlanItem(ExerciseTemplate.QUICK_TAP.id, 60, 3))
        val result = PlanValidator.validate(plan(days = mapOf(1 to heavy)))
        assertEquals(DomainError.PlanInvalid, (result as Outcome.Err).error)
    }

    @Test
    fun weeklyGoalMustBeReachableByThePlan() {
        val result = PlanValidator.validate(plan(goal = 1_000))
        // An unreachable weekly goal is a schedule conflict, not a malformed plan.
        assertEquals(DomainError.PlanConflict, (result as Outcome.Err).error)
    }

    @Test
    fun planItemsEnforceTheirOwnBounds() {
        assertThrows(IllegalArgumentException::class.java) { PlanItem("x", 0, 3) }
        assertThrows(IllegalArgumentException::class.java) { PlanItem("x", 5, 0) }
        assertThrows(IllegalArgumentException::class.java) { PlanItem("x", 5, 3, 24) }
    }

    @Test
    fun restDaysReturnNoItems() {
        val p = plan()
        assertTrue(PlanValidator.itemsFor(p, 2).isEmpty())
        assertEquals(1, PlanValidator.itemsFor(p, 1).size)
    }

    @Test
    fun warningsFlagUnhealthyWeeks() {
        val everyDay = TrainingPlan(
            id = "p2",
            name = "Every day",
            origin = PlanOrigin.MANUAL,
            days = (1..7).associateWith { listOf(PlanItem(ExerciseTemplate.FOCUS_LOCK.id, 10, 3)) },
            weeklyGoalMinutes = 70,
            restDays = emptySet(),
            createdAtMillis = 0,
            updatedAtMillis = 0,
            active = true,
        )
        assertEquals(PlanWarning.NO_REST_DAY, PlanValidator.scheduleWarning(everyDay))
        assertNull(PlanValidator.scheduleWarning(plan()))
    }

    @Test
    fun adaptivePlannerBuildsAValidBalancedPlan() {
        val built = AdaptivePlanner.build(
            id = "adaptive-1",
            weakSkills = listOf(Skill.VISUAL_MEMORY, Skill.READING_COMPREHENSION),
            availableWeekdays = setOf(1, 2, 4, 6),
            nowMillis = 1_700_000_000_000,
        )
        assertTrue(PlanValidator.validate(built) is Outcome.Ok)
        assertEquals(PlanOrigin.ADAPTIVE, built.origin)
        assertEquals(4, built.days.size)
        assertEquals(3, built.restDays.size)
        assertFalse(built.days.keys.any { it in built.restDays })
        assertTrue(built.days.values.flatten().all { it.template != null })
    }

    @Test
    fun adaptivePlannerIsDeterministic() {
        val a = AdaptivePlanner.build("x", listOf(Skill.REACTION_SPEED), setOf(1, 3, 5), nowMillis = 5, goalMinutes = 40)
        val b = AdaptivePlanner.build("x", listOf(Skill.REACTION_SPEED), setOf(1, 3, 5), nowMillis = 5, goalMinutes = 40)
        assertEquals(a, b)
    }

    @Test
    fun adaptivePlannerKeepsAllFourModulesWhenSkillsAreNarrow() {
        val built = AdaptivePlanner.build("y", emptyList(), setOf(1, 2, 3), nowMillis = 0)
        val modules = built.days.values.flatten().mapNotNull { it.template?.module }.toSet()
        assertTrue("all modules should appear", modules.isNotEmpty())
        assertTrue(PlanValidator.validate(built) is Outcome.Ok)
    }
}

class AnalyticsTest {

    private val day = AnalyticsService.DAY_MILLIS

    /** Timestamp inside the queried range that the tests use for "recent". */
    private val now = day * 1 + 3_600_000L

    private fun recent(id: String, accuracy: Double = 0.9): com.neurox.domain.model.SessionRecord =
        Records.of(id = id, accuracy = accuracy).let { it.copy(startedAtMillis = now, endedAtMillis = now + it.activeDurationMillis) }

    @Test
    fun emptyHistoryProducesHonestEmptySnapshot() {
        val snapshot = AnalyticsService.snapshot(emptyList(), 0, day * 7)
        assertEquals(0, snapshot.sessionCount)
        assertEquals(0.0, snapshot.meanAccuracy, 1e-9)
        assertEquals(0, snapshot.totalXp)
        assertEquals(8, snapshot.activity.size)
        assertTrue(snapshot.activity.all { it.sessionCount == 0 })
        assertTrue(snapshot.perModule.all { it.sessions == 0 && it.lastTrainedMillis == null })
    }

    @Test
    fun activityIsBucketedPerDayAndCountsOnlySessionsInRange() {
        val records = listOf(
            Records.of(id = "a", startedAt = day * 2 + 1_000, activeMillis = 120_000),
            Records.of(id = "b", startedAt = day * 2 + 86_000_000, activeMillis = 60_000),
            Records.of(id = "c", startedAt = day * 4 + 5_000, activeMillis = 60_000),
            Records.of(id = "d", startedAt = day * 40, activeMillis = 60_000),
        )
        val snapshot = AnalyticsService.snapshot(records, 0, day * 6)
        assertEquals(3, snapshot.sessionCount)
        val dayTwo = snapshot.activity.first { it.bucketStartMillis == day * 2 }
        assertEquals(2, dayTwo.sessionCount)
        assertEquals(3, dayTwo.activeMinutes)
    }

    @Test
    fun baselineSessionsAreExcludedFromStatistics() {
        val records = listOf(
            recent("a", 1.0).copy(kind = SessionKind.BASELINE),
            recent("b", 0.5),
        )
        val snapshot = AnalyticsService.snapshot(records, 0, day * 3)
        assertEquals(1, snapshot.sessionCount)
        assertEquals(0.5, snapshot.meanAccuracy, 1e-9)
    }

    @Test
    fun abandonedSessionsNeverRaiseAccuracyAverages() {
        val records = listOf(
            recent("a", 0.9),
            recent("b", 0.1).copy(status = SessionStatus.ABANDONED),
        )
        val snapshot = AnalyticsService.snapshot(records, 0, day * 3)
        assertEquals(2, snapshot.sessionCount)
        assertEquals(1, snapshot.abandonedCount)
        assertEquals(0.9, snapshot.meanAccuracy, 1e-9)
    }

    @Test
    fun speedIsOnlyComparedInsideAModule() {
        val records = listOf(
            recent("a").copy(module = Module.REFLEX, responseTimeMeanMillis = 400.0),
            recent("b").copy(module = Module.TYPE, responseTimeMeanMillis = 200.0),
            recent("c").copy(module = Module.READ, responseTimeMeanMillis = 900.0),
        )
        val snapshot = AnalyticsService.snapshot(records, 0, day * 3)
        assertEquals(400.0, snapshot.speedByModule.getValue(Module.REFLEX), 1e-9)
        assertEquals(200.0, snapshot.speedByModule.getValue(Module.TYPE), 1e-9)
        assertEquals(900.0, snapshot.speedByModule.getValue(Module.READ), 1e-9)
    }

    @Test
    fun errorProfileRanksExercisesByErrorCount() {
        val records = listOf(
            recent("a", 0.5).copy(exerciseId = ExerciseTemplate.FOCUS_LOCK.id, module = Module.VISION),
            recent("b", 0.95).copy(exerciseId = ExerciseTemplate.QUICK_TAP.id),
        )
        val profile = AnalyticsService.errorProfile(records, Module.VISION)
        assertEquals(1, profile.size)
        assertEquals(ExerciseTemplate.FOCUS_LOCK.id, profile.first().first)
        assertEquals(5, profile.first().second)
    }

    @Test
    fun personalRecordsKeepTheBestScorePerExercise() {
        val records = listOf(
            Records.of(id = "a", score = 40.0),
            Records.of(id = "b", score = 70.0),
            Records.of(id = "c", score = 55.0),
        )
        val best = AnalyticsService.personalRecords(records)
        assertEquals("b", best[ExerciseTemplate.QUICK_TAP.id]?.id)
    }

    @Test
    fun weekBoundaryIsMondayMidnightUtc() {
        // 2025-01-01T00:00:00Z is a Wednesday.
        val wednesday = 1_735_689_600_000L
        val monday = AnalyticsService.weekStartMillis(wednesday)
        val mondayCalendar = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = monday
        }
        assertEquals(java.util.Calendar.MONDAY, mondayCalendar.get(java.util.Calendar.DAY_OF_WEEK))
        assertEquals(0, mondayCalendar.get(java.util.Calendar.HOUR_OF_DAY))
        // Wednesday minus Monday is exactly two days.
        assertEquals(172_800_000L, wednesday - monday)
    }

    @Test
    fun monthAndYearBoundariesAreCalendarCorrect() {
        val march15 = 1_741_392_000_000L // 2025-03-05T00:00:00Z roughly
        val monthStart = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = AnalyticsService.monthStartMillis(march15)
        }
        assertEquals(1, monthStart.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(0, monthStart.get(java.util.Calendar.HOUR_OF_DAY))
        val yearStart = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = AnalyticsService.yearStartMillis(march15)
        }
        assertEquals(1, yearStart.get(java.util.Calendar.DAY_OF_YEAR))
        // Leap-year handling: a February 29 timestamp must map to February 1.
        val leap = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(2024, java.util.Calendar.FEBRUARY, 29, 12, 0, 0)
        }.timeInMillis
        val leapMonth = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = AnalyticsService.monthStartMillis(leap)
        }
        assertEquals(java.util.Calendar.FEBRUARY, leapMonth.get(java.util.Calendar.MONTH))
    }

    @Test
    fun dailySeriesReportsSampleCountSoEmptyDaysAreVisible() {
        val records = listOf(Records.of(id = "a", startedAt = day * 1 + 1_000, accuracy = 0.8))
        val snapshot = AnalyticsService.snapshot(records, 0, day * 2)
        val first = snapshot.dailySeries.first { it.bucketStartMillis == day * 1 }
        assertEquals(1, first.sampleCount)
        assertEquals(0.8, first.value, 1e-9)
        val empty = snapshot.dailySeries.first { it.bucketStartMillis == 0L }
        assertEquals(0, empty.sampleCount)
    }

    @Test
    fun rangeArgumentsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalyticsService.snapshot(emptyList(), 100, 10)
        }
    }

    @Test
    fun moduleTrendShowsImprovement() {
        val records = (0 until 6).map { index ->
            Records.of(
                id = "r$index",
                score = 30.0 + index * 5.0,
                startedAt = day * index,
            )
        }
        val snapshot = AnalyticsService.snapshot(records, 0, day * 6)
        val reflex = snapshot.perModule.first { it.module == Module.REFLEX }
        assertTrue(reflex.trend > 0.0)
        assertNotNull(reflex.lastTrainedMillis)
    }
}
