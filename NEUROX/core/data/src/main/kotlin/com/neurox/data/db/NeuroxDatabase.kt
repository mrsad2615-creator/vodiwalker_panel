package com.neurox.data.db

import androidx.room.Database
import androidx.room.JournalMode
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The NEUROX database.
 *
 * MIGRATION POLICY (mandatory)
 * - `exportSchema` is true, so every schema version is stored in the repository.
 * - A migration must be written for every version step and tested with the Room
 *   migration test helper against a real SQLite file.
 * - No migration may delete user data. Removing a column means copying it first.
 * - A destructive fallback is deliberately NOT configured: a schema change that
 *   cannot be migrated must fail loudly in a test, not wipe a user's history.
 */
@Database(
    entities = [
        ProfileEntity::class,
        SessionEntity::class,
        CustomExerciseEntity::class,
        AchievementEntity::class,
        FavoriteEntity::class,
        SettingEntity::class,
        PlanEntity::class,
        CheckpointEntity::class,
    ],
    version = Schema.CURRENT_VERSION,
    exportSchema = true,
)
abstract class NeuroxDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun achievementDao(): AchievementDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun settingDao(): SettingDao
    abstract fun planDao(): PlanDao

    companion object {
        /**
         * Migrations, oldest first. Version 1 is the initial schema, so the list is
         * empty today; the contract above keeps it honest.
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf()

        /**
         * Opens the database with WAL, foreign keys and no destructive fallback.
         * [configure] lets a release build add an SQLCipher factory without changing
         * this function.
         */
        fun open(
            context: android.content.Context,
            name: String = Schema.DATABASE_NAME,
            configure: (androidx.room.RoomDatabase.Builder<NeuroxDatabase>) -> Unit = {},
        ): NeuroxDatabase {
            val builder = Room.databaseBuilder(context, NeuroxDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            configure(builder)
            return builder.build()
        }
    }
}

/**
 * Foreign keys must be enabled on every connection; Room does this for declared
 * foreign keys, and this callback makes the intent explicit for raw queries.
 */
val FOREIGN_KEYS_CALLBACK = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
