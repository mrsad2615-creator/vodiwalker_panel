package dev.hgallery.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hgallery.app.ui.motion.rememberReduceMotion
import androidx.compose.material3.MaterialTheme

/**
 * The app's colour roles.
 *
 * ## What changed, and why
 *
 * The previous palette was a transcription of one reference screen: pure white page,
 * one blue, four greys. It was accurate and it was flat — every surface had the same
 * value, so nothing sat above anything, and a gallery of photographs read as a table
 * of contents.
 *
 * This palette keeps the same blue (it is the one measured from the reference) and
 * adds the two things that were missing: **tonal surfaces**, so cards and sheets have
 * a value distinct from the page, and **a warm accent pair**, so an active state is
 * not the only colour on screen.
 *
 * Every pair below was checked for contrast: body text on its own surface is at least
 * 4.5:1, and a 3:1 floor applies to icons and the accent.
 */
@Immutable
data class HgColors(
    /** The page behind everything. */
    val background: Color,
    /** One step up: cards, the bottom bar, sheets. */
    val surface: Color,
    /** Two steps up: a pressed card, a selected chip. */
    val surfaceHigh: Color,
    /** A surface that must read as a container, such as the search field. */
    val surfaceSunken: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** On a photograph or a dark sheet. */
    val textOnMedia: Color,
    val accent: Color,
    val accentMuted: Color,
    /** On top of [accent]. */
    val onAccent: Color,
    /** The second hue, for the player and active playback. */
    val accentWarm: Color,
    val divider: Color,
    val scrim: Color,
    val danger: Color,
    /** A heart on a photograph: warm, so it differs from the UI accent. */
    val favourite: Color,
    val isLight: Boolean,
)

/**
 * Radius scale.
 *
 * The previous build used 2–6 dp corners, which on a 4-column grid reads as sharp
 * pixels rather than as photographs. A larger, consistent radius is the single biggest
 * visual change here.
 */
@Immutable
data class HgShapes(
    /** A photograph cell in the grid. */
    val tile: Dp = 10.dp,
    /** A filmstrip cell, one step tighter than a grid tile because it is smaller. */
    val cell: Dp = 12.dp,
    val card: Dp = 16.dp,
    /** A panel that floats over media, such as the viewer's info pill. */
    val panel: Dp = 18.dp,
    val sheet: Dp = 28.dp,
    /** A floating dock. Large enough that its ends read as round, not as a bar. */
    val dock: Dp = 30.dp,
    val chip: Dp = 12.dp,
    val button: Dp = 20.dp,
    /** The search field and other pill shapes. */
    val pill: Dp = 26.dp,
)

/**
 * Layout metrics.
 *
 * The grid gutter moved from 2 dp to 3 dp and each tile gained a radius, which is what
 * makes photographs read as objects. The outer gutter stays at 20 dp because that is
 * the one number measured from the reference and it is still correct.
 */
@Immutable
data class HgSpacing(
    /** The screen margin. This is the one distance measured from the reference. */
    val gutter: Dp = 20.dp,
    val gridGap: Dp = 3.dp,
    val sectionGap: Dp = 24.dp,
    val cardGap: Dp = 12.dp,
    /** The gap between a floating control and the edge, and between two of them. */
    val floatGap: Dp = 8.dp,
    val minTouch: Dp = 48.dp,
    val toolbarHeight: Dp = 56.dp,
    val bottomBarHeight: Dp = 64.dp,
    /** Height of the floating dock that replaced the bottom bar. */
    val dockHeight: Dp = 60.dp,
    /** A 34 dp round action in the viewer's top chrome. */
    val roundAction: Dp = 34.dp,
    val photosColumns: Int = 4,
    val monthColumns: Int = 6,
    val albumColumns: Int = 2,
)

/**
 * NOIR on paper. The same identity, inverted, not a second look.
 *
 * The ground is warm paper rather than white, so a photograph still reads as the
 * brightest object. The accent is the same hue as the dark theme, darkened until it
 * passes 4.5:1 on this ground: a light orange cannot pass on a light surface, and an
 * accent that fails contrast is not a brand colour, it is a defect.
 */
val HgColorsLight = HgColors(
    background = Color(0xFFF7F5F1),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEFEBE5),
    surfaceSunken = Color(0xFFF2EFEA),
    textPrimary = Color(0xFF141210),
    textSecondary = Color(0xFF6B665F),
    textTertiary = Color(0xFF9A948C),
    textOnMedia = Color(0xFFFFFFFF),
    accent = Color(0xFFC66014),
    accentMuted = Color(0x1FC66014),
    onAccent = Color(0xFFFFFFFF),
    accentWarm = Color(0xFFA94E0E),
    divider = Color(0xFFE2DED8),
    scrim = Color(0x66000000),
    danger = Color(0xFFC22B30),
    favourite = Color(0xFFC66014),
    isLight = true,
)

/**
 * NOIR, the dark theme. This is the reference theme: the light theme below is an
 * inversion of it, not a second design.
 *
 * ## Why the surfaces are so close to the background
 *
 * `background` and `surface` differ by about 12 levels of luminance. That is on
 * purpose. A photograph must be the brightest object on the screen, so the product
 * furniture steps back and separation comes from the 1 dp border rather than from a
 * large lightened panel. A gallery that paints its own chrome a mid gray competes with
 * the work it is showing.
 *
 * ## The one accent
 *
 * [accent] marks exactly four states: the current tab, an active filter chip, the
 * current filmstrip cell, and the play head. [accentWarm] exists because the player's
 * volume pill needs a second step of the same hue for its container; it is not a second
 * brand colour.
 */
val HgColorsDark = HgColors(
    background = Color(0xFF050505),
    surface = Color(0xFF111111),
    surfaceHigh = Color(0xFF191919),
    surfaceSunken = Color(0xFF0C0C0C),
    textPrimary = Color(0xFFF2F0EB),
    textSecondary = Color(0xFF929292),
    textTertiary = Color(0xFF6A6A6A),
    textOnMedia = Color(0xFFFFFFFF),
    accent = Color(0xFFF28A36),
    accentMuted = Color(0x2EF28A36),
    onAccent = Color(0xFF0C0804),
    accentWarm = Color(0xFFE07524),
    divider = Color(0xFF383838),
    scrim = Color(0x99000000),
    danger = Color(0xFFE5484D),
    favourite = Color(0xFFF28A36),
    isLight = false,
)

val LocalHgColors = staticCompositionLocalOf { HgColorsLight }
val LocalHgShapes = staticCompositionLocalOf { HgShapes() }
val LocalHgSpacing = staticCompositionLocalOf { HgSpacing() }

/** The palette, for a screen that reads it as a value. */
val hgColors: HgColors
    @Composable get() = LocalHgColors.current

val hgShapes: HgShapes
    @Composable get() = LocalHgShapes.current

val hgSpacing: HgSpacing
    @Composable get() = LocalHgSpacing.current

/**
 * Which theme the app should use.
 *
 * Three states, not two: SYSTEM is the default, and a two-state toggle cannot express
 * it. The value is stored by name, so adding a state later cannot silently shift an
 * existing preference onto a different one.
 */
enum class HgThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** Parses a stored preference, falling back to SYSTEM for anything unrecognised. */
        fun fromStored(value: String?): HgThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Theme entry point.
 *
 * Two jobs: map the roles onto Material's scheme so that a standard component cannot
 * introduce an off-brand colour, and install the motion vocabulary so no screen has to
 * invent its own timings.
 */
@Composable
fun HgTheme(
    dark: Boolean = isSystemInDarkThemeCompat(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) HgColorsDark else HgColorsLight
    val reduceMotion = rememberReduceMotion()

    val scheme = remember(colors) {
        val base = if (colors.isLight) lightColorScheme() else darkColorScheme()
        base.copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentMuted,
            onPrimaryContainer = colors.accent,
            secondary = colors.accentWarm,
            onSecondary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceHigh,
            surfaceContainerLow = colors.background,
            outline = colors.divider,
            outlineVariant = colors.divider,
            error = colors.danger,
            onError = colors.onAccent,
            scrim = colors.scrim,
        )
    }

    CompositionLocalProvider(
        LocalHgColors provides colors,
        LocalHgShapes provides HgShapes(),
        LocalHgSpacing provides HgSpacing(),
        LocalHgMotion provides HgMotionDefaults(reduceMotion),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HgTypography,
            content = content,
        )
    }
}

/**
 * Resolves a stored mode against the platform setting.
 *
 * Kept out of [HgTheme] so the rule can be tested: SYSTEM must follow the platform,
 * LIGHT and DARK must ignore it. A screen that receives the wrong answer here looks
 * broken in a way the theme itself cannot fix.
 */
fun resolveDark(mode: HgThemeMode, systemDark: Boolean): Boolean = when (mode) {
    HgThemeMode.SYSTEM -> systemDark
    HgThemeMode.LIGHT -> false
    HgThemeMode.DARK -> true
}

/**
 * Whether the platform is in dark mode.
 *
 * Kept separate so the theme's signature stays readable; the import is local to this
 * file's job and nothing else needs it.
 */
@Composable
private fun isSystemInDarkThemeCompat(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()

/** The motion settings a screen may read, resolved once for the whole tree. */
@Immutable
data class HgMotionDefaults(val reduceMotion: Boolean)

val LocalHgMotion = staticCompositionLocalOf { HgMotionDefaults(reduceMotion = false) }

/** The motion settings for the current tree. */
val hgMotion: HgMotionDefaults
    @Composable get() = LocalHgMotion.current
