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
    IncidentCategory.SECURITY_THREAT -> if (lang == AppLanguage.FA) "نفوذ یا تهدید امنیتی" else "Security Breach / Threat"
    IncidentCategory.UTILITY_OUTAGE -> if (lang == AppLanguage.FA) "اختلال شبکه / ارتباط" else "Network / System Anomaly"
    IncidentCategory.MISSING_PERSON -> if (lang == AppLanguage.FA) "گم‌شدن یا سرقت دستگاه" else "Lost / Compromised Device"
    IncidentCategory.MEDICAL -> if (lang == AppLanguage.FA) "فیشینگ و کلاهبرداری" else "Phishing / Scam"
    IncidentCategory.FIRE -> if (lang == AppLanguage.FA) "بدافزار و ویروس" else "Malware / Trojan"
    IncidentCategory.EARTHQUAKE -> if (lang == AppLanguage.FA) "نشت اطلاعات و رمز" else "Credential / Data Leak"
    IncidentCategory.FLOOD -> if (lang == AppLanguage.FA) "شنود و دسترسی مخفی" else "Spyware / Surveillance"
    IncidentCategory.TRAFFIC_ACCIDENT -> if (lang == AppLanguage.FA) "دستکاری سیستم / روت" else "OS Tampering / Root"
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
    IncidentStatus.RESOLVED -> if (lang == AppLanguage.FA) "برطرف‌شده" else "Resolved"
    IncidentStatus.ARCHIVED -> if (lang == AppLanguage.FA) "بایگانی" else "Archived"
}

fun markerCategoryLabel(category: MarkerCategory, lang: AppLanguage): String = when (category) {
    MarkerCategory.SHELTER -> if (lang == AppLanguage.FA) "منطقه امن" else "Safe Zone"
    MarkerCategory.HOSPITAL -> if (lang == AppLanguage.FA) "مرکز درمانی" else "Hospital"
    MarkerCategory.POLICE -> if (lang == AppLanguage.FA) "مرکز امنیتی / پلیس" else "Security / Police"
    MarkerCategory.FIRE_STATION -> if (lang == AppLanguage.FA) "پایگاه امداد" else "Rescue Station"
    MarkerCategory.WATER_POINT -> if (lang == AppLanguage.FA) "نقطه خدمات" else "Service Hub"
    MarkerCategory.ASSEMBLY_POINT -> if (lang == AppLanguage.FA) "نقطه قرار / تجمع" else "Rally Point"
    MarkerCategory.HAZARD -> if (lang == AppLanguage.FA) "هشدار / پرریسک" else "Caution / Risk"
    MarkerCategory.CUSTOM -> if (lang == AppLanguage.FA) "نشانگر دلخواه" else "Custom Pin"
}

fun vaultCategoryLabel(category: VaultCategory, lang: AppLanguage): String = when (category) {
    VaultCategory.IDENTITY -> if (lang == AppLanguage.FA) "کلید / احراز هویت" else "Auth / Identity"
    VaultCategory.ACCESS_KEY -> if (lang == AppLanguage.FA) "کلید API / SSH / بازیابی" else "Recovery / API Key"
    VaultCategory.FINANCIAL -> if (lang == AppLanguage.FA) "اطلاعات بانکی محرمانه" else "Banking Secrets"
    VaultCategory.LEGAL -> if (lang == AppLanguage.FA) "اسناد و شواهد" else "Legal / Forensics"
    VaultCategory.MEDICAL -> if (lang == AppLanguage.FA) "اطلاعات ضروری" else "Critical Info"
    VaultCategory.OTHER -> if (lang == AppLanguage.FA) "سایر اسرار" else "Other Secret"
}

fun familyStatusLabel(status: FamilyCheckInStatus, lang: AppLanguage): String = when (status) {
    FamilyCheckInStatus.UNKNOWN -> if (lang == AppLanguage.FA) "بررسی‌نشده" else "Unchecked"
    FamilyCheckInStatus.SAFE -> if (lang == AppLanguage.FA) "پاک و امن" else "Verified Safe"
    FamilyCheckInStatus.NEED_HELP -> if (lang == AppLanguage.FA) "مشکوک / نیازمند کمک" else "Needs Help"
    FamilyCheckInStatus.EVACUATING -> if (lang == AppLanguage.FA) "در حال پاک‌سازی" else "Securing Account"
}

fun threatMoodLabel(mood: ThreatMoodLevel, lang: AppLanguage): String = when (mood) {
    ThreatMoodLevel.CALM -> if (lang == AppLanguage.FA) "ایمن" else "Secure"
    ThreatMoodLevel.CAUTIOUS -> if (lang == AppLanguage.FA) "نیازمند بررسی" else "Review"
    ThreatMoodLevel.ELEVATED -> if (lang == AppLanguage.FA) "ریسک بالا" else "Elevated Risk"
    ThreatMoodLevel.CRITICAL -> if (lang == AppLanguage.FA) "هشدار بحرانی" else "Critical Alert"
}

fun academyDomainLabel(domain: AcademyDomain, lang: AppLanguage): String = when (domain) {
    AcademyDomain.DIGITAL_SAFETY -> if (lang == AppLanguage.FA) "استاندارد OWASP MASVS" else "OWASP MASVS"
    AcademyDomain.FIRST_AID -> if (lang == AppLanguage.FA) "واکنش به نفوذ (IR)" else "Incident Response"
    AcademyDomain.FIRE_SAFETY -> if (lang == AppLanguage.FA) "تشخیص بدافزار و تروجان" else "Malware & Trojans"
    AcademyDomain.EARTHQUAKE -> if (lang == AppLanguage.FA) "ضد فیشینگ و مهندسی اجتماعی" else "Anti-Phishing"
    AcademyDomain.FLOOD -> if (lang == AppLanguage.FA) "امنیت شبکه و گواهی CA" else "Network & TLS Audit"
    AcademyDomain.TRAVEL_SAFETY -> if (lang == AppLanguage.FA) "ایمن‌سازی فیزیکی و ADB" else "Physical & ADB Hardening"
}
