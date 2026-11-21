package dev.mrsad.app.feature.mind

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.db.ActivityEventEntity
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.app.util.firstValue
import dev.mrsad.core.mind.MindGenerators
import dev.mrsad.core.mind.MindProgression
import dev.mrsad.core.random.SplitMix64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MindScreenState { LOBBY, PLAYING, RESULT }

enum class MindExercise(val titleFa: String, val subtitleFa: String, val categoryFa: String) {
    NUMBER_MEMORY("حافظهٔ عددی", "توالی اعداد را به خاطر بسپار", "حافظه"),
    PATTERN_MEMORY("حافظهٔ بصری", "الگوی خانه‌ها را بازسازی کن", "حافظه"),
    ATTENTION("توجهٔ انتخابی", "هدف را میان حواس‌پرتی پیدا کن", "توجه"),
    STROOP("اثر استروپ", "رنگ را از معنی جدا کن", "توجه"),
    ARITHMETIC("سرعت محاسبه", "در زمان محدود حساب کن", "پردازش"),
    LOGIC("استدلال منطقی", "الگوی عددی را کامل کن", "منطق"),
    REACTION("سرعت واکنش", "به‌محض ظهور محرک دیداری ضربه بزن", "واکنش"),
}

data class MindUiState(
    val screen: MindScreenState = MindScreenState.LOBBY,
    val exercise: MindExercise? = null,
    // adaptive level & history
    /**
     * The difficulty ladder of the exercise being played, 0-based.
     * Each exercise carries its own ladder — see [levels].
     */
    val level: Int = 0,
    /** The level the ladder reaches after the round that just finished. */
    val nextLevel: Int = 0,
    /** Per-exercise difficulty levels, derived from each exercise's own scores. */
    val levels: Map<MindExercise, Int> = emptyMap(),
    val totalSessions: Int = 0,
    val averageScore: Double = 0.0,
    val bestScores: Map<MindExercise, Double> = emptyMap(),
    // current round
    val roundCounter: Int = 1,
    val questionIndex: Int = 0,
    val questionCount: Int = 5,
    val correctAnswers: Int = 0,
    val roundStartMonotonic: Long = 0L,
    val reactionTimes: List<Long> = emptyList(),
    // number memory
    val digits: String = "",
    val showingStimulus: Boolean = false,
    val entry: String = "",
    val rememberTimer: Long = 0L,
    // pattern memory
    val patternGrid: MindGenerators.PatternGrid? = null,
    val selectedCells: Set<Int> = emptySet(),
    // attention
    val attentionGrid: MindGenerators.AttentionGrid? = null,
    // stroop / arithmetic / logic / reaction queues
    val stroopQueue: List<MindGenerators.StroopItem> = emptyList(),
    val arithmeticQueue: List<MindGenerators.ArithmeticItem> = emptyList(),
    val logicQueue: List<MindGenerators.NumberSequence> = emptyList(),
    val reactionQueue: List<MindGenerators.ReactionTrial> = emptyList(),
    val reactionSignalArmed: Boolean = false,
    // result of last session
    val lastScore: Double = 0.0,
    val resultDetailFa: String = "",
    val levelAdjusted: Boolean = false,
    val levelUp: Boolean = false,
    val saved: Boolean = false,
) {
    /** The difficulty level of one exercise, 0 when it has never been played. */
    fun levelOf(exercise: MindExercise): Int = levels[exercise] ?: 0

    /** The number of exercises the user has real history for. */
    val practisedExercises: Int get() = levels.size

    val progress: Float
        get() = when (exercise) {
            MindExercise.NUMBER_MEMORY, MindExercise.PATTERN_MEMORY ->
                if (screen == MindScreenState.PLAYING) 0.5f else 0f
            else -> if (questionCount == 0) 0f else questionIndex.toFloat() / questionCount
        }

    val stroopItem: MindGenerators.StroopItem? get() = stroopQueue.getOrNull(questionIndex)
    val arithmeticItem: MindGenerators.ArithmeticItem? get() = arithmeticQueue.getOrNull(questionIndex)
    val logicSequence: MindGenerators.NumberSequence? get() = logicQueue.getOrNull(questionIndex)
    val reactionTrial: MindGenerators.ReactionTrial? get() = reactionQueue.getOrNull(questionIndex)
}

/**
 * MRSAD MIND controller.
 *
 * Scoring is always "fraction of correct responses", reaction time comes from
 * the monotonic clock, and adaptive level updates follow the ladder in
 * `dev.mrsad.core.mind.MindProgression`: a level moves only after two
 * consecutive strong (≥ 85 %) or two consecutive weak (< 50 %) rounds, and
 * **every exercise carries its own ladder**, so a skill is never dragged up
 * or down by an unrelated one. The level is a pure function of the recorded
 * scores, so it survives a restart unchanged.
 *
 * Every finished session is persisted to the activity log so Home/Analytics/
 * Coach can use real data.
 */
class MindViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(MindUiState())
    val state: StateFlow<MindUiState> = _state.asStateFlow()

    private var sessionSeed: Long = 0L

    /**
     * The recorded scores of every exercise, oldest first.
     *
     * This mirrors what the activity log holds; it is kept in memory so the
     * ladder can be advanced the moment a round finishes, without waiting
     * for the database round trip. Nothing here changes what is persisted.
     */
    private val scoreHistory = LinkedHashMap<MindExercise, MutableList<Double>>()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val events = container.database.activityDao()
                .recentEvents(System.currentTimeMillis() - 90L * 86_400_000L, 200)
                .firstValue()
                .filter { it.module == "MIND" }
            val avg = if (events.isEmpty()) 0.0 else events.map { it.secondaryMetric }.average()
            val best = events.groupBy { it.payload.substringBefore(';') }
                .mapNotNull { (name, group) ->
                    val exercise = MindExercise.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                    exercise to group.maxOf { it.secondaryMetric }
                }
                .toMap()
            // The DAO returns newest first; the ladder needs oldest first.
            val chronological = events.asReversed()
            scoreHistory.clear()
            chronological.forEach { event ->
                val exercise = MindExercise.entries
                    .firstOrNull { it.name == event.payload.substringBefore(';') } ?: return@forEach
                scoreHistory.getOrPut(exercise) { mutableListOf() }.add(event.secondaryMetric)
            }
            val levels = scoreHistory.mapValues { (_, scores) ->
                MindProgression.levelFrom(scores)
            }
            val lastExercise = chronological.lastOrNull()?.payload?.substringBefore(';')
                ?.let { name -> MindExercise.entries.firstOrNull { it.name == name } }
            _state.update {
                it.copy(
                    totalSessions = events.size,
                    averageScore = avg,
                    bestScores = best,
                    levels = levels,
                    level = lastExercise?.let { e -> levels[e] ?: 0 } ?: 0,
                )
            }
        }
    }

    fun openExercise(exercise: MindExercise) {
        sessionSeed = System.currentTimeMillis() xor SplitMix64.seedFor(exercise.name)
        val level = _state.value.levelOf(exercise)
        _state.update {
            it.copy(
                screen = MindScreenState.PLAYING,
                exercise = exercise,
                level = level,
                nextLevel = level,
                roundCounter = 1,
                questionIndex = 0,
                correctAnswers = 0,
                selectedCells = emptySet(),
                entry = "",
                saved = false,
                reactionTimes = emptyList(),
            )
        }
        startRound()
    }

    private fun startRound() {
        val current = _state.value
        val exercise = current.exercise ?: return
        val level = current.level
        val seed = sessionSeed + current.roundCounter * 7919L
        when (exercise) {
            MindExercise.NUMBER_MEMORY -> {
                val digits = MindGenerators.digitSpan(seed, level)
                _state.update {
                    it.copy(
                        digits = digits,
                        entry = "",
                        showingStimulus = true,
                        rememberTimer = it.rememberTimer + 1,
                        questionCount = 1,
                    )
                }
            }
            MindExercise.PATTERN_MEMORY -> {
                val grid = MindGenerators.patternMemory(seed, level)
                _state.update {
                    it.copy(
                        patternGrid = grid,
                        selectedCells = emptySet(),
                        showingStimulus = true,
                        rememberTimer = it.rememberTimer + 1,
                        questionCount = 1,
                    )
                }
            }
            MindExercise.ATTENTION -> {
                val grid = MindGenerators.attentionGrid(seed, level)
                _state.update {
                    it.copy(
                        attentionGrid = grid,
                        selectedCells = emptySet(),
                        roundStartMonotonic = SystemClock.elapsedRealtime(),
                        questionIndex = 0,
                        questionCount = 1,
                    )
                }
            }
            MindExercise.STROOP -> {
                val count = 6 + minOf(level, 4)
                _state.update {
                    it.copy(
                        stroopQueue = MindGenerators.stroop(seed, count),
                        questionIndex = 0,
                        questionCount = count,
                        correctAnswers = 0,
                        roundStartMonotonic = SystemClock.elapsedRealtime(),
                    )
                }
            }
            MindExercise.ARITHMETIC -> {
                val count = 6 + minOf(level, 4)
                _state.update {
                    it.copy(
                        arithmeticQueue = MindGenerators.arithmetic(seed, level, count),
                        questionIndex = 0,
                        questionCount = count,
                        correctAnswers = 0,
                        roundStartMonotonic = SystemClock.elapsedRealtime(),
                    )
                }
            }
            MindExercise.LOGIC -> {
                val count = 4 + minOf(level, 3)
                _state.update {
                    it.copy(
                        logicQueue = MindGenerators.logicSequences(seed, level, count),
                        questionIndex = 0,
                        questionCount = count,
                        correctAnswers = 0,
                        roundStartMonotonic = SystemClock.elapsedRealtime(),
                    )
                }
            }
            MindExercise.REACTION -> {
                val count = 5
                _state.update {
                    it.copy(
                        reactionQueue = MindGenerators.reactionTrials(seed, level, count),
                        questionIndex = 0,
                        questionCount = count,
                        correctAnswers = 0,
                        reactionSignalArmed = false,
                        rememberTimer = it.rememberTimer + 1,
                        roundStartMonotonic = SystemClock.elapsedRealtime(),
                    )
                }
            }
        }
    }

    // ---------------- Number memory ----------------

    fun revealDigits() {
        if (_state.value.exercise != MindExercise.NUMBER_MEMORY) return
        _state.update { it.copy(showingStimulus = false) }
    }

    fun appendDigit(digit: Int, haptics: Boolean) {
        val current = _state.value
        if (current.showingStimulus || current.entry.length >= current.digits.length) return
        _state.update { it.copy(entry = it.entry + digit) }
    }

    fun deleteDigit() {
        _state.update { if (it.entry.isEmpty()) it else it.copy(entry = it.entry.dropLast(1)) }
    }

    fun submitNumberMemory() {
        val current = _state.value
        val correct = current.entry == current.digits
        finishSingleRound(
            score = if (correct) 1.0 else 0.0,
            detail = if (correct) {
                "درست بود! طول توالی: ${current.digits.length} رقم"
            } else {
                "توالی درست: ${current.digits} — پاسخ شما: ${current.entry.ifEmpty { "خالی" }}"
            },
        )
    }

    // ---------------- Pattern memory ----------------

    fun beginPatternRecall() {
        if (_state.value.exercise != MindExercise.PATTERN_MEMORY) return
        if (!_state.value.showingStimulus) return
        _state.update { it.copy(showingStimulus = false, selectedCells = emptySet()) }
    }

    fun toggleCell(index: Int, haptics: Boolean) {
        val current = _state.value
        if (current.showingStimulus) return
        val selection = current.selectedCells.toMutableSet()
        if (!selection.add(index)) selection.remove(index)
        _state.update { it.copy(selectedCells = selection) }
    }

    fun submitPatternMemory() {
        val current = _state.value
        val grid = current.patternGrid ?: return
        val selection = current.selectedCells
        val hits = selection.count { it in grid.litCells }
        val misses = selection.count { it !in grid.litCells }
        val score = ((hits - misses).toDouble() / grid.litCells.size).coerceIn(0.0, 1.0)
        finishSingleRound(
            score = score,
            detail = "درست: ${hits} از ${grid.litCells.size} — انتخاب نادرست: $misses",
        )
    }

    // ---------------- Attention ----------------

    fun submitAttention() {
        val current = _state.value
        val grid = current.attentionGrid ?: return
        val selection = current.selectedCells
        val hits = selection.count { it in grid.targetIndices }
        val falsePositives = selection.size - hits
        val missed = grid.targetIndices.size - hits
        val total = grid.targetIndices.size
        val score = ((total - missed - falsePositives).toDouble() / total).coerceIn(0.0, 1.0)
        finishSingleRound(
            score = score,
            detail = "درست: $hits از $total — انتخاب اشتباه: $falsePositives",
        )
    }

    // ---------------- Stroop / arithmetic / logic ----------------

    fun answerStroop(index: Int, haptics: Boolean) {
        val current = _state.value
        val item = current.stroopQueue.getOrNull(current.questionIndex) ?: return
        val now = SystemClock.elapsedRealtime()
        val reaction = now - current.roundStartMonotonic
        val correct = index == item.correctAnswerIndex
        advanceQueue(correct = correct, reactionMillis = reaction)
    }

    fun answerArithmetic(index: Int, haptics: Boolean) {
        val current = _state.value
        val item = current.arithmeticQueue.getOrNull(current.questionIndex) ?: return
        val now = SystemClock.elapsedRealtime()
        val reaction = now - current.roundStartMonotonic
        val correct = index == item.correctIndex
        advanceQueue(correct = correct, reactionMillis = reaction)
    }

    fun answerLogic(index: Int, haptics: Boolean) {
        val current = _state.value
        val item = current.logicQueue.getOrNull(current.questionIndex) ?: return
        val now = SystemClock.elapsedRealtime()
        val reaction = now - current.roundStartMonotonic
        val correct = index == item.correctIndex
        advanceQueue(correct = correct, reactionMillis = reaction)
    }

    fun armReactionSignal() {
        val current = _state.value
        if (current.exercise != MindExercise.REACTION) return
        _state.update {
            it.copy(
                reactionSignalArmed = true,
                roundStartMonotonic = SystemClock.elapsedRealtime(),
            )
        }
    }

    fun tapReaction() {
        val current = _state.value
        if (current.exercise != MindExercise.REACTION) return
        if (!current.reactionSignalArmed) {
            // Early tap before stimulus — counts as a miss with 900ms penalty
            advanceReaction(correct = false, reactionMillis = 900L)
            return
        }
        val elapsed = (SystemClock.elapsedRealtime() - current.roundStartMonotonic).coerceAtLeast(120L)
        advanceReaction(correct = elapsed <= 650L, reactionMillis = elapsed)
    }

    private fun advanceReaction(correct: Boolean, reactionMillis: Long) {
        val current = _state.value
        val nextIndex = current.questionIndex + 1
        val newCorrect = current.correctAnswers + if (correct) 1 else 0
        val reactions = current.reactionTimes + reactionMillis
        if (nextIndex >= current.questionCount) {
            val avgReaction = reactions.average().toLong()
            val score = MindGenerators.scoreReactionMillis(avgReaction)
            finishSingleRound(
                score = score,
                detail = "میانگین زمان واکنش: ${avgReaction} میلی‌ثانیه (${newCorrect} از ${current.questionCount} واکنش به‌موقع)",
            )
        } else {
            _state.update {
                it.copy(
                    questionIndex = nextIndex,
                    correctAnswers = newCorrect,
                    reactionTimes = reactions,
                    reactionSignalArmed = false,
                    rememberTimer = it.rememberTimer + 1,
                )
            }
        }
    }

    private fun advanceQueue(correct: Boolean, reactionMillis: Long) {
        val current = _state.value
        val nextIndex = current.questionIndex + 1
        val newCorrect = current.correctAnswers + if (correct) 1 else 0
        val reactions = current.reactionTimes + reactionMillis
        if (nextIndex >= current.questionCount) {
            val score = newCorrect.toDouble() / current.questionCount
            val avgReaction = reactions.average().toLong()
            finishSingleRound(
                score = score,
                detail = "پاسخ درست: $newCorrect از ${current.questionCount} — میانگین زمان پاسخ: ${avgReaction} میلی‌ثانیه",
            )
        } else {
            _state.update {
                it.copy(
                    questionIndex = nextIndex,
                    correctAnswers = newCorrect,
                    reactionTimes = reactions,
                    roundStartMonotonic = SystemClock.elapsedRealtime(),
                )
            }
        }
    }

    // ---------------- Round lifecycle ----------------

    private fun finishSingleRound(score: Double, detail: String) {
        val current = _state.value
        val exercise = current.exercise
        val average = if (current.totalSessions == 0) score
        else (current.averageScore * current.totalSessions + score) / (current.totalSessions + 1)

        // The ladder is a pure function of the recorded scores, so the level
        // after this round is the level the history implies — no hidden
        // counter that could drift away from what is on disk.
        var newLevels = current.levels
        var nextLevel = current.level
        if (exercise != null) {
            val history = scoreHistory.getOrPut(exercise) { mutableListOf() }
            history += score
            nextLevel = MindProgression.levelFrom(history)
            newLevels = current.levels + (exercise to nextLevel)
        }
        val levelUp = nextLevel > current.level
        val levelDown = nextLevel < current.level

        _state.update {
            it.copy(
                screen = MindScreenState.RESULT,
                lastScore = score,
                resultDetailFa = detail,
                averageScore = average,
                totalSessions = current.totalSessions + 1,
                levels = newLevels,
                nextLevel = nextLevel,
                levelAdjusted = levelUp || levelDown,
                levelUp = levelUp,
                bestScores = it.bestScores.toMutableMap().apply {
                    val ex = it.exercise ?: return@apply
                    put(ex, maxOf(get(ex) ?: 0.0, score))
                },
                saved = false,
            )
        }
        persist(score)
    }

    private fun persist(score: Double) {
        val current = _state.value
        val exercise = current.exercise ?: return
        val minutes = when (exercise) {
            MindExercise.NUMBER_MEMORY, MindExercise.PATTERN_MEMORY -> 2
            MindExercise.ATTENTION -> 2
            else -> 3
        }
        viewModelScope.launch(Dispatchers.IO) {
            val now = MrsadClock.nowEpochMillis()
            runCatching {
                container.database.activityDao().insert(
                    ActivityEventEntity(
                        module = "MIND",
                        dateIso = MrsadClock.isoFor(now),
                        finishedAtMillis = now,
                        minutes = minutes,
                        primaryMetric = score * 100.0,
                        secondaryMetric = score,
                        payload = exercise.name,
                    )
                )
            }
            _state.update { it.copy(saved = true) }
        }
    }

    /** Applies the adaptive level decided by the finished round. */
    fun restartExercise() {
        val current = _state.value
        val exercise = current.exercise ?: return
        _state.update {
            it.copy(
                screen = MindScreenState.PLAYING,
                level = current.nextLevel,
                levels = it.levels + (exercise to current.nextLevel),
                roundCounter = current.roundCounter + 1,
                questionIndex = 0,
                correctAnswers = 0,
                reactionTimes = emptyList(),
                selectedCells = emptySet(),
            )
        }
        startRound()
    }

    fun backToLobby() {
        _state.update {
            it.copy(
                screen = MindScreenState.LOBBY,
                exercise = null,
                showingStimulus = false,
                selectedCells = emptySet(),
            )
        }
    }

    fun abort() {
        backToLobby()
    }


    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MindViewModel(container) as T
    }
}
