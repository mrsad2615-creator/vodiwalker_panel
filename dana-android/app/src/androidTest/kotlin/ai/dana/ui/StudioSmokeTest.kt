package ai.dana.ui

import ai.dana.MainActivity
import ai.dana.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the exact installed preview; all input is synthetic and stays offline. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StudioSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeAccountConnectionAndSharedNoteWorkOnFreshInstall() {
        trace("Activity resumed; wait for Studio semantics")
        visible(R.string.studio_hello)
        visible(R.string.hub_heading)
        capture("home")

        click(R.string.tab_account)
        visible(R.string.account_email_title)
        visible(R.string.account_configure)
        capture("account")
        click(R.string.account_configure)
        enter(R.string.account_project_url, "https://abcdefghijklmnopqrst.supabase.co")
        enter(R.string.account_public_key, "sb_publishable_dana-ci-public-fixture")
        click(R.string.save)
        visible(R.string.account_email_title)
        enter(R.string.account_email, "fixture@dana.example.test")
        compose.onNodeWithText(label(R.string.account_send)).assertIsNotEnabled()
        // The bottom bar intentionally hides while the keyboard is open.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        click(R.string.tab_engines)
        visible(R.string.engines_title)
        visible(R.string.quick_title)
        capture("engines")
        click(R.string.quick_title)
        visible(R.string.quick_key)
        visible(R.string.quick_browser)
        compose.onNodeWithText(label(R.string.quick_connect)).assertIsNotEnabled()
        enter(R.string.quick_key, "sk-or-v1-dana-ci-offline-fixture")
        compose.onNodeWithText(label(R.string.quick_connect)).assertIsEnabled()
        click(R.string.cancel)
        trace("Quick connection form validates without contacting any provider")

        click(R.string.tab_home)
        click(R.string.art_title)
        visible(R.string.art_need_engine)
        capture("art")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        click(R.string.tab_compare)
        visible(R.string.compare_heading)
        visible(R.string.compare_offline)

        // Exercise Android's public intent path, not a direct call into the ViewModel.
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(Intent(activity, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(Intent.EXTRA_TEXT, "note DANA_SMOKE_LOCAL")
            })
        }
        visible("note DANA_SMOKE_LOCAL")
        compose.onNodeWithText(label(R.string.chat_note_saved)).assertDoesNotExist()
        capture("chat")
        click(R.string.chat_review)
        visible(R.string.chat_confirm_action)
        click(R.string.chat_execute)
        visible(R.string.chat_note_saved)
        click(R.string.tab_home)
        click(R.string.tab_notes)
        visible("DANA_SMOKE_LOCAL")
    }

    @Test
    fun assistantEntryIgnoresExtrasAndRunsReadOnlyCommandsOffline() {
        val assist = Intent(Intent.ACTION_ASSIST).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(assist, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.name == MainActivity::class.java.name })
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(assist.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Intent.EXTRA_ASSIST_CONTEXT, Bundle().apply { putString("fixture", "note DO_NOT_IMPORT") }))
        }
        visible(R.string.voice_record_turn)
        compose.onNodeWithText("note DO_NOT_IMPORT").assertDoesNotExist()
        assertTrue(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
        val battery = checkNotNull(context.getSystemService(BatteryManager::class.java))
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        assertTrue(percent in 0..100)
        val result = context.getString(if (battery.isCharging) R.string.phone_battery_charging else R.string.phone_battery_result, faDigits(percent))
        enter(R.string.chat_prompt, "شارژ گوشی چقدره؟")
        click(R.string.chat_review)
        visible(result)
        compose.onNodeWithText(label(R.string.chat_confirm_action)).assertDoesNotExist()
        enter(R.string.chat_prompt, "فرمان ناشناس محلی")
        click(R.string.chat_review)
        visible(R.string.phone_unrecognized)
        enter(R.string.chat_prompt, "جستجوی وب عبارت آزمایشی")
        click(R.string.chat_review)
        visible(R.string.phone_web_offline)
        trace("Assistant entry, direct battery read and offline guards passed")
    }

    private fun label(@StringRes id: Int) = context.getString(id)
    private fun visible(@StringRes id: Int) = visible(label(id))
    private fun visible(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), timeoutMillis = 12_000)
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun click(@StringRes id: Int) {
        val click = hasText(label(id)) and hasClickAction()
        compose.waitUntilAtLeastOneExists(click, timeoutMillis = 12_000)
        compose.onNode(click).assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun enter(@StringRes id: Int, value: String) {
        val enter = hasText(label(id)) and hasSetTextAction()
        compose.waitUntilAtLeastOneExists(enter, timeoutMillis = 12_000)
        compose.onNode(enter).assertIsDisplayed().performTextReplacement(value)
    }

    private fun trace(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "\nDana smoke: $message\n")
        })
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        trace("Capture $name")
        val directory = File(context.filesDir, "ci-screens").apply { mkdirs() }
        val capture = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p")
        // Capture the actual screen. PixelCopy on Compose windows destabilized the headless emulator.
        ParcelFileDescriptor.AutoCloseInputStream(capture).use { input ->
            File(directory, "$name.png").outputStream().use { output -> assertTrue(input.copyTo(output) > 24) }
        }
        trace("Captured $name")
    }
}
