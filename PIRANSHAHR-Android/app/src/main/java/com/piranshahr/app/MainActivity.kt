package com.piranshahr.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * PIRANSHAHR — Native Android M1 Vertical Slice
 * WebView loads local Three.js city (offline) with hardware acceleration.
 * Future UE5 builds will replace this with Vulkan native; this is production M1.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive — status bar matches #070b13
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PiranshahrApp()
            }
        }
    }
}

@Composable
fun PiranshahrApp() {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        showSplash = false
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF070B13))
    ) {
        if (showSplash) {
            SplashScreen()
        } else {
            PiranshahrWebView()
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF070B13)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("پیرانشهر", color = Color(0xFFC9A8FF), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("شبیه‌ساز رانندگی", color = Color(0xFF9AA7BC), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = Color(0xFFA855F7))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PiranshahrWebView() {
    val ctx = LocalContext.current
    AndroidView(
        factory = {
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // Performance
                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                    // Hardware acceleration already via manifest
                }
                setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                }
                // Offline-first: load local assets/www/index.html (copied from web-prototype/dist)
                loadUrl("file:///android_asset/www/index.html")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
