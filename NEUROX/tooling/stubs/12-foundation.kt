// NEUROX offline compile stubs - foundation: lazy lists, scroll, click, canvas.
package androidx.compose.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp

fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = this

fun Modifier.clickable(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource?,
    indication: Any?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = this

fun Modifier.verticalScroll(state: ScrollState, enabled: Boolean = true): Modifier = this
fun Modifier.horizontalScroll(state: ScrollState, enabled: Boolean = true): Modifier = this

@Composable
fun rememberScrollState(initial: Int = 0): ScrollState = ScrollState(initial)

class ScrollState(initial: Int = 0) {
    var value: Int = initial
        private set

    fun scrollTo(value: Int) {
        this.value = value
    }

    fun animateScrollTo(value: Int) {
        this.value = value
    }
}

@Composable
fun Canvas(modifier: Modifier, onDraw: DrawScope.() -> Unit) {}
