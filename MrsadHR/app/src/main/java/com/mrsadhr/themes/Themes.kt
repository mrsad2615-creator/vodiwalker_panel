package com.mrsadhr.themes

import com.mrsadhr.core.render.Argb
import com.mrsadhr.core.render.Lerp

/**
 * A palette is the complete colour vocabulary of one theme.
 *
 * Every widget in MrsadHR draws exclusively from this object, which means a theme change is a
 * single lerp of two palettes instead of a chain of per-screen conditionals. The result is a
 * genuinely continuous "material morph" during the 560 ms theme transition.
 */
data class Palette(
    val bgTop: Int,
    val bgMid: Int,
    val bgBottom: Int,
    val ambientA: Int,
    val ambientB: Int,
    val ambientC: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val accent: Int,
    val accentSoft: Int,
    val accentEdge: Int,
    val onAccent: Int,
    val ringTrack: Int,
    val ringTrackEdge: Int,
    val progressStart: Int,
    val progressMid: Int,
    val progressEnd: Int,
    val ringGlow: Int,
    val ringDiscTop: Int,
    val ringDiscBottom: Int,
    val ringBevelLight: Int,
    val ringBevelDark: Int,
    val glassTop: Int,
    val glassBottom: Int,
    val glassBorder: Int,
    val glassSpecular: Int,
    val chipFill: Int,
    val chipBorder: Int,
    val chipText: Int,
    val chipSelectedFill: Int,
    val chipSelectedText: Int,
    val shadow: Int,
    val tick: Int,
    val scrim: Int,
    val danger: Int,
    val lightness: Float,
) {
    companion object {
        fun lerp(a: Palette, b: Palette, t: Float): Palette {
            if (t <= 0f) return a
            if (t >= 1f) return b
            fun c(x: Int, y: Int) = Argb.mix(x, y, t)
            return Palette(
                bgTop = c(a.bgTop, b.bgTop),
                bgMid = c(a.bgMid, b.bgMid),
                bgBottom = c(a.bgBottom, b.bgBottom),
                ambientA = c(a.ambientA, b.ambientA),
                ambientB = c(a.ambientB, b.ambientB),
                ambientC = c(a.ambientC, b.ambientC),
                textPrimary = c(a.textPrimary, b.textPrimary),
                textSecondary = c(a.textSecondary, b.textSecondary),
                textTertiary = c(a.textTertiary, b.textTertiary),
                accent = c(a.accent, b.accent),
                accentSoft = c(a.accentSoft, b.accentSoft),
                accentEdge = c(a.accentEdge, b.accentEdge),
                onAccent = c(a.onAccent, b.onAccent),
                ringTrack = c(a.ringTrack, b.ringTrack),
                ringTrackEdge = c(a.ringTrackEdge, b.ringTrackEdge),
                progressStart = c(a.progressStart, b.progressStart),
                progressMid = c(a.progressMid, b.progressMid),
                progressEnd = c(a.progressEnd, b.progressEnd),
                ringGlow = c(a.ringGlow, b.ringGlow),
                ringDiscTop = c(a.ringDiscTop, b.ringDiscTop),
                ringDiscBottom = c(a.ringDiscBottom, b.ringDiscBottom),
                ringBevelLight = c(a.ringBevelLight, b.ringBevelLight),
                ringBevelDark = c(a.ringBevelDark, b.ringBevelDark),
                glassTop = c(a.glassTop, b.glassTop),
                glassBottom = c(a.glassBottom, b.glassBottom),
                glassBorder = c(a.glassBorder, b.glassBorder),
                glassSpecular = c(a.glassSpecular, b.glassSpecular),
                chipFill = c(a.chipFill, b.chipFill),
                chipBorder = c(a.chipBorder, b.chipBorder),
                chipText = c(a.chipText, b.chipText),
                chipSelectedFill = c(a.chipSelectedFill, b.chipSelectedFill),
                chipSelectedText = c(a.chipSelectedText, b.chipSelectedText),
                shadow = c(a.shadow, b.shadow),
                tick = c(a.tick, b.tick),
                scrim = c(a.scrim, b.scrim),
                danger = c(a.danger, b.danger),
                lightness = Lerp.f(a.lightness, b.lightness, t),
            )
        }
    }
}

/**
 * Material personality of the timer ring. Chrome, frosted silver and neon glass are produced by
 * the very same renderer with different physical parameters.
 */
data class RingStyle(
    val glow: Float,
    val specular: Float,
    val bevel: Float,
    val trackDepth: Float,
    val sweepSpeed: Float,
    val neon: Float,
    val thicknessDp: Float,
) {
    companion object {
        fun lerp(a: RingStyle, b: RingStyle, t: Float) = RingStyle(
            glow = Lerp.f(a.glow, b.glow, t),
            specular = Lerp.f(a.specular, b.specular, t),
            bevel = Lerp.f(a.bevel, b.bevel, t),
            trackDepth = Lerp.f(a.trackDepth, b.trackDepth, t),
            sweepSpeed = Lerp.f(a.sweepSpeed, b.sweepSpeed, t),
            neon = Lerp.f(a.neon, b.neon, t),
            thicknessDp = Lerp.f(a.thicknessDp, b.thicknessDp, t),
        )
    }
}

object Palettes {

    val Obsidian = Palette(
        bgTop = Argb.hex(0x06070A),
        bgMid = Argb.hex(0x0A0C11),
        bgBottom = Argb.hex(0x04050A),
        ambientA = Argb.hex(0x9FB4D8),
        ambientB = Argb.hex(0x2B3442),
        ambientC = Argb.hex(0x5C6470),
        textPrimary = Argb.hex(0xF4F6FA),
        textSecondary = Argb.hex(0xA6AEBC),
        textTertiary = Argb.hex(0x6B7482),
        accent = Argb.hex(0xE7ECF3),
        accentSoft = Argb.hex(0x9BA5B4),
        accentEdge = Argb.hex(0xFFFFFF),
        onAccent = Argb.hex(0x0A0B0E),
        ringTrack = Argb.hex(0x15181E),
        ringTrackEdge = Argb.hex(0x2C313B),
        progressStart = Argb.hex(0xFFFFFF),
        progressMid = Argb.hex(0xC6D0DE),
        progressEnd = Argb.hex(0x7B8593),
        ringGlow = Argb.hex(0xB9C6DA),
        ringDiscTop = Argb.hex(0x12151B),
        ringDiscBottom = Argb.hex(0x06070A),
        ringBevelLight = Argb.hex(0xFFFFFF),
        ringBevelDark = Argb.hex(0x000000),
        glassTop = Argb.of(18, 255, 255, 255),
        glassBottom = Argb.of(4, 255, 255, 255),
        glassBorder = Argb.of(30, 255, 255, 255),
        glassSpecular = Argb.of(58, 255, 255, 255),
        chipFill = Argb.of(12, 255, 255, 255),
        chipBorder = Argb.of(22, 255, 255, 255),
        chipText = Argb.hex(0xC7CFDB),
        chipSelectedFill = Argb.hex(0xE9EEF5),
        chipSelectedText = Argb.hex(0x0A0B0E),
        shadow = Argb.of(190, 0, 0, 0),
        tick = Argb.of(44, 255, 255, 255),
        scrim = Argb.of(190, 4, 5, 8),
        danger = Argb.hex(0xFF7B72),
        lightness = 0f,
    )

    val Pure = Palette(
        bgTop = Argb.hex(0xFCFDFE),
        bgMid = Argb.hex(0xF2F4F7),
        bgBottom = Argb.hex(0xE3E7EE),
        ambientA = Argb.hex(0xFFFFFF),
        ambientB = Argb.hex(0xC3CCD9),
        ambientC = Argb.hex(0xE8ECF2),
        textPrimary = Argb.hex(0x0F1216),
        textSecondary = Argb.hex(0x59616D),
        textTertiary = Argb.hex(0x8B94A1),
        accent = Argb.hex(0x1B1F25),
        accentSoft = Argb.hex(0x49515D),
        accentEdge = Argb.hex(0x000000),
        onAccent = Argb.hex(0xF7F9FC),
        ringTrack = Argb.hex(0xD8DDE5),
        ringTrackEdge = Argb.hex(0xBEC6D1),
        progressStart = Argb.hex(0x242932),
        progressMid = Argb.hex(0x434B57),
        progressEnd = Argb.hex(0x707A88),
        ringGlow = Argb.hex(0x9AA6B6),
        ringDiscTop = Argb.hex(0xFFFFFF),
        ringDiscBottom = Argb.hex(0xE6EAF0),
        ringBevelLight = Argb.hex(0xFFFFFF),
        ringBevelDark = Argb.hex(0x2A3038),
        glassTop = Argb.of(205, 255, 255, 255),
        glassBottom = Argb.of(120, 255, 255, 255),
        glassBorder = Argb.of(235, 255, 255, 255),
        glassSpecular = Argb.of(255, 255, 255, 255),
        chipFill = Argb.of(160, 255, 255, 255),
        chipBorder = Argb.of(24, 16, 19, 24),
        chipText = Argb.hex(0x2B3038),
        chipSelectedFill = Argb.hex(0x1B1F25),
        chipSelectedText = Argb.hex(0xFFFFFF),
        shadow = Argb.of(38, 82, 96, 118),
        tick = Argb.of(60, 20, 26, 34),
        scrim = Argb.of(210, 244, 246, 250),
        danger = Argb.hex(0xD93B33),
        lightness = 1f,
    )

    val Aurora = Palette(
        bgTop = Argb.hex(0x04070E),
        bgMid = Argb.hex(0x071120),
        bgBottom = Argb.hex(0x03050A),
        ambientA = Argb.hex(0x19E6FF),
        ambientB = Argb.hex(0x8B5CFF),
        ambientC = Argb.hex(0x18FFC7),
        textPrimary = Argb.hex(0xEAF7FF),
        textSecondary = Argb.hex(0x9DB6CF),
        textTertiary = Argb.hex(0x5E7590),
        accent = Argb.hex(0x3BEFFF),
        accentSoft = Argb.hex(0x8B5CFF),
        accentEdge = Argb.hex(0xA9FBFF),
        onAccent = Argb.hex(0x04121A),
        ringTrack = Argb.hex(0x0F1F2C),
        ringTrackEdge = Argb.hex(0x1D3A4E),
        progressStart = Argb.hex(0x8CFAFF),
        progressMid = Argb.hex(0x33D6FF),
        progressEnd = Argb.hex(0x8B5CFF),
        ringGlow = Argb.hex(0x3BE8FF),
        ringDiscTop = Argb.hex(0x0A1A29),
        ringDiscBottom = Argb.hex(0x03080F),
        ringBevelLight = Argb.hex(0xCFFBFF),
        ringBevelDark = Argb.hex(0x000308),
        glassTop = Argb.of(18, 130, 205, 255),
        glassBottom = Argb.of(8, 105, 70, 255),
        glassBorder = Argb.of(42, 120, 230, 255),
        glassSpecular = Argb.of(92, 190, 255, 255),
        chipFill = Argb.of(16, 80, 180, 255),
        chipBorder = Argb.of(46, 90, 220, 255),
        chipText = Argb.hex(0xC8E8FF),
        chipSelectedFill = Argb.hex(0x2BE6FF),
        chipSelectedText = Argb.hex(0x041018),
        shadow = Argb.of(200, 0, 4, 10),
        tick = Argb.of(58, 130, 220, 255),
        scrim = Argb.of(195, 3, 6, 12),
        danger = Argb.hex(0xFF6B8A),
        lightness = 0f,
    )
}

enum class ThemeId(val key: String, val labelEn: String, val labelFa: String) {
    Obsidian("obsidian", "Obsidian", "اُبسیدیَن"),
    Pure("pure", "Pure", "پیور"),
    Aurora("aurora", "Aurora", "آرورا"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeId = entries.firstOrNull { it.key == key } ?: Obsidian
    }
}

data class ThemeSpec(
    val id: ThemeId,
    val palette: Palette,
    val ringStyle: RingStyle,
    val taglineEn: String,
    val taglineFa: String,
)

object Themes {
    val Obsidian = ThemeSpec(
        id = ThemeId.Obsidian,
        palette = Palettes.Obsidian,
        ringStyle = RingStyle(glow = 0.34f, specular = 0.92f, bevel = 0.85f, trackDepth = 0.8f, sweepSpeed = 0.30f, neon = 0f, thicknessDp = 15f),
        taglineEn = "Black chrome · liquid glass",
        taglineFa = "کروم سیاه · شیشه مایع",
    )

    val Pure = ThemeSpec(
        id = ThemeId.Pure,
        palette = Palettes.Pure,
        ringStyle = RingStyle(glow = 0.10f, specular = 1f, bevel = 0.95f, trackDepth = 0.45f, sweepSpeed = 0.45f, neon = 0f, thicknessDp = 15f),
        taglineEn = "Frosted silver · daylight",
        taglineFa = "نقره‌ی مات · روشنایی روز",
    )

    val Aurora = ThemeSpec(
        id = ThemeId.Aurora,
        palette = Palettes.Aurora,
        ringStyle = RingStyle(glow = 0.95f, specular = 0.38f, bevel = 0.45f, trackDepth = 0.6f, sweepSpeed = 0.85f, neon = 1f, thicknessDp = 14f),
        taglineEn = "Plasma cyan · violet haze",
        taglineFa = "فیروزه‌ی پلاسما · غبار بنفش",
    )

    val all: List<ThemeSpec> = listOf(Obsidian, Pure, Aurora)

    fun spec(id: ThemeId): ThemeSpec = when (id) {
        ThemeId.Obsidian -> Obsidian
        ThemeId.Pure -> Pure
        ThemeId.Aurora -> Aurora
    }
}
