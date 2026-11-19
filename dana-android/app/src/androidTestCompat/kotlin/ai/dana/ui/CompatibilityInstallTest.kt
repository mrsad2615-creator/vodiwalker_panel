package ai.dana.ui

import ai.dana.MainActivity
import ai.dana.MessageReviewActivity
import ai.dana.R
import ai.dana.data.di.EngineClientModule
import ai.dana.data.network.EngineClients
import ai.dana.localai.DeviceModels
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@UninstallModules(EngineClientModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CompatibilityInstallTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = AtomicInteger()
    private val homeLabel get() = if (InstrumentationRegistry.getArguments().getString("expectedHosted") == "true")
        R.string.managed_login else R.string.models_compat_summary
    @BindValue @JvmField val engines = EngineClients(OkHttpClient.Builder().addInterceptor {
        calls.incrementAndGet()
        error("No HTTP request is allowed in the compatibility smoke test")
    }.build())
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>(effectContext = StandardTestDispatcher())

    @Test fun installedPreviewStartsOnRequestedApiAndRunsLocalBatteryTool() {
        val arguments = InstrumentationRegistry.getArguments()
        arguments.getString("expectedApi")?.let { assertEquals(it.toInt(), Build.VERSION.SDK_INT) }
        arguments.getString("expected64Bit")?.let { assertEquals(it.toBooleanStrict(), Process.is64Bit()) }
        assertEquals("ai.dana.mrsad.compat.command", context.packageName)
        assertFalse(DeviceModels.BUNDLED)
        assertTrue(runBlocking { DeviceModels(context).available().isEmpty() })
        assertTrue(context.assets.list("models").orEmpty().isEmpty())
        visible(homeLabel)
        compose.onNodeWithTag("local-qwen3-0.6b").assertDoesNotExist()
        compose.onNodeWithTag("local-smollm2-135m").assertDoesNotExist()
        click(R.string.tab_phone)
        click(R.string.phone_battery)
        val battery = checkNotNull(context.getSystemService(BatteryManager::class.java))
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        assertTrue(percent in 0..100)
        visible(context.getString(if (battery.isCharging) R.string.phone_battery_charging else R.string.phone_battery_result, faDigits(percent)))
        assertEquals(0, calls.get())
    }

    @Test fun sharesTextIntoIsolatedLocalReviewWithoutModelsAccountOrMic() {
        val source = "گزارش را فردا ساعت ۱۸:۳۰ آماده کنید."
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName).putExtra(Intent.EXTRA_TEXT, source)
        val handlers = context.packageManager.queryIntentActivities(share, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(listOf(MessageReviewActivity::class.java.name), handlers.map { it.activityInfo.name })
        compose.activityRule.scenario.onActivity { it.startActivity(share) }
        visible(R.string.message_title)
        visible(source)
        compose.onNodeWithText(context.getString(R.string.message_result)).assertDoesNotExist()
        val list = compose.onNodeWithTag("message-review-list")
        val local = hasText(context.getString(R.string.message_compat_scan)) and hasClickAction()
        list.performScrollToNode(local)
        compose.onNode(local).assertIsDisplayed().performClick()
        val clock = context.getString(R.string.message_clock, "۱۸:۳۰")
        list.performScrollToNode(hasText(clock))
        visible(clock)
        visible(R.string.message_compat_explain)
        assertEquals(0, calls.get())
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val offline = context.getString(R.string.message_offline)
        list.performScrollToNode(hasText(offline))
        visible(offline)
        compose.onNodeWithText(context.getString(R.string.message_online)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.studio_back)).performClick()
        visible(homeLabel)
    }

    private fun visible(id: Int) = visible(context.getString(id))
    private fun visible(value: String) {
        compose.waitUntilAtLeastOneExists(hasText(value), timeoutMillis = 15_000)
        compose.onNodeWithText(value).assertIsDisplayed()
    }
    private fun click(id: Int) {
        val matcher = hasText(context.getString(id)) and hasClickAction()
        compose.waitUntilAtLeastOneExists(matcher, timeoutMillis = 15_000)
        compose.onNode(matcher).assertIsDisplayed().performClick()
    }
}
