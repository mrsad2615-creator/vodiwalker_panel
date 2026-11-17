package com.typflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.typflow.app.ui.TypflowApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLink = intent?.getStringExtra("screen")?.lowercase()
        setContent {
            TypflowApp(initialScreen = deepLink)
        }
    }
}
