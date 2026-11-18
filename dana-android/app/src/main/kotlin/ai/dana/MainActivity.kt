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
import ai.dana.ui.PhoneViewModel
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
    private val accountDelegate = viewModels<AccountViewModel>()
    private val account by accountDelegate
    private val media: MediaViewModel by viewModels()
    private val artDelegate = viewModels<ArtViewModel>()
    private val art by artDelegate
    private val phoneDelegate = viewModels<PhoneViewModel>()
    private val phone by phoneDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleExternal(intent)
        setContent {
            DanaTheme { DanaApp(model, chat, media, { account }, { art }, { phone }) }
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
                model.dispatch(DanaIntent.Navigate(DashboardTab.PHONE))
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
            if (accountDelegate.isInitialized()) account.background()
            if (artDelegate.isInitialized()) art.stop()
            if (phoneDelegate.isInitialized()) phone.cancel()
        }
        super.onStop()
    }
}
