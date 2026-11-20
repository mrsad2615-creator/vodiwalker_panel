package dev.mrsad.core.mind

/**
 * Adaptive difficulty ladder for the cognitive exercises.
 *
 * The ladder is deliberately strict and deliberately small:
 *
 * * a level only moves after **two consecutive** rounds in the same
 *   direction, so a single lucky or unlucky round never changes the
 *   difficulty;
 * * the ladder is capped, so no exercise can become unwinnable;
 * * the level is a pure function of the recorded scores, which means the
 *   level the user sees after a restart is exactly the level they had
 *   before it — no hidden state, no "the app forgot" surprises.
 *
 * Every exercise carries its own ladder. A user who is fast at arithmetic
 * but slow at pattern recall is not dragged up and down by a single
 * blended average: each skill moves on its own evidence.
 */
object MindProgression {

    /** Highest level the ladder reaches. */
    const val MAX_LEVEL: Int = 8

    /** A round at or above this score counts as strong. */
    const val PROMOTE_AT: Double = 0.85

    /** A round below this score counts as weak. */
    const val DEMOTE_AT: Double = 0.50

    /** Consecutive strong rounds needed to move up. */
    const val PROMOTE_STREAK: Int = 2

    /** Consecutive weak rounds needed to move down. */
    const val DEMOTE_STREAK: Int = 2

    /**
     * The level implied by a score history.
     *
     * @param scores the rounds played for one exercise, oldest first.
     * @return the level the ladder has reached, 0..[MAX_LEVEL].
     */
    fun levelFrom(scores: List<Double>): Int {
        var level = 0
        var strongRun = 0
        var weakRun = 0
        for (score in scores) {
            when {
                score >= PROMOTE_AT -> {
                    strongRun++
                    weakRun = 0
                }
                score < DEMOTE_AT -> {
                    weakRun++
                    strongRun = 0
                }
                else -> {
                    strongRun = 0
                    weakRun = 0
                }
            }
            if (strongRun >= PROMOTE_STREAK) {
                level = (level + 1).coerceAtMost(MAX_LEVEL)
                strongRun = 0
            } else if (weakRun >= DEMOTE_STREAK) {
                level = (level - 1).coerceAtLeast(0)
                weakRun = 0
            }
        }
        return level
    }

    /**
     * Whether the ladder would move after appending [score] to [scores].
     *
     * @return `+1` when the level goes up, `-1` when it goes down, `0` when
     *   it stays where it is.
     */
    fun step(scores: List<Double>, score: Double): Int {
        val before = levelFrom(scores)
        val after = levelFrom(scores + score)
        return when {
            after > before -> 1
            after < before -> -1
            else -> 0
        }
    }

    /** A short human label for a level, for the profile cards. */
    fun labelFa(level: Int): String = when (level.coerceIn(0, MAX_LEVEL)) {
        0 -> "بنیادی"
        1, 2 -> "در حال رشد"
        3, 4 -> "میانی"
        5, 6 -> "پیشرفته"
        else -> "تخصصی"
    }
}
