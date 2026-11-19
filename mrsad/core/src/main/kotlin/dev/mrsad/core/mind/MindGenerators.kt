package dev.mrsad.core.mind

import dev.mrsad.core.random.SplitMix64

/**
 * Procedural generators for MRSAD MIND cognitive exercises.
 *
 * Each generator is deterministic for a (seed, level) pair, returns real,
 * solvable items with exactly one correct answer, and grows difficulty with
 * [level] (0-based). UI layers render whatever is produced here — the logic
 * never lives in the screen.
 */
object MindGenerators {

    // ---------------- Memory ----------------

    /**
     * Digit-span stimulus for numeric working-memory training. Level 0 starts
     * at 4 digits, +1 digit per level.
     */
    fun digitSpan(seed: Long, level: Int): String {
        val rng = SplitMix64(seed)
        val count = (4 + level).coerceIn(3, 14)
        val first = rng.nextInt(1, 10) // never start with zero
        return buildString(count) {
            append(first)
            repeat(count - 1) { append(rng.nextInt(10)) }
        }
    }

    /**
     * Grid-pattern memory: a cells grid with [lit] cells that must be recalled.
     */
    fun patternMemory(seed: Long, level: Int): PatternGrid {
        val rng = SplitMix64(seed)
        val side = (3 + level / 2).coerceIn(3, 6)
        val cellCount = side * side
        val lit = (3 + level).coerceIn(3, cellCount - 2)
        val litCells = rng.shuffle((0 until cellCount).toList()).take(lit).sorted()
        return PatternGrid(side = side, litCells = litCells, showMillis = (2200 - level * 120).coerceAtLeast(900))
    }

    data class PatternGrid(val side: Int, val litCells: List<Int>, val showMillis: Int)

    // ---------------- Attention ----------------

    private val GRID_SYMBOLS = listOf("◆", "●", "▲", "■", "★", "✦")

    /**
     * Selective attention grid: exactly [targets] cells equal [targetSymbol];
     * everything else is drawn from distractors that differ only subtly.
     */
    fun attentionGrid(seed: Long, level: Int): AttentionGrid {
        val rng = SplitMix64(seed)
        val side = (4 + level / 2).coerceIn(4, 7)
        val targetSymbol = GRID_SYMBOLS[rng.nextInt(GRID_SYMBOLS.size)]
        val distractor = GRID_SYMBOLS[(GRID_SYMBOLS.indexOf(targetSymbol) + 1 + rng.nextInt(GRID_SYMBOLS.size - 1)) % GRID_SYMBOLS.size]
        val cells = side * side
        val targetCount = (3 + level).coerceIn(3, cells / 3)
        val targetIndices = rng.shuffle((0 until cells).toList()).take(targetCount).toSortedSet()
        val symbols = MutableList(cells) { distractor }
        for (index in targetIndices) symbols[index] = targetSymbol
        return AttentionGrid(side, targetSymbol, symbols, targetIndices.toList())
    }

    data class AttentionGrid(
        val side: Int,
        val targetSymbol: String,
        val symbols: List<String>,
        val targetIndices: List<Int>,
    )

    /**
     * Stroop items: the prompt shows a color word rendered in an ink color;
     * the user must answer the INK color, not the word.
     */
    fun stroop(seed: Long, count: Int): List<StroopItem> {
        val rng = SplitMix64(seed)
        val colors = listOf("سبز", "آبی", "قرمز", "زرد", "بنفش", "نارنجی")
        return List(count) {
            val ink = rng.nextInt(colors.size)
            var word: Int
            do {
                word = rng.nextInt(colors.size)
            } while (word == ink)
            StroopItem(wordColorIndex = word, inkColorIndex = ink, colors = colors)
        }
    }

    data class StroopItem(val wordColorIndex: Int, val inkColorIndex: Int, val colors: List<String>) {
        /** Correct answer is always the ink color (the word is a distractor). */
        val correctAnswerIndex: Int get() = inkColorIndex
        val promptFa: String get() = colors[wordColorIndex]
    }

    // ---------------- Processing speed ----------------

    fun arithmetic(seed: Long, level: Int, count: Int): List<ArithmeticItem> {
        val rng = SplitMix64(seed)
        return List(count) {
            val max = (10 + level * 5).coerceAtMost(99)
            val a = rng.nextInt(2, max)
            val b = rng.nextInt(2, max)
            val sum = a + b
            val offsets = listOf(-3, -2, -1, 1, 2, 3)
            val wrongs = mutableSetOf<Int>()
            while (wrongs.size < 3) {
                val candidate = (sum + offsets[rng.nextInt(offsets.size)]).coerceAtLeast(1)
                if (candidate != sum) wrongs += candidate
            }
            val options = rng.shuffle((wrongs + sum).toList())
            ArithmeticItem(
                promptFa = "${dev.mrsad.core.text.PersianText.toPersianDigits(a.toString())} + " +
                    dev.mrsad.core.text.PersianText.toPersianDigits(b.toString()),
                options = options,
                correctIndex = options.indexOf(sum),
            )
        }
    }

    data class ArithmeticItem(val promptFa: String, val options: List<Int>, val correctIndex: Int)

    // ---------------- Logic ----------------

    fun logicSequences(seed: Long, level: Int, count: Int): List<NumberSequence> {
        val rng = SplitMix64(seed)
        return List(count) {
            when (rng.nextInt(4)) {
                0 -> {
                    // Arithmetic progression.
                    val step = rng.nextInt(2, 3 + level)
                    val start = rng.nextInt(1, 12)
                    buildSequence(rng, List(4 + minOf(level, 3)) { i -> start + step * i }, "عدد بعدی چند است؟")
                }
                1 -> {
                    // Geometric progression with small ratio.
                    val ratio = rng.nextInt(2, 4)
                    val start = rng.nextInt(1, 4)
                    val values = ArrayList<Int>()
                    var v = start
                    repeat(4 + minOf(level, 2)) { values += v; v *= ratio }
                    buildSequence(rng, values.take(5), "الگوی ضربی را پیدا کن")
                }
                2 -> {
                    // Alternating additions: +a, +b, +a, +b …
                    val a = rng.nextInt(1, 6)
                    val b = rng.nextInt(1, 6)
                    var v = rng.nextInt(0, 8)
                    val values = ArrayList<Int>()
                    repeat(5) { i -> values += v; v += if (i % 2 == 0) a else b }
                    buildSequence(rng, values, "دو الگو یکی‌درمیان تکرار می‌شوند")
                }
                else -> {
                    // Squares plus offset.
                    val offset = rng.nextInt(0, 4)
                    val base = rng.nextInt(1, 3 + level)
                    val values = List(5) { i -> (base + i) * (base + i) + offset }
                    buildSequence(rng, values, "مربع اعداد را بررسی کن")
                }
            }
        }
    }

    data class NumberSequence(val shownFa: String, val options: List<Int>, val correctIndex: Int, val hintFa: String) {
        val correctValue: Int get() = options[correctIndex]
    }

    private fun buildSequence(rng: SplitMix64, values: List<Int>, hintFa: String): NumberSequence {
        val full = values + (values.last() + (values.last() - values.getOrElse(values.size - 2) { values.last() - 1 }))
        val answer = full.last()
        val shown = full.dropLast(1).joinToString("، ") {
            dev.mrsad.core.text.PersianText.toPersianDigits(it.toString())
        } + "، ؟"
        val wrongs = mutableSetOf<Int>()
        var guard = 0
        while (wrongs.size < 3 && guard++ < 64) {
            val delta = listOf(-3, -2, -1, 1, 2, 3, 4)[rng.nextInt(7)]
            val candidate = answer + delta
            if (candidate != answer && candidate >= 0) wrongs += candidate
        }
        val options = rng.shuffle((wrongs + answer).toList())
        return NumberSequence(shown, options, options.indexOf(answer), hintFa)
    }

    // ---------------- Spatial / visual ----------------

    private val SHAPES = listOf("▲", "■", "●", "◆")

    /**
     * Odd-one-out matrix: one cell breaks the pattern of the others.
     */
    fun oddOneOut(seed: Long, level: Int): OddOneOut {
        val rng = SplitMix64(seed)
        val side = (2 + level / 3).coerceIn(2, 3)
        val cells = side * side
        val base = SHAPES[rng.nextInt(SHAPES.size)]
        val odd = SHAPES[(SHAPES.indexOf(base) + 1 + rng.nextInt(SHAPES.size - 1)) % SHAPES.size]
        val oddIndex = rng.nextInt(cells)
        val symbols = MutableList(cells) { base }
        symbols[oddIndex] = odd
        return OddOneOut(side, symbols, oddIndex)
    }

    data class OddOneOut(val side: Int, val symbols: List<String>, val correctIndex: Int)
}
