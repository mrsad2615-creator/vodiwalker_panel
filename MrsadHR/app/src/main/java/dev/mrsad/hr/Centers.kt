package dev.mrsad.hr

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun Section(name: String, code: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Type(name, 21, weight = FontWeight.Medium)
        Tag(code)
    }
}

@Composable
fun Plate(body: @Composable ColumnScope.() -> Unit) {
    val m = Metal.current
    val sh = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(m.panel, m.panel.copy(.55f))), sh)
            .border(1.dp, m.rim.copy(.36f), sh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = body,
    )
}

@Composable
fun TimeBank(c: Clock, vm: Desk) {
    val list by vm.presets.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Preset?>(null) }
    val enabled = c.phase != Phase.RUNNING
    fun set(t: Long) {
        if (enabled) vm.repo.update(false) { Clock(goal = t) }
    }
    Section("Time bank", "MEMORY / 04")
    if (enabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 5, 10, 30).forEach {
                Pad("${it}m", Modifier.weight(1f), c.goal == it * 60000L) { set(it * 60000L) }
            }
        }
        Plate {
            Tag("DURATION   /   HR : MIN : SEC")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pad("Edit time", Modifier.weight(1f)) { editing = Preset(-1, "", c.goal) }
                Pad("Save preset", Modifier.weight(1f)) { editing = Preset(0, "", c.goal) }
            }
        }
        list.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pad("${it.name}  ·  ${digits(it.duration)}", Modifier.weight(1f)) {
                    set(it.duration)
                }
                Pad("Edit") { editing = it }
            }
        }
    } else Type("Pause to adjust the duration.", 14, Metal.current.muted)
    editing?.let { item ->
        EditTime(
            item,
            { editing = null },
            { p ->
                if (p.id < 0) set(p.duration) else vm.save(p)
                editing = null
            },
            {
                vm.delete(item)
                editing = null
            },
        )
    }
}

@Composable
fun EditTime(item: Preset, close: () -> Unit, save: (Preset) -> Unit, delete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(item.name) }
    var h by rememberSaveable { mutableStateOf((item.duration / 3600000).toString()) }
    var m by rememberSaveable { mutableStateOf((item.duration / 60000 % 60).toString()) }
    var s by rememberSaveable { mutableStateOf((item.duration / 1000 % 60).toString()) }
    val duration =
        ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) *
            1000
    AlertDialog(
        onDismissRequest = close,
        title = { Type(if (item.id < 0) "Set duration" else "Preset memory", 22) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.id >= 0)
                    OutlinedTextField(
                        name,
                        { name = it.take(40) },
                        singleLine = true,
                        label = { Text("Preset name") },
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(Triple("Hr", h, 99), Triple("Min", m, 59), Triple("Sec", s, 59))
                        .forEachIndexed { i, (label, v, max) ->
                            OutlinedTextField(
                                v,
                                {
                                    if (
                                        it.length <= 2 &&
                                            it.all(Char::isDigit) &&
                                            (it.toIntOrNull() ?: 0) <= max
                                    )
                                        when (i) {
                                            0 -> h = it
                                            1 -> m = it
                                            else -> s = it
                                        }
                                },
                                Modifier.weight(1f),
                                label = { Text(label) },
                                singleLine = true,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                }
                if (item.id > 0) TextButton(onClick = delete) { Text("Delete preset") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = duration > 0 && (item.id < 0 || name.isNotBlank()),
                onClick = { save(item.copy(name = name.trim(), duration = duration)) },
            ) {
                Text("Apply")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

@Composable
fun Themes(selected: String, choose: (String) -> Unit) {
    Section("Material library", "FINISH / 03")
    finishes.entries.forEachIndexed { i, (name, m) ->
        CompositionLocalProvider(Metal provides m) {
            val sh = RoundedCornerShape(22.dp)
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(m.panel, m.bg, m.rim)), sh)
                    .border(1.dp, m.rim, sh)
                    .clickable { choose(name) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag("MATERIAL  /  0${i+1}")
                    Type(name, 27)
                    Type(
                        listOf("Brushed chrome", "Frosted porcelain", "Ionised violet")[i],
                        13,
                        m.muted,
                    )
                    Tag(if (selected == name) "● ACTIVE" else "SELECT FINISH", m.beam)
                }
                DialFace(.72f, Modifier.size(98.dp))
            }
        }
    }
}

@Composable
fun Sounds(selected: String, vol: Float, vm: Desk) {
    val ctx = LocalContext.current
    val player = remember { Player(ctx) }
    var playing by remember { mutableStateOf("") }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            player.stop()
            playing = ""
        }
    }
    Section("Sound library", "AUDIO / 04")
    tracks.forEachIndexed { i, n ->
        Plate {
            Tag("0${i+1}   /   ${n.uppercase()}")
            Section(
                listOf("Single chime", "Pulse code", "Low orbit", "Afterglow")[i],
                if (n == selected) "● SELECTED" else "",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pad(if (playing == n) "Stop" else "Preview", Modifier.weight(1f)) {
                    if (playing == n) {
                        player.stop()
                        playing = ""
                    } else {
                        playing = n
                        player.play(n, vol) { playing = "" }
                    }
                }
                Pad(
                    if (n == selected) "Selected" else "Select",
                    Modifier.weight(1f),
                    n == selected,
                ) {
                    vm.pref("sound", n)
                }
            }
        }
    }
    Plate {
        Section("Level", "${(vol*100).toInt()}%")
        var local by remember(vol) { mutableFloatStateOf(vol) }
        Slider(
            local,
            { local = it },
            onValueChangeFinished = { vm.pref("volume", local.toString()) },
        )
    }
}

@Composable
fun Settings(p: Preferences, vm: Desk, sounds: () -> Unit) {
    Section("Preferences", "SYSTEM / M1")
    Plate {
        listOf(
                Triple("focus", "Focus while running", true),
                Triple("haptic", "Completion vibration", true),
                Triple("reduce", "Reduced motion", false),
                Triple("screen", "Keep screen on", false),
                Triple("notify", "Completion notification", true),
            )
            .forEach { (key, label, d) ->
                val checked = p[stringPreferencesKey(key)]?.toBoolean() ?: d
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { Type(label) }
                    Switch(checked, { vm.pref(key, it.toString()) })
                }
            }
    }
    Pad("Sound preferences  →", Modifier.fillMaxWidth(), click = sounds)
    Plate {
        Tag("INSTRUMENT SPECIFICATION")
        Type("MrsadHR  /  1.0", 22)
        Type("Motion Engine M1 · Monotonic timebase", 14, Metal.current.muted)
    }
}
