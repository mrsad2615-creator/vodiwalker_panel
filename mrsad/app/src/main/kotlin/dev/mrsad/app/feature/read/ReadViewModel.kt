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
    val selectedTextId: String = ReadTexts.all.first().id,
    val wpm: Int = 300,
    val wordsPerDisplay: Int = 1,
    val chunkIndex: Int = 0,
    val chunkCount: Int = 0,
    val currentChunkText: String = "",
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
 * RSVP session controller.
 *
 * Presentation speed is driven by the configured WPM, but the *measured*
 * reading speed always uses elapsed monotonic time with pauses subtracted.
 * Chunk deadlines are absolute, so a slow frame can never speed up or slow
 * down the presentation relative to the configured wpm.
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

    fun selectText(id: String) {
        if (_state.value.phase != ReadPhase.SETUP) return
        _state.update { it.copy(selectedTextId = id) }
    }

    fun start() {
        val current = _state.value
        chunks = ReadingEngine.chunk(current.selectedText.body, current.wordsPerDisplay)
        if (chunks.isEmpty()) return
        chunksShown = 1
        pausedAccumulated = 0L
        sessionStartMonotonic = SystemClock.elapsedRealtime()
        _state.update {
            it.copy(
                phase = ReadPhase.READING,
                chunkIndex = 0,
                chunkCount = chunks.size,
                currentChunkText = chunks.first().text,
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

    /** Absolute-deadline presentation loop; safe against slow frames. */
    fun runPresentationClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            while (_state.value.phase == ReadPhase.READING) {
                val current = _state.value
                val chunk = chunks.getOrNull(current.chunkIndex) ?: return@launch
                delay(ReadingEngine.chunkDurationMillis(current.wpm, chunk.wordCount))
                if (_state.value.phase != ReadPhase.READING) return@launch
                val next = current.chunkIndex + 1
                if (next >= chunks.size) {
                    finishReading()
                    return@launch
                }
                chunksShown = next + 1
                _state.update {
                    it.copy(
                        chunkIndex = next,
                        currentChunkText = chunks[next].text,
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
        if (current.answers.getOrNull(current.questionIndex) == null) return
        if (current.questionIndex < current.questions.lastIndex) {
            _state.update { it.copy(questionIndex = it.questionIndex + 1) }
            return
        }
        val result = ReadingEngine.score(current.answers, current.questions)
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
