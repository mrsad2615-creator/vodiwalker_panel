package com.neurox.app

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Each navigation scenario starts with an explicit empty guest fixture. */
@RunWith(AndroidJUnit4::class)
class NavigationTest : NeuroxUiFixture() {
    @Test fun homeOpensAnActualExercise() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("VISION").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("VISION").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("تنظیم تمرین پیشنهادی").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تنظیم تمرین پیشنهادی").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("آغاز جلسه").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("آغاز جلسه").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("آغاز جلسه").performClick()
        compose.onNodeWithText("خانهٔ نشانه‌دار را لمس کنید").assertIsDisplayed()
        compose.onNodeWithText("مکث").performClick()
        compose.onNodeWithText("جلسه متوقف است").assertIsDisplayed()
    }
    @Test fun emptyHistoryDoesNotInventStatistics() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("خانه").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تحلیل").performClick()
        compose.onNodeWithText("مسیر شما از اینجا شروع می‌شود").performScrollTo().assertIsDisplayed()
    }
    @Test fun builderSavesAValidatedExerciseAndReturnsToMyExercises() {
        compose.runOnUiThread { model.openBuilder() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("نام تمرین").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("نام تمرین").performScrollTo().performTextInput("تمرین شخصی من")
        compose.onNodeWithText("ذخیرهٔ تمرین").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model.state.value.customExercises.size == 1 && model.screen.value == Screen.LIBRARY.name }
        compose.onNodeWithText("تمرین شخصی من").performScrollTo().assertIsDisplayed()
        check(model.libraryArea.value == "CUSTOM")
    }

    @Test fun allFourStudioOverviewsHaveRealSetupAndBackNavigation() {
        for (module in com.neurox.domain.Module.entries) {
            compose.runOnUiThread { model.openModule(module) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("${module.name} / STUDIO").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("تنظیم تمرین پیشنهادی").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(5_000) { model.screen.value == Screen.TRAINING.name }
            check(com.neurox.domain.Exercise.valueOf(model.selectedExercise.value).module == module)
            compose.runOnUiThread { model.back() }
            compose.waitUntil(5_000) { model.screen.value == Screen.MODULE.name }
            check(model.selectedModule.value == module.name)
        }
    }
    @Test fun milestonesStartLockedAndNeverInventRewards() {
        compose.runOnUiThread { model.navigate(Screen.ACHIEVEMENTS) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("گام‌های کوچک، دستاوردهای واقعی").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("به‌دست‌آمده").assertCountEquals(0)
        compose.onNodeWithText("اولین گام").performScrollTo().assertIsDisplayed()
        check(model.state.value.sessions.isEmpty())
    }

}
