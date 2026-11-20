package dev.mrsad.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the emulator runner and the smoke-test class in step.
 *
 * The instrumented tests do not run as one suite: the CI runner executes
 * one method per Gradle invocation, under a timeout, so a hanging test
 * cannot take the whole workflow budget with it. The list of methods lives
 * in `.ci/run-connected-tests.sh`.
 *
 * That split has a sharp edge: a method that is missing from the list
 * never runs at all, and a test that never runs is indistinguishable from
 * a test that passes. Two methods were silently missing for exactly that
 * reason — the brand-mark rendering check and the accessibility audit.
 *
 * So the two lists are compared here, over the real files, in a plain JVM
 * unit test that needs neither an emulator nor a device.
 */
class SmokeTestCoverageChecks {

    @Test
    fun everySmokeTestIsExecutedByTheCiRunner() {
        val declared = declaredTestMethods()
        val executed = runnerTestMethods()

        assertTrue(
            "no smoke test was found in the instrumented test class",
            declared.isNotEmpty(),
        )
        assertTrue(
            "the CI runner runs no smoke test at all",
            executed.isNotEmpty(),
        )
        assertEquals(
            "the CI runner and the smoke-test class disagree about which " +
                "tests exist; every method of the class has to be listed in " +
                ".ci/run-connected-tests.sh",
            declared.toSet(),
            executed.toSet(),
        )
        val duplicates = executed.groupingBy { it }.eachCount()
            .filterValues { count -> count > 1 }
            .keys
        assertTrue(
            "the CI runner lists these smoke tests twice: $duplicates",
            duplicates.isEmpty(),
        )
    }

    // ---- parsing helpers ---------------------------------------------------

    /** The names of the `@Test` methods in the instrumented smoke class. */
    private fun declaredTestMethods(): List<String> {
        val file = locateSmokeTestSource()
        val text = file.readText(Charsets.UTF_8)
        return Regex("@Test\\s+fun\\s+([A-Za-z0-9_]+)\\s*\\(", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }

    /** The names the CI runner passes to the instrumentation. */
    private fun runnerTestMethods(): List<String> {
        val script = locateRunnerScript()
        val text = script.readText(Charsets.UTF_8)
        val block = Regex("METHODS=\"([^\"]*)\"", RegexOption.DOT_MATCHES_ALL)
            .find(text)
            ?.groupValues
            ?.get(1)
            ?: error("no METHODS list found in ${script.path}")
        return block.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** The file that holds the instrumented smoke tests. */
    private fun locateSmokeTestSource(): File {
        val direct = listOf(
            File("src/androidTest/kotlin/dev/mrsad/app/MrsadSmokeTest.kt"),
            File("app/src/androidTest/kotlin/dev/mrsad/app/MrsadSmokeTest.kt"),
            File("../app/src/androidTest/kotlin/dev/mrsad/app/MrsadSmokeTest.kt"),
        ).firstOrNull { it.isFile }
        if (direct != null) return direct

        var current: File? = File(".").absoluteFile
        while (current != null) {
            val candidate = File(current, "app/src/androidTest/kotlin/dev/mrsad/app/MrsadSmokeTest.kt")
            if (candidate.isFile) return candidate
            current = current.parentFile
        }
        error(
            "cannot locate the instrumented smoke test from ${File(".").absolutePath}; " +
                "the CI coverage check cannot run",
        )
    }

    /** The shell script the emulator job runs. */
    private fun locateRunnerScript(): File {
        val direct = listOf(
            File(".ci/run-connected-tests.sh"),
            File("../.ci/run-connected-tests.sh"),
            File("mrsad/.ci/run-connected-tests.sh"),
        ).firstOrNull { it.isFile }
        if (direct != null) return direct

        var current: File? = File(".").absoluteFile
        while (current != null) {
            val candidate = File(current, ".ci/run-connected-tests.sh")
            if (candidate.isFile) return candidate
            current = current.parentFile
        }
        error(
            "cannot locate .ci/run-connected-tests.sh from ${File(".").absolutePath}; " +
                "the CI coverage check cannot run",
        )
    }
}
