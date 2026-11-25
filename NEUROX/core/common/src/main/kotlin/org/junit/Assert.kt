@file:Suppress("unused")

package org.junit

/**
 * Source-only JUnit 4 assertion API for offline verification.
 *
 * WHY (decision DEC-0004): the NEUROX tests use the standard JUnit 4 API, which
 * Gradle resolves normally when the dependency repository is reachable. The
 * NEUROX sandbox verification environment has no Maven access, so this
 * declaration-only shim makes the *same, unmodified test sources* compile and run
 * there. It contains no behaviour beyond assertions, and it lives in `src/main`
 * so it can be compiled into the offline classpath only; Gradle's unit-test
 * classpath puts the real `junit:junit` artifact first, so this shim is not part
 * of the shipped application (it is a compile classpath entry, never a runtime
 * dependency of `app`).
 */
object Assert {
    @JvmStatic
    fun fail(message: String?): Nothing = throw AssertionError(message)

    @JvmStatic
    fun fail() {
        throw AssertionError()
    }

    @JvmStatic
    fun assertTrue(condition: Boolean) {
        if (!condition) throw AssertionError("expected true but was false")
    }

    @JvmStatic
    fun assertTrue(message: String?, condition: Boolean) {
        if (!condition) throw AssertionError(message ?: "expected true but was false")
    }

    @JvmStatic
    fun assertFalse(condition: Boolean) {
        if (condition) throw AssertionError("expected false but was true")
    }

    @JvmStatic
    fun assertFalse(message: String?, condition: Boolean) {
        if (condition) throw AssertionError(message ?: "expected false but was true")
    }

    @JvmStatic
    fun assertNull(value: Any?) {
        if (value != null) throw AssertionError("expected null but was <$value>")
    }

    @JvmStatic
    fun assertNotNull(value: Any?) {
        if (value == null) throw AssertionError("expected not null")
    }

    @JvmStatic
    fun assertNotNull(message: String?, value: Any?) {
        if (value == null) throw AssertionError(message ?: "expected not null")
    }

    @JvmStatic
    fun assertNull(message: String?, value: Any?) {
        if (value != null) throw AssertionError(message ?: "expected null but was <$value>")
    }

    @JvmStatic
    fun assertEquals(expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("expected <$expected> but was <$actual>")
        }
    }

    @JvmStatic
    fun assertEquals(message: String?, expected: Float, actual: Float, delta: Float) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("$message: expected <$expected> but was <$actual> (delta $delta)")
        }
    }

    @JvmStatic
    fun assertEquals(message: String?, expected: Int, actual: Int) {
        if (expected != actual) throw AssertionError("$message: expected <$expected> but was <$actual>")
    }

    @JvmStatic
    fun assertEquals(message: String?, expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("$message: expected <$expected> but was <$actual>")
        }
    }

    @JvmStatic
    fun assertEquals(expected: Long, actual: Long) {
        if (expected != actual) throw AssertionError("expected <$expected> but was <$actual>")
    }

    @JvmStatic
    fun assertEquals(message: String?, expected: Long, actual: Long) {
        if (expected != actual) throw AssertionError("$message: expected <$expected> but was <$actual>")
    }

    @JvmStatic
    fun assertEquals(expected: Int, actual: Int) {
        if (expected != actual) throw AssertionError("expected <$expected> but was <$actual>")
    }

    @JvmStatic
    fun assertEquals(expected: Float, actual: Float, delta: Float) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("expected <$expected> but was <$actual> (delta $delta)")
        }
    }

    @JvmStatic
    fun assertEquals(expected: Double, actual: Double, delta: Double) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("expected <$expected> but was <$actual> (delta $delta)")
        }
    }

    @JvmStatic
    fun assertEquals(message: String?, expected: Double, actual: Double, delta: Double) {
        if (kotlin.math.abs(expected - actual) > delta) {
            throw AssertionError("$message: expected <$expected> but was <$actual> (delta $delta)")
        }
    }

    @JvmStatic
    fun assertNotEquals(unexpected: Any?, actual: Any?) {
        if (unexpected == actual) throw AssertionError("expected a value different from <$unexpected>")
    }

    @JvmStatic
    fun assertSame(expected: Any?, actual: Any?) {
        if (expected !== actual) throw AssertionError("expected same instance")
    }

    @JvmStatic
    fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        if (!expected.contentEquals(actual)) throw AssertionError("byte arrays differ")
    }

    @JvmStatic
    fun <T> assertArrayEquals(expected: Array<T>, actual: Array<T>) {
        if (!expected.contentEquals(actual)) throw AssertionError("arrays differ")
    }

    @JvmStatic
    inline fun <reified T : Throwable> assertThrows(clazz: Class<T>?, block: () -> Unit): T {
        try {
            block()
        } catch (t: Throwable) {
            if (clazz == null || clazz.isInstance(t)) return t as T
            throw AssertionError("expected ${clazz.name} but caught ${t::class.java.name}")
        }
        throw AssertionError("expected ${clazz?.name ?: "an exception"} but nothing was thrown")
    }

    @JvmStatic
    fun <T> assertThrows(clazz: Class<T>?, block: Runnable): T {
        try {
            block.run()
        } catch (t: Throwable) {
            if (clazz == null) return t as T
            if (clazz.isInstance(t)) return clazz.cast(t)
            throw AssertionError("expected ${clazz.name} but caught ${t::class.java.name}")
        }
        throw AssertionError("expected ${clazz?.name ?: "an exception"} but nothing was thrown")
    }
}
