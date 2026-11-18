package ai.dana.data.di

import ai.dana.data.BuildConfig
import ai.dana.data.account.StoredEmailAccounts
import ai.dana.data.account.SupabaseEmailApi
import ai.dana.data.local.DanaDatabase
import ai.dana.data.local.PreferencesStore
import ai.dana.data.local.RoomHistoryRepository
import ai.dana.data.local.RoomMemoryRepository
import ai.dana.data.local.RoomNotesRepository
import ai.dana.data.network.AndroidNetworkMonitor
import ai.dana.data.network.OkHttpEngineTransport
import ai.dana.data.network.OkHttpModelDirectory
import ai.dana.data.network.danaHttpClient
import ai.dana.data.network.privateLanClient
import ai.dana.data.provider.EngineRepository
import ai.dana.data.provider.KeystoreVault
import ai.dana.domain.DanaAiRouter
import ai.dana.domain.EngineDiscovery
import ai.dana.domain.EmailAccounts
import ai.dana.domain.EmailProject
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
import java.util.concurrent.TimeUnit
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
    fun preferences(@ApplicationContext context: Context): PrivacyPreferences = PreferencesStore(context)

    @Provides
    @Singleton
    fun network(@ApplicationContext context: Context): NetworkMonitor = AndroidNetworkMonitor(context)

    private val client by lazy { danaHttpClient() }
    private val lanClient by lazy { client.privateLanClient() }

    @Provides
    @Singleton
    fun emailAccounts(@ApplicationContext context: Context, preferences: PrivacyPreferences): EmailAccounts {
        val fixed = EmailProject.parse(BuildConfig.EMAIL_PROJECT_URL, BuildConfig.EMAIL_PUBLISHABLE_KEY)
        val auth = SupabaseEmailApi(danaHttpClient().newBuilder().callTimeout(25, TimeUnit.SECONDS).build())
        return StoredEmailAccounts(context, fixed, preferences, auth)
    }

    @Provides
    @Singleton
    fun discovery(preferences: PrivacyPreferences, providers: ProviderRepository): EngineDiscovery =
        EngineDiscovery(preferences, providers, OkHttpModelDirectory(client, lanClient))

    @Provides
    @Singleton
    fun router(preferences: PrivacyPreferences, providers: ProviderRepository): DanaAiRouter =
        DanaAiRouter(preferences, providers, OkHttpEngineTransport(client, lanClient))
}
