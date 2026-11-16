# MESAD — Clean Architecture
## 4-Layer Design: Presentation / Domain / Data / Platform

---

### 1. Layer Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ PRESENTATION (UI)                                           │
│  Compose Screens (8) │ ViewModels │ Navigation │ Widgets     │
│  GLES 3D Renderer │ Performance 2D Canvas                   │
├─────────────────────────────────────────────────────────────┤
│ DOMAIN (Pure Kotlin, No Android)                            │
│  Models: Alarm, WorldCity, TimerSession, Lap, JalaliDate    │
│  UseCases: GetTime, ScheduleAlarm, StartTimer, AddLap       │
│  Repositories (Interfaces): AlarmRepo, TimeRepo, ThemeRepo  │
│  Jalali Calendar Algorithm (Jalaali)                        │
├─────────────────────────────────────────────────────────────┤
│ DATA (Android)                                              │
│  Room DB: AlarmEntity, CityEntity, TimerEntity, LapEntity   │
│  DataStore: ThemePrefs, StudioPreset, Settings              │
│  Repository Impls: AlarmRepositoryImpl, TimeRepositoryImpl  │
│  AlarmManager Scheduler, Notification Manager               │
├─────────────────────────────────────────────────────────────┤
│ PLATFORM (Android System)                                   │
│  AlarmManager.setAlarmClock() │ ForegroundService           │
│  BOOT_COMPLETED Receiver │ Timezone/Time Broadcasts         │
│  GLES 3 Renderer │ Glance Widget                            │
│  App Shortcuts │ Exact Alarm Permission Flow                │
└─────────────────────────────────────────────────────────────┘
```

---

### 2. Domain Models

```kotlin
// Domain — Pure Kotlin, No Android Imports
data class Alarm(
    val id: Long = 0,
    val hour: Int,           // 0-23
    val minute: Int,         // 0-59
    val label: String = "",
    val repeatDays: Set<DayOfWeek> = emptySet(), // empty = one-time
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val nextTriggerElapsedRealtime: Long? = null,
    val nextTriggerWallClock: Long? = null
)

data class WorldCity(
    val id: Long = 0,
    val name: String,        // "Tehran"
    val zoneId: String,      // "Asia/Tehran" (IANA)
    val isLocal: Boolean = false,
    val sortOrder: Int = 0
)

data class TimerSession(
    val id: Long = 0,
    val label: String = "",
    val durationMs: Long,
    val deadlineElapsedRealtime: Long? = null, // Monotonic deadline
    val deadlineWallClock: Long? = null,       // Wall-clock backup
    val state: TimerState = IDLE,              // IDLE, RUNNING, PAUSED, FINISHED
    val remainingMs: Long = durationMs,
    val createdAt: Long = System.currentTimeMillis()
)

data class Lap(
    val id: Long = 0,
    val stopwatchId: Long,
    val lapNumber: Int,
    val totalElapsedMs: Long,  // Monotonic elapsed at lap
    val deltaMs: Long,         // Delta from previous lap
    val isBest: Boolean = false,
    val isWorst: Boolean = false
)

data class JalaliDate(
    val year: Int,   // e.g., 1404
    val month: Int,  // 1-12 (Farvardin..Esfand)
    val day: Int,    // 1-29/30/31
    val weekday: DayOfWeek
)

enum class ClockEngine { ORBIT, CHRONOS, NEXUS }
enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }
```

---

### 3. Repository Interfaces (Domain)

```kotlin
interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    suspend fun getAlarm(id: Long): Alarm?
    suspend fun insert(alarm: Alarm): Long
    suspend fun update(alarm: Alarm)
    suspend fun delete(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    fun observeNextAlarm(): Flow<Alarm?>
}

interface TimeRepository {
    fun observeCurrentTime(): Flow<Instant>
    fun observeTimezone(): Flow<ZoneId>
    fun getJalaliDate(instant: Instant, zone: ZoneId): JalaliDate
    fun formatTime(instant: Instant, zone: ZoneId, use24h: Boolean, showSeconds: Boolean): String
}

interface TimerRepository {
    fun observeTimers(): Flow<List<TimerSession>>
    suspend fun startTimer(id: Long)
    suspend fun pauseTimer(id: Long)
    suspend fun resetTimer(id: Long)
    suspend fun createTimer(durationMs: Long, label: String): Long
    suspend fun deleteTimer(id: Long)
}

interface ThemeRepository {
    fun observeTheme(): Flow<ThemeState>
    suspend fun setClockEngine(engine: ClockEngine)
    suspend fun setPerformanceMode(enabled: Boolean)
    suspend fun saveStudioPreset(preset: StudioPreset)
    fun observeStudioPreset(): Flow<StudioPreset>
}
```

---

### 4. Use Cases (Domain)

```kotlin
class ScheduleAlarmUseCase(
    private val alarmRepo: AlarmRepository,
    private val alarmScheduler: AlarmScheduler // Platform interface
) {
    suspend operator fun invoke(alarm: Alarm) {
        val id = alarmRepo.insert(alarm)
        if (alarm.enabled) alarmScheduler.schedule(alarm.copy(id = id))
    }
}

class CalculateNextAlarmUseCase {
    operator fun invoke(alarms: List<Alarm>, now: Instant): Alarm? {
        // Handles repeat weekdays, one-time, DST transitions
        // Returns alarm with earliest nextTriggerWallClock > now
    }
}

class JalaliConverter {
    fun gregorianToJalali(gregorian: LocalDate): JalaliDate {
        // Jalaali algorithm (Birashk 33-year cycle)
        // Verified against official Iranian calendar
    }
    fun jalaliToGregorian(jalali: JalaliDate): LocalDate { ... }
}
```

---

### 5. Platform Interfaces (Data → Platform)

```kotlin
interface AlarmScheduler {
    fun schedule(alarm: Alarm)
    fun cancel(alarmId: Long)
    fun canScheduleExactAlarms(): Boolean
    fun requestExactAlarmPermission()
}

interface TimerEngine {
    fun start(timer: TimerSession)
    fun pause(timerId: Long)
    fun cancel(timerId: Long)
    fun observeRemaining(timerId: Long): Flow<Long> // Monotonic
}
```

**Implementations:**
- `AlarmManagerScheduler` — `AlarmManager.setAlarmClock()` + `PendingIntent` → `AlarmReceiver`.
- `ForegroundServiceTimerEngine` — `Service` with `elapsedRealtime()` deadline loop, `Notification` with progress.

---

### 6. Monotonic Time Rules (Critical)

**NEVER** use `delay(1000)` decrementing counters. **ALWAYS** use deadline-based math:

```kotlin
// Stopwatch — Correct
val startElapsed = SystemClock.elapsedRealtime() // Monotonic, survives sleep
val elapsed = SystemClock.elapsedRealtime() - startElapsed + accumulatedMs

// Timer — Correct
val deadlineElapsed = SystemClock.elapsedRealtime() + durationMs
val remaining = deadlineElapsed - SystemClock.elapsedRealtime() // Clamp to 0

// Alarm — Correct
val triggerAtElapsed = SystemClock.elapsedRealtime() + delayMs
val triggerAtWallClock = System.currentTimeMillis() + delayMs
// Store BOTH — reboot recovery uses wall-clock, runtime uses monotonic
```

**Edge Cases Handled:**
- App kill → Timers restore from `deadlineElapsedRealtime` + `deadlineWallClock` in Room.
- Reboot → `BOOT_COMPLETED` reschedules alarms, recalculates timer deadlines.
- Clock change (user sets time) → `ACTION_TIME_CHANGED` triggers recomposition, timers unaffected (monotonic).
- Timezone change → `ACTION_TIMEZONE_CHANGED` updates World Clock, Jalali date, alarm wall-clock.
- Doze → `setAlarmClock()` wakes device, `ForegroundService` keeps timer alive.

---

### 7. Dependency Injection

**Hilt** (or manual Koin for zero-annotation overhead):
- `@Singleton` — Repositories, Database, DataStore, and native GLES renderer lifecycle.
- `@ActivityRetainedScoped` — ViewModels.
- `@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher` — Coroutine dispatchers.

---

### 8. Testing Strategy

| Layer | Framework | Coverage |
|---|---|---|
| Domain (Jalali, Next Alarm, Lap Calc) | JUnit 5 + Truth | 95%+ |
| Data (Room, DataStore, Scheduler) | Instrumented + Robolectric | 85%+ |
| Presentation (Compose UI) | Compose Test + Espresso | All 8 screens |
| Integration (Alarm → Receiver → Service) | AndroidTest | Boot, Kill, Timezone Change |
| Performance (Frame time, Cold start) | Macrobenchmark | 60fps, <800ms start |

**Edge Case Tests:**
- Alarm at 00:00, 23:59, DST transition, leap year, Jalali leap year (Esfand 30).
- Timer with 0ms, 24h, pause during Doze, reboot during countdown.
- Stopwatch with 100+ laps, reset during lap, background for 1 hour.
- World Clock with 50 cities, timezone deleted, DST offset change.
