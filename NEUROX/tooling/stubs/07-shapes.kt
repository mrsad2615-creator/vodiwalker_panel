// NEUROX offline compile stubs - shapes.
package androidx.compose.foundation.shape

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

class RoundedCornerShape(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
) : Shape {
    constructor(size: Dp) : this(size, size, size, size)
    constructor(percent: Int) : this(percent.dpStub(), percent.dpStub(), percent.dpStub(), percent.dpStub())
    constructor(size: androidx.compose.ui.unit.TextUnit) : this(size.toDp(), size.toDp(), size.toDp(), size.toDp())
    constructor(
        topStartPercent: Int, topEndPercent: Int, bottomStartPercent: Int, bottomEndPercent: Int,
    ) : this(topStartPercent.dpStub(), topEndPercent.dpStub(), bottomEndPercent.dpStub(), bottomStartPercent.dpStub())
}

private fun Int.dpStub(): Dp = androidx.compose.ui.unit.Dp(toFloat())

private fun androidx.compose.ui.unit.TextUnit.toDp(): Dp =
    androidx.compose.ui.unit.Dp(raw / 4f)

object CircleShape : Shape

object RectangleShape : Shape
