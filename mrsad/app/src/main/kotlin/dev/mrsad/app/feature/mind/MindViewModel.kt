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
    NUMBER_MEMORY("حافظهٔ عددی", "توالی ارقام را در حافظهٔ کاری نگه دار و بازنویسی کن", "حافظه"),
    PATTERN_MEMORY("حافظهٔ فضایی", "الگوی خانه‌های روشن را در شبکه بازسازی کن", "حافظه"),
    ATTENTION("توجهٔ انتخابی", "همهٔ نمادهای هدف را میان محرک‌های مشابه پیدا کن", "توجه"),
    STROOP("مهار تداخل استروپ", "رنگ جوهر نوشته را مستقل از معنی کلمه تشخیص بده", "توجه"),
    ARITHMETIC("سرعت محاسبهٔ ذهنی", "عملیات چهارگانه را در کمترین زمان حل کن", "پردازش"),
    LOGIC("استدلال و کشف الگو", "قاعدهٔ ریاضی دنباله را کشف و کامل کن", "منطق"),
    REACTION("سرعت واکنش عصبی", "به‌محض ظهور محرک دیداری با کمترین تأخیر ضربه بزن", "واکنش"),
}

data class MindUiState(
    val screen: MindScreenState = MindScreenState.LOBBY,
    val selectedCategoryFilter: String = "ALL",
    val exercise: MindExercise? = null,
    val level: Int = 0,
    val nextLevel: Int = 0,
    val levels: Map<MindExercise, Int> = emptyMap(),
    val totalSessions: Int = 0,
    val averageScore: Double = 0.0,
    val bestScores: Map<MindExercise, Double> = emptyMap(),
    // current round
    val roundCounter: Int = 1,
    val questionIndex: Int = 0,
    val questionCount: Int = 5,
    val correctAnswers: Int = 0,
    val stageScores: List<Double> = emptyList(),
    val lastStageFeedbackFa: String? = null,
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
    fun levelOf(exercise: MindExercise): Int = levels[exercise] ?: 0

    val practisedExercises: Int get() = levels.size

    val progress: Float
        get() = if (questionCount <= 0) 0f else (questionIndex + 1).toFloat() / questionCount

    val stroopItem: MindGenerators.StroopItem? get() = stroopQueue.getOrNull(questionIndex)
    val arithmeticItem: MindGenerators.ArithmeticItem? get() = arithmeticQueue.getOrNull(questionIndex)
    val logicSequence: MindGenerators.NumberSequence? get() = logicQueue.getOrNull(questionIndex)
    val reactionTrial: MindGenerators.ReactionTrial? get() = reactionQueue.getOrNull(questionIndex)
}

/**
 * MRSAD MIND controller.
 *
 * Every exercise runs a multi-trial session (`3..10` stages per session),
 * records normalized accuracy/reaction scores in `0.0..1.0`, and updates the
 * per-exercise adaptive difficulty ladder via `MindProgression`.
 */
class MindViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(MindUiState())
    val state: StateFlow<MindUiState> = _state.asStateFlow()

    private var sessionSeed: Long = 0L
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
            val normalizedSecondary = events.map { ev ->
                if (ev.secondaryMetric > 1.0) (ev.secondaryMetric / 100.0).coerceIn(0.0, 1.0)
                else ev.secondaryMetric.coerceIn(0.0, 1.0)
            }
            val avg = if (normalizedSecondary.isEmpty()) 0.0 else normalizedSecondary.average()
            val best = events.groupBy { it.payload.substringBefore(';') }
                .mapNotNull { (name, group) ->
                    val exercise = MindExercise.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                    val maxScore = group.maxOf { ev ->
                        if (ev.secondaryMetric > 1.0) (ev.secondaryMetric / 100.0).coerceIn(0.0, 1.0)
                        else ev.secondaryMetric.coerceIn(0.0, 1.0)
                    }
                    exercise to maxScore
                }
                .toMap()

            val chronological = events.asReversed()
            scoreHistory.clear()
            chronological.forEach { event ->
                val exercise = MindExercise.entries
                    .firstOrNull { it.name == event.payload.substringBefore(';') } ?: return@forEach
                val norm = if (event.secondaryMetric > 1.0) {
                    (event.secondaryMetric / 100.0).coerceIn(0.0, 1.0)
                } else {
                    event.secondaryMetric.coerceIn(0.0, 1.0)
                }
                scoreHistory.getOrPut(exercise) { mutableListOf() }.add(norm)
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

    fun setCategoryFilter(category: String) {
        _state.update { it.copy(selectedCategoryFilter = category) }
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
                stageScores = emptyList(),
                lastStageFeedbackFa = null,
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
                        questionIndex = 0,
                        questionCount = 3,
                        stageScores = emptyList(),
                        lastStageFeedbackFa = null,
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
                        questionIndex = 0,
                        questionCount = 3,
                        stageScores = emptyList(),
                        lastStageFeedbackFa = null,
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
                        questionCount = 3,
                        stageScores = emptyList(),
                        lastStageFeedbackFa = null,
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
                        lastStageFeedbackFa = null,
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
                        lastStageFeedbackFa = null,
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
                        lastStageFeedbackFa = null,
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
                        lastStageFeedbackFa = null,
                        rememberTimer = it.rememberTimer + 1,
                        roundStartMonotonic = SystemClock.elapsedRealtime(),
                    )
                }
            }
        }
    }

    // ---------------- Number memory (3 progressive stages) ----------------

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
        container.audioEngine.playSfx(
            if (correct) dev.mrsad.app.audio.SfxType.TARGET_HIT else dev.mrsad.app.audio.SfxType.TARGET_MISS,
        )
        val stageScore = if (correct) 1.0 else 0.0
        val updatedScores = current.stageScores + stageScore
        val nextIndex = current.questionIndex + 1

        if (nextIndex < current.questionCount) {
            val bonusLevel = current.level + if (updatedScores.all { it >= 1.0 }) 1 else 0
            val nextSeed = sessionSeed + (current.roundCounter * 31L + nextIndex) * 7919L
            val nextDigits = MindGenerators.digitSpan(nextSeed, bonusLevel)
            _state.update {
                it.copy(
                    questionIndex = nextIndex,
                    correctAnswers = it.correctAnswers + if (correct) 1 else 0,
                    stageScores = updatedScores,
                    digits = nextDigits,
                    entry = "",
                    showingStimulus = true,
                    rememberTimer = it.rememberTimer + 1,
                    lastStageFeedbackFa = if (correct) {
                        "مرحلهٔ ${current.questionIndex + 1} درست بود! آمادهٔ مرحلهٔ بعدی شوید"
                    } else {
                        "در مرحلهٔ قبل توالی درست ${current.digits} بود — تمرکز روی مرحلهٔ جدید"
                    },
                )
            }
        } else {
            val totalCorrect = current.correctAnswers + if (correct) 1 else 0
            val avgScore = updatedScores.average().coerceIn(0.0, 1.0)
            finishSingleRound(
                score = avgScore,
                detail = "مراحل درست: $totalCorrect از ${current.questionCount} • طول آخرین توالی: ${current.digits.length} رقم",
            )
        }
    }

    // ---------------- Pattern memory (3 progressive stages) ----------------

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
        val stageScore = ((hits - misses).toDouble() / grid.litCells.size).coerceIn(0.0, 1.0)
        container.audioEngine.playSfx(
            if (stageScore >= 0.75) dev.mrsad.app.audio.SfxType.TARGET_HIT else dev.mrsad.app.audio.SfxType.TARGET_MISS,
        )
        val updatedScores = current.stageScores + stageScore
        val nextIndex = current.questionIndex + 1

        if (nextIndex < current.questionCount) {
            val bonusLevel = current.level + if (stageScore >= 1.0) 1 else 0
            val nextSeed = sessionSeed + (current.roundCounter * 37L + nextIndex) * 7919L
            val nextGrid = MindGenerators.patternMemory(nextSeed, bonusLevel)
            _state.update {
                it.copy(
                    questionIndex = nextIndex,
                    stageScores = updatedScores,
                    patternGrid = nextGrid,
                    selectedCells = emptySet(),
                    showingStimulus = true,
                    rememberTimer = it.rememberTimer + 1,
                    lastStageFeedbackFa = "مرحلهٔ ${current.questionIndex + 1}: $hits از ${grid.litCells.size} خانه درست (${misses} خطا)",
                )
            }
        } else {
            val avgScore = updatedScores.average().coerceIn(0.0, 1.0)
            finishSingleRound(
                score = avgScore,
                detail = "میانگین دقت بازسازی الگو در ${current.questionCount} مرحله: ${(avgScore * 100).toInt()}٪",
            )
        }
    }

    // ---------------- Attention (3 progressive stages) ----------------

    fun submitAttention() {
        val current = _state.value
        val grid = current.attentionGrid ?: return
        val selection = current.selectedCells
        val hits = selection.count { it in grid.targetIndices }
        val falsePositives = selection.size - hits
        val missed = grid.targetIndices.size - hits
        val total = grid.targetIndices.size
        val stageScore = ((total - missed - falsePositives).toDouble() / total).coerceIn(0.0, 1.0)
        container.audioEngine.playSfx(
            if (stageScore >= 0.75) dev.mrsad.app.audio.SfxType.TARGET_HIT else dev.mrsad.app.audio.SfxType.TARGET_MISS,
        )
        val updatedScores = current.stageScores + stageScore
        val nextIndex = current.questionIndex + 1

        if (nextIndex < current.questionCount) {
            val nextSeed = sessionSeed + (current.roundCounter * 43L + nextIndex) * 7919L
            val nextGrid = MindGenerators.attentionGrid(nextSeed, current.level + if (stageScore >= 1.0) 1 else 0)
            _state.update {
                it.copy(
                    questionIndex = nextIndex,
                    stageScores = updatedScores,
                    attentionGrid = nextGrid,
                    selectedCells = emptySet(),
                    roundStartMonotonic = SystemClock.elapsedRealtime(),
                    lastStageFeedbackFa = "مرحلهٔ ${current.questionIndex + 1}: $hits از $total نماد هدف پیدا شد (${falsePositives} انتخاب نادرست)",
                )
            }
        } else {
            val avgScore = updatedScores.average().coerceIn(0.0, 1.0)
            finishSingleRound(
                score = avgScore,
                detail = "میانگین توجه انتخابی در ${current.questionCount} مرحله: ${(avgScore * 100).toInt()}٪",
            )
        }
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
            // Early tap before stimulus — counts as a false-start penalty
            advanceReaction(
                correct = false,
                reactionMillis = 700L,
                feedbackFa = "ضربهٔ زودهنگام! پیش از روشن شدن سیگنال ضربه زدید",
            )
            return
        }
        val elapsed = (SystemClock.elapsedRealtime() - current.roundStartMonotonic).coerceAtLeast(120L)
        advanceReaction(
            correct = elapsed <= 650L,
            reactionMillis = elapsed,
            feedbackFa = "زمان واکنش مرحلهٔ ${current.questionIndex + 1}: $elapsed میلی‌ثانیه",
        )
    }

    private fun advanceReaction(correct: Boolean, reactionMillis: Long, feedbackFa: String) {
        container.audioEngine.playSfx(
            if (correct) dev.mrsad.app.audio.SfxType.TARGET_HIT else dev.mrsad.app.audio.SfxType.TARGET_MISS,
        )
        val current = _state.value
        val nextIndex = current.questionIndex + 1
        val newCorrect = current.correctAnswers + if (correct) 1 else 0
        val reactions = current.reactionTimes + reactionMillis
        if (nextIndex >= current.questionCount) {
            val avgReaction = reactions.average().toLong()
            val normalizedScore = (MindGenerators.scoreReactionMillis(avgReaction) / 100.0).coerceIn(0.0, 1.0)
            finishSingleRound(
                score = normalizedScore,
                detail = "میانگین زمان واکنش: ${avgReaction} میلی‌ثانیه (${newCorrect} از ${current.questionCount} واکنش معتبر)",
            )
        } else {
            _state.update {
                it.copy(
                    questionIndex = nextIndex,
                    correctAnswers = newCorrect,
                    reactionTimes = reactions,
                    reactionSignalArmed = false,
                    lastStageFeedbackFa = feedbackFa,
                    rememberTimer = it.rememberTimer + 1,
                )
            }
        }
    }

    private fun advanceQueue(correct: Boolean, reactionMillis: Long) {
        container.audioEngine.playSfx(
            if (correct) dev.mrsad.app.audio.SfxType.TARGET_HIT else dev.mrsad.app.audio.SfxType.TARGET_MISS,
        )
        val current = _state.value
        val nextIndex = current.questionIndex + 1
        val newCorrect = current.correctAnswers + if (correct) 1 else 0
        val reactions = current.reactionTimes + reactionMillis
        if (nextIndex >= current.questionCount) {
            val score = (newCorrect.toDouble() / current.questionCount).coerceIn(0.0, 1.0)
            val avgReaction = reactions.average().toLong()
            finishSingleRound(
                score = score,
                detail = "پاسخ درست: $newCorrect از ${current.questionCount} • میانگین زمان پاسخ: ${avgReaction} میلی‌ثانیه",
            )
        } else {
            _state.update {
                it.copy(
                    questionIndex = nextIndex,
                    correctAnswers = newCorrect,
                    reactionTimes = reactions,
                    lastStageFeedbackFa = if (correct) "پاسخ درست ثبت شد" else "پاسخ نادرست — به الگوی بعدی دقت کنید",
                    roundStartMonotonic = SystemClock.elapsedRealtime(),
                )
            }
        }
    }

    // ---------------- Round lifecycle ----------------

    private fun finishSingleRound(score: Double, detail: String) {
        container.audioEngine.playSfx(dev.mrsad.app.audio.SfxType.SESSION_COMPLETE)
        val normalizedScore = score.coerceIn(0.0, 1.0)
        val current = _state.value
        val exercise = current.exercise
        val average = if (current.totalSessions == 0) normalizedScore
        else (current.averageScore * current.totalSessions + normalizedScore) / (current.totalSessions + 1)

        var newLevels = current.levels
        var nextLevel = current.level
        if (exercise != null) {
            val history = scoreHistory.getOrPut(exercise) { mutableListOf() }
            history += normalizedScore
            nextLevel = MindProgression.levelFrom(history)
            newLevels = current.levels + (exercise to nextLevel)
        }
        val levelUp = nextLevel > current.level
        val levelDown = nextLevel < current.level

        _state.update {
            it.copy(
                screen = MindScreenState.RESULT,
                lastScore = normalizedScore,
                resultDetailFa = detail,
                averageScore = average,
                totalSessions = current.totalSessions + 1,
                levels = newLevels,
                nextLevel = nextLevel,
                levelAdjusted = levelUp || levelDown,
                levelUp = levelUp,
                bestScores = it.bestScores.toMutableMap().apply {
                    val ex = it.exercise ?: return@apply
                    put(ex, maxOf(get(ex) ?: 0.0, normalizedScore))
                },
                saved = false,
            )
        }
        persist(normalizedScore)
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
                        primaryMetric = (score * 100.0).coerceIn(0.0, 100.0),
                        secondaryMetric = score.coerceIn(0.0, 1.0),
                        payload = exercise.name,
                    )
                )
            }
            _state.update { it.copy(saved = true) }
        }
    }

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
                stageScores = emptyList(),
                lastStageFeedbackFa = null,
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
                lastStageFeedbackFa = null,
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
