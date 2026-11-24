package ai.neurox.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.neuroxPreferences by preferencesDataStore(name = "neurox_preferences")

data class ThemeSettings(val mode: String = "dark", val accentHex: String = "D6B875")

class AppPreferences(private val context: Context) {
    private val languageKey = stringPreferencesKey("language")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val accentKey = stringPreferencesKey("accent_hex")

    val language: Flow<String> = context.neuroxPreferences.data.map { values ->
        values[languageKey]?.takeIf { it == "fa" || it == "en" } ?: "en"
    }

    val theme: Flow<ThemeSettings> = context.neuroxPreferences.data.map { values ->
        ThemeSettings(
            mode = values[themeModeKey]?.takeIf { it == "dark" || it == "light" } ?: "dark",
            accentHex = values[accentKey]?.takeIf { it.matches(Regex("^[0-9A-Fa-f]{6}$")) } ?: "D6B875"
        )
    }

    suspend fun setLanguage(language: String) {
        require(language == "fa" || language == "en")
        context.neuroxPreferences.edit { it[languageKey] = language }
    }

    suspend fun setThemeMode(mode: String) {
        require(mode == "dark" || mode == "light")
        context.neuroxPreferences.edit { it[themeModeKey] = mode }
    }

    suspend fun setAccent(hex: String) {
        require(hex.matches(Regex("^[0-9A-Fa-f]{6}$")))
        context.neuroxPreferences.edit { it[accentKey] = hex.uppercase() }
    }
}
