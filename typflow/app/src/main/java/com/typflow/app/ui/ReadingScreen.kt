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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.Texts
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import kotlinx.coroutines.delay

private enum class ReadPhase { PLAYER, QUIZ, RESULT }

@Composable
fun ReadingScreen(theme: TypflowTheme, store: TypflowStore) {
    val lang = store.lang
    val isFa = lang == "fa"
    val pool = if (isFa) Texts.readingFa else Texts.readingEn

    var textIndex by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(ReadPhase.PLAYER) }
    var wordIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var wpm by remember { mutableIntStateOf(250) }
    var showFull by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf(List(pool[textIndex].questions.size) { -1 }) }
    var score by remember { mutableIntStateOf(0) }

    val text = pool[textIndex]
    val words = remember(textIndex, lang) { text.body.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val progress = if (words.isEmpty()) 0f else (wordIndex.toFloat() / words.size.toFloat()).coerceIn(0f, 1f)
    val currentWord = words.getOrNull(wordIndex) ?: ""

    fun resetPlayer() {
        wordIndex = 0
        playing = false
        phase = ReadPhase.PLAYER
        answers = List(text.questions.size) { -1 }
        score = 0
    }

    // RSVP engine
    LaunchedEffect(playing, wpm, textIndex) {
        if (playing) {
            val interval = (60000.0 / wpm).toLong().coerceAtLeast(40L)
            while (playing && wordIndex < words.size) {
                delay(interval)
                wordIndex += 1
            }
            if (wordIndex >= words.size) {
                playing = false
                phase = ReadPhase.QUIZ
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
        SectionTitle(theme, tr(lang, "تندخوانی RSVP", "RSVP Speed Reading"))

        // Text selector
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pool.forEachIndexed { i, t ->
                Chip(theme, t.title, i == textIndex) {
                    textIndex = i
                    resetPlayer()
                }
            }
        }

        if (phase == ReadPhase.PLAYER) {
            // RSVP stage
            GlassCard(theme, Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tr(lang, "کلمه ${wordIndex + 1} از ${words.size}", "Word ${wordIndex + 1} of ${words.size}"),
                        color = theme.onSurface.copy(alpha = 0.65f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalLayoutDirection provides if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        // ORP focus marker: the word stays a single styled run so
                        // Persian letters keep their joins; the focal point is
                        // indicated with a glowing guide instead of per-glyph colour.
                        val orp = if (currentWord.isEmpty()) 0
                        else (currentWord.length * 0.35).toInt()
                            .coerceIn(0, currentWord.length - 1)
                        val wordStyle = TextStyle(
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.onBackground
                        )
                        val measurer = rememberTextMeasurer()
                        val fullWidth = measurer
                            .measure(AnnotatedString(currentWord), wordStyle)
                            .size.width.toFloat()
                        val prefixWidth = measurer
                            .measure(AnnotatedString(currentWord.take(orp)), wordStyle)
                            .size.width.toFloat()
                        val charWidth = measurer
                            .measure(
                                AnnotatedString(currentWord.drop(orp).take(1)),
                                wordStyle
                            )
                            .size.width.toFloat()
                        val orpX = if (isFa)
                            fullWidth - prefixWidth - charWidth / 2f
                        else
                            prefixWidth + charWidth / 2f
                        Box(
                            modifier = Modifier.height(74.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.matchParentSize()
                            ) {
                                val cx = if (fullWidth >= size.width)
                                    size.width / 2f else orpX + (size.width - fullWidth) / 2f
                                drawRoundRect(
                                    color = theme.primary.copy(alpha = 0.18f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        cx - 16.dp.toPx(), size.height / 2f - 30.dp.toPx()
                                    ),
                                    size = androidx.compose.ui.geometry.Size(
                                        32.dp.toPx(), 60.dp.toPx()
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                        10.dp.toPx()
                                    )
                                )
                                drawCircle(
                                    color = theme.primary,
                                    radius = 2.5.dp.toPx(),
                                    center = androidx.compose.ui.geometry.Offset(
                                        cx, size.height / 2f + 34.dp.toPx()
                                    )
                                )
                            }
                            Text(
                                text = currentWord,
                                style = wordStyle,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = theme.primary,
                        trackColor = theme.surfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    GradientButton(
                        theme,
                        if (playing) tr(lang, "⏸ توقف", "⏸ Pause") else tr(lang, "▶ پخش", "▶ Play"),
                        {
                            if (wordIndex >= words.size) wordIndex = 0
                            playing = !playing
                        },
                        Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GhostButton(
                            theme,
                            tr(lang, "↺ از اول", "↺ Restart"),
                            { resetPlayer() },
                            Modifier.weight(1f)
                        )
                        GhostButton(
                            theme,
                            tr(lang, "آزمون", "Quiz"),
                            {
                                playing = false
                                phase = ReadPhase.QUIZ
                            },
                            Modifier.weight(1f)
                        )
                    }
                }
            }

            // Speed control
            GlassCard(theme, Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            tr(lang, "سرعت نمایش", "Display speed"),
                            color = theme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$wpm WPM",
                            color = theme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                    Slider(
                        value = wpm.toFloat(),
                        onValueChange = { wpm = it.toInt() },
                        valueRange = 100f..1000f,
                        steps = 35,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.primary,
                            activeTrackColor = theme.primary,
                            inactiveTrackColor = theme.surfaceVariant
                        )
                    )
                    Text(
                        text = tr(lang, "رکورد مطالعه شما: ", "Your reading best: ") + store.bestReadingWpm.toString() + " WPM",
                        color = theme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
            }

            // Full text toggle
            GhostButton(
                theme,
                if (showFull) tr(lang, "پنهان کردن متن کامل", "Hide full text")
                else tr(lang, "نمایش متن کامل", "Show full text"),
                { showFull = !showFull }
            )
            if (showFull) {
                GlassCard(theme, Modifier.fillMaxWidth()) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalLayoutDirection provides if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Text(
                            text = text.body,
                            color = theme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 30.sp
                        )
                    }
                }
            }
        }

        if (phase == ReadPhase.QUIZ) {
            SectionTitle(theme, tr(lang, "آزمون درک مطلب", "Comprehension Quiz"))
            text.questions.forEachIndexed { qi, q ->
                GlassCard(theme, Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "${qi + 1}. ${q.question}",
                            color = theme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        q.options.forEachIndexed { oi, opt ->
                            val selected = answers[qi] == oi
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(2.dp)
                                    .then(
                                        androidx.compose.ui.Modifier
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                            .background(
                                                if (selected) androidx.compose.ui.graphics.Brush.horizontalGradient(theme.gradient)
                                                else androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(theme.surfaceVariant, theme.surfaceVariant)
                                                )
                                            )
                                            .clickable {
                                                answers = answers.toMutableList().also { it[qi] = oi }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp)
                                    )
                            ) {
                                Text(
                                    text = opt,
                                    color = if (selected) androidx.compose.ui.graphics.Color.Black else theme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
            GradientButton(theme, tr(lang, "ثبت پاسخ‌ها", "Submit answers"), {
                var correct = 0
                text.questions.forEachIndexed { i, q ->
                    if (answers[i] == q.answerIndex) correct += 1
                }
                score = if (text.questions.isEmpty()) 0 else (correct * 100 / text.questions.size)
                store.recordReading(wpm, score)
                phase = ReadPhase.RESULT
            })
        }

        if (phase == ReadPhase.RESULT) {
            GlassCard(theme, Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HoloText(tr(lang, "نتیجه مطالعه", "Reading Result"), theme, 20)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(wpm, theme, 40)
                            Text("WPM", color = theme.onSurface, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNumber(score, theme, 40)
                            Text(tr(lang, "درصد درک مطلب", "Comprehension %"), color = theme.onSurface, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    GradientButton(theme, tr(lang, "متن بعدی", "Next text"), {
                        textIndex = (textIndex + 1) % pool.size
                        resetPlayer()
                    })
                }
            }
        }
    }
}
