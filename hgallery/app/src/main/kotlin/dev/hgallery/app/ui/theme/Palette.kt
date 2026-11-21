package dev.hgallery.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** Shortcut used by every screen: the measured palette. */
val hgPalette: HgPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalHgPalette.current
