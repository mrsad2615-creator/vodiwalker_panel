package com.neurox.domain

import kotlin.random.Random

/** All generators are deterministic for a saved configuration and round number. */
enum class SearchMark { T_UP, T_RIGHT, T_DOWN, T_LEFT, L_UP, L_RIGHT, L_DOWN, L_LEFT }
data class SearchBoard(val cells: List<SearchMark?>, val targetCell: Int)
data class PhraseFrame(val text: String, val index: Int, val count: Int, val minimumDisplayMs: Long)

object Stimuli {
    private fun random(config: TrainingConfig, round: Int, salt: Long) = Random(config.stimulusSeed xor (round.toLong() * 1_000_003L) xor salt)

    fun search(config: TrainingConfig, round: Int): SearchBoard {
        val random = random(config, round, 0x53454152)
        val positions = (0..15).shuffled(random)
        val target = positions.first()
        val distractors = SearchMark.entries.filter { it != SearchMark.T_UP }
        val cells = MutableList<SearchMark?>(16) { null }
        cells[target] = SearchMark.T_UP
        positions.drop(1).take(3 + (config.difficulty - 1) * 3).forEach {
            cells[it] = distractors[random.nextInt(distractors.size)]
        }
        return SearchBoard(cells.toList(), target)
    }

    /** Balanced shuffled plan. At least one GO; at least one NO-GO when rounds >= 2. */
    fun goPlan(config: TrainingConfig): List<Boolean> {
        val noGoCount = if (config.rounds == 1) 0 else (config.rounds / 3).coerceAtLeast(1)
        return (List(config.rounds - noGoCount) { true } + List(noGoCount) { false })
            .shuffled(random(config, 0, 0x474F4E4F))
    }

    fun phrases(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .chunked(3).map { it.joinToString(" ") }

    fun phraseDisplayMs(text: String, difficulty: Int): Long {
        require(difficulty in 1..5)
        val wpm = 120 + (difficulty - 1) * 30
        return (wordCount(text) * 60_000L / wpm).coerceAtLeast(400)
    }

    fun sprintWords(config: TrainingConfig, round: Int): List<String> {
        val words = if (config.language == Language.FA) listOf(
            "نور", "روز", "راه", "رنگ", "برگ", "خانه", "آرام", "زمان", "کتاب", "دریا", "تمرین", "دقت", "فردا", "تلاش", "تمرکز", "یادگیری", "اندیشه", "نوشتار", "پیشرفت", "فرصت"
        ) else listOf(
            "sun", "day", "way", "red", "leaf", "home", "calm", "time", "book", "sea", "train", "care", "learn", "focus", "write", "growth", "thought", "practice", "progress", "purpose"
        )
        val pool = if (config.difficulty <= 2) words.filter { it.codePointCount(0, it.length) <= 4 } else words
        return pool.shuffled(random(config, round, 0x574F5244)).take(3 + config.difficulty)
    }
}
