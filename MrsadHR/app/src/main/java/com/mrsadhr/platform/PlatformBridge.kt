package com.mrsadhr.platform

enum class HapticKind { Light, Medium, Heavy, Success, Warning }

/**
 * Everything the pure application core needs from the host platform. The Android implementation
 * lives in [com.mrsadhr.services.AndroidBridge]; tests use a recording stub.
 */
interface PlatformBridge {
    /** Called whenever the countdown starts, pauses or stops, so the foreground service can follow. */
    fun onTimerStateChanged(running: Boolean, remainingMs: Long, durationMs: Long)

    /** The countdown reached zero. */
    fun onTimerCompleted(soundId: String, volume: Float, playSound: Boolean)

    fun onTimerDismissed()

    fun haptic(kind: HapticKind)

    fun keepScreenOn(enabled: Boolean)

    /** One second heartbeat while the countdown runs (drives the notification chronometer). */
    fun onTimerTick(remainingMs: Long, durationMs: Long)
}

object NullPlatformBridge : PlatformBridge {
    override fun onTimerStateChanged(running: Boolean, remainingMs: Long, durationMs: Long) {}
    override fun onTimerCompleted(soundId: String, volume: Float, playSound: Boolean) {}
    override fun onTimerDismissed() {}
    override fun haptic(kind: HapticKind) {}
    override fun keepScreenOn(enabled: Boolean) {}
    override fun onTimerTick(remainingMs: Long, durationMs: Long) {}
}

interface SoundEngine {
    fun playPreview(soundId: String, volume: Float)
    fun stopPreview()
    fun playCompletion(soundId: String, volume: Float)
    fun stopCompletion()
    fun release()
}

object NoopSoundEngine : SoundEngine {
    override fun playPreview(soundId: String, volume: Float) {}
    override fun stopPreview() {}
    override fun playCompletion(soundId: String, volume: Float) {}
    override fun stopCompletion() {}
    override fun release() {}
}
