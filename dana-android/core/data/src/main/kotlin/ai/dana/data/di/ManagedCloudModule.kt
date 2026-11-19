package ai.dana.data.di

import ai.dana.data.BuildConfig
import ai.dana.data.account.StoredEmailAccounts
import ai.dana.data.account.SupabaseEmailApi
import ai.dana.data.network.ManagedAuthorization
import ai.dana.data.network.OkHttpManagedCloud
import ai.dana.data.network.danaHttpClient
import ai.dana.domain.EmailAccounts
import ai.dana.domain.EmailProject
import ai.dana.domain.ManagedCloud
import ai.dana.domain.PrivacyPreferences
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Module
@InstallIn(SingletonComponent::class)
object ManagedCloudModule {
    @Provides @Singleton
    internal fun storedAccounts(@ApplicationContext context: Context, preferences: PrivacyPreferences): StoredEmailAccounts {
        val issuer = EmailProject.parse(BuildConfig.EMAIL_PROJECT_URL, BuildConfig.EMAIL_PUBLISHABLE_KEY)
        val http = danaHttpClient().newBuilder().callTimeout(25, TimeUnit.SECONDS).build()
        return StoredEmailAccounts(context, issuer, preferences, SupabaseEmailApi(http))
    }

    @Provides
    internal fun emailAccounts(accounts: StoredEmailAccounts): EmailAccounts = accounts

    @Provides @Singleton
    internal fun managedCloud(accounts: StoredEmailAccounts): ManagedCloud {
        val http = danaHttpClient().newBuilder().callTimeout(142, TimeUnit.SECONDS).readTimeout(132, TimeUnit.SECONDS).build()
        return OkHttpManagedCloud(BuildConfig.MANAGED_GATEWAY_URL.toHttpUrlOrNull(), object : ManagedAuthorization {
            override suspend fun <T> withAccess(operation: suspend (String) -> T): T = accounts.withManagedAccess(operation)
        }, http)
    }
}
