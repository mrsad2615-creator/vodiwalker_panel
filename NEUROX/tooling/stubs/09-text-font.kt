// NEUROX offline compile stubs - font types.
package androidx.compose.ui.text.font

import androidx.compose.ui.text.FontStyle

/**
 * FontWeight is an inline value class with named constants in the real library.
 * The app uses the named weights and comparisons, so those are real here.
 */
class FontWeight(val weight: Int) : Comparable<FontWeight> {
    override fun compareTo(other: FontWeight) = weight.compareTo(other.weight)

    companion object {
        val Thin = FontWeight(100)
        val ExtraLight = FontWeight(200)
        val Light = FontWeight(300)
        val Normal = FontWeight(400)
        val Medium = FontWeight(500)
        val SemiBold = FontWeight(600)
        val Bold = FontWeight(700)
        val ExtraBold = FontWeight(800)
        val Black = FontWeight(900)
    }
}

/** A font resource reference; the app only builds one family from bundled files. */
class Font(val resId: Int, val weight: FontWeight = FontWeight.Normal, val style: FontStyle = FontStyle.Normal)

/**
 * FontFamily.
 *
 * The real library exposes a sealed class plus a top-level FontFamily(...) factory
 * function. Here the class is open with a vararg-fonts constructor, which accepts the
 * same `FontFamily(Font(...), Font(...))` call the app makes. Font resources are
 * Android res ids, so R.font.* must still resolve in the module being compiled.
 */
open class FontFamily(vararg val fonts: Font) {
    companion object {
        val Default: FontFamily = FontFamily()
        val SansSerif: FontFamily = FontFamily()
        val Serif: FontFamily = FontFamily()
        val Monospace: FontFamily = FontFamily()
    }
}
