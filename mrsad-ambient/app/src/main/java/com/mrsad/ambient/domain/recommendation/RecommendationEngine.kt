package com.mrsad.ambient.domain.recommendation

import com.mrsad.ambient.domain.model.BatteryState
import com.mrsad.ambient.domain.model.ProfileType
import java.util.Calendar

data class AmbientRecommendation(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val descriptionFa: String,
    val descriptionEn: String,
    val suggestedProfile: ProfileType? = null
)

object RecommendationEngine {
    fun evaluate(battery: BatteryState, currentProfile: ProfileType): List<AmbientRecommendation> {
        val list = mutableListOf<AmbientRecommendation>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (battery.isLowBattery && !battery.isCharging && currentProfile != ProfileType.BATTERY_SAVER) {
            list.add(
                AmbientRecommendation(
                    id = "low_battery",
                    titleFa = "پیشنهاد حالت صرفه‌جویی باتری",
                    titleEn = "Battery Saver Recommended",
                    descriptionFa = "شارژ باتری به ${battery.percentage}% رسیده است. کاهش انیمیشن و زمان نمایش توصیه می‌شود.",
                    descriptionEn = "Battery is at ${battery.percentage}%. Reducing animations and display timeout is recommended.",
                    suggestedProfile = ProfileType.BATTERY_SAVER
                )
            )
        }

        if ((hour >= 22 || hour < 6) && currentProfile == ProfileType.DAY) {
            list.add(
                AmbientRecommendation(
                    id = "night_profile",
                    titleFa = "پیشنهاد پروفایل شب",
                    titleEn = "Night Profile Recommended",
                    descriptionFa = "در ساعات شب، کاهش روشنایی و اطلاعات نمایشگر برای نمایشگر IPS LCD بهینه‌تر است.",
                    descriptionEn = "During night hours, lower brightness and essential density are optimal for IPS LCD.",
                    suggestedProfile = ProfileType.NIGHT
                )
            )
        }

        return list
    }
}
