// NEUROX offline compile stubs - interaction -> state.
package androidx.compose.foundation.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf

val LocalInteractionSource: Any = staticCompositionLocalOf { 0 }

@Composable
fun InteractionSource.collectIsPressedAsState(): State<Boolean> = error("stub")

@Composable
fun InteractionSource.collectIsFocusedAsState(): State<Boolean> = error("stub")

@Composable
fun InteractionSource.collectIsHoveredAsState(): State<Boolean> = error("stub")
