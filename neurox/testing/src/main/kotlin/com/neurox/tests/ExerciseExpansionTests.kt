package com.neurox.tests

import com.neurox.domain.*

internal fun exerciseExpansionTests() {
    case("home retains one primary exercise per module") {
        eq(4, Catalogue.primary.size)
        eq(Module.entries.toSet(), Catalogue.primary.map { it.exercise.module }.toSet())
    }
    case("each module exposes three distinct executable exercises") {
        Module.entries.forEach { module -> eq(3, Catalogue.runnable.count { it.exercise.module == module }) }
    }
    case("unpublished GO cannot be triggered by its own tap") {
        val clock = Clock()
        val e = engine(Exercise.QUICK_TAP, clock)
        clock.advance(10_000) // A stalled ticker has not published GO.
        e.tap()
        eq(Outcome.PREMATURE, e.view().attempts.single().outcome)
    }
    case("search board has exactly one upright T across seeds and levels") {
        for (level in 1..5) for (seed in 0L..99L) {
            val config = TrainingConfig(Exercise.VISUAL_SEARCH, difficulty = level, stimulusSeed = seed)
            val board = Stimuli.search(config, 0)
            eq(16, board.cells.size)
            eq(1, board.cells.count { it == SearchMark.T_UP })
            eq(SearchMark.T_UP, board.cells[board.targetCell])
            eq(4 + (level - 1) * 3, board.cells.count { it != null })
        }
    }
    case("search seeded boards reproduce exactly") {
        val config = TrainingConfig(Exercise.VISUAL_SEARCH, stimulusSeed = 42)
        eq(Stimuli.search(config, 3), Stimuli.search(config, 3))
    }
    case("search varies across seeds") {
        val config = TrainingConfig(Exercise.VISUAL_SEARCH, difficulty = 5, stimulusSeed = 42)
        check(Stimuli.search(config, 0) != Stimuli.search(config.copy(stimulusSeed = 43), 0))
    }
    case("search correct target uses genuine measured response time") {
        val c = Clock(); val e = engine(Exercise.VISUAL_SEARCH, c)
        c.advance(1234); e.tap(e.view().searchBoard!!.targetCell)
        eq(Outcome.CORRECT, e.view().attempts.single().outcome)
        eq(1234L, e.view().attempts.single().responseMs)
    }
    case("search distractor is an error") {
        val c = Clock(); val e = engine(Exercise.VISUAL_SEARCH, c)
        c.advance(250); e.tap((e.view().targetCell + 1) % 16)
        eq(Outcome.INCORRECT, e.view().attempts.single().outcome)
    }
    case("search target timeout records missed") {
        val c = Clock(); val e = engine(Exercise.VISUAL_SEARCH, c)
        c.advance(5000); e.tick(); eq(Outcome.MISSED, e.view().attempts.single().outcome)
    }
    case("search invalid cell cannot score") {
        val c = Clock(); val e = engine(Exercise.VISUAL_SEARCH, c)
        e.tap(-1); e.tap(16); e.tap(); eq(0, e.view().attempts.size)
    }
    case("search recovery recreates same unfinished board") {
        val c = Clock(); val e = engine(Exercise.VISUAL_SEARCH, c, 3)
        val board = e.view().searchBoard
        c.advance(700); e.pause()
        val cp = e.checkpoint()
        val restored = TrainingEngine(cp.config, Clock(50_000), checkpoint = cp)
        eq(Phase.PAUSED, restored.view().phase)
        restored.resume(); eq(board, restored.view().searchBoard)
        eq(0, restored.view().attempts.size)
    }
    case("go-no-go plan is balanced for every allowed round count") {
        for (rounds in 1..30) for (seed in 0L..30L) {
            val plan = Stimuli.goPlan(TrainingConfig(Exercise.GO_NO_GO, rounds = rounds, stimulusSeed = seed))
            eq(rounds, plan.size)
            check(plan.any { it })
            if (rounds > 1) check(plan.any { !it })
        }
    }
    case("go-no-go plan changes with seed") {
        val a = TrainingConfig(Exercise.GO_NO_GO, rounds = 15, stimulusSeed = 0)
        check((1L..10L).any { Stimuli.goPlan(a) != Stimuli.goPlan(a.copy(stimulusSeed = it)) })
    }
    case("go-no-go includes both go timing and correct inhibition") {
        val c = Clock(); val e = engine(Exercise.GO_NO_GO, c, 6)
        repeat(6) {
            c.advance(3000); e.tick()
            check(e.view().phase == Phase.ACTIVE)
            if (e.view().goSignal) { c.advance(300); e.tap() }
            else { c.advance(5000); e.tick() }
        }
        val summary = Scoring.summarize(e.checkpoint())
        eq(Phase.COMPLETE, e.view().phase)
        eq(6, summary.correct); eq(2, summary.withheld)
        near(1.0, summary.accuracy); near(300.0, summary.meanResponseMs!!)
        near(0.0, summary.responseDeviationMs!!)
        eq(30, summary.xp)
    }
    case("go-no-go false alarm differs from correct withholding") {
        val c = Clock(); val e = engine(Exercise.GO_NO_GO, c, 3)
        var falseAlarms = 0
        repeat(3) {
            c.advance(3000); e.tick()
            if (!e.view().goSignal) falseAlarms++
            c.advance(200); e.tap()
        }
        eq(1, falseAlarms)
        eq(1, e.view().attempts.count { it.outcome == Outcome.INCORRECT })
        eq(0, Scoring.summarize(e.checkpoint()).withheld)
    }
    case("go-no-go always tapping early does not earn XP") {
        val c = Clock(); val e = engine(Exercise.GO_NO_GO, c, 3)
        repeat(3) { c.advance(50); e.tap() }
        eq(3, Scoring.summarize(e.checkpoint()).premature)
        eq(0, Scoring.summarize(e.checkpoint()).xp)
    }
    case("go-no-go inactivity misses GO and is not perfect performance") {
        val c = Clock(); val e = engine(Exercise.GO_NO_GO, c, 3)
        repeat(3) { c.advance(3000); e.tick(); c.advance(5000); e.tick() }
        val summary = Scoring.summarize(e.checkpoint())
        eq(2, summary.missed); eq(1, summary.withheld)
        near(1.0 / 3, summary.accuracy)
        eq(null, summary.meanResponseMs); eq(0, summary.xp)
    }
    case("go-no-go expired tap is not scored on next waiting round") {
        val c = Clock(); val e = engine(Exercise.GO_NO_GO, c, 3)
        c.advance(3000); e.tick(); c.advance(5001); e.tap()
        eq(1, e.view().attempts.size); eq(Phase.WAITING, e.view().phase)
    }
    case("go-no-go pause and resume does not reward an interrupted no-go window") {
        val c = Clock(); val e = engine(Exercise.GO_NO_GO, c, 3)
        c.advance(3000); e.tick()
        val signal = e.view().goSignal
        c.advance(1000); e.pause(); c.advance(90_000); e.resume()
        eq(0, e.view().attempts.size); eq(Phase.WAITING, e.view().phase)
        eq(signal, e.view().goSignal)
    }
    case("withheld outcome cannot carry reaction latency") { rejects { Attempt(Outcome.WITHHELD, 500) } }
    case("withheld outcome invalid for other exercise histories") {
        rejects { Checkpoint(TrainingConfig(Exercise.FOCUS_LOCK, rounds = 1), listOf(Attempt(Outcome.WITHHELD, null)), 5000, true) }
    }
    case("phrase splitting preserves Persian order and ZWNJ") {
        eq(listOf("من می‌روم به", "خانه"), Stimuli.phrases(" من می‌روم به\nخانه "))
    }
    case("phrase splitting covers every word without loss") {
        Language.entries.flatMap { Content.passages(it) }.forEach { passage ->
            val phrases = Stimuli.phrases(passage.text)
            eq(wordCount(passage.text), phrases.sumOf { wordCount(it) })
            check(phrases.all { wordCount(it) in 1..3 })
        }
    }
    case("phrase duration scales with actual chunk length and difficulty") {
        eq(1500L, Stimuli.phraseDisplayMs("one two three", 1))
        eq(750L, Stimuli.phraseDisplayMs("one two three", 5))
        eq(500L, Stimuli.phraseDisplayMs("one", 1))
        eq(400L, Stimuli.phraseDisplayMs("one", 5))
        rejects { Stimuli.phraseDisplayMs("a", 6) }
    }
    case("phrase reading begins with first phrase only") {
        val e = engine(Exercise.PHRASE_READING, Clock())
        eq(1, e.view().phrase!!.index)
        eq(3, wordCount(e.view().phrase!!.text))
        e.showQuestion(); eq(Phase.ACTIVE, e.view().phase)
        e.answer(0); eq(0, e.view().attempts.size)
    }
    case("phrase reading cannot advance before minimum display time") {
        val c = Clock(); val e = engine(Exercise.PHRASE_READING, c)
        c.advance(e.view().phrase!!.minimumDisplayMs - 1); e.tick()
        eq(1, e.view().phrase!!.index)
        c.advance(1); e.tick(); eq(2, e.view().phrase!!.index)
    }
    case("stalled phrase frame never skips unread phrases") {
        val c = Clock(); val e = engine(Exercise.PHRASE_READING, c)
        c.advance(60_000); e.tick()
        eq(2, e.view().phrase!!.index)
        eq(Phase.ACTIVE, e.view().phase)
    }
    case("phrase reading comprehension controls accuracy and actual WPM") {
        val c = Clock(); val e = engine(Exercise.PHRASE_READING, c)
        val words = wordCount(e.view().passage.text)
        while (e.view().phase == Phase.ACTIVE) {
            c.advance(e.view().phrase!!.minimumDisplayMs + 200) // Deliberate scheduling delay.
            e.tick()
        }
        eq(Phase.QUESTION, e.view().phase)
        val displayTime = e.view().elapsedMs
        c.advance(10_000); e.answer(e.view().passage.correctOption)
        val summary = Scoring.summarize(e.checkpoint())
        eq(displayTime, e.view().attempts.single().readingMs)
        near(words / (displayTime / 60_000.0), summary.wpm!!)
        check(summary.wpm!! < 120)
        near(1.0, summary.accuracy)
    }
    case("phrase reading wrong comprehension is not rewarded as high accuracy") {
        val c = Clock(); val e = engine(Exercise.PHRASE_READING, c, difficulty = 5)
        while (e.view().phase == Phase.ACTIVE) { c.advance(2000); e.tick() }
        e.answer((e.view().passage.correctOption + 1) % 3)
        near(0.0, Scoring.summarize(e.checkpoint()).accuracy)
    }
    case("phrase reading resume restarts first phrase without phantom completion") {
        val c = Clock(); val e = engine(Exercise.PHRASE_READING, c)
        c.advance(2000); e.tick(); e.pause(); c.advance(90000); e.resume()
        eq(1, e.view().phrase!!.index); eq(0, e.view().attempts.size)
    }
    case("phrase reading recovery is paused and starts at round boundary") {
        val c = Clock(); val e = engine(Exercise.PHRASE_READING, c)
        c.advance(2000); e.tick(); e.pause()
        val cp = e.checkpoint()
        val restored = TrainingEngine(cp.config, Clock(), checkpoint = cp)
        restored.resume(); eq(1, restored.view().phrase!!.index)
    }
    case("word sprint deterministic unique local words for each language and level") {
        for (language in Language.entries) for (level in 1..5) {
            val config = TrainingConfig(Exercise.WORD_SPRINT, language = language, difficulty = level, stimulusSeed = 42)
            val words = Stimuli.sprintWords(config, 0)
            eq(3 + level, words.size); eq(words.size, words.toSet().size)
            eq(words, Stimuli.sprintWords(config, 0))
            check(words.all { it.isNotBlank() && !it.contains(' ') })
        }
    }
    case("word sprint scores only when every word in round is submitted") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c)
        val count = e.view().sprintWordCount
        var correctChars = 0
        repeat(count) { i ->
            eq(i + 1, e.view().sprintWordIndex)
            val target = e.view().typingTarget
            correctChars += target.codePointCount(0, target.length)
            c.advance(2000); e.submitTyping(target)
            eq(if (i == count - 1) 1 else 0, e.view().attempts.size)
        }
        eq(Phase.COMPLETE, e.view().phase)
        val attempt = e.view().attempts.single()
        eq(correctChars, attempt.correctCharacters); eq(0, attempt.incorrectCharacters)
        eq(count * 2000L, attempt.responseMs)
        near(correctChars / 5.0 / (count * 2000 / 60000.0), Scoring.summarize(e.checkpoint()).wpm!!)
    }
    case("word sprint sums errors and weak keys without storing entered words") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c)
        repeat(e.view().sprintWordCount) { c.advance(1000); e.submitTyping("private-input") }
        check(e.view().attempts.single().incorrectCharacters > 0)
        check(e.view().attempts.single().weakKeys.isNotEmpty())
        check(!e.checkpoint().toString().contains("private-input"))
    }
    case("word sprint blank input cannot skip words") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c)
        e.submitTyping(""); e.submitTyping("   ")
        eq(1, e.view().sprintWordIndex); eq(0, e.view().attempts.size)
    }
    case("word sprint oversized input rejected before state mutation") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c)
        rejects { e.submitTyping("a".repeat(65)) }
        eq(1, e.view().sprintWordIndex)
    }
    case("word sprint bounded worst-case input remains a valid checkpoint") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c, difficulty = 5)
        repeat(e.view().sprintWordCount) { c.advance(1000); e.submitTyping("x".repeat(64)) }
        check(e.checkpoint().completed)
        check(e.view().attempts.single().incorrectCharacters <= 512)
    }
    case("word sprint pause discards unfinished batch and excludes pause time") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c)
        val first = e.view().typingTarget
        c.advance(500); e.submitTyping(first); e.pause(); c.advance(90000); e.resume()
        eq(1, e.view().sprintWordIndex); eq(first, e.view().typingTarget)
        eq(500L, e.view().elapsedMs)
        eq(0, e.view().attempts.size)
    }
    case("word sprint process recovery replays the unfinished batch") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c)
        val first = e.view().typingTarget
        c.advance(1000); e.submitTyping(first)
        val cp = e.checkpoint()
        val restored = TrainingEngine(cp.config, Clock(), checkpoint = cp)
        restored.resume(); eq(first, restored.view().typingTarget); eq(1, restored.view().sprintWordIndex)
    }
    case("word sprint Persian words use code-point accuracy") {
        val c = Clock(); val config = TrainingConfig(Exercise.WORD_SPRINT, language = Language.FA, rounds = 1, stimulusSeed = 99)
        val e = TrainingEngine(config, c).also { it.resume() }
        repeat(e.view().sprintWordCount) { c.advance(1000); e.submitTyping(e.view().typingTarget) }
        near(1.0, Scoring.summarize(e.checkpoint()).accuracy)
    }
    case("new exercise baselines never grant XP") {
        val c = Clock(); val e = engine(Exercise.VISUAL_SEARCH, c, baseline = true)
        c.advance(1000); e.tap(e.view().targetCell)
        eq(0, Scoring.summarize(e.checkpoint()).xp)
    }
    case("duplicate visual tap cannot score on an unseen next round") {
        val c = Clock(); val e = engine(Exercise.VISUAL_SEARCH, c, 2)
        val frame = e.view(); c.advance(1000)
        e.tap(frame.targetCell, frame.inputRevision)
        e.tap(e.view().targetCell, frame.inputRevision)
        eq(1, e.view().attempts.size)
    }
    case("waiting-frame callback cannot score a newly published GO") {
        val c = Clock(); val e = engine(Exercise.QUICK_TAP, c)
        val waiting = e.view(); c.advance(3000); e.tick()
        e.tap(expectedRevision = waiting.inputRevision)
        eq(0, e.view().attempts.size)
        c.advance(200); e.tap(expectedRevision = e.view().inputRevision)
        eq(200L, e.view().attempts.single().responseMs)
    }
    case("duplicate sprint word submission cannot consume next target") {
        val c = Clock(); val e = engine(Exercise.WORD_SPRINT, c)
        val frame = e.view(); c.advance(1000)
        e.submitTyping(frame.typingTarget, frame.inputRevision)
        e.submitTyping(frame.typingTarget, frame.inputRevision)
        eq(2, e.view().sprintWordIndex)
    }
    case("answer callback from previous round cannot answer new reading question") {
        val c = Clock(); val e = engine(Exercise.COMPREHENSION, c, 2)
        c.advance(1000); e.showQuestion(); val frame = e.view()
        e.answer(frame.passage.correctOption, frame.inputRevision)
        c.advance(1000); e.showQuestion()
        e.answer(frame.passage.correctOption, frame.inputRevision)
        eq(1, e.view().attempts.size)
        eq(Phase.QUESTION, e.view().phase)
    }
    case("pre-pause input revision is rejected after resume") {
        val c = Clock(); val e = engine(Exercise.FOCUS_LOCK, c)
        val revision = e.view().inputRevision
        e.pause(); e.resume(); c.advance(1000)
        e.tap(e.view().targetCell, revision)
        eq(0, e.view().attempts.size)
    }
    case("checkpoint rejects fake inhibition for a GO round") {
        val config = TrainingConfig(Exercise.GO_NO_GO, rounds = 1)
        rejects { Checkpoint(config, listOf(Attempt(Outcome.WITHHELD, null)), 5000, true) }
    }

    case("all twelve exercises complete across languages and supported levels") {
        for (exercise in Exercise.entries) for (language in Language.entries) for (difficulty in 1..5) {
            if (exercise == Exercise.COMPREHENSION && difficulty != 1) continue
            val clock = Clock()
            val config = TrainingConfig(exercise, language, difficulty, rounds = 3, stimulusSeed = 17)
            val e = TrainingEngine(config, clock).also { it.resume() }
            var steps = 0
            while (e.view().phase != Phase.COMPLETE) {
                check(steps++ < 400)
                val frame = e.view()
                when (frame.phase) {
                    Phase.WAITING -> { clock.advance(3000); e.tick() }
                    Phase.QUESTION -> { clock.advance(1000); e.answer(frame.passage.correctOption, frame.inputRevision) }
                    Phase.ACTIVE -> when (exercise.module) {
                        Module.VISION -> { clock.advance(1000); e.tap(frame.targetCell, frame.inputRevision) }
                        Module.REFLEX -> if (frame.goSignal) { clock.advance(300); e.tap(if (exercise == Exercise.SHAPE_REACTION) frame.reactionShape!!.ordinal else null, frame.inputRevision) }
                            else { clock.advance(5000); e.tick() }
                        Module.READ -> if (exercise in listOf(Exercise.PHRASE_READING, Exercise.RSVP_READER)) {
                            clock.advance(frame.phrase!!.minimumDisplayMs); e.tick()
                        } else { clock.advance(1000); e.showQuestion(frame.inputRevision) }
                        Module.TYPE -> { clock.advance(1000); e.submitTyping(frame.typingTarget, frame.inputRevision) }
                    }
                    else -> error("Unexpected phase ${frame.phase}")
                }
                e.checkpoint() // Validate every intermediate durable snapshot.
            }
            eq(3, e.view().attempts.size)
            val summary = Scoring.summarize(e.checkpoint())
            near(1.0, summary.accuracy); check(summary.valid); eq(30, summary.xp)
        }
    }
    case("each exercise preserves finished rounds across process recovery") {
        for (exercise in Exercise.entries) {
            val clock = Clock()
            val config = TrainingConfig(exercise, Language.FA, rounds = 2, stimulusSeed = 42)
            val e = TrainingEngine(config, clock).also { it.resume() }
            while (e.view().attempts.isEmpty()) {
                val frame = e.view()
                when (frame.phase) {
                    Phase.WAITING -> { clock.advance(3000); e.tick() }
                    Phase.QUESTION -> e.answer(frame.passage.correctOption)
                    Phase.ACTIVE -> when (exercise.module) {
                        Module.VISION -> { clock.advance(1000); e.tap(frame.targetCell) }
                        Module.REFLEX -> if (frame.goSignal) { clock.advance(200); e.tap(if (exercise == Exercise.SHAPE_REACTION) frame.reactionShape!!.ordinal else null) } else { clock.advance(5000); e.tick() }
                        Module.READ -> if (exercise in listOf(Exercise.PHRASE_READING, Exercise.RSVP_READER)) { clock.advance(2000); e.tick() }
                            else { clock.advance(1000); e.showQuestion() }
                        Module.TYPE -> { clock.advance(1000); e.submitTyping(frame.typingTarget) }
                    }
                    else -> error("Unexpected phase")
                }
            }
            val cp = e.checkpoint()
            val restored = TrainingEngine(config, Clock(9_999_999), checkpoint = cp)
            eq(Phase.PAUSED, restored.view().phase)
            eq(cp.attempts, restored.view().attempts)
            eq(cp.elapsedMs, restored.view().elapsedMs)
            restored.resume()
            eq(2, restored.view().round)
        }
    }

}
