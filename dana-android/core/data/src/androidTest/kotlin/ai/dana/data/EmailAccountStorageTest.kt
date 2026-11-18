package ai.dana.data

import ai.dana.data.account.StoredEmailAccounts
import ai.dana.data.account.SupabaseEmailApi
import ai.dana.data.network.danaHttpClient
import ai.dana.data.provider.KeystoreVault
import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountProblem
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.SignOutResult
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailAccountStorageTest {
    @Test
    fun offlineSkipsRequestsAndVerifiedRefreshRotatesUnderRealAndroidKeystore() {
        runBlocking {
            withTimeout(20_000) {
                val id = UUID.randomUUID().toString()
                val alias = "dana.test.account.$id"
                val context = ApplicationProvider.getApplicationContext<Context>()
                val file = File(context.cacheDir, "$id.preferences_pb")
                val scope = Job()
                val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + scope)) { file }
                val prefs = object : PrivacyPreferences {
                    override val offlineMode = MutableStateFlow(true)
                    override suspend fun setOfflineMode(enabled: Boolean) { offlineMode.value = enabled }
                }
                val total = AtomicInteger()
                val json = "application/json".toMediaType()
                val transport = danaHttpClient().newBuilder().addInterceptor { chain ->
                    val n = total.incrementAndGet()
                    val req = chain.request()
                    check(req.url.host == "abcdefghijklmnopqrst.supabase.co")
                    val payload = when (req.url.encodedPath) {
                        "/auth/v1/otp", "/auth/v1/logout" -> "{}"
                        "/auth/v1/verify", "/auth/v1/token" -> """{"access_token":"access-test","refresh_token":"session-refresh-test-$n","token_type":"bearer","user":{"email":"someone@example.test","email_confirmed_at":"2026-11-17T00:00:00Z"}}"""
                        else -> error("Unexpected endpoint")
                    }
                    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic fixture")
                        .body(payload.toResponseBody(json)).build()
                }.build()
                fun accounts() = StoredEmailAccounts(context, null, prefs, SupabaseEmailApi(transport), store, KeystoreVault(alias))
                try {
                    val account = accounts()
                    val email = "someone@example.test"
                    val entry = stringPreferencesKey("protected_refresh")
                    account.configure("https://abcdefghijklmnopqrst.supabase.co", "sb_publishable_public_test_123456")
                    try { account.requestCode(email); error("Offline allowed a request") }
                    catch (failure: AccountFailure) { assertEquals(AccountProblem.OFFLINE, failure.problem) }
                    assertEquals(0, total.get())
                    prefs.setOfflineMode(false)
                    account.requestCode(email)
                    assertFalse(account.setup.first().canResume)
                    assertEquals(email, account.verify(email, "۱۲۳۴۵۶").email)
                    val first = checkNotNull(store.data.first()[entry])
                    assertTrue(first.startsWith("v1."))
                    assertFalse(first.contains("session-refresh-test"))
                    assertTrue(account.setup.first().canResume)
                    val reopened = accounts()
                    assertEquals(email, reopened.resume().email)
                    assertNotEquals(first, store.data.first()[entry])
                    assertEquals(SignOutResult.REVOKED, reopened.signOut())
                    assertEquals(5, total.get())
                    assertFalse(reopened.setup.first().canResume)
                    assertEquals(null, store.data.first()[entry])
                } finally {
                    scope.cancelAndJoin()
                    file.delete()
                    transport.connectionPool.evictAll()
                    transport.dispatcher.executorService.shutdown()
                    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
                }
            }
        }
    }
}
