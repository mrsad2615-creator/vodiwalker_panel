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
    AegisModule.HOME -> if (lang == AppLanguage.FA) "مرکز فرماندهی" else "Command Home"
    AegisModule.PERSONAL -> if (lang == AppLanguage.FA) "امنیت شخصی" else "Personal Security"
    AegisModule.CRISIS -> if (lang == AppLanguage.FA) "مدیریت بحران" else "Crisis Management"
    AegisModule.MAPS -> if (lang == AppLanguage.FA) "نقشه آفلاین ۲D/۳D" else "Offline 2D/3D Map"
    AegisModule.EMERGENCY -> if (lang == AppLanguage.FA) "پاسخ اضطراری SOS" else "Emergency Response"
    AegisModule.AI -> if (lang == AppLanguage.FA) "هوش مصنوعی محلی" else "Local AI Intelligence"
    AegisModule.FAMILY -> if (lang == AppLanguage.FA) "امنیت خانواده" else "Family Safety"
    AegisModule.ACADEMY -> if (lang == AppLanguage.FA) "آکادمی آمادگی" else "Preparedness Academy"
    AegisModule.ANALYTICS -> if (lang == AppLanguage.FA) "تحلیل و گزارش" else "Advanced Analytics"
    AegisModule.PRIVACY -> if (lang == AppLanguage.FA) "حریم خصوصی و رمزنگاری" else "Privacy & Security"
    AegisModule.UTILITIES -> if (lang == AppLanguage.FA) "ابزارهای پیشرفته" else "Advanced Utilities"
}

fun moduleSubtitle(module: AegisModule, lang: AppLanguage): String = when (module) {
    AegisModule.HOME -> if (lang == AppLanguage.FA) "داشبورد هوشمند و ویجت‌ها" else "Adaptive dashboard & quick hub"
    AegisModule.PERSONAL -> if (lang == AppLanguage.FA) "کارت اضطراری، گاوصندوق، مخاطبین، دفترچه" else "ID card, AES vault, plans, journal"
    AegisModule.CRISIS -> if (lang == AppLanguage.FA) "ثبت رخداد، تایم‌لاین، شواهد، PDF" else "Incidents, timeline, checklists, PDF"
    AegisModule.MAPS -> if (lang == AppLanguage.FA) "بسته‌های آفلاین، مسیرها، خط‌کش، قطب‌نما" else "3D terrain, packages, routes, ruler"
    AegisModule.EMERGENCY -> if (lang == AppLanguage.FA) "آژیر SOS، کمک‌های اولیه، راهنمای بلایا" else "SOS beacon, dialer, first aid, guides"
    AegisModule.AI -> if (lang == AppLanguage.FA) "دستیار آفلاین، دسته‌بندی، تشخیص تکرار" else "Offline assistant, classifier, insights"
    AegisModule.FAMILY -> if (lang == AppLanguage.FA) "پروفیل اعضا، نقاط تجمع، اشتراک محلی" else "Profiles, rally points, consent share"
    AegisModule.ACADEMY -> if (lang == AppLanguage.FA) "۶ دوره، آزمون، سناریوی تعاملی" else "6 courses, quizzes, live scenarios"
    AegisModule.ANALYTICS -> if (lang == AppLanguage.FA) "نمودار، نقشه حرارتی، خروجی PDF/CSV" else "Charts, heatmap, filters, PDF/CSV"
    AegisModule.PRIVACY -> if (lang == AppLanguage.FA) "قفل PIN، بکاپ رمزنگاری، گزارش ممیزی" else "PIN/bio lock, backup, audit, perms"
    AegisModule.UTILITIES -> if (lang == AppLanguage.FA) "چراغ‌قوه، قطب‌نما، GPS، صدا، اسکنر، ابزارها" else "12 offline survival & field tools"
}
