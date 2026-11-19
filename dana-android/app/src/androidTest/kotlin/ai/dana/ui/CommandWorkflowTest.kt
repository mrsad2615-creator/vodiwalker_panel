package ai.dana.ui

import ai.dana.MainActivity
import ai.dana.R
import android.Manifest
import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CommandWorkflowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>(effectContext = StandardTestDispatcher())
    private val ui get() = WorkflowUi(compose)

    @Test fun typingAndFinalVoiceTextShareClarificationAndOneShotClockReview() {
        ui.click(R.string.tab_phone)
        Intents.init()
        try {
            ui.enter(R.string.workspace_phone_prompt, "یه تایمر بذار", scroll = true)
            ui.click(R.string.workspace_run, scroll = true)
            ui.visible(R.string.phone_ask_timer_duration, scroll = true)
            val activity = compose.activity
            val phone = compose.runOnIdle { ViewModelProvider(activity)[PhoneViewModel::class.java] }
            // Inject only the final transcript at the same entry point as SpeechRecognizer. No mic or ASR required.
            compose.runOnIdle {
                phone.speech("دو دقیقه و سی ثانیه") { error("Speech may propose, not launch a timer.") }
            }
            ui.visible(ui.context.getString(R.string.chat_action_timer, 150))
            ui.click(R.string.cancel)
            assertTrue(Intents.getIntents().isEmpty())

            ui.enter(R.string.workspace_phone_prompt, "بعد از یک ربع، وای‌فای رو قطع کن", scroll = true)
            ui.click(R.string.workspace_run, scroll = true)
            ui.visible(R.string.phone_wifi_reminder_review)
            val timer = allOf(hasAction(AlarmClock.ACTION_SET_TIMER), hasExtra(AlarmClock.EXTRA_LENGTH, 900),
                hasExtra(AlarmClock.EXTRA_SKIP_UI, false), hasExtra(AlarmClock.EXTRA_MESSAGE, ui.context.getString(R.string.phone_wifi_label_off)))
            Intents.intending(timer).respondWith(ActivityResult(Activity.RESULT_OK, null))
            val approval = checkNotNull(phone.state.value.pending)
            assertTrue(Intents.getIntents().isEmpty())
            ui.click(R.string.phone_submit_clock)
            Intents.intended(timer)
            ui.visible(R.string.phone_wifi_clock_opened, scroll = true)
            compose.runOnIdle { phone.confirm(approval) { error("An already used approval must be inert.") } }

            ui.enter(R.string.workspace_phone_prompt, "الان وای‌فای رو خاموش کن", scroll = true)
            ui.click(R.string.workspace_run, scroll = true)
            ui.visible(R.string.phone_wifi_manual_review)
            val wifi = hasAction(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)
            Intents.intending(wifi).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
            assertEquals(1, Intents.getIntents().size)
            ui.click(R.string.chat_action_wifi)
            Intents.intended(wifi)
            ui.visible(R.string.phone_wifi_manual_opened, scroll = true)
            assertEquals(2, Intents.getIntents().size)
        } finally { Intents.release() }
        ui.noMicrophone()
    }

    @Test fun assistantEntryRequestsAndroidConsentFromCommandAndDeniedReturnKeepsLocalTools() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_ASSIST)
                .putExtra("untrusted_foreground_text", "چراغ قوه را روشن کن"))
        }
        ui.visible(R.string.workspace_phone_title, scroll = true)
        compose.onAllNodes(hasText(ui.context.getString(R.string.chat_confirm_action))).assertCountEquals(0)
        ui.noMicrophone()
        val role = if (Build.VERSION.SDK_INT >= 29) requestRole(compose.activity) else null
        val system = hasAction(role?.action ?: Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        Intents.init()
        try {
            Intents.intending(system).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
            ui.click(R.string.phone_assistant_enable, scroll = true)
            Intents.intended(system)
            if (role != null) ui.visible(R.string.workspace_assistant_not_selected, scroll = true)
            compose.onAllNodes(hasText(ui.context.getString(R.string.phone_assistant_active))).assertCountEquals(0)
        } finally { Intents.release() }
        ui.enter(R.string.workspace_phone_prompt, "میشه بهم بگی چی کار کنم؟", scroll = true)
        ui.click(R.string.workspace_run, scroll = true)
        ui.visible(R.string.workspace_unrecognized, scroll = true)
        ui.noMicrophone()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class WorkflowUi(private val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
    val context get() = compose.activity.applicationContext
    private fun text(id: Int) = context.getString(id)
    fun visible(id: Int, scroll: Boolean = false) = visible(text(id), scroll)
    fun visible(value: String, scroll: Boolean = false, list: String? = null) {
        find(hasText(value), scroll, list)
    }
    fun click(id: Int, scroll: Boolean = false, list: String? = null) = click(text(id), scroll, list)
    fun click(value: String, scroll: Boolean = false, list: String? = null) {
        val node = find(hasText(value) and hasClickAction(), scroll, list).assertIsEnabled()
        node.performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
    }
    fun enter(id: Int, value: String, scroll: Boolean = false, list: String? = null) {
        find(hasText(text(id)) and hasSetTextAction(), scroll, list).performTextReplacement(value)
    }
    fun find(matcher: SemanticsMatcher, scroll: Boolean = false, list: String? = null): androidx.compose.ui.test.SemanticsNodeInteraction {
        if (list != null) {
            compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag(list), timeoutMillis = 15_000)
            compose.onNodeWithTag(list).performScrollToNode(matcher)
        }
        compose.waitUntilAtLeastOneExists(matcher, timeoutMillis = 15_000)
        return compose.onNode(matcher).also { if (scroll) it.performScrollTo() }.assertIsDisplayed()
    }
    fun noMicrophone() = assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
}
