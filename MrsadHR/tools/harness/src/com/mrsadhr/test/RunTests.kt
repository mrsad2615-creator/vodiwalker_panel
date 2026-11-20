package com.mrsadhr.test

import java.io.File
import kotlin.system.exitProcess

/**
 * Entry point for the whole test suite.
 *
 *   java -cp <classes>:<kotlin-stdlib> com.mrsadhr.test.RunTests
 *
 * Exits non-zero when anything fails so it can be used directly in CI.
 */
object RunTests {
    @JvmStatic
    fun main(args: Array<String>) {
        val fonts = File("app/src/main/assets/fonts")
        if (!fonts.isDirectory) {
            System.err.println("run from the project root: ${fonts.absolutePath} not found")
            exitProcess(2)
        }
        EngineTests.register()
        ControllerTests.register()
        InteractionTests.register()
        val code = Suite.run()
        if (code == 0) println("OK") else println("FAILURES: $code")
        exitProcess(code)
    }
}
