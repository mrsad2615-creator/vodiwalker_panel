package ir.aegis.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AegisOfflineE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun apkHasZeroInternetPermissions() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse("App must NEVER declare INTERNET permission", "android.permission.INTERNET" in perms)
        assertFalse("App must NEVER declare ACCESS_NETWORK_STATE", "android.permission.ACCESS_NETWORK_STATE" in perms)
    }

    @Test
    fun offlineDashboardLanguageToggleAndModuleFlowsWork() {
        composeRule.onNodeWithText("AegisGuard").assertIsDisplayed()
        composeRule.onNodeWithText("100% OFFLINE · NO CLOUD").assertIsDisplayed()

        // Switch EN -> FA (RTL) and back to EN
        composeRule.onNodeWithText("فا").performClick()
        composeRule.onNodeWithText("شاخص آمادگی آفلاین").assertIsDisplayed()
        composeRule.onNodeWithText("EN").performClick()
        composeRule.onNodeWithText("Offline Readiness Index").assertIsDisplayed()

        // Navigate into Emergency Response Center and activate / deactivate SOS screen state
        composeRule.onNodeWithText("Quick SOS").performClick()
        composeRule.onNodeWithText("OFFLINE SOS RESPONSE PANEL").assertIsDisplayed()
        composeRule.onNodeWithText("←").performClick()

        // Navigate into Local AI and execute an offline BM25 query
        composeRule.onNodeWithText("Local AI").performClick()
        composeRule.onNodeWithText("earthquake").performClick()
        composeRule.onAllNodesWithText("Referenced Reviewed Protocols:").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("←").performClick()

        // Navigate into Crisis Management and register a real offline incident
        composeRule.onNodeWithText("Log Incident").performClick()
        composeRule.onNodeWithText("Register New").performClick()
        composeRule.onNodeWithText("Incident Title").performTextInput("Airplane E2E Drill")
        composeRule.onNodeWithText("Create Incident + Auto Checklist").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Airplane E2E Drill").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("←").performClick()

        // Verify Privacy & Security confirms zero-network permission audit
        composeRule.onNodeWithText("Privacy & Security").performScrollTo().performClick()
        composeRule.onNodeWithText("Audit & Perms").performClick()
        composeRule.onNodeWithText("• INTERNET Permission: ABSENT BY DESIGN").assertIsDisplayed()
    }
}
