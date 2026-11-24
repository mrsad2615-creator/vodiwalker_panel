@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.neurox.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neurox.data.*
import com.neurox.design.*
import com.neurox.domain.*
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val LocalPersian = staticCompositionLocalOf { true }
@Composable internal fun tr(fa: String, en: String) = if (LocalPersian.current) fa else en
@Composable internal fun number(value: Number, decimals: Int = 0): String = NumberFormat.getNumberInstance(if (LocalPersian.current) Locale.forLanguageTag("fa") else Locale.ENGLISH).apply { maximumFractionDigits = decimals }.format(value)
@Composable internal fun exerciseName(exercise: Exercise): String = Catalogue.get(exercise).let { tr(it.fa, it.en) }
internal fun glyph(module: Module) = when (module) { Module.VISION -> Glyph.VISION; Module.REFLEX -> Glyph.REFLEX; Module.READ -> Glyph.READ; Module.TYPE -> Glyph.TYPE }

@Composable
fun NeuroxApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val route by vm.screen.collectAsStateWithLifecycle()
    val profile = state.profile
    val fa = profile?.language != "EN"
    val dark = when (profile?.theme) { "LIGHT" -> false; "SYSTEM" -> androidx.compose.foundation.isSystemInDarkTheme(); else -> true }
    NeuroxTheme(profile?.theme ?: "DARK", if (dark) profile?.accent ?: 0xFFD4B982 else profile?.lightAccent ?: 0xFF765A28, profile?.textScale ?: 1f) {
        CompositionLocalProvider(LocalPersian provides fa, LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val screen = runCatching { Screen.valueOf(route) }.getOrDefault(Screen.HOME)
            BackHandler(enabled = screen != Screen.HOME) {
                if (screen == Screen.RUN) vm.pause()
                vm.navigate(when (screen) { Screen.COLOR -> Screen.SETTINGS; Screen.BUILDER -> Screen.LIBRARY; else -> Screen.HOME })
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (screen != Screen.RUN) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NeuroxEmblem(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("NEUROX", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
                        Text(tr("کاملاً آفلاین", "OFFLINE"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { vm.navigate(if (screen in listOf(Screen.SETTINGS, Screen.COLOR)) Screen.HOME else Screen.SETTINGS) }) {
                            NeuroxIcon(Glyph.SETTINGS, label = tr("تنظیمات", "Settings"))
                        }
                    }
                },
                bottomBar = {
                    if (screen !in listOf(Screen.RUN, Screen.RESULT)) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                            listOf(
                                Triple(Screen.HOME, Glyph.HOME, tr("خانه", "Home")), Triple(Screen.TRAINING, Glyph.TRAINING, tr("تمرین", "Train")),
                                Triple(Screen.ANALYTICS, Glyph.ANALYTICS, tr("تحلیل", "Stats")), Triple(Screen.LIBRARY, Glyph.LIBRARY, tr("کتابخانه", "Library")),
                                Triple(Screen.PROFILE, Glyph.PROFILE, tr("پروفایل", "Profile")),
                            ).forEach { (destination, icon, label) ->
                                NavigationBarItem(selected = screen == destination, onClick = { vm.navigate(destination) }, icon = { NeuroxIcon(icon, tint = if (screen == destination) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }, label = { Text(label, maxLines = 1) })
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when {
                        state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(tr("باز کردن داده‌های محلی…", "Opening local data…"))
                        }
                        profile == null -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                            Text(tr("داده‌های محلی باز نشدند.", "Local data could not be opened."))
                            Button(onClick = vm::load) { Text(tr("تلاش دوباره", "Retry")) }
                        }
                        else -> when (screen) {
                            Screen.HOME -> HomeScreen(state, vm)
                            Screen.TRAINING -> TrainingSetup(state, vm)
                            Screen.ANALYTICS -> AnalyticsScreen(state)
                            Screen.LIBRARY -> LibraryScreen(state, vm)
                            Screen.PROFILE -> ProfileScreen(state, vm)
                            Screen.SETTINGS -> SettingsScreen(state, vm)
                            Screen.COLOR -> ColorStudio(state, vm)
                            Screen.BUILDER -> ExerciseBuilderScreen(state, vm)
                            Screen.RUN -> TrainingRun(state, vm)
                            Screen.RESULT -> ResultScreen(vm)
                        }
                    }
                }
            }
            if (state.error != null) AlertDialog(
                onDismissRequest = vm::clearError,
                title = { Text(tr("عملیات انجام نشد", "Operation failed")) },
                text = { Text(tr("ذخیره یا خواندن داده‌ها کامل نشد. جلسه متوقف شده است. داده‌های قبلی حفظ می‌شوند؛ دوباره تلاش کنید.", "The data operation did not finish. Training is paused. Previous data is preserved. Try again.")) },
                confirmButton = { TextButton(onClick = { vm.clearError(); vm.retrySave() }) { Text(tr("تلاش دوباره", "Retry")) } },
                dismissButton = { TextButton(onClick = vm::clearError) { Text(tr("بستن", "Close")) } },
            )
        }
    }
}

@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}
@Composable internal fun SectionTitle(eyebrow: String, title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (description != null) Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable internal fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun EmptyState() {
    Panel {
        Text(tr("مسیر شما از اینجا شروع می‌شود", "Your progress starts here"), style = MaterialTheme.typography.titleMedium)
        Text(tr("هنوز تمرین معتبری ثبت نشده است. پس از تکمیل تمرین، نتیجهٔ واقعی اینجا نمایش داده می‌شود.", "No valid training session is stored yet. Complete an exercise to see your measured results."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeScreen(state: AppState, vm: AppViewModel) {
    val profile = requireNotNull(state.profile)
    val completed = state.sessions.filter { it.status == "COMPLETE" && it.valid && !it.baseline }
    val xp = completed.sumOf { it.xp.toLong() }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val days = completed.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
    var streak = 0
    var date = if (today in days) today else today.minusDays(1)
    while (date in days) { streak++; date = date.minusDays(1) }
    Page {
        Text(tr("خوش آمدید، ${if (profile.guest) "مهمان" else profile.name}", "Welcome, ${profile.name}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("TRAIN YOUR POTENTIAL", tr("هر روز، دقیق‌تر\nاز دیروز.", "A sharper mind.\nOne session at a time."), tr("تمرینِ متمرکز. پیشرفتِ قابل‌اندازه‌گیری.", "Focused training. Measurable progress."))
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(tr("سطح", "LEVEL"), number(Progression.level(xp)))
                Metric(tr("امتیاز تجربه", "TOTAL XP"), number(xp))
                Metric(tr("روز پیوسته", "DAY STREAK"), number(streak))
            }
            LinearProgressIndicator(progress = { Progression.progressInLevel(xp) }, modifier = Modifier.fillMaxWidth())
            Text(tr("${number(250 - xp % 250)} امتیاز تا سطح بعدی", "${number(250 - xp % 250)} XP to the next level"), style = MaterialTheme.typography.bodySmall)
        }
        if (state.interrupted != null) Panel {
            Text(tr("یک جلسهٔ ناتمام دارید", "An interrupted session is saved"), style = MaterialTheme.typography.titleMedium)
            Text(tr("دور ناتمام پس از ادامه از نو آغاز می‌شود و به‌عنوان تکمیل‌شده حساب نمی‌شود.", "The unfinished round restarts on resume. It is not counted as completed."))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = vm::restore, enabled = !state.busy) { Text(tr("ادامه", "Resume")) }
                TextButton(onClick = vm::discard, enabled = !state.busy) { Text(tr("کنار گذاشتن", "Discard")) }
            }
        }
        Text(tr("چهار مسیر، یک پتانسیل", "FOUR SYSTEMS. ONE POTENTIAL."), style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints {
            if (maxWidth >= 360.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Catalogue.primary.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { index, definition -> ModuleCard(definition, Catalogue.primary.indexOf(definition) + 1, Modifier.weight(1f)) { vm.chooseExercise(definition.exercise) } }
                } }
            } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Catalogue.primary.forEachIndexed { index, definition -> ModuleCard(definition, index + 1) { vm.chooseExercise(definition.exercise) } }
            }
        }
        if (completed.isEmpty()) EmptyState() else {
            Panel {
                Text(tr("ریتم این هفته", "YOUR WEEKLY RHYTHM"), style = MaterialTheme.typography.titleMedium)
                val weekly = completed.count { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() >= today.minusDays(6) }
                Text(tr("${number(weekly)} جلسه در هفت روز گذشته", "${number(weekly)} sessions in the past seven days"))
                Text(tr("${number(completed.sumOf { it.durationMs } / 60_000.0, 1)} دقیقه تمرین ثبت‌شده", "${number(completed.sumOf { it.durationMs } / 60_000.0, 1)} measured training minutes"))
            }
            Text(tr("آخرین فعالیت", "RECENT ACTIVITY"), style = MaterialTheme.typography.titleMedium)
            completed.take(3).forEach { SessionRow(it) }
        }
        Text(tr("این برنامه ابزار تشخیص یا درمان پزشکی نیست.", "This application does not diagnose or treat medical conditions."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModuleCard(definition: ExerciseDefinition, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NeuroxIcon(glyph(definition.exercise.module), Modifier.size(30.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text("0$index", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(definition.exercise.module.name, style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
            Text(tr(definition.fa, definition.en), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SessionRow(session: SessionEntity) {
    val exercise = runCatching { Exercise.valueOf(session.exercise) }.getOrNull()
    val details = remember(session.checkpoint) { runCatching { CheckpointCodec.decode(session.checkpoint) } }
    Panel {
        Text(details.getOrNull()?.config?.customExerciseName ?: exercise?.let { exerciseName(it) } ?: session.module, style = MaterialTheme.typography.titleMedium)
        val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd · HH:mm"))
        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(tr("دقت", "Accuracy"), "${number(session.accuracy * 100, 1)}٪")
            if (session.module in listOf("VISION", "REFLEX")) session.responseMs?.let { Metric(tr("میانگین پاسخ درست", "Mean correct response"), "${number(it, 0)} ms") }
            session.wpm?.let { Metric(tr("واژه در دقیقه", "Words per minute"), number(it, 1)) }
            Metric(tr("زمان فعال", "Active time"), "${number(session.durationMs / 1000.0, 1)} s")
        }
        if (exercise == Exercise.GO_NO_GO) {
            val decoded = remember(details) { details.mapCatching { Scoring.summarize(it) } }
            decoded.getOrNull()?.let { summary ->
                Text(tr("خودداری درست: ${number(summary.withheld)} · لمس نادرست: ${number(summary.incorrect)} · زودهنگام: ${number(summary.premature)} · ازدست‌رفته: ${number(summary.missed)}",
                    "Correct inhibitions: ${number(summary.withheld)} · False alarms: ${number(summary.incorrect)} · Early: ${number(summary.premature)} · Missed: ${number(summary.missed)}"), style = MaterialTheme.typography.bodySmall)
            }
        }
        details.getOrNull()?.takeIf { it.config.customExerciseId != null }?.let { checkpoint ->
            val summary = Scoring.summarize(checkpoint)
            Text(tr("دقت هدف: ${number(checkpoint.config.targetAccuracyPercent)}٪ · حداکثر دور ناموفق: ${number(checkpoint.config.errorAllowance)}", "Target accuracy: ${number(checkpoint.config.targetAccuracyPercent)}% · Unsuccessful-round limit: ${number(checkpoint.config.errorAllowance)}"), style = MaterialTheme.typography.bodySmall)
            Text(if (summary.goalMet) tr("معیار موفقیت برآورده شد", "Success criteria met") else tr("معیار موفقیت برآورده نشد", "Success criteria not met"), style = MaterialTheme.typography.bodySmall)
        }
        if (details.isFailure) Text(tr("جزئیات این جلسه خوانده نشد؛ اطلاعات خلاصه نمایش داده شده است.", "Round details could not be read. Only the stored summary is shown."), color = MaterialTheme.colorScheme.error)
    }
}
