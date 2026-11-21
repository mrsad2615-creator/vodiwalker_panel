package dev.mrsad.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Detailed typing session record. Aggregated home/analytics numbers never read
 * from here — they read [ActivityEventEntity] — but this table powers per-skill
 * analysis, personal records and error-pattern reports.
 */
@Entity(
    tableName = "typing_sessions",
    indices = [Index("dateIso"), Index("finishedAtMillis")],
)
data class TypingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String,
    val mode: String,
    val cpm: Double,
    val wpm: Double,
    val wordWpm: Double,
    val accuracy: Double,
    val correctChars: Int,
    val totalKeystrokes: Int,
    val errorKeystrokes: Int,
    val correctWords: Int,
    val elapsedMillis: Long,
    val completed: Boolean,
    val finishedAtMillis: Long,
    /** Local calendar day, "yyyy-MM-dd". */
    val dateIso: String,
    /** Serialised "char:count|char:count" mistakes for pattern analysis. */
    val mistakes: String,
)

/**
 * Single append-only log of completed training activity across all modules.
 * This is the canonical source for streaks, daily summaries and charts, so
 * adding a module never means rewriting analytics.
 */
@Entity(
    tableName = "activity_events",
    indices = [Index("dateIso"), Index("module"), Index(value = ["dateIso", "module"])],
)
data class ActivityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val module: String,
    val dateIso: String,
    val finishedAtMillis: Long,
    val minutes: Int,
    /** Module-specific primary metric (WPM, score %, …). */
    val primaryMetric: Double = 0.0,
    /** Secondary metric (accuracy, comprehension, …). */
    val secondaryMetric: Double = 0.0,
    /** Extra payload for module-specific summaries (JSON). */
    val payload: String = "",
)
