package dev.mrsad.app.testing

import androidx.compose.runtime.mutableStateOf
import dev.mrsad.app.BuildConfig

/**
 * Deterministic-test hook.
 *
 * A Compose UI test can only finish when the UI stops asking for frames.
 * MRSAD has exactly three things that request frames forever:
 *
 *  - the ambient particle field in `AmbientBackground`,
 *  - the living core in `MrsadCore`,
 *  - the focus dial in `FocusDial`,
 *
 * and two controllers that refresh their state on a fixed timer
 * (`TypeViewModel` every 100 ms, `FocusViewModel` every 500 ms).
 *
 * The emulator's "disable animations" switch only scales *Android* animators
 * to zero — a Compose infinite transition keeps requesting frames regardless,
 * so an instrumentation test would wait for idle forever.
 *
 * Instrumented tests turn the hook on *before* the activity is created, so the
 * very first rendered frame is already static and the Compose idling resource
 * can reach idle. The flag is a Compose state, so flipping it later also
 * disposes animations that are already running.
 *
 * The hook is inert in a release build: it can never be switched on there.
 */
object MrsadTestHooks {

    private val deterministic = mutableStateOf(false)

    /** True while the deterministic (animation-free) mode is active. */
    val isDeterministic: Boolean
        get() = deterministic.value

    /**
     * Turns off every recurring animation and timer.
     *
     * Only instrumentation tests may call this, and only in a debug build.
     */
    fun enableDeterministicMode() {
        if (!BuildConfig.DEBUG) return
        deterministic.value = true
    }

    /** Restores the normal, animated behaviour. */
    fun disableDeterministicMode() {
        deterministic.value = false
    }
}
