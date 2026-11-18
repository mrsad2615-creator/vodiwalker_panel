package com.vodiwalker.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vodiwalker.gesture.core.designsystem.theme.VodiGestureTheme
import com.vodiwalker.gesture.core.model.GestureCapture
import com.vodiwalker.gesture.core.model.GesturePoint
import com.vodiwalker.gesture.core.model.GestureStroke
import com.vodiwalker.gesture.core.model.PermissionStatusSnapshot
import com.vodiwalker.gesture.core.model.PredictionResult
import com.vodiwalker.gesture.feature.permissions.PermissionOnboardingScreen
import com.vodiwalker.gesture.feature.recorder.GestureRecorderScreen
import com.vodiwalker.gesture.feature.recorder.GestureRecorderUiState
import com.vodiwalker.gesture.service.overlay.GlobalOverlayRoot
import com.vodiwalker.gesture.service.overlay.GlobalOverlayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Jetpack Compose UI Test Suite verifying:
 * 1. Google Play Prominent Disclosure gating in [PermissionOnboardingScreen].
 * 2. Custom multi-sample touch recording & save-button gating in [GestureRecorderScreen].
 * 3. Global Glassmorphic Overlay edge-handle expansion, AI prediction chip selection, and dismissal.
 */
@RunWith(AndroidJUnit4::class)
class VodiGestureComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permissionFlowGatesAccessibilityStepUntilProminentDisclosureAccepted() {
        var disclosureAccepted = false
        composeRule.setContent {
            VodiGestureTheme(darkTheme = true, dynamicColor = false) {
                PermissionOnboardingScreen(
                    state = PermissionStatusSnapshot(
                        accessibilityEnabled = false,
                        prominentDisclosureAccepted = false,
                        notificationsGranted = false,
                        restrictedSettingsLikelyBlocking = true,
                        canPerformGesturesVerified = false,
                    ),
                    onAcceptDisclosure = { disclosureAccepted = true },
                    onOpenAccessibilitySettings = {},
                    onOpenAppInfoForRestrictedSettings = {},
                    onRequestNotifications = {},
                    onContinue = {},
                )
            }
        }

        // Continue button and Step 2 must remain disabled before user consents to disclosure
        composeRule.onNodeWithTag("continue_to_studio_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("step_accessibility_card").assertIsNotEnabled()
        composeRule.onNodeWithTag("restricted_settings_helper").assertIsDisplayed()

        // Open Prominent Disclosure modal and accept
        composeRule.onNodeWithTag("step_disclosure_card").performClick()
        composeRule.onNodeWithText("Accessibility Service Disclosure").assertIsDisplayed()
        composeRule.onNodeWithTag("accept_disclosure_button").performClick()
        assertTrue(disclosureAccepted)
    }

    @Test
    fun gestureRecorderCapturesCanvasSwipeAndEnablesSaveAfterThreeSamples() {
        val sampled = mutableListOf<GesturePoint>()
        var savedInvoked = false
        val readySample = GestureCapture(
            listOf(
                GestureStroke(
                    0,
                    listOf(GesturePoint(100f, 200f, 0L), GesturePoint(500f, 600f, 120L)),
                ),
            ),
        )

        composeRule.setContent {
            VodiGestureTheme(darkTheme = true, dynamicColor = false) {
                GestureRecorderScreen(
                    state = GestureRecorderUiState(
                        templateName = "Split Screen V",
                        recordedSamples = listOf(readySample, readySample, readySample),
                    ),
                    onNameChanged = {},
                    onToggleOrientation = {},
                    onSelectHaptic = {},
                    onPointSampled = sampled::add,
                    onStrokeFinished = {},
                    onCommitSample = {},
                    onReset = {},
                    onSave = { savedInvoked = true },
                )
            }
        }

        composeRule.onNodeWithTag("gesture_name_input").performTextInput(" Pro")
        composeRule.onNodeWithTag("cinematic_gesture_canvas").performTouchInput {
            swipe(start = Offset(120f, 180f), end = Offset(480f, 520f), durationMillis = 160L)
        }
        assertTrue("Expected pointer samples on canvas", sampled.size >= 2)

        composeRule.onNodeWithTag("save_gesture_button").assertIsEnabled().performClick()
        assertTrue(savedInvoked)
    }

    @Test
    fun globalOverlayDisplaysAiPredictionChipsAndDispatchesQuickSelection() {
        var pickedTemplateId: String? = null
        var dismissed = false

        composeRule.setContent {
            VodiGestureTheme(darkTheme = true, dynamicColor = false) {
                GlobalOverlayRoot(
                    state = GlobalOverlayUiState(
                        isVisible = true,
                        isCanvasExpanded = true,
                        topPredictions = listOf(
                            PredictionResult(
                                templateId = "screenshot_z",
                                templateName = "Screenshot Z",
                                fusedProbability = 0.91f,
                                prefixSimilarity = 0.94f,
                                contextualPrior = 0.85f,
                                markovTransitionProb = 0.80f,
                                completionRatio = 0.75f,
                                ghostCompletionTrail = listOf(
                                    GesturePoint(200f, 500f, 0L),
                                    GesturePoint(600f, 500f, 80L),
                                ),
                                shouldPrewarmAction = true,
                            ),
                        ),
                    ),
                    onExpand = {},
                    onPoint = {},
                    onStrokeFinished = {},
                    onPickPrediction = { pickedTemplateId = it },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("Screenshot Z 91%").assertIsDisplayed().performClick()
        assertEquals("screenshot_z", pickedTemplateId)

        composeRule.onNodeWithTag("overlay_dismiss_button").performClick()
        assertTrue(dismissed)
    }
}
