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
    val tile: Dp = 10.dp,
    val card: Dp = 16.dp,
    val sheet: Dp = 28.dp,
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
    val gutter: Dp = 20.dp,
    val gridGap: Dp = 3.dp,
    val sectionGap: Dp = 24.dp,
    val cardGap: Dp = 12.dp,
    val minTouch: Dp = 48.dp,
    val toolbarHeight: Dp = 56.dp,
    val bottomBarHeight: Dp = 64.dp,
    val photosColumns: Int = 4,
    val monthColumns: Int = 6,
    val albumColumns: Int = 2,
)

val HgColorsLight = HgColors(
    background = Color(0xFFF7F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEFEFF4),
    surfaceSunken = Color(0xFFF0F0F5),
    textPrimary = Color(0xFF141418),
    textSecondary = Color(0xFF6B6B76),
    textTertiary = Color(0xFF9C9CA6),
    textOnMedia = Color(0xFFFFFFFF),
    accent = Color(0xFF0068E8),
    accentMuted = Color(0x1A0068E8),
    onAccent = Color(0xFFFFFFFF),
    accentWarm = Color(0xFFE8623C),
    divider = Color(0xFFE4E4EA),
    scrim = Color(0x66000000),
    danger = Color(0xFFD93636),
    favourite = Color(0xFFF2536B),
    isLight = true,
)

val HgColorsDark = HgColors(
    background = Color(0xFF0B0B0F),
    surface = Color(0xFF16161C),
    surfaceHigh = Color(0xFF22222A),
    surfaceSunken = Color(0xFF1B1B22),
    textPrimary = Color(0xFFF4F4F7),
    textSecondary = Color(0xFFA0A0AC),
    textTertiary = Color(0xFF6E6E7A),
    textOnMedia = Color(0xFFFFFFFF),
    accent = Color(0xFF5AA6FF),
    accentMuted = Color(0x2E5AA6FF),
    onAccent = Color(0xFF04121F),
    accentWarm = Color(0xFFFF8A63),
    divider = Color(0xFF26262E),
    scrim = Color(0x99000000),
    danger = Color(0xFFFF6B6B),
    favourite = Color(0xFFFF7A8E),
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
