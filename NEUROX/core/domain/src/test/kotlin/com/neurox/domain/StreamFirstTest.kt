package com.neurox.domain

import com.neurox.domain.repository.Stream
import com.neurox.domain.repository.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snapshot reads from repository streams.
 *
 * WHY THESE TESTS EXIST: the application shell, the settings screen, the library and
 * the reading studio each need a one-shot read of a repository stream. Calling
 * `collect` on a Room-backed stream never returns, so every one of those screens hung
 * before its first frame. These tests pin the primitive that replaced it.
 *
 * The bounded-wait tests are the important ones: an "empty stream" is not a corner
 * case, it is a brand-new profile with no settings row yet. Without a bound, the first
 * launch of the app hangs - which is exactly what happened.
 */
class StreamFirstTest {

    /** Emits [values], then never completes - exactly like a Room query. */
    private fun endlessStream(vararg values: Int): Stream<Int> = Stream { collector ->
        for (value in values) collector(value)
        kotlinx.coroutines.awaitCancellation()
    }

    @Test
    fun firstReturnsTheValueOfAStreamThatNeverCompletes() = runBlocking {
        assertEquals(7, endlessStream(7).first())
    }

    @Test
    fun firstReturnsTheFirstOfSeveralValues() = runBlocking {
        assertEquals(1, endlessStream(1, 2, 3).first())
    }

    @Test
    fun anEmptyStreamTimesOutInsteadOfHanging() = runBlocking {
        val started = System.nanoTime()
        assertNull(endlessStream().first(waitMillis = 150))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        // It waited, and it returned: both halves matter.
        assertTrue("returned after $elapsedMillis ms", elapsedMillis >= 140)
        assertTrue("returned promptly, not after the default budget", elapsedMillis < 2_000)
    }

    @Test
    fun aStreamWithAValueReturnsWithoutWaitingForTheBudget() = runBlocking {
        val started = System.nanoTime()
        assertEquals(5, endlessStream(5).first(waitMillis = 2_000))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue("returned in $elapsedMillis ms instead of waiting 2 s", elapsedMillis < 1_000)
    }

    @Test
    fun firstOfAStreamThatCompletesReturnsItsOnlyValue() = runBlocking {
        val completed: Stream<String> = Stream { collector -> collector("done") }
        assertEquals("done", completed.first())
    }

    @Test
    fun firstOfAnEmptyCompletedStreamIsNull() = runBlocking {
        val empty: Stream<String> = Stream { }
        assertNull(empty.first(waitMillis = 150))
    }

    @Test
    fun firstDoesNotLeakTheControlSignalToTheCaller() = runBlocking {
        // Calling twice proves the result is not cached state and the timeout is not
        // sticky: a second read on the same stream still works.
        val stream = endlessStream(42)
        assertEquals(42, stream.first())
        assertEquals(42, stream.first())
    }

    @Test
    fun aNegativeWaitIsRejectedInsteadOfBehavingLikeNoTimeout() = runBlocking {
        // A negative wait would silently mean "no timeout" if it were passed through
        // to withTimeoutOrNull, which is the opposite of what the caller asked for.
        val thrown = runCatching { endlessStream(1).first(waitMillis = -1) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $thrown",
            thrown is IllegalArgumentException)
    }
}
