package com.typflow.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.theme.TypflowTheme
import kotlinx.coroutines.delay

/**
 * Raw monochrome logo motion: the name "mrsad" is typed out behind a blinking
 * block cursor, then the wordmark, a hairline loader and the version appear.
 */
@Composable
fun SplashScreen(theme: TypflowTheme, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2350)
        onDone()
    }

    val brand = "mrsad"
    var typed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(brand.length) {
            delay(135)
            typed += 1
        }
    }

    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(460), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    val fill by rememberInfiniteTransition(label = "load").animateFloat(
        initialValue = 0.08f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "loadFill"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
    ) {
        RawBackdrop(theme)

        // masthead
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MicroLabel(theme, "TYPFLOW", color = theme.dim)
            MicroLabel(theme, "SYS.01", color = theme.dim)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = brand.take(typed),
                color = theme.ink,
                fontFamily = uiFont(theme),
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-3).sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            // block cursor under the wordmark
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .width(58.dp)
                    .height(6.dp)
                    .background(
                        if (typed >= brand.length || blink > 0.4f) theme.ink
                        else Color.Transparent
                    )
            )

            Spacer(Modifier.height(30.dp))
            MicroLabel(theme, "T Y P I N G   ·   R E A D I N G", color = theme.dim, size = 10)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "TYPFLOW",
                color = theme.ink,
                fontFamily = uiFont(theme),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 8.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Bar(theme, fill, height = 2.dp)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MicroLabel(theme, "LOADING", color = theme.dim)
                MicroLabel(theme, "v1.1.0", color = theme.dim)
            }
        }
    }
}
