package com.mesad.time.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mesadPreferences by preferencesDataStore(name = "mesad_settings")

/** Typed DataStore-backed local settings and Studio preset persistence. */
class MesadSettingsStore(private val context: Context) {
    val values: Flow<Map<String, Any>> = context.mesadPreferences.data.map { prefs ->
        prefs.asMap().mapKeys { it.key.name }.mapValues { it.value }
    }

    suspend fun put(key: String, value: Any) {
        context.mesadPreferences.edit { prefs ->
            when (value) {
                is Boolean -> prefs[booleanPreferencesKey(key)] = value
                is Float -> prefs[floatPreferencesKey(key)] = value
                is String -> prefs[stringPreferencesKey(key)] = value
                else -> throw IllegalArgumentException("Unsupported MESAD preference type: ${value::class.java.simpleName}")
            }
        }
    }

    suspend fun putStudioPreset(engine: String, material: String, scale: Float, glow: Float) {
        context.mesadPreferences.edit { prefs ->
            prefs[stringPreferencesKey("studio_engine")] = engine
            prefs[stringPreferencesKey("studio_material")] = material
            prefs[floatPreferencesKey("studio_scale")] = scale
            prefs[floatPreferencesKey("studio_glow")] = glow
        }
    }
}
