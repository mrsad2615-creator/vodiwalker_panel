package ai.dana

import ai.dana.ui.DanaApp
import ai.dana.ui.DanaIntent
import ai.dana.ui.DanaTheme
import ai.dana.ui.DanaViewModel
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: DanaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DanaTheme { DanaApp(model) }
        }
    }
    override fun onStop() {
        if (!isChangingConfigurations) model.dispatch(DanaIntent.StopRemoteRequests)
        super.onStop()
    }
}
