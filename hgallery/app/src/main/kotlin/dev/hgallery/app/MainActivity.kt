package dev.hgallery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.data.MediaPermissions
import dev.hgallery.app.ui.HGalleryApp
import dev.hgallery.app.ui.theme.HgTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // `super` first: enableEdgeToEdge() reaches for the window and the
        // decor view, which do not exist until the activity has been created.
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = AppContainer(this)

        // Ask for media access once. On a device where it is already granted
        // this never shows a dialog.
        if (!MediaPermissions.canReadAnything(this)) {
            runCatching { permissionLauncher.launch(MediaPermissions.required) }
        }

        setContent {
            HgTheme {
                // Edge-to-edge means the app paints behind the status bar, so
                // the system bar icons must be dark on the light background.
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = true
                val appContainer = remember { container }
                HGalleryApp(container = appContainer)
            }
        }
    }
}
