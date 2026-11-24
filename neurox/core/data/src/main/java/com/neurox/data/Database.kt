package com.neurox.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neurox.domain.Exercise
import com.neurox.domain.ExerciseBlueprint
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val guest: Boolean = false,
    val language: String = "FA",
    val theme: String = "DARK",
    val accent: Long = 0xFFD4B982,
    val lightAccent: Long = 0xFF765A28,
    val adaptive: Boolean = true,
    val reducedMotion: Boolean = false,
    val textScale: Float = 1f,
)

@Entity(tableName = "sessions", foreignKeys = [ForeignKey(
    entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["profileId", "startedAt"])])
data class SessionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val exercise: String,
    val module: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "ACTIVE",
    val checkpoint: String,
    val difficulty: Int,
    val baseline: Boolean,
    val valid: Boolean = false,
    val durationMs: Long = 0,
    val accuracy: Double = 0.0,
    val responseMs: Double? = null,
    val responseDeviationMs: Double? = null,
    val wpm: Double? = null,
    val xp: Int = 0,
)

@Entity(tableName = "favorites", primaryKeys = ["profileId", "exercise"], foreignKeys = [ForeignKey(
    entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE
)])
data class FavoriteEntity(val profileId: String, val exercise: String)

@Entity(tableName = "saved_themes", foreignKeys = [ForeignKey(
    entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE
)], indices = [Index("profileId")])
data class SavedThemeEntity(@PrimaryKey val id: String, val profileId: String, val name: String, val darkAccent: Long, val lightAccent: Long)

@Entity(tableName = "adaptations", foreignKeys = [ForeignKey(
    entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE
)], indices = [Index("profileId")])
data class AdaptationEntity(@PrimaryKey val id: String, val profileId: String, val exercise: String, val createdAt: Long, val fromDifficulty: Int, val toDifficulty: Int, val reason: String, val accepted: Boolean)

@Entity(tableName = "custom_exercises", foreignKeys = [ForeignKey(
    entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE
)], indices = [Index("profileId")])
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val template: String,
    val module: String,
    val difficulty: Int,
    val rounds: Int,
    val responseLimitMs: Long,
    val targetAccuracyPercent: Int,
    val errorAllowance: Int,
    val schemaVersion: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun blueprint(): ExerciseBlueprint {
        val exercise = Exercise.valueOf(template)
        require(module == exercise.module.name)
        return ExerciseBlueprint(name, exercise, difficulty, rounds, responseLimitMs, targetAccuracyPercent, errorAllowance, schemaVersion)
            .also { require(it.validate().isEmpty()) { "Invalid saved exercise" } }
    }
}

object DatabaseMigrations {
    // Additive migration: no replacement or deletion of any existing table.
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `custom_exercises` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT NOT NULL, `template` TEXT NOT NULL, `module` TEXT NOT NULL, `difficulty` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `responseLimitMs` INTEGER NOT NULL, `targetAccuracyPercent` INTEGER NOT NULL, `errorAllowance` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_exercises_profileId` ON `custom_exercises` (`profileId`)")
        }
    }
}

@Dao
interface NeuroxDao {
    @Query("SELECT * FROM profiles ORDER BY guest DESC, name") fun profiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun profile(id: String): ProfileEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertProfile(profile: ProfileEntity)
    @Update suspend fun updateProfile(profile: ProfileEntity)
    @Query("DELETE FROM profiles WHERE id = :id AND guest = 0") suspend fun deleteProfile(id: String)
    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startedAt DESC") fun sessions(profileId: String): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1") suspend fun activeSession(profileId: String): SessionEntity?
    @Query("SELECT * FROM sessions WHERE id = :id AND profileId = :profileId") suspend fun session(id: String, profileId: String): SessionEntity?
    @Insert suspend fun insertSession(session: SessionEntity)
    @Update suspend fun updateSession(session: SessionEntity)
    @Query("SELECT * FROM favorites WHERE profileId = :profileId") fun favorites(profileId: String): Flow<List<FavoriteEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun favorite(value: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE profileId = :profileId AND exercise = :exercise") suspend fun unfavorite(profileId: String, exercise: String)
    @Query("SELECT * FROM saved_themes WHERE profileId = :profileId ORDER BY name") fun themes(profileId: String): Flow<List<SavedThemeEntity>>
    @Query("SELECT * FROM saved_themes WHERE id = :id") suspend fun theme(id: String): SavedThemeEntity?
    @Upsert suspend fun saveTheme(theme: SavedThemeEntity)
    @Query("DELETE FROM saved_themes WHERE id = :id AND profileId = :profileId") suspend fun deleteTheme(id: String, profileId: String)
    @Insert suspend fun recordAdaptation(value: AdaptationEntity)
    @Query("SELECT * FROM custom_exercises WHERE profileId = :profileId ORDER BY updatedAt DESC") fun customExercises(profileId: String): Flow<List<CustomExerciseEntity>>
    @Query("SELECT * FROM custom_exercises WHERE id = :id") suspend fun customExercise(id: String): CustomExerciseEntity?
    @Upsert suspend fun saveCustomExercise(value: CustomExerciseEntity)
    @Query("DELETE FROM custom_exercises WHERE id = :id AND profileId = :profileId") suspend fun deleteCustomExercise(id: String, profileId: String)
}

@Database(entities = [ProfileEntity::class, SessionEntity::class, FavoriteEntity::class, SavedThemeEntity::class, AdaptationEntity::class, CustomExerciseEntity::class], version = 2, exportSchema = true)
abstract class NeuroxDatabase : RoomDatabase() {
    abstract fun dao(): NeuroxDao
    companion object {
        fun create(context: Context): NeuroxDatabase = Room.databaseBuilder(context, NeuroxDatabase::class.java, "neurox.db")
            // Never add fallbackToDestructiveMigration. Future upgrades need explicit migrations.
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .build()
    }
}
