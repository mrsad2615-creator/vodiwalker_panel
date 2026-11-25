package com.neurox.app

import com.neurox.common.MotionModeName
import com.neurox.data.repository.ProfileRepositoryImpl
import com.neurox.presentation.i18n.Strings

/**
 * The state the application shell needs from storage.
 *
 * Small on purpose: the activity must not become a second view model. Everything a
 * screen needs beyond this is loaded by the screen from its own repositories, so a
 * preference change does not force the whole tree to reload.
 */
data class AppUiState(
    val loaded: Boolean,
    val needsOnboarding: Boolean,
    val languageTag: String,
    val themeId: String,
    val motionMode: MotionModeName,
    val reducedMotion: Boolean,
) {
    companion object {
        fun initial() = AppUiState(
            loaded = false,
            // Not "needs onboarding" until storage says so: guessing either answer
            // would flash the wrong start screen on a cold start.
            needsOnboarding = false,
            languageTag = Strings.DEFAULT_LANGUAGE,
            themeId = ProfileRepositoryImpl.DEFAULT_THEME_ID,
            motionMode = MotionModeName.BALANCED,
            reducedMotion = false,
        )
    }
}
