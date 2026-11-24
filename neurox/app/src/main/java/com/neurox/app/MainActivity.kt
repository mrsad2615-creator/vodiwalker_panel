package com.neurox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel((application as NeuroxApplication).repository, createSavedStateHandle()) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NeuroxApp(model) }
    }
    override fun onStop() {
        super.onStop()
        if (model.screen.value == Screen.RUN.name) model.pause()
    }
}
