package dev.mrsad.hr

enum class Phase {
    READY,
    RUNNING,
    PAUSED,
    COMPLETE,
}

data class Clock(
    val goal: Long = 300000,
    val bank: Long = 0,
    val anchor: Long = 0,
    val phase: Phase = Phase.READY,
    val watch: Boolean = false,
    val laps: List<Long> = emptyList(),
) {
    fun elapsed(now: Long) =
        bank + if (phase == Phase.RUNNING) (now - anchor).coerceAtLeast(0) else 0

    fun value(now: Long) = if (watch) elapsed(now) else (goal - elapsed(now)).coerceAtLeast(0)

    fun start(now: Long): Clock =
        when {
            phase == Phase.RUNNING || (!watch && goal == 0L) -> this
            phase == Phase.COMPLETE -> reset().start(now)
            else -> copy(anchor = now, phase = Phase.RUNNING)
        }

    fun settle(now: Long) =
        if (!watch && phase == Phase.RUNNING && value(now) == 0L)
            copy(bank = goal, anchor = 0, phase = Phase.COMPLETE)
        else this

    fun pause(now: Long) =
        settle(now).let {
            if (it.phase == Phase.RUNNING)
                it.copy(bank = elapsed(now), anchor = 0, phase = Phase.PAUSED)
            else it
        }

    fun reset() = Clock(goal = goal, watch = watch)

    fun lap(now: Long) =
        if (watch && phase == Phase.RUNNING) copy(laps = laps + elapsed(now)) else this
}
