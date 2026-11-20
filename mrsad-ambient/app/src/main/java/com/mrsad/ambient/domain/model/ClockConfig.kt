package com.mrsad.ambient.domain.model

enum class ClockStyle(val titleFa: String, val titleEn: String) {
    HYBRID_ARCHITECTURAL("هیبریدی معماری", "Hybrid Architectural"),
    MINIMAL_DIGITAL("دیجیتال مینیمال", "Minimal Digital"),
    ANALOG_RING("حلقه آنالوگ", "Analog Ring")
}

enum class ClockPosition(val titleFa: String, val titleEn: String) {
    TOP("بالا", "Top"),
    CENTER("مرکز", "Center")
}

data class ClockConfig(
    val style: ClockStyle = ClockStyle.HYBRID_ARCHITECTURAL,
    val position: ClockPosition = ClockPosition.TOP,
    val use24Hour: Boolean = true,
    val usePersianNumerals: Boolean = true,
    val showSeconds: Boolean = false,
    val showAnalogTicks: Boolean = true,
    val scale: Float = 1.0f
)
