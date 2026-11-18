package ir.aegis.core.ui

import androidx.compose.runtime.Composable
import ir.aegis.core.model.AegisModule
import ir.aegis.core.model.AppLanguage

@Composable
fun tr(en: String, fa: String): String =
    if (LocalAppLanguage.current == AppLanguage.FA) fa else en

fun trText(lang: AppLanguage, en: String, fa: String): String =
    if (lang == AppLanguage.FA) fa else en

fun moduleTitle(module: AegisModule, lang: AppLanguage): String = when (module) {
    AegisModule.HOME -> if (lang == AppLanguage.FA) "سپر امنیتی ایجیس" else "AegisGuard Security Hub"
    AegisModule.EMERGENCY -> if (lang == AppLanguage.FA) "ضد هک و بازرسی سیستم" else "Anti-Hack OS Inspector"
    AegisModule.UTILITIES -> if (lang == AppLanguage.FA) "آنتی‌ویروس و اسکنر برنامه" else "App Malware Scanner"
    AegisModule.AI -> if (lang == AppLanguage.FA) "تشخیص فیشینگ و رمز" else "Phishing & Password Lab"
    AegisModule.MAPS -> if (lang == AppLanguage.FA) "نقشه و مکان‌یاب حرفه‌ای" else "Pro Offline Map & GNSS"
    AegisModule.PRIVACY -> if (lang == AppLanguage.FA) "ممیزی مجوزها و قفل" else "Permissions, Lock & Backup"
    AegisModule.CRISIS -> if (lang == AppLanguage.FA) "گزارش رخداد و جرم‌یابی" else "Security Incident Log"
    AegisModule.PERSONAL -> if (lang == AppLanguage.FA) "گاوصندوق کلید و اسرار" else "AES-256 Key Vault"
    AegisModule.ACADEMY -> if (lang == AppLanguage.FA) "استانداردها و آکادمی دفاع" else "Cyber Defense Academy"
    AegisModule.ANALYTICS -> if (lang == AppLanguage.FA) "تحلیل ریسک و خروجی ممیزی" else "Security Risk & Audit"
    AegisModule.FAMILY -> if (lang == AppLanguage.FA) "ایمنی خانواده و تماس اضطراری" else "Family Cyber Hygiene"
}

fun moduleSubtitle(module: AegisModule, lang: AppLanguage): String = when (module) {
    AegisModule.HOME -> if (lang == AppLanguage.FA) "نمای شیشه‌ای امنیت گوشی و دسترسی سریع" else "Minimal frosted glass security overview"
    AegisModule.EMERGENCY -> if (lang == AppLanguage.FA) "کشف روت، ADB، گواهی CA، شنود و ردیاب پنهان" else "Root, ADB, CA certs, listeners & lockdown"
    AegisModule.UTILITIES -> if (lang == AppLanguage.FA) "اسکن بسته‌های نصب‌شده، امضا و فایل‌های APK" else "APK malware heuristics, signing cert & hash lab"
    AegisModule.AI -> if (lang == AppLanguage.FA) "بررسی لینک پیامکی، آنتروپی رمز و مشاور دفاعی" else "SMS/URL phishing detector, password & playbook AI"
    AegisModule.MAPS -> if (lang == AppLanguage.FA) "نقشه کامل جهان و ایران، GPS دقیق، پیشنهاد هوشمند" else "Global/Iran vector map, GNSS locator, smart routing"
    AegisModule.PRIVACY -> if (lang == AppLanguage.FA) "مجوزهای حساس، قفل پین/بایومتریک و بکاپ رمزنگاری" else "Permission audit, PIN/biometric lock & AES backup"
    AegisModule.CRISIS -> if (lang == AppLanguage.FA) "ثبت موارد نفوذ، مدارک SHA-256 و گزارش PDF" else "Threat dossier, timeline, checklists & PDF"
    AegisModule.PERSONAL -> if (lang == AppLanguage.FA) "نگهداری امن کلیدها، بازیابی و اطلاعات حساس" else "Encrypted credentials, recovery keys & notes"
    AegisModule.ACADEMY -> if (lang == AppLanguage.FA) "استاندارد OWASP MASVS، MITRE و آزمون ضد هک" else "OWASP MASVS, MITRE Mobile & defense drills"
    AegisModule.ANALYTICS -> if (lang == AppLanguage.FA) "نمودار وضعیت ایمنی، شدت تهدیدها و گزارش ممیزی" else "Security posture score, threat heatmap & reports"
    AegisModule.FAMILY -> if (lang == AppLanguage.FA) "وضعیت ایمنی دستگاه‌های خانواده و اشتراک با رضایت" else "Member security status & consent emergency plan"
}
