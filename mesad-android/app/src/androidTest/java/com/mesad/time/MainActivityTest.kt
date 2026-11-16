package com.mesad.time

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun homeScreenDisplaysMESADAndHeroClock() {
        composeRule.onNodeWithText("MESAD").assertIsDisplayed()
        composeRule.onNodeWithText("LOCAL TIME").assertIsDisplayed()
    }

    @Test fun alarmScreenCanBeOpened() {
        composeRule.onNodeWithText("Alarm").performClick()
        composeRule.onNodeWithText("Reliable, repeatable, yours.").assertIsDisplayed()
    }
}
