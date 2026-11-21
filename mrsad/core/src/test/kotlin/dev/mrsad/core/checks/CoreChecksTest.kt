package dev.mrsad.core.checks

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JUnit entry point so CI (`./gradlew :core:test`) executes the same
 * executable specification as the local plain-Kotlin runner.
 */
class CoreChecksTest {

    @Test
    fun domainCoreSpecificationPasses() {
        assertEquals("MRSAD core checks failed (see stdout)", 0, CoreChecks.runAll())
    }
}
