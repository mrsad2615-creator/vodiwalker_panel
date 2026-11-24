package com.neurox.tests

import com.neurox.domain.*

internal fun checkpointProgressTests() {
    val config = TrainingConfig(Exercise.FOCUS_LOCK, rounds = 3, stimulusSeed = 2)
    val first = Attempt(Outcome.INCORRECT, 200)
    val previous = Checkpoint(config, listOf(first), 1000, false)
    case("checkpoint progress accepts an identical retry") {
        check(CheckpointProgress.accept(previous, previous))
    }
    case("checkpoint progress accepts added active time without changing attempts") {
        check(CheckpointProgress.accept(previous, previous.copy(elapsedMs = 1500)))
    }
    case("checkpoint progress accepts an append with preserved prefix") {
        check(CheckpointProgress.accept(previous, previous.copy(attempts = listOf(first, Attempt(Outcome.CORRECT, 250)), elapsedMs = 1500)))
    }
    case("checkpoint progress ignores stale shorter snapshots") {
        check(!CheckpointProgress.accept(previous, Checkpoint(config, emptyList(), 0, false)))
    }
    case("checkpoint progress ignores stale equal-count timestamps") {
        check(!CheckpointProgress.accept(previous, previous.copy(elapsedMs = 900)))
    }
    case("checkpoint progress rejects reward-inflating outcome rewrites") {
        rejects { CheckpointProgress.accept(previous, previous.copy(attempts = listOf(first.copy(outcome = Outcome.CORRECT)))) }
    }
    case("checkpoint progress rejects rewrites concealed in a longer snapshot") {
        rejects { CheckpointProgress.accept(previous, previous.copy(attempts = listOf(first.copy(outcome = Outcome.CORRECT), Attempt(Outcome.CORRECT, 250)), elapsedMs = 1500)) }
    }
    case("checkpoint progress rejects decreased time with added attempts") {
        rejects { CheckpointProgress.accept(previous, previous.copy(attempts = listOf(first, Attempt(Outcome.CORRECT, 250)), elapsedMs = 900)) }
    }
    case("checkpoint progress rejects configuration changes") {
        rejects { CheckpointProgress.accept(previous, previous.copy(config = config.copy(difficulty = 2))) }
    }
    case("checkpoint progress accepts completion only with the same attempt prefix") {
        val complete = previous.copy(attempts = listOf(first, Attempt(Outcome.CORRECT, 250), Attempt(Outcome.CORRECT, 300)), elapsedMs = 2000, completed = true)
        check(CheckpointProgress.accept(previous, complete))
        check(CheckpointProgress.accept(complete, complete))
        check(!CheckpointProgress.accept(complete, previous))
    }
}
