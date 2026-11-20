package com.mrsadhr.core.render

/**
 * Small, dependency free colour toolkit.
 *
 * The whole UI core is written against plain ARGB integers so that identical drawing code can run
 * on the Android hardware canvas and on the desktop JVM preview harness (see tools/harness).
 */
object Argb {

    fun of(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun rgb(r: Int, g: Int, b: Int): Int = of(255, r, g, b)

    /** 0xRRGGBB or 0xAARRGGBB literal. */
    fun hex(value: Long, alpha: Int = 255): Int {
        val a = when {
            value > 0xFFFFFFL -> ((value shr 24) and 0xFF).toInt()
            else -> alpha
        }
        return of(a, ((value shr 16) and 0xFF).toInt(), ((value shr 8) and 0xFF).toInt(), (value and 0xFF).toInt())
    }

    fun a(c: Int): Int = (c ushr 24) and 0xFF
    fun r(c: Int): Int = (c shr 16) and 0xFF
    fun g(c: Int): Int = (c shr 8) and 0xFF
    fun b(c: Int): Int = c and 0xFF

    /** Multiplies the existing alpha channel by [factor] (0..1). */
    fun scaleAlpha(c: Int, factor: Float): Int {
        val a = (a(c) * factor.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return of(a, r(c), g(c), b(c))
    }

    fun withAlpha(c: Int, alpha: Float): Int = of(alpha.coerceIn(0f, 1f).times(255).toInt(), r(c), g(c), b(c))

    fun withAlphaInt(c: Int, alpha: Int): Int = of(alpha.coerceIn(0, 255), r(c), g(c), b(c))

    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return of(
            lerpInt(Argb.a(a), Argb.a(b), f),
            lerpInt(Argb.r(a), Argb.r(b), f),
            lerpInt(Argb.g(a), Argb.g(b), f),
            lerpInt(Argb.b(a), Argb.b(b), f),
        )
    }

    fun lighten(c: Int, t: Float): Int = mix(c, of(a(c), 255, 255, 255), t)

    fun darken(c: Int, t: Float): Int = mix(c, of(a(c), 0, 0, 0), t)

    fun luminance(c: Int): Float {
        fun channel(v: Int): Float {
            val s = v / 255f
            return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * channel(r(c)) + 0.7152f * channel(g(c)) + 0.0722f * channel(b(c))
    }

    private fun lerpInt(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
