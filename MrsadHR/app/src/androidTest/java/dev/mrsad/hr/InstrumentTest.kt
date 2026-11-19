package dev.mrsad.hr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentTest {
    @get:Rule
    val permission: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun focusTransportAndRecreation() {
        ui.onNodeWithText("5m").performScrollTo().performClick()
        ui.onNodeWithText("▶   Start").performScrollTo().performClick()
        ui.onNodeWithText("Exit focus").assertExists()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Ⅱ   Pause").performScrollTo().performClick()
        ui.onNodeWithText("▶   Resume").assertExists()
        ui.onNodeWithText("Reset").performClick()
    }

    @Test
    fun materialAndSoundSelection() {
        ui.onNodeWithContentDescription("Themes").performClick()
        ui.onNodeWithText("Aurora").performScrollTo().performClick()
        ui.onNodeWithText("←  Studio").performClick()
        ui.onNodeWithContentDescription("Sounds").performClick()
        ui.onAllNodesWithText("Select")[0].performScrollTo().performClick()
        val app = ui.activity.application as Studio
        runBlocking {
            app.options.data.first {
                it[stringPreferencesKey("sound")] != null &&
                    it[stringPreferencesKey("theme")] == "Aurora"
            }
        }
    }

    @Test
    fun roomSurvivesReopen() {
        val ctx = ui.activity.applicationContext
        fun open() = Room.databaseBuilder(ctx, Library::class.java, "preset-test").build()
        runBlocking {
            val db = open()
            try {
                db.presets().save(Preset(17, "Interval", 62000))
            } finally {
                db.close()
            }
            val restored = open()
            try {
                Assert.assertEquals(62000, restored.presets().all().first().single().duration)
            } finally {
                restored.close()
                ctx.deleteDatabase("preset-test")
            }
        }
    }
}
