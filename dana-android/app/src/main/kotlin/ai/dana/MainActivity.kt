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
import ai.dana.ui.ManagedWorkspaceViewModel
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
    private val managedDelegate = viewModels<ManagedWorkspaceViewModel>()
    private val managed by managedDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleExternal(intent)
        setContent {
            DanaTheme {
                DanaApp(model, chat, media, { account }, { art }, { phone }, managed) {
                    startActivity(Intent(this, MessageReviewActivity::class.java))
                }
            }
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
                clearCommandContext()
                model.dispatch(DanaIntent.Navigate(DashboardTab.PHONE))
            }
            OneFrameCaptureService.ACTION_OPEN_MEDIA -> {
                clearCommandContext()
                model.dispatch(DanaIntent.Navigate(DashboardTab.CHAT))
                media.open()
            }
        }
    }

    private fun clearCommandContext() {
        model.dispatch(DanaIntent.StopRemoteRequests)
        if (phoneDelegate.isInitialized()) phone.cancel()
        if (managedDelegate.isInitialized()) managed.stop()
        if (accountDelegate.isInitialized()) account.background()
        if (artDelegate.isInitialized()) art.stop()
        chat.background()
    }

    override fun onStart() {
        super.onStart()
        managed.setForeground(true)
    }

    override fun onStop() {
        if (managedDelegate.isInitialized()) managed.setForeground(false)
        if (!isChangingConfigurations) clearCommandContext()
        super.onStop()
    }
}
