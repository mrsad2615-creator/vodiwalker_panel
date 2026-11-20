package com.mrsad.ambient.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrsad.ambient.domain.model.ClockConfig
import com.mrsad.ambient.domain.model.ClockPosition
import com.mrsad.ambient.domain.model.ClockStyle
import com.mrsad.ambient.domain.model.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mrsad_ambient_prefs")

data class UserPreferences(
    val language: String = "fa",
    val activeProfile: ProfileType = ProfileType.DAY,
    val clockConfig: ClockConfig = ClockConfig(),
    val ambientEnabled: Boolean = true,
    val triggerLiftToWake: Boolean = true,
    val triggerDoubleTap: Boolean = true,
    val triggerNotification: Boolean = true,
    val triggerProximity: Boolean = true,
    val pocketDetection: Boolean = true,
    val weatherCity: String = "Tehran",
    val weatherLat: Double = 35.6892,
    val weatherLon: Double = 51.3890,
    val lowBatteryThreshold: Int = 20
)

class PreferencesDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val CLOCK_POSITION = stringPreferencesKey("clock_position")
        val CLOCK_24H = booleanPreferencesKey("clock_24h")
        val CLOCK_PERSIAN_NUMERALS = booleanPreferencesKey("clock_persian_numerals")
        val CLOCK_SECONDS = booleanPreferencesKey("clock_seconds")
        val CLOCK_ANALOG_TICKS = booleanPreferencesKey("clock_analog_ticks")
        val CLOCK_SCALE = floatPreferencesKey("clock_scale")
        val AMBIENT_ENABLED = booleanPreferencesKey("ambient_enabled")
        val TRIGGER_LIFT = booleanPreferencesKey("trigger_lift")
        val TRIGGER_DOUBLE_TAP = booleanPreferencesKey("trigger_double_tap")
        val TRIGGER_NOTIFICATION = booleanPreferencesKey("trigger_notification")
        val TRIGGER_PROXIMITY = booleanPreferencesKey("trigger_proximity")
        val POCKET_DETECTION = booleanPreferencesKey("pocket_detection")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            language = prefs[Keys.LANGUAGE] ?: "fa",
            activeProfile = ProfileType.entries.find { it.name == prefs[Keys.ACTIVE_PROFILE] } ?: ProfileType.DAY,
            clockConfig = ClockConfig(
                style = ClockStyle.entries.find { it.name == prefs[Keys.CLOCK_STYLE] } ?: ClockStyle.HYBRID_ARCHITECTURAL,
                position = ClockPosition.entries.find { it.name == prefs[Keys.CLOCK_POSITION] } ?: ClockPosition.TOP,
                use24Hour = prefs[Keys.CLOCK_24H] ?: true,
                usePersianNumerals = prefs[Keys.CLOCK_PERSIAN_NUMERALS] ?: true,
                showSeconds = prefs[Keys.CLOCK_SECONDS] ?: false,
                showAnalogTicks = prefs[Keys.CLOCK_ANALOG_TICKS] ?: true,
                scale = prefs[Keys.CLOCK_SCALE] ?: 1.0f
            ),
            ambientEnabled = prefs[Keys.AMBIENT_ENABLED] ?: true,
            triggerLiftToWake = prefs[Keys.TRIGGER_LIFT] ?: true,
            triggerDoubleTap = prefs[Keys.TRIGGER_DOUBLE_TAP] ?: true,
            triggerNotification = prefs[Keys.TRIGGER_NOTIFICATION] ?: true,
            triggerProximity = prefs[Keys.TRIGGER_PROXIMITY] ?: true,
            pocketDetection = prefs[Keys.POCKET_DETECTION] ?: true,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "Tehran",
            lowBatteryThreshold = prefs[Keys.LOW_BATTERY_THRESHOLD] ?: 20
        )
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    suspend fun setActiveProfile(profile: ProfileType) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE] = profile.name }
    }

    suspend fun setClockConfig(config: ClockConfig) {
        context.dataStore.edit {
            it[Keys.CLOCK_STYLE] = config.style.name
            it[Keys.CLOCK_POSITION] = config.position.name
            it[Keys.CLOCK_24H] = config.use24Hour
            it[Keys.CLOCK_PERSIAN_NUMERALS] = config.usePersianNumerals
            it[Keys.CLOCK_SECONDS] = config.showSeconds
            it[Keys.CLOCK_ANALOG_TICKS] = config.showAnalogTicks
            it[Keys.CLOCK_SCALE] = config.scale
        }
    }

    suspend fun setAmbientEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMBIENT_ENABLED] = enabled }
    }

    suspend fun setWeatherCity(city: String) {
        context.dataStore.edit { it[Keys.WEATHER_CITY] = city }
    }
}
