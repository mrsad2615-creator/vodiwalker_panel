package ir.aegis.core.database

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.aegis.core.model.AegisModule
import ir.aegis.core.model.AppLanguage
import ir.aegis.core.model.AppPreferences
import ir.aegis.core.model.DashboardWidgetId
import ir.aegis.core.model.PreferencesRepository
import ir.aegis.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aegisPrefsDataStore by preferencesDataStore(name = "aegis_security_prefs")

class DataStorePreferencesRepository(private val context: Context) : PreferencesRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val FLAG_SECURE = booleanPreferencesKey("flag_secure")
        val REDACT_NOTIF = booleanPreferencesKey("redact_notifications")
        val AUTO_LOCK_SECS = intPreferencesKey("auto_lock_seconds")
        val FAVORITES = stringPreferencesKey("favorite_modules")
        val WIDGET_ORDER = stringPreferencesKey("widget_order")
        val DISABLED_WIDGETS = stringPreferencesKey("disabled_widgets")
        val FALLBACK_LAT = doublePreferencesKey("fallback_lat")
        val FALLBACK_LON = doublePreferencesKey("fallback_lon")
        val FALLBACK_LABEL = stringPreferencesKey("fallback_label")
        val ACTIVE_MAP_PACK = stringPreferencesKey("active_map_pack")
        val MODEL_PATH = stringPreferencesKey("local_model_path")
        val MODEL_NAME = stringPreferencesKey("local_model_name")
        val MODEL_SHA = stringPreferencesKey("local_model_sha256")
        val SHARING_CONSENT = booleanPreferencesKey("local_sharing_consent")
        val LAST_BACKUP = longPreferencesKey("last_backup_millis")
    }

    override val preferences: Flow<AppPreferences> = context.aegisPrefsDataStore.data.map { p ->
        val favs = p[Keys.FAVORITES]
            ?.split(",")
            ?.mapNotNull { raw -> runCatching { AegisModule.valueOf(raw.trim()) }.getOrNull() }
            ?: AppPreferences().favoriteModules
        val order = p[Keys.WIDGET_ORDER]
            ?.split(",")
            ?.mapNotNull { raw -> runCatching { DashboardWidgetId.valueOf(raw.trim()) }.getOrNull() }
            ?.let { list -> list + DashboardWidgetId.entries.filterNot { it in list } }
            ?: DashboardWidgetId.entries.toList()
        val disabled = p[Keys.DISABLED_WIDGETS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { raw -> runCatching { DashboardWidgetId.valueOf(raw.trim()) }.getOrNull() }
            ?.toSet()
            ?: emptySet()

        AppPreferences(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            language = p[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.EN,
            pinEnabled = p[Keys.PIN_ENABLED] ?: false,
            pinSaltBase64 = p[Keys.PIN_SALT] ?: "",
            pinHashBase64 = p[Keys.PIN_HASH] ?: "",
            biometricEnabled = p[Keys.BIOMETRIC] ?: false,
            flagSecureEnabled = p[Keys.FLAG_SECURE] ?: true,
            sensitiveNotificationRedacted = p[Keys.REDACT_NOTIF] ?: true,
            autoLockSeconds = p[Keys.AUTO_LOCK_SECS] ?: 120,
            favoriteModules = favs,
            widgetOrder = order,
            disabledWidgets = disabled,
            fallbackLatitude = p[Keys.FALLBACK_LAT] ?: 35.6892,
            fallbackLongitude = p[Keys.FALLBACK_LON] ?: 51.3890,
            fallbackLocationLabel = p[Keys.FALLBACK_LABEL] ?: "Offline Anchor (Manual)",
            activeMapPackageId = p[Keys.ACTIVE_MAP_PACK] ?: "pack-tehran-grid",
            localModelPath = p[Keys.MODEL_PATH] ?: "",
            localModelName = p[Keys.MODEL_NAME] ?: "",
            localModelSha256 = p[Keys.MODEL_SHA] ?: "",
            localDeviceSharingConsent = p[Keys.SHARING_CONSENT] ?: false,
            lastBackupAtMillis = p[Keys.LAST_BACKUP] ?: 0L,
        )
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        context.aegisPrefsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    override suspend fun updateLanguage(language: AppLanguage) {
        context.aegisPrefsDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    override suspend fun setPinCredential(saltBase64: String, hashBase64: String) {
        context.aegisPrefsDataStore.edit {
            it[Keys.PIN_ENABLED] = true
            it[Keys.PIN_SALT] = saltBase64
            it[Keys.PIN_HASH] = hashBase64
        }
    }

    override suspend fun clearPinCredential() {
        context.aegisPrefsDataStore.edit {
            it[Keys.PIN_ENABLED] = false
            it[Keys.PIN_SALT] = ""
            it[Keys.PIN_HASH] = ""
            it[Keys.BIOMETRIC] = false
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        context.aegisPrefsDataStore.edit { it[Keys.BIOMETRIC] = enabled }
    }

    override suspend fun setFlagSecure(enabled: Boolean) {
        context.aegisPrefsDataStore.edit { it[Keys.FLAG_SECURE] = enabled }
    }

    override suspend fun setSensitiveNotificationRedacted(redacted: Boolean) {
        context.aegisPrefsDataStore.edit { it[Keys.REDACT_NOTIF] = redacted }
    }

    override suspend fun setAutoLockSeconds(seconds: Int) {
        context.aegisPrefsDataStore.edit { it[Keys.AUTO_LOCK_SECS] = seconds.coerceIn(15, 3600) }
    }

    override suspend fun toggleFavoriteModule(module: AegisModule) {
        context.aegisPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES]
                ?.split(",")
                ?.mapNotNull { runCatching { AegisModule.valueOf(it.trim()) }.getOrNull() }
                ?.toMutableList()
                ?: AppPreferences().favoriteModules.toMutableList()
            if (module in current) current.remove(module) else current.add(module)
            prefs[Keys.FAVORITES] = current.joinToString(",") { it.name }
        }
    }

    override suspend fun updateDashboardWidgets(order: List<DashboardWidgetId>, disabled: Set<DashboardWidgetId>) {
        context.aegisPrefsDataStore.edit {
            it[Keys.WIDGET_ORDER] = order.joinToString(",") { w -> w.name }
            it[Keys.DISABLED_WIDGETS] = disabled.joinToString(",") { w -> w.name }
        }
    }

    override suspend fun setFallbackLocation(latitude: Double, longitude: Double, label: String) {
        context.aegisPrefsDataStore.edit {
            it[Keys.FALLBACK_LAT] = latitude
            it[Keys.FALLBACK_LON] = longitude
            it[Keys.FALLBACK_LABEL] = label
        }
    }

    override suspend fun setActiveMapPackage(packageId: String) {
        context.aegisPrefsDataStore.edit { it[Keys.ACTIVE_MAP_PACK] = packageId }
    }

    override suspend fun setLocalModelInfo(path: String, name: String, sha256: String) {
        context.aegisPrefsDataStore.edit {
            it[Keys.MODEL_PATH] = path
            it[Keys.MODEL_NAME] = name
            it[Keys.MODEL_SHA] = sha256
        }
    }

    override suspend fun setLocalDeviceSharingConsent(consented: Boolean) {
        context.aegisPrefsDataStore.edit { it[Keys.SHARING_CONSENT] = consented }
    }

    override suspend fun setLastBackupTimestamp(timestampMillis: Long) {
        context.aegisPrefsDataStore.edit { it[Keys.LAST_BACKUP] = timestampMillis }
    }

    override suspend fun replacePreferences(preferences: AppPreferences) {
        context.aegisPrefsDataStore.edit {
            it[Keys.THEME] = preferences.themeMode.name
            it[Keys.LANGUAGE] = preferences.language.name
            it[Keys.PIN_ENABLED] = preferences.pinEnabled
            it[Keys.PIN_SALT] = preferences.pinSaltBase64
            it[Keys.PIN_HASH] = preferences.pinHashBase64
            it[Keys.BIOMETRIC] = preferences.biometricEnabled
            it[Keys.FLAG_SECURE] = preferences.flagSecureEnabled
            it[Keys.REDACT_NOTIF] = preferences.sensitiveNotificationRedacted
            it[Keys.AUTO_LOCK_SECS] = preferences.autoLockSeconds
            it[Keys.FAVORITES] = preferences.favoriteModules.joinToString(",") { m -> m.name }
            it[Keys.WIDGET_ORDER] = preferences.widgetOrder.joinToString(",") { w -> w.name }
            it[Keys.DISABLED_WIDGETS] = preferences.disabledWidgets.joinToString(",") { w -> w.name }
            it[Keys.FALLBACK_LAT] = preferences.fallbackLatitude
            it[Keys.FALLBACK_LON] = preferences.fallbackLongitude
            it[Keys.FALLBACK_LABEL] = preferences.fallbackLocationLabel
            it[Keys.ACTIVE_MAP_PACK] = preferences.activeMapPackageId
            it[Keys.MODEL_PATH] = preferences.localModelPath
            it[Keys.MODEL_NAME] = preferences.localModelName
            it[Keys.MODEL_SHA] = preferences.localModelSha256
            it[Keys.SHARING_CONSENT] = preferences.localDeviceSharingConsent
            it[Keys.LAST_BACKUP] = preferences.lastBackupAtMillis
        }
    }
}
