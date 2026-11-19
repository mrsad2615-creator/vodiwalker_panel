package dev.mrsad.hr

import android.Manifest
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.compose.*
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Instrument() }
    }
}

@Composable
fun Instrument(vm: Desk = viewModel()) {
    val p by vm.prefs.collectAsStateWithLifecycle(emptyPreferences())
    fun get(k: String, d: String) = p[stringPreferencesKey(k)] ?: d
    val theme = get("theme", "Obsidian")
    val reduced = get("reduce", "false") == "true"
    var page by rememberSaveable { mutableStateOf("Studio") }
    var watch by rememberSaveable { mutableStateOf(false) }
    val timer by vm.repo.timer.collectAsStateWithLifecycle()
    val sw by vm.repo.watch.collectAsStateWithLifecycle()
    val c = if (watch) sw else timer
    val active = c.phase == Phase.RUNNING
    var escape by rememberSaveable { mutableStateOf(false) }
    val focus = active && !escape && get("focus", "true") == "true"
    val activity = LocalContext.current as ComponentActivity
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val keep = timer.phase == Phase.RUNNING && get("screen", "false") == "true"
    DisposableEffect(keep) {
        if (keep) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(page != "Studio" || focus) {
        if (page != "Studio") page = "Studio" else escape = true
    }
    CompositionLocalProvider(Reduced provides reduced) {
        val m = blend(finishes[theme] ?: finishes.values.first())
        CompositionLocalProvider(Metal provides m) {
            val scheme =
                if (theme == "Pure")
                    lightColorScheme(primary = m.beam, surface = m.panel, onSurface = m.ink)
                else darkColorScheme(primary = m.beam, surface = m.panel, onSurface = m.ink)
            MaterialTheme(colorScheme = scheme) {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    m.panel,
                                    m.bg,
                                    m.bg,
                                    m.rim.copy(alpha = .32f).compositeOver(m.bg),
                                ),
                            ),
                        )
                        .safeDrawingPadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 20.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Type("MrsadHR", 25, weight = FontWeight.Bold, spacing = -1.3f)
                                Tag("TEMPORAL INSTRUMENTS")
                            }
                            if (focus) Pad("Exit focus") { escape = true }
                            else if (page != "Studio") Pad("←  Studio") { page = "Studio" }
                            else
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Tool("◐", "Themes") { page = "Themes" }
                                    Tool("♫", "Sounds") { page = "Sounds" }
                                    Tool("☷", "Settings") { page = "Settings" }
                                }
                        }
                        HorizontalDivider(color = m.rim.copy(.34f))
                        AnimatedContent(
                            page,
                            transitionSpec = {
                                fadeIn(tween(if (reduced) 0 else M1.PAGE)) togetherWith
                                    fadeOut(tween(if (reduced) 0 else 180))
                            },
                            label = "workspace",
                        ) { screen ->
                            Column(
                                Modifier.fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                when (screen) {
                                    "Themes" -> Themes(theme) { vm.pref("theme", it) }
                                    "Sounds" ->
                                        Sounds(
                                            get("sound", "Minimal"),
                                            get("volume", "0.7").toFloat(),
                                            vm,
                                        )
                                    "Settings" -> Settings(p, vm) { page = "Sounds" }
                                    else -> {
                                        if (!focus) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Pad("Countdown", Modifier.weight(1f), !watch) {
                                                    watch = false
                                                }
                                                Pad("Stopwatch", Modifier.weight(1f), watch) {
                                                    watch = true
                                                }
                                            }
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Tag(
                                                if (watch) "CHRONOGRAPH  /  02"
                                                else "COUNTDOWN  /  01",
                                            )
                                            Tag("●  ${theme.uppercase()}", m.beam)
                                        }
                                        Readout(c)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Pad(
                                                if (active) "Ⅱ   Pause"
                                                else
                                                    when (c.phase) {
                                                        Phase.PAUSED -> "▶   Resume"
                                                        Phase.COMPLETE -> "↻   Restart"
                                                        else -> "▶   Start"
                                                    },
                                                Modifier.weight(1.7f),
                                                true,
                                            ) {
                                                if (!active) {
                                                    escape = false
                                                    if (
                                                        !watch &&
                                                            Build.VERSION.SDK_INT >= 33 &&
                                                            activity.checkSelfPermission(
                                                                Manifest.permission
                                                                    .POST_NOTIFICATIONS,
                                                            ) !=
                                                                android.content.pm.PackageManager
                                                                    .PERMISSION_GRANTED
                                                    )
                                                        permission.launch(
                                                            Manifest.permission.POST_NOTIFICATIONS,
                                                        )
                                                }
                                                vm.action(watch, if (active) "pause" else "start")
                                            }
                                            Pad("Reset", Modifier.weight(1f)) {
                                                vm.action(watch, "reset")
                                            }
                                            if (watch && active)
                                                Pad("Lap") { vm.action(true, "lap") }
                                        }
                                        if (!focus) {
                                            if (!watch) TimeBank(c, vm)
                                            else Laps(c) { vm.action(true, "clear") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Tool(glyph: String, label: String, click: () -> Unit) {
    Box(
        Modifier.size(48.dp)
            .clip(CircleShape)
            .background(Metal.current.panel)
            .clickable(onClick = click)
            .then(Modifier.accessLabel(label)),
        contentAlignment = Alignment.Center,
    ) {
        Type(glyph, 21)
    }
}

fun Modifier.accessLabel(label: String) = this.semantics { contentDescription = label }

@Composable
fun Readout(c: Clock) {
    val m = Metal.current
    val life = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val now by
        produceState(SystemClock.elapsedRealtime(), c, life) {
            life.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    value = SystemClock.elapsedRealtime()
                    if (c.phase != Phase.RUNNING) break
                    delay(if (c.watch) 48 else 125)
                }
            }
        }
    val ms = c.value(now)
    val progress =
        if (c.watch) (ms % 60000) / 60000f else if (c.goal > 0) ms.toFloat() / c.goal else 0f
    val complete = c.phase == Phase.COMPLETE
    val glow by
        animateFloatAsState(
            if (complete) 1f else 0f,
            tween(if (Reduced.current) 0 else M1.FINISH),
            label = "completion light",
        )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.widthIn(max = 350.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Brush.radialGradient(
                        listOf(m.beam.copy(.08f + .22f * glow), Color.Transparent),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            DialFace(progress, Modifier.fillMaxSize())
            if (complete && !Reduced.current) {
                val halo by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.resonance))
                LottieAnimation(halo, modifier = Modifier.fillMaxSize())
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Tag(if (complete) "TIME IS UP" else c.phase.name, m.beam)
                Type(
                    digits(ms, !c.watch),
                    if (ms >= 3600000) 32 else 49,
                    weight = FontWeight.Medium,
                    spacing = -1.8f,
                )
                Tag(
                    if (c.watch) String.format(Locale.ROOT, "%03d MS", ms % 1000)
                    else if (ms >= 3600000) "HR : MIN : SEC" else "MIN : SEC",
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(22.dp).height(2.dp).background(m.beam))
            }
        }
    }
}

@Composable
fun Laps(c: Clock, clear: () -> Unit) {
    Section("Lap register", "${c.laps.size} MARKS")
    if (c.laps.isEmpty()) Type("Record a lap while the chronograph runs.", 14, Metal.current.muted)
    else {
        Pad("Clear history", click = clear)
        c.laps.withIndex().toList().asReversed().forEach { (i, t) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Tag("LAP %02d".format(i + 1))
                Type("${digits(t)}.%03d".format(t % 1000))
            }
        }
    }
}
