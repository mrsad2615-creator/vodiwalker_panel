package ir.aegis.core.ui

import ir.aegis.core.model.AcademyDomain
import ir.aegis.core.model.AppLanguage
import ir.aegis.core.model.FamilyCheckInStatus
import ir.aegis.core.model.IncidentCategory
import ir.aegis.core.model.IncidentPriority
import ir.aegis.core.model.IncidentStatus
import ir.aegis.core.model.MarkerCategory
import ir.aegis.core.model.ThreatMoodLevel
import ir.aegis.core.model.VaultCategory

fun categoryLabel(category: IncidentCategory, lang: AppLanguage): String = when (category) {
    IncidentCategory.MEDICAL -> if (lang == AppLanguage.FA) "اورژانس پزشکی" else "Medical"
    IncidentCategory.FIRE -> if (lang == AppLanguage.FA) "آتش‌سوزی" else "Fire"
    IncidentCategory.EARTHQUAKE -> if (lang == AppLanguage.FA) "زلزله" else "Earthquake"
    IncidentCategory.FLOOD -> if (lang == AppLanguage.FA) "سیل" else "Flood"
    IncidentCategory.SECURITY_THREAT -> if (lang == AppLanguage.FA) "تهدید امنیتی" else "Security Threat"
    IncidentCategory.TRAFFIC_ACCIDENT -> if (lang == AppLanguage.FA) "حادثه ترافیکی" else "Traffic"
    IncidentCategory.UTILITY_OUTAGE -> if (lang == AppLanguage.FA) "قطع زیرساخت" else "Utility Outage"
    IncidentCategory.MISSING_PERSON -> if (lang == AppLanguage.FA) "فرد گمشده" else "Missing Person"
}

fun priorityLabel(priority: IncidentPriority, lang: AppLanguage): String = when (priority) {
    IncidentPriority.LOW -> if (lang == AppLanguage.FA) "کم" else "Low"
    IncidentPriority.MEDIUM -> if (lang == AppLanguage.FA) "متوسط" else "Medium"
    IncidentPriority.HIGH -> if (lang == AppLanguage.FA) "بالا" else "High"
    IncidentPriority.CRITICAL -> if (lang == AppLanguage.FA) "بحرانی" else "Critical"
}

fun statusLabel(status: IncidentStatus, lang: AppLanguage): String = when (status) {
    IncidentStatus.OPEN -> if (lang == AppLanguage.FA) "باز" else "Open"
    IncidentStatus.INVESTIGATING -> if (lang == AppLanguage.FA) "در حال بررسی" else "Investigating"
    IncidentStatus.CONTAINED -> if (lang == AppLanguage.FA) "مهار شده" else "Contained"
    IncidentStatus.RESOLVED -> if (lang == AppLanguage.FA) "حل‌شده" else "Resolved"
    IncidentStatus.ARCHIVED -> if (lang == AppLanguage.FA) "آرشیو" else "Archived"
}

fun markerCategoryLabel(category: MarkerCategory, lang: AppLanguage): String = when (category) {
    MarkerCategory.SHELTER -> if (lang == AppLanguage.FA) "پناهگاه" else "Shelter"
    MarkerCategory.HOSPITAL -> if (lang == AppLanguage.FA) "بیمارستان" else "Hospital"
    MarkerCategory.POLICE -> if (lang == AppLanguage.FA) "امداد/امنیت" else "Security"
    MarkerCategory.FIRE_STATION -> if (lang == AppLanguage.FA) "آتش‌نشانی" else "Fire Station"
    MarkerCategory.WATER_POINT -> if (lang == AppLanguage.FA) "منبع آب" else "Water Hub"
    MarkerCategory.ASSEMBLY_POINT -> if (lang == AppLanguage.FA) "نقطه تجمع" else "Assembly"
    MarkerCategory.HAZARD -> if (lang == AppLanguage.FA) "منطقه خطر" else "Hazard"
    MarkerCategory.CUSTOM -> if (lang == AppLanguage.FA) "نقطه دلخواه" else "Custom Pin"
}

fun vaultCategoryLabel(category: VaultCategory, lang: AppLanguage): String = when (category) {
    VaultCategory.IDENTITY -> if (lang == AppLanguage.FA) "هویت" else "Identity"
    VaultCategory.MEDICAL -> if (lang == AppLanguage.FA) "پزشکی" else "Medical"
    VaultCategory.ACCESS_KEY -> if (lang == AppLanguage.FA) "کلید دسترسی" else "Access Key"
    VaultCategory.LEGAL -> if (lang == AppLanguage.FA) "حقوقی" else "Legal"
    VaultCategory.FINANCIAL -> if (lang == AppLanguage.FA) "مالی" else "Financial"
    VaultCategory.OTHER -> if (lang == AppLanguage.FA) "سایر" else "Other"
}

fun familyStatusLabel(status: FamilyCheckInStatus, lang: AppLanguage): String = when (status) {
    FamilyCheckInStatus.UNKNOWN -> if (lang == AppLanguage.FA) "نامشخص" else "Unknown"
    FamilyCheckInStatus.SAFE -> if (lang == AppLanguage.FA) "امن" else "Safe"
    FamilyCheckInStatus.NEED_HELP -> if (lang == AppLanguage.FA) "نیازمند کمک" else "Needs Help"
    FamilyCheckInStatus.EVACUATING -> if (lang == AppLanguage.FA) "در حال تخلیه" else "Evacuating"
}

fun threatMoodLabel(mood: ThreatMoodLevel, lang: AppLanguage): String = when (mood) {
    ThreatMoodLevel.CALM -> if (lang == AppLanguage.FA) "عادی" else "Calm"
    ThreatMoodLevel.CAUTIOUS -> if (lang == AppLanguage.FA) "محتاط" else "Cautious"
    ThreatMoodLevel.ELEVATED -> if (lang == AppLanguage.FA) "هشدار بالا" else "Elevated"
    ThreatMoodLevel.CRITICAL -> if (lang == AppLanguage.FA) "بحرانی" else "Critical"
}

fun academyDomainLabel(domain: AcademyDomain, lang: AppLanguage): String = when (domain) {
    AcademyDomain.FIRST_AID -> if (lang == AppLanguage.FA) "کمک‌های اولیه" else "First Aid"
    AcademyDomain.FIRE_SAFETY -> if (lang == AppLanguage.FA) "ایمنی آتش‌سوزی" else "Fire Safety"
    AcademyDomain.EARTHQUAKE -> if (lang == AppLanguage.FA) "آمادگی زلزله" else "Earthquake"
    AcademyDomain.FLOOD -> if (lang == AppLanguage.FA) "ایمنی سیل" else "Flood Safety"
    AcademyDomain.TRAVEL_SAFETY -> if (lang == AppLanguage.FA) "امنیت سفر" else "Travel Safety"
    AcademyDomain.DIGITAL_SAFETY -> if (lang == AppLanguage.FA) "امنیت دیجیتال" else "Digital Safety"
}
