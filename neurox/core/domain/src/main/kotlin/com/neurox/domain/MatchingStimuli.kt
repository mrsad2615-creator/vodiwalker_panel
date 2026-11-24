package com.neurox.domain

data class PatternTrial(val sample: Set<Int>, val options: List<Set<Int>>, val correctOption: Int)
enum class ReactionShape { CIRCLE, TRIANGLE, SQUARE }

/** Ordinals are persisted only as scored choices, never as screen-relative directions. */
enum class ReactionColor { RED, BLUE, GREEN, YELLOW }
enum class PhysicalDirection { UP, RIGHT, DOWN, LEFT }
data class DirectionTrial(val direction: PhysicalDirection, val position: PhysicalDirection)

object AdditionalStimuli {
    private fun random(config: TrainingConfig, round: Int, salt: Long) =
        kotlin.random.Random(config.stimulusSeed xor round.toLong() * 1_000_003L xor salt)

    fun pattern(config: TrainingConfig, round: Int): PatternTrial {
        val random = random(config, round, 0x50415454)
        val count = 2 + config.difficulty
        val sample = (0..8).shuffled(random).take(count).toSet()
        // Each distractor moves one filled cell. Counts alone cannot reveal the answer.
        val pairs = sample.flatMap { removed -> (0..8).filter { it !in sample }.map { removed to it } }.shuffled(random)
        val distractors = pairs.map { (removed, added) -> sample - removed + added }.distinct().take(3)
        val correct = random.nextInt(4)
        val options = distractors.toMutableList().apply { add(correct, sample) }
        return PatternTrial(sample, options.toList(), correct)
    }
    fun shape(config: TrainingConfig, round: Int): ReactionShape =
        ReactionShape.entries[random(config, round, 0x53484150).nextInt(ReactionShape.entries.size)]
    fun color(config: TrainingConfig, round: Int): ReactionColor =
        ReactionColor.entries[random(config, round, 0x434F4C52).nextInt(ReactionColor.entries.size)]

    fun direction(config: TrainingConfig, round: Int): DirectionTrial {
        val random = random(config, round, 0x44495245)
        val direction = PhysicalDirection.entries[random.nextInt(4)]
        // At higher levels the arrow can occur on an incongruent side. Its position
        // never changes the correct answer, including in a right-to-left locale.
        val position = if (config.difficulty == 1) direction else PhysicalDirection.entries[random.nextInt(4)]
        return DirectionTrial(direction, position)
    }

}
