package com.neurox.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.neurox.common.MotionModeName
import com.neurox.designsystem.tokens.NxMotion
import com.neurox.presentation.i18n.LayoutDirection as TextLayoutDirection
import com.neurox.presentation.i18n.Strings

/**
 * Bridge between the pure presentation module and Compose.
 *
 * WHY A BRIDGE: the presentation module deliberately has no Android dependency, so
 * it cannot call Compose and Compose cannot see its layout enum. Translating once,
 * in one file, is what keeps both modules honest: the presentation layer decides
 * direction and motion policy (and tests it), the design system applies it.
 */
object PresentationBridge {

    /** Maps the persisted motion mode onto the design system's animation budget. */
    fun motionMode(mode: MotionModeName): NxMotion.MotionMode = when (mode) {
        MotionModeName.CINEMATIC -> NxMotion.MotionMode.CINEMATIC
        MotionModeName.BALANCED -> NxMotion.MotionMode.BALANCED
        MotionModeName.PERFORMANCE -> NxMotion.MotionMode.PERFORMANCE
    }

    /** Maps the presentation layer's direction onto Compose's. */
    fun layoutDirection(direction: TextLayoutDirection): LayoutDirection = when (direction) {
        TextLayoutDirection.RTL -> LayoutDirection.Rtl
        TextLayoutDirection.LTR -> LayoutDirection.Ltr
    }

    /**
     * Resolves a theme id to a theme spec.
     *
     * Unknown ids fall back to the default theme instead of failing: a profile
     * restored from another device may name a theme this build does not have, and
     * refusing to draw would be worse than drawing the default.
     */
    fun themeSpec(themeId: String): NxThemeSpec = NxThemeSpec.byId(themeId)
}

/**
 * Applies the whole presentation context: colors, typography, motion budget, text
 * direction and the string catalogue.
 *
 * Every screen is wrapped in this exactly once, at the app root. Screens receive
 * [Strings] as a parameter rather than reading a global, which is what makes the
 * Persian build testable without an Android resource system.
 */
@Composable
fun NeuroxAppTheme(
    strings: Strings,
    themeId: String,
    motionMode: MotionModeName,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION")
    strings
    NeuroxTheme(
        spec = PresentationBridge.themeSpec(themeId),
        motionMode = PresentationBridge.motionMode(motionMode),
        reducedMotion = reducedMotion,
        rtl = false,
    ) {
        // Direction is applied here so every descendant inherits it, including
        // Material components that know nothing about NEUROX.
        CompositionLocalProvider(
            LocalLayoutDirection provides PresentationBridge.layoutDirection(strings.layoutDirection),
        ) {
            content()
        }
    }
}
