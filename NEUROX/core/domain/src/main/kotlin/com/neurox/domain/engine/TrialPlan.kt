package com.neurox.domain.engine

import com.neurox.domain.exercise.ExerciseConfig
import kotlin.random.Random

/**
 * What a single trial asks of the user.
 */
enum class TrialKind {
    /** A signal appears and a response is expected inside the window. */
    TARGET,

    /** A signal appears and a response must be withheld (inhibition). */
    NO_GO,

    /** A signal appears; the user must choose the matching answer. */
    CHOICE,

    /** Text or a word is shown; the user confirms it was read correctly. */
    RECALL,
}

/**
 * One planned step of a session. Fully deterministic: generated from the
 * session seed, so the same seed always gives the same sequence.
 */
data class TrialPlan(
    val index: Int,
    val kind: TrialKind,
    /** Delay from the previous settle point to the stimulus, in milliseconds. */
    val delayMillis: Int,
    /** Response window after the stimulus, in milliseconds. */
    val windowMillis: Int,
    /** True when a response must be given; false means responding is an error. */
    val expectedResponse: Boolean,
    /** Stable slot index for layouts with several positions. */
    val slot: Int,
    /** Correct choice index for CHOICE trials, otherwise 0. */
    val correctChoice: Int = 0,
)

/** How a settled trial ends. */
enum class TrialOutcome {
    CORRECT,
    INCORRECT,
    MISSED,

    /** A response arrived before the stimulus. */
    PREMATURE,
}

/**
 * Deterministic generator for trial plans.
 *
 * [generate] uses a seeded [Random] and the exercise configuration, therefore it
 * is reproducible, unit-testable, and identical between UI and headless runs.
 */
object TrialPlanner {

    fun generate(
        seed: Long,
        config: ExerciseConfig,
        referenceMillis: Double,
        noGoRatio: Double = 0.0,
        choiceCount: Int = 1,
        kind: TrialKind = TrialKind.TARGET,
    ): List<TrialPlan> {
        val random = Random(seed)
        val rounds = config.rounds.coerceIn(1, 99)
        val minDelay = 350
        val maxDelay = (referenceMillis * 2.2).toInt().coerceIn(minDelay + 200, 3_000)
        val window = (referenceMillis * 2.4).toInt().coerceIn(400, 6_000)
        return buildList {
            for (i in 0 until rounds) {
                val isNoGo = noGoRatio > 0.0 && random.nextDouble() < noGoRatio
                val kindForTrial = if (isNoGo) TrialKind.NO_GO else kind
                add(
                    TrialPlan(
                        index = i,
                        kind = kindForTrial,
                        delayMillis = random.nextInt(minDelay, maxDelay + 1),
                        windowMillis = if (isNoGo) (window * 0.9).toInt() else window,
                        expectedResponse = !isNoGo,
                        slot = if (config.targetCount > 1) random.nextInt(config.targetCount) else 0,
                        correctChoice = if (choiceCount > 1) random.nextInt(choiceCount) else 0,
                    ),
                )
            }
        }
    }
}

/** Immutable result of one settled trial, useful for charts and tests. */
data class TrialResult(
    val plan: TrialPlan,
    val outcome: TrialOutcome,
    val responseMillis: Double?,
)
