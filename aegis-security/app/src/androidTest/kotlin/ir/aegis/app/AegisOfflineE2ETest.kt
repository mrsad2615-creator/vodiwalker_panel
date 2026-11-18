package ir.aegis.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.aegis.core.model.SecurityReminder
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AegisOfflineE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun apkHasZeroInternetPermissionsAndOfflineWorkManagerWorks() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse("App must NEVER declare INTERNET permission", "android.permission.INTERNET" in perms)
        assertFalse("App must NEVER declare ACCESS_NETWORK_STATE", "android.permission.ACCESS_NETWORK_STATE" in perms)
        assertFalse("App must NEVER declare ACCESS_WIFI_STATE", "android.permission.ACCESS_WIFI_STATE" in perms)

        // Verify offline WorkManager scheduling works without ACCESS_NETWORK_STATE
        val app = ctx.applicationContext as AegisApplication
        val rem = SecurityReminder(
            id = "e2e-rem-1",
            title = "Offline Kit Check",
            category = "Kit",
            dueAtMillis = System.currentTimeMillis() + 3_600_000L,
            recurrenceDays = 0,
            enabled = true,
            completed = false,
            createdAtMillis = System.currentTimeMillis(),
        )
        app.container.reminderScheduler.scheduleReminder(rem, redactContent = true)
        app.container.reminderScheduler.cancelReminder(rem.id)
    }

    @Test
    fun offlineDashboardLanguageToggleAndModuleFlowsWork() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AegisGuard").assertIsDisplayed()
        composeRule.onNodeWithText("100% OFFLINE · NO CLOUD").assertIsDisplayed()

        // Switch EN -> FA (RTL) and back to EN
        composeRule.onNodeWithText("فا").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("شاخص آمادگی آفلاین").assertIsDisplayed()
        composeRule.onNodeWithText("EN").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Offline Readiness Index").assertIsDisplayed()

        // Navigate into Emergency Response Center and verify SOS panel
        composeRule.onNodeWithTag("quick_actions_row").performScrollToNode(hasText("Quick SOS"))
        composeRule.onNodeWithText("Quick SOS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("OFFLINE SOS RESPONSE PANEL").assertIsDisplayed()
        composeRule.onNodeWithText("←").performClick()
        composeRule.waitForIdle()

        // Navigate into Local AI and execute an offline BM25 query
        composeRule.onNodeWithTag("quick_actions_row").performScrollToNode(hasText("Local AI"))
        composeRule.onNodeWithText("Local AI").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("earthquake").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_assistant_list").performScrollToIndex(2)
        composeRule.onAllNodesWithText("Referenced Reviewed Protocols:").onFirst().performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("←").performClick()
        composeRule.waitForIdle()

        // Navigate into Crisis Management and register a real offline incident
        composeRule.onNodeWithTag("quick_actions_row").performScrollToNode(hasText("Log Incident"))
        composeRule.onNodeWithText("Log Incident").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Register New").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Incident Title").performTextInput("Airplane E2E Drill")
        composeRule.onNodeWithText("Create Incident + Auto Checklist").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Airplane E2E Drill").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Airplane E2E Drill").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("←").performClick()
        composeRule.waitForIdle()

        // Scroll Home LazyColumn to Privacy & Security module card and verify zero-network permission audit
        composeRule.onNodeWithTag("home_lazy_list").performScrollToNode(hasText("Privacy & Security"))
        composeRule.onNodeWithText("Privacy & Security").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Audit & Perms").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("• INTERNET Permission: ABSENT BY DESIGN").assertIsDisplayed()
    }
}
