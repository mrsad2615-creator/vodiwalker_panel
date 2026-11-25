package com.neurox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access objects.
 *
 * EVERY user query takes profileId as its first parameter and filters in SQL.
 * The UI never receives rows from another profile, so isolation does not depend on
 * a screen remembering to filter.
 */
@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE active = 1 LIMIT 1")
    fun observeActive(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE active = 1 LIMIT 1")
    suspend fun active(): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProfileEntity)

    @Query(
        "UPDATE profiles SET displayName = :name WHERE id = :id",
    )
    suspend fun updateName(id: String, name: String)

    @Query("UPDATE profiles SET avatarId = :avatarId WHERE id = :id")
    suspend fun updateAvatar(id: String, avatarId: Int)

    @Query("UPDATE profiles SET themeId = :themeId WHERE id = :id")
    suspend fun updateTheme(id: String, themeId: String)

    @Query("UPDATE profiles SET languageTag = :languageTag WHERE id = :id")
    suspend fun updateLanguage(id: String, languageTag: String)

    @Query("UPDATE profiles SET pinHash = :pinHash WHERE id = :id")
    suspend fun updatePin(id: String, pinHash: String?)

    @Query("UPDATE profiles SET lastActiveAtMillis = :whenMillis WHERE id = :id")
    suspend fun touch(id: String, whenMillis: Long)

    /** Switches the active profile atomically, so two rows cannot be active. */
    @Transaction
    suspend fun switchActive(id: String, whenMillis: Long) {
        clearActive()
        setActive(id, whenMillis)
    }

    @Query("UPDATE profiles SET active = 0")
    suspend fun clearActive()

    @Query("UPDATE profiles SET active = 1, lastActiveAtMillis = :whenMillis WHERE id = :id")
    suspend fun setActive(id: String, whenMillis: Long)

    @Query("SELECT * FROM profiles ORDER BY createdAtMillis ASC LIMIT 1")
    suspend fun firstByCreation(): ProfileEntity?

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: SessionEntity): Long

    /** Idempotent save: a repeated write of the same id changes nothing. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startedAtMillis ASC")
    fun observeAll(profileId: String): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions WHERE profileId = :profileId AND module = :module " +
            "ORDER BY startedAtMillis ASC",
    )
    fun observeByModule(profileId: String, module: String): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions WHERE profileId = :profileId AND startedAtMillis >= :since " +
            "ORDER BY startedAtMillis ASC",
    )
    fun observeSince(profileId: String, since: Long): Flow<List<SessionEntity>>

    @Query(
        "SELECT * FROM sessions WHERE profileId = :profileId AND startedAtMillis >= :since " +
            "AND kind = :kind ORDER BY startedAtMillis ASC",
    )
    fun observeSinceByKind(profileId: String, since: Long, kind: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startedAtMillis DESC LIMIT :limit")
    suspend fun recent(profileId: String, limit: Int): List<SessionEntity>

    @Query(
        "SELECT * FROM sessions WHERE profileId = :profileId AND kind = :kind " +
            "ORDER BY startedAtMillis DESC LIMIT :limit",
    )
    suspend fun recentByKind(profileId: String, kind: String, limit: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startedAtMillis ASC")
    suspend fun all(profileId: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id AND profileId = :profileId LIMIT 1")
    suspend fun byId(profileId: String, id: String): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE profileId = :profileId AND status = 'COMPLETED'")
    suspend fun completedCount(profileId: String): Int

    @Query("SELECT DISTINCT dayKey FROM sessions WHERE profileId = :profileId AND status = 'COMPLETED'")
    suspend fun completedDayKeys(profileId: String): List<Long>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND kind = 'BASELINE' ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun latestBaseline(profileId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND exerciseId = :exerciseId ORDER BY startedAtMillis DESC LIMIT :limit")
    suspend fun historyForExercise(profileId: String, exerciseId: String, limit: Int): List<SessionEntity>

    @Query("SELECT MAX(difficulty) FROM sessions WHERE profileId = :profileId AND status = 'COMPLETED'")
    suspend fun highestDifficulty(profileId: String): Int?

    /**
     * Closes sessions left RUNNING by a crash. They are recorded as abandoned, so
     * they never count for XP, records or adaptive trends.
     */
    @Query(
        "UPDATE sessions SET status = 'ABANDONED', endedAtMillis = :endedAt " +
            "WHERE profileId = :profileId AND status = 'RUNNING'",
    )
    suspend fun closeDangling(profileId: String, endedAt: Long): Int

    @Query("DELETE FROM sessions WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: String)
}

@Dao
interface CheckpointDao {

    @Query("SELECT * FROM session_checkpoints WHERE profileId = :profileId LIMIT 1")
    suspend fun load(profileId: String): CheckpointEntity?

    @Upsert
    suspend fun save(checkpoint: CheckpointEntity)

    @Query("DELETE FROM session_checkpoints WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}

@Dao
interface AchievementDao {

    @Query("SELECT achievementId FROM achievements WHERE profileId = :profileId")
    fun observeIds(profileId: String): Flow<List<String>>

    @Query("SELECT achievementId FROM achievements WHERE profileId = :profileId")
    suspend fun ids(profileId: String): List<String>

    /** Returns -1 when the row already exists, which prevents duplicate rewards. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(achievement: AchievementEntity): Long

    @Query("SELECT COUNT(*) FROM achievements WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM custom_exercises WHERE profileId = :profileId ORDER BY updatedAtMillis DESC")
    fun observeCustom(profileId: String): Flow<List<CustomExerciseEntity>>

    @Query("SELECT * FROM custom_exercises WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun customById(profileId: String, id: String): CustomExerciseEntity?

    @Upsert
    suspend fun upsertCustom(exercise: CustomExerciseEntity)

    @Query("DELETE FROM custom_exercises WHERE profileId = :profileId AND id = :id")
    suspend fun deleteCustom(profileId: String, id: String)

    @Query("SELECT exerciseId FROM favorites WHERE profileId = :profileId")
    fun observeFavorites(profileId: String): Flow<List<String>>

    @Query("SELECT exerciseId FROM favorites WHERE profileId = :profileId")
    suspend fun favorites(profileId: String): List<String>

    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun favorite(profileId: String, exerciseId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND exerciseId = :exerciseId")
    suspend fun removeFavorite(profileId: String, exerciseId: String)
}

@Dao
interface SettingDao {

    @Query("SELECT * FROM settings WHERE profileId = :profileId")
    fun observe(profileId: String): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE profileId = :profileId AND key = :key LIMIT 1")
    suspend fun get(profileId: String, key: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Upsert
    suspend fun putAll(settings: List<SettingEntity>)

    @Query("SELECT * FROM settings WHERE profileId = :profileId")
    suspend fun all(profileId: String): List<SettingEntity>
}

@Dao
interface PlanDao {

    @Query("SELECT * FROM plans WHERE profileId = :profileId AND active = 1 LIMIT 1")
    fun observeActive(profileId: String): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE profileId = :profileId ORDER BY updatedAtMillis DESC")
    suspend fun history(profileId: String): List<PlanEntity>

    @Query("SELECT * FROM plans WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun byId(profileId: String, id: String): PlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: PlanEntity)

    @Query("UPDATE plans SET active = 0 WHERE profileId = :profileId")
    suspend fun deactivateAll(profileId: String)

    @Transaction
    suspend fun replaceActive(plan: PlanEntity) {
        deactivateAll(plan.profileId)
        upsert(plan)
    }
}
