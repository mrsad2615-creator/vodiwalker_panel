package com.mrsadhr.sounds

import com.mrsadhr.core.model.SoundCategory

/**
 * Completion sounds. Every asset shipped with the app was synthesised specifically for MrsadHR
 * (tools/gen/sounds.py) and is therefore original, royalty free material.
 */
data class SoundDef(
    val id: String,
    val category: SoundCategory,
    val nameEn: String,
    val nameFa: String,
    val asset: String,
    val gain: Float,
    val durationMs: Int,
    val blurbEn: String,
    val blurbFa: String,
)

object SoundCatalog {

    val all: List<SoundDef> = listOf(
        SoundDef("minimal-tick", SoundCategory.Minimal, "Soft Tick", "تیک نرم", "sounds/minimal_tick.wav", 0.9f, 1200,
            "A dry, precise click", "کلیکِ خشک و دقیق"),
        SoundDef("minimal-bell", SoundCategory.Minimal, "Glass Bell", "زنگ شیشه‌ای", "sounds/minimal_bell.wav", 0.8f, 2600,
            "Single struck glass tone", "یک ضربه‌ی شیشه"),
        SoundDef("minimal-pulse", SoundCategory.Minimal, "Quiet Pulse", "پالس آرام", "sounds/minimal_pulse.wav", 0.85f, 1800,
            "Two muted sine pulses", "دو پالسِ خفه"),
        SoundDef("digital-beep", SoundCategory.Digital, "Triple Beep", "بوق سه‌گانه", "sounds/digital_beep.wav", 0.75f, 1500,
            "Classic digital alert", "هشدار دیجیتال کلاسیک"),
        SoundDef("digital-chime", SoundCategory.Digital, "Neon Chime", "کایم نئونی", "sounds/digital_chime.wav", 0.8f, 2400,
            "Ascending neon arpeggio", "آرپژی صعودی نئون"),
        SoundDef("digital-arcade", SoundCategory.Digital, "Arcade Win", "برد آرکید", "sounds/digital_arcade.wav", 0.7f, 1800,
            "Retro square-wave flourish", "شادی موج مربعی رترو"),
        SoundDef("cinematic-impact", SoundCategory.Cinematic, "Deep Impact", "ضربه‌ی عمیق", "sounds/cinematic_impact.wav", 0.9f, 3200,
            "Sub-bass hit with tail", "ضربه‌ی بیس با دنباله"),
        SoundDef("cinematic-riser", SoundCategory.Cinematic, "Riser", "اوج‌گیر", "sounds/cinematic_riser.wav", 0.7f, 2600,
            "Tension riser into a hit", "اوج‌گیری تا ضربه"),
        SoundDef("cinematic-bloom", SoundCategory.Cinematic, "Orchestral Bloom", "شکوفه‌ی ارکستری", "sounds/cinematic_bloom.wav", 0.75f, 3600,
            "Warm string swell", "اوج زهی گرم"),
        SoundDef("ambient-ocean", SoundCategory.Ambient, "Ocean Air", "هوای اقیانوس", "sounds/ambient_ocean.wav", 0.65f, 4000,
            "Filtered surf and air", "موج و هوا"),
        SoundDef("ambient-rain", SoundCategory.Ambient, "Rain Window", "باران پنجره", "sounds/ambient_rain.wav", 0.6f, 4000,
            "Soft rain on glass", "باران نرم روی شیشه"),
        SoundDef("ambient-pad", SoundCategory.Ambient, "Hush Pad", "پد ساکت", "sounds/ambient_pad.wav", 0.7f, 4200,
            "Slow breathing chord", "آکورد آرام"),
    )

    fun byId(id: String): SoundDef = all.firstOrNull { it.id == id } ?: all[4]

    fun byCategory(category: SoundCategory): List<SoundDef> = all.filter { it.category == category }

    val categories: List<SoundCategory> = SoundCategory.entries

    fun categoryLabel(category: SoundCategory, persian: Boolean): String = when (category) {
        SoundCategory.Minimal -> if (persian) "مینیمال" else "Minimal"
        SoundCategory.Digital -> if (persian) "دیجیتال" else "Digital"
        SoundCategory.Cinematic -> if (persian) "سینمایی" else "Cinematic"
        SoundCategory.Ambient -> if (persian) "محیطی" else "Ambient"
    }
}
