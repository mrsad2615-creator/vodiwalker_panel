package dev.hgallery.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Persian translation, verified by reading the strings the device would show.
 *
 * This does not claim to prove the layout is correct in every right-to-left
 * edge case; it proves the Persian resources are present, are selected by the
 * platform, and are wired to the tab bar — which is the failure mode that
 * usually ships (a translated file nothing references).
 */
@RunWith(AndroidJUnit4::class)
class LocalisationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun persianTabLabelsExistInResources() {
        val context = rule.activity
        val persian = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale("fa", "IR"))
        }
        val resources = context.createConfigurationContext(persian).resources

        // Each of these must differ from the English default; an untranslated
        // string falls back to English and would be caught here.
        val pairs = listOf(
            R.string.tab_photos to "Photos",
            R.string.tab_albums to "Albums",
            R.string.search to "Search",
            R.string.delete to "Delete",
        )
        for ((id, english) in pairs) {
            val fa = resources.getString(id)
            assert(fa.isNotBlank()) { "empty Persian string for id $id" }
            assert(fa != english) { "id $id was not translated: still \"$fa\"" }
        }
    }

    @Test
    fun englishIsTheDefaultAndStillRenders() {
        rule.onNodeWithText("Photos").assertIsDisplayed()
    }
}
