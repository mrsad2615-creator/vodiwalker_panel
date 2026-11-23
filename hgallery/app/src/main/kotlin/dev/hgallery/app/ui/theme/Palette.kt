package dev.hgallery.app.ui.theme

import androidx.compose.runtime.Composable

/**
 * The old palette name, kept as a view onto the new colour roles.
 *
 * The redesign replaced a flat, measured palette with tonal roles (see [HgColors]).
 * Sixteen files still read `hgPalette`, and rewriting all of them in the same commit
 * would mix a rename with a visual change, which is how a regression hides. This
 * adapter keeps them compiling while each screen is ported on purpose.
 *
 * It is not a fork of the palette: every value is derived from [hgColors], so a screen
 * that was not ported still picks up the new colours.
 */
val hgPalette: HgPaletteCompat
    @Composable get() = HgPaletteCompat(hgColors)

/**
 * The old field names, mapped onto the new roles.
 *
 * Each mapping is a judgement, not a mechanical rename:
 * `page` becomes `background`, `pageAlt` becomes `surface` (the previous palette used
 * it for the slightly raised areas), and `chipBackground` becomes `surfaceHigh`, which
 * is the role a chip actually plays now.
 */
@Suppress("LongParameterList")
class HgPaletteCompat(private val c: HgColors) {
    val page get() = c.background
    val pageAlt get() = c.surface
    val surface get() = c.surface
    val textPrimary get() = c.textPrimary
    val textSecondary get() = c.textSecondary
    val textTertiary get() = c.textTertiary
    val accent get() = c.accent
    val accentMuted get() = c.accentMuted
    val divider get() = c.divider
    val scrim get() = c.scrim
    val viewerBackground get() = androidx.compose.ui.graphics.Color.Black
    val chipBackground get() = c.surfaceHigh
    val danger get() = c.danger
    val isLight get() = c.isLight
}
