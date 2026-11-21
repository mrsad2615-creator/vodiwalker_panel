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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        container = AppContainer(this)

        // Ask for media access once, up front. On a device where the user has
        // already granted it this is a no-op and never shows a dialog.
        if (!MediaPermissions.canReadAnything(this)) {
            permissionLauncher.launch(MediaPermissions.required)
        }

        setContent {
            HgTheme {
                // Edge-to-edge means the app draws the status bar background
                // itself, so the system bar icons must be dark on a white page.
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
                val appContainer = remember { container }
                HGalleryApp(container = appContainer)
            }
        }
    }
}
