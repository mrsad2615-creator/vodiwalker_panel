@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.neurox.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neurox.design.NeuroxIcon
import com.neurox.domain.*

@Composable
internal fun TrainingSetup(state: AppState, vm: AppViewModel) {
    val selected by vm.selectedExercise.collectAsStateWithLifecycle()
    val exercise = Exercise.valueOf(selected)
    var rounds by rememberSaveable { mutableIntStateOf(5) }
    var difficulty by rememberSaveable(selected) { mutableIntStateOf(1) }
    var baseline by rememberSaveable { mutableStateOf(false) }
    val definition = Catalogue.get(exercise)
    val history = state.sessions.filter { it.exercise == selected && it.status == "COMPLETE" }.map {
        Performance(it.difficulty, it.accuracy, it.responseMs, it.responseDeviationMs, it.valid, it.baseline)
    }
    val recommendation = Adaptive.recommend(difficulty, history, state.profile?.adaptive ?: true)
    var recommendationDecided by rememberSaveable(selected, difficulty, history.size) { mutableStateOf(false) }
    Page {
        SectionTitle("TRAINING STUDIO", tr("تمرین با هدف", "Practice with purpose"), tr("ابتدا راهنما را بخوانید، سپس جلسه را آغاز کنید.", "Read the instructions before you start."))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Catalogue.runnable.forEach { item -> FilterChip(selected = exercise == item.exercise, onClick = { vm.chooseExercise(item.exercise) }, label = { Text(exerciseName(item.exercise)) }) }
        }
        Panel {
            NeuroxIcon(glyph(exercise.module), Modifier.size(36.dp), MaterialTheme.colorScheme.primary)
            Text(exerciseName(exercise), style = MaterialTheme.typography.titleLarge)
            Text(tr(definition.instructionFa, definition.instructionEn))
            if (exercise == Exercise.COMPREHENSION) Text(tr("این نسخه سه متن کوتاه دارد؛ متن‌ها پس از سه دور تکرار می‌شوند. سطح متن در این مرحله ثابت است.", "This milestone includes three short passages. They repeat after three rounds. Text difficulty is fixed in this milestone."), style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            Text(tr("تعداد دور: ${number(rounds)}", "Rounds: ${number(rounds)}"), style = MaterialTheme.typography.titleMedium)
            Slider(value = rounds.toFloat(), onValueChange = { rounds = it.toInt() }, valueRange = 1f..15f, steps = 13)
            if (exercise != Exercise.COMPREHENSION) {
                Text(tr("دشواری: ${number(difficulty)}", "Difficulty: ${number(difficulty)}"))
                Slider(value = difficulty.toFloat(), onValueChange = { difficulty = it.toInt() }, valueRange = 1f..5f, steps = 3)
                Text(if (exercise.module == Module.TYPE) tr("سطح بالاتر متن یا واژه‌های بیشتری دارد.", "A higher level uses more text or words.") else if (exercise in listOf(Exercise.PHRASE_READING, Exercise.RSVP_READER)) tr("نمایش هدف: ${number(120 + (difficulty - 1) * 30)} واژه در دقیقه؛ زمان واقعی ممکن است بیشتر باشد.", "Target presentation: ${number(120 + (difficulty - 1) * 30)} WPM. Actual display time can be longer.") else if (exercise == Exercise.VISUAL_SEARCH) tr("سطح بالاتر نشانه‌های بیشتری و زمان کمتری دارد.", "A higher level has more distractors and less response time.") else tr("سطح بالاتر زمان پاسخ را کوتاه‌تر می‌کند.", "A higher level gives less time to respond."), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(tr("ثبت به‌عنوان سنجش پایه", "Record as baseline")); Text(tr("از تمرین و پاداش جدا می‌ماند.", "Separate from training and rewards."), style = MaterialTheme.typography.bodySmall) }
                Switch(checked = baseline, onCheckedChange = { baseline = it })
            }
        }
        if (exercise != Exercise.COMPREHENSION) Panel {
            Text(tr("پیشنهاد محلی", "LOCAL RECOMMENDATION"), style = MaterialTheme.typography.titleMedium)
            Text(reasonText(recommendation.reason))
            if (!recommendationDecided && recommendation.difficulty != difficulty) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.acceptRecommendation(exercise, difficulty, recommendation, true); rounds = recommendation.rounds; difficulty = recommendation.difficulty; recommendationDecided = true }) { Text(tr("پذیرش", "Accept")) }
                TextButton(onClick = { vm.acceptRecommendation(exercise, difficulty, recommendation, false); recommendationDecided = true }) { Text(tr("فعلاً نه", "Not now")) }
            }
        }
        if (state.interrupted != null) {
            Text(tr("پیش از شروع، جلسهٔ ناتمام را ادامه دهید یا کنار بگذارید.", "Resume or discard the saved session before starting another."))
            Button(onClick = vm::restore, enabled = !state.busy) { Text(tr("ادامهٔ جلسهٔ ذخیره‌شده", "Open saved session")) }
        } else Button(onClick = { vm.start(exercise, rounds, if (exercise == Exercise.COMPREHENSION) 1 else difficulty, baseline) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(8.dp)) {
            Text(tr("آغاز جلسه", "Begin session"))
        }
    }
}

@Composable private fun reasonText(reason: AdaptationReason) = when (reason) {
    AdaptationReason.DISABLED -> tr("پیشنهاد تطبیقی خاموش است. تنظیم دستی شما حفظ می‌شود.", "Adaptive suggestions are off. Your manual settings are kept.")
    AdaptationReason.INSUFFICIENT_HISTORY -> tr("برای تشخیص روند، حداقل سه جلسهٔ معتبر در همین سطح لازم است.", "At least three valid sessions at this level are needed to detect a trend.")
    AdaptationReason.STABLE_SUCCESS -> tr("پنج جلسهٔ دقیق و پایدار دارید؛ یک سطح بالاتر پیشنهاد می‌شود.", "Five accurate, stable sessions support a one-level increase.")
    AdaptationReason.REPEATED_ERRORS -> tr("در سه جلسه خطا بیشتر بوده است؛ جلسهٔ کوتاه‌تر و یک سطح پایین‌تر پیشنهاد می‌شود.", "Three sessions show repeated errors. Try fewer rounds and one lower level.")
    AdaptationReason.MAINTAIN -> tr("هنوز شواهد پایداری برای تغییر سطح وجود ندارد. سطح فعلی حفظ می‌شود.", "There is not enough stable evidence for a change. Keep this level.")
}

@Composable
internal fun TrainingRun(state: AppState, vm: AppViewModel) {
    val view by vm.training.collectAsStateWithLifecycle()
    val exercise = view?.exercise
    if (view == null || exercise == null) {
        Page { Text(tr("جلسه در دسترس نیست.", "The session is not available.")); Button(onClick = { vm.navigate(Screen.HOME) }) { Text(tr("خانه", "Home")) } }
        return
    }
    val current = requireNotNull(view)
    // Never save the user's draft into the Activity SavedState bundle.
    var input by remember(exercise, current.round, current.sprintWordIndex, current.phase == Phase.PAUSED) { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    Page {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(current.customName ?: exerciseName(exercise), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::pause, enabled = current.phase !in listOf(Phase.PAUSED, Phase.COMPLETE)) { Text(tr("مکث", "Pause")) }
        }
        Text(tr("دور ${number(current.round)} از ${number(current.totalRounds)} · زمان فعال ${number(current.elapsedMs / 1000.0, 1)} ثانیه", "Round ${number(current.round)} of ${number(current.totalRounds)} · ${number(current.elapsedMs / 1000.0, 1)} active seconds"), style = MaterialTheme.typography.bodySmall)
        if (current.practice) Text(tr("اجرای آزمایشی · بدون XP و خارج از آمار تمرین", "Test run · no XP and excluded from training statistics"), color = MaterialTheme.colorScheme.primary)
        if (current.phase == Phase.PAUSED) Panel {
            Text(tr("جلسه متوقف است", "Session paused"), style = MaterialTheme.typography.titleLarge)
            Text(tr("هنگام ادامه، دور ناتمام دوباره آغاز می‌شود. زمان مکث در نتیجه حساب نمی‌شود.", "Resume restarts the unfinished round. Paused time does not count."))
            Button(onClick = { input = ""; vm.resume() }, enabled = !state.busy) { Text(tr("ادامه", "Resume")) }
            TextButton(onClick = { vm.navigate(Screen.HOME) }) { Text(tr("ذخیره و بازگشت", "Save and return")) }
            TextButton(onClick = { confirmDiscard = true }) { Text(tr("پایان بدون پاداش", "End without rewards")) }
        } else if (current.phase == Phase.COMPLETE) {
            Text(tr("در حال ثبت نتیجه…", "Saving the result…"))
            CircularProgressIndicator()
        } else when (exercise) {
            Exercise.FOCUS_LOCK, Exercise.VISUAL_SEARCH -> {
                if (exercise == Exercise.VISUAL_SEARCH) {
                    Text(tr("فقط T ایستاده را پیدا کنید", "Find the upright T only"))
                    SearchShape(SearchMark.T_UP, Modifier.size(48.dp))
                } else Text(tr("خانهٔ نشانه‌دار را لمس کنید", "Tap the marked cell"))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) { col ->
                                val cell = row * 4 + col
                                val target = cell == current.targetCell
                                val search = exercise == Exercise.VISUAL_SEARCH
                                val mark = current.searchBoard?.cells?.get(cell)
                                val label = if (search) searchCellLabel(mark, cell) else tr(if (target) "هدف؛ خانه ${cell + 1}" else "خانهٔ خالی ${cell + 1}", if (target) "Target, cell ${cell + 1}" else "Empty cell ${cell + 1}")
                                Surface(onClick = { vm.tap(cell, current.inputRevision) }, modifier = Modifier.weight(1f).aspectRatio(1f).sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label },
                                    color = if (target && !search) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (target && !search) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)), shape = RoundedCornerShape(8.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (search && mark != null) SearchShape(mark, Modifier.size(32.dp))
                                        else if (!search && target) NeuroxIcon(glyph(Module.VISION), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        } }
                    }
                }
            }
            Exercise.PATTERN_MATCH -> {
                current.pattern?.let { trial ->
                    Text(tr("نمونه", "SAMPLE"), style = MaterialTheme.typography.labelLarge)
                    PatternGrid(trial.sample, Modifier.size(130.dp))
                    Text(tr("گزینهٔ کاملاً یکسان را انتخاب کنید", "Choose the exact match"))
                    trial.options.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEachIndexed { col, cells ->
                                val option = row * 2 + col
                                Surface(onClick = { vm.tap(option, current.inputRevision) }, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(tr("گزینهٔ ${number(option + 1)}", "Option ${number(option + 1)}"))
                                        PatternGrid(cells, Modifier.fillMaxWidth().aspectRatio(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Exercise.SHAPE_REACTION -> {
                Panel {
                    Box(Modifier.fillMaxWidth().heightIn(min = 150.dp), contentAlignment = Alignment.Center) {
                        val shape = current.reactionShape
                        if (shape == null) Text(tr("منتظر شکل بمانید", "Wait for the shape")) else ReactionShapeIcon(shape, Modifier.size(96.dp))
                    }
                    ReactionShape.entries.forEach { shape ->
                        OutlinedButton(onClick = { vm.tap(shape.ordinal, current.inputRevision) }, modifier = Modifier.fillMaxWidth()) {
                            ReactionShapeIcon(shape, Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(shapeName(shape))
                        }
                    }
                }
            }
            Exercise.COLOR_REACTION -> {
                Panel {
                    Box(Modifier.fillMaxWidth().heightIn(min = 150.dp), contentAlignment = Alignment.Center) {
                        val signal = current.reactionColor
                        if (signal == null) Text(tr("منتظر رنگ بمانید", "Wait for the color")) else ColorSignal(signal, Modifier.size(100.dp))
                    }
                    ReactionColor.entries.forEach { color ->
                        OutlinedButton(onClick = { vm.tap(color.ordinal, current.inputRevision) }, modifier = Modifier.fillMaxWidth()) {
                            ColorSignal(color, Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(colorName(color))
                        }
                    }
                    Text(tr("نام رنگ نشانه برای صفحه‌خوان فراهم است؛ نتایج با و بدون ابزار کمکی مستقیماً قابل مقایسه نیستند.", "The signal exposes its color name to screen readers. Assisted and unassisted results are not directly comparable."), style = MaterialTheme.typography.bodySmall)
                }
            }
            Exercise.DIRECTION_MATCH -> {
                Panel {
                    DirectionSignal(current.directionTrial, Modifier.fillMaxWidth().height(190.dp))
                    // Physical left/right must not follow the locale's layout mirroring.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            DirectionAnswer(PhysicalDirection.UP, current, vm)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                DirectionAnswer(PhysicalDirection.LEFT, current, vm)
                                DirectionAnswer(PhysicalDirection.DOWN, current, vm)
                                DirectionAnswer(PhysicalDirection.RIGHT, current, vm)
                            }
                        }
                    }
                    Text(tr("جهت نوک پیکان مهم است، نه جای آن.", "Match the arrow's direction, not its position."))
                }
            }
            Exercise.QUICK_TAP, Exercise.GO_NO_GO -> {
                val active = current.phase == Phase.ACTIVE
                val go = active && (exercise == Exercise.QUICK_TAP || current.goSignal)
                val noGo = active && !go
                Surface(onClick = { vm.tap(revision = current.inputRevision) }, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), shape = RoundedCornerShape(12.dp),
                    color = if (go) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (active) Text(if (noGo) "×" else "○", style = MaterialTheme.typography.displaySmall, color = if (go) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        Text(if (go) tr("بزن", "GO") else if (noGo) tr("نزن", "NO-GO") else tr("صبر کن", "WAIT"), style = MaterialTheme.typography.displaySmall, color = if (go) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        Text(if (go) tr("اکنون لمس کن", "Tap now") else if (noGo) tr("تا پایان نشانه لمس نکن", "Do not tap until this signal ends") else tr("هنوز لمس نکن", "Do not tap yet"), color = if (go) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(tr("تأخیر لمس، نمایشگر و ابزارهای دسترس‌پذیری روی مقایسهٔ نتیجه اثر دارد.", "Touch, display and accessibility-tool latency affect comparisons."), style = MaterialTheme.typography.bodySmall)
            }
            Exercise.COMPREHENSION, Exercise.PHRASE_READING, Exercise.RSVP_READER -> {
                if (current.phase == Phase.QUESTION) Panel {
                    Text(current.passage.question, style = MaterialTheme.typography.titleLarge)
                    current.passage.options.forEachIndexed { index, option -> OutlinedButton(onClick = { vm.answer(index, current.inputRevision) }, modifier = Modifier.fillMaxWidth()) { Text(option) } }
                } else if (exercise in listOf(Exercise.PHRASE_READING, Exercise.RSVP_READER)) {
                    current.phrase?.let { phrase -> Panel {
                        Text(current.passage.title, style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                            Text(phrase.text, style = MaterialTheme.typography.headlineMedium)
                        }
                        Text(if (exercise == Exercise.RSVP_READER) tr("واژه ${number(phrase.index)} از ${number(phrase.count)}", "Word ${number(phrase.index)} of ${number(phrase.count)}") else tr("عبارت ${number(phrase.index)} از ${number(phrase.count)}", "Phrase ${number(phrase.index)} of ${number(phrase.count)}"))
                        LinearProgressIndicator(progress = { phrase.index.toFloat() / phrase.count }, modifier = Modifier.fillMaxWidth())
                    } }
                } else {
                    Panel { Text(current.passage.title, style = MaterialTheme.typography.titleLarge); Text(current.passage.text, style = MaterialTheme.typography.bodyLarge) }
                    Button(onClick = { vm.question(current.inputRevision) }, enabled = current.roundElapsedMs >= 1000, modifier = Modifier.fillMaxWidth()) { Text(tr("خواندم؛ نمایش پرسش", "Finished reading · show question")) }
                }
            }
            Exercise.KEY_PRECISION, Exercise.WORD_SPRINT, Exercise.CODE_TYPING -> {
                if (exercise == Exercise.WORD_SPRINT) Text(tr("واژه ${number(current.sprintWordIndex)} از ${number(current.sprintWordCount)}", "Word ${number(current.sprintWordIndex)} of ${number(current.sprintWordCount)}"))
                Panel {
                    Text(tr("متن هدف", "TARGET TEXT"), style = MaterialTheme.typography.labelSmall)
                    CompositionLocalProvider(LocalLayoutDirection provides if (exercise == Exercise.CODE_TYPING) LayoutDirection.Ltr else LocalLayoutDirection.current) {
                        Text(current.typingTarget, style = if (exercise == Exercise.CODE_TYPING) MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) else MaterialTheme.typography.titleLarge)
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides if (exercise == Exercise.CODE_TYPING) LayoutDirection.Ltr else LocalLayoutDirection.current) {
                OutlinedTextField(value = input, onValueChange = { if (it.length <= if (exercise == Exercise.WORD_SPRINT) 64 else 1000) input = it }, label = { Text(tr("اینجا تایپ کنید", "Type here")) }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Text))
                }
                Text(tr("جملهٔ واردشده در تاریخچه ذخیره نمی‌شود؛ فقط شمارش خطا و کلیدهای ضعیف ثبت می‌شود.", "Entered text is not stored in history. Only error counts and weak-key totals are saved."), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.type(input, current.inputRevision); input = "" }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (exercise == Exercise.WORD_SPRINT) tr("ثبت واژه", "Submit word") else tr("ثبت این دور", "Submit this round")) }
            }
        }
        current.attempts.lastOrNull()?.let { attempt ->
            Text(when (attempt.outcome) {
                Outcome.WITHHELD -> tr("پاسخ قبلی: خودداری درست", "Previous response: correct inhibition")
                Outcome.CORRECT -> tr("پاسخ قبلی: درست", "Previous response: correct")
                Outcome.INCORRECT -> tr("پاسخ قبلی: نادرست", "Previous response: incorrect")
                Outcome.MISSED -> tr("پاسخ قبلی: زمان تمام شد", "Previous response: time expired")
                Outcome.PREMATURE -> tr("پاسخ قبلی: زودهنگام", "Previous response: too early")
            }, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text(tr("جلسه پایان یابد؟", "End this session?")) }, text = { Text(tr("دورهای ثبت‌شده حفظ می‌شوند، اما این جلسه کامل محسوب نمی‌شود و پاداش ندارد.", "Saved rounds are kept. This session will not count as complete and earns no rewards.")) }, confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discard() }) { Text(tr("پایان جلسه", "End session")) } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("بازگشت", "Cancel")) } })
}

@Composable
internal fun ResultScreen(vm: AppViewModel) {
    val result by vm.result.collectAsStateWithLifecycle()
    val training by vm.training.collectAsStateWithLifecycle()
    Page {
        SectionTitle("SESSION REPORT", tr("یک قدم به جلو", "One step forward"))
        result?.let { summary ->
            Panel {
                Text(tr("نتیجه در حافظهٔ محلی ثبت شد.", "The result was saved on this device."))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric(tr("دقت", "Accuracy"), "${number(summary.accuracy * 100, 1)}٪")
                    Metric(tr("درست", "Correct"), number(summary.correct))
                    Metric(tr("نادرست", "Incorrect"), number(summary.incorrect))
                    Metric(tr("ازدست‌رفته", "Missed"), number(summary.missed))
                    Metric(tr("زودهنگام", "Early"), number(summary.premature))
                    if (training?.exercise == Exercise.GO_NO_GO) Metric(tr("خودداری درست", "Correct inhibitions"), number(summary.withheld))
                    Metric(tr("امتیاز تجربه", "XP"), number(summary.xp))
                }
                if (training?.exercise?.module in listOf(Module.VISION, Module.REFLEX)) {
                    summary.meanResponseMs?.let { Metric(tr("میانگین پاسخ درست", "Mean correct response"), "${number(it, 1)} ms") }
                    summary.responseDeviationMs?.let { Metric(tr("انحراف معیار پاسخ", "Response standard deviation"), "${number(it, 1)} ms") }
                }
                summary.wpm?.let { Metric(tr("واژه در دقیقه", "Words per minute"), number(it, 1)) }
                if (training?.practice == true) Text(tr("این اجرای آزمایشی است؛ XP و سابقهٔ عملکرد عادی را تغییر نمی‌دهد.", "This is a test run. It does not change XP or normal performance history."))
                else Text(if (summary.goalMet) tr("معیار موفقیت این جلسه برآورده شد.", "This session met its success criteria.") else tr("معیار موفقیت هنوز برآورده نشده است؛ نتیجهٔ واقعی ثبت شد.", "The success criteria were not met. Your measured result was saved."))
                if (!summary.valid && training?.practice != true) Text(tr("این جلسه معیار اعتبار را ندارد و پاداشی نمی‌گیرد.", "This session does not meet the validity rules and earns no reward."))
            }
        } ?: Text(tr("نتیجهٔ این جلسه در تاریخچه قابل بررسی است.", "Check the saved session in your history."))
        Button(onClick = { vm.navigate(Screen.ANALYTICS) }, modifier = Modifier.fillMaxWidth()) { Text(tr("بررسی تاریخچه", "View history")) }
        OutlinedButton(onClick = { vm.navigate(Screen.HOME) }, modifier = Modifier.fillMaxWidth()) { Text(tr("بازگشت به خانه", "Back to home")) }
    }
}
