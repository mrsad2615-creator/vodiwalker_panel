package com.mesad.time.data.repository

import android.os.SystemClock
import com.mesad.time.data.local.AlarmDao
import com.mesad.time.data.local.AlarmEntity
import com.mesad.time.data.local.LapDao
import com.mesad.time.data.local.LapEntity
import com.mesad.time.data.local.TimerDao
import com.mesad.time.data.local.TimerEntity
import com.mesad.time.data.local.WorldCityDao
import com.mesad.time.data.local.WorldCityEntity
import com.mesad.time.domain.model.Alarm
import com.mesad.time.domain.model.JalaliDate
import com.mesad.time.domain.model.Lap
import com.mesad.time.domain.model.TimerSession
import com.mesad.time.domain.model.TimerState
import com.mesad.time.domain.model.WorldCity
import com.mesad.time.domain.repository.AlarmRepository
import com.mesad.time.domain.repository.StopwatchRepository
import com.mesad.time.domain.repository.TimeRepository
import com.mesad.time.domain.repository.TimerRepository
import com.mesad.time.domain.repository.WorldClockRepository
import com.mesad.time.domain.usecase.NextAlarmCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ── Alarm Repository ─────────────────────────────────────────────────────────

class AlarmRepositoryImpl(
    private val dao: AlarmDao,
    private val calculator: NextAlarmCalculator = NextAlarmCalculator()
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAlarm(id: Long): Alarm? =
        dao.getById(id)?.toDomain()

    override suspend fun insert(alarm: Alarm): Long =
        dao.insert(alarm.toEntity())

    override suspend fun update(alarm: Alarm) {
        dao.update(alarm.toEntity())
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    override fun observeNextAlarm(): Flow<Alarm?> =
        observeAlarms().map { alarms ->
            calculator.findNextAlarm(alarms)
        }

    override suspend fun getEnabledAlarms(): List<Alarm> =
        dao.getEnabled().map { it.toDomain() }
}

// ── Time Repository ──────────────────────────────────────────────────────────

class TimeRepositoryImpl : TimeRepository {

    private val timeFormatter24 = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val timeFormatter12 = DateTimeFormatter.ofPattern("h:mm:ss a")
    private val timeFormatter24NoSec = DateTimeFormatter.ofPattern("HH:mm")
    private val timeFormatter12NoSec = DateTimeFormatter.ofPattern("h:mm a")

    override fun observeCurrentTime(): Flow<Instant> = kotlinx.coroutines.flow.flow {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            emit(Instant.now())
            kotlinx.coroutines.delay(250)
        }
    }

    override fun observeTimezone(): Flow<ZoneId> = kotlinx.coroutines.flow.flow {
        var last = ZoneId.systemDefault()
        emit(last)
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val current = ZoneId.systemDefault()
            if (current != last) { last = current; emit(current) }
            kotlinx.coroutines.delay(1000)
        }
    }

    override fun getJalaliDate(instant: Instant, zone: ZoneId): JalaliDate {
        val localDate = instant.atZone(zone).toLocalDate()
        return JalaliDate.fromGregorian(localDate)
    }

    override fun formatTime(
        instant: Instant,
        zone: ZoneId,
        use24h: Boolean,
        showSeconds: Boolean
    ): String {
        val zoned = instant.atZone(zone)
        val formatter = when {
            use24h && showSeconds -> timeFormatter24
            use24h && !showSeconds -> timeFormatter24NoSec
            !use24h && showSeconds -> timeFormatter12
            else -> timeFormatter12NoSec
        }
        return zoned.format(formatter)
    }

    override fun formatDate(instant: Instant, zone: ZoneId): String {
        val zoned = instant.atZone(zone)
        return zoned.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
}

// ── World Clock Repository ───────────────────────────────────────────────────

class WorldClockRepositoryImpl(
    private val dao: WorldCityDao
) : WorldClockRepository {

    override fun observeCities(): Flow<List<WorldCity>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addCity(city: WorldCity): Long =
        dao.insert(city.toEntity())

    override suspend fun removeCity(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun reorderCities(cities: List<WorldCity>) {
        cities.forEachIndexed { index, city ->
            dao.update(city.copy(sortOrder = index).toEntity())
        }
    }

    override suspend fun getCity(id: Long): WorldCity? =
        dao.getById(id)?.toDomain()
}

// ── Timer Repository ─────────────────────────────────────────────────────────

class TimerRepositoryImpl(
    private val dao: TimerDao
) : TimerRepository {

    override fun observeTimers(): Flow<List<TimerSession>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun createTimer(durationMs: Long, label: String): Long {
        val entity = TimerEntity(
            label = label,
            durationMs = durationMs,
            remainingMs = durationMs,
            state = TimerState.IDLE.name
        )
        return dao.insert(entity)
    }

    override suspend fun startTimer(id: Long) {
        val entity = dao.getById(id) ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val remaining = entity.remainingMs
        dao.update(
            entity.copy(
                state = TimerState.RUNNING.name,
                deadlineElapsedRealtime = nowElapsed + remaining,
                deadlineWallClock = nowWall + remaining
            )
        )
    }

    override suspend fun pauseTimer(id: Long) {
        val entity = dao.getById(id) ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        val deadline = entity.deadlineElapsedRealtime ?: entity.remainingMs
        val remaining = (deadline - nowElapsed).coerceAtLeast(0L)
        dao.update(
            entity.copy(
                state = TimerState.PAUSED.name,
                remainingMs = remaining,
                deadlineElapsedRealtime = null,
                deadlineWallClock = null
            )
        )
    }

    override suspend fun resetTimer(id: Long) {
        val entity = dao.getById(id) ?: return
        dao.update(
            entity.copy(
                state = TimerState.IDLE.name,
                remainingMs = entity.durationMs,
                deadlineElapsedRealtime = null,
                deadlineWallClock = null
            )
        )
    }

    override suspend fun deleteTimer(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun getTimer(id: Long): TimerSession? =
        dao.getById(id)?.toDomain()
}

// ── Stopwatch Repository ─────────────────────────────────────────────────────

class StopwatchRepositoryImpl(
    private val dao: LapDao
) : StopwatchRepository {

    override fun observeLaps(): Flow<List<Lap>> =
        dao.observeByStopwatch(1L).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addLap(totalElapsedMs: Long, deltaMs: Long): Lap {
        val existing = dao.getByStopwatch(1L)
        val lapNumber = (existing.maxOfOrNull { it.lapNumber } ?: 0) + 1

        val entity = LapEntity(
            stopwatchId = 1L,
            lapNumber = lapNumber,
            totalElapsedMs = totalElapsedMs,
            deltaMs = deltaMs
        )
        val id = dao.insert(entity)

        // Recalculate best/worst
        val all = dao.getByStopwatch(1L)
        val bestDelta = all.minOf { it.deltaMs }
        val worstDelta = all.maxOf { it.deltaMs }
        all.forEach { lap ->
            val isBest = lap.deltaMs == bestDelta && all.size > 1
            val isWorst = lap.deltaMs == worstDelta && all.size > 1
            if (lap.isBest != isBest || lap.isWorst != isWorst) dao.update(lap.copy(isBest = isBest, isWorst = isWorst))
        }

        return Lap(
            id = id,
            stopwatchId = 1L,
            lapNumber = lapNumber,
            totalElapsedMs = totalElapsedMs,
            deltaMs = deltaMs,
            isBest = deltaMs == bestDelta,
            isWorst = deltaMs == worstDelta
        )
    }

    override suspend fun clearLaps() {
        dao.deleteByStopwatch(1L)
    }

    override suspend fun getLaps(): List<Lap> =
        dao.getByStopwatch(1L).map { it.toDomain() }
}

// ── Mappers ──────────────────────────────────────────────────────────────────

private fun AlarmEntity.toDomain() = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    repeatDays = repeatDays.split(",").filter { it.isNotBlank() }
        .mapNotNull { runCatching { java.time.DayOfWeek.valueOf(it.trim()) }.getOrNull() }.toSet(),
    ringtoneUri = ringtoneUri,
    vibrate = vibrate,
    enabled = enabled,
    snoozeMinutes = snoozeMinutes,
    nextTriggerElapsedRealtime = nextTriggerElapsedRealtime,
    nextTriggerWallClock = nextTriggerWallClock,
    createdAt = createdAt
)

private fun Alarm.toEntity() = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    repeatDays = repeatDays.joinToString(",") { it.name },
    ringtoneUri = ringtoneUri,
    vibrate = vibrate,
    enabled = enabled,
    snoozeMinutes = snoozeMinutes,
    nextTriggerElapsedRealtime = nextTriggerElapsedRealtime,
    nextTriggerWallClock = nextTriggerWallClock,
    createdAt = createdAt
)

private fun WorldCityEntity.toDomain() = WorldCity(
    id = id, name = name, zoneId = zoneId, isLocal = isLocal, sortOrder = sortOrder
)

private fun WorldCity.toEntity() = WorldCityEntity(
    id = id, name = name, zoneId = zoneId, isLocal = isLocal, sortOrder = sortOrder
)

private fun TimerEntity.toDomain() = TimerSession(
    id = id,
    label = label,
    durationMs = durationMs,
    deadlineElapsedRealtime = deadlineElapsedRealtime,
    deadlineWallClock = deadlineWallClock,
    state = runCatching { TimerState.valueOf(state) }.getOrDefault(TimerState.IDLE),
    remainingMs = remainingMs,
    createdAt = createdAt
)

private fun LapEntity.toDomain() = Lap(
    id = id,
    stopwatchId = stopwatchId,
    lapNumber = lapNumber,
    totalElapsedMs = totalElapsedMs,
    deltaMs = deltaMs,
    isBest = isBest,
    isWorst = isWorst
)
