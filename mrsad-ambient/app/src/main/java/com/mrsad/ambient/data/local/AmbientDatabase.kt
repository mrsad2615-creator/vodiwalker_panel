package com.mrsad.ambient.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val category: String,
    val message: String,
    val isEstimate: Boolean
)

@Entity(tableName = "session_usage")
data class SessionUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val durationSeconds: Int,
    val profileName: String,
    val triggerSource: String
)

@Dao
interface AmbientDao {
    @Insert
    suspend fun insertLog(log: DiagnosticLogEntity)

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeLogs(): Flow<List<DiagnosticLogEntity>>

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearLogs()

    @Insert
    suspend fun insertSession(session: SessionUsageEntity)

    @Query("SELECT * FROM session_usage ORDER BY timestamp DESC LIMIT 500")
    fun observeSessions(): Flow<List<SessionUsageEntity>>

    @Query("DELETE FROM session_usage")
    suspend fun clearSessions()
}

@Database(
    entities = [DiagnosticLogEntity::class, SessionUsageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AmbientDatabase : RoomDatabase() {
    abstract fun ambientDao(): AmbientDao

    companion object {
        @Volatile
        private var INSTANCE: AmbientDatabase? = null

        fun getInstance(context: Context): AmbientDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AmbientDatabase::class.java,
                    "mrsad_ambient.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
