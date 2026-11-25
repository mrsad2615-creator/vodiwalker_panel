// NEUROX offline compile stubs - layout modifiers and the layout containers.
package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

fun Modifier.padding(all: Dp): Modifier = this
fun Modifier.padding(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)): Modifier = this
fun Modifier.padding(start: Dp = Dp(0f), top: Dp = Dp(0f), end: Dp = Dp(0f), bottom: Dp = Dp(0f)): Modifier = this
fun Modifier.padding(paddingValues: PaddingValues): Modifier = this

fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier = this
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier = this
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier = this
fun Modifier.width(width: Dp): Modifier = this
fun Modifier.height(height: Dp): Modifier = this
fun Modifier.size(size: Dp): Modifier = this
fun Modifier.size(width: Dp, height: Dp): Modifier = this
fun Modifier.heightIn(min: Dp = Dp(0f), max: Dp = Dp(Float.POSITIVE_INFINITY)): Modifier = this
fun Modifier.widthIn(min: Dp = Dp(0f), max: Dp = Dp(Float.POSITIVE_INFINITY)): Modifier = this
fun Modifier.sizeIn(
    minWidth: Dp = Dp(0f), minHeight: Dp = Dp(0f),
    maxWidth: Dp = Dp(Float.POSITIVE_INFINITY), maxHeight: Dp = Dp(Float.POSITIVE_INFINITY),
): Modifier = this
fun Modifier.aspectRatio(ratio: Float, matchHeightConstraintsFirst: Boolean = false): Modifier = this
fun Modifier.wrapContentSize(): Modifier = this
fun Modifier.defaultMinSize(minWidth: Dp = Dp(0f), minHeight: Dp = Dp(0f)): Modifier = this

/**
 * RowScope/ColumnScope.
 *
 * weight() and align() are scope members in the real library, so declaring them on a
 * scope interface is what keeps `Modifier.weight(1f)` valid only inside Row/Column -
 * the same rule the real library enforces.
 */
interface RowScope {
    fun Modifier.weight(weight: Float = 1f, fill: Boolean = true): Modifier
    fun Modifier.align(alignment: Alignment): Modifier
}

interface ColumnScope {
    fun Modifier.weight(weight: Float = 1f, fill: Boolean = true): Modifier
    fun Modifier.align(alignment: Alignment): Modifier
}

class PaddingValues(val start: Dp = Dp(0f), val top: Dp = Dp(0f), val end: Dp = Dp(0f), val bottom: Dp = Dp(0f)) {
    fun calculateStartPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection): Dp = start
    fun calculateEndPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection): Dp = end
}

fun PaddingValues(horizontal: Dp? = null, vertical: Dp? = null): PaddingValues {
    val h = horizontal ?: Dp(0f)
    val v = vertical ?: Dp(0f)
    return PaddingValues(h, v, h, v)
}

fun PaddingValues(all: Dp): PaddingValues = PaddingValues(all, all, all, all)

class Arrangement private constructor() {
    companion object {
        val Start = Arrangement()
        val End = Arrangement()
        val Center = Arrangement()
        val Top = Arrangement()
        val Bottom = Arrangement()
        val SpaceBetween = Arrangement()
        val SpaceAround = Arrangement()
        val SpaceEvenly = Arrangement()

        fun spacedBy(space: Dp, alignment: Alignment = Alignment.Center): Arrangement = Arrangement()



        object Horizontal {
            val Start = Arrangement()
            val End = Arrangement()
            val Center = Arrangement()
            val SpaceBetween = Arrangement()
            val SpaceAround = Arrangement()
            val SpaceEvenly = Arrangement()
        }

        object Vertical {
            val Top = Arrangement()
            val Bottom = Arrangement()
            val Center = Arrangement()
            val SpaceBetween = Arrangement()
            val SpaceAround = Arrangement()
            val SpaceEvenly = Arrangement()
        }
    }
}

@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement = Arrangement.Top,
    horizontalAlignment: Alignment = Alignment.TopStart,
    content: @Composable ColumnScope.() -> Unit,
) {}

@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement = Arrangement.Start,
    verticalAlignment: Alignment = Alignment.TopCenter,
    content: @Composable RowScope.() -> Unit,
) {}

@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {}

@Composable
fun Box(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart) {}

interface BoxScope {
    fun Modifier.align(alignment: Alignment): Modifier
    fun Modifier.matchParentSize(): Modifier
}

@Composable
fun Spacer(modifier: Modifier) {}


