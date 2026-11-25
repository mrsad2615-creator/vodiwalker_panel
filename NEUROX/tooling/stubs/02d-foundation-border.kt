// NEUROX offline compile stubs - androidx.compose.foundation modifiers.
package androidx.compose.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

fun Modifier.border(width: Dp, color: Color, shape: Shape? = null): Modifier = this
fun Modifier.border(width: Dp, brush: Brush, shape: Shape): Modifier = this
fun Modifier.background(color: Color, shape: Shape? = null): Modifier = this
fun Modifier.background(brush: Brush, shape: Shape? = null, alpha: Float = 1f): Modifier = this
