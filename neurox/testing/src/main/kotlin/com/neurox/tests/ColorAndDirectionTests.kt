package com.neurox.tests

import com.neurox.domain.*

internal fun colorAndDirectionTests() {
    for (exercise in listOf(Exercise.COLOR_REACTION, Exercise.DIRECTION_MATCH)) {
        case("$exercise hides stimulus until actual publication") {
            val c = Clock(); val e = engine(exercise, c)
            eq(null, e.view().reactionColor); eq(null, e.view().directionTrial)
            c.advance(3000)
            // Merely waiting does not publish a stimulus.
            eq(null, e.view().reactionColor); eq(null, e.view().directionTrial)
            e.tick()
            if (exercise == Exercise.COLOR_REACTION) check(e.view().reactionColor != null)
            else check(e.view().directionTrial != null)
        }
        case("$exercise scores matching choice with monotonic reaction time") {
            val c = Clock(); val e = engine(exercise, c)
            c.advance(3000); e.tick(); c.advance(247); e.tap(e.view().targetCell)
            eq(Outcome.CORRECT, e.view().attempts.single().outcome)
            eq(247L, e.view().attempts.single().responseMs)
            check(Scoring.summarize(e.checkpoint()).valid)
        }
        case("$exercise scores mismatching choice as incorrect") {
            val c = Clock(); val e = engine(exercise, c)
            c.advance(3000); e.tick(); c.advance(185); e.tap((e.view().targetCell + 1) % 4)
            eq(Outcome.INCORRECT, e.view().attempts.single().outcome)
            eq(185L, e.view().attempts.single().responseMs)
        }
        case("$exercise late waiting tap is premature not self-published GO") {
            val c = Clock(); val e = engine(exercise, c)
            c.advance(4000); e.tap(1)
            eq(Outcome.PREMATURE, e.view().attempts.single().outcome)
            eq(null, e.view().attempts.single().responseMs)
        }
        case("$exercise ignores out-of-range active choices") {
            val c = Clock(); val e = engine(exercise, c)
            c.advance(3000); e.tick(); c.advance(150)
            for (choice in listOf(null, -1, 4, Int.MAX_VALUE)) e.tap(choice)
            check(e.view().attempts.isEmpty()); eq(Phase.ACTIVE, e.view().phase)
        }
        case("$exercise expired target is missed, never inhibition") {
            val c = Clock(); val e = engine(exercise, c)
            c.advance(3000); e.tick(); c.advance(5000); e.tap(e.view().targetCell)
            eq(Outcome.MISSED, e.view().attempts.single().outcome)
            eq(null, e.view().attempts.single().responseMs)
        }
        case("$exercise stale double input cannot score next trial") {
            val c = Clock(); val e = engine(exercise, c, rounds = 2)
            c.advance(3000); e.tick(); c.advance(200)
            val frame = e.view(); e.tap(frame.targetCell, frame.inputRevision)
            e.tap(frame.targetCell, frame.inputRevision)
            eq(1, e.view().attempts.size); eq(Phase.WAITING, e.view().phase)
        }
        case("$exercise pause hides stimulus and restarts unfinished trial") {
            val c = Clock(); val e = engine(exercise, c)
            c.advance(3000); e.tick(); c.advance(500); e.pause()
            eq(null, e.view().reactionColor); eq(null, e.view().directionTrial)
            c.advance(50_000); e.resume(); eq(Phase.WAITING, e.view().phase)
            c.advance(3000); e.tick(); c.advance(210); e.tap(e.view().targetCell)
            eq(210L, e.view().attempts.single().responseMs)
            check(e.checkpoint().elapsedMs < 50_000)
        }
        case("$exercise seed survives a process restore without persisting hidden stimulus") {
            val c = Clock(); val config = TrainingConfig(exercise, difficulty = 5, rounds = 2, stimulusSeed = 43)
            val e = TrainingEngine(config, c).also { it.resume() }
            c.advance(3000); e.tick(); val first = e.view(); e.pause()
            val restored = TrainingEngine(config, c, checkpoint = e.checkpoint())
            restored.resume(); c.advance(3000); restored.tick()
            eq(first.reactionColor, restored.view().reactionColor)
            eq(first.directionTrial, restored.view().directionTrial)
        }
    }
    case("color stimuli cover every named color deterministically") {
        val values = (0L..200L).map { seed ->
            val config = TrainingConfig(Exercise.COLOR_REACTION, stimulusSeed = seed)
            val value = AdditionalStimuli.color(config, 2)
            eq(value, AdditionalStimuli.color(config, 2)); value
        }.toSet()
        eq(ReactionColor.entries.toSet(), values)
    }
    case("direction match level one has congruent positions in either language") {
        for (language in Language.entries) for (seed in 0L..100L) {
            val t = AdditionalStimuli.direction(TrainingConfig(Exercise.DIRECTION_MATCH, language, stimulusSeed = seed), 0)
            eq(t.direction, t.position)
        }
    }
    case("higher-level directional stimuli include every congruence pair") {
        val pairs = (0L..400L).map { AdditionalStimuli.direction(TrainingConfig(Exercise.DIRECTION_MATCH, difficulty = 3, stimulusSeed = it), 0) }.toSet()
        eq(16, pairs.size)
    }
    case("direction target does not depend on language layout") {
        for (level in 1..5) for (seed in 0L..80L) {
            val config = TrainingConfig(Exercise.DIRECTION_MATCH, Language.FA, difficulty = level, stimulusSeed = seed)
            eq(AdditionalStimuli.direction(config, 1), AdditionalStimuli.direction(config.copy(language = Language.EN), 1))
        }
    }
}
