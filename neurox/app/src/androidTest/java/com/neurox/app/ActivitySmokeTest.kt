package com.neurox.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real manifest Activity wiring. This test makes no assumption about saved history. */
@RunWith(AndroidJUnit4::class)
class ActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun realActivityOpensSettingsFromItsVisibleControl() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("تنظیمات").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("تنظیمات").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("مطابق با شما").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("مطابق با شما").assertIsDisplayed()
    }
}
