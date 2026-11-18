package ir.aegis.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AegisModule(val route: String) {
    HOME("home"),
    PERSONAL("personal"),
    CRISIS("crisis"),
    MAPS("maps"),
    EMERGENCY("emergency"),
    AI("ai"),
    FAMILY("family"),
    ACADEMY("academy"),
    ANALYTICS("analytics"),
    PRIVACY("privacy"),
    UTILITIES("utilities"),
}

@Serializable
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

@Serializable
enum class AppLanguage {
    EN,
    FA,
}

@Serializable
enum class DashboardWidgetId {
    PREPAREDNESS_SCORE,
    CRISIS_PULSE,
    FAMILY_CHECKIN,
    REMINDERS_AND_INVENTORY,
    INCIDENT_SPARKLINE,
}

@Serializable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.FA,
    val pinEnabled: Boolean = false,
    val pinSaltBase64: String = "",
    val pinHashBase64: String = "",
    val biometricEnabled: Boolean = false,
    val flagSecureEnabled: Boolean = true,
    val sensitiveNotificationRedacted: Boolean = true,
    val autoLockSeconds: Int = 120,
    val favoriteModules: List<AegisModule> = listOf(
        AegisModule.EMERGENCY,
        AegisModule.CRISIS,
        AegisModule.MAPS,
        AegisModule.AI,
    ),
    val widgetOrder: List<DashboardWidgetId> = DashboardWidgetId.entries.toList(),
    val disabledWidgets: Set<DashboardWidgetId> = emptySet(),
    val fallbackLatitude: Double = 35.6892,
    val fallbackLongitude: Double = 51.3890,
    val fallbackLocationLabel: String = "تهران (مرکز آفلاین)",
    val activeMapPackageId: String = "pack-tehran-grid",
    val localModelPath: String = "",
    val localModelName: String = "",
    val localModelSha256: String = "",
    val localDeviceSharingConsent: Boolean = false,
    val lastBackupAtMillis: Long = 0L,
    val onboardingSeen: Boolean = false,
)
