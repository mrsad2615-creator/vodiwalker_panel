package ai.dana.ui

import ai.dana.MainActivity
import ai.dana.R
import ai.dana.data.di.EngineClientModule
import ai.dana.data.di.ManagedCloudModule
import ai.dana.data.network.EngineClients
import ai.dana.domain.AccountFailure
import ai.dana.domain.AccountIdentity
import ai.dana.domain.AccountProblem
import ai.dana.domain.AccountSetup
import ai.dana.domain.ArtImage
import ai.dana.domain.ArtShape
import ai.dana.domain.ConversationInput
import ai.dana.domain.EmailAccounts
import ai.dana.domain.LocalAction
import ai.dana.domain.ManagedCatalog
import ai.dana.domain.ManagedCloud
import ai.dana.domain.ManagedKind
import ai.dana.domain.ManagedModel
import ai.dana.domain.PhoneIntent
import ai.dana.domain.PrivacyPreferences
import ai.dana.domain.SignOutResult
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The installed UI uses a single account/session and only the test APK substitutes the auth and model services. */
@OptIn(ExperimentalTestApi::class)
@UninstallModules(ManagedCloudModule::class, EngineClientModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HostedWorkspaceTest {
    private val email = IsolatedEmail()
    private val api = IsolatedCloud(email)
    @BindValue @JvmField val accounts: EmailAccounts = email
    @BindValue @JvmField val cloud: ManagedCloud = api
    @BindValue @JvmField val engines = EngineClients(OkHttpClient.Builder().addInterceptor { error("A personal provider must not be contacted") }.build())
    @Inject lateinit var privacy: PrivacyPreferences
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>(effectContext = StandardTestDispatcher())
    private val ui get() = WorkflowUi(compose)

    @Test fun sameEmailUnlocksChatArtAndReviewOnlyCommandWithoutSeparateSetup() {
        hilt.inject()
        val wasOffline = runBlocking { privacy.offlineMode.first() }
        try {
            ui.click(R.string.managed_login)
            ui.enter(R.string.account_email, ADDRESS, scroll = true)
            ui.click(R.string.account_send, scroll = true)
            ui.enter(R.string.account_code, "۰۰۰۰۰۰", scroll = true)
            ui.click(R.string.account_verify, scroll = true)
            ui.visible(R.string.account_error_code, scroll = true)
            assertTrue(api.calls.isEmpty())
            ui.enter(R.string.account_code, "۸۵۹۲۷۱", scroll = true)
            ui.click(R.string.account_verify, scroll = true)
            compose.waitUntilAtLeastOneExists(hasTestTag("managed-picker"), timeoutMillis = 15_000)
            ui.click("Fixture · First", list = LIST)
            ui.click("Fixture · Second")
            ui.enter(R.string.chat_prompt, "کد تایید ۱۲۳۴۵۶ را تکرار نکن", list = LIST)
            ui.click(R.string.workspace_send)
            compose.onNodeWithTag("managed-outbound").assertTextEquals("کد تایید [محفوظ] را تکرار نکن")
            assertTrue(api.calls.isEmpty())
            ui.click(R.string.cancel)
            assertTrue(api.calls.isEmpty())
            ui.click(R.string.workspace_send)
            ui.click(R.string.message_send_once)
            ui.visible(ANSWER, list = LIST)
            assertEquals(listOf("chat:sample/second"), api.calls.toList())

            ui.click(DashboardTab.ART.label)
            ui.enter(R.string.art_prompt, "ساخت تصویر باغ بارانی", list = LIST)
            ui.click(R.string.art_generate)
            compose.onNodeWithTag("managed-outbound").assertTextEquals("ساخت تصویر باغ بارانی")
            ui.click(R.string.message_send_once)
            val preview = hasContentDescription(ui.context.getString(R.string.art_preview))
            compose.waitUntilAtLeastOneExists(preview, timeoutMillis = 15_000)
            ui.find(preview, list = LIST)
            assertEquals(listOf("chat:sample/second", "image:fixture-picture"), api.calls.toList())

            ui.click(R.string.tab_phone)
            Intents.init()
            try {
                ui.enter(R.string.workspace_phone_prompt, COMPLEX, scroll = true)
                ui.click(R.string.workspace_run, scroll = true)
                ui.click(R.string.managed_command_button, scroll = true)
                compose.onNodeWithTag("managed-command-outbound").assertTextEquals(COMPLEX)
                ui.click(R.string.cancel)
                assertEquals(2, api.calls.size)
                ui.click(R.string.managed_command_button, scroll = true)
                ui.click(R.string.message_send_once)
                ui.visible(ui.context.getString(R.string.chat_action_timer, 35))
                assertTrue(Intents.getIntents().isEmpty())
                ui.click(R.string.cancel)
                assertEquals(listOf("chat:sample/second", "image:fixture-picture", "command:sample/first"), api.calls.toList())
                assertTrue(Intents.getIntents().isEmpty())
            } finally { Intents.release() }
            assertEquals(1, email.sent)
            assertEquals(1, email.verified)
            ui.noMicrophone()
        } finally { runBlocking { privacy.setOfflineMode(wasOffline) } }
    }

    private class IsolatedEmail : EmailAccounts {
        override val setup = MutableStateFlow(AccountSetup("auth.example.test", editable = false, canResume = false))
        var sent = 0
        var verified = 0
        override suspend fun configure(origin: String, publishableKey: String) = error("Fixed issuer")
        override suspend fun removeProject() = error("Fixed issuer")
        override suspend fun requestCode(email: String) { assertEquals(ADDRESS, email); sent++ }
        override suspend fun verify(email: String, code: String): AccountIdentity {
            assertEquals(ADDRESS, email)
            assertEquals(1, sent)
            if (code != "859271") throw AccountFailure(AccountProblem.INVALID_CODE)
            verified++
            setup.value = setup.value.copy(canResume = true)
            return AccountIdentity(email)
        }
        override suspend fun resume() = if (setup.value.canResume) AccountIdentity(ADDRESS) else throw AccountFailure(AccountProblem.EXPIRED_SESSION)
        override suspend fun signOut(): SignOutResult {
            setup.value = setup.value.copy(canResume = false)
            return SignOutResult.REVOKED
        }
    }

    private class IsolatedCloud(private val auth: IsolatedEmail) : ManagedCloud {
        override val configured = true
        val calls = CopyOnWriteArrayList<String>()
        override suspend fun catalog(): ManagedCatalog {
            auth.resume()
            return ManagedCatalog(listOf(FIRST, SECOND, ART), FIRST, 12)
        }
        override suspend fun chat(model: ManagedModel, input: ConversationInput, requestId: String): String {
            auth.resume()
            assertEquals(SECOND, model)
            assertEquals("کد تایید [محفوظ] را تکرار نکن", input.prompt)
            assertTrue(input.history.isEmpty() && input.facts.isEmpty())
            calls += "chat:${model.id}"
            return ANSWER
        }
        override suspend fun image(model: ManagedModel, prompt: String, shape: ArtShape, requestId: String): ArtImage {
            auth.resume()
            assertEquals(ART, model)
            assertEquals("ساخت تصویر باغ بارانی", prompt)
            calls += "image:${model.id}"
            return ByteArrayOutputStream().use { stream ->
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) } finally { bitmap.recycle() }
                checkNotNull(ArtImage.create("image/png", stream.toByteArray()))
            }
        }
        override suspend fun command(model: ManagedModel, prompt: String, requestId: String): PhoneIntent {
            auth.resume()
            assertEquals(FIRST, model)
            assertEquals(COMPLEX, prompt)
            calls += "command:${model.id}"
            return PhoneIntent.Propose(LocalAction.Timer(35))
        }
    }

    private companion object {
        const val ADDRESS = "pilot@example.test"
        const val LIST = "managed-workspace-list"
        const val ANSWER = "پاسخ مدل منتخب بدون کلید شخصی"
        const val COMPLEX = "برای دم کشیدن چای، هر وقت ۳۵ ثانیه گذشت به من خبر بده"
        val FIRST = ManagedModel("sample/first", "First", "Fixture", ManagedKind.CHAT, 1, null)
        val SECOND = ManagedModel("sample/second", "Second", "Fixture", ManagedKind.CHAT, 1, null)
        val ART = ManagedModel("fixture-picture", "Picture", "Fixture", ManagedKind.IMAGE, 5, "2K")
    }
}
