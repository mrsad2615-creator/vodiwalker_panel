package ai.neurox.app

import android.os.Bundle
import android.content.res.Configuration
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.neurox.app.data.*
import ai.neurox.app.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class NeuroxPalette(val background: Color, val panel: Color, val accent: Color, val text: Color, val muted: Color)
private val LocalPalette = staticCompositionLocalOf { NeuroxPalette(Color(0xFF10100F), Color(0xFF1B1A18), Color(0xFFD6B875), Color(0xFFF3EFE6), Color(0xFFAAA49A)) }
private val Obsidian: Color @Composable get() = LocalPalette.current.background
private val Panel: Color @Composable get() = LocalPalette.current.panel
private val Gold: Color @Composable get() = LocalPalette.current.accent
private val Ivory: Color @Composable get() = LocalPalette.current.text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { NeuroxRoot(TrainingDatabase.get(this).sessions(), AppPreferences(this)) }
    }
}

@Composable private fun NeuroxRoot(dao: SessionDao, preferences: AppPreferences) {
    val theme by preferences.theme.collectAsStateWithLifecycle(initialValue = ThemeSettings())
    NeuroxTheme(theme.mode, theme.accentHex) { NeuroxApp(dao, preferences) }
}

@Composable private fun NeuroxTheme(mode: String, accentHex: String, content: @Composable () -> Unit) {
    val palette = remember(mode, accentHex) { buildPalette(mode, accentHex) }
    CompositionLocalProvider(LocalPalette provides palette) {
        val scheme = if (mode == "light") lightColorScheme(primary = palette.accent, background = palette.background, surface = palette.panel, onSurface = palette.text, onBackground = palette.text)
        else darkColorScheme(primary = palette.accent, background = palette.background, surface = palette.panel, onSurface = palette.text, onBackground = palette.text)
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun buildPalette(mode: String, accentHex: String): NeuroxPalette {
    val accent = runCatching { Color(android.graphics.Color.parseColor("#$accentHex")) }.getOrDefault(Color(0xFFD6B875))
    return if (mode == "light") {
        val accessibleAccent = Color(accent.red * .58f, accent.green * .58f, accent.blue * .58f, accent.alpha)
        NeuroxPalette(Color(0xFFFAF7F0), Color(0xFFF0ECE3), accessibleAccent, Color(0xFF292722), Color(0xFF625D55))
    } else NeuroxPalette(Color(0xFF10100F), Color(0xFF1B1A18), accent, Color(0xFFF3EFE6), Color(0xFFAAA49A))
}

@Composable private fun NeuroxApp(dao: SessionDao, preferences: AppPreferences) {
    val sessions by dao.observeRecent().collectAsStateWithLifecycle(initialValue = emptyList())
    val language by preferences.language.collectAsStateWithLifecycle(initialValue = "en")
    val theme by preferences.theme.collectAsStateWithLifecycle(initialValue = ThemeSettings())
    val introSeen by preferences.introSeen.collectAsStateWithLifecycle(initialValue = null as Boolean?)
    var introDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val baseContext = LocalContext.current
    val localizedContext = remember(language, baseContext) {
        val config = Configuration(baseContext.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        baseContext.createConfigurationContext(config)
    }
    var selectedModule by remember { mutableStateOf<Module?>(null) }
    var page by remember { mutableStateOf("Home") }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides if (language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        if (introSeen == false && !introDismissed) {
            LaunchedEffect(introSeen, introDismissed) {
                kotlinx.coroutines.delay(900)
                runCatching { preferences.markIntroSeen() }
                introDismissed = true
            }
            NeuroxIntro(onSkip = {
                scope.launch {
                    runCatching { preferences.markIntroSeen() }
                    introDismissed = true
                }
            })
        } else Surface(Modifier.fillMaxSize(), color = Obsidian) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painter = painterResource(if (theme.mode == "light") R.drawable.neurox_emblem_light else R.drawable.neurox_emblem), contentDescription = stringResource(R.string.logo_description), modifier = Modifier.size(42.dp))
                        Column {
                            Text(stringResource(R.string.app_name), color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                            Text(stringResource(R.string.tagline), color = Ivory.copy(alpha = .55f), fontSize = 9.sp, letterSpacing = 2.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.offline_local), color = Gold.copy(alpha = .8f), fontSize = 10.sp)
                        TextButton(onClick = { scope.launch {
                            try {
                                preferences.setLanguage(if (language == "fa") "en" else "fa")
                                snackbarHost.showSnackbar(localizedContext.getString(R.string.language_saved))
                            } catch (_: Exception) { snackbarHost.showSnackbar(localizedContext.getString(R.string.language_error)) }
                        } }) { Text(stringResource(R.string.lang_toggle), color = Gold, fontSize = 11.sp) }
                    }
                }
                AnimatedContent(targetState = selectedModule to page, modifier = Modifier.weight(1f).fillMaxWidth(), label = "page") { (module, destination) ->
                    if (module != null) TrainingScreen(module, dao, language == "fa") { selectedModule = null }
                    else when (destination) {
                        "Analytics" -> AnalyticsScreen(sessions)
                        "Settings" -> ThemeStudio(theme.mode, theme.accentHex,
                            onRestore = { scope.launch {
                                try { preferences.setThemeMode("dark"); preferences.setAccent("D6B875"); snackbarHost.showSnackbar(localizedContext.getString(R.string.theme_saved)) }
                                catch (_: Exception) { snackbarHost.showSnackbar(localizedContext.getString(R.string.theme_save_error)) }
                            } },
                            onModeChange = { value -> scope.launch { savePreference(snackbarHost, localizedContext, R.string.theme_saved, R.string.theme_save_error) { preferences.setThemeMode(value) } } },
                            onAccentChange = { value -> scope.launch { savePreference(snackbarHost, localizedContext, R.string.theme_saved, R.string.theme_save_error) { preferences.setAccent(value) } } })
                        "Profile" -> SimpleSection(R.string.identity_kicker, R.string.guest_profile, R.string.profile_detail)
                        "Library" -> SimpleSection(R.string.library_kicker, R.string.library_title, R.string.library_detail)
                        else -> HomeScreen(sessions) { selectedModule = it }
                    }
                }
                NavigationBar(containerColor = Panel) {
                    listOf("Home" to R.string.home, "Training" to R.string.training, "Analytics" to R.string.analytics, "Library" to R.string.library, "Profile" to R.string.profile, "Settings" to R.string.settings).forEach { (route, label) ->
                        NavigationBarItem(selected = page == route && selectedModule == null, onClick = { page = route; selectedModule = null },
                            icon = { Image(painterResource(navIconId(route)), contentDescription = null, colorFilter = ColorFilter.tint(Gold), modifier = Modifier.size(22.dp)) },
                            label = { Text(stringResource(label), fontSize = 9.sp) })
                    }
                }
                SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable private fun NeuroxIntro(onSkip: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(durationMillis = 720)) }
    Box(Modifier.fillMaxSize().background(Obsidian)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Gold.copy(alpha = .16f), Color.Transparent), center = Offset(size.width * .5f, size.height * .42f), radius = size.minDimension * .52f))
        }
        Column(Modifier.align(Alignment.Center).graphicsLayer {
            alpha = reveal.value
            val scale = .92f + .08f * reveal.value
            scaleX = scale; scaleY = scale
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.neurox_emblem), contentDescription = stringResource(R.string.logo_description), modifier = Modifier.size(112.dp))
            Spacer(Modifier.height(14.dp))
            Text("NEUROX", color = Gold, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Text(stringResource(R.string.tagline), color = Ivory.copy(alpha = .66f), fontSize = 10.sp, letterSpacing = 2.2.sp, modifier = Modifier.padding(top = 7.dp))
        }
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Text(stringResource(R.string.skip_intro), color = Ivory.copy(alpha = .7f))
        }
    }
}

private suspend fun savePreference(host: SnackbarHostState, context: android.content.Context, success: Int, failure: Int, action: suspend () -> Unit) {
    try { action(); host.showSnackbar(context.getString(success)) }
    catch (_: Exception) { host.showSnackbar(context.getString(failure)) }
}

@Composable private fun HomeScreen(sessions: List<SessionEntity>, onStart: (Module) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(22.dp))
        Text(stringResource(R.string.train_intentionally), color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(stringResource(R.string.home_headline), color = Ivory, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(stringResource(R.string.sessions), sessions.size.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.level), "—", Modifier.weight(1f))
            val startOfToday = remember { LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            StatCard(stringResource(R.string.today), sessions.count { it.completedAtEpochMs >= startOfToday }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.choose_training), color = Ivory.copy(alpha = .7f), fontSize = 11.sp, letterSpacing = 1.6.sp)
        Module.entries.forEach { module ->
            Card(onClick = { onStart(module) }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Gold.copy(alpha = .12f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Image(painter = painterResource(moduleIconId(module)), contentDescription = stringResource(moduleSubtitleId(module)), colorFilter = ColorFilter.tint(Gold), modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(module.title, color = Ivory, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(stringResource(moduleSubtitleId(module)), color = Ivory.copy(alpha = .55f), fontSize = 12.sp)
                    }
                    Text(stringResource(R.string.start), color = Gold, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (sessions.isEmpty()) Text(stringResource(R.string.no_history), color = Ivory.copy(alpha = .55f), fontSize = 12.sp, lineHeight = 19.sp)
        else {
            Text(stringResource(R.string.recent_session), color = Gold, fontSize = 10.sp, letterSpacing = 1.5.sp)
            val last = sessions.first()
            Text(stringResource(R.string.accuracy_value, last.module, Scoring.accuracy(last.correct, last.total)), color = Ivory, modifier = Modifier.padding(top = 7.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable private fun SimpleSection(kicker: Int, title: Int, detail: Int) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(kicker), color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(stringResource(title), color = Ivory, fontSize = 28.sp, modifier = Modifier.padding(top = 10.dp))
        Text(stringResource(detail), color = Ivory.copy(alpha = .62f), fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable private fun ThemeStudio(mode: String, accentHex: String, onRestore: () -> Unit, onModeChange: (String) -> Unit, onAccentChange: (String) -> Unit) {
    val presets = listOf(
        R.string.accent_champagne to "D6B875", R.string.accent_sand to "C8B99A", R.string.accent_beige to "C9A982",
        R.string.accent_stone to "9A9388", R.string.accent_rose to "C78F98", R.string.accent_olive to "87936B",
        R.string.accent_lavender to "A69BC8", R.string.accent_silver to "AAB4BE", R.string.accent_red to "E53935",
        R.string.accent_blue to "2878D0", R.string.accent_green to "31875B"
    )
    var customHex by remember { mutableStateOf(accentHex) }
    LaunchedEffect(accentHex) { customHex = accentHex }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(stringResource(R.string.settings_kicker), color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(stringResource(R.string.appearance_title), color = Ivory, fontSize = 28.sp, modifier = Modifier.padding(vertical = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "dark", onClick = { onModeChange("dark") }, label = { Text(stringResource(R.string.dark_theme)) })
            FilterChip(selected = mode == "light", onClick = { onModeChange("light") }, label = { Text(stringResource(R.string.light_theme)) })
        }
        Text(stringResource(R.string.accent_title), color = Gold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        presets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, hex) ->
                    FilterChip(selected = accentHex.equals(hex, ignoreCase = true), onClick = { onAccentChange(hex) }, label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor("#$hex")), RoundedCornerShape(50)))
                            Text(stringResource(label), modifier = Modifier.padding(start = 6.dp), fontSize = 11.sp)
                        }
                    })
                }
            }
        }
        OutlinedTextField(value = customHex, onValueChange = { customHex = it.filter(Char::isLetterOrDigit).take(6).uppercase() },
            label = { Text(stringResource(R.string.custom_hex)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        Button(onClick = { onAccentChange(customHex) }, enabled = customHex.matches(Regex("^[0-9A-F]{6}$")), modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.apply_color))
        }
        TextButton(onClick = onRestore, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.restore_theme)) }
    }
}

private fun navIconId(route: String): Int = when (route) {
    "Home" -> R.drawable.ic_nav_home
    "Training" -> R.drawable.ic_nav_training
    "Analytics" -> R.drawable.ic_nav_analytics
    "Library" -> R.drawable.ic_nav_library
    "Profile" -> R.drawable.ic_nav_profile
    else -> R.drawable.ic_nav_settings
}

private fun moduleIconId(module: Module): Int = when (module) {
    Module.VISION -> R.drawable.ic_vision
    Module.REFLEX -> R.drawable.ic_reflex
    Module.READ -> R.drawable.ic_read
    Module.TYPE -> R.drawable.ic_type
}

private fun moduleSubtitleId(module: Module): Int = when (module) {
    Module.VISION -> R.string.vision_subtitle
    Module.REFLEX -> R.string.reflex_subtitle
    Module.READ -> R.string.read_subtitle
    Module.TYPE -> R.string.type_subtitle
}

@Composable private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = LocalPalette.current.muted, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(value, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun TrainingScreen(module: Module, dao: SessionDao, isPersian: Boolean, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val saveErrorText = stringResource(R.string.save_error)
    val retryErrorText = stringResource(R.string.save_retry_error)
    val startedAt = remember { System.nanoTime() }
    val run = remember(module) { TrainingRun() }
    var revision by remember { mutableIntStateOf(0) }
    var prompt by remember(module, isPersian) { mutableStateOf(ExerciseFactory.create(module, 1, if (isPersian) "fa" else "en")) }
    var stimulusAt by remember { mutableLongStateOf(System.nanoTime()) }
    var typed by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val count = remember(revision) { run.count }
    val correct = remember(revision) { run.correct }
    val difficulty = remember(revision) { run.difficulty }

    fun answer(response: String) {
        val latency = ((System.nanoTime() - stimulusAt).coerceAtLeast(0L)) / 1_000_000
        run.record(normalizeAnswer(response, isPersian).equals(prompt.answer, true), latency)
        revision++
        if (run.completed) {
            val summary = run.summary(module, ((System.nanoTime() - startedAt).coerceAtLeast(0L)) / 1_000_000)
            saving = true
            scope.launch {
                try {
                    dao.insert(SessionEntity(module = module.name, correct = summary.correct, total = summary.total, durationMs = summary.durationMs, meanResponseMs = run.meanLatencyMs(), completedAtEpochMs = System.currentTimeMillis()))
                    saved = true
                } catch (_: Exception) { saveError = saveErrorText }
                finally { saving = false }
            }
        } else {
            prompt = ExerciseFactory.create(module, run.difficulty, if (isPersian) "fa" else "en")
            stimulusAt = System.nanoTime()
            if (module == Module.TYPE) typed = ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onExit) { Text(stringResource(R.string.back), color = Gold) }
            Text(stringResource(R.string.round_label, module.title, (count + 1).coerceAtMost(10)), color = Ivory.copy(alpha = .6f), modifier = Modifier.padding(top = 14.dp), fontSize = 11.sp)
        }
        Spacer(Modifier.height(36.dp))
        if (run.completed) {
            Text(stringResource(if (saved) R.string.session_saved else R.string.session_complete), color = Gold, letterSpacing = 2.sp)
            Text("${run.summary(module, ((System.nanoTime() - startedAt).coerceAtLeast(0L)) / 1_000_000).accuracyPercent}%", color = Ivory, fontSize = 58.sp, modifier = Modifier.padding(12.dp))
            Text(stringResource(R.string.correct_of_total, correct, count), color = Ivory.copy(alpha = .65f))
            if (module == Module.REFLEX) Text(stringResource(R.string.mean_response, run.meanLatencyMs()), color = Ivory.copy(alpha = .65f), modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.measurement_note), color = LocalPalette.current.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
            if (saving) CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
            saveError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(enabled = !saving, onClick = {
                    saveError = null; saving = true
                    retrySave(run, module, startedAt, dao, scope, onSaved = { saved = true; saving = false }, onFailure = { saveError = retryErrorText; saving = false })
                }) { Text(stringResource(R.string.retry_saving), color = Gold) }
            }
            Button(onClick = onExit, enabled = saved) { Text(stringResource(R.string.done)) }
        } else {
            Text(stringResource(instructionId(module)), color = LocalPalette.current.muted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Text(prompt.stimulus, color = if (module == Module.REFLEX) reflexInk(prompt.answer) else Gold,
                fontSize = if (module == Module.TYPE) 28.sp else 44.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 40.sp)
            Spacer(Modifier.height(26.dp))
            if (module == Module.TYPE) {
                BasicTextField(value = typed, onValueChange = { typed = it.take(120) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ivory, fontSize = 18.sp))
                Spacer(Modifier.height(12.dp))
                Button(onClick = { answer(typed) }, enabled = !saving) { Text(stringResource(R.string.submit)) }
            } else prompt.choices.forEach { choice ->
                OutlinedButton(onClick = { answer(choice) }, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(choiceLabel(choice, isPersian), color = Ivory)
                }
            }
            Text(stringResource(R.string.difficulty_progress, difficulty, count), color = LocalPalette.current.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}

private fun retrySave(run: TrainingRun, module: Module, startedAt: Long, dao: SessionDao, scope: CoroutineScope, onSaved: () -> Unit, onFailure: () -> Unit) {
    scope.launch {
        try {
            val summary = run.summary(module, ((System.nanoTime() - startedAt).coerceAtLeast(0L)) / 1_000_000)
            dao.insert(SessionEntity(module = module.name, correct = summary.correct, total = summary.total, durationMs = summary.durationMs, meanResponseMs = run.meanLatencyMs(), completedAtEpochMs = System.currentTimeMillis()))
            onSaved()
        } catch (_: Exception) { onFailure() }
    }
}

private fun instructionId(module: Module): Int = when (module) {
    Module.VISION -> R.string.vision_instruction
    Module.REFLEX -> R.string.reflex_instruction
    Module.READ -> R.string.read_instruction
    Module.TYPE -> R.string.type_instruction
}

@Composable private fun choiceLabel(choice: String, fa: Boolean): String = if (!fa) choice else when (choice) {
    "GOLD" -> stringResource(R.string.color_gold); "IVORY" -> stringResource(R.string.color_ivory)
    "BLUE" -> stringResource(R.string.color_blue); "GREEN" -> stringResource(R.string.color_green); else -> choice
}

private fun normalizeAnswer(value: String, fa: Boolean): String = if (!fa) value.trim() else when (value.trim()) {
    "طلایی" -> "GOLD"; "عاجی" -> "IVORY"; "آبی" -> "BLUE"; "سبز" -> "GREEN"; else -> value.trim()
}

@Composable private fun reflexInk(name: String): Color = when (name) {
    "GOLD" -> Gold; "IVORY" -> Ivory; "BLUE" -> Color(0xFF76A9FF); "GREEN" -> Color(0xFF68C28A); else -> Gold
}

@Composable private fun AnalyticsScreen(sessions: List<SessionEntity>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Text(stringResource(R.string.analytics), color = Gold, letterSpacing = 2.sp)
        Text(stringResource(R.string.analytics_title), color = Ivory, fontSize = 29.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        if (sessions.isEmpty()) Text(stringResource(R.string.empty_analytics), color = LocalPalette.current.muted)
        Module.entries.forEach { module ->
            val records = sessions.filter { it.module == module.name }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(module.title, color = Gold, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.session_count, records.size), color = LocalPalette.current.muted, fontSize = 12.sp)
                        if (module == Module.REFLEX && records.isNotEmpty()) Text(stringResource(R.string.mean_response_analytics, records.map { it.meanResponseMs }.average().toLong()), color = LocalPalette.current.muted, fontSize = 11.sp)
                    }
                    Text(if (records.isEmpty()) "—" else "${records.map { Scoring.accuracy(it.correct, it.total) }.average().toInt()}%", color = Ivory, fontSize = 22.sp)
                }
            }
        }
        Text(stringResource(R.string.analytics_note), color = LocalPalette.current.muted, fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 18.dp))
    }
}
