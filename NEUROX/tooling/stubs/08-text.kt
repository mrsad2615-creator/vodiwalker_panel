// NEUROX offline compile stubs - text: TextStyle, fonts, alignment, overflow.
package androidx.compose.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

class TextStyle(
    val color: Color = Color.Unspecified,
    val fontSize: TextUnit = TextUnit(14f),
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val lineHeight: TextUnit = TextUnit(Float.NaN),
    val letterSpacing: TextUnit = TextUnit(Float.NaN),
    val fontFamily: FontFamily? = null,
    val textAlign: TextAlign? = null,
    val textDirection: TextDirection? = null,
    val textDecoration: TextDecoration? = null,
    val fontFeatureSettings: String? = null,
    val lineHeightStyle: LineHeightStyle? = null,
    val platformStyle: PlatformTextStyle? = null,
) {
    fun copy(
        color: Color = this.color,
        fontSize: TextUnit = this.fontSize,
        fontWeight: FontWeight? = this.fontWeight,
        fontStyle: FontStyle? = this.fontStyle,
        lineHeight: TextUnit = this.lineHeight,
        letterSpacing: TextUnit = this.letterSpacing,
        fontFamily: FontFamily? = this.fontFamily,
        textAlign: TextAlign? = this.textAlign,
        textDirection: TextDirection? = this.textDirection,
        textDecoration: TextDecoration? = this.textDecoration,
        fontFeatureSettings: String? = this.fontFeatureSettings,
        lineHeightStyle: LineHeightStyle? = this.lineHeightStyle,
    ): TextStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        fontFamily = fontFamily,
        textAlign = textAlign,
        textDirection = textDirection,
        textDecoration = textDecoration,
        fontFeatureSettings = fontFeatureSettings,
        lineHeightStyle = lineHeightStyle,
    )

    fun merge(other: TextStyle): TextStyle = TextStyle(
        color = other.color,
        fontSize = other.fontSize,
        fontWeight = other.fontWeight ?: fontWeight,
        lineHeight = other.lineHeight,
        fontFamily = other.fontFamily ?: fontFamily,
        textAlign = other.textAlign ?: textAlign,
        textDirection = other.textDirection ?: textDirection,
        fontFeatureSettings = other.fontFeatureSettings ?: fontFeatureSettings,
    )
}

class PlatformTextStyle(val includeFontPadding: Boolean = true)

class FontStyle private constructor() {
    companion object {
        val Normal = FontStyle()
        val Italic = FontStyle()
    }
}

class TextDecoration private constructor() {
    companion object {
        val None = TextDecoration()
        val Underline = TextDecoration()
        val LineThrough = TextDecoration()
    }
}
