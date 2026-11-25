// NEUROX offline compile stubs - text style enums.
package androidx.compose.ui.text.style

class TextAlign private constructor() {
    companion object {
        val Left = TextAlign()
        val Right = TextAlign()
        val Center = TextAlign()
        val Start = TextAlign()
        val End = TextAlign()
        val Justify = TextAlign()
        val Unspecified = TextAlign()
    }
}

class TextDirection private constructor() {
    companion object {
        val Ltr = TextDirection()
        val Rtl = TextDirection()
        val Content = TextDirection()
        val ContentOrRtl = TextDirection()
        val Unspecified = TextDirection()
    }
}

enum class TextOverflow { Clip, Ellipsis, Visible, StartEllipsis, MiddleEllipsis }

class LineHeightStyle(
    val alignment: Alignment = Alignment.Center,
    val trim: Trim = Trim.None,
) {
    class Alignment private constructor() {
        companion object {
            val Top = Alignment()
            val Center = Alignment()
            val Bottom = Alignment()
            val Proportional = Alignment()
        }
    }

    class Trim private constructor() {
        companion object {
            val None = Trim()
            val FirstLineTop = Trim()
            val LastLineBottom = Trim()
            val Both = Trim()
        }
    }
}
