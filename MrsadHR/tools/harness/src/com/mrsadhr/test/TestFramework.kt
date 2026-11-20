package com.mrsadhr.test

/**
 * A deliberately tiny test harness.
 *
 * The project ships no test framework dependency (nothing but the Kotlin standard library is
 * reachable in this build environment), so tests are plain functions registered in [RunTests] and
 * executed by [Suite]. Failures carry the expectation that did not hold, and the process exits
 * non-zero when anything fails, which is what CI needs.
 */
class AssertionFailed(message: String) : RuntimeException(message)

object Suite {

    private val cases = mutableListOf<Triple<String, String, () -> Unit>>()

    fun test(group: String, name: String, body: () -> Unit) {
        cases += Triple(group, name, body)
    }

    fun run(): Int {
        var passed = 0
        val failures = mutableListOf<String>()
        var currentGroup = ""
        for ((group, name, body) in cases) {
            if (group != currentGroup) {
                println("\n$group")
                currentGroup = group
            }
            try {
                body()
                passed++
                println("  PASS  $name")
            } catch (t: Throwable) {
                val message = when (t) {
                    is AssertionFailed -> t.message ?: "assertion failed"
                    else -> "${t::class.simpleName}: ${t.message}"
                }
                failures += "$group / $name -> $message"
                println("  FAIL  $name")
                println("        $message")
                t.stackTrace.firstOrNull { it.className.startsWith("com.mrsadhr") }?.let {
                    println("        at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
                }
            }
        }
        println()
        println("=".repeat(64))
        println("${cases.size} tests, $passed passed, ${failures.size} failed")
        if (failures.isNotEmpty()) {
            println()
            failures.forEach { println("FAILED: $it") }
            return 1
        }
        return 0
    }
}

fun expect(condition: Boolean, message: String) {
    if (!condition) throw AssertionFailed(message)
}

fun <T> expectEq(expected: T, actual: T, what: String) {
    if (expected != actual) throw AssertionFailed("$what: expected <$expected> but was <$actual>")
}

fun expectNear(expected: Long, actual: Long, tolerance: Long, what: String) {
    if (kotlin.math.abs(expected - actual) > tolerance) {
        throw AssertionFailed("$what: expected <$expected> ±$tolerance but was <$actual>")
    }
}

fun expectNear(expected: Float, actual: Float, tolerance: Float, what: String) {
    if (kotlin.math.abs(expected - actual) > tolerance) {
        throw AssertionFailed("$what: expected <$expected> ±$tolerance but was <$actual>")
    }
}
