package dev.mrsad.app.feature.eye

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.db.ActivityEventEntity
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.app.util.firstValue
import dev.mrsad.core.eye.EyeTrainingEngine
import dev.mrsad.core.eye.EyeTrainingEngine.DrillType
import dev.mrsad.core.eye.EyeTrainingEngine.SessionReport
import dev.mrsad.core.eye.EyeTrainingEngine.TargetPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

enum class EyePhase { SETUP, RUNNING, PAUSED, RESULT }

data class EyeUiState(
    val phase: EyePhase = EyePhase.SETUP,
    val selectedDrill: DrillType = DrillType.SACCADE,
    val speedLevel: Int = 3,
    val difficultyLevel: Int = 2,
    val durationSeconds: Int = 60,
    val remainingMillis: Long = 60_000L,
    val progress: Float = 0f,
    val currentPoint: TargetPoint = TargetPoint(0.5f, 0.5f, 0),
    val schulteBoard: EyeTrainingEngine.SchulteBoard = EyeTrainingEngine.generateSchulteBoard(1L, 2),
    val nextSchulteNumber: Int = 1,
    val hits: Int = 0,
    val misses: Int = 0,
    val totalTargets: Int = 0,
    val stabilitySignalActive: Boolean = false,
    val distractorPhase: Float = 0f,
    val lastTapFeedbackFa: String? = null,
    val lastTapWasHit: Boolean = true,
    val report: SessionReport? = null,
    val totalCompletedSessions: Int = 0,
    val bestAgilityScore: Int = 0,
)

class EyeTrainingViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(EyeUiState())
    val state: StateFlow<EyeUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var saccadePoints: List<TargetPoint> = emptyList()
    private var sessionSeed: Long = 1L
    private var lastScoredStepIndex: Int = -1

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = MrsadClock.nowEpochMillis()
            val events = container.database.activityDao()
                .recentEvents(now - 90L * 86_400_000L, 200)
                .firstValue()
                .filter { it.module == "EYE" }
            val best = events.maxOfOrNull { it.primaryMetric.toInt() } ?: 0
            _state.update {
                it.copy(
                    totalCompletedSessions = events.size,
                    bestAgilityScore = best,
                )
            }
        }
    }

    fun selectDrill(drill: DrillType) {
        if (_state.value.phase != EyePhase.SETUP) return
        _state.update { it.copy(selectedDrill = drill) }
    }

    fun setSpeedLevel(level: Int) {
        if (_state.value.phase != EyePhase.SETUP) return
        _state.update { it.copy(speedLevel = level.coerceIn(1, 5)) }
    }

    fun setDifficultyLevel(level: Int) {
        if (_state.value.phase != EyePhase.SETUP) return
        _state.update { it.copy(difficultyLevel = level.coerceIn(1, 5)) }
    }

    fun setDurationSeconds(seconds: Int) {
        if (_state.value.phase != EyePhase.SETUP) return
        val clamped = seconds.coerceIn(30, 180)
        _state.update {
            it.copy(
                durationSeconds = clamped,
                remainingMillis = clamped * 1000L,
            )
        }
    }

    fun startSession() {
        val current = _state.value
        sessionSeed = MrsadClock.nowEpochMillis()
        lastScoredStepIndex = -1
        val totalDurationMs = current.durationSeconds * 1000L
        val intervalMs = EyeTrainingEngine.stepIntervalMillis(current.speedLevel)
        val estimatedSteps = (totalDurationMs / intervalMs).toInt().coerceAtLeast(12)

        saccadePoints = EyeTrainingEngine.generateSaccadeSequence(
            seed = sessionSeed,
            difficultyLevel = current.difficultyLevel,
            count = estimatedSteps + 8,
        )
        val board = EyeTrainingEngine.generateSchulteBoard(
            seed = sessionSeed,
            difficultyLevel = current.difficultyLevel,
        )

        _state.update {
            it.copy(
                phase = EyePhase.RUNNING,
                remainingMillis = totalDurationMs,
                progress = 0f,
                currentPoint = saccadePoints.firstOrNull() ?: TargetPoint(0.5f, 0.5f, 0),
                schulteBoard = board,
                nextSchulteNumber = 1,
                hits = 0,
                misses = 0,
                totalTargets = if (current.selectedDrill == DrillType.PERIPHERAL_SPAN) board.totalCells else 1,
                stabilitySignalActive = false,
                distractorPhase = 0f,
                lastTapFeedbackFa = null,
                lastTapWasHit = true,
                report = null,
            )
        }

        if (!MrsadTestHooks.isDeterministic) {
            runClock(totalDurationMs, intervalMs)
        }
    }

    private fun runClock(totalDurationMs: Long, intervalMs: Long) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            val startMono = SystemClock.elapsedRealtime()
            var lastStepIndex = 0
            while (isActive && _state.value.phase == EyePhase.RUNNING) {
                delay(40L)
                val elapsed = (SystemClock.elapsedRealtime() - startMono).coerceAtLeast(0L)
                val remaining = (totalDurationMs - elapsed).coerceAtLeast(0L)
                val progress = (elapsed.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                val stepIdx = (elapsed / intervalMs).toInt()
                val tSec = elapsed / 1000f

                _state.update { s ->
                    when (s.selectedDrill) {
                        DrillType.SACCADE -> {
                            val pt = saccadePoints.getOrElse(stepIdx % saccadePoints.size) {
                                TargetPoint(0.5f, 0.5f, stepIdx)
                            }
                            val newTargets = if (stepIdx > lastStepIndex) s.totalTargets + 1 else s.totalTargets
                            lastStepIndex = stepIdx
                            s.copy(
                                remainingMillis = remaining,
                                progress = progress,
                                currentPoint = pt,
                                totalTargets = newTargets,
                            )
                        }
                        DrillType.SMOOTH_PURSUIT -> {
                            val pt = EyeTrainingEngine.pursuitPosition(tSec, s.speedLevel, s.difficultyLevel)
                            val pulseActive = (stepIdx % 2 == 0)
                            val newTargets = if (stepIdx > lastStepIndex && pulseActive) s.totalTargets + 1 else s.totalTargets
                            lastStepIndex = stepIdx
                            s.copy(
                                remainingMillis = remaining,
                                progress = progress,
                                currentPoint = pt,
                                stabilitySignalActive = pulseActive,
                                totalTargets = newTargets,
                            )
                        }
                        DrillType.PERIPHERAL_SPAN -> {
                            s.copy(
                                remainingMillis = remaining,
                                progress = progress,
                            )
                        }
                        DrillType.GAZE_STABILITY -> {
                            val active = (stepIdx % 2 == 1)
                            val newTargets = if (stepIdx > lastStepIndex && active) s.totalTargets + 1 else s.totalTargets
                            lastStepIndex = stepIdx
                            s.copy(
                                remainingMillis = remaining,
                                progress = progress,
                                currentPoint = TargetPoint(0.5f, 0.5f, stepIdx),
                                stabilitySignalActive = active,
                                distractorPhase = tSec,
                                totalTargets = newTargets,
                            )
                        }
                    }
                }

                if (remaining <= 0L) {
                    finishSession(completed = true)
                    break
                }
            }
        }
    }

    /**
     * Validates a spatial tap on the interactive canvas using normalized
     * `(0f..1f, 0f..1f)` coordinates.
     */
    fun onCanvasTap(normalizedX: Float, normalizedY: Float) {
        val s = _state.value
        if (s.phase != EyePhase.RUNNING) return

        when (s.selectedDrill) {
            DrillType.SACCADE -> {
                val dist = hypot(normalizedX - s.currentPoint.x, normalizedY - s.currentPoint.y)
                val hitRadius = 0.17f
                if (dist <= hitRadius) {
                    val nextStep = (s.currentPoint.stepIndex + 1) % saccadePoints.size.coerceAtLeast(1)
                    val nextPt = saccadePoints.getOrElse(nextStep) { s.currentPoint }
                    _state.update {
                        it.copy(
                            hits = it.hits + 1,
                            totalTargets = maxOf(it.totalTargets, it.hits + it.misses + 1),
                            currentPoint = nextPt,
                            lastTapFeedbackFa = "ثبت دقیق روی هدف ساکاد",
                            lastTapWasHit = true,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            misses = it.misses + 1,
                            totalTargets = it.totalTargets + 1,
                            lastTapFeedbackFa = "خارج از محدودهٔ هدف — دقیقاً روی دایرهٔ درخشان ضربه بزنید",
                            lastTapWasHit = false,
                        )
                    }
                }
            }

            DrillType.SMOOTH_PURSUIT -> {
                val dist = hypot(normalizedX - s.currentPoint.x, normalizedY - s.currentPoint.y)
                val hitRadius = 0.18f
                if (dist <= hitRadius) {
                    if (lastScoredStepIndex != s.currentPoint.stepIndex) {
                        lastScoredStepIndex = s.currentPoint.stepIndex
                        _state.update {
                            it.copy(
                                hits = it.hits + 1,
                                totalTargets = maxOf(it.totalTargets, it.hits + it.misses),
                                lastTapFeedbackFa = "ردیابی پیوستهٔ هدف ثبت شد",
                                lastTapWasHit = true,
                            )
                        }
                    }
                } else {
                    _state.update {
                        it.copy(
                            misses = it.misses + 1,
                            totalTargets = it.totalTargets + 1,
                            lastTapFeedbackFa = "فاصله از مسیر هدف — نگاه و ضربه را روی هدف متحرک نگه دارید",
                            lastTapWasHit = false,
                        )
                    }
                }
            }

            DrillType.GAZE_STABILITY -> {
                val distFromCenter = hypot(normalizedX - 0.5f, normalizedY - 0.5f)
                if (distFromCenter > 0.24f) {
                    _state.update {
                        it.copy(
                            misses = it.misses + 1,
                            totalTargets = it.totalTargets + 1,
                            lastTapFeedbackFa = "حواس‌پرتی محیطی! به حلقه‌های اطراف ضربه نزنید؛ روی مرکز بمانید",
                            lastTapWasHit = false,
                        )
                    }
                    return
                }
                if (s.stabilitySignalActive && lastScoredStepIndex != s.currentPoint.stepIndex) {
                    lastScoredStepIndex = s.currentPoint.stepIndex
                    _state.update {
                        it.copy(
                            hits = it.hits + 1,
                            totalTargets = maxOf(it.totalTargets, it.hits + it.misses),
                            lastTapFeedbackFa = "ثبت عالی در لحظهٔ تغییر رنگ سیگنال مرکزی",
                            lastTapWasHit = true,
                        )
                    }
                } else if (!s.stabilitySignalActive) {
                    _state.update {
                        it.copy(
                            misses = it.misses + 1,
                            totalTargets = it.totalTargets + 1,
                            lastTapFeedbackFa = "زود ضربه زدید! فقط وقتی نقطهٔ مرکزی طلایی شد ضربه بزنید",
                            lastTapWasHit = false,
                        )
                    }
                }
            }

            DrillType.PERIPHERAL_SPAN -> Unit
        }
    }

    /**
     * Button-based signal confirmation for Gaze Stability & Smooth Pursuit.
     */
    fun onTargetHit() {
        val s = _state.value
        if (s.phase != EyePhase.RUNNING) return
        when (s.selectedDrill) {
            DrillType.GAZE_STABILITY -> onCanvasTap(0.5f, 0.5f)
            DrillType.SMOOTH_PURSUIT, DrillType.SACCADE -> onCanvasTap(s.currentPoint.x, s.currentPoint.y)
            DrillType.PERIPHERAL_SPAN -> Unit
        }
    }

    fun onSchulteCellTap(number: Int) {
        val s = _state.value
        if (s.phase != EyePhase.RUNNING || s.selectedDrill != DrillType.PERIPHERAL_SPAN) return
        if (number == s.nextSchulteNumber) {
            val next = s.nextSchulteNumber + 1
            val completedBoard = next > s.schulteBoard.totalCells
            if (completedBoard) {
                val newBoard = EyeTrainingEngine.generateSchulteBoard(
                    seed = MrsadClock.nowEpochMillis() + next,
                    difficultyLevel = s.difficultyLevel,
                )
                _state.update {
                    it.copy(
                        hits = it.hits + 1,
                        totalTargets = it.totalTargets + newBoard.totalCells,
                        schulteBoard = newBoard,
                        nextSchulteNumber = 1,
                        lastTapFeedbackFa = "جدول کامل شد! جدول شولتهٔ بعدی بارگذاری شد",
                        lastTapWasHit = true,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        hits = it.hits + 1,
                        nextSchulteNumber = next,
                        lastTapFeedbackFa = "درست! عدد بعدی: $next",
                        lastTapWasHit = true,
                    )
                }
            }
        } else {
            _state.update {
                it.copy(
                    misses = it.misses + 1,
                    totalTargets = it.totalTargets + 1,
                    lastTapFeedbackFa = "عدد نادرست! باید عدد ${it.nextSchulteNumber} را پیدا کنید",
                    lastTapWasHit = false,
                )
            }
        }
    }

    fun finishSession(completed: Boolean) {
        tickerJob?.cancel()
        val s = _state.value
        if (!completed && s.hits == 0 && s.misses == 0) {
            _state.update { it.copy(phase = EyePhase.SETUP) }
            return
        }
        val effectiveTargets = maxOf(s.totalTargets, s.hits + s.misses, 1)
        val report = EyeTrainingEngine.evaluateSession(
            drill = s.selectedDrill,
            speedLevel = s.speedLevel,
            difficultyLevel = s.difficultyLevel,
            durationSeconds = s.durationSeconds,
            hits = s.hits.coerceAtLeast(if (completed && s.misses == 0) 1 else 0),
            totalTargets = effectiveTargets,
        )
        _state.update {
            it.copy(
                phase = EyePhase.RESULT,
                report = report,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val now = MrsadClock.nowEpochMillis()
            val minutes = (s.durationSeconds / 60).coerceAtLeast(1)
            container.database.activityDao().insert(
                ActivityEventEntity(
                    dateIso = MrsadClock.todayIso(),
                    finishedAtMillis = now,
                    module = "EYE",
                    minutes = minutes,
                    primaryMetric = report.agilityScore.toDouble(),
                    secondaryMetric = report.accuracyPercent.toDouble(),
                    payload = report.drill.name,
                ),
            )
            loadHistory()
        }
    }

    fun resetToSetup() {
        tickerJob?.cancel()
        _state.update { it.copy(phase = EyePhase.SETUP, report = null, lastTapFeedbackFa = null) }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EyeTrainingViewModel(container) as T
    }
}
