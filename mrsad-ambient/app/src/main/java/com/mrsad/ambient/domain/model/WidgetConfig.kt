package com.mrsad.ambient.domain.model

enum class WidgetType(val titleFa: String, val titleEn: String) {
    CALENDAR("تقویم و رویداد", "Calendar"),
    WEATHER("آب‌وهوا", "Weather"),
    BATTERY("باتری", "Battery"),
    MUSIC("موسیقی", "Music"),
    NOTIFICATIONS("اعلان‌ها", "Notifications"),
    ACTIVITY("فعالیت روزانه", "Daily Activity"),
    CONNECTIVITY("اتصالات", "Connectivity"),
    CALLS("تماس‌ها", "Calls")
}

enum class WidgetSize(val spanColumns: Int, val titleFa: String, val titleEn: String) {
    COMPACT(1, "کوچک", "Compact"),
    WIDE(2, "عریض", "Wide")
}

data class WidgetItemConfig(
    val type: WidgetType,
    val enabled: Boolean = true,
    val size: WidgetSize = WidgetSize.COMPACT,
    val order: Int = 0,
    val priority: Int = 1
)

data class SavedLayout(
    val id: String,
    val name: String,
    val clockConfig: ClockConfig,
    val widgets: List<WidgetItemConfig>
)

object DefaultWidgets {
    fun list(): List<WidgetItemConfig> = listOf(
        WidgetItemConfig(WidgetType.CALENDAR, enabled = true, size = WidgetSize.WIDE, order = 0, priority = 1),
        WidgetItemConfig(WidgetType.WEATHER, enabled = true, size = WidgetSize.COMPACT, order = 1, priority = 2),
        WidgetItemConfig(WidgetType.BATTERY, enabled = true, size = WidgetSize.COMPACT, order = 2, priority = 1),
        WidgetItemConfig(WidgetType.MUSIC, enabled = true, size = WidgetSize.WIDE, order = 3, priority = 2),
        WidgetItemConfig(WidgetType.NOTIFICATIONS, enabled = true, size = WidgetSize.COMPACT, order = 4, priority = 1),
        WidgetItemConfig(WidgetType.ACTIVITY, enabled = true, size = WidgetSize.COMPACT, order = 5, priority = 3),
        WidgetItemConfig(WidgetType.CONNECTIVITY, enabled = true, size = WidgetSize.COMPACT, order = 6, priority = 3),
        WidgetItemConfig(WidgetType.CALLS, enabled = true, size = WidgetSize.COMPACT, order = 7, priority = 1)
    )
}
