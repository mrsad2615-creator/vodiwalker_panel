package dev.mrsad.app.data.typing

import dev.mrsad.app.data.db.ActivityEventEntity
import dev.mrsad.app.data.db.ActivityDao
import dev.mrsad.app.data.db.TypingSessionDao
import dev.mrsad.app.data.db.TypingSessionEntity
import dev.mrsad.core.typing.TypingResult
import kotlinx.coroutines.flow.Flow

/** Serialises a mistake map as "char:count|char:count" (no JSON dependency). */
internal fun encodeMistakes(map: Map<Char, Int>): String =
    map.entries.sortedByDescending { it.value }.joinToString("|") { "${it.key.code}:${it.value}" }

internal fun decodeMistakes(encoded: String): Map<Char, Int> =
    encoded.split('|').mapNotNull { part ->
        val pieces = part.split(':')
        val code = pieces.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val count = pieces.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        code.toChar() to count
    }.toMap()

class TypingRepository(
    private val typingDao: TypingSessionDao,
    private val activityDao: ActivityDao,
) {

    /**
     * Persists a finished session plus its activity event.
     * [nowEpochMillis] and [dateIso] are supplied by the caller so the same
     * clock is used for the record and the log (no midnight race).
     */
    suspend fun record(result: TypingResult, nowEpochMillis: Long, dateIso: String): Long {
        val id = typingDao.insert(
            TypingSessionEntity(
                language = result.language.name,
                mode = result.mode,
                cpm = result.cpm,
                wpm = result.wpm,
                wordWpm = result.wordWpm,
                accuracy = result.accuracy,
                correctChars = result.correctChars,
                totalKeystrokes = result.totalKeystrokes,
                errorKeystrokes = result.errorKeystrokes,
                correctWords = result.correctWords,
                elapsedMillis = result.elapsedMillis,
                completed = result.completed,
                finishedAtMillis = nowEpochMillis,
                dateIso = dateIso,
                mistakes = encodeMistakes(result.errorHeatmap),
            )
        )
        activityDao.insert(
            ActivityEventEntity(
                module = "TYPE",
                dateIso = dateIso,
                finishedAtMillis = nowEpochMillis,
                minutes = (result.elapsedMillis / 60_000L).toInt().coerceAtLeast(1),
                primaryMetric = result.wordWpm,
                secondaryMetric = result.accuracy,
                payload = "chars=${result.correctChars};errors=${result.errorKeystrokes}",
            )
        )
        return id
    }

    fun recent(limit: Int = 20) = typingDao.recent(limit)
    fun bestWordWpm() = typingDao.bestWordWpm()
    fun bestAccuracy() = typingDao.bestAccuracy()
    fun totalSessions() = typingDao.totalCount()

    suspend fun mistakesInLast(daysMillis: Long, nowMillis: Long): Map<Char, Int> {
        val since = nowMillis - daysMillis
        val encoded = typingDao.mistakesSince(since)
        return encoded.fold(mutableMapOf<Char, Int>()) { acc, entry ->
            decodeMistakes(entry).forEach { (ch, count) -> acc.merge(ch, count, Int::plus) }
            acc
        }
    }

    suspend fun recentAverageWpm(nowMillis: Long, days: Long = 7): Double? =
        typingDao.averageWpmSince(nowMillis - days * 86_400_000L)

    suspend fun previousAverageWpm(nowMillis: Long, days: Long = 7): Double? =
        typingDao.averageWpmBetween(nowMillis - 2 * days * 86_400_000L, nowMillis - days * 86_400_000L)

    suspend fun recentAverageAccuracy(nowMillis: Long, days: Long = 7): Double? =
        typingDao.averageAccuracySince(nowMillis - days * 86_400_000L)

    fun recentActivity(sinceMillis: Long): Flow<List<ActivityEventEntity>> =
        activityDao.recentEvents(sinceMillis, 40)
}
