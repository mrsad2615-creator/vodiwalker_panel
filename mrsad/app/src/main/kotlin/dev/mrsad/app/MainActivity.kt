package dev.mrsad.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.navigation.MrsadRoot
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.ui.theme.MrsadMotion
import dev.mrsad.app.ui.theme.MrsadTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash hands over on the first real frame — no artificial
        // delay, and no blocking work before the theme is ready (spec §22).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MrsadApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = MrsadSettings())

            // Instrumented tests need a static first frame; report the
            // effective motion budget so a failing CI run can show whether
            // the deterministic hook was actually in effect.
            val deterministic = MrsadTestHooks.isDeterministic
            if (deterministic) {
                val motion = MrsadMotion.of(settings, deterministic)
                Log.i(
                    MrsadTestHooks.TAG,
                    "motion budget: animationsEnabled=${motion.animationsEnabled} " +
                        "particleCount=${motion.particleCount} quality=${motion.quality}",
                )
            }

            MrsadTheme(settings = settings) {
                // The app speaks Persian only, so the layout has to run
                // right-to-left on every device. The direction of the root
                // view follows the *system* locale, so an English phone
                // would otherwise lay the whole app out left-to-right and
                // put the navigation and the back arrows on the wrong
                // side. The override is explicit, so the UI never depends
                // on the language the device happens to be set to.
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MrsadRoot(container = container, settings = settings)
                    }
                }
            }
        }
    }
}
