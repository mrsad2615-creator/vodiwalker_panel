package dev.mrsad.app.feature.read

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.db.ActivityEventEntity
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.core.reading.ReadingChunk
import dev.mrsad.core.reading.ReadingEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReadPhase { SETUP, READING, PAUSED, QUIZ, RESULT }

data class ReadUiState(
    val phase: ReadPhase = ReadPhase.SETUP,
    val selectedLevelFilter: Int = 0,
    val selectedTextId: String = ReadTexts.all.first().id,
    val wpm: Int = 280,
    val wordsPerDisplay: Int = 1,
    val chunkIndex: Int = 0,
    val chunkCount: Int = 0,
    val currentChunkText: String = "",
    val currentChunkPivotIndex: Int = 0,
    val progress: Float = 0f,
    val questionIndex: Int = 0,
    val answers: List<Int?> = emptyList(),
    val questions: List<ReadingEngine.ComprehensionQuestion> = emptyList(),
    val rawWpm: Int = 0,
    val comprehensionPercent: Int = 0,
    val effectiveWpm: Int = 0,
    val wordsRead: Int = 0,
    val readingMillis: Long = 0L,
    val saved: Boolean = false,
) {
    val selectedText: ReadText get() = ReadTexts.byId(selectedTextId)
}

/**
 * RSVP session controller with Optimal Recognition Point (ORP) pivot tracking.
 *
 * Presentation speed is driven by the configured WPM, while the *measured*
 * reading speed uses elapsed monotonic time with pauses subtracted.
 */
class ReadViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ReadUiState())
    val state: StateFlow<ReadUiState> = _state.asStateFlow()

    private var chunks: List<ReadingChunk> = emptyList()
    private var clockJob: Job? = null

    private var sessionStartMonotonic: Long = 0L
    private var pausedAccumulated: Long = 0L
    private var pauseStartedAt: Long = 0L
    private var chunksShown: Int = 0

    fun setWpm(value: Int) {
        if (_state.value.phase != ReadPhase.SETUP) return
        _state.update { it.copy(wpm = value.coerceIn(120, 900)) }
    }

    fun setWordsPerDisplay(words: Int) {
        if (_state.value.phase != ReadPhase.SETUP) return
        _state.update { it.copy(wordsPerDisplay = words.coerceIn(1, 3)) }
    }

    fun setLevelFilter(level: Int) {
        if (_state.value.phase != ReadPhase.SETUP) return
        val clamped = level.coerceIn(0, 3)
        val filtered = ReadTexts.forLevel(clamped)
        _state.update { current ->
            val nextSelected = if (filtered.any { it.id == current.selectedTextId }) {
                current.selectedTextId
            } else {
                filtered.firstOrNull()?.id ?: current.selectedTextId
            }
            val recommended = ReadTexts.byId(nextSelected).recommendedWpm
            current.copy(
                selectedLevelFilter = clamped,
                selectedTextId = nextSelected,
                wpm = recommended,
            )
        }
    }

    fun selectText(id: String) {
        if (_state.value.phase != ReadPhase.SETUP) return
        val text = ReadTexts.byId(id)
        _state.update {
            it.copy(
                selectedTextId = id,
                wpm = text.recommendedWpm,
            )
        }
    }

    fun start() {
        val current = _state.value
        chunks = ReadingEngine.chunk(current.selectedText.body, current.wordsPerDisplay)
        if (chunks.isEmpty()) return
        chunksShown = 1
        pausedAccumulated = 0L
        sessionStartMonotonic = SystemClock.elapsedRealtime()
        val first = chunks.first()
        _state.update {
            it.copy(
                phase = ReadPhase.READING,
                chunkIndex = 0,
                chunkCount = chunks.size,
                currentChunkText = first.text,
                currentChunkPivotIndex = first.pivotIndex,
                progress = 1f / chunks.size,
                questionIndex = 0,
                answers = emptyList(),
                questions = emptyList(),
                readingMillis = 0L,
                saved = false,
            )
        }
    }

    fun pause() {
        if (_state.value.phase != ReadPhase.READING) return
        pauseStartedAt = SystemClock.elapsedRealtime()
        clockJob?.cancel()
        _state.update { it.copy(phase = ReadPhase.PAUSED) }
    }

    fun resume() {
        if (_state.value.phase != ReadPhase.PAUSED) return
        pausedAccumulated += SystemClock.elapsedRealtime() - pauseStartedAt
        _state.update { it.copy(phase = ReadPhase.READING) }
    }

    fun stepChunk(delta: Int) {
        if (chunks.isEmpty()) return
        val current = _state.value
        if (current.phase != ReadPhase.READING && current.phase != ReadPhase.PAUSED) return
        val nextIndex = (current.chunkIndex + delta).coerceIn(0, chunks.lastIndex)
        val nextChunk = chunks[nextIndex]
        chunksShown = maxOf(chunksShown, nextIndex + 1)
        _state.update {
            it.copy(
                chunkIndex = nextIndex,
                currentChunkText = nextChunk.text,
                currentChunkPivotIndex = nextChunk.pivotIndex,
                progress = (nextIndex + 1).toFloat() / chunks.size,
            )
        }
    }

    /** Presentation loop with punctuation-aware micro-pauses for natural comprehension. */
    fun runPresentationClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (_state.value.phase == ReadPhase.READING) {
                val current = _state.value
                val chunk = chunks.getOrNull(current.chunkIndex) ?: return@launch
                val baseDuration = ReadingEngine.chunkDurationMillis(current.wpm, chunk.wordCount)
                val punctuationBonus = if (chunk.text.endsWith(".") || chunk.text.endsWith("؛") || chunk.text.endsWith("؟")) {
                    (baseDuration * 0.35).toLong()
                } else if (chunk.text.endsWith("،")) {
                    (baseDuration * 0.18).toLong()
                } else {
                    0L
                }
                delay(baseDuration + punctuationBonus)
                if (_state.value.phase != ReadPhase.READING) return@launch
                val next = current.chunkIndex + 1
                if (next >= chunks.size) {
                    finishReading()
                    return@launch
                }
                chunksShown = maxOf(chunksShown, next + 1)
                val nextChunk = chunks[next]
                _state.update {
                    it.copy(
                        chunkIndex = next,
                        currentChunkText = nextChunk.text,
                        currentChunkPivotIndex = nextChunk.pivotIndex,
                        progress = (next + 1).toFloat() / chunks.size,
                    )
                }
            }
        }
    }

    fun stopEarly() {
        if (_state.value.phase == ReadPhase.SETUP) return
        finishReading()
    }

    private fun measuredElapsedMillis(): Long {
        val now = SystemClock.elapsedRealtime()
        val inPause =
            if (_state.value.phase == ReadPhase.PAUSED) now - pauseStartedAt else 0L
        return (now - sessionStartMonotonic - pausedAccumulated - inPause).coerceAtLeast(1L)
    }

    private fun finishReading() {
        clockJob?.cancel()
        val current = _state.value
        val elapsed = measuredElapsedMillis()
        val wordsRead = chunks.take(chunksShown).sumOf { it.wordCount }
        val rawWpm = ReadingEngine.wordsPerMinute(wordsRead, elapsed)
        _state.update {
            it.copy(
                phase = ReadPhase.QUIZ,
                questions = current.selectedText.questions,
                questionIndex = 0,
                answers = List(current.selectedText.questions.size) { null },
                wordsRead = wordsRead,
                readingMillis = elapsed,
                rawWpm = rawWpm,
                progress = 1f,
            )
        }
    }

    fun answer(index: Int) {
        val current = _state.value
        if (current.phase != ReadPhase.QUIZ) return
        val answers = current.answers.toMutableList()
        answers[current.questionIndex] = index
        _state.update { it.copy(answers = answers) }
    }

    fun submitQuiz() {
        val current = _state.value
        if (current.phase != ReadPhase.QUIZ) return
        val chosen = current.answers.getOrNull(current.questionIndex) ?: return
        val q = current.questions.getOrNull(current.questionIndex)
        if (q != null) {
            container.audioEngine.playSfx(
                if (chosen == q.correctIndex) dev.mrsad.app.audio.SfxType.TARGET_HIT
                else dev.mrsad.app.audio.SfxType.TARGET_MISS,
            )
        }
        if (current.questionIndex < current.questions.lastIndex) {
            _state.update { it.copy(questionIndex = current.questionIndex + 1) }
            return
        }
        val result = ReadingEngine.score(current.answers, current.questions)
        container.audioEngine.playSfx(dev.mrsad.app.audio.SfxType.SESSION_COMPLETE)
        val effective = ReadingEngine.effectiveWpm(
            wordsRead = current.wordsRead,
            elapsedMillis = current.readingMillis,
            comprehensionPercent = result.percent,
        )
        _state.update {
            it.copy(
                phase = ReadPhase.RESULT,
                comprehensionPercent = result.percent,
                effectiveWpm = effective,
                saved = false,
            )
        }
        persist(current.readingMillis, effective, result.percent)
    }

    private fun persist(readingMillis: Long, effectiveWpm: Int, comprehension: Int) {
        viewModelScope.launch {
            val now = MrsadClock.nowEpochMillis()
            val minutes = ((readingMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            runCatching {
                container.database.activityDao().insert(
                    ActivityEventEntity(
                        module = "READ",
                        dateIso = MrsadClock.isoFor(now),
                        finishedAtMillis = now,
                        minutes = minutes,
                        primaryMetric = effectiveWpm.toDouble(),
                        secondaryMetric = comprehension.toDouble(),
                        payload = "rawWpm=${_state.value.rawWpm}",
                    )
                )
            }
            _state.update { it.copy(saved = true) }
        }
    }

    fun reset() {
        clockJob?.cancel()
        _state.update {
            ReadUiState(
                selectedLevelFilter = it.selectedLevelFilter,
                selectedTextId = it.selectedTextId,
                wpm = it.wpm,
                wordsPerDisplay = it.wordsPerDisplay,
            )
        }
    }

    override fun onCleared() {
        clockJob?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReadViewModel(container) as T
    }
}
