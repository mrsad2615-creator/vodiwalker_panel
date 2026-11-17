package ai.dana.data.local

import ai.dana.domain.PrivacyPreferences
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.danaSettings by preferencesDataStore(name = "dana_settings")

internal class PreferencesStore(private val dataStore: DataStore<Preferences>) : PrivacyPreferences {
    constructor(context: Context) : this(context.applicationContext.danaSettings)

    private val offlineKey = booleanPreferencesKey("fully_offline")

    override val offlineMode = dataStore.data.map { it[offlineKey] ?: true }.distinctUntilChanged()

    override suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { it[offlineKey] = enabled }
    }
}
