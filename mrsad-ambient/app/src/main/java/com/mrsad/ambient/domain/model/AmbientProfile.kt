package com.mrsad.ambient.domain.model

enum class ProfileType(val titleFa: String, val titleEn: String) {
    DAY("روز", "Day"),
    NIGHT("شب", "Night"),
    SLEEP("خواب", "Sleep"),
    STUDY("مطالعه", "Study"),
    BATTERY_SAVER("صرفه‌جویی باتری", "Battery Saver")
}

enum class InformationDensity(val titleFa: String, val titleEn: String) {
    ESSENTIAL("ضروری", "Essential"),
    BALANCED("متعادل", "Balanced"),
    DETAILED("کامل", "Detailed")
}

data class AmbientProfile(
    val type: ProfileType,
    val density: InformationDensity,
    val sessionTimeoutSeconds: Int,
    val reducedMotion: Boolean,
    val brightnessFraction: Float,
    val hideNotificationContent: Boolean,
    val autoStartHour: Int? = null,
    val autoEndHour: Int? = null,
    val lowBatteryThreshold: Int = 20
) {
    companion object {
        fun defaults(): List<AmbientProfile> = listOf(
            AmbientProfile(ProfileType.DAY, InformationDensity.BALANCED, 12, false, 0.55f, false, 7, 21),
            AmbientProfile(ProfileType.NIGHT, InformationDensity.ESSENTIAL, 8, true, 0.15f, true, 21, 23),
            AmbientProfile(ProfileType.SLEEP, InformationDensity.ESSENTIAL, 6, true, 0.08f, true, 23, 7),
            AmbientProfile(ProfileType.STUDY, InformationDensity.BALANCED, 15, false, 0.40f, true),
            AmbientProfile(ProfileType.BATTERY_SAVER, InformationDensity.ESSENTIAL, 6, true, 0.20f, false, lowBatteryThreshold = 20)
        )
    }
}
