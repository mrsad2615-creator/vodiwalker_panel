package com.mesad.time

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.mesad.time.data.local.AlarmEntity
import com.mesad.time.data.local.DatabaseProvider
import com.mesad.time.data.local.LapEntity
import com.mesad.time.data.local.MesadDatabase
import com.mesad.time.data.local.TimerEntity
import com.mesad.time.domain.model.ClockEngine
import com.mesad.time.domain.model.JalaliDate
import com.mesad.time.domain.model.StudioPreset
import com.mesad.time.domain.model.TimerState
import com.mesad.time.platform.alarm.AlarmManagerScheduler
import com.mesad.time.platform.render.MesadClockView
import com.mesad.time.ui.clock.MesadClock
import com.mesad.time.ui.theme.Champagne
import com.mesad.time.ui.theme.ElectricCyan
import com.mesad.time.ui.theme.Graphite
import com.mesad.time.ui.theme.MesadTheme
import com.mesad.time.ui.theme.Obsidian
import com.mesad.time.ui.theme.Titanium
import com.mesad.time.ui.theme.TitaniumHi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

private enum class Screen(val title: String) {
    HOME("Home"), WORLD("World Clock"), ALARM("Alarm"), STOPWATCH("Stopwatch"), TIMER("Timer"), STUDIO("Clock Studio"), THEMES("Themes"), SETTINGS("Settings")
}
private data class City(val name: String, val zone: String)
private val popularCities = listOf(
    City("New York", "America/New_York"), City("London", "Europe/London"), City("Paris", "Europe/Paris"),
    City("Tehran", "Asia/Tehran"), City("Tokyo", "Asia/Tokyo"), City("Dubai", "Asia/Dubai"),
    City("Singapore", "Asia/Singapore"), City("Sydney", "Australia/Sydney"), City("Los Angeles", "America/Los_Angeles")
)

@Composable private fun HomeScreen(now:java.time.ZonedDateTime,jalali:JalaliDate,showJalali:Boolean,formatter:DateTimeFormatter,engine:ClockEngine,performance:Boolean,scale:Float,glow:Float,material:String,seconds:Boolean,nextAlarm:Pair<AlarmEntity,java.time.ZonedDateTime>?,cities:List<com.mesad.time.data.local.WorldCityEntity>,onOpen:(Screen)->Unit,onTools:()->Unit){
    val context=LocalContext.current;var view:MesadClockView? by remember{mutableStateOf(null)}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=androidx.compose.foundation.layout.PaddingValues(bottom=18.dp)){
        item { Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF101821)).padding(14.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${engine.name}  ·  LIVE",fontSize=9.sp,letterSpacing=1.4.sp,color=ElectricCyan);Text("LOCAL TIME",fontSize=9.sp,letterSpacing=1.sp,color=Titanium)}
            if(performance) MesadClock(engine,StudioPreset(engine=engine,scale=scale,glowIntensity=glow,materialFinish=runCatching{com.mesad.time.domain.model.MaterialFinish.valueOf(material)}.getOrDefault(com.mesad.time.domain.model.MaterialFinish.BRUSHED_TITANIUM)),Modifier.size(250.dp),showSeconds=seconds)
            else AndroidView(factory={MesadClockView(context).also{view=it;it.setEngine(engine.name);it.setScale(scale);it.setGlow(glow);it.setMaterial(material)}},modifier=Modifier.size(250.dp),update={it.setEngine(engine.name);it.setScale(scale);it.setGlow(glow);it.setMaterial(material)})
            Text(now.format(formatter),fontSize=38.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Light,letterSpacing=(-2).sp,color=Color(0xFFE8EDF5))
            Text(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),fontSize=12.sp,color=Titanium)
            if(showJalali) Text("${jalali.day} · ${JalaliDate.monthName(jalali.month)} ${jalali.year} SH",fontSize=10.sp,color=Champagne,modifier=Modifier.padding(top=4.dp))
        } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Graphite)){Column(Modifier.fillMaxWidth().clickable{onOpen(Screen.ALARM)}.padding(16.dp)){Text("NEXT ALARM",fontSize=9.sp,letterSpacing=1.4.sp,color=Champagne);Spacer(Modifier.height(6.dp));Text(nextAlarm?.first?.let{String.format("%02d:%02d  ·  %s",it.hour,it.minute,it.label.ifBlank{"Alarm"})}?:"No upcoming alarms",fontSize=20.sp,color=TitaniumHi);Text("Manage alarms  →",fontSize=10.sp,color=ElectricCyan)}} }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={onOpen(Screen.WORLD)},modifier=Modifier.weight(1f)){androidx.compose.material3.Icon(Icons.Outlined.Public,null);Text("World clock")};OutlinedButton(onClick=onTools,modifier=Modifier.weight(1f)){androidx.compose.material3.Icon(Icons.Outlined.CalendarMonth,null);Text("Temporal tools")}} }
        items(cities.take(3)){city->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Graphite).padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(city.name,color=Titanium);Text(Instant.now().atZone(ZoneId.of(city.zoneId)).format(DateTimeFormatter.ofPattern("HH:mm")),fontFamily=FontFamily.Monospace,color=TitaniumHi)}}
    }
}

@Composable private fun WorldClockScreen(cities:List<com.mesad.time.data.local.WorldCityEntity>,query:String,onQuery:(String)->Unit,onAdd:()->Unit,onCityAdd:(City)->Unit,onDelete:(Long)->Unit,now:java.time.ZonedDateTime){
    Column(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(query,onQuery,label={Text("Search cities")},modifier=Modifier.weight(1f));Button(onClick=onAdd){Text("Add")}}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=12.dp)){items(cities){c->val local=Instant.now().atZone(ZoneId.of(c.zoneId));Card(colors=CardDefaults.cardColors(containerColor=Graphite)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(c.name,color=TitaniumHi);Text(c.zoneId,fontSize=9.sp,color=Titanium);Text(local.format(DateTimeFormatter.ofPattern("EEEE, MMM d · z")),fontSize=10.sp,color=Champagne)};Text(local.format(DateTimeFormatter.ofPattern("HH:mm")),fontSize=22.sp,fontFamily=FontFamily.Monospace);TextButton(onClick={onDelete(c.id)}){androidx.compose.material3.Icon(Icons.Outlined.Delete,null)}}}}}
    }
}


@Composable private fun AlarmScreen(alarms:List<AlarmEntity>,onAdd:()->Unit,onToggle:(AlarmEntity,Boolean)->Unit,onDelete:(AlarmEntity)->Unit){Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Reliable, repeatable, yours.",fontSize=12.sp,color=Titanium);Button(onClick=onAdd){Text("+ Alarm")}};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=12.dp)){items(alarms){a->Card(colors=CardDefaults.cardColors(containerColor=Graphite)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(String.format("%02d:%02d",a.hour,a.minute),fontSize=28.sp,fontFamily=FontFamily.Monospace,color=TitaniumHi);Text(a.label.ifBlank{"Alarm"},fontSize=12.sp,color=Titanium);Text(if(a.repeatDays.isBlank())"One time" else a.repeatDays.replace(","," · "),fontSize=9.sp,color=Champagne)};Switch(checked=a.enabled,onCheckedChange={onToggle(a,it)});TextButton(onClick={onDelete(a)}){androidx.compose.material3.Icon(Icons.Outlined.Delete,null)}}}}}}

@Composable private fun StopwatchScreen(
    accumulated: Long,
    running: Boolean,
    startedAtElapsed: Long,
    onRunning: (Boolean) -> Unit,
    onLap: (Long) -> Unit,
    onReset: () -> Unit,
    laps: List<LapEntity>
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(running) { while (running) { now = SystemClock.elapsedRealtime(); delay(25) } }
    val elapsed = if (running) (now - startedAtElapsed).coerceAtLeast(0) else accumulated
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MONOTONIC CHRONOGRAPH", fontSize=9.sp, letterSpacing=1.5.sp, color=Champagne)
        Text(formatMs(elapsed), fontSize=44.sp, fontFamily=FontFamily.Monospace, color=TitaniumHi, modifier=Modifier.padding(top=28.dp))
        Canvas(Modifier.size(230.dp).padding(10.dp)) {
            drawCircle(Color(0xFF283341), style=Stroke(2.dp.toPx()))
            drawArc(ElectricCyan, startAngle=-90f, sweepAngle=(elapsed%60000)/60000f*360f, useCenter=false, topLeft=androidx.compose.ui.geometry.Offset.Zero, size=androidx.compose.ui.geometry.Size(size.width,size.height), style=Stroke(2.dp.toPx(), cap=StrokeCap.Round))
            drawCircle(Champagne, radius=4.dp.toPx())
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={ if(running) onLap(elapsed) else confirmReset=true }) { Text(if(running) "Lap" else "Reset") }
            Button(onClick={ onRunning(!running) }) {
                androidx.compose.material3.Icon(if(running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,null)
                Text(if(running) "Pause" else "Start")
            }
            OutlinedButton(onClick={confirmReset=true}) { Text("Reset") }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(top=14.dp)) {
            items(laps.reversed()) { lap ->
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Lap ${lap.lapNumber}${if(lap.isBest) " · BEST" else if(lap.isWorst) " · SLOW" else ""}", color=if(lap.isBest) Color(0xFF53DABC) else if(lap.isWorst) Champagne else Titanium)
                    Text(formatMs(lap.deltaMs), fontFamily=FontFamily.Monospace, color=if(lap.isBest) Color(0xFF53DABC) else if(lap.isWorst) Champagne else TitaniumHi)
                    Text(formatMs(lap.totalElapsedMs), fontFamily=FontFamily.Monospace, color=Champagne)
                }
            }
        }
    }
    if(confirmReset) AlertDialog(
        onDismissRequest={confirmReset=false},
        title={Text("Reset stopwatch?")},
        text={Text("This will clear the elapsed time and all recorded laps.")},
        confirmButton={TextButton(onClick={confirmReset=false;onReset()}){Text("Reset")}},
        dismissButton={TextButton(onClick={confirmReset=false}){Text("Cancel")}}
    )
}

@Composable private fun TimerScreen(timers:List<TimerEntity>,h:String,onH:(String)->Unit,m:String,onM:(String)->Unit,s:String,onS:(String)->Unit,label:String,onLabel:(String)->Unit,onCreate:()->Unit,onToggle:(TimerEntity)->Unit,onReset:(TimerEntity)->Unit,onDelete:(Long)->Unit){var tick by remember{mutableLongStateOf(SystemClock.elapsedRealtime())};LaunchedEffect(timers.any{it.state==TimerState.RUNNING.name}){while(timers.any{it.state==TimerState.RUNNING.name}){tick=SystemClock.elapsedRealtime();delay(100)}};Column(Modifier.fillMaxSize()){Card(colors=CardDefaults.cardColors(containerColor=Graphite)){Column(Modifier.padding(14.dp)){Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("H" to h to onH,"M" to m to onM,"S" to s to onS).forEach{ item->val unit=item.first.first;val value=item.first.second;val setter=item.second;OutlinedTextField(value,setter,label={Text(unit)},modifier=Modifier.weight(1f))}};OutlinedTextField(label,onLabel,label={Text("Timer label")},modifier=Modifier.fillMaxWidth());Button(onClick=onCreate,modifier=Modifier.fillMaxWidth()){Text("Create timer")}}};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=12.dp)){items(timers){timer->val remaining=if(timer.state==TimerState.RUNNING.name)(timer.deadlineElapsedRealtime?:tick)-tick else timer.remainingMs;Card(colors=CardDefaults.cardColors(containerColor=Graphite)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(timer.label.ifBlank{"Timer"},color=Titanium);Text(formatMs(remaining.coerceAtLeast(0)),fontSize=25.sp,fontFamily=FontFamily.Monospace,color=if(remaining<=0)Champagne else TitaniumHi);Text(timer.state,fontSize=9.sp,color=Champagne)};Button(onClick={onToggle(timer)}){Text(if(timer.state==TimerState.RUNNING.name)"Pause" else "Start")};TextButton(onClick={onReset(timer)}){Text("Reset")};TextButton(onClick={onDelete(timer.id)}){Text("×")}}}}}}
}

@Composable private fun StudioScreen(engine:ClockEngine,onEngine:(ClockEngine)->Unit,material:String,onMaterial:(String)->Unit,performance:Boolean,onPerformance:(Boolean)->Unit,scale:Float,onScale:(Float)->Unit,glow:Float,onGlow:(Float)->Unit,saved:Boolean,onSave:()->Unit){val context= LocalContext.current;Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){Text("CLOCK STUDIO",fontSize=9.sp,letterSpacing=1.5.sp,color=Champagne);if(performance)MesadClock(engine,StudioPreset(engine=engine,scale=scale,glowIntensity=glow,materialFinish=runCatching{com.mesad.time.domain.model.MaterialFinish.valueOf(material)}.getOrDefault(com.mesad.time.domain.model.MaterialFinish.BRUSHED_TITANIUM)),Modifier.size(250.dp)) else AndroidView(factory={MesadClockView(context).also{it.setEngine(engine.name);it.setMaterial(material)}},modifier=Modifier.size(250.dp),update={it.setEngine(engine.name);it.setScale(scale);it.setGlow(glow);it.setMaterial(material)});Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){ClockEngine.entries.forEach{e->FilterChip(selected=e==engine,onClick={onEngine(e)},label={Text(e.name)})}};Text("Clock scale · ${"%.2f".format(scale)}",modifier=Modifier.fillMaxWidth(),color=Titanium);Slider(scale,onScale,valueRange=.7f..1.3f);Text("Light / emission · ${"%.2f".format(glow)}",modifier=Modifier.fillMaxWidth(),color=Titanium);Slider(glow,onGlow);Text("Material finish",Modifier.fillMaxWidth(),fontSize=12.sp,color=Titanium);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){com.mesad.time.domain.model.MaterialFinish.entries.forEach{finish->FilterChip(selected=material==finish.name,onClick={onMaterial(finish.name)},label={Text(finish.name.replace("_"," "),fontSize=9.sp)})}};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Performance mode · 2D fallback",Modifier.weight(1f),fontSize=12.sp,color=Titanium);Switch(performance,onPerformance)};Button(onClick=onSave){Text(if(saved)"Preset saved" else "Save signature preset")}}
}

@Composable private fun ThemesScreen(dark:Boolean,onDark:(Boolean)->Unit){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("SURFACES SHAPED BY LIGHT",fontSize=9.sp,letterSpacing=1.3.sp,color=Champagne);listOf("Obsidian · Deep matte black","Midnight · Blue-hour precision","Champagne · Warm metal","Daylight · Soft titanium silver").forEach{label->Card(colors=CardDefaults.cardColors(containerColor=Graphite),modifier=Modifier.fillMaxWidth().clickable{onDark(label.startsWith("Obsidian")||label.startsWith("Midnight")||label.startsWith("Champagne"))}){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),color=TitaniumHi);if((dark&& !label.startsWith("Daylight"))||(!dark&&label.startsWith("Daylight")))androidx.compose.material3.Icon(Icons.Outlined.Check,null,tint=ElectricCyan)}}}}}

@Composable private fun SettingsScreen(use24:Boolean,on24:(Boolean)->Unit,seconds:Boolean,onSeconds:(Boolean)->Unit,jalali:Boolean,onJalali:(Boolean)->Unit,performance:Boolean,onPerformance:(Boolean)->Unit,dark:Boolean,onDark:(Boolean)->Unit,onTools:()->Unit,onExact:()->Unit,onNotifications:()->Unit){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("TIME & DISPLAY",fontSize=9.sp,letterSpacing=1.4.sp,color=Champagne);Setting("24-hour time","Use a 00–23 hour cycle.",use24,on24);Setting("Show seconds","Show seconds in the hero clock.",seconds,onSeconds);Setting("Jalali date","Display Persian calendar alongside Gregorian.",jalali,onJalali);Setting("Performance mode","Use efficient Compose Canvas clock.",performance,onPerformance);Setting("Dark finish","Prefer obsidian surfaces.",dark,onDark);Spacer(Modifier.height(8.dp));Button(onClick=onExact,modifier=Modifier.fillMaxWidth()){androidx.compose.material3.Icon(Icons.Outlined.Alarm,null);Text("Review exact alarm access")};Button(onClick=onNotifications,modifier=Modifier.fillMaxWidth()){Text("Allow alarm notifications")};Button(onClick=onTools,modifier=Modifier.fillMaxWidth()){androidx.compose.material3.Icon(Icons.Outlined.CalendarMonth,null);Text("Calendar · Converter · Reminders")};Text("Alarm schedules are stored locally in Room. Exact alarm access is checked when an alarm is enabled.",fontSize=10.sp,color=Titanium,modifier=Modifier.padding(top=12.dp))}}
@Composable private fun Setting(title:String,detail:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,color=TitaniumHi);Text(detail,fontSize=9.sp,color=Titanium)};Switch(value,onChange)}}

private fun formatMs(ms:Long):String{val s=(ms.coerceAtLeast(0)/1000);val h=s/3600;val m=s%3600/60;val sec=s%60;return if(h>0)String.format("%02d:%02d:%02d",h,m,sec) else String.format("%02d:%02d",m,sec)}
private fun syncTimerService(context: android.content.Context) { context.startForegroundService(android.content.Intent(context, com.mesad.time.platform.timer.TimerForegroundService::class.java).setAction(com.mesad.time.platform.timer.TimerForegroundService.ACTION_SYNC)) }
private fun AlarmEntity.toDomainAlarm() = com.mesad.time.domain.model.Alarm(id=id,hour=hour,minute=minute,label=label,repeatDays=repeatDays.split(",").filter{it.isNotBlank()}.mapNotNull{runCatching{DayOfWeek.valueOf(it)}.getOrNull()}.toSet(),ringtoneUri=ringtoneUri,vibrate=vibrate,enabled=enabled,snoozeMinutes=snoozeMinutes)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MesadTheme(dynamicColor = false) { MesadApplicationUi((application as MesadApplication).database) } }
    }
}

@Composable
private fun MesadApplicationUi(db: MesadDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("mesad_preferences", 0) }
    val settingsStore = remember(context) { com.mesad.time.data.local.MesadSettingsStore(context) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var engine by remember { mutableStateOf(ClockEngine.valueOf(preferences.getString("engine", "ORBIT")!!)) }
    var performance by remember { mutableStateOf(preferences.getBoolean("performance", false)) }
    var use24h by remember { mutableStateOf(preferences.getBoolean("use24h", false)) }
    var showSeconds by remember { mutableStateOf(preferences.getBoolean("seconds", true)) }
    var showJalali by remember { mutableStateOf(preferences.getBoolean("jalali", true)) }
    var dark by remember { mutableStateOf(preferences.getBoolean("dark", true)) }
    var scale by remember { mutableStateOf(preferences.getFloat("scale", 1f)) }
    var glow by remember { mutableStateOf(preferences.getFloat("glow", .55f)) }
    var material by remember { mutableStateOf(preferences.getString("material", "BRUSHED_TITANIUM")!!) }
    var currentTime by remember { mutableStateOf<java.time.ZonedDateTime>(java.time.ZonedDateTime.now(ZoneId.systemDefault())) }
    var tick by remember { mutableLongStateOf(0L) }
    val alarms by db.alarmDao().observeAll().collectAsState(initial = emptyList())
    val cities by db.worldCityDao().observeAll().collectAsState(initial = emptyList())
    val timers by db.timerDao().observeAll().collectAsState(initial = emptyList())
    val laps by db.lapDao().observeByStopwatch(1).collectAsState(initial = emptyList())
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var cityQuery by remember { mutableStateOf("") }
    var alarmHour by remember { mutableStateOf("07") }
    var alarmMinute by remember { mutableStateOf("00") }
    var alarmLabel by remember { mutableStateOf("") }
    var alarmDays by remember { mutableStateOf(setOf<Int>()) }
    var timerH by remember { mutableStateOf("00") }
    var timerM by remember { mutableStateOf("05") }
    var timerS by remember { mutableStateOf("00") }
    var timerLabel by remember { mutableStateOf("") }
    val bootCount = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)
    val savedStopwatchElapsed = preferences.getLong("sw_elapsed", 0L)
    val savedStopwatchStart = preferences.getLong("sw_started", 0L)
    val restoreStopwatch = preferences.getBoolean("sw_running", false) && preferences.getInt("sw_boot", bootCount) == bootCount && savedStopwatchStart in 1..SystemClock.elapsedRealtime()
    var stopwatchAccumulated by remember { mutableLongStateOf(savedStopwatchElapsed) }
    var stopwatchStart by remember { mutableLongStateOf(if (restoreStopwatch) savedStopwatchStart else SystemClock.elapsedRealtime() - savedStopwatchElapsed) }
    var stopwatchRunning by remember { mutableStateOf(restoreStopwatch) }
    var converterZone by remember { mutableStateOf("Asia/Tehran") }
    var converterMenu by remember { mutableStateOf(false) }
    var converterTime by remember { mutableStateOf("09:00") }
    var reminderText by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf(preferences.getStringSet("reminders", emptySet())!!.toList()) }
    var calendarMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var selectedCalendarDate by remember { mutableStateOf(LocalDate.now()) }
    var toolsDialog by remember { mutableStateOf(false) }
    var studioSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) { currentTime = java.time.ZonedDateTime.now(ZoneId.systemDefault()); tick++; delay(250) }
    }
    val jalali = remember(currentTime.toLocalDate()) { JalaliDate.fromGregorian(currentTime.toLocalDate()) }
    val timeFormatter = remember(use24h, showSeconds) { DateTimeFormatter.ofPattern(if (use24h) if (showSeconds) "HH:mm:ss" else "HH:mm" else if (showSeconds) "h:mm:ss a" else "h:mm a", Locale.getDefault()) }
    val nextAlarm = remember(alarms, currentTime) {
        alarms.filter { it.enabled }.mapNotNull { entity ->
            val alarm = entity.hour to entity.minute
            val candidate = currentTime.withHour(alarm.first).withMinute(alarm.second).withSecond(0).withNano(0)
            val whenNext = if (candidate.isAfter(currentTime)) candidate else candidate.plusDays(1)
            entity to whenNext
        }.minByOrNull { it.second }
    }
    fun savePref(key: String, value: Any) { preferences.edit().apply { when (value) { is Boolean -> putBoolean(key,value); is Float -> putFloat(key,value); is String -> putString(key,value) } }.apply(); scope.launch { settingsStore.put(key,value) } }

    MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme(primary = ElectricCyan, secondary = Champagne, background = Obsidian, surface = Graphite, onBackground = Titanium, onSurface = Titanium) else androidx.compose.material3.lightColorScheme(primary = Color(0xFF007887), secondary = Color(0xFF8A6D3B))) {
        Scaffold(
            containerColor = if (dark) Obsidian else Color(0xFFF3F5F8),
            topBar = { Row(Modifier.fillMaxWidth().background(if (dark) Obsidian else Color(0xFFF3F5F8)).padding(horizontal=20.dp, vertical=15.dp), verticalAlignment=Alignment.CenterVertically) {
                Text("MESAD", fontWeight=FontWeight.Bold, letterSpacing=3.sp, color=if(dark) Titanium else Obsidian)
                Spacer(Modifier.width(12.dp)); Text("·  THE ART OF TIME", fontSize=9.sp, letterSpacing=1.5.sp, color=Champagne)
                Spacer(Modifier.weight(1f)); Text(screen.title.uppercase(), fontSize=9.sp, letterSpacing=1.sp, color=Titanium)
            } },
            bottomBar = {
                NavigationBar(containerColor = if (dark) Graphite else Color.White) {
                    val primary = listOf(Screen.HOME, Screen.WORLD, Screen.ALARM, Screen.STOPWATCH, Screen.TIMER)
                    primary.forEach { item ->
                        NavigationBarItem(selected = screen == item, onClick = { screen = item }, icon = { androidx.compose.material3.Icon(when(item){Screen.HOME->Icons.Outlined.Home;Screen.WORLD->Icons.Outlined.Public;Screen.ALARM->Icons.Outlined.Alarm;Screen.STOPWATCH->Icons.Outlined.WatchLater;Screen.TIMER->Icons.Outlined.HourglassBottom;else->Icons.Outlined.Tune}, null) }, label = { Text(item.title, fontSize=9.sp) })
                    }
                    NavigationBarItem(selected = screen in listOf(Screen.STUDIO,Screen.THEMES,Screen.SETTINGS), onClick = { showMore = true }, icon = { androidx.compose.material3.Icon(Icons.Outlined.Tune, null) }, label = { Text("More", fontSize=9.sp) })
                    androidx.compose.material3.DropdownMenu(expanded=showMore,onDismissRequest={showMore=false}) {
                        listOf(Screen.STUDIO,Screen.THEMES,Screen.SETTINGS).forEach { item -> androidx.compose.material3.DropdownMenuItem(text={Text(item.title)},onClick={screen=item;showMore=false}) }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=18.dp).padding(top=8.dp)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(currentTime, jalali, showJalali, timeFormatter, engine, performance, scale, glow, material, showSeconds, nextAlarm, cities, onOpen={screen=it}, onTools={toolsDialog=true})
                    Screen.WORLD -> WorldClockScreen(cities, cityQuery, {cityQuery=it}, {showCityDialog=true}, {city -> scope.launch { db.worldCityDao().insert(com.mesad.time.data.local.WorldCityEntity(name=city.name,zoneId=city.zone)) }}, {id->scope.launch { db.worldCityDao().deleteById(id) }}, currentTime)
                    Screen.ALARM -> AlarmScreen(alarms, onAdd={alarmHour="07";alarmMinute="00";alarmLabel="";alarmDays=emptySet();showAlarmDialog=true}, onToggle={a,enabled->scope.launch { db.alarmDao().setEnabled(a.id,enabled); val d=a.copy(enabled=enabled);val scheduler=AlarmManagerScheduler(context);if(enabled){if(!scheduler.canScheduleExactAlarms())scheduler.requestExactAlarmPermission();scheduler.schedule(d.toDomainAlarm())}else scheduler.cancel(a.id) }}, onDelete={a->scope.launch {AlarmManagerScheduler(context).cancel(a.id);db.alarmDao().deleteById(a.id)}})
                    Screen.STOPWATCH -> StopwatchScreen(stopwatchAccumulated,stopwatchRunning,stopwatchStart,{ value -> if(value){stopwatchStart=SystemClock.elapsedRealtime()-stopwatchAccumulated;stopwatchRunning=true;preferences.edit().putLong("sw_started",stopwatchStart).putInt("sw_boot",bootCount).putBoolean("sw_running",true).apply()}else{stopwatchAccumulated=SystemClock.elapsedRealtime()-stopwatchStart;stopwatchRunning=false;preferences.edit().putLong("sw_elapsed",stopwatchAccumulated).putBoolean("sw_running",false).apply()} },{total->scope.launch {val dao=db.lapDao();val existing=dao.getByStopwatch(1);dao.insert(LapEntity(lapNumber=(existing.maxOfOrNull{it.lapNumber}?:0)+1,totalElapsedMs=total,deltaMs=total-(existing.lastOrNull()?.totalElapsedMs?:0)));val all=dao.getByStopwatch(1);val best=all.minOfOrNull{it.deltaMs};val worst=all.maxOfOrNull{it.deltaMs};all.forEach{lap->dao.update(lap.copy(isBest=all.size>1&&lap.deltaMs==best,isWorst=all.size>1&&lap.deltaMs==worst))}}},{scope.launch {stopwatchAccumulated=0;stopwatchRunning=false;stopwatchStart=SystemClock.elapsedRealtime();preferences.edit().putLong("sw_elapsed",0).putBoolean("sw_running",false).apply();db.lapDao().deleteByStopwatch(1)}},laps)
                    Screen.TIMER -> TimerScreen(timers,timerH,{timerH=it},timerM,{timerM=it},timerS,{timerS=it},timerLabel,{timerLabel=it},{scope.launch { val duration=(timerH.toLongOrNull()?:0)*3600000+(timerM.toLongOrNull()?:0)*60000+(timerS.toLongOrNull()?:0)*1000;if(duration in 1..86_400_000L){db.timerDao().insert(TimerEntity(label=timerLabel.ifBlank{"MESAD Timer"},durationMs=duration,remainingMs=duration));timerLabel=""} }},{t->scope.launch { val now=SystemClock.elapsedRealtime(); val remaining=when(t.state){TimerState.RUNNING.name->(t.deadlineElapsedRealtime?:now)-now;TimerState.FINISHED.name->t.durationMs;else->t.remainingMs}; val running=t.state!=TimerState.RUNNING.name;db.timerDao().update(t.copy(state=if(running)TimerState.RUNNING.name else TimerState.PAUSED.name,remainingMs=remaining.coerceAtLeast(0),deadlineElapsedRealtime=if(running)now+remaining else null,deadlineWallClock=if(running)System.currentTimeMillis()+remaining else null));syncTimerService(context) }},{t->scope.launch {db.timerDao().update(t.copy(state=TimerState.IDLE.name,remainingMs=t.durationMs,deadlineElapsedRealtime=null,deadlineWallClock=null));syncTimerService(context)}},{id->scope.launch {db.timerDao().deleteById(id);syncTimerService(context)}})
                    Screen.STUDIO -> StudioScreen(engine,{engine=it;savePref("engine",it.name)},material,{material=it;savePref("material",it)},performance,{performance=it;savePref("performance",it)},scale,{scale=it;savePref("scale",it)},glow,{glow=it;savePref("glow",it)},studioSaved,{studioSaved=true;savePref("studioSaved",true);scope.launch{settingsStore.putStudioPreset(engine.name,material,scale,glow)}})
                    Screen.THEMES -> ThemesScreen(dark,{dark=it;savePref("dark",it)})
                    Screen.SETTINGS -> SettingsScreen(use24h,{use24h=it;savePref("use24h",it)},showSeconds,{showSeconds=it;savePref("seconds",it)},showJalali,{showJalali=it;savePref("jalali",it)},performance,{performance=it;savePref("performance",it)},dark,{dark=it;savePref("dark",it)},onTools={toolsDialog=true},onExact={AlarmManagerScheduler(context).requestExactAlarmPermission()},onNotifications={if(android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)(context as? android.app.Activity)?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),1002)})
                }
            }
        }
    }

    if (showAlarmDialog) AlertDialog(onDismissRequest={showAlarmDialog=false},title={Text("New alarm")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(alarmHour,{alarmHour=it.takeLast(2)},label={Text("Hour")},modifier=Modifier.weight(1f));OutlinedTextField(alarmMinute,{alarmMinute=it.takeLast(2)},label={Text("Minute")},modifier=Modifier.weight(1f))}
        OutlinedTextField(alarmLabel,{alarmLabel=it},label={Text("Label")},modifier=Modifier.fillMaxWidth())
        Text("Repeat days",fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){listOf("S","M","T","W","T","F","S").forEachIndexed{index,label->FilterChip(selected=index in alarmDays,onClick={alarmDays=if(index in alarmDays)alarmDays-index else alarmDays+index},label={Text(label)},modifier=Modifier.weight(1f))}}
    }},confirmButton={TextButton(onClick={scope.launch { val h=(alarmHour.toIntOrNull()?:7).coerceIn(0,23);val m=(alarmMinute.toIntOrNull()?:0).coerceIn(0,59);val days=alarmDays.map{if(it==0)DayOfWeek.SUNDAY.value else it}.joinToString(","){DayOfWeek.of(it).name};val id=db.alarmDao().insert(AlarmEntity(hour=h,minute=m,label=alarmLabel,repeatDays=days));val scheduler=AlarmManagerScheduler(context);if(!scheduler.canScheduleExactAlarms())scheduler.requestExactAlarmPermission();scheduler.schedule(com.mesad.time.domain.model.Alarm(id=id,hour=h,minute=m,label=alarmLabel,repeatDays=alarmDays.map{if(it==0)DayOfWeek.SUNDAY else DayOfWeek.of(it)}.toSet()));showAlarmDialog=false}){Text("Save alarm")}},dismissButton={TextButton(onClick={showAlarmDialog=false}){Text("Cancel")}})

    if(showCityDialog) AlertDialog(onDismissRequest={showCityDialog=false},title={Text("Add a city")},text={Column {
        OutlinedTextField(cityQuery,{cityQuery=it},label={Text("Search cities or IANA zone")})
        LazyColumn(Modifier.height(300.dp)){items(popularCities.filter{it.name.contains(cityQuery,true)||it.zone.contains(cityQuery,true)}){city->Text("${city.name}  ·  ${city.zone}",Modifier.fillMaxWidth().clickable{onCityAdd(city);showCityDialog=false}.padding(12.dp),fontSize=12.sp)}}
    }},confirmButton={TextButton(onClick={showCityDialog=false}){Text("Done")}})

    if(toolsDialog) AlertDialog(onDismissRequest={toolsDialog=false},title={Text("Temporal tools")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Timezone converter",fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(converterTime,{converterTime=it},label={Text("Local time")},modifier=Modifier.weight(1f));Box{TextButton(onClick={converterMenu=true}){Text(converterZone.substringAfter("/").replace("_"," "))};androidx.compose.material3.DropdownMenu(expanded=converterMenu,onDismissRequest={converterMenu=false}){popularCities.forEach{city->androidx.compose.material3.DropdownMenuItem(text={Text(city.name)},onClick={converterZone=city.zone;converterMenu=false})}}}};val parts=converterTime.split(":");val localMoment=runCatching{LocalDate.now().atTime(parts[0].toInt(),parts[1].toInt()).atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of(converterZone))}.getOrNull();Text(localMoment?.format(DateTimeFormatter.ofPattern("HH:mm · EEE, MMM d · z"))?:"Enter a local time",color=ElectricCyan,fontFamily=FontFamily.Monospace)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(calendarMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),fontWeight=FontWeight.SemiBold);Row{TextButton(onClick={calendarMonth=calendarMonth.minusMonths(1)}){Text("‹")};TextButton(onClick={calendarMonth=calendarMonth.plusMonths(1)}){Text("›")}}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){listOf("S","M","T","W","T","F","S").forEach{Text(it,fontSize=9.sp,color=Titanium)}}
        val firstOffset=calendarMonth.dayOfWeek.value%7;val daysInMonth=calendarMonth.lengthOfMonth()
        (0..5).forEach{week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){(0..6).forEach{weekday->val day=week*7+weekday-firstOffset+1;if(day in 1..daysInMonth)TextButton(onClick={selectedCalendarDate=calendarMonth.withDayOfMonth(day)}){Text(day.toString(),color=if(selectedCalendarDate==calendarMonth.withDayOfMonth(day))ElectricCyan else Titanium,fontSize=11.sp)}else Spacer(Modifier.width(42.dp).height(38.dp))}}}
        Text("Selected: ${selectedCalendarDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))} · Today Jalali ${jalali.day}/${jalali.month}/${jalali.year}",fontSize=10.sp,color=Champagne)
        OutlinedTextField(reminderText,{reminderText=it},label={Text("New reminder")},modifier=Modifier.fillMaxWidth());Button(onClick={if(reminderText.isNotBlank()){reminders=listOf(reminderText)+reminders;preferences.edit().putStringSet("reminders",reminders.toSet()).apply();reminderText=""}}){Text("Add reminder")}
        reminders.forEach{rem->Text("• $rem",Modifier.fillMaxWidth().clickable{reminders=reminders-rem;preferences.edit().putStringSet("reminders",reminders.toSet()).apply()}.padding(4.dp),fontSize=11.sp)}
    }},confirmButton={TextButton(onClick={toolsDialog=false}){Text("Done")}})
}

