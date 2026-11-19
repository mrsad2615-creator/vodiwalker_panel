package dev.mrsad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.navigation.MrsadRoot
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

            MrsadTheme(settings = settings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MrsadRoot(container = container, settings = settings)
                }
            }
        }
    }
}
