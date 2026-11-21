package dev.mrsad.app.testing

import android.util.Log
import androidx.compose.runtime.mutableStateOf

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
 * so an instrumentation test would wait for idle forever. Espresso's
 * `onIdle()` has no timeout of its own, so such a test hangs the whole job.
 *
 * Instrumented tests turn the hook on *before* the activity is created, so the
 * very first rendered frame is already static and the Compose idling resource
 * can reach idle. The flag is a Compose state, so flipping it later also
 * disposes animations that are already running.
 *
 * The hook lives in the application source set (a Compose state cannot be
 * shared with the test source set otherwise) and is never enabled by the
 * product: only instrumentation tests call [enableDeterministicMode].
 */
object MrsadTestHooks {

    const val TAG = "MrsadTest"

    private val deterministic = mutableStateOf(false)

    /** True while the deterministic (animation-free) mode is active. */
    val isDeterministic: Boolean
        get() = deterministic.value

    /**
     * Turns off every recurring animation and timer.
     *
     * Only instrumentation tests call this.
     */
    fun enableDeterministicMode() {
        deterministic.value = true
        Log.i(TAG, "deterministic mode enabled")
    }

    /** Restores the normal, animated behaviour. */
    fun disableDeterministicMode() {
        deterministic.value = false
        Log.i(TAG, "deterministic mode disabled")
    }
}
