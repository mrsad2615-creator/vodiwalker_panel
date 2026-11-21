package dev.mrsad.app.data.settings

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mrsad_settings")

/** Visual themes shipped with MRSAD. */
enum class ThemeVariant(val id: String, val labelFa: String) {
    OBSIDIAN("obsidian", "ابسیدین"),
    QUANTUM("quantum", "کوانتوم"),
    CRYSTAL("crystal", "کریستال"),
    ARCTIC("arctic", "آرکتیک"),
    PHANTOM("phantom", "فانتوم"),
    SPECTRUM("spectrum", "اسپکتروم");

    companion object {
        fun fromId(id: String?): ThemeVariant = entries.firstOrNull { it.id == id } ?: OBSIDIAN
    }
}

enum class GraphicsQuality(val id: String, val labelResKey: String) {
    HIGH("high", "quality_high"),
    BALANCED("balanced", "quality_balanced"),
    PERFORMANCE("performance", "quality_performance");

    companion object {
        fun fromId(id: String?): GraphicsQuality = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

data class MrsadSettings(
    val theme: ThemeVariant = ThemeVariant.OBSIDIAN,
    val quality: GraphicsQuality = GraphicsQuality.BALANCED,
    val persianNumerals: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val reduceMotion: Boolean = false,
    val fontScale: Float = 1.0f,
) {
    /** Full-strength animations only when the user allows it. */
    val animationsEnabled: Boolean get() = !reduceMotion
    val particleCount: Int
        get() = when (quality) {
            GraphicsQuality.HIGH -> 90
            GraphicsQuality.BALANCED -> 45
            GraphicsQuality.PERFORMANCE -> 0
        }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val quality = stringPreferencesKey("quality")
        val persianNumerals = booleanPreferencesKey("persian_numerals")
        val haptics = booleanPreferencesKey("haptics")
        val sound = booleanPreferencesKey("sound")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val fontScale = stringPreferencesKey("font_scale")
        val defaultsSeeded = booleanPreferencesKey("defaults_seeded")
    }

    val settings: Flow<MrsadSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            MrsadSettings(
                theme = ThemeVariant.fromId(prefs[Keys.theme]),
                quality = GraphicsQuality.fromId(prefs[Keys.quality]),
                persianNumerals = prefs[Keys.persianNumerals] ?: true,
                hapticsEnabled = prefs[Keys.haptics] ?: true,
                soundEnabled = prefs[Keys.sound] ?: false,
                reduceMotion = prefs[Keys.reduceMotion] ?: false,
                fontScale = prefs[Keys.fontScale]?.toFloatOrNull()?.coerceIn(0.85f, 1.3f) ?: 1.0f,
            )
        }

    /**
     * Seeds defaults once. Visual quality is chosen from real device memory:
     * low-RAM devices start at Balanced (not High) so the first launch is
     * smooth even before the user visits Studio (spec §26 adaptive graphics).
     */
    suspend fun ensureDefaults() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.defaultsSeeded] != true) {
                if (prefs[Keys.quality] == null) {
                    prefs[Keys.quality] = defaultQualityForDevice().id
                }
                prefs[Keys.defaultsSeeded] = true
            }
        }
    }

    private fun defaultQualityForDevice(): GraphicsQuality {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return GraphicsQuality.BALANCED
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024L * 1024L)
        return when {
            activityManager.isLowRamDevice -> GraphicsQuality.PERFORMANCE
            totalRamMb < 3_000 -> GraphicsQuality.BALANCED
            totalRamMb >= 6_000 -> GraphicsQuality.HIGH
            else -> GraphicsQuality.BALANCED
        }
    }

    suspend fun setTheme(variant: ThemeVariant) = edit { it[Keys.theme] = variant.id }
    suspend fun setQuality(quality: GraphicsQuality) = edit { it[Keys.quality] = quality.id }
    suspend fun setPersianNumerals(enabled: Boolean) = edit { it[Keys.persianNumerals] = enabled }
    suspend fun setHaptics(enabled: Boolean) = edit { it[Keys.haptics] = enabled }
    suspend fun setSound(enabled: Boolean) = edit { it[Keys.sound] = enabled }
    suspend fun setReduceMotion(enabled: Boolean) = edit { it[Keys.reduceMotion] = enabled }
    suspend fun setFontScale(scale: Float) = edit { it[Keys.fontScale] = scale.coerceIn(0.85f, 1.3f).toString() }

    suspend fun restoreDefaults() = edit { prefs ->
        prefs.remove(Keys.theme)
        prefs.remove(Keys.quality)
        prefs.remove(Keys.persianNumerals)
        prefs.remove(Keys.haptics)
        prefs.remove(Keys.sound)
        prefs.remove(Keys.reduceMotion)
        prefs.remove(Keys.fontScale)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
