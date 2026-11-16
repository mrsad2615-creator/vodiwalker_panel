package com.mesad.time.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.DayOfWeek

// ── Alarm Entity ─────────────────────────────────────────────────────────────

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val repeatDays: String = "", // Comma-separated DayOfWeek names
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val nextTriggerElapsedRealtime: Long? = null,
    val nextTriggerWallClock: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ── World City Entity ────────────────────────────────────────────────────────

@Entity(tableName = "world_cities")
data class WorldCityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val zoneId: String,
    val isLocal: Boolean = false,
    val sortOrder: Int = 0
)

// ── Timer Entity ─────────────────────────────────────────────────────────────

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val durationMs: Long,
    val deadlineElapsedRealtime: Long? = null,
    val deadlineWallClock: Long? = null,
    val state: String = "IDLE", // TimerState name
    val remainingMs: Long = durationMs,
    val createdAt: Long = System.currentTimeMillis()
)

// ── Lap Entity ───────────────────────────────────────────────────────────────

@Entity(tableName = "laps")
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stopwatchId: Long = 1,
    val lapNumber: Int,
    val totalElapsedMs: Long,
    val deltaMs: Long,
    val isBest: Boolean = false,
    val isWorst: Boolean = false
)

// ── Type Converters ──────────────────────────────────────────────────────────

class Converters {
    @TypeConverter
    fun fromDayOfWeekSet(days: Set<DayOfWeek>): String {
        return days.joinToString(",") { it.name }
    }

    @TypeConverter
    fun toDayOfWeekSet(value: String): Set<DayOfWeek> {
        if (value.isBlank()) return emptySet()
        return value.split(",").mapNotNull { runCatching { DayOfWeek.valueOf(it.trim()) }.getOrNull() }.toSet()
    }
}
