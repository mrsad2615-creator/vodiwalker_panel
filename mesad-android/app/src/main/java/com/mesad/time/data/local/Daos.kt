package com.mesad.time.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ── Alarm DAO ────────────────────────────────────────────────────────────────

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY hour ASC, minute ASC")
    suspend fun getEnabled(): List<AlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(alarm: AlarmEntity)
}

// ── World City DAO ───────────────────────────────────────────────────────────

@Dao
interface WorldCityDao {
    @Query("SELECT * FROM world_cities ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<WorldCityEntity>>

    @Query("SELECT * FROM world_cities WHERE id = :id")
    suspend fun getById(id: Long): WorldCityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: WorldCityEntity): Long

    @Update
    suspend fun update(city: WorldCityEntity)

    @Query("DELETE FROM world_cities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM world_cities")
    suspend fun deleteAll()
}

// ── Timer DAO ────────────────────────────────────────────────────────────────

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun getById(id: Long): TimerEntity?

    @Query("SELECT * FROM timers WHERE state = 'RUNNING'")
    suspend fun getRunning(): List<TimerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timer: TimerEntity): Long

    @Update
    suspend fun update(timer: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ── Lap DAO ──────────────────────────────────────────────────────────────────

@Dao
interface LapDao {
    @Query("SELECT * FROM laps WHERE stopwatchId = :stopwatchId ORDER BY lapNumber ASC")
    fun observeByStopwatch(stopwatchId: Long): Flow<List<LapEntity>>

    @Query("SELECT * FROM laps WHERE stopwatchId = :stopwatchId ORDER BY lapNumber ASC")
    suspend fun getByStopwatch(stopwatchId: Long): List<LapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lap: LapEntity): Long

    @Update
    suspend fun update(lap: LapEntity)

    @Query("DELETE FROM laps WHERE stopwatchId = :stopwatchId")
    suspend fun deleteByStopwatch(stopwatchId: Long)

    @Query("DELETE FROM laps")
    suspend fun deleteAll()
}
