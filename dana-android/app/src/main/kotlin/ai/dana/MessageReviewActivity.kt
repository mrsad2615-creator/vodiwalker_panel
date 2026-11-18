package ai.dana

import ai.dana.ui.DanaTheme
import ai.dana.ui.MessageReviewScreen
import ai.dana.ui.MessageReviewViewModel
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint

/** The share target has no access to the chat or Phone view models. */
@AndroidEntryPoint
class MessageReviewActivity : ComponentActivity() {
    private val review: MessageReviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        if (savedInstanceState == null) receive(intent) else forgetIntent()
        setContent { DanaTheme { MessageReviewScreen(review, onClose = ::finish) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(received: Intent?) {
        forgetIntent()
        if (received?.action != Intent.ACTION_SEND) return
        val text = if (received.type == "text/plain") {
            runCatching { received.getCharSequenceExtra(Intent.EXTRA_TEXT) }.getOrNull()
        } else null
        // No URI, EXTRA_STREAM, HTML or other app context is followed or imported.
        review.receive(text)
    }

    private fun forgetIntent() = setIntent(Intent(this, MessageReviewActivity::class.java))

    override fun onStop() {
        review.stop()
        super.onStop()
    }
}
