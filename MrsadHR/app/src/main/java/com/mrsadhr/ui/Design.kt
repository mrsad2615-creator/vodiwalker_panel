package com.mrsadhr.ui

import com.mrsadhr.core.render.Paint
import com.mrsadhr.core.render.TextAlign
import com.mrsadhr.core.render.Typeface
import com.mrsadhr.themes.Palette
import com.mrsadhr.themes.RingStyle
import kotlin.math.roundToInt

enum class Weight { Regular, Medium, Bold }

/** Font loading is platform specific (assets on device, files in the preview harness). */
interface FontLoader {
    fun load(fileName: String): Typeface
}

class Typefaces(loader: FontLoader) {
    val latinRegular = loader.load("SpaceGrotesk-400.ttf")
    val latinMedium = loader.load("SpaceGrotesk-500.ttf")
    val latinBold = loader.load("SpaceGrotesk-700.ttf")
    val vazirRegular = loader.load("Vazirmatn-400.ttf")
    val vazirMedium = loader.load("Vazirmatn-500.ttf")
    val vazirBold = loader.load("Vazirmatn-700.ttf")

    fun latin(weight: Weight): Typeface = when (weight) {
        Weight.Regular -> latinRegular
        Weight.Medium -> latinMedium
        Weight.Bold -> latinBold
    }

    fun persian(weight: Weight): Typeface = when (weight) {
        Weight.Regular -> vazirRegular
        Weight.Medium -> vazirMedium
        Weight.Bold -> vazirBold
    }
}

/**
 * Resolved design system for the current frame: palette + typography scale + spacing rhythm.
 * Screens never hard-code a colour or a font; they ask the design for a paint.
 */
class Design(
    val typefaces: Typefaces,
    val palette: Palette,
    val ringStyle: RingStyle,
    val density: Float,
    val isPersian: Boolean,
    val reducedMotion: Boolean,
    /** System font scale, clamped so the dense instrument layout stays usable at 200%. */
    val fontScale: Float = 1f,
) {
    fun dp(value: Float): Float = value * density

    /** Text sizes follow the user's accessibility font scale, clamped to a safe range. */
    private fun textSize(sizeDp: Float): Float = dp(sizeDp) * fontScale.coerceIn(0.85f, 1.3f)
    fun sp(value: Float): Int = (value * density).roundToInt()

    // ---- Spacing rhythm (4dp grid) ----
    val gapXs = dp(4f)
    val gapSm = dp(8f)
    val gapMd = dp(12f)
    val gapLg = dp(16f)
    val gapXl = dp(24f)
    val gapXxl = dp(32f)

    fun typeface(weight: Weight): Typeface =
        if (isPersian) typefaces.persian(weight) else typefaces.latin(weight)

    /** Large timer digits: tabular, optically tight. */
    fun timePaint(sizeDp: Float, color: Int, weight: Weight = Weight.Medium): Paint = Paint().apply {
        typeface = typeface(weight)
        textSize = textSize(sizeDp)
        this.color = color
        textAlign = TextAlign.LEFT
        letterSpacingEm = -0.012f
    }

    fun displayPaint(sizeDp: Float, color: Int, weight: Weight = Weight.Bold, align: TextAlign = TextAlign.LEFT): Paint = Paint().apply {
        typeface = typeface(weight)
        textSize = textSize(sizeDp)
        this.color = color
        textAlign = align
        letterSpacingEm = -0.02f
    }

    fun titlePaint(sizeDp: Float, color: Int, align: TextAlign = TextAlign.LEFT): Paint = Paint().apply {
        typeface = typeface(Weight.Medium)
        textSize = textSize(sizeDp)
        this.color = color
        textAlign = align
        letterSpacingEm = -0.01f
    }

    fun bodyPaint(sizeDp: Float, color: Int, align: TextAlign = TextAlign.LEFT): Paint = Paint().apply {
        typeface = typeface(Weight.Regular)
        textSize = textSize(sizeDp)
        this.color = color
        textAlign = align
        letterSpacingEm = 0.004f
    }

    /**
     * Uppercase micro label with wide tracking. Language aware: Persian labels use Vazirmatn,
     * unless the caller explicitly asks for the Latin face (brand marks, unit suffixes).
     */
    fun labelPaint(
        sizeDp: Float,
        color: Int,
        trackingEm: Float = 0.16f,
        align: TextAlign = TextAlign.LEFT,
        latinOnly: Boolean = false,
    ): Paint = Paint().apply {
        typeface = if (latinOnly) typefaces.latin(Weight.Medium) else typeface(Weight.Medium)
        textSize = textSize(sizeDp)
        this.color = color
        textAlign = align
        letterSpacingEm = if (isPersian && !latinOnly) trackingEm * 0.35f else trackingEm
    }

    fun wordmarkPaint(sizeDp: Float, color: Int, align: TextAlign = TextAlign.LEFT): Paint = Paint().apply {
        // The brand mark stays in the Latin face in every language.
        typeface = typefaces.latin(Weight.Bold)
        textSize = textSize(sizeDp)
        this.color = color
        textAlign = align
        letterSpacingEm = 0.30f
    }

    fun withPalette(palette: Palette, ringStyle: RingStyle = this.ringStyle): Design =
        Design(typefaces, palette, ringStyle, density, isPersian, reducedMotion, fontScale)
}
