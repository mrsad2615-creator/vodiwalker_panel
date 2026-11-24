package com.neurox.domain

import java.text.Normalizer
import kotlin.math.sqrt
import kotlin.random.Random

/** Only implemented exercises appear as runnable actions. */
enum class Module { VISION, REFLEX, READ, TYPE }
enum class Exercise(val module: Module) {
    FOCUS_LOCK(Module.VISION), QUICK_TAP(Module.REFLEX),
    COMPREHENSION(Module.READ), KEY_PRECISION(Module.TYPE),
    VISUAL_SEARCH(Module.VISION), GO_NO_GO(Module.REFLEX),
    PHRASE_READING(Module.READ), WORD_SPRINT(Module.TYPE),
    PATTERN_MATCH(Module.VISION), SHAPE_REACTION(Module.REFLEX),
    RSVP_READER(Module.READ), CODE_TYPING(Module.TYPE),
    COLOR_REACTION(Module.REFLEX), DIRECTION_MATCH(Module.REFLEX)
}
enum class Language { FA, EN }
enum class Phase { WAITING, ACTIVE, QUESTION, PAUSED, COMPLETE, ABORTED }
enum class Outcome { CORRECT, INCORRECT, PREMATURE, MISSED, WITHHELD }
val Outcome.isCorrect: Boolean get() = this == Outcome.CORRECT || this == Outcome.WITHHELD

data class TrainingConfig(
    val exercise: Exercise,
    val language: Language = Language.FA,
    val difficulty: Int = 1,
    val rounds: Int = 5,
    val responseLimitMs: Long = 5_000,
    val baseline: Boolean = false,
    val stimulusSeed: Long = Random.nextLong(),
    val targetAccuracyPercent: Int = 80,
    val errorAllowance: Int = 30,
    val practice: Boolean = false,
    val customExerciseId: String? = null,
    val customExerciseName: String? = null,
) {
    val effectiveResponseLimitMs: Long get() = (responseLimitMs - (difficulty - 1) * 600L).coerceAtLeast(1000L)
    fun validate(): List<String> = buildList {
        if ((customExerciseId == null) != (customExerciseName == null)) add("custom source fields must be paired")
        if (customExerciseId != null && runCatching { java.util.UUID.fromString(customExerciseId).toString() == customExerciseId }.getOrDefault(false).not()) add("invalid custom exercise identifier")
        if (customExerciseName != null && (customExerciseName.trim().length !in 1..60 || customExerciseName.any { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' })) add("invalid custom exercise name")
        if (targetAccuracyPercent !in 1..100) add("targetAccuracyPercent must be in 1..100")
        if (errorAllowance !in 0..30) add("errorAllowance must be in 0..30")
        if (difficulty !in 1..5) add("difficulty must be in 1..5")
        if (exercise == Exercise.COMPREHENSION && difficulty != 1) add("reading difficulty is fixed at 1 in this milestone")
        if (rounds !in 1..30) add("rounds must be in 1..30")
        if (responseLimitMs !in 1_000..60_000) add("responseLimitMs must be in 1000..60000")
    }
}

fun interface MonotonicClock { fun nowMs(): Long }

data class Attempt(
    val outcome: Outcome,
    val responseMs: Long?,
    val correctCharacters: Int = 0,
    val incorrectCharacters: Int = 0,
    val wordsRead: Int = 0,
    val readingMs: Long = 0,
    val weakKeys: Map<String, Int> = emptyMap(),
) {
    init {
        require(responseMs == null || responseMs in 0..86_400_000)
        require((outcome in listOf(Outcome.MISSED, Outcome.PREMATURE, Outcome.WITHHELD)) == (responseMs == null))
        require(correctCharacters in 0..1000 && incorrectCharacters in 0..1000 && wordsRead in 0..10_000 && readingMs in 0..86_400_000)
        require(weakKeys.size <= 256 && weakKeys.all { it.key.codePointCount(0, it.key.length) == 1 && it.value in 1..1000 })
    }
}

data class Checkpoint(
    val config: TrainingConfig,
    val attempts: List<Attempt>,
    val elapsedMs: Long,
    val completed: Boolean,
) {
    init {
        require(config.validate().isEmpty())
        require(attempts.size <= config.rounds)
        require(elapsedMs in 0..86_400_000)
        require(completed == (attempts.size == config.rounds))
        require(attempts.sumOf { it.responseMs ?: 0 } <= elapsedMs)
        require(attempts.all { it.readingMs <= (it.responseMs ?: 0) })
        val goPlan = if (config.exercise == Exercise.GO_NO_GO) Stimuli.goPlan(config) else null
        require(attempts.withIndex().all { (index, attempt) ->
            attempt.outcome != Outcome.WITHHELD || (goPlan != null && !goPlan[index])
        })
    }
}

data class SessionView(
    val exercise: Exercise,
    val totalRounds: Int,
    val inputRevision: Long,
    val phase: Phase,
    val round: Int,
    val targetCell: Int,
    val elapsedMs: Long,
    val roundElapsedMs: Long,
    val attempts: List<Attempt>,
    val passage: Passage,
    val typingTarget: String,
    val searchBoard: SearchBoard?,
    val goSignal: Boolean,
    val phrase: PhraseFrame?,
    val sprintWordIndex: Int,
    val sprintWordCount: Int,
    val pattern: PatternTrial?,
    val reactionShape: ReactionShape?,
    val reactionColor: ReactionColor?,
    val directionTrial: DirectionTrial?,
    val customName: String?,
    val practice: Boolean,
)

/** Monotonic elapsed time only. Wall-clock timestamps belong to the data layer. */
class TrainingEngine(
    val config: TrainingConfig,
    private val clock: MonotonicClock,
    private val random: Random = Random.Default,
    checkpoint: Checkpoint? = null,
) {
    private var attempts = checkpoint?.attempts?.toList() ?: emptyList()
    private var elapsed = checkpoint?.elapsedMs ?: 0L
    private var anchor = clock.nowMs()
    private var roundAnchor = anchor
    private var readingMs = 0L
    private var waitingMs = 0L
    private var targetCell = 0
    private var phase = Phase.PAUSED
    private var inputRevision = 0L
    private val goPlan = Stimuli.goPlan(config)
    private val passages = Content.passages(config.language)
    private val typingTexts = Content.typing(config.language)
    private var searchBoard: SearchBoard? = null
    private var phraseIndex = 0
    private var phraseAnchor = anchor
    private var phraseChunks = emptyList<String>()
    private var sprintWords = emptyList<String>()
    private var sprintMeasurements = emptyList<TypingMeasurement>()
    private var wordAnchor = anchor
    private var patternTrial: PatternTrial? = null
    private var reactionShape: ReactionShape? = null
    private var reactionColor: ReactionColor? = null
    private var directionTrial: DirectionTrial? = null

    init {
        require(config.validate().isEmpty()) { config.validate().joinToString() }
        require(checkpoint == null || checkpoint.config == config)
        if (checkpoint?.completed == true) phase = Phase.COMPLETE
    }

    private fun now(): Long = clock.nowMs().also { require(it >= anchor) { "Monotonic clock moved backwards" } }
    private fun running() = phase == Phase.WAITING || phase == Phase.ACTIVE || phase == Phase.QUESTION
    private fun total(now: Long) = elapsed + if (running()) now - anchor else 0
    private fun beginRound(now: Long) {
        inputRevision++
        roundAnchor = now
        readingMs = 0
        searchBoard = if (config.exercise == Exercise.VISUAL_SEARCH) Stimuli.search(config, attempts.size) else null
        patternTrial = if (config.exercise == Exercise.PATTERN_MATCH) AdditionalStimuli.pattern(config, attempts.size) else null
        reactionShape = if (config.exercise == Exercise.SHAPE_REACTION) AdditionalStimuli.shape(config, attempts.size) else null
        reactionColor = if (config.exercise == Exercise.COLOR_REACTION) AdditionalStimuli.color(config, attempts.size) else null
        directionTrial = if (config.exercise == Exercise.DIRECTION_MATCH) AdditionalStimuli.direction(config, attempts.size) else null
        targetCell = searchBoard?.targetCell ?: patternTrial?.correctOption ?: reactionShape?.ordinal ?:
            reactionColor?.ordinal ?: directionTrial?.direction?.ordinal ?: random.nextInt(16)
        phraseIndex = 0
        phraseAnchor = now
        phraseChunks = when (config.exercise) {
            Exercise.PHRASE_READING -> Stimuli.phrases(currentPassage().text)
            Exercise.RSVP_READER -> currentPassage().text.trim().split(Regex("\\s+"))
            else -> emptyList()
        }
        sprintWords = if (config.exercise == Exercise.WORD_SPRINT) Stimuli.sprintWords(config, attempts.size) else emptyList()
        sprintMeasurements = emptyList()
        wordAnchor = now
        waitingMs = random.nextLong(900, 2_401)
        phase = if (config.exercise.module == Module.REFLEX) Phase.WAITING else Phase.ACTIVE
    }
    fun resume() {
        if (phase != Phase.PAUSED) return
        anchor = clock.nowMs()
        // An interrupted stimulus is never scored. Restart the current round.
        beginRound(anchor)
    }
    fun pause() {
        if (!running()) return
        elapsed = total(now())
        phase = Phase.PAUSED
        inputRevision++
    }
    fun abort() {
        if (phase == Phase.COMPLETE || phase == Phase.ABORTED) return
        if (running()) elapsed = total(now())
        phase = Phase.ABORTED
        inputRevision++
    }
    fun tick() {
        if (!running()) return
        val now = now()
        if (phase == Phase.WAITING && now - roundAnchor >= waitingMs) {
            // Use actual stimulus publication time, not the scheduled deadline.
            roundAnchor = now
            phase = Phase.ACTIVE
            inputRevision++
            return
        }
        if (config.exercise in listOf(Exercise.PHRASE_READING, Exercise.RSVP_READER) && phase == Phase.ACTIVE &&
            now - phraseAnchor >= phraseDisplayMs(phraseChunks[phraseIndex])) {
            inputRevision++
            // Publish at most one chunk per tick. A delayed frame must not skip unseen words.
            if (phraseIndex == phraseChunks.lastIndex) {
                readingMs = now - roundAnchor
                phase = Phase.QUESTION
            } else {
                phraseIndex++
                phraseAnchor = now
            }
            return
        }
        val timed = config.exercise.module in setOf(Module.VISION, Module.REFLEX)
        if (timed && phase == Phase.ACTIVE && now - roundAnchor >= config.effectiveResponseLimitMs) {
            val withheld = config.exercise == Exercise.GO_NO_GO && !goPlan[attempts.size]
            record(Attempt(if (withheld) Outcome.WITHHELD else Outcome.MISSED, null), now)
        }
    }
    private fun record(attempt: Attempt, now: Long) {
        attempts = attempts + attempt
        if (attempts.size == config.rounds) {
            elapsed = total(now)
            phase = Phase.COMPLETE
            inputRevision++
        } else beginRound(now)
    }
    fun tap(cell: Int? = null, expectedRevision: Long? = null) {
        if (expectedRevision != null && expectedRevision != inputRevision) return
        if (config.exercise.module !in setOf(Module.VISION, Module.REFLEX) || !running()) return
        // A tap cannot publish its own GO stimulus. Waiting taps remain premature,
        // even when the scheduled deadline has passed but tick() has not published GO.
        val previousCount = attempts.size
        if (phase != Phase.WAITING) tick()
        if (!running() || attempts.size != previousCount) return
        val now = now()
        if (phase == Phase.WAITING) {
            record(Attempt(Outcome.PREMATURE, null), now)
        } else if (phase == Phase.ACTIVE) {
            val allowed = when (config.exercise) {
                Exercise.PATTERN_MATCH, Exercise.COLOR_REACTION, Exercise.DIRECTION_MATCH -> 0..3
                Exercise.SHAPE_REACTION -> 0..2
                else -> 0..15
            }
            if (config.exercise != Exercise.QUICK_TAP && config.exercise != Exercise.GO_NO_GO && cell !in allowed) return
            val correct = when (config.exercise) {
                Exercise.QUICK_TAP -> true
                Exercise.GO_NO_GO -> goPlan[attempts.size]
                else -> cell == targetCell
            }
            record(Attempt(if (correct) Outcome.CORRECT else Outcome.INCORRECT, now - roundAnchor), now)
        }
    }
    fun showQuestion(expectedRevision: Long? = null) {
        if (expectedRevision != null && expectedRevision != inputRevision) return
        if (config.exercise != Exercise.COMPREHENSION || phase != Phase.ACTIVE) return
        readingMs = now() - roundAnchor
        if (readingMs < 1_000) return
        phase = Phase.QUESTION
        inputRevision++
    }
    fun answer(option: Int, expectedRevision: Long? = null) {
        if (expectedRevision != null && expectedRevision != inputRevision) return
        if (config.exercise.module != Module.READ || phase != Phase.QUESTION || option !in 0..2) return
        val passage = currentPassage()
        record(Attempt(
            if (option == passage.correctOption) Outcome.CORRECT else Outcome.INCORRECT,
            now() - roundAnchor, wordsRead = wordCount(passage.text), readingMs = readingMs,
        ), now())
    }
    fun submitTyping(input: String, expectedRevision: Long? = null) {
        if (expectedRevision != null && expectedRevision != inputRevision) return
        if (config.exercise.module != Module.TYPE || phase != Phase.ACTIVE) return
        require(input.length <= if (config.exercise == Exercise.WORD_SPRINT) 64 else 1_000) { "Input is too large" }
        if (input.isBlank()) return
        val now = now()
        if (config.exercise == Exercise.WORD_SPRINT) {
            val measurement = Typing.measure(currentTypingTarget(), input, now - wordAnchor)
            sprintMeasurements = sprintMeasurements + measurement
            wordAnchor = now
            inputRevision++
            if (sprintMeasurements.size < sprintWords.size) return
            val weak = sprintMeasurements.flatMap { it.weakKeys.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
            val errors = sprintMeasurements.sumOf { it.incorrect }
            record(Attempt(if (errors == 0) Outcome.CORRECT else Outcome.INCORRECT, now - roundAnchor,
                sprintMeasurements.sumOf { it.correct }, errors, weakKeys = weak), now)
        } else {
            val measurement = Typing.measure(currentTypingTarget(), input, now - roundAnchor)
            record(Attempt(
                if (measurement.incorrect == 0) Outcome.CORRECT else Outcome.INCORRECT,
                measurement.durationMs, measurement.correct, measurement.incorrect, weakKeys = measurement.weakKeys,
            ), now)
        }
    }
    private fun currentPassage() = passages[attempts.size % passages.size]
    private fun phraseDisplayMs(text: String): Long = if (config.exercise == Exercise.RSVP_READER)
        (60_000L / (120 + (config.difficulty - 1) * 30)).coerceAtLeast(150)
        else Stimuli.phraseDisplayMs(text, config.difficulty)
    private fun currentTypingTarget(): String {
        if (config.exercise == Exercise.CODE_TYPING) return Content.code(config.language, config.difficulty, attempts.size)
        if (config.exercise == Exercise.WORD_SPRINT) return sprintWords.getOrNull(sprintMeasurements.size).orEmpty()
        val texts = typingTexts
        return (0 until config.difficulty).joinToString(" ") { texts[(attempts.size + it) % texts.size] }
    }
    fun view(): SessionView {
        val n = now()
        return SessionView(config.exercise, config.rounds, inputRevision, phase, (attempts.size + 1).coerceAtMost(config.rounds), targetCell,
            total(n), if (running()) n - roundAnchor else 0, attempts, currentPassage(), currentTypingTarget(),
            searchBoard, config.exercise != Exercise.GO_NO_GO || goPlan.getOrElse(attempts.size) { true },
            if (config.exercise in listOf(Exercise.PHRASE_READING, Exercise.RSVP_READER) && phraseChunks.isNotEmpty()) PhraseFrame(
                phraseChunks[phraseIndex], phraseIndex + 1, phraseChunks.size,
                phraseDisplayMs(phraseChunks[phraseIndex])) else null,
            (sprintMeasurements.size + 1).coerceAtMost(sprintWords.size), sprintWords.size, patternTrial,
            if (phase == Phase.ACTIVE) reactionShape else null,
            if (phase == Phase.ACTIVE) reactionColor else null,
            if (phase == Phase.ACTIVE) directionTrial else null, config.customExerciseName, config.practice)
    }
    fun checkpoint() = Checkpoint(config, attempts, total(now()), phase == Phase.COMPLETE)
}

data class TypingMeasurement(val correct: Int, val incorrect: Int, val durationMs: Long, val weakKeys: Map<String, Int>) {
    val accuracy: Double get() = if (correct + incorrect == 0) 0.0 else correct.toDouble() / (correct + incorrect)
    // Net correct characters / 5 / elapsed minutes. Missing characters count as errors.
    val wpm: Double get() = if (durationMs <= 0) 0.0 else correct / 5.0 / (durationMs / 60_000.0)
}
object Typing {
    fun measure(target: String, input: String, durationMs: Long): TypingMeasurement {
        require(target.isNotBlank() && target.length <= 1000 && input.length <= 1000 && durationMs >= 0)
        val expected = Normalizer.normalize(target, Normalizer.Form.NFC).codePoints().toArray()
        val actual = Normalizer.normalize(input, Normalizer.Form.NFC).codePoints().toArray()
        var correct = 0
        val weak = mutableMapOf<String, Int>()
        for (i in expected.indices) {
            if (i < actual.size && expected[i] == actual[i]) correct++
            else {
                val key = String(Character.toChars(expected[i]))
                weak[key] = (weak[key] ?: 0) + 1
            }
        }
        return TypingMeasurement(correct, maxOf(expected.size, actual.size) - correct, durationMs, weak)
    }
}
fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

data class SessionSummary(
    val completed: Boolean,
    val valid: Boolean,
    val correct: Int,
    val incorrect: Int,
    val premature: Int,
    val missed: Int,
    val accuracy: Double,
    val meanResponseMs: Double?,
    val responseDeviationMs: Double?,
    val wpm: Double?,
    val xp: Int,
    val withheld: Int,
    val goalMet: Boolean,
)
object Scoring {
    fun summarize(checkpoint: Checkpoint): SessionSummary {
        val a = checkpoint.attempts
        val correct = a.count { it.outcome.isCorrect }
        val timing = a.filter { it.outcome == Outcome.CORRECT }.mapNotNull { it.responseMs?.toDouble() }
        val mean = timing.takeIf { it.isNotEmpty() }?.average()
        val deviation = mean?.let { m -> sqrt(timing.map { (it - m) * (it - m) }.average()) }
        val type = checkpoint.config.exercise.module == Module.TYPE
        val characters = a.sumOf { it.correctCharacters + it.incorrectCharacters }
        val accuracy = if (type && characters > 0) a.sumOf { it.correctCharacters }.toDouble() / characters
            else if (a.isEmpty()) 0.0 else correct.toDouble() / a.size
        val valid = !checkpoint.config.practice && checkpoint.completed && checkpoint.elapsedMs >= 1_000 && a.any { it.responseMs != null }
        val wpm = when (checkpoint.config.exercise.module) {
            Module.TYPE -> a.sumOf { it.responseMs ?: 0 }.takeIf { it > 0 }?.let {
                a.sumOf { it.correctCharacters } / 5.0 / (it / 60_000.0)
            }
            Module.READ -> a.sumOf { it.readingMs }.takeIf { it > 0 }?.let {
                a.sumOf { it.wordsRead } / (it / 60_000.0)
            }
            else -> null
        }
        // Rewards are separate from measured performance. Baselines do not earn XP.
        val xp = if (valid && !checkpoint.config.baseline) 10 + (20 * accuracy).toInt() else 0
        return SessionSummary(checkpoint.completed, valid, correct,
            a.count { it.outcome == Outcome.INCORRECT }, a.count { it.outcome == Outcome.PREMATURE },
            a.count { it.outcome == Outcome.MISSED }, accuracy, mean, deviation, wpm, xp, a.count { it.outcome == Outcome.WITHHELD },
            valid && accuracy * 100 + 1e-8 >= checkpoint.config.targetAccuracyPercent &&
                a.count { !it.outcome.isCorrect } <= checkpoint.config.errorAllowance)
    }
}
