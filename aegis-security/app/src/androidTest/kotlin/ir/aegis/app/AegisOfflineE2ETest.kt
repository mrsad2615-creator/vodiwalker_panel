package ir.aegis.app

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.aegis.core.maps.VectorMapEngine
import ir.aegis.core.model.SecurityReminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AegisOfflineE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun apkHasZeroInternetPermissionsAndMapAssetsAndWorkManagerWork() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse("App must NEVER declare INTERNET permission", "android.permission.INTERNET" in perms)
        assertFalse("App must NEVER declare ACCESS_NETWORK_STATE", "android.permission.ACCESS_NETWORK_STATE" in perms)
        assertFalse("App must NEVER declare ACCESS_WIFI_STATE", "android.permission.ACCESS_WIFI_STATE" in perms)

        val atlas = VectorMapEngine.loadIndexedBaseAtlas()
        assertTrue("Atlas countries failed to load on Android", atlas.countries.size >= 150)
        assertTrue("Iran provinces failed to load on Android", atlas.iranProvinces.size >= 28)
        assertTrue("Tehran city pack failed to load on Android", VectorMapEngine.loadBundle("pack-tehran-grid").roads.isNotEmpty())

        val app = ctx.applicationContext as AegisApplication
        val rem = SecurityReminder(
            id = "e2e-rem-1",
            title = "Offline Vault Key Check",
            category = "Security",
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
    fun offlinePersianDefaultBentoDashboardFullScreenMapAntiHackScannerPhishingAndPermsWork() {
        composeRule.waitForIdle()
        // Default primary language is Persian (RTL)
        composeRule.onNodeWithText("ایجیس‌گارد").assertIsDisplayed()
        composeRule.onNodeWithText("کاملاً آفلاین").assertIsDisplayed()

        // Toggle FA -> EN -> FA cleanly
        composeRule.onNodeWithText("EN").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("System Posture").assertIsDisplayed()
        composeRule.onNodeWithText("فا").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("وضعیت کلی امنیت").assertIsDisplayed()

        // Switch to EN for deterministic label assertions across sub-screens
        composeRule.onNodeWithText("EN").performClick()
        composeRule.waitForIdle()

        // 1. Full-Screen Multi-Scale Offline Map + All Iran / World + Directory Routing
        composeRule.onNodeWithText("Map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("All Iran").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("World").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Directory", substring = true).onFirst().performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Route on Map").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Route on Map").onFirst().performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("min", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("min", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // 2. Anti-Hack OS Inspector
        composeRule.onNodeWithText("Anti-Hack").performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Real-Time Hardware & OS Hardening Audit").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Real-Time Hardware & OS Hardening Audit").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // 3. Antivirus / App Malware Scanner
        composeRule.onNodeWithText("Scanner").performClick()
        composeRule.waitUntil(8_000L) {
            composeRule.onAllNodesWithText("Antivirus & APK Heuristic Engine").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Antivirus & APK Heuristic Engine").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // 4. Phishing Analyzer & Password Lab
        composeRule.onNodeWithText("Phishing").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_tab_lazy_list").performScrollToNode(hasText("MALICIOUS PHISHING", substring = true))
        composeRule.onAllNodesWithText("MALICIOUS PHISHING", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // 5. Security Incident Log
        composeRule.onNodeWithTag("quick_actions_row").performScrollToNode(hasText("Incidents"))
        composeRule.onNodeWithText("Incidents").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Register New").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Incident Title").performTextInput("Airplane E2E Security Drill")
        composeRule.onNodeWithText("Create Incident + Auto Checklist").performScrollTo().performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("Airplane E2E Security Drill").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Airplane E2E Security Drill").onFirst().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        // 6. Permission Auditor + Zero Network Verification
        composeRule.onNodeWithTag("quick_actions_row").performScrollToNode(hasText("Permissions"))
        composeRule.onNodeWithText("Permissions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Audit & Perms").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Zero-network design verified", substring = true).onFirst().assertIsDisplayed()
    }
}
