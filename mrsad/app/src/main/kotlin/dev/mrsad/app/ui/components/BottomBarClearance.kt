package dev.mrsad.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The height the floating navigation bar occupies at the bottom of the
 * window.
 *
 * The bar is drawn *over* the content, so every screen behind it has to
 * reserve this much space at the bottom of its scroll viewport. Otherwise the
 * last row of a screen can come to rest underneath the bar, and a tap on it
 * hits the navigation instead of the control the user aimed at.
 *
 * The value is measured instead of hard-coded because the bar's height also
 * depends on the system navigation inset, which differs between devices and
 * between gesture and three-button navigation.
 */
val LocalBottomBarClearance = compositionLocalOf<Dp> { 0.dp }
