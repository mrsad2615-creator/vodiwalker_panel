package com.neurox.domain.repository

/**
 * Structural stand-in for an observable stream.
 *
 * WHY (decision DEC-0003): the domain must stay free of Android and of any
 * third-party runtime so it can be unit-tested on a plain JVM. `kotlinx.coroutines`
 * `Flow<T>` is a *fun interface* with the single abstract method
 * `collect(collector)`, so any `Flow<T>` value is assignable to this interface
 * without an adapter and without the domain knowing about coroutines.
 *
 * The data layer returns real `Flow<T>` values from Room and DataStore. The
 * presentation layer collects the same `Flow<T>` with `collectAsStateWithLifecycle`.
 * See docs/ARCHITECTURE.md, section "Repository streams".
 */
fun interface Stream<out T> {
    suspend fun collect(collector: (T) -> Unit)
}

/**
 * Reads the first value of a stream and returns.
 *
 * WHY THIS EXISTS: a repository stream is backed by a Room Flow, so `collect` never
 * returns on its own. Calling it directly in a load path is a hang, not a read - it
 * was the reason the application shell never finished starting. This is the one
 * supported way to take a snapshot.
 */
suspend fun <T> Stream<T>.first(): T? {
    var captured: T? = null
    var seen = false
    try {
        collect { value ->
            if (!seen) {
                seen = true
                captured = value
                throw FirstValueFound()
            }
        }
    } catch (stop: FirstValueFound) {
        // Expected: the collector is cancelled after the first value.
    }
    return captured
}

/** Internal control-flow signal used by [first]. Never escapes the call. */
private class FirstValueFound : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}
