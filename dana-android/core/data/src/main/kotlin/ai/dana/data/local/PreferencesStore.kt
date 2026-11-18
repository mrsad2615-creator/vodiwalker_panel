package ai.dana.data.local

import ai.dana.domain.DirectSendSettings
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.ProviderProfile
import ai.dana.domain.SendGrant
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.danaSettings by preferencesDataStore(name = "dana_settings")

internal class PreferencesStore(private val dataStore: DataStore<Preferences>) : PrivacyPreferences, DirectSendSettings {
    constructor(context: Context) : this(context.applicationContext.danaSettings)

    private val offlineKey = booleanPreferencesKey("fully_offline")

    private val engineKey = stringPreferencesKey("preferred_engine")
    private val grantsKey = stringSetPreferencesKey("approved_routes_v1")

    override val preferredEngine = dataStore.data.map { it[engineKey] }.distinctUntilChanged()
    override val offlineMode = dataStore.data.map { it[offlineKey] ?: true }.distinctUntilChanged()

    override suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { it[offlineKey] = enabled }
    }

    override suspend fun permits(grant: SendGrant): Boolean = grant.id in dataStore.data.first()[grantsKey].orEmpty()

    override suspend fun remember(grant: SendGrant) {
        dataStore.edit { it[grantsKey] = keepRecent(it[grantsKey].orEmpty(), grant.id) }
    }

    override suspend fun forgetAll() {
        dataStore.edit { it.remove(grantsKey) }
    }

    override suspend fun activate(profile: ProviderProfile, allowDirectText: Boolean) {
        dataStore.edit {
            it[engineKey] = profile.id
            it[offlineKey] = false
            if (allowDirectText) {
                val grant = SendGrant.chat(listOf(profile), parallel = false, context = true, memory = false,
                    attachments = false, masked = true)
                it[grantsKey] = keepRecent(it[grantsKey].orEmpty(), grant.id)
            }
        }
    }

    private fun keepRecent(previous: Set<String>, id: String): Set<String> =
        (previous.filterNot { it == id }.takeLast(95) + id).toSet()
}
