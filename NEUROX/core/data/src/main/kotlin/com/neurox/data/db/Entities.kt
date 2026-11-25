package com.neurox.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema, version 1.
 *
 * DESIGN RULES
 * - Every user-owned row carries `profileId`, and every query filters on it in SQL.
 * - Foreign keys cascade profile deletion, so deleting a profile cannot leave
 *   orphan history behind.
 * - Timestamps are UTC epoch milliseconds. Calendar grouping happens in the domain.
 * - No personal free text is stored except a profile display name, a custom
 *   exercise name and a description the user writes on purpose.
 * - The schema never stores typed training text; only aggregated counts.
 */
object Schema {
    const val CURRENT_VERSION = 1
    const val DATABASE_NAME = "neurox.db"
}

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["createdAtMillis"])],
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val isGuest: Boolean,
    val avatarId: Int,
    val createdAtMillis: Long,
    val themeId: String,
    val languageTag: String,
    val pinHash: String?,
    val active: Boolean,
    val lastActiveAtMillis: Long,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profileId", "startedAtMillis"]),
        Index(value = ["profileId", "module"]),
        Index(value = ["profileId", "exerciseId"]),
        Index(value = ["profileId", "kind", "status"]),
    ],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val exerciseId: String,
    val module: String,
    val kind: String,
    val status: String,
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
    /** Day bucket (UTC day index) for fast daily aggregation without date parsing. */
    val dayKey: Long,
    val schemaVersion: Int,
)

@Entity(
    tableName = "custom_exercises",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "module"]), Index(value = ["profileId", "updatedAtMillis"])],
)
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val module: String,
    val templateId: String,
    val difficulty: Int,
    val description: String,
    /** Validated configuration as JSON text. Never executable code. */
    val configJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "achievements",
    primaryKeys = ["profileId", "achievementId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "earnedAtMillis"])],
)
data class AchievementEntity(
    val profileId: String,
    val achievementId: String,
    val earnedAtMillis: Long,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["profileId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    val profileId: String,
    val exerciseId: String,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "settings",
    primaryKeys = ["profileId", "key"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SettingEntity(
    val profileId: String,
    val key: String,
    val value: String,
)

@Entity(
    tableName = "plans",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "active"]), Index(value = ["profileId", "updatedAtMillis"])],
)
data class PlanEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val origin: String,
    val weeklyGoalMinutes: Int,
    val restDays: String,
    /** Day-to-items map as JSON text. */
    val daysJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val active: Boolean,
)

/**
 * Checkpoint of a running session, so process death can restore the last safe state.
 * One row per profile: f891a new session replaces the checkpoint.
 */
@Entity(
    tableName = "session_checkpoints",
    primaryKeys = ["profileId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CheckpointEntity(
    val profileId: String,
    val sessionId: String,
    val exerciseId: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Serialized session state produced by the presentation layer. */
    val payloadJson: String,
)
