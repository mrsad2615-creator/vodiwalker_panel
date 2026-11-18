package com.vodiwalker.gesture.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vodiwalker.gesture.core.model.DockEdge
import com.vodiwalker.gesture.core.model.OverlayUiConfig
import com.vodiwalker.gesture.core.model.PowerMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vodiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vodigesture_user_prefs",
)

/**
 * Immutable snapshot of user preferences stored via Jetpack Preferences DataStore (replacing
 * deprecated `PreferenceManager` / blocking `SharedPreferences`).
 */
data class UserSettings(
    val prominentDisclosureAccepted: Boolean,
    val dynamicMaterialYouColor: Boolean,
    val forceHighContrast: Boolean,
    val hapticIntensity: Float,
    val preferredPowerMode: PowerMode?,
    val overlayConfig: OverlayUiConfig,
    val privacyExcludedPackages: Set<String>,
)

/**
 * Single source of truth for preferences, exposed as a cold [Flow] plus suspend writers.
 */
@Singleton
class UserSettingsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val PROMINENT_DISCLOSURE_ACCEPTED = booleanPreferencesKey("prominent_disclosure_accepted")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_material_you_color")
        val HIGH_CONTRAST = booleanPreferencesKey("force_high_contrast")
        val HAPTIC_INTENSITY = floatPreferencesKey("haptic_intensity")
        val PREFERRED_POWER_MODE = stringPreferencesKey("preferred_power_mode")
        val OVERLAY_DOCK_EDGE = stringPreferencesKey("overlay_dock_edge")
        val OVERLAY_VERTICAL_FRACTION = floatPreferencesKey("overlay_vertical_fraction")
        val OVERLAY_GLASS_BLUR = booleanPreferencesKey("overlay_glass_blur")
        val OVERLAY_AI_GHOST_TRAIL = booleanPreferencesKey("overlay_ai_ghost_trail")
        val PRIVACY_EXCLUDED_PACKAGES = stringSetPreferencesKey("privacy_excluded_packages")
    }

    val settingsFlow: Flow<UserSettings> = context.vodiPreferencesDataStore.data
        .catch { ex ->
            if (ex is IOException) emit(emptyPreferences()) else throw ex
        }
        .map { prefs ->
            UserSettings(
                prominentDisclosureAccepted = prefs[Keys.PROMINENT_DISCLOSURE_ACCEPTED] ?: false,
                dynamicMaterialYouColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                forceHighContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
                hapticIntensity = (prefs[Keys.HAPTIC_INTENSITY] ?: 0.85f).coerceIn(0f, 1f),
                preferredPowerMode = prefs[Keys.PREFERRED_POWER_MODE]?.let { raw ->
                    PowerMode.entries.firstOrNull { it.name == raw }
                },
                overlayConfig = OverlayUiConfig(
                    dockEdge = if (prefs[Keys.OVERLAY_DOCK_EDGE] == DockEdge.START.name) {
                        DockEdge.START
                    } else {
                        DockEdge.END
                    },
                    verticalFraction = (prefs[Keys.OVERLAY_VERTICAL_FRACTION] ?: 0.45f).coerceIn(0.12f, 0.88f),
                    enableGlassBlur = prefs[Keys.OVERLAY_GLASS_BLUR] ?: true,
                    showAiGhostTrail = prefs[Keys.OVERLAY_AI_GHOST_TRAIL] ?: true,
                ),
                privacyExcludedPackages = prefs[Keys.PRIVACY_EXCLUDED_PACKAGES] ?: DEFAULT_PROTECTED_PACKAGES,
            )
        }

    /**
     * Records the Google Play Prominent Disclosure acknowledgement.
     */
    suspend fun setProminentDisclosureAccepted(accepted: Boolean) {
        context.vodiPreferencesDataStore.edit { it[Keys.PROMINENT_DISCLOSURE_ACCEPTED] = accepted }
    }

    /**
     * Stores the user's haptic gain in `[0,1]`.
     */
    suspend fun setHapticIntensity(intensity: Float) {
        context.vodiPreferencesDataStore.edit { it[Keys.HAPTIC_INTENSITY] = intensity.coerceIn(0f, 1f) }
    }

    /**
     * Pins a power mode, or null to return to the adaptive governor.
     */
    suspend fun setPreferredPowerMode(mode: PowerMode?) {
        context.vodiPreferencesDataStore.edit { prefs ->
            if (mode == null) prefs.remove(Keys.PREFERRED_POWER_MODE) else prefs[Keys.PREFERRED_POWER_MODE] = mode.name
        }
    }

    /**
     * Persists the overlay dock edge and clamped vertical position.
     */
    suspend fun updateOverlayPosition(edge: DockEdge, verticalFraction: Float) {
        context.vodiPreferencesDataStore.edit { prefs ->
            prefs[Keys.OVERLAY_DOCK_EDGE] = edge.name
            prefs[Keys.OVERLAY_VERTICAL_FRACTION] = verticalFraction.coerceIn(0.12f, 0.88f)
        }
    }

    /**
     * Adds or removes a package from the privacy exclusion list.
     */
    suspend fun togglePrivacyExcludedPackage(packageName: String, excluded: Boolean) {
        context.vodiPreferencesDataStore.edit { prefs ->
            val current = (prefs[Keys.PRIVACY_EXCLUDED_PACKAGES] ?: DEFAULT_PROTECTED_PACKAGES).toMutableSet()
            if (excluded) current.add(packageName) else current.remove(packageName)
            prefs[Keys.PRIVACY_EXCLUDED_PACKAGES] = current
        }
    }

    companion object {
        /**
         * Default hardened exclusion list protecting credential managers, package installers, and
         * permission controllers on top of Android 14's `isAccessibilityDataSensitive` check.
         */
        val DEFAULT_PROTECTED_PACKAGES: Set<String> = setOf(
            "com.android.systemui.biometrics",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.android.credentials",
            "com.google.android.apps.walletnfcrel",
            "com.bitwarden.mobile",
            "com.agilebits.onepassword",
        )
    }
}
