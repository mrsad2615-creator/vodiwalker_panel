package com.neurox.games.stimulus

import kotlin.random.Random

/**
 * What the user sees for one trial.
 *
 * The engine never computes pixels. A stimulus describes *semantic* content and a
 * *cell* on a normalized grid; the Compose layer maps cells to density-independent
 * coordinates. This keeps all exercise logic testable on a plain JVM and keeps
 * rendering free to adapt to screen size.
 */
data class Stimulus(
    /** Cells that carry the primary target. Empty for pure timing trials. */
    val targetCells: Set<Int> = emptySet(),
    /** Cells that carry distractors. */
    val distractorCells: Set<Int> = emptySet(),
    /** Symbol id of the target, for shape and pattern exercises. */
    val targetSymbol: Int = 0,
    /** Symbol ids of the distractors, parallel to [distractorCells]. */
    val distractorSymbols: List<Int> = emptyList(),
    /** Accent color of the stimulus, used by color exercises. */
    val colorArgb: Long = 0L,
    /** Direction id for direction exercises (0 up, 1 right, 2 down, 3 left). */
    val direction: Int = 0,
    /** Text for reading exercises. */
    val text: String? = null,
    /** Choice labels for identification exercises. */
    val choices: List<String> = emptyList(),
    /** Index of the correct choice, or -1 when the trial has no choice. */
    val correctChoice: Int = -1,
    /** Duration the stimulus stays visible, in milliseconds. */
    val visibleMillis: Int = 0,
    /** Cue cell for spatial-attention exercises, or -1. */
    val cueCell: Int = -1,
)

/** What the user did. */
sealed interface ResponseInput {
    /** A single tap or a key press with no location. */
    data object Tap : ResponseInput

    /** A tap on one grid cell. */
    data class CellTap(val cell: Int) : ResponseInput

    /** A choice by index. */
    data class Choice(val index: Int) : ResponseInput

    /** A direction answer (0 up, 1 right, 2 down, 3 left). */
    data class DirectionAnswer(val direction: Int) : ResponseInput

    /** A set of cells, used by multi-target exercises. */
    data class MultiCellTap(val cells: Set<Int>) : ResponseInput

    /** A typed character, used by the TYPE engine. */
    data class Character(val value: Char) : ResponseInput

    /**
     * An ordered sequence of cells, used by sequence-memory exercises where the
     * order of the answer is part of the answer.
     */
    data class Sequence(val cells: List<Int>) : ResponseInput
}

/**
 * Deterministic geometry for stimulus layouts.
 *
 * A field is a square grid of [columns] x [rows] cells. Positions are integers,
 * so a test can assert exact layouts without a display.
 */
class StimulusField(val columns: Int, val rows: Int) {
    init {
        require(columns in 1..12) { "columns must be in 1..12" }
        // A single row is valid: word strips and scanning lines use it.
        require(rows in 1..12) { "rows must be in 1..12" }
    }

    val cellCount: Int get() = columns * rows

    fun centerCell(): Int = (rows / 2) * columns + (columns / 2)

    /** True when [cell] lies on the outer ring of the grid. */
    fun isPeripheral(cell: Int): Boolean {
        val column = cell % columns
        val row = cell / columns
        return column == 0 || row == 0 || column == columns - 1 || row == rows - 1
    }

    /** True when [cell] is closer than one cell to the center. */
    fun isCentral(cell: Int): Boolean {
        val column = cell % columns
        val row = cell / columns
        val midColumn = (columns - 1) / 2.0
        val midRow = (rows - 1) / 2.0
        return kotlin.math.abs(column - midColumn) <= 1.0 && kotlin.math.abs(row - midRow) <= 1.0
    }

    /** Deterministic sampling of [count] distinct cells. */
    fun sampleCells(random: Random, count: Int, predicate: (Int) -> Boolean = { true }): List<Int> {
        val candidates = (0 until cellCount).filter(predicate)
        require(count <= candidates.size) { "requested $count cells but only ${candidates.size} are allowed" }
        return candidates.shuffled(random).take(count)
    }

    /** Deterministic sampling that also avoids the given cells. */
    fun sampleCellsAvoiding(
        random: Random,
        count: Int,
        excluded: Set<Int>,
        predicate: (Int) -> Boolean = { true },
    ): List<Int> = sampleCells(random, count) { it !in excluded && predicate(it) }

    /** Left-to-right reading order, used by scanning and reading exercises. */
    fun readingOrder(): List<Int> = (0 until cellCount).toList()
}

/** Symbol ids shared by the presentation layer. */
object Symbols {
    const val CIRCLE = 0
    const val SQUARE = 1
    const val TRIANGLE = 2
    const val DIAMOND = 3
    const val STAR = 4
    const val HEXAGON = 5
    val all: List<Int> = listOf(CIRCLE, SQUARE, TRIANGLE, DIAMOND, STAR, HEXAGON)

    /** A stable subset of [count] symbols. */
    fun subset(count: Int): List<Int> = all.take(count.coerceIn(1, all.size))
}

/** Direction ids, aligned with [ResponseInput.DirectionAnswer]. */
object Directions {
    const val UP = 0
    const val RIGHT = 1
    const val DOWN = 2
    const val LEFT = 3
    val all = listOf(UP, RIGHT, DOWN, LEFT)

    /** Names used by the accessibility layer and by tests. */
    fun key(direction: Int): String = when (direction) {
        UP -> "direction.up"
        RIGHT -> "direction.right"
        DOWN -> "direction.down"
        else -> "direction.left"
    }
}

/**
 * Palette ids for color exercises. The values are ARGB so the presentation layer
 * can use them directly, and they stay readable on obsidian surfaces.
 */
object StimulusColors {
    const val CHAMPAGNE = 0xFFD8B36A
    const val IVORY = 0xFFF2EDE4
    const val ROSE = 0xFFD98E8E
    const val TEAL = 0xFF6FB3A8
    const val VIOLET = 0xFF9A8FCB
    val all: List<Long> = listOf(CHAMPAGNE, IVORY, ROSE, TEAL, VIOLET)

    fun subset(count: Int): List<Long> = all.take(count.coerceIn(2, all.size))
}
