/*
 * Offline test runner for the NEUROX verification environment.
 *
 * Discovers every class whose simple name ends in "Test" in the compiled output,
 * instantiates it, and runs its public methods annotated with @org.junit.Test in
 * name order. Prints a JUnit-style summary and exits non-zero on any failure.
 *
 * This runner is NOT used by Gradle. Gradle runs the same test classes with the
 * real JUnit 4 runner. See docs/TEST_PLAN.md.
 */
package com.neurox.verify

import java.io.File
import java.lang.reflect.InvocationTargetException

fun main(args: Array<String>) {
    // Accepts one or more .jar files, or directories, and scans them for tests.
    val inputs = if (args.isEmpty()) listOf("build/verify-tests.jar") else args.toList()
    val entries = inputs
    val classes = inputs.flatMap { path ->
        val file = File(path)
        require(file.exists()) { "input not found: $path" }
        if (file.isDirectory) {
            file.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") && !it.name.contains('$') }
                .map { it.relativeTo(file).path.removeSuffix(".class").replace(File.separatorChar, '.') }
                .toList()
        } else {
            java.util.zip.ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && !it.contains('$') }
                    .map { it.removeSuffix(".class").replace('/', '.') }
                    .toList()
            }
        }
    }.filter { it.endsWith("Test") }.distinct().sorted()

    if (classes.isEmpty()) {
        println("FATAL: no test classes found in ${inputs.joinToString()}")
        kotlin.system.exitProcess(2)
    }

    var run = 0
    var failed = 0
    var skipped = 0
    val failures = mutableListOf<String>()

    for (className in classes) {
        val clazz = Class.forName(className)
        val testMethods = clazz.declaredMethods
            .filter { m -> m.annotations.any { it.annotationClass.qualifiedName == "org.junit.Test" } }
            .sortedBy { it.name }
        if (testMethods.isEmpty()) continue
        val instance = clazz.getDeclaredConstructor().newInstance()
        for (method in testMethods) {
            val ignored = method.annotations.any { it.annotationClass.qualifiedName == "org.junit.Ignore" }
            if (ignored) {
                skipped += 1
                println("SKIP ${clazz.simpleName}.${method.name}")
                continue
            }
            run += 1
            val label = "${clazz.simpleName}.${method.name}"
            try {
                method.invoke(instance)
                println("PASS $label")
            } catch (e: InvocationTargetException) {
                val cause = e.targetException
                failed += 1
                failures += "$label -> ${cause::class.java.simpleName}: ${cause.message}"
                println("FAIL $label")
                println("     ${cause::class.java.simpleName}: ${cause.message}")
            } catch (e: Throwable) {
                failed += 1
                failures += "$label -> $e"
                println("FAIL $label -> $e")
            }
        }
    }

    println()
    println("Total: $run   Passed: ${run - failed}   Failed: $failed   Skipped: $skipped")
    println("Classpath entries: ${entries.size}")
    if (failed > 0) {
        println("--- FAILURES ---")
        failures.forEach { println(it) }
        kotlin.system.exitProcess(1)
    }
}
