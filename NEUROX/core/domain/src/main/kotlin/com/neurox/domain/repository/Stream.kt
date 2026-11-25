package com.neurox.domain.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 * Reads the current value of a stream and returns.
 *
 * WHY THIS EXISTS: a repository stream is a Room Flow, so `collect` never returns on
 * its own. Calling it in a load path is a hang, not a read - it was the reason the
 * application shell never finished starting. This is the one supported way to take a
 * snapshot.
 *
 * [waitMillis] is a bounded wait, not a subscription: a stream that has a row emits
 * immediately, so the common case returns on the first value with no delay. A stream
 * with nothing to emit yet returns null after the budget instead of blocking the
 * caller forever - which is exactly what an unbounded `first()` does to an empty
 * Room query, and exactly the hang this file exists to prevent.
 *
 * The collector runs in a child scope so that leaving the timeout (or the caller's
 * cancellation) cancels it: no orphaned collector is left subscribed to the database.
 */
suspend fun <T> Stream<T>.first(waitMillis: Long = DEFAULT_FIRST_WAIT_MILLIS): T? {
    if (waitMillis < 0) {
        throw IllegalArgumentException("waitMillis must not be negative: $waitMillis")
    }
    val received = Channel<T>(capacity = 1)
    return coroutineScope {
        val collector = launch(Dispatchers.IO) {
            try {
                collect { value -> received.trySend(value) }
            } finally {
                received.close()
            }
        }
        try {
            // receiveCatching maps "closed with nothing sent" to a closed result
            // instead of throwing, so an empty completed stream is null and not a
            // crash. A database with no row yet is exactly this case.
            withTimeoutOrNull(waitMillis) {
                received.receiveCatching().getOrNull()
            }
        } finally {
            // Cancels the collector whatever happened: value received, timeout, or the
            // caller going away.
            collector.cancel()
        }
    }
}

/** How long [first] waits for a stream that has nothing to emit yet. */
const val DEFAULT_FIRST_WAIT_MILLIS: Long = 750L
