package com.mrsadhr.app

import com.mrsadhr.core.model.Language
import com.mrsadhr.core.model.Mode
import com.mrsadhr.core.model.TimePart
import com.mrsadhr.themes.ThemeId

sealed interface Action {
    data class SetMode(val mode: Mode) : Action

    data object TimerStart : Action
    data object TimerPause : Action
    data object TimerReset : Action
    data object TimerRepeat : Action
    data object DismissCompletion : Action

    data class SetDuration(val ms: Long) : Action
    data class StepPart(val part: TimePart, val delta: Int) : Action
    data class SelectPreset(val seconds: Long) : Action
    data object SavePreset : Action
    data class DeletePreset(val id: String) : Action
    data class UpdatePreset(val id: String) : Action

    data class SetTheme(val theme: ThemeId) : Action
    data class SetLanguage(val language: Language) : Action

    data object ToggleHaptics : Action
    data object ToggleSound : Action
    data class SetVolume(val volume: Float) : Action
    data class SelectSound(val id: String) : Action
    data class PreviewSound(val id: String) : Action
    data object StopPreview : Action

    data object ToggleFocusMode : Action
    data object ToggleReducedMotion : Action
    data object ToggleKeepScreenOn : Action
    data object ToggleNotifications : Action

    data object StopwatchToggle : Action
    data object StopwatchReset : Action
    data object StopwatchLap : Action
    data object StopwatchClearLaps : Action
}
