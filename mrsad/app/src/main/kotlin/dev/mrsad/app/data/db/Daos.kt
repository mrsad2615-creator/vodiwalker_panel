package dev.mrsad.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TypingSessionDao {
    @Insert
    suspend fun insert(entity: TypingSessionEntity): Long

    @Query("SELECT * FROM typing_sessions ORDER BY finishedAtMillis DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TypingSessionEntity>>

    @Query("SELECT * FROM typing_sessions WHERE dateIso = :dateIso ORDER BY finishedAtMillis DESC")
    fun byDate(dateIso: String): Flow<List<TypingSessionEntity>>

    @Query("SELECT MAX(wordWpm) FROM typing_sessions WHERE completed = 1")
    fun bestWordWpm(): Flow<Double?>

    @Query("SELECT MAX(accuracy) FROM typing_sessions WHERE totalKeystrokes >= 50 AND completed = 1")
    fun bestAccuracy(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM typing_sessions")
    fun totalCount(): Flow<Int>

    @Query("SELECT AVG(wordWpm) FROM typing_sessions WHERE finishedAtMillis >= :sinceMillis")
    suspend fun averageWpmSince(sinceMillis: Long): Double?

    @Query("SELECT AVG(accuracy) FROM typing_sessions WHERE finishedAtMillis >= :sinceMillis")
    suspend fun averageAccuracySince(sinceMillis: Long): Double?

    @Query("SELECT AVG(wordWpm) FROM typing_sessions WHERE finishedAtMillis >= :from AND finishedAtMillis < :to")
    suspend fun averageWpmBetween(from: Long, to: Long): Double?

    @Query("SELECT mistakes FROM typing_sessions WHERE finishedAtMillis >= :sinceMillis ORDER BY finishedAtMillis DESC LIMIT 50")
    suspend fun mistakesSince(sinceMillis: Long): List<String>
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(event: ActivityEventEntity): Long

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM activity_events WHERE dateIso = :dateIso")
    fun minutesForDate(dateIso: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM activity_events WHERE dateIso = :dateIso")
    fun sessionCountForDate(dateIso: String): Flow<Int>

    @Query("SELECT * FROM activity_events WHERE dateIso = :dateIso ORDER BY finishedAtMillis DESC")
    fun eventsForDate(dateIso: String): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_events WHERE finishedAtMillis >= :since ORDER BY finishedAtMillis DESC LIMIT :limit")
    fun recentEvents(since: Long, limit: Int): Flow<List<ActivityEventEntity>>

    @Query("SELECT DISTINCT dateIso FROM activity_events ORDER BY dateIso DESC")
    fun activeDates(): Flow<List<String>>

    @Query("SELECT dateIso, COALESCE(SUM(minutes),0) AS minutes, COUNT(*) AS sessions FROM activity_events WHERE dateIso >= :fromDateIso GROUP BY dateIso ORDER BY dateIso")
    fun dailyTotalsSince(fromDateIso: String): Flow<List<DailyTotal>>

    @Query("SELECT COALESCE(SUM(minutes),0) FROM activity_events WHERE dateIso >= :fromDateIso")
    fun minutesSince(fromDateIso: String): Flow<Int>

    @Query("SELECT dateIso, COALESCE(AVG(primaryMetric),0) AS metric FROM activity_events WHERE module = :module AND dateIso >= :fromDateIso GROUP BY dateIso ORDER BY dateIso")
    fun dailyMetricSince(module: String, fromDateIso: String): Flow<List<DailyMetric>>

    @Query("SELECT module AS module, COALESCE(SUM(minutes),0) AS minutes FROM activity_events WHERE dateIso >= :fromDateIso GROUP BY module ORDER BY minutes DESC")
    fun moduleTotalsSince(fromDateIso: String): Flow<List<ModuleTotal>>
}

data class DailyTotal(val dateIso: String, val minutes: Int, val sessions: Int)

data class DailyMetric(val dateIso: String, val metric: Double)

data class ModuleTotal(val module: String, val minutes: Int)
