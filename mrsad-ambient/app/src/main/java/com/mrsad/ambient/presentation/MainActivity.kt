package com.mrsad.ambient.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.data.local.AmbientDatabase
import com.mrsad.ambient.data.local.PreferencesDataStore
import com.mrsad.ambient.data.local.UserPreferences
import com.mrsad.ambient.designsystem.MrsadAmbientTheme
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.designsystem.MrsadIcon
import com.mrsad.ambient.designsystem.MrsadIconType
import com.mrsad.ambient.presentation.dashboard.DashboardScreen
import com.mrsad.ambient.presentation.diagnostics.DiagnosticsScreen
import com.mrsad.ambient.presentation.editor.CustomizationEditorScreen
import com.mrsad.ambient.presentation.settings.SettingsScreen
import com.mrsad.ambient.presentation.splash.SplashScreen
import com.mrsad.ambient.presentation.themes.ThemeLibraryScreen
import com.mrsad.ambient.system.BatteryMonitor
import com.mrsad.ambient.system.SensorManagerHelper
import kotlinx.coroutines.launch

enum class MainTab(val icon: MrsadIconType, val labelFa: String, val labelEn: String) {
    DASHBOARD(MrsadIconType.CLOCK, "داشبورد", "Home"),
    EDITOR(MrsadIconType.ACTIVITY, "ویرایشگر", "Editor"),
    THEMES(MrsadIconType.CALENDAR, "پوسته‌ها", "Themes"),
    DIAGNOSTICS(MrsadIconType.SENSORS, "گزارش", "Reports"),
    SETTINGS(MrsadIconType.SETTINGS, "تنظیمات", "Settings")
}

class MainActivity : ComponentActivity() {

    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var sensorHelper: SensorManagerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        batteryMonitor = BatteryMonitor(this)
        sensorHelper = SensorManagerHelper(this)

        val prefsStore = PreferencesDataStore(this)
        val db = AmbientDatabase.getInstance(this)

        setContent {
            val prefs by prefsStore.preferencesFlow.collectAsState(initial = UserPreferences())
            val battery by batteryMonitor.state.collectAsState()
            val sensors by sensorHelper.status.collectAsState()
            val sessions by db.ambientDao().observeSessions().collectAsState(initial = emptyList())
            val scope = rememberCoroutineScope()

            var showSplash by remember { mutableStateOf(true) }
            var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
            val usePersian = prefs.language == "fa"
            val layoutDir = if (usePersian) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                MrsadAmbientTheme {
                    if (showSplash) {
                        SplashScreen(onFinished = { showSplash = false })
                    } else {
                        Scaffold(
                            containerColor = MrsadColors.PureBlack,
                            bottomBar = {
                                NavigationBar(containerColor = MrsadColors.GraphiteDark) {
                                    MainTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = currentTab == tab,
                                            onClick = { currentTab = tab },
                                            icon = { MrsadIcon(type = tab.icon, size = 18.dp) },
                                            label = {
                                                Text(
                                                    text = if (usePersian) tab.labelFa else tab.labelEn,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (currentTab) {
                                    MainTab.DASHBOARD -> DashboardScreen(
                                        prefs = prefs,
                                        battery = battery,
                                        sensors = sensors,
                                        onToggleAmbient = { scope.launch { prefsStore.setAmbientEnabled(it) } },
                                        onSelectProfile = { scope.launch { prefsStore.setActiveProfile(it) } },
                                        onOpenEditor = { currentTab = MainTab.EDITOR },
                                        onOpenSettings = { currentTab = MainTab.SETTINGS }
                                    )
                                    MainTab.EDITOR -> CustomizationEditorScreen(
                                        prefs = prefs,
                                        battery = battery,
                                        onUpdateClock = { scope.launch { prefsStore.setClockConfig(it) } },
                                        onSelectProfile = { scope.launch { prefsStore.setActiveProfile(it) } }
                                    )
                                    MainTab.THEMES -> ThemeLibraryScreen(
                                        usePersian = usePersian,
                                        onApplyPreset = { scope.launch { prefsStore.setClockConfig(it) } }
                                    )
                                    MainTab.DIAGNOSTICS -> DiagnosticsScreen(
                                        sessions = sessions,
                                        usePersian = usePersian,
                                        onClearReports = { scope.launch { db.ambientDao().clearSessions() } }
                                    )
                                    MainTab.SETTINGS -> SettingsScreen(
                                        prefs = prefs,
                                        onToggleLanguage = {
                                            scope.launch {
                                                prefsStore.setLanguage(if (usePersian) "en" else "fa")
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        batteryMonitor.start()
    }

    override fun onStop() {
        super.onStop()
        batteryMonitor.stop()
    }
}
