package com.neurox.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neurox.domain.Module

/** Quiet Precision. Shared values for every engine, not screen-local theme forks. */
object NeuroxSpace {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val page = 22.dp
    val touchTarget = 48.dp
    val contentMax = 840.dp
}
object NeuroxRadius {
    val control = 14.dp
    val card = 20.dp
    val hero = 24.dp
}
object NeuroxMotion {
    const val entranceMs = 160
    const val emphasisMs = 220
    // No entrance/exit animations on measurement surfaces.
}

@Composable
fun moduleColor(module: Module): Color {
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.4f
    return Color(when (module) {
        Module.VISION -> if (dark) 0xFFAAC4B4 else 0xFF325B46
        Module.REFLEX -> if (dark) 0xFFAFC7DF else 0xFF355B79
        Module.READ -> if (dark) 0xFFC7B6DE else 0xFF6B4D88
        Module.TYPE -> if (dark) 0xFFD4B982 else 0xFF765A28
    })
}
