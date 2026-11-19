package dev.mrsad.core.session

/**
 * Focus / Pomodoro session machine. Time is supplied by the caller from
 * monotonic sources (e.g. SystemClock.elapsedRealtime), so locking the screen
 * or backgrounding the app can never make the timer drift.
 */
class FocusSession(
    val focusMinutes: Int,
    val breakMinutes: Int,
    val rounds: Int,
) {
    enum class Phase { FOCUS, BREAK, DONE }

    var phase: Phase = Phase.FOCUS
        private set
    var round: Int = 1
        private set

    private var phaseStartedAtMillis: Long = -1L
    private var elapsedBeforePause: Long = 0L
    private var pausedAtMillis: Long = -1L

    val isPaused: Boolean get() = pausedAtMillis >= 0L

    fun phaseMillis(): Long = when (phase) {
        Phase.FOCUS -> roundPhase(focusMinutes)
        Phase.BREAK -> roundPhase(breakMinutes)
        Phase.DONE -> 0L
    }

    fun start(nowMillis: Long) {
        phaseStartedAtMillis = nowMillis
        pausedAtMillis = -1L
        elapsedBeforePause = 0L
    }

    fun pause(nowMillis: Long) {
        if (isPaused || phase == Phase.DONE) return
        // Capture elapsed BEFORE marking paused, otherwise elapsedInPhase would
        // return the (still zero) frozen value instead of live progress.
        elapsedBeforePause = (nowMillis - phaseStartedAtMillis).coerceAtLeast(0L)
        pausedAtMillis = nowMillis
    }

    fun resume(nowMillis: Long) {
        if (!isPaused) return
        phaseStartedAtMillis = nowMillis - elapsedBeforePause
        pausedAtMillis = -1L
    }

    fun elapsedInPhase(nowMillis: Long): Long =
        if (pausedAtMillis >= 0) elapsedBeforePause
        else if (phaseStartedAtMillis < 0) 0L
        else (nowMillis - phaseStartedAtMillis).coerceAtLeast(0L)

    fun remainingMillis(nowMillis: Long): Long =
        (phaseMillis() - elapsedInPhase(nowMillis)).coerceAtLeast(0L)

    fun progress(nowMillis: Long): Float {
        val total = phaseMillis()
        return if (total <= 0L) 1f else (elapsedInPhase(nowMillis).toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * Advances phases whose deadline has already passed. Returns the new phase
     * or null when nothing changed. Handles long background gaps by skipping
     * whole completed phases.
     */
    fun tick(nowMillis: Long): Phase? {
        if (phase == Phase.DONE || phaseStartedAtMillis < 0) return null
        var changed: Phase? = null
        while (phase != Phase.DONE && !isPaused && elapsedInPhase(nowMillis) >= phaseMillis()) {
            val overshoot = elapsedInPhase(nowMillis) - phaseMillis()
            when (phase) {
                Phase.FOCUS -> {
                    if (round >= rounds) {
                        phase = Phase.DONE
                        changed = Phase.DONE
                    } else {
                        phase = Phase.BREAK
                        phaseStartedAtMillis = nowMillis - overshoot
                        changed = Phase.BREAK
                    }
                }
                Phase.BREAK -> {
                    phase = Phase.FOCUS
                    round++
                    phaseStartedAtMillis = nowMillis - overshoot
                    changed = Phase.FOCUS
                }
                Phase.DONE -> Unit
            }
        }
        return changed
    }

    /** Total focus minutes for the session (breaks excluded). */
    val plannedFocusMinutes: Int get() = focusMinutes * rounds

    private fun roundPhase(minutes: Int): Long = minutes.coerceAtLeast(1) * 60_000L
}
