package ai.dana.data.di

import ai.dana.data.local.DanaDatabase
import ai.dana.data.local.PreferencesStore
import ai.dana.data.local.RoomHistoryRepository
import ai.dana.data.local.RoomMemoryRepository
import ai.dana.data.local.RoomNotesRepository
import ai.dana.data.network.AndroidNetworkMonitor
import ai.dana.data.network.EngineClients
import ai.dana.data.provider.EngineRepository
import ai.dana.data.provider.KeystoreVault
import ai.dana.domain.DanaAiRouter
import ai.dana.domain.DirectSendSettings
import ai.dana.domain.ImageGeneration
import ai.dana.domain.QuickConnect
import ai.dana.domain.EngineDiscovery
import ai.dana.domain.HistoryRepository
import ai.dana.domain.MemoryRepository
import ai.dana.domain.NetworkMonitor
import ai.dana.domain.NotesRepository
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.ProviderRepository
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DanaDatabase =
        Room.databaseBuilder(context, DanaDatabase::class.java, "dana.db").build()

    @Provides
    @Singleton
    fun notes(db: DanaDatabase): NotesRepository = RoomNotesRepository(db.notes())

    @Provides
    @Singleton
    fun history(db: DanaDatabase): HistoryRepository = RoomHistoryRepository(db)

    @Provides
    @Singleton
    fun memory(db: DanaDatabase): MemoryRepository = RoomMemoryRepository(db.memory())

    @Provides
    @Singleton
    fun providers(db: DanaDatabase): ProviderRepository = EngineRepository(db, KeystoreVault())

    @Provides
    @Singleton
    fun settings(@ApplicationContext context: Context): PreferencesStore = PreferencesStore(context)

    @Provides
    fun preferences(settings: PreferencesStore): PrivacyPreferences = settings

    @Provides
    fun directSend(settings: PreferencesStore): DirectSendSettings = settings

    @Provides
    @Singleton
    fun network(@ApplicationContext context: Context): NetworkMonitor = AndroidNetworkMonitor(context)

    @Provides
    @Singleton
    fun discovery(preferences: PrivacyPreferences, providers: ProviderRepository, clients: EngineClients): EngineDiscovery =
        EngineDiscovery(preferences, providers, clients.catalog)

    @Provides
    @Singleton
    fun router(preferences: PrivacyPreferences, providers: ProviderRepository, clients: EngineClients): DanaAiRouter =
        DanaAiRouter(preferences, providers, clients.chat)

    @Provides
    @Singleton
    fun quickConnect(clients: EngineClients): QuickConnect = clients.setup

    @Provides
    @Singleton
    fun imageGeneration(preferences: PrivacyPreferences, providers: ProviderRepository, clients: EngineClients): ImageGeneration =
        ImageGeneration(preferences, providers, clients.art)
}
