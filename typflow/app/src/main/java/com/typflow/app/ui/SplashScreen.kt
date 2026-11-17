package com.typflow.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.theme.TypflowTheme
import kotlinx.coroutines.delay

/**
 * Cinematic logo-motion splash: staggered "mrsad" letters with a holographic
 * gradient sweep, glowing underline and a shimmer loading bar.
 */
@Composable
fun SplashScreen(theme: TypflowTheme, onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2900)
        onDone()
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val bar by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background),
        contentAlignment = Alignment.Center
    ) {
        CinematicBackdrop(theme)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // ---- mrsad logo motion (always LTR, staggered reveal) ----
            val brand = "mrsad"
            var reveal by remember { mutableStateOf(0f) }
            LaunchedEffect(Unit) {
                androidx.compose.animation.core.animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) { value, _ -> reveal = value }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // soft holographic halo behind the wordmark
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        theme.primary.copy(alpha = 0.26f * glow),
                                        theme.accent.copy(alpha = 0.12f * glow),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    // slow orbiting halo ring
                    androidx.compose.foundation.Canvas(Modifier.size(252.dp)) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    theme.primary.copy(alpha = 0.35f),
                                    theme.accent.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension / 2f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.6.dp.toPx()
                            )
                        )
                    }
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        brand.forEachIndexed { index, ch ->
                            val p = ((reveal - index * 0.11f) / 0.45f).coerceIn(0f, 1f)
                            Text(
                                text = ch.toString(),
                                style = TextStyle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            theme.primary, theme.secondary, theme.accent
                                        ),
                                        start = androidx.compose.ui.geometry.Offset(
                                            index * 46f - 150f + sweep * 40f, 0f
                                        ),
                                        end = androidx.compose.ui.geometry.Offset(
                                            index * 46f + 10f + sweep * 40f, 90f
                                        )
                                    ),
                                    fontSize = 66.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1).sp
                                ),
                                modifier = Modifier.graphicsLayer {
                                    alpha = p
                                    translationY = (1f - p) * 44f
                                    val sc = 0.72f + 0.28f * p
                                    scaleX = sc
                                    scaleY = sc
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // glowing underline
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(theme.gradient))
                    .alpha(glow)
            )

            Spacer(Modifier.height(26.dp))

            // ---- TYPFLOW wordmark ----
            var flowVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(900)
                flowVisible = true
            }
            Text(
                text = "T Y P F L O W",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = theme.onBackground,
                letterSpacing = 8.sp,
                modifier = Modifier
                    .alpha(if (flowVisible) 1f else 0f)
                    .graphicsLayer { translationY = if (flowVisible) 0f else 18f }
            )

            Spacer(Modifier.height(10.dp))

            var tagVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(1150)
                tagVisible = true
            }
            Text(
                text = "سرعت  •  دقت  •  تمرکز",
                fontSize = 14.sp,
                color = theme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.alpha(if (tagVisible) 1f else 0f)
            )

            Spacer(Modifier.height(44.dp))

            // shimmer loading bar
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(theme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bar)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(theme.gradient))
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "v1.0.0",
                fontSize = 11.sp,
                color = theme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}
