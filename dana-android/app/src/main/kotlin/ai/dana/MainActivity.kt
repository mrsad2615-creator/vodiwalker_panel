package ai.dana

import ai.dana.media.MediaViewModel
import ai.dana.media.OneFrameCaptureService
import ai.dana.ui.AccountViewModel
import ai.dana.ui.ArtViewModel
import ai.dana.ui.ChatViewModel
import ai.dana.ui.DashboardTab
import ai.dana.ui.DanaApp
import ai.dana.ui.DanaIntent
import ai.dana.ui.DanaTheme
import ai.dana.ui.DanaViewModel
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: DanaViewModel by viewModels()
    private val chat: ChatViewModel by viewModels()
    private val account: AccountViewModel by viewModels()
    private val media: MediaViewModel by viewModels()
    private val art: ArtViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == Intent.ACTION_MAIN && ValueAnimator.areAnimatorsEnabled()) {
            splash.setOnExitAnimationListener { screen ->
                screen.iconView.animate().scaleX(1.09f).scaleY(1.09f).setDuration(240L).start()
                screen.view.animate().alpha(0f).setDuration(260L).withEndAction { screen.remove() }.start()
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleExternal(intent)
        setContent {
            DanaTheme { DanaApp(model, chat, media, account, art) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternal(intent)
    }

    private fun handleExternal(received: Intent?) {
        when (received?.action) {
            Intent.ACTION_ASSIST -> {
                // Android can attach foreground-app context. Ignore it; no screen read or microphone start.
                model.dispatch(DanaIntent.Navigate(DashboardTab.CHAT))
                chat.assistant(local = true)
            }
            OneFrameCaptureService.ACTION_OPEN_MEDIA -> {
                model.dispatch(DanaIntent.Navigate(DashboardTab.CHAT))
                media.open()
            }
            Intent.ACTION_SEND -> if (received.type == "text/plain") {
                val text = runCatching { received.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }.getOrNull() ?: return
                // Treat external content as an editable draft. Do not resolve intents, submit it, or run actions.
                model.dispatch(DanaIntent.Navigate(DashboardTab.CHAT))
                chat.importText(text)
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            model.dispatch(DanaIntent.StopRemoteRequests)
            chat.background()
            account.background()
            art.stop()
        }
        super.onStop()
    }
}
