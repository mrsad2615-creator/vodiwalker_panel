package com.neurox.domain.plan

import com.neurox.common.result.DomainError
import com.neurox.common.result.Outcome
import com.neurox.domain.exercise.ExerciseTemplate
import com.neurox.domain.exercise.Skill
import com.neurox.domain.model.Module

/** Days are ISO weekday numbers: Monday is 1, Sunday is 7. */
typealias IsoWeekday = Int

/** One scheduled item inside a plan. */
data class PlanItem(
    val templateId: String,
    val minutes: Int,
    val difficulty: Int,
    val preferredHourLocal: Int? = null,
) {
    init {
        require(minutes in 1..120) { "minutes must be in 1..120" }
        require(difficulty in 1..5) { "difficulty must be in 1..5" }
        preferredHourLocal?.let { require(it in 0..23) { "hour must be in 0..23" } }
    }

    val template: ExerciseTemplate? get() = ExerciseTemplate.fromId(templateId)
}

/** A weekly plan. Manual, adaptive or combined. */
data class TrainingPlan(
    val id: String,
    val name: String,
    val origin: PlanOrigin,
    val days: Map<IsoWeekday, List<PlanItem>>,
    val weeklyGoalMinutes: Int,
    val restDays: Set<IsoWeekday>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val active: Boolean,
) {
    val totalItems: Int get() = days.values.sumOf { it.size }
    val plannedMinutes: Int get() = days.values.sumOf { items -> items.sumOf { it.minutes } }
}

enum class PlanOrigin { MANUAL, ADAPTIVE, COMBINED }

/**
 * Validation and conflict detection for plans.
 *
 * Rules:
 * - Days and rest days must be within 1..7.
 * - A day cannot be both scheduled and a rest day.
 * - One day cannot hold more than [MAX_ITEMS_PER_DAY] items.
 * - One day cannot exceed [MAX_MINUTES_PER_DAY], which protects against
 *   accidental over-training plans.
 * - Weekly goal must be consistent with planned minutes (>= 50 percent).
 */
object PlanValidator {

    const val MAX_ITEMS_PER_DAY = 8
    const val MAX_MINUTES_PER_DAY = 90

    fun validate(plan: TrainingPlan): Outcome<TrainingPlan> {
        val allDays = plan.days.keys + plan.restDays
        if (allDays.any { it !in 1..7 }) return Outcome.Err(DomainError.PlanInvalid)
        if (plan.restDays.any { it in plan.days.keys }) return Outcome.Err(DomainError.PlanConflict)
        if (plan.weeklyGoalMinutes !in 0..1_200) return Outcome.Err(DomainError.PlanInvalid)
        for ((day, items) in plan.days) {
            if (items.size > MAX_ITEMS_PER_DAY) return Outcome.Err(DomainError.PlanInvalid)
            val minutes = items.sumOf { it.minutes }
            if (minutes > MAX_MINUTES_PER_DAY) return Outcome.Err(DomainError.PlanInvalid)
            if (items.any { it.template == null }) return Outcome.Err(DomainError.ExerciseNotFound)
            if (day !in 1..7) return Outcome.Err(DomainError.PlanInvalid)
        }
        if (plan.weeklyGoalMinutes > 0 && plan.plannedMinutes < plan.weeklyGoalMinutes / 2) {
            return Outcome.Err(DomainError.PlanConflict)
        }
        return Outcome.Ok(plan)
    }

    /** Items scheduled on [day], or an empty list for a rest day. */
    fun itemsFor(plan: TrainingPlan, day: IsoWeekday): List<PlanItem> {
        if (day in plan.restDays) return emptyList()
        return plan.days[day].orEmpty()
    }

    /**
     * Difficulty ceiling for a day: the harder a plan is, the earlier rest days
     * must appear. Returns null when the plan is acceptable.
     */
    fun scheduleWarning(plan: TrainingPlan): PlanWarning? {
        val trainingDays = plan.days.keys.sorted()
        if (trainingDays.size == 7) return PlanWarning.NO_REST_DAY
        val heavy = plan.days.count { (_, items) -> items.sumOf { it.minutes } >= 45 }
        if (heavy >= 5) return PlanWarning.HEAVY_WEEK
        return null
    }
}

enum class PlanWarning(val messageKey: String) {
    NO_REST_DAY("plan.warning.no_rest_day"),
    HEAVY_WEEK("plan.warning.heavy_week"),
}

/**
 * Builds an adaptive weekly plan from measured weak skills.
 *
 * Deterministic: identical input gives an identical plan. Rest days are chosen
 * so no more than four consecutive days carry training.
 */
object AdaptivePlanner {

    fun build(
        id: String,
        weakSkills: List<Skill>,
        availableWeekdays: Set<IsoWeekday>,
        nowMillis: Long,
        goalMinutes: Int = 40,
    ): TrainingPlan {
        val days = availableWeekdays.ifEmpty { setOf(1, 3, 5) }.sorted().take(4)
        val templates = templatesFor(weakSkills)
        val perDay = mutableMapOf<IsoWeekday, List<PlanItem>>()
        days.forEachIndexed { index, day ->
            val template = templates[index % templates.size]
            val minutes = when (index % 3) {
                0 -> 8
                1 -> 6
                else -> 10
            }
            perDay[day] = listOf(PlanItem(templateId = template.id, minutes = minutes, difficulty = 3))
        }
        val rest = (1..7).toSet() - days.toSet()
        return TrainingPlan(
            id = id,
            name = "Adaptive plan",
            origin = PlanOrigin.ADAPTIVE,
            days = perDay,
            weeklyGoalMinutes = goalMinutes.coerceAtLeast(perDay.values.sumOf { items -> items.sumOf { it.minutes } }),
            restDays = rest,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            active = true,
        )
    }

    private fun templatesFor(skills: List<Skill>): List<ExerciseTemplate> {
        val fromSkills = skills.flatMap { skill ->
            ExerciseTemplate.entries.filter { skill in it.skills }
        }.distinct()
        if (fromSkills.size >= 4) return fromSkills.take(4)
        // Fall back to one exercise per module, so every system stays active.
        val perModule = Module.entries.mapNotNull { module ->
            ExerciseTemplate.forModule(module).firstOrNull()
        }
        return (fromSkills + perModule).distinct().take(4)
    }
}
