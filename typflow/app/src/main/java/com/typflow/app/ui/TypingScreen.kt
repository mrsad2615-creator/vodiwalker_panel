package com.typflow.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.Texts
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import kotlinx.coroutines.delay
import kotlin.math.max

private enum class TypingMode { PRACTICE, TIMED }

@Composable
fun TypingScreen(theme: TypflowTheme, store: TypflowStore) {
    val lang = store.lang
    val isFa = lang == "fa"
    val pool = if (isFa) Texts.typingFa else Texts.typingEn

    var mode by remember { mutableStateOf(TypingMode.PRACTICE) }
    var target by remember { mutableStateOf(pool.random()) }
    var input by remember { mutableStateOf("") }
    var elapsed by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var resultWpm by remember { mutableIntStateOf(0) }
    var resultAcc by remember { mutableIntStateOf(0) }
    var resultErrors by remember { mutableIntStateOf(0) }

    fun reset(newText: Boolean = true) {
        if (newText) target = pool.random()
        input = ""
        elapsed = 0
        started = false
        finished = false
    }

    fun finish() {
        if (finished) return
        val correct = input.indices.count { i -> i < target.length && input[i] == target[i] }
        val errors = (input.length - correct).coerceAtLeast(0)
        val acc = if (input.isEmpty()) 0 else (correct * 100 / input.length)
        val minutes = max(elapsed, 1) / 60.0
        val wpm = if (minutes > 0) ((correct / 5.0) / minutes).toInt() else 0
        resultWpm = wpm
        resultAcc = acc
        resultErrors = errors
        finished = true
        started = false
        store.recordTyping(wpm, acc, max(1, elapsed / 60))
    }

    // Timer loop
    LaunchedEffect(started, mode) {
        if (started && !finished) {
            while (!finished) {
                delay(1000)
                elapsed += 1
                if (mode == TypingMode.TIMED && elapsed >= 60) {
                    finish()
                    break
                }
            }
        }
    }

    // Auto-finish in practice mode when the whole text is typed
    LaunchedEffect(input) {
        if (mode == TypingMode.PRACTICE && input.length >= target.length && input.isNotEmpty() && !finished) {
            finish()
        }
    }

    val correctNow = input.indices.count { i -> i < target.length && input[i] == target[i] }
    val errorsNow = (input.length - correctNow).coerceAtLeast(0)
    val accNow = if (input.isEmpty()) 100 else (correctNow * 100 / input.length)
    val liveWpm = if (elapsed > 0) ((correctNow / 5.0) / (elapsed / 60.0)).toInt() else 0
    val timeLeft = if (mode == TypingMode.TIMED) (60 - elapsed).coerceAtLeast(0) else elapsed

    val annotated = buildAnnotatedString {
        target.forEachIndexed { i, ch ->
            val color = when {
                i < input.length && input[i] == ch -> theme.success
                i < input.length -> theme.error
                i == input.length -> theme.primary
                else -> theme.onSurface.copy(alpha = 0.45f)
            }
            withStyle(
                SpanStyle(
                    color = color,
                    fontWeight = if (i == input.length) FontWeight.Bold else FontWeight.Normal
                )
            ) {
                append(ch)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle(theme, tr(lang, "تمرین تایپ", "Typing Practice"))

        // Mode chips
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(theme, tr(lang, "تمرین آزاد", "Practice"), mode == TypingMode.PRACTICE) {
                mode = TypingMode.PRACTICE
                reset()
            }
            Chip(theme, tr(lang, "آزمون ۶۰ ثانیه‌ای", "60s Test"), mode == TypingMode.TIMED) {
                mode = TypingMode.TIMED
                reset()
            }
        }

        // Live stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(theme, tr(lang, "سرعت", "Speed"), liveWpm, "WPM", Modifier.weight(1f))
            StatCard(theme, tr(lang, "دقت", "Accuracy"), accNow, "%", Modifier.weight(1f))
            StatCard(
                theme,
                if (mode == TypingMode.TIMED) tr(lang, "زمان باقی‌مانده", "Time left")
                else tr(lang, "زمان", "Time"),
                timeLeft,
                tr(lang, "ثانیه", "sec"),
                Modifier.weight(1f)
            )
        }

        // Target text
        GlassCard(theme, Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = tr(lang, "متن هدف — اشتباهات قرمز، درست سبز", "Target text — red = error, green = correct"),
                    color = theme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalLayoutDirection provides if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Text(
                            text = annotated,
                            fontSize = 19.sp,
                            lineHeight = 32.sp,
                            style = TextStyle(textDirection = if (isFa) TextDirection.Rtl else TextDirection.Ltr)
                        )
                    }
                }
            }
        }

        // Input
        TextField(
            value = input,
            onValueChange = { v ->
                if (!finished) {
                    if (!started && v.isNotEmpty()) started = true
                    input = v
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !finished,
            placeholder = {
                Text(
                    tr(lang, "اینجا تایپ کنید…", "Type here…"),
                    color = theme.onSurface.copy(alpha = 0.4f)
                )
            },
            textStyle = TextStyle(
                color = theme.onBackground,
                fontSize = 17.sp,
                textDirection = if (isFa) TextDirection.Rtl else TextDirection.Ltr
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = theme.surface.copy(alpha = 0.6f),
                unfocusedContainerColor = theme.surface.copy(alpha = 0.6f),
                focusedIndicatorColor = theme.primary,
                unfocusedIndicatorColor = theme.primary.copy(alpha = 0.3f),
                disabledContainerColor = theme.surface.copy(alpha = 0.4f)
            ),
            singleLine = false,
            minLines = 3
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(lang, "اشتباهات: ", "Errors: ") + errorsNow.toString(),
                color = theme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            GhostButton(theme, tr(lang, "متن جدید", "New text"), { reset(true) })
            if (mode == TypingMode.TIMED && started && !finished) {
                GradientButton(theme, tr(lang, "پایان آزمون", "Finish"), { finish() })
            }
        }

        // Result
        if (finished) {
            GlassCard(theme, Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HoloText(
                        tr(lang, "نتیجه آزمون", "Test Result"),
                        theme, 20
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(resultWpm, theme, 40)
                            Text(tr(lang, "کلمه در دقیقه", "WPM"), color = theme.onSurface, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(resultAcc, theme, 40)
                            Text(tr(lang, "درصد دقت", "Accuracy %"), color = theme.onSurface, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(resultErrors, theme, 40)
                            Text(tr(lang, "اشتباه", "Errors"), color = theme.onSurface, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = tr(lang, "رکورد شما: ", "Your best: ") + store.bestWpm.toString() + " WPM",
                        color = theme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    GradientButton(theme, tr(lang, "تمرین دوباره", "Practice again"), { reset(true) })
                }
            }
        }
    }
}
