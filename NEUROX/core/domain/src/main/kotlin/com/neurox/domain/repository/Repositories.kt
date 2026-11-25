package com.neurox.domain.repository

import com.neurox.common.result.Outcome
import com.neurox.domain.model.Module
import com.neurox.domain.model.ProfileSummary
import com.neurox.domain.model.SessionKind
import com.neurox.domain.model.SessionRecord

/**
 * Repository contracts. The data layer implements them (Room + DataStore).
 *
 * OWNERSHIP RULE: every query takes a [profileId] (or returns data that already
 * belongs to one) and implementations must filter in SQL, never only in the UI.
 * This is what enforces profile isolation.
 */
interface ProfileRepository {
    fun observeProfiles(): Stream<List<ProfileSummary>>
    fun observeActiveProfile(): Stream<ProfileSummary?>
    suspend fun getActiveProfile(): ProfileSummary?
    suspend fun getProfile(id: String): ProfileSummary?
    suspend fun createProfile(displayName: String, isGuest: Boolean = false): Outcome<ProfileSummary>
    suspend fun renameProfile(id: String, newName: String): Outcome<ProfileSummary>
    suspend fun updateAvatar(id: String, avatarId: Int): Outcome<ProfileSummary>
    suspend fun updateSettingsPatch(id: String, themeId: String?, languageTag: String?): Outcome<ProfileSummary>
    suspend fun switchProfile(id: String, pin: String?): Outcome<ProfileSummary>
    suspend fun setPin(id: String, pin: String?): Outcome<Unit>
    suspend fun verifyPin(id: String, pin: String): Boolean
    suspend fun deleteProfile(id: String): Outcome<Unit>
    suspend fun countProfiles(): Int
}

interface SessionRepository {
    /** Saves a record. Idempotent on the record id. */
    suspend fun save(record: SessionRecord): Outcome<SessionRecord>
    /** Reads one session; the profile is part of the key, so ids cannot be guessed. */
    suspend fun getById(profileId: String, id: String): SessionRecord?
    /** Oldest first. */
    fun observeForProfile(profileId: String, module: Module? = null): Stream<List<SessionRecord>>
    fun observeSince(profileId: String, sinceMillis: Long, kind: SessionKind? = null): Stream<List<SessionRecord>>
    suspend fun listRecent(profileId: String, limit: Int, kind: SessionKind? = null): List<SessionRecord>
    suspend fun listAll(profileId: String): List<SessionRecord>
    suspend fun countCompleted(profileId: String): Int

    /**
     * Marks every RUNNING session of a profile as ABANDONED.
     *
     * A process that dies cannot finish a session, so on the next start the
     * application closes those rows explicitly instead of leaving permanent
     * "running" rows that would corrupt analytics.
     */
    suspend fun closeDanglingSessions(profileId: String, endedAtMillis: Long): Int

    /** In-progress session checkpoint, used for process-death recovery. */
    suspend fun saveCheckpoint(profileId: String, json: String)
    suspend fun loadCheckpoint(profileId: String): String?
    suspend fun clearCheckpoint(profileId: String)
}

/** A settings document per profile, stored as typed key/value entries. */
interface SettingsRepository {
    fun observeSettings(profileId: String): Stream<Map<String, String>>
    suspend fun get(profileId: String, key: String): String?
    suspend fun put(profileId: String, key: String, value: String)
    suspend fun putAll(profileId: String, values: Map<String, String>)
}

interface AchievementRepository {
    fun observeEarnedIds(profileId: String): Stream<Set<String>>
    suspend fun earnedIds(profileId: String): Set<String>
    /** Inserts if absent; returns true only when the id was newly inserted. */
    suspend fun grant(profileId: String, achievementId: String, earnedAtMillis: Long): Boolean
}

interface PlanRepository {
    fun observeActivePlan(profileId: String): Stream<com.neurox.domain.plan.TrainingPlan?>
    suspend fun save(profileId: String, plan: com.neurox.domain.plan.TrainingPlan): Outcome<Unit>
    suspend fun listHistory(profileId: String): List<com.neurox.domain.plan.TrainingPlan>
}

interface ExerciseRepository {
    fun observeCustomExercises(profileId: String): Stream<List<CustomExercise>>
    suspend fun saveCustom(profileId: String, exercise: CustomExercise): Outcome<CustomExercise>
    suspend fun deleteCustom(profileId: String, exerciseId: String): Outcome<Unit>
    fun observeFavorites(profileId: String): Stream<Set<String>>
    suspend fun toggleFavorite(profileId: String, exerciseId: String): Outcome<Boolean>
}

/**
 * A user-built exercise. Immutable value object: [updatedAtMillis] changes on
 * every edit, so downstream caches can invalidate on change.
 */
data class CustomExercise(
    val id: String,
    val profileId: String,
    val name: String,
    val module: Module,
    val templateId: String,
    val difficulty: Int,
    val description: String,
    val config: com.neurox.domain.exercise.ExerciseConfig,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** Audio and haptic preferences, per profile. */
interface AudioRepository {
    fun observe(profileId: String): Stream<com.neurox.domain.model.AudioSettings>
    suspend fun update(profileId: String, settings: com.neurox.domain.model.AudioSettings)
}
