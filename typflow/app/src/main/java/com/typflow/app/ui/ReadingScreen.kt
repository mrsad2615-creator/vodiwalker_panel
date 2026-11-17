package com.typflow.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.ReadingText
import com.typflow.app.data.Texts
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import kotlinx.coroutines.delay

private enum class ReadPhase { PLAYER, QUIZ, RESULT }

@Composable
fun ReadingScreen(theme: TypflowTheme, store: TypflowStore) {
    val lang = store.lang
    val isFa = lang == "fa"
    val pool: List<ReadingText> = remember(lang) { Texts.readingPool(lang) }

    var textIndex by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(ReadPhase.PLAYER) }
    var wordIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var wpm by remember { mutableIntStateOf(300) }
    var showFull by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf(List(8) { -1 }) }
    var score by remember { mutableIntStateOf(0) }

    val text = pool[textIndex.coerceIn(0, pool.size - 1)]
    val words = remember(text) { text.body.split(" ").filter { it.isNotBlank() } }
    val chunk = store.chunkSize.coerceIn(1, 3)
    val current = words.getOrElse(wordIndex) { "" }
    val progress = if (words.isEmpty()) 0f else wordIndex.toFloat() / words.size

    fun resetPlayer() {
        wordIndex = 0
        playing = false
        answers = List(8) { -1 }
        score = 0
        phase = ReadPhase.PLAYER
    }

    LaunchedEffect(playing, wpm, chunk, textIndex) {
        if (!playing) return@LaunchedEffect
        while (playing) {
            val ms = (60000f / wpm.toFloat() * chunk).toLong().coerceIn(40L, 1200L)
            delay(ms)
            if (!playing) break
            if (wordIndex + chunk >= words.size) {
                wordIndex = words.size - 1
                playing = false
                phase = ReadPhase.QUIZ
                break
            }
            wordIndex += chunk
            if (store.rampOn && wordIndex % 25 == 0 && wpm < 900) {
                wpm += 10
            }
        }
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
            index = "03",
            title = tr(lang, "تندخوانی", "Speed reading"),
            subtitle = tr(
                lang,
                "کلمه به کلمه، با نشانگر نقطه تمرکز.",
                "One fixated point at a time."
            )
        )

        Spacer(Modifier.height(16.dp))

        // ---- passage selector ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pool.forEachIndexed { i, t ->
                MonoChip(
                    theme = theme,
                    text = t.title,
                    selected = i == textIndex,
                    onClick = {
                        textIndex = i
                        resetPlayer()
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (phase == ReadPhase.PLAYER) {
            Panel(theme, Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MicroLabel(theme, "${wordIndex + 1} / ${words.size}")
                    MicroLabel(theme, if (store.rampOn) "RAMP ON" else "RAMP OFF")
                }
                Spacer(Modifier.height(16.dp))

                // ---- RSVP word with ORP caret ----
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides
                        if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    val wordStyle = TextStyle(
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        color = theme.ink
                    )
                    val display = if (chunk == 1) current
                    else words.drop(wordIndex).take(chunk).joinToString(" ")
                    val measurer = rememberTextMeasurer()
                    val fullW = measurer.measure(AnnotatedString(display), wordStyle)
                        .size.width.toFloat()
                    val orp = (display.length * 0.35f).toInt().coerceIn(0, (display.length - 1).coerceAtLeast(0))
                    val prefixW = measurer
                        .measure(AnnotatedString(display.take(orp)), wordStyle)
                        .size.width.toFloat()
                    val charW = measurer
                        .measure(AnnotatedString(display.drop(orp).take(1)), wordStyle)
                        .size.width.toFloat()
                    val orpX = if (isFa) fullW - prefixW - charW / 2f
                    else prefixW + charW / 2f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = display,
                            style = wordStyle,
                            maxLines = 1,
                            softWrap = false
                        )
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val cx = if (fullW >= size.width) size.width / 2f
                            else orpX + (size.width - fullW) / 2f
                            val topY = size.height / 2f + 30.dp.toPx()
                            drawRect(
                                color = theme.ink,
                                topLeft = Offset(cx - 1.dp.toPx(), topY),
                                size = Size(2.dp.toPx(), 10.dp.toPx())
                            )
                            drawRect(
                                color = theme.line,
                                topLeft = Offset(cx - 40.dp.toPx(), topY + 16.dp.toPx()),
                                size = Size(80.dp.toPx(), 1.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Bar(theme, progress, height = 2.dp)
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SolidButton(
                        theme = theme,
                        text = if (playing) tr(lang, "توقف", "PAUSE")
                        else tr(lang, "پخش", "PLAY"),
                        onClick = {
                            if (wordIndex >= words.size - 1) wordIndex = 0
                            playing = !playing
                        },
                        modifier = Modifier.weight(1f)
                    )
                    LineButton(
                        theme = theme,
                        text = tr(lang, "از اول", "RESET"),
                        onClick = { resetPlayer() },
                        modifier = Modifier.weight(1f)
                    )
                    LineButton(
                        theme = theme,
                        text = tr(lang, "آزمون", "QUIZ"),
                        onClick = { playing = false; phase = ReadPhase.QUIZ },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- speed control ----
            Panel(theme, Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MicroLabel(theme, tr(lang, "سرعت نمایش", "SPEED"))
                    MicroLabel(theme, "$wpm WPM", color = theme.ink)
                }
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = wpm.toFloat(),
                    onValueChange = { wpm = it.toInt() },
                    valueRange = 100f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.ink,
                        activeTrackColor = theme.ink,
                        inactiveTrackColor = theme.line
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MicroLabel(theme, tr(lang, "اندازه قطعه", "CHUNK"))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3).forEach { c ->
                            MonoChip(
                                theme = theme,
                                text = c.toString(),
                                selected = chunk == c,
                                onClick = { store.updateChunk(c) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Hairline(theme)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = tr(lang, "شیب افزایش سرعت", "Speed ramp"),
                            color = theme.ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        MicroLabel(
                            theme,
                            tr(lang, "هر 25 کلمه 10+", "+10 every 25 words")
                        )
                    }
                    Switch(
                        checked = store.rampOn,
                        onCheckedChange = { store.updateRamp(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.bg,
                            checkedTrackColor = theme.ink,
                            uncheckedThumbColor = theme.dim,
                            uncheckedTrackColor = theme.surface,
                            uncheckedBorderColor = theme.line
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton(
                    theme = theme,
                    text = if (showFull) tr(lang, "پنهان کردن متن", "HIDE TEXT")
                    else tr(lang, "نمایش متن کامل", "SHOW FULL TEXT"),
                    onClick = { showFull = !showFull },
                    active = showFull
                )
            }

            if (showFull) {
                Spacer(Modifier.height(12.dp))
                Panel(theme, Modifier.fillMaxWidth()) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalLayoutDirection provides
                            if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Text(
                            text = text.body,
                            color = theme.ink,
                            fontSize = (15f * store.textScale).sp,
                            lineHeight = (30f * store.textScale).sp
                        )
                    }
                }
            }
        }

        if (phase == ReadPhase.QUIZ) {
            SectionLabel(theme, tr(lang, "آزمون درک مطلب", "COMPREHENSION"))
            Spacer(Modifier.height(12.dp))
            text.questions.forEachIndexed { qi, q ->
                Panel(theme, Modifier.fillMaxWidth()) {
                    Text(
                        text = "${qi + 1}. ${q.question}",
                        color = theme.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    q.options.forEachIndexed { oi, opt ->
                        val selected = answers[qi] == oi
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(if (selected) theme.ink else theme.bg)
                                .border(1.dp, if (selected) theme.ink else theme.line)
                                .clickable {
                                    answers = answers.toMutableList().also { it[qi] = oi }
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            Text(
                                text = opt,
                                color = if (selected) theme.bg else theme.ink,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            SolidButton(
                theme = theme,
                text = tr(lang, "ثبت پاسخ‌ها", "SUBMIT ANSWERS"),
                onClick = {
                    var correct = 0
                    text.questions.forEachIndexed { i, q ->
                        if (answers[i] == q.answerIndex) correct += 1
                    }
                    score = if (text.questions.isEmpty()) 0
                    else correct * 100 / text.questions.size
                    store.recordReading(wpm, score)
                    phase = ReadPhase.RESULT
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (phase == ReadPhase.RESULT) {
            Panel(theme, Modifier.fillMaxWidth()) {
                MicroLabel(theme, tr(lang, "نتیجه مطالعه", "READING RESULT"), color = theme.ink)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        BigFigure(theme, wpm.toString(), "WPM", size = 40)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        BigFigure(theme, score.toString(), "%", size = 40)
                        MicroLabel(theme, tr(lang, "درک مطلب", "COMPREHENSION"))
                    }
                }
                Spacer(Modifier.height(16.dp))
                SolidButton(
                    theme = theme,
                    text = tr(lang, "متن بعدی", "NEXT PASSAGE"),
                    onClick = {
                        textIndex = (textIndex + 1) % pool.size
                        resetPlayer()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LineButton(
                    theme = theme,
                    text = tr(lang, "تلاش دوباره", "TRY AGAIN"),
                    onClick = { resetPlayer() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MicroLabel(theme, tr(lang, "رکورد مطالعه", "READING BEST"))
            MicroLabel(theme, "${store.bestReadingWpm} WPM", color = theme.ink)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MicroLabel(theme, tr(lang, "بهترین درک مطلب", "TOP COMPREHENSION"))
            MicroLabel(theme, "${store.bestComprehension}%", color = theme.ink)
        }
    }
}
