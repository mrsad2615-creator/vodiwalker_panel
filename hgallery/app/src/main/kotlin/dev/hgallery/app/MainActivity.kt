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

        // Video playback must not stop because the screen dimmed. Scoped to this
        // activity rather than set on the window globally, so the flag cannot
        // keep the display awake while the user is in a static grid.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Ask for media access once. On a device where it is already granted
        // this never shows a dialog.
        if (!MediaPermissions.canReadAnything(this)) {
            runCatching { permissionLauncher.launch(MediaPermissions.required) }
        }

        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            HgTheme(dark = systemDark) {
                // Edge-to-edge means the app paints behind the bars. The icon colour
                // must follow the theme: hard-coding "light icons" inverted them in
                // dark mode, which is where this app is most often used.
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !systemDark
                    isAppearanceLightNavigationBars = !systemDark
                }
                val appContainer = remember { container }
                HGalleryApp(container = appContainer)
            }
        }
    }
}
