package com.neurox.data.map

import com.neurox.common.json.JsonParser
import com.neurox.common.json.JsonWriter
import com.neurox.common.json.asIntOrDefault
import com.neurox.common.json.asObj
import com.neurox.common.json.asStringOrDefault
import com.neurox.common.json.jsonArray
import com.neurox.common.json.jsonObject
import com.neurox.data.db.AchievementEntity
import com.neurox.data.db.CheckpointEntity
import com.neurox.data.db.CustomExerciseEntity
import com.neurox.data.db.PlanEntity
import com.neurox.data.db.ProfileEntity
import com.neurox.data.db.SessionEntity
import com.neurox.data.db.SettingEntity
import com.neurox.domain.analytics.AnalyticsService
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.model.Module
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.plan.PlanItem
import com.neurox.domain.plan.PlanOrigin
import com.neurox.domain.plan.TrainingPlan
import com.neurox.domain.repository.CustomExercise
import com.neurox.domain.vault.VaultMappers

/**
 * Entity <-> domain mapping.
 *
 * INVARIANTS
 * - Unknown enum values are read as a safe default instead of throwing, so a newer
 *   file cannot brick an older build. The domain validates the result.
 * - Mappers are pure, so they are unit-tested without a database.
 */
object Mappers {

    fun profile(entity: ProfileEntity): ProfileSummary = ProfileSummary(
        id = entity.id,
        displayName = entity.displayName,
        isGuest = entity.isGuest,
        avatarId = entity.avatarId,
        createdAtMillis = entity.createdAtMillis,
        hasPin = entity.pinHash != null,
        themeId = entity.themeId,
        languageTag = entity.languageTag,
        active = entity.active,
    )

    fun sessionEntity(record: SessionRecord): SessionEntity = SessionEntity(
        id = record.id,
        profileId = record.profileId,
        exerciseId = record.exerciseId,
        module = record.module.id,
        kind = record.kind.name,
        status = record.status.name,
        startedAtMillis = record.startedAtMillis,
        endedAtMillis = record.endedAtMillis,
        activeDurationMillis = record.activeDurationMillis,
        difficulty = record.difficulty,
        roundCount = record.roundCount,
        correctCount = record.correctCount,
        incorrectCount = record.incorrectCount,
        missedCount = record.missedCount,
        prematureCount = record.prematureCount,
        responseTimeMeanMillis = record.responseTimeMeanMillis,
        responseTimeMedianMillis = record.responseTimeMedianMillis,
        responseTimeStdevMillis = record.responseTimeStdevMillis,
        bestResponseMillis = record.bestResponseMillis,
        accuracy = record.accuracy,
        score = record.score,
        xpAwarded = record.xpAwarded,
        dayKey = AnalyticsService.dayKey(record.startedAtMillis),
        schemaVersion = record.schemaVersion,
    )

    fun session(entity: SessionEntity): SessionRecord = SessionRecord(
        id = entity.id,
        profileId = entity.profileId,
        exerciseId = entity.exerciseId,
        module = Module.fromId(entity.module) ?: Module.VISION,
        kind = enumOrDefault(entity.kind, SessionKind.TRAINING),
        status = enumOrDefault(entity.status, SessionStatus.ABANDONED),
        startedAtMillis = entity.startedAtMillis,
        endedAtMillis = entity.endedAtMillis,
        activeDurationMillis = entity.activeDurationMillis,
        difficulty = entity.difficulty.coerceIn(1, 5),
        roundCount = entity.roundCount,
        correctCount = entity.correctCount,
        incorrectCount = entity.incorrectCount,
        missedCount = entity.missedCount,
        prematureCount = entity.prematureCount,
        responseTimeMeanMillis = entity.responseTimeMeanMillis,
        responseTimeMedianMillis = entity.responseTimeMedianMillis,
        responseTimeStdevMillis = entity.responseTimeStdevMillis,
        bestResponseMillis = entity.bestResponseMillis,
        accuracy = entity.accuracy.coerceIn(0.0, 1.0),
        score = entity.score,
        xpAwarded = entity.xpAwarded,
        schemaVersion = entity.schemaVersion,
    )

    fun exercise(entity: CustomExerciseEntity): CustomExercise = CustomExercise(
        id = entity.id,
        profileId = entity.profileId,
        name = entity.name,
        module = Module.fromId(entity.module) ?: Module.VISION,
        templateId = entity.templateId,
        difficulty = entity.difficulty,
        description = entity.description,
        config = runCatching { VaultMappers.configFromJson(entity.configJson) }
            .getOrDefault(ExerciseConfig()),
        createdAtMillis = entity.createdAtMillis,
        updatedAtMillis = entity.updatedAtMillis,
    )

    fun exerciseEntity(exercise: CustomExercise): CustomExerciseEntity = CustomExerciseEntity(
        id = exercise.id,
        profileId = exercise.profileId,
        name = exercise.name,
        module = exercise.module.id,
        templateId = exercise.templateId,
        difficulty = exercise.difficulty,
        description = exercise.description,
        configJson = JsonWriter.write(VaultMappers.configToJson(exercise.config)),
        createdAtMillis = exercise.createdAtMillis,
        updatedAtMillis = exercise.updatedAtMillis,
    )

    fun achievement(profileId: String, award: Pair<String, Long>): AchievementEntity =
        AchievementEntity(profileId = profileId, achievementId = award.first, earnedAtMillis = award.second)

    fun setting(profileId: String, key: String, value: String): SettingEntity =
        SettingEntity(profileId = profileId, key = key, value = value)

    fun checkpoint(profileId: String, sessionId: String, exerciseId: String, now: Long, payload: String): CheckpointEntity =
        CheckpointEntity(
            profileId = profileId,
            sessionId = sessionId,
            exerciseId = exerciseId,
            createdAtMillis = now,
            updatedAtMillis = now,
            payloadJson = payload,
        )

    fun planEntity(profileId: String, plan: TrainingPlan): PlanEntity = PlanEntity(
        id = plan.id,
        profileId = profileId,
        name = plan.name,
        origin = plan.origin.name,
        weeklyGoalMinutes = plan.weeklyGoalMinutes,
        restDays = JsonWriter.write(jsonArray(plan.restDays.sorted().map { com.neurox.common.json.JsonValue.Num(it.toDouble()) })),
        daysJson = JsonWriter.write(
            jsonArray(
                plan.days.toSortedMap().map { (day, items) ->
                    jsonObject {
                        "day" integer day
                        "items" array items.map { item ->
                            jsonObject {
                                "templateId" string item.templateId
                                "minutes" integer item.minutes
                                "difficulty" integer item.difficulty
                                "hour" value (
                                    item.preferredHourLocal?.let { com.neurox.common.json.JsonValue.Num(it.toDouble()) }
                                        ?: com.neurox.common.json.JsonValue.Null
                                    )
                            }
                        }
                    }
                },
            ),
        ),
        createdAtMillis = plan.createdAtMillis,
        updatedAtMillis = plan.updatedAtMillis,
        active = plan.active,
    )

    fun plan(entity: PlanEntity): TrainingPlan {
        val days = runCatching {
            JsonParser.parse(entity.daysJson).let { parsed ->
                val list = (parsed as? com.neurox.common.json.JsonValue.Arr)?.items ?: emptyList()
                list.mapNotNull { dayValue ->
                    val fields = dayValue.asObj()
                    val day = fields["day"]?.asIntOrDefault(0) ?: 0
                    if (day !in 1..7) return@mapNotNull null
                    val items = (fields["items"] as? com.neurox.common.json.JsonValue.Arr)?.items.orEmpty()
                        .mapNotNull { itemValue ->
                            val itemFields = itemValue.asObj()
                            val template = itemFields["templateId"]?.asStringOrDefault("") ?: ""
                            if (template.isBlank()) return@mapNotNull null
                            PlanItem(
                                templateId = template,
                                minutes = (itemFields["minutes"]?.asIntOrDefault(5) ?: 5).coerceIn(1, 120),
                                difficulty = (itemFields["difficulty"]?.asIntOrDefault(3) ?: 3).coerceIn(1, 5),
                                preferredHourLocal = itemFields["hour"]?.asIntOrDefault(-1)?.takeIf { it in 0..23 },
                            )
                        }
                    day to items
                }.toMap()
            }
        }.getOrDefault(emptyMap())
        val restDays = runCatching {
            JsonParser.parse(entity.restDays).let { parsed ->
                ((parsed as? com.neurox.common.json.JsonValue.Arr)?.items ?: emptyList())
                    .mapNotNull { it.asIntOrDefault(0).takeIf { day -> day in 1..7 } }
                    .toSet()
            }
        }.getOrDefault(emptySet())
        return TrainingPlan(
            id = entity.id,
            name = entity.name,
            origin = enumOrDefault(entity.origin, PlanOrigin.MANUAL),
            days = days,
            weeklyGoalMinutes = entity.weeklyGoalMinutes,
            restDays = restDays,
            createdAtMillis = entity.createdAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
            active = entity.active,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}
