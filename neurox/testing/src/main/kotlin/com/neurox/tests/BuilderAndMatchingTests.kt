package com.neurox.tests

import com.neurox.domain.*

internal fun builderAndMatchingTests() {
    case("builder validates every current template in both languages") {
        for (exercise in Exercise.entries) {
            val bp = ExerciseBlueprint("تمرین local", exercise)
            eq(emptyList<String>(), bp.validate())
            for (language in Language.entries) {
                val config = bp.configuration(language, 42)
                eq(exercise, config.exercise); eq(language, config.language)
                eq(emptyList<String>(), config.validate())
            }
        }
    }
    case("builder rejects empty and oversized names") {
        check(ExerciseBlueprint(" ", Exercise.FOCUS_LOCK).validate().isNotEmpty())
        check(ExerciseBlueprint("a".repeat(61), Exercise.FOCUS_LOCK).validate().isNotEmpty())
    }
    case("builder rejects control and bidi override names but permits Persian ZWNJ") {
        for (name in listOf("line\nbreak", "bad\u0000name", "test\u202Eexe", "test\u2066exe")) {
            check(ExerciseBlueprint(name, Exercise.FOCUS_LOCK).validate().isNotEmpty())
        }
        check(ExerciseBlueprint("تمرین‌های من", Exercise.FOCUS_LOCK).validate().isEmpty())
    }
    case("builder rejects unsupported schema and unsafe parameter bounds") {
        val bp = ExerciseBlueprint("Local", Exercise.FOCUS_LOCK)
        check(bp.copy(schemaVersion = 2).validate().isNotEmpty())
        check(bp.copy(difficulty = 0).validate().isNotEmpty())
        check(bp.copy(rounds = 31).validate().isNotEmpty())
        check(bp.copy(responseLimitMs = 999).validate().isNotEmpty())
        check(bp.copy(responseLimitMs = 60001).validate().isNotEmpty())
    }
    case("builder rejects invalid success criteria") {
        val bp = ExerciseBlueprint("Local", Exercise.FOCUS_LOCK)
        for (goal in listOf(0, 101)) check(bp.copy(targetAccuracyPercent = goal).validate().isNotEmpty())
        for (errors in listOf(-1, 31)) check(bp.copy(errorAllowance = errors).validate().isNotEmpty())
    }
    case("builder module derives only from the template") {
        Exercise.entries.forEach { eq(it.module, ExerciseBlueprint("Local", it).module) }
    }
    case("builder source fields preserve a stable identity and trimmed snapshot name") {
        val id = "4e533adb-3953-445c-b1f2-49831021cb12"
        val config = ExerciseBlueprint("  تمرین من  ", Exercise.VISUAL_SEARCH).configuration(Language.FA, 92, id)
        eq(id, config.customExerciseId); eq("تمرین من", config.customExerciseName); eq(92L, config.stimulusSeed)
        check(config.validate().isEmpty())
    }
    case("invalid or unpaired custom identifiers rejected") {
        val base = TrainingConfig(Exercise.FOCUS_LOCK)
        check(base.copy(customExerciseId = "not-a-uuid", customExerciseName = "Local").validate().isNotEmpty())
        check(base.copy(customExerciseName = "Local").validate().isNotEmpty())
        check(base.copy(customExerciseId = "4e533adb-3953-445c-b1f2-49831021cb12").validate().isNotEmpty())
    }
    case("practice session records measurement but never XP or ordinary validity") {
        val clock = Clock()
        val config = ExerciseBlueprint("Test", Exercise.FOCUS_LOCK, rounds = 1).configuration(Language.EN, 42, practice = true)
        val e = TrainingEngine(config, clock).also { it.resume() }
        clock.advance(1000); e.tap(e.view().targetCell)
        val result = Scoring.summarize(e.checkpoint())
        near(1.0, result.accuracy); eq(0, result.xp); check(!result.valid); check(!result.goalMet)
        check(e.view().practice); check(e.checkpoint().completed)
    }
    case("success target uses measured accuracy without rewriting it") {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 2, targetAccuracyPercent = 50, errorAllowance = 1)
        val attempts = listOf(Attempt(Outcome.CORRECT, 500), Attempt(Outcome.INCORRECT, 500))
        val pass = Scoring.summarize(Checkpoint(config, attempts, 1000, true))
        check(pass.goalMet); near(0.5, pass.accuracy)
        val fail = Scoring.summarize(Checkpoint(config.copy(targetAccuracyPercent = 51), attempts, 1000, true))
        check(!fail.goalMet); near(pass.accuracy, fail.accuracy); eq(pass.xp, fail.xp)
    }
    case("error allowance can fail a high-accuracy session") {
        val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 2, targetAccuracyPercent = 50, errorAllowance = 0)
        val cp = Checkpoint(config, listOf(Attempt(Outcome.CORRECT, 500), Attempt(Outcome.MISSED, null)), 6000, true)
        check(!Scoring.summarize(cp).goalMet)
    }
    case("pattern trial has exactly one match and equal-density unique distractors") {
        for (level in 1..5) for (seed in 0L..99L) {
            val config = TrainingConfig(Exercise.PATTERN_MATCH, difficulty = level, stimulusSeed = seed)
            val trial = AdditionalStimuli.pattern(config, 0)
            eq(2 + level, trial.sample.size); eq(4, trial.options.size)
            eq(4, trial.options.toSet().size)
            eq(1, trial.options.count { it == trial.sample })
            eq(trial.sample, trial.options[trial.correctOption])
            check(trial.options.all { it.size == trial.sample.size && it.all { c -> c in 0..8 } })
        }
    }
    case("pattern trial replay is deterministic") {
        val config = TrainingConfig(Exercise.PATTERN_MATCH, stimulusSeed = 23)
        eq(AdditionalStimuli.pattern(config, 4), AdditionalStimuli.pattern(config, 4))
    }
    case("pattern correct and incorrect choices are actually scored") {
        val c = Clock(); val e = engine(Exercise.PATTERN_MATCH, c, 2)
        c.advance(1000); e.tap(e.view().pattern!!.correctOption)
        c.advance(1000); e.tap((e.view().pattern!!.correctOption + 1) % 4)
        near(0.5, Scoring.summarize(e.checkpoint()).accuracy)
    }
    case("pattern rejects invalid option and expires unanswered trial") {
        val c = Clock(); val e = engine(Exercise.PATTERN_MATCH, c)
        e.tap(4); e.tap(-1); eq(0, e.view().attempts.size)
        c.advance(5000); e.tick(); eq(Outcome.MISSED, e.view().attempts.single().outcome)
    }
    case("pattern pause recreates identical board and options") {
        val c = Clock(); val e = engine(Exercise.PATTERN_MATCH, c)
        val trial = e.view().pattern
        e.pause(); e.resume(); eq(trial, e.view().pattern)
    }
    case("shape reaction has no shape before stimulus publication") {
        val c = Clock(); val e = engine(Exercise.SHAPE_REACTION, c)
        eq(null, e.view().reactionShape)
        c.advance(3000); e.tick(); check(e.view().reactionShape != null)
    }
    case("shape reaction correct choice measures reaction and wrong choice fails") {
        val c = Clock(); val e = engine(Exercise.SHAPE_REACTION, c, 2)
        c.advance(3000); e.tick(); c.advance(250); e.tap(e.view().reactionShape!!.ordinal)
        eq(250L, e.view().attempts.single().responseMs)
        c.advance(3000); e.tick(); c.advance(200); e.tap((e.view().reactionShape!!.ordinal + 1) % 3)
        eq(1, Scoring.summarize(e.checkpoint()).incorrect)
    }
    case("shape reaction premature answers do not obtain latency") {
        val c = Clock(); val e = engine(Exercise.SHAPE_REACTION, c)
        e.tap(0); eq(Outcome.PREMATURE, e.view().attempts.single().outcome)
        eq(null, e.view().attempts.single().responseMs)
    }
    case("shape reaction rejects out-of-range active choice") {
        val c = Clock(); val e = engine(Exercise.SHAPE_REACTION, c)
        c.advance(3000); e.tick(); e.tap(3); e.tap(-1)
        eq(0, e.view().attempts.size)
    }
    case("shape reaction samples all three shapes across seeds") {
        val shapes = (0L..100L).map { AdditionalStimuli.shape(TrainingConfig(Exercise.SHAPE_REACTION, stimulusSeed = it), 0) }.toSet()
        eq(ReactionShape.entries.toSet(), shapes)
    }
    case("RSVP shows one word and delayed tick cannot skip more words") {
        val c = Clock(); val e = engine(Exercise.RSVP_READER, c)
        eq(1, wordCount(e.view().phrase!!.text))
        eq(wordCount(e.view().passage.text), e.view().phrase!!.count)
        c.advance(90_000); e.tick(); eq(2, e.view().phrase!!.index)
    }
    case("RSVP higher level uses a shorter word interval") {
        val slow = engine(Exercise.RSVP_READER, Clock(), difficulty = 1).view().phrase!!
        val fast = engine(Exercise.RSVP_READER, Clock(), difficulty = 5).view().phrase!!
        eq(500L, slow.minimumDisplayMs); eq(250L, fast.minimumDisplayMs)
    }
    case("RSVP requires a question and excludes answer time from speed") {
        val c = Clock(); val e = engine(Exercise.RSVP_READER, c)
        e.showQuestion(); eq(Phase.ACTIVE, e.view().phase)
        while (e.view().phase == Phase.ACTIVE) { c.advance(500); e.tick() }
        val readMs = e.view().elapsedMs
        c.advance(10000); e.answer(e.view().passage.correctOption)
        eq(readMs, e.view().attempts.single().readingMs)
        near(120.0, Scoring.summarize(e.checkpoint()).wpm!!)
    }
    case("code snippets are local text in both languages and bounded") {
        for (language in Language.entries) for (level in 1..5) {
            val code = Content.code(language, level, 0)
            check(code.isNotBlank() && code.length <= 1000)
            check(code.contains("val ") || code.contains("fun "))
        }
        check(Content.code(Language.FA, 2, 0).contains("سلام"))
        check(Content.code(Language.EN, 5, 0).contains("Hello"))
    }
    case("code typing counts indentation and newline errors") {
        val c = Clock(); val e = engine(Exercise.CODE_TYPING, c, difficulty = 3)
        val code = e.view().typingTarget
        check(code.contains("\n    "))
        c.advance(10000); e.submitTyping(code.replace("\n", " "))
        check(e.view().attempts.single().incorrectCharacters > 0)
    }
    case("exact code typing earns measured accuracy without executing code") {
        val c = Clock(); val e = engine(Exercise.CODE_TYPING, c, difficulty = 5)
        val code = e.view().typingTarget
        c.advance(60000); e.submitTyping(code)
        near(1.0, Scoring.summarize(e.checkpoint()).accuracy)
        near(code.codePointCount(0, code.length) / 5.0, Scoring.summarize(e.checkpoint()).wpm!!)
    }
}
