package com.mrsadhr.android

import android.content.Context
import com.mrsadhr.app.Action
import com.mrsadhr.app.AppController
import com.mrsadhr.platform.HapticKind
import com.mrsadhr.platform.PlatformBridge

/**
 * Connects the pure controller to Android: foreground service, notifications, haptics, the screen
 * wake lock and audio. Everything here is fire-and-forget and main-thread only.
 */
class AndroidBridge(
    private val context: Context,
    private val sounds: SoundPlayer,
    private val haptics: Haptics,
) : PlatformBridge {

    private val notifications = Notifications(context).also { it.ensureChannels() }
    private var controller: AppController? = null

    /** Wired by the view: the activity is the only one that can hold the screen awake. */
    var screenOnListener: ((Boolean) -> Unit)? = null

    /** Wired by the activity so a notification permission result can update the UI. */
    var notificationsAllowed: Boolean = true

    fun attach(controller: AppController) {
        this.controller = controller
    }

    private fun wantsNotifications(): Boolean =
        controller?.state()?.settings?.notifications ?: false

    private fun wantsSound(): Boolean = controller?.state()?.settings?.soundEnabled ?: false

    override fun onTimerStateChanged(running: Boolean, remainingMs: Long, durationMs: Long) {
        val state = controller?.state()
        val notify = state?.settings?.notifications ?: false
        keepScreenOn(state != null && state.settings.keepScreenOn && running)
        if (running && notify && notifications.canPost()) {
            TimerService.start(context, durationMs, remainingMs)
        } else {
            TimerService.stop(context)
            notifications.cancelRunning()
        }
        if (!running) sounds.stopCompletion()
    }

    override fun onTimerCompleted(soundId: String, volume: Float, playSound: Boolean) {
        val state = controller?.state()
        if (playSound) sounds.playCompletion(soundId, volume)
        if (state != null && state.settings.notifications && notifications.canPost()) {
            if (state.settings.soundId.isNotEmpty()) {
                notifications.notifyCompleted(
                    state.timer.durationMs,
                    state.settings.language,
                    soundId,
                )
            }
        }
    }

    override fun onTimerDismissed() {
        sounds.stopCompletion()
        notifications.cancelCompleted()
    }

    override fun haptic(kind: HapticKind) = haptics.play(kind)

    override fun keepScreenOn(enabled: Boolean) {
        screenOnListener?.invoke(enabled)
    }

    /**
     * The running notification is a system chronometer with a count-down deadline, so it stays
     * exact without any per-second work from the app. Nothing to do here by design.
     */
    override fun onTimerTick(remainingMs: Long, durationMs: Long) {
        if (notificationsAllowed) Unit
        if (remainingMs < 0L) Unit
    }

    fun dispatch(action: Action) {
        controller?.dispatch(action)
    }

    fun canPostNotifications(): Boolean = notifications.canPost()

    fun cancelAllNotifications() {
        notifications.cancelRunning()
        notifications.cancelCompleted()
    }

    val isSoundEnabled: Boolean get() = wantsSound()
}
