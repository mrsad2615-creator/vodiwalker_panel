package dev.mrsad.app.feature.eye

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.db.ActivityEventEntity
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.core.eye.EyeTrainingEngine
import dev.mrsad.core.eye.EyeTrainingEngine.DrillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EyePhase { SETUP, RUNNING, RESULT }

data class EyeUiState(
    val phase: EyePhase = EyePhase.SETUP,
    val selectedDrill: DrillType = DrillType.SACCADE,
    val speedLevel: Int = 3,
    val difficultyLevel: Int = 2,
    val durationSeconds: Int = 45,
    val remainingMillis: Long = 45_000L,
    val progress: Float = 0f,
    // Active drill state
    val currentPoint: EyeTrainingEngine.GazePoint = EyeTrainingEngine.GazePoint(0.5f, 0.5f, 0),
    val schulteBoard: EyeTrainingEngine.SchulteBoard = EyeTrainingEngine.generateSchulteBoard(42L, 2),
    val nextSchulteNumber: Int = 1,
    val stabilitySignalActive: Boolean = false,
    val hits: Int = 0,
    val totalTargets: Int = 0,
    // Report & history
    val report: EyeTrainingEngine.EyeSessionReport? = null,
    val totalEyeSessions: Int = 0,
    val averageAgilityScore: Int = 0,
)

class EyeTrainingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(EyeUiState())
    val state: StateFlow<EyeUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var saccadePoints: List<EyeTrainingEngine.GazePoint> = emptyList()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = MrsadClock.nowEpochMillis()
            val events = container.database.activityDao()
                .recentEvents(now - 90L * 24L * 3_600_000L, 500)
                .first()
                .filter { it.module == "EYE" }
            val avg = if (events.isEmpty()) 0 else events.map { it.primaryMetric }.average().toInt()
            _state.update {
                it.copy(
                    totalEyeSessions = events.size,
                    averageAgilityScore = avg,
                )
            }
        }
    }

    fun selectDrill(drill: DrillType) {
        if (_state.value.phase != EyePhase.SETUP) return
        _state.update { it.copy(selectedDrill = drill) }
    }

    fun setSpeed(level: Int) {
        if (_state.value.phase != EyePhase.SETUP) return
        _state.update { it.copy(speedLevel = level.coerceIn(1, 5)) }
    }

    fun setDifficulty(level: Int) {
        if (_state.value.phase != EyePhase.SETUP) return
        _state.update { it.copy(difficultyLevel = level.coerceIn(1, 5)) }
    }

    fun setDuration(seconds: Int) {
        if (_state.value.phase != EyePhase.SETUP) return
        val safe = seconds.coerceIn(30, 120)
        _state.update { it.copy(durationSeconds = safe, remainingMillis = safe * 1000L) }
    }

    fun startSession() {
        val current = _state.value
        val seed = MrsadClock.nowEpochMillis()
        saccadePoints = EyeTrainingEngine.generateSaccadeSequence(seed, current.difficultyLevel, count = 60)
        val board = EyeTrainingEngine.generateSchulteBoard(seed, current.difficultyLevel)
        val totalMillis = current.durationSeconds * 1000L

        _state.update {
            it.copy(
                phase = EyePhase.RUNNING,
                remainingMillis = totalMillis,
                progress = 0f,
                currentPoint = saccadePoints.firstOrNull() ?: EyeTrainingEngine.GazePoint(0.5f, 0.5f, 0),
                schulteBoard = board,
                nextSchulteNumber = 1,
                stabilitySignalActive = true,
                hits = 0,
                totalTargets = if (current.selectedDrill == DrillType.PERIPHERAL_SPAN) board.totalCells else 1,
                report = null,
            )
        }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        if (MrsadTestHooks.isDeterministic) return
        val startMono = SystemClock.elapsedRealtime()
        val cfg = _state.value
        val totalMillis = cfg.durationSeconds * 1000L
        val stepInterval = EyeTrainingEngine.targetIntervalMillis(cfg.speedLevel, cfg.difficultyLevel)
        var lastStepIndex = 0

        tickerJob = viewModelScope.launch {
            while (true) {
                delay(50L)
                val elapsed = (SystemClock.elapsedRealtime() - startMono).coerceAtLeast(0L)
                val remaining = (totalMillis - elapsed).coerceAtLeast(0L)
                val progress = (elapsed.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
                val stepIdx = (elapsed / stepInterval).toInt()

                _state.update { s ->
                    when (s.selectedDrill) {
                        DrillType.SACCADE -> {
                            val pt = saccadePoints[stepIdx % saccadePoints.size]
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
                            val tSec = elapsed / 1000f
                            val pt = EyeTrainingEngine.pursuitPosition(tSec, s.speedLevel, s.difficultyLevel)
                            val newTargets = if (stepIdx > lastStepIndex) s.totalTargets + 1 else s.totalTargets
                            lastStepIndex = stepIdx
                            s.copy(
                                remainingMillis = remaining,
                                progress = progress,
                                currentPoint = pt,
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
                            val active = (stepIdx % 2 == 0)
                            val newTargets = if (stepIdx > lastStepIndex && active) s.totalTargets + 1 else s.totalTargets
                            lastStepIndex = stepIdx
                            s.copy(
                                remainingMillis = remaining,
                                progress = progress,
                                stabilitySignalActive = active,
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

    fun onTargetHit() {
        val s = _state.value
        if (s.phase != EyePhase.RUNNING) return
        val nextStep = (s.currentPoint.stepIndex + 1) % saccadePoints.size.coerceAtLeast(1)
        val nextPt = saccadePoints.getOrElse(nextStep) { s.currentPoint }
        _state.update {
            it.copy(
                hits = it.hits + 1,
                totalTargets = maxOf(it.totalTargets, it.hits + 1),
                currentPoint = if (it.selectedDrill == DrillType.SACCADE) nextPt else it.currentPoint,
            )
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
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        hits = it.hits + 1,
                        nextSchulteNumber = next,
                    )
                }
            }
        } else {
            _state.update { it.copy(totalTargets = it.totalTargets + 1) }
        }
    }

    fun finishSession(completed: Boolean) {
        tickerJob?.cancel()
        val s = _state.value
        if (!completed && s.hits == 0) {
            _state.update { it.copy(phase = EyePhase.SETUP) }
            return
        }
        val report = EyeTrainingEngine.evaluateSession(
            drill = s.selectedDrill,
            speedLevel = s.speedLevel,
            difficultyLevel = s.difficultyLevel,
            durationSeconds = s.durationSeconds,
            hits = s.hits.coerceAtLeast(if (completed) 1 else 0),
            totalTargets = s.totalTargets.coerceAtLeast(1),
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
        _state.update { it.copy(phase = EyePhase.SETUP, report = null) }
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
