package com.neurox.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens. Every dimension in the application comes from here.
 *
 * WHY: the specification forbids repeating the same hard-coded value on unrelated
 * screens. A token change must update every screen at once, and a layout change
 * must never change the accent color, the typography or the exercise logic.
 */
object NxSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Screen gutter: 20 dp on phones, 32 dp on wide layouts. */
    fun gutter(expanded: Boolean): Dp = if (expanded) xxl else 20.dp

    /** Maximum content width so text does not stretch on tablets. */
    val contentMaxWidth: Dp = 720.dp
}

object NxRadius {
    val xs: Dp = 6.dp
    val sm: Dp = 10.dp
    val md: Dp = 14.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val pill: Dp = 999.dp

    /** Cards use [md]; premium hero surfaces use [lg] or [xl]. */
    val card: Dp = md
    val sheet: Dp = xl
}

object NxElevation {
    val none: Dp = 0.dp
    val low: Dp = 1.dp
    val medium: Dp = 3.dp
    val high: Dp = 8.dp
}

object NxSize {
    /** Minimum touch target required by accessibility guidance. */
    val minTouchTarget: Dp = 48.dp
    val iconSm: Dp = 18.dp
    val iconMd: Dp = 24.dp
    val iconLg: Dp = 32.dp
    val avatarSm: Dp = 32.dp
    val avatarMd: Dp = 44.dp
    val avatarLg: Dp = 64.dp
    val bottomBar: Dp = 68.dp
    val topBar: Dp = 56.dp
    val hairline: Dp = 1.dp
    val progressBar: Dp = 6.dp
    val progressBarThick: Dp = 10.dp

    /** Exercise target sizes are bounded so a target is always reachable. */
    val exerciseTargetMin: Dp = 24.dp
    val exerciseTargetMax: Dp = 96.dp
}

object NxType {
    /**
     * Sizes in sp. The scale is intentionally short: five levels.
     *
     * Each size is published twice - as a raw number and as a TextUnit - because line
     * height is a multiple of the font size: converting back from a TextUnit would be
     * a lossy round trip for no benefit.
     */
    const val DISPLAY_SP = 34f
    const val HEADLINE_SP = 26f
    const val TITLE_SP = 20f
    const val BODY_SP = 16f
    const val LABEL_SP = 13f
    const val CAPTION_SP = 11f

    val display = DISPLAY_SP.sp
    val headline = HEADLINE_SP.sp
    val title = TITLE_SP.sp
    val body = BODY_SP.sp
    val label = LABEL_SP.sp
    val caption = CAPTION_SP.sp

    val lineHeightTight = 1.15
    val lineHeightNormal = 1.45
    val lineHeightReading = 1.6

    /** Persian text needs slightly more line height for correct shaping. */
    fun lineHeight(multiplier: Double, rtl: Boolean): Double =
        if (rtl) multiplier + 0.08 else multiplier
}

object NxMotion {
    /** Fast feedback for a tap: 90 ms. */
    const val TAP_MILLIS = 90
    const val FAST_MILLIS = 160
    const val NORMAL_MILLIS = 240
    const val SLOW_MILLIS = 420
    const val CINEMATIC_MILLIS = 1_100

    /** Standard easing: confident start, soft landing. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val gentle: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Motion intensity is a user setting; modes multiply the durations. */
    enum class MotionMode(val scale: Float) {
        CINEMATIC(1.0f),
        BALANCED(0.65f),
        PERFORMANCE(0.0f),
    }

    fun durationMillis(base: Int, mode: MotionMode): Int =
        if (mode == MotionMode.PERFORMANCE) 0 else (base * mode.scale).toInt()

    /** True when a reveal animation should play at all. */
    fun shouldAnimate(mode: MotionMode): Boolean = mode != MotionMode.PERFORMANCE
}

/**
 * Breakpoints for adaptive layout.
 *
 * [compact] is a phone in portrait, [medium] a large phone or small tablet,
 * [expanded] a tablet or a phone in landscape.
 */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

object NxBreakpoints {
    const val MEDIUM_MIN_DP = 600
    const val EXPANDED_MIN_DP = 840

    fun of(widthDp: Float): WidthClass = when {
        widthDp >= EXPANDED_MIN_DP -> WidthClass.EXPANDED
        widthDp >= MEDIUM_MIN_DP -> WidthClass.MEDIUM
        else -> WidthClass.COMPACT
    }

    /** Home shows four module cards in a column on compact, two columns above. */
    fun moduleColumns(widthClass: WidthClass): Int = when (widthClass) {
        WidthClass.COMPACT -> 1
        WidthClass.MEDIUM -> 2
        WidthClass.EXPANDED -> 2
    }

    fun analyticsColumns(widthClass: WidthClass): Int =
        if (widthClass == WidthClass.COMPACT) 1 else 2
}

/** Accessibility thresholds used by review and by tests. */
object NxAccessibility {
    /** WCAG AA contrast for normal text. */
    const val MIN_CONTRAST_NORMAL = 4.5

    /** WCAG AA contrast for large text (18 pt or 14 pt bold). */
    const val MIN_CONTRAST_LARGE = 3.0

    /** Minimum touch target in dp. */
    const val MIN_TOUCH_TARGET_DP = 48f

    /** Minimum font scale the layouts must survive. */
    const val MAX_FONT_SCALE = 2.0f
}
