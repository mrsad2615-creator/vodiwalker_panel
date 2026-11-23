package dev.hgallery.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.data.HgSettings
import dev.hgallery.app.data.MediaPermissions
import dev.hgallery.app.ui.HGalleryApp
import dev.hgallery.app.ui.onboarding.PermissionScreen
import dev.hgallery.app.ui.theme.HgTheme
import dev.hgallery.app.ui.theme.resolveDark

class MainActivity : ComponentActivity() {

    /**
     * The system's page for this app, where media access can be granted after a denial.
     *
     * `ACTION_APPLICATION_DETAILS_SETTINGS` with the package URI is the only reliable
     * route; a permission-specific intent does not exist for runtime media permissions.
     */
    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        runCatching { startActivity(intent) }
    }

    private lateinit var container: AppContainer

    /** Media access, as the screen needs to see it. */
    private var permissionState by mutableStateOf(MediaPermissions.State.DENIED)

    /**
     * True when the user denied access with "don't ask again", or denied twice.
     *
     * The platform stops showing the dialog in that state and returns instantly, so the
     * only route forward is the app's settings page. Without this flag the button
     * appeared to do nothing on the second press, which is worse than not offering it.
     */
    private var permissionBlocked by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionState = MediaPermissions.state(this)
            permissionBlocked = permissionState == MediaPermissions.State.DENIED &&
                !shouldShowRequestPermissionRationale(MediaPermissions.required.first())
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Installed before `super`, which is the documented order: the splash screen
        // must know its exit animation before the window is created, or it is replaced
        // abruptly and the hand-off to the first Compose frame flashes.
        //
        // The exit is a small forward scale with a fade, not a slide or a zoom-out. The
        // mark is being replaced by the grid, so it should recede into the surface rather
        // than travel across it.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { false }

        // `super` first: enableEdgeToEdge() reaches for the window and the
        // decor view, which do not exist until the activity has been created.
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // One container for the process. A second one would open a second DataStore on
        // the same file, which throws, so the application owns it and the activity
        // borrows it.
        container = (application as HGalleryApplication).container

        // Video playback must not stop because the screen dimmed. Scoped to this
        // activity rather than set on the window globally, so the flag cannot
        // keep the display awake while the user is in a static grid.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Read the current state, but do not ask yet. Asking in onCreate put the system
        // dialog over a blank window with no explanation; PermissionScreen now explains
        // the request first and starts it from the user's press.
        permissionState = MediaPermissions.state(this)

        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

            // The stored mode is read once, asynchronously, and the default while it
            // loads is SYSTEM. That is the only default that cannot flash: it matches
            // what the platform window is already drawing.
            val settings by container.settings.settings.collectAsState(initial = HgSettings())
            val dark = resolveDark(settings.themeMode, systemDark)

            HgTheme(dark = dark) {
                // Edge-to-edge means the app paints behind the bars. The icon colour
                // must follow the theme: hard-coding "light icons" inverted them in
                // dark mode, which is where this app is most often used.
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                when (permissionState) {
                    MediaPermissions.State.DENIED -> PermissionScreen(
                        permanentlyDenied = permissionBlocked,
                        onRequest = {
                            runCatching { permissionLauncher.launch(MediaPermissions.required) }
                        },
                        onOpenSettings = ::openAppSettings,
                    )
                    else -> HGalleryApp(container = container)
                }
            }
        }
    }

    /**
     * Re-read the permission state on return.
     *
     * This is what makes the Settings route work: the user grants access in the system
     * UI, presses back, and the app must notice without being restarted.
     */
    override fun onResume() {
        super.onResume()
        permissionState = MediaPermissions.state(this)
        if (permissionState != MediaPermissions.State.DENIED) permissionBlocked = false
    }
}
