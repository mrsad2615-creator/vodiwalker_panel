package com.typflow.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.typflow.app.data.Texts
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import kotlinx.coroutines.delay

private val MODE_SECONDS = listOf(0, 15, 30, 60, 120)

@Composable
fun TypingScreen(theme: TypflowTheme, store: TypflowStore) {
    val lang = store.lang
    val isFa = lang == "fa"

    var modeIndex by remember { mutableIntStateOf(0) }
    var level by remember { mutableIntStateOf(2) }
    var custom by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    var resWpm by remember { mutableIntStateOf(0) }
    var resAcc by remember { mutableIntStateOf(0) }
    var resErr by remember { mutableIntStateOf(0) }
    var problemKeys by remember { mutableStateOf(listOf<Char>()) }

    val haptic = LocalHapticFeedback.current
    val tone = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 30) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { tone?.release() } }
    }

    fun pickTarget(): String =
        custom ?: Texts.typingPool(lang, level).random()

    fun reset(newText: Boolean = true) {
        if (newText) target = pickTarget()
        input = ""
        startedAt = 0L
        now = 0L
        finished = false
        resWpm = 0
        resAcc = 0
        resErr = 0
        problemKeys = emptyList()
    }

    LaunchedEffect(lang, level, custom) {
        reset(newText = true)
    }

    LaunchedEffect(startedAt) {
        if (startedAt == 0L) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (finished) break
            delay(80)
        }
    }

    val elapsedMs = if (startedAt == 0L) 0L else (now - startedAt).coerceAtLeast(0L)
    val elapsedSec = (elapsedMs / 1000L).toInt()
    val timerMode = MODE_SECONDS[modeIndex]

    val correctChars = input.indices.count { i ->
        i < target.length && input[i] == target[i]
    }
    val errorChars = (input.length - correctChars).coerceAtLeast(0)
    val accuracy = if (input.isEmpty()) 100 else (correctChars * 100 / input.length)
    val wpm = if (elapsedSec > 0) ((correctChars / 5.0) / (elapsedSec / 60.0)).toInt() else 0
    val timeShown = if (timerMode > 0) (timerMode - elapsedSec).coerceAtLeast(0) else elapsedSec

    fun finish() {
        if (finished) return
        finished = true
        val secs = if (elapsedSec < 1) 1 else elapsedSec
        val finalWpm = ((correctChars / 5.0) / (secs / 60.0)).toInt()
        val finalAcc = if (input.isEmpty()) 0 else (correctChars * 100 / input.length)
        resWpm = finalWpm
        resAcc = finalAcc
        resErr = errorChars
        problemKeys = sessionErrors(input, target)
        store.recordTyping(
            wpm = finalWpm,
            acc = finalAcc,
            minutes = (secs / 60).coerceAtLeast(1),
            errors = errorChars,
            sessionKeyErrors = sessionErrorMap(input, target)
        )
    }

    LaunchedEffect(input, now, timerMode) {
        if (finished) return@LaunchedEffect
        if (startedAt == 0L) return@LaunchedEffect
        if (timerMode > 0 && elapsedSec >= timerMode) finish()
        else if (timerMode == 0 && target.isNotEmpty() && input.length >= target.length) finish()
    }

    val annotated = if (!isFa) {
        buildAnnotatedString {
            target.forEachIndexed { i, ch ->
                val style = when {
                    i < input.length && input[i] == ch -> SpanStyle(color = theme.ink)
                    i < input.length -> SpanStyle(
                        color = theme.bg,
                        background = theme.ink
                    )
                    i == input.length -> SpanStyle(
                        color = theme.ink,
                        fontWeight = FontWeight.Black,
                        textDecoration = TextDecoration.Underline
                    )
                    else -> SpanStyle(color = theme.dim)
                }
                withStyle(style) { append(ch) }
            }
        }
    } else {
        buildAnnotatedString {
            var i = 0
            while (i < target.length) {
                val start = i
                while (i < target.length && !target[i].isWhitespace()) i++
                val word = target.substring(start, i)
                val typed = (input.length - start).coerceIn(0, word.length)
                val slice = if (typed > 0) input.substring(start, start + typed) else ""
                val hasError = slice.indices.any { k -> k >= word.length || slice[k] != word[k] }
                val style = when {
                    typed == 0 -> SpanStyle(color = theme.dim)
                    hasError -> SpanStyle(
                        color = theme.ink,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    )
                    typed == word.length -> SpanStyle(color = theme.ink)
                    else -> SpanStyle(color = theme.ink, fontWeight = FontWeight.Bold)
                }
                withStyle(style) { append(word) }
                while (i < target.length && target[i].isWhitespace()) {
                    append(target[i])
                    i++
                }
            }
        }
    }

    val fraction = when {
        timerMode > 0 -> (elapsedSec.toFloat() / timerMode.toFloat())
        target.isNotEmpty() -> input.length.toFloat() / target.length.toFloat()
        else -> 0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 28.dp)
    ) {
        ScreenHeader(
            theme = theme,
            index = "02",
            title = tr(lang, "تمرین تایپ", "Typing lab"),
            subtitle = tr(
                lang,
                "دقت را بساز، سرعت خودش می آید.",
                "Build accuracy, speed follows."
            )
        )

        Spacer(Modifier.height(16.dp))

        // ---- mode ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MODE_SECONDS.forEachIndexed { index, secs ->
                MonoChip(
                    theme = theme,
                    text = if (secs == 0) tr(lang, "آزاد", "FREE") else "${secs}s",
                    selected = modeIndex == index,
                    onClick = {
                        modeIndex = index
                        reset(newText = false)
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoChip(theme, "L1", level == 1, { level = 1 })
            MonoChip(theme, "L2", level == 2, { level = 2 })
            MonoChip(theme, "L3", level == 3, { level = 3 })
            Spacer(Modifier.weight(1f))
            LineButton(
                theme = theme,
                text = tr(lang, "متن من", "MY TEXT"),
                onClick = { draft = custom ?: ""; showPaste = true },
                modifier = Modifier.height(36.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        // ---- live stats ----
        Panel(theme, Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBlock(theme, tr(lang, "سرعت", "WPM"), wpm, "WPM", size = 28)
                StatBlock(theme, tr(lang, "دقت", "ACC"), accuracy, "%", size = 28)
                StatBlock(theme, tr(lang, "زمان", "TIME"), timeShown, "S", size = 28)
                StatBlock(theme, tr(lang, "خطا", "ERR"), errorChars, "", size = 28)
            }
            Spacer(Modifier.height(12.dp))
            Bar(theme, fraction, height = 2.dp)
        }

        Spacer(Modifier.height(14.dp))

        // ---- target text ----
        Panel(theme, Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MicroLabel(theme, tr(lang, "متن هدف", "TARGET"))
                MicroLabel(
                    theme,
                    "${input.length} / ${target.length}",
                    color = theme.dim
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = annotated,
                style = TextStyle(
                    fontSize = (19f * store.textScale).sp,
                    lineHeight = (34f * store.textScale).sp,
                    fontFamily = uiFont(theme),
                    textDirection = if (isFa) TextDirection.Rtl else TextDirection.Ltr
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- input ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface)
                .border(1.dp, if (startedAt > 0 && !finished) theme.ink else theme.line)
                .padding(14.dp)
        ) {
            if (input.isEmpty()) {
                MicroLabel(
                    theme,
                    tr(lang, "اینجا تایپ کنید…", "TYPE HERE…"),
                    color = theme.dim
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { value ->
                    if (finished) return@BasicTextField
                    if (startedAt == 0L && value.isNotEmpty()) {
                        startedAt = System.currentTimeMillis()
                        now = startedAt
                    }
                    input = value
                    if (store.vibrationOn) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    if (store.soundOn) {
                        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 12) }
                    }
                },
                enabled = !finished,
                textStyle = TextStyle(
                    color = theme.ink,
                    fontSize = (17f * store.textScale).sp,
                    fontFamily = uiFont(theme),
                    textDirection = if (isFa) TextDirection.Rtl else TextDirection.Ltr
                ),
                cursorBrush = SolidColor(theme.ink),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (finished) {
            Spacer(Modifier.height(16.dp))
            Panel(theme, Modifier.fillMaxWidth()) {
                MicroLabel(theme, tr(lang, "نتیجه جلسه", "SESSION RESULT"), color = theme.ink)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        BigFigure(theme, resWpm.toString(), "WPM", size = 44)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        StatBlock(theme, tr(lang, "دقت", "ACC"), resAcc, "%", size = 24)
                        StatBlock(theme, tr(lang, "خطا", "ERR"), resErr, "", size = 24)
                    }
                }
                if (problemKeys.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Hairline(theme)
                    Spacer(Modifier.height(10.dp))
                    MicroLabel(theme, tr(lang, "کلیدهای مشکل‌دار", "PROBLEM KEYS"))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        problemKeys.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .width(30.dp)
                                    .border(1.dp, theme.line)
                                    .background(theme.bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key.toString(), color = theme.ink, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SolidButton(
                    theme = theme,
                    text = tr(lang, "متن جدید", "NEW TEXT"),
                    onClick = { reset(newText = true) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LineButton(
                    theme = theme,
                    text = tr(lang, "تکرار همین متن", "RETRY SAME TEXT"),
                    onClick = { reset(newText = false) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton(
                    theme = theme,
                    text = tr(lang, "متن جدید", "NEW TEXT"),
                    onClick = { reset(newText = true) }
                )
                if (custom != null) {
                    LineButton(
                        theme = theme,
                        text = tr(lang, "حذف متن من", "CLEAR MY TEXT"),
                        onClick = { custom = null }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MicroLabel(theme, tr(lang, "رکورد شما", "YOUR BEST"))
            MicroLabel(theme, "${store.bestWpm} WPM", color = theme.ink)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MicroLabel(theme, tr(lang, "جلسات", "SESSIONS"))
            MicroLabel(theme, store.totalSessions.toString(), color = theme.ink)
        }
    }

    if (showPaste) {
        Dialog(onDismissRequest = { showPaste = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.bg)
                    .border(1.dp, theme.line)
                    .padding(18.dp)
            ) {
                MicroLabel(theme, tr(lang, "متن تمرین خودت", "YOUR OWN TEXT"), color = theme.ink)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .border(1.dp, theme.line)
                        .padding(10.dp)
                ) {
                    if (draft.isEmpty()) {
                        MicroLabel(theme, tr(lang, "متن را اینجا بچسبان…", "PASTE TEXT HERE…"))
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = TextStyle(
                            color = theme.ink,
                            fontSize = 14.sp,
                            fontFamily = uiFont(theme),
                            textDirection = if (isFa) TextDirection.Rtl else TextDirection.Ltr
                        ),
                        cursorBrush = SolidColor(theme.ink),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SolidButton(
                        theme = theme,
                        text = tr(lang, "استفاده", "USE TEXT"),
                        onClick = {
                            val trimmed = draft.trim()
                            if (trimmed.isNotEmpty()) {
                                custom = trimmed
                                showPaste = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    LineButton(
                        theme = theme,
                        text = tr(lang, "بستن", "CLOSE"),
                        onClick = { showPaste = false },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun sessionErrorMap(input: String, target: String): Map<Char, Int> {
    val out = LinkedHashMap<Char, Int>()
    for (i in input.indices) {
        if (i >= target.length) break
        if (input[i] != target[i]) {
            val expected = target[i]
            if (!expected.isWhitespace()) out[expected] = (out[expected] ?: 0) + 1
        }
    }
    return out
}

private fun sessionErrors(input: String, target: String): List<Char> =
    sessionErrorMap(input, target).entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key }
