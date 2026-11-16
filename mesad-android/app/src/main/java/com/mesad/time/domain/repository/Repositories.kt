package com.mesad.time.domain.repository

import com.mesad.time.domain.model.Alarm
import com.mesad.time.domain.model.ClockEngine
import com.mesad.time.domain.model.JalaliDate
import com.mesad.time.domain.model.Lap
import com.mesad.time.domain.model.StudioPreset
import com.mesad.time.domain.model.TimerSession
import com.mesad.time.domain.model.WorldCity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

// ── Alarm ────────────────────────────────────────────────────────────────────

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    suspend fun getAlarm(id: Long): Alarm?
    suspend fun insert(alarm: Alarm): Long
    suspend fun update(alarm: Alarm)
    suspend fun delete(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    fun observeNextAlarm(): Flow<Alarm?>
    suspend fun getEnabledAlarms(): List<Alarm>
}

// ── Time ─────────────────────────────────────────────────────────────────────

interface TimeRepository {
    fun observeCurrentTime(): Flow<Instant>
    fun observeTimezone(): Flow<ZoneId>
    fun getJalaliDate(instant: Instant, zone: ZoneId): JalaliDate
    fun formatTime(instant: Instant, zone: ZoneId, use24h: Boolean, showSeconds: Boolean): String
    fun formatDate(instant: Instant, zone: ZoneId): String
}

// ── World Clock ──────────────────────────────────────────────────────────────

interface WorldClockRepository {
    fun observeCities(): Flow<List<WorldCity>>
    suspend fun addCity(city: WorldCity): Long
    suspend fun removeCity(id: Long)
    suspend fun reorderCities(cities: List<WorldCity>)
    suspend fun getCity(id: Long): WorldCity?
}

// ── Timer ────────────────────────────────────────────────────────────────────

interface TimerRepository {
    fun observeTimers(): Flow<List<TimerSession>>
    suspend fun createTimer(durationMs: Long, label: String): Long
    suspend fun startTimer(id: Long)
    suspend fun pauseTimer(id: Long)
    suspend fun resetTimer(id: Long)
    suspend fun deleteTimer(id: Long)
    suspend fun getTimer(id: Long): TimerSession?
}

// ── Stopwatch ────────────────────────────────────────────────────────────────

interface StopwatchRepository {
    fun observeLaps(): Flow<List<Lap>>
    suspend fun addLap(totalElapsedMs: Long, deltaMs: Long): Lap
    suspend fun clearLaps()
    suspend fun getLaps(): List<Lap>
}

// ── Theme / Studio ───────────────────────────────────────────────────────────

data class ThemeState(
    val clockEngine: ClockEngine = ClockEngine.ORBIT,
    val performanceMode: Boolean = false,
    val use24Hour: Boolean = false,
    val showSeconds: Boolean = true,
    val showJalali: Boolean = true,
    val showGregorian: Boolean = true,
    val darkTheme: Boolean = true,
    val dynamicColor: Boolean = true,
    val studioPreset: StudioPreset = StudioPreset()
)

interface ThemeRepository {
    fun observeTheme(): Flow<ThemeState>
    suspend fun setClockEngine(engine: ClockEngine)
    suspend fun setPerformanceMode(enabled: Boolean)
    suspend fun setUse24Hour(enabled: Boolean)
    suspend fun setShowSeconds(enabled: Boolean)
    suspend fun setShowJalali(enabled: Boolean)
    suspend fun setShowGregorian(enabled: Boolean)
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun saveStudioPreset(preset: StudioPreset)
    suspend fun resetStudioPreset()
}
