package com.neurox.common.log

/**
 * Logger abstraction. Domain code never imports android.util.Log.
 *
 * Implementations must NOT log personal data (display names, typed text, PINs)
 * or cryptographic material. See docs/SECURITY_SPEC.md.
 */
interface NxLog {
    fun debug(tag: String, message: String)
    fun warn(tag: String, message: String, error: Throwable? = null)
    fun error(tag: String, message: String, error: Throwable? = null)
}

object NoopLog : NxLog {
    override fun debug(tag: String, message: String) = Unit
    override fun warn(tag: String, message: String, error: Throwable?) = Unit
    override fun error(tag: String, message: String, error: Throwable?) = Unit
}

/** Recorder used by tests to assert on emitted diagnostics. */
class RecordingLog : NxLog {
    val entries: MutableList<String> = mutableListOf()
    override fun debug(tag: String, message: String) { entries += "D/$tag $message" }
    override fun warn(tag: String, message: String, error: Throwable?) { entries += "W/$tag $message" }
    override fun error(tag: String, message: String, error: Throwable?) { entries += "E/$tag $message" }
}
