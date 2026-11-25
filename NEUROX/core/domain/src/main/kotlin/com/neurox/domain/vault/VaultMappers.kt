package com.neurox.domain.vault

import com.neurox.common.json.JsonParser
import com.neurox.common.json.JsonValue
import com.neurox.common.json.JsonWriter
import com.neurox.common.json.asArr
import com.neurox.common.json.asBoolOrDefault
import com.neurox.common.json.asDoubleOrDefault
import com.neurox.common.json.asIntOrDefault
import com.neurox.common.json.asLongOrDefault
import com.neurox.common.json.asObj
import com.neurox.common.json.asStringOrDefault
import com.neurox.common.json.asStringOrNull
import com.neurox.common.json.jsonArray
import com.neurox.common.json.jsonObject
import com.neurox.common.json.requiredArray
import com.neurox.common.json.requiredBool
import com.neurox.common.json.requiredDouble
import com.neurox.common.json.requiredInt
import com.neurox.common.json.requiredLong
import com.neurox.common.json.requiredString
import com.neurox.domain.exercise.ExerciseConfig
import com.neurox.domain.exercise.MovementDirection
import com.neurox.domain.exercise.StimulusMode
import com.neurox.domain.exercise.TypingLanguage
import com.neurox.domain.model.Module
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord
import com.neurox.domain.model.SessionStatus
import com.neurox.domain.plan.PlanItem
import com.neurox.domain.plan.PlanOrigin
import com.neurox.domain.plan.TrainingPlan

/**
 * Deterministic JSON mapping for every persisted domain object.
 *
 * CONTRACT
 * - `toJson` always writes every field that has no default, in schema order.
 * - `fromJson` uses explicit defaults for optional fields, so an older file still
 *   loads after a field is added (forward compatibility).
 * - A malformed required field throws [com.neurox.common.json.JsonParseException].
 *   The vault import path converts that exception to a domain error.
 */
object VaultMappers {

    // ---- ExerciseConfig ----------------------------------------------------

    fun configToJson(config: ExerciseConfig): JsonValue.Obj = jsonObject {
        "targetCount" integer config.targetCount
        "targetSizeDp" integer config.targetSizeDp
        "targetSpeedDpPerSecond" integer config.targetSpeedDpPerSecond
        "displayDurationMillis" integer config.displayDurationMillis
        "rounds" integer config.rounds
        "sessionDurationSeconds" integer config.sessionDurationSeconds
        "errorAllowance" integer config.errorAllowance
        "movementDirection" string config.movementDirection.name
        "stimulusMode" string config.stimulusMode.name
        "showLabels" bool config.showLabels
        "readingTextId" value (config.readingTextId?.let { JsonValue.Str(it) } ?: JsonValue.Null)
        "typingTextId" value (config.typingTextId?.let { JsonValue.Str(it) } ?: JsonValue.Null)
        "typingLanguage" string config.typingLanguage.name
        "timerVisible" bool config.timerVisible
        "requireComprehension" bool config.requireComprehension
    }

    fun configFromJson(text: String): ExerciseConfig = configFromJson(JsonParser.parse(text))

    fun configFromJson(value: JsonValue): ExerciseConfig {
        val o = value.asObj()
        return ExerciseConfig(
            targetCount = o["targetCount"]?.asIntOrDefault(1) ?: 1,
            targetSizeDp = o["targetSizeDp"]?.asIntOrDefault(48) ?: 48,
            targetSpeedDpPerSecond = o["targetSpeedDpPerSecond"]?.asIntOrDefault(60) ?: 60,
            displayDurationMillis = o["displayDurationMillis"]?.asIntOrDefault(800) ?: 800,
            rounds = o["rounds"]?.asIntOrDefault(12) ?: 12,
            sessionDurationSeconds = o["sessionDurationSeconds"]?.asIntOrDefault(120) ?: 120,
            errorAllowance = o["errorAllowance"]?.asIntOrDefault(3) ?: 3,
            movementDirection = o["movementDirection"]?.asStringOrNull()
                ?.let { runCatching { MovementDirection.valueOf(it) }.getOrNull() }
                ?: MovementDirection.ANY,
            stimulusMode = o["stimulusMode"]?.asStringOrNull()
                ?.let { runCatching { StimulusMode.valueOf(it) }.getOrNull() }
                ?: StimulusMode.VISUAL,
            showLabels = o["showLabels"]?.asBoolOrDefault(true) ?: true,
            readingTextId = o["readingTextId"]?.asStringOrNull(),
            typingTextId = o["typingTextId"]?.asStringOrNull(),
            typingLanguage = o["typingLanguage"]?.asStringOrNull()
                ?.let { runCatching { TypingLanguage.valueOf(it) }.getOrNull() }
                ?: TypingLanguage.EN,
            timerVisible = o["timerVisible"]?.asBoolOrDefault(true) ?: true,
            requireComprehension = o["requireComprehension"]?.asBoolOrDefault(false) ?: false,
        )
    }

    // ---- SessionRecord -----------------------------------------------------

    fun sessionToJson(record: SessionRecord): JsonValue.Obj = jsonObject {
        "id" string record.id
        "profileId" string record.profileId
        "exerciseId" string record.exerciseId
        "module" string record.module.id
        "kind" string record.kind.name
        "status" string record.status.name
        "startedAtMillis" long record.startedAtMillis
        "endedAtMillis" long record.endedAtMillis
        "activeDurationMillis" long record.activeDurationMillis
        "difficulty" integer record.difficulty
        "roundCount" integer record.roundCount
        "correctCount" integer record.correctCount
        "incorrectCount" integer record.incorrectCount
        "missedCount" integer record.missedCount
        "prematureCount" integer record.prematureCount
        "responseTimeMeanMillis" number record.responseTimeMeanMillis
        "responseTimeMedianMillis" number record.responseTimeMedianMillis
        "responseTimeStdevMillis" number record.responseTimeStdevMillis
        "bestResponseMillis" long record.bestResponseMillis
        "accuracy" number record.accuracy
        "score" number record.score
        "xpAwarded" integer record.xpAwarded
        "schemaVersion" integer record.schemaVersion
    }

    fun sessionFromJson(value: JsonValue): SessionRecord {
        val o = value.asObj()
        return SessionRecord(
            id = o.requiredString("id"),
            profileId = o.requiredString("profileId"),
            exerciseId = o.requiredString("exerciseId"),
            module = Module.fromId(o.requiredString("module"))
                ?: throw com.neurox.common.json.JsonParseException("unknown module", -1),
            kind = SessionKind.valueOf(o.requiredString("kind")),
            status = SessionStatus.valueOf(o.requiredString("status")),
            startedAtMillis = o.requiredLong("startedAtMillis"),
            endedAtMillis = o.requiredLong("endedAtMillis"),
            activeDurationMillis = o.requiredLong("activeDurationMillis"),
            difficulty = o.requiredInt("difficulty"),
            roundCount = o.requiredInt("roundCount"),
            correctCount = o.requiredInt("correctCount"),
            incorrectCount = o.requiredInt("incorrectCount"),
            missedCount = o.requiredInt("missedCount"),
            prematureCount = o.requiredInt("prematureCount"),
            responseTimeMeanMillis = o.requiredDouble("responseTimeMeanMillis"),
            responseTimeMedianMillis = o.requiredDouble("responseTimeMedianMillis"),
            responseTimeStdevMillis = o.requiredDouble("responseTimeStdevMillis"),
            bestResponseMillis = o.requiredLong("bestResponseMillis"),
            accuracy = o.requiredDouble("accuracy"),
            score = o.requiredDouble("score"),
            xpAwarded = o.requiredInt("xpAwarded"),
            schemaVersion = o["schemaVersion"]?.asIntOrDefault(SessionRecord.CURRENT_SCHEMA_VERSION)
                ?: SessionRecord.CURRENT_SCHEMA_VERSION,
        )
    }

    // ---- Plan --------------------------------------------------------------

    fun planToJson(plan: TrainingPlan): JsonValue.Obj = jsonObject {
        "id" string plan.id
        "name" string plan.name
        "origin" string plan.origin.name
        "weeklyGoalMinutes" integer plan.weeklyGoalMinutes
        "restDays" integers plan.restDays.sorted()
        "days" array plan.days.toSortedMap().map { (day, items) ->
            jsonObject {
                "day" integer day
                "items" array items.map { item ->
                    jsonObject {
                        "templateId" string item.templateId
                        "minutes" integer item.minutes
                        "difficulty" integer item.difficulty
                        "preferredHourLocal" value (
                            item.preferredHourLocal?.let { JsonValue.Num(it.toDouble()) } ?: JsonValue.Null
                            )
                    }
                }
            }
        }
        "createdAtMillis" long plan.createdAtMillis
        "updatedAtMillis" long plan.updatedAtMillis
        "active" bool plan.active
    }

    fun planFromJson(value: JsonValue): TrainingPlan {
        val o = value.asObj()
        val days = LinkedHashMap<Int, List<PlanItem>>()
        for (dayValue in o.requiredArray("days")) {
            val dayObj = dayValue.asObj()
            val day = dayObj.requiredInt("day")
            val items = dayObj.requiredArray("items").map { itemValue ->
                val item = itemValue.asObj()
                PlanItem(
                    templateId = item.requiredString("templateId"),
                    minutes = item.requiredInt("minutes"),
                    difficulty = item.requiredInt("difficulty"),
                    preferredHourLocal = item["preferredHourLocal"]?.asIntOrDefault(-1)?.takeIf { it >= 0 },
                )
            }
            days[day] = items
        }
        return TrainingPlan(
            id = o.requiredString("id"),
            name = o.requiredString("name"),
            origin = PlanOrigin.valueOf(o.requiredString("origin")),
            days = days,
            weeklyGoalMinutes = o.requiredInt("weeklyGoalMinutes"),
            restDays = o.requiredArray("restDays").mapNotNull { it.asIntOrDefault(-1).takeIf { d -> d > 0 } }.toSet(),
            createdAtMillis = o.requiredLong("createdAtMillis"),
            updatedAtMillis = o.requiredLong("updatedAtMillis"),
            active = o.requiredBool("active"),
        )
    }

    // ---- Vault payload -----------------------------------------------------

    fun payloadToJson(payload: VaultPayload): JsonValue.Obj = jsonObject {
        "schemaVersion" integer payload.schemaVersion
        "profileId" string payload.profileId
        "profileName" string payload.profileName
        "exportedAtMillis" long payload.exportedAtMillis
        "profiles" array payload.profiles.map {
            jsonObject {
                "id" string it.id
                "displayName" string it.displayName
                "isGuest" bool it.isGuest
                "avatarId" integer it.avatarId
                "createdAtMillis" long it.createdAtMillis
                "themeId" string it.themeId
                "languageTag" string it.languageTag
            }
        }
        "sessions" array payload.sessions.map { it.toJson() }
        "exercises" array payload.exercises.map {
            jsonObject {
                "id" string it.id
                "name" string it.name
                "module" string it.module
                "templateId" string it.templateId
                "difficulty" integer it.difficulty
                "description" string it.description
                "configJson" string it.configJson
                "createdAtMillis" long it.createdAtMillis
                "updatedAtMillis" long it.updatedAtMillis
            }
        }
        "achievements" array payload.achievements.map {
            jsonObject {
                "achievementId" string it.achievementId
                "earnedAtMillis" long it.earnedAtMillis
            }
        }
        "plans" array payload.plans.map {
            jsonObject {
                "id" string it.id
                "name" string it.name
                "origin" string it.origin
                "weeklyGoalMinutes" integer it.weeklyGoalMinutes
                "restDays" integers it.restDays
                "daysJson" string it.daysJson
                "createdAtMillis" long it.createdAtMillis
                "updatedAtMillis" long it.updatedAtMillis
                "active" bool it.active
            }
        }
        "settings" value jsonObject {
            payload.settings.toSortedMap().forEach { (k, v) -> k string v }
        }
        "checksum" string payload.checksum
    }

    fun payloadFromJson(value: JsonValue): VaultPayload {
        val o = value.asObj()
        return VaultPayload(
            schemaVersion = o["schemaVersion"]?.asIntOrDefault(1) ?: 1,
            profileId = o.requiredString("profileId"),
            profileName = o.requiredString("profileName"),
            exportedAtMillis = o.requiredLong("exportedAtMillis"),
            profiles = o.requiredArray("profiles").map {
                val p = it.asObj()
                ExportedProfile(
                    id = p.requiredString("id"),
                    displayName = p.requiredString("displayName"),
                    isGuest = p.requiredBool("isGuest"),
                    avatarId = p.requiredInt("avatarId"),
                    createdAtMillis = p.requiredLong("createdAtMillis"),
                    themeId = p.requiredString("themeId"),
                    languageTag = p.requiredString("languageTag"),
                )
            },
            sessions = o.requiredArray("sessions").map { it.toSession() },
            exercises = o.requiredArray("exercises").map {
                val e = it.asObj()
                ExportedExercise(
                    id = e.requiredString("id"),
                    name = e.requiredString("name"),
                    module = e.requiredString("module"),
                    templateId = e.requiredString("templateId"),
                    difficulty = e.requiredInt("difficulty"),
                    description = e.requiredString("description"),
                    configJson = e.requiredString("configJson"),
                    createdAtMillis = e.requiredLong("createdAtMillis"),
                    updatedAtMillis = e.requiredLong("updatedAtMillis"),
                )
            },
            achievements = o.requiredArray("achievements").map {
                val a = it.asObj()
                ExportedAchievement(
                    achievementId = a.requiredString("achievementId"),
                    earnedAtMillis = a.requiredLong("earnedAtMillis"),
                )
            },
            plans = o.requiredArray("plans").map {
                val p = it.asObj()
                ExportedPlan(
                    id = p.requiredString("id"),
                    name = p.requiredString("name"),
                    origin = p.requiredString("origin"),
                    weeklyGoalMinutes = p.requiredInt("weeklyGoalMinutes"),
                    restDays = p.requiredArray("restDays").map { d -> d.asIntOrDefault(0) },
                    daysJson = p.requiredString("daysJson"),
                    createdAtMillis = p.requiredLong("createdAtMillis"),
                    updatedAtMillis = p.requiredLong("updatedAtMillis"),
                    active = p.requiredBool("active"),
                )
            },
            settings = o["settings"]?.asObj()?.mapValues { it.value.asStringOrDefault("") } ?: emptyMap(),
            checksum = o.requiredString("checksum"),
        )
    }

    private fun ExportedSession.toJson(): JsonValue.Obj = jsonObject {
        "id" string id
        "exerciseId" string exerciseId
        "module" string module
        "kind" string kind
        "status" string status
        "startedAtMillis" long startedAtMillis
        "endedAtMillis" long endedAtMillis
        "activeDurationMillis" long activeDurationMillis
        "difficulty" integer difficulty
        "roundCount" integer roundCount
        "correctCount" integer correctCount
        "incorrectCount" integer incorrectCount
        "missedCount" integer missedCount
        "prematureCount" integer prematureCount
        "responseTimeMeanMillis" number responseTimeMeanMillis
        "responseTimeMedianMillis" number responseTimeMedianMillis
        "responseTimeStdevMillis" number responseTimeStdevMillis
        "bestResponseMillis" long bestResponseMillis
        "accuracy" number accuracy
        "score" number score
        "xpAwarded" integer xpAwarded
    }

    private fun JsonValue.toSession(): ExportedSession {
        val o = asObj()
        return ExportedSession(
            id = o.requiredString("id"),
            exerciseId = o.requiredString("exerciseId"),
            module = o.requiredString("module"),
            kind = o.requiredString("kind"),
            status = o.requiredString("status"),
            startedAtMillis = o.requiredLong("startedAtMillis"),
            endedAtMillis = o.requiredLong("endedAtMillis"),
            activeDurationMillis = o.requiredLong("activeDurationMillis"),
            difficulty = o.requiredInt("difficulty"),
            roundCount = o.requiredInt("roundCount"),
            correctCount = o.requiredInt("correctCount"),
            incorrectCount = o.requiredInt("incorrectCount"),
            missedCount = o.requiredInt("missedCount"),
            prematureCount = o.requiredInt("prematureCount"),
            responseTimeMeanMillis = o.requiredDouble("responseTimeMeanMillis"),
            responseTimeMedianMillis = o.requiredDouble("responseTimeMedianMillis"),
            responseTimeStdevMillis = o.requiredDouble("responseTimeStdevMillis"),
            bestResponseMillis = o.requiredLong("bestResponseMillis"),
            accuracy = o.requiredDouble("accuracy"),
            score = o.requiredDouble("score"),
            xpAwarded = o.requiredInt("xpAwarded"),
        )
    }

    fun sessionToExported(record: SessionRecord, profileIdIsRedacted: Boolean = false): ExportedSession =
        ExportedSession(
            id = record.id,
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
        ).also { @Suppress("UNUSED_EXPRESSION") profileIdIsRedacted }

    fun writeJson(value: JsonValue): String = JsonWriter.write(value)

    fun planItemsToJson(items: List<PlanItem>): String = JsonWriter.write(
        jsonArray(items.map {
            jsonObject {
                "templateId" string it.templateId
                "minutes" integer it.minutes
                "difficulty" integer it.difficulty
            }
        }),
    )

    fun planItemsFromJson(text: String): List<PlanItem> =
        JsonParser.parse(text).asArr().map { v ->
            val o = v.asObj()
            PlanItem(
                templateId = o["templateId"]?.asStringOrNull() ?: "",
                minutes = o["minutes"]?.asIntOrDefault(5) ?: 5,
                difficulty = o["difficulty"]?.asIntOrDefault(3) ?: 3,
            )
        }
}
