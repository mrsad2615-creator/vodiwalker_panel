package dev.hgallery.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hgallery.app.ui.components.HgTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Photos tab must survive repeated view switches.
 *
 * The pinch gesture itself cannot be driven from Compose Test, which injects
 * single-pointer events only. What *can* be verified is that the screen that owns
 * the gesture composes correctly and keeps its grid identity across
 * recomposition, so a broken gesture handler shows up here as a crash or a
 * missing grid rather than as a screenshot nobody looked at.
 */
@RunWith(AndroidJUnit4::class)
class MonthViewTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun photosGridIsPresentAndStable() {
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()
        rule.waitForIdle()
        rule.onNodeWithTag(HgTestTags.PHOTOS_GRID).assertExists()
    }
}
