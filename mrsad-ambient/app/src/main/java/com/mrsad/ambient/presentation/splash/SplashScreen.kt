package com.mrsad.ambient.presentation.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.designsystem.MrsadBrandSymbol
import com.mrsad.ambient.designsystem.MrsadColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(850L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MrsadColors.PureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MrsadBrandSymbol(size = 64.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "MRSAD AMBIENT",
                color = MrsadColors.PureWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 5.sp
            )
        }
    }
}
