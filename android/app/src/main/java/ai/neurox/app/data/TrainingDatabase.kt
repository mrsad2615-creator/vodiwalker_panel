package ai.neurox.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "training_sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val module: String,
    val correct: Int,
    val total: Int,
    val durationMs: Long,
    @ColumnInfo(defaultValue = "0") val meanResponseMs: Long = 0,
    val completedAtEpochMs: Long
)

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity)
    @Query("SELECT * FROM training_sessions ORDER BY completedAtEpochMs DESC") fun observeRecent(): Flow<List<SessionEntity>>
    @Query("SELECT COUNT(*) FROM training_sessions") fun observeCount(): Flow<Int>
}

@Database(entities = [SessionEntity::class], version = 2, exportSchema = true)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE training_sessions ADD COLUMN meanResponseMs INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: TrainingDatabase? = null
        fun get(context: Context): TrainingDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TrainingDatabase::class.java, "neurox-local.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
