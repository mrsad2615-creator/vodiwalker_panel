package ai.dana.data.di

import ai.dana.data.network.EngineClients
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineClientModule {
    @Provides @Singleton
    fun engines(): EngineClients = EngineClients()
}
