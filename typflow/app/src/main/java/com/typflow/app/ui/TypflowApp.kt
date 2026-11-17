package com.typflow.app.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import com.typflow.app.theme.themeById

enum class Tab { HOME, TYPING, READING, ANALYTICS, PROFILE }

private data class TabSpec(val tab: Tab, val index: String, val fa: String, val en: String)

private val TABS = listOf(
    TabSpec(Tab.HOME, "01", "خانه", "HOME"),
    TabSpec(Tab.TYPING, "02", "تایپ", "TYPE"),
    TabSpec(Tab.READING, "03", "خواندن", "READ"),
    TabSpec(Tab.ANALYTICS, "04", "آمار", "STATS"),
    TabSpec(Tab.PROFILE, "05", "پروفایل", "YOU")
)

private fun tabFromName(name: String?): Tab = when (name) {
    "typing" -> Tab.TYPING
    "reading" -> Tab.READING
    "analytics" -> Tab.ANALYTICS
    "profile" -> Tab.PROFILE
    else -> Tab.HOME
}

@Composable
fun TypflowApp(initialScreen: String? = null) {
    val context = LocalContext.current
    val store = remember { TypflowStore(context) }
    val theme = themeById(store.themeId)

    var tab by remember { mutableStateOf(tabFromName(initialScreen)) }
    var showSettings by remember { mutableStateOf(initialScreen == "settings") }
    var showSplash by remember { mutableStateOf(initialScreen == null) }

    CompositionLocalProvider(
        LocalLayoutDirection provides
            if (store.lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Box(modifier = Modifier.fillMaxSize().background(theme.bg)) {
            RawBackdrop(theme)

            if (showSplash) {
                SplashScreen(theme) { showSplash = false }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (showSettings) {
                            SettingsScreen(theme, store) { showSettings = false }
                        } else {
                            when (tab) {
                                Tab.HOME -> HomeScreen(
                                    theme = theme,
                                    store = store,
                                    onNavigate = { tab = it },
                                    onOpenSettings = { showSettings = true }
                                )
                                Tab.TYPING -> TypingScreen(theme, store)
                                Tab.READING -> ReadingScreen(theme, store)
                                Tab.ANALYTICS -> AnalyticsScreen(theme, store)
                                Tab.PROFILE -> ProfileScreen(
                                    theme = theme,
                                    store = store,
                                    onOpenSettings = { showSettings = true }
                                )
                            }
                        }
                    }
                    if (!showSettings) {
                        TabBar(theme, store.lang, tab) { tab = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabBar(
    theme: TypflowTheme,
    lang: String,
    current: Tab,
    onSelect: (Tab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(theme.bg)) {
        Hairline(theme)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TABS.forEach { spec ->
                val active = spec.tab == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (active) theme.ink else theme.bg)
                        .clickable { onSelect(spec.tab) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = spec.index,
                        color = if (active) theme.bg else theme.dim,
                        fontFamily = uiFont(theme),
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (lang == "fa") spec.fa else spec.en,
                        color = if (active) theme.bg else theme.ink,
                        fontFamily = uiFont(theme),
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Black else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
