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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.typflow.app.data.TypflowStore
import com.typflow.app.theme.TypflowTheme
import com.typflow.app.theme.themeById

private enum class Screen { HOME, TYPING, READING, ANALYTICS, PROFILE, SETTINGS }

private data class NavItem(val screen: Screen, val icon: String, val fa: String, val en: String)

private val NAV_ITEMS = listOf(
    NavItem(Screen.HOME, "🏠", "خانه", "Home"),
    NavItem(Screen.TYPING, "⌨️", "تایپ", "Typing"),
    NavItem(Screen.READING, "📖", "خواندن", "Reading"),
    NavItem(Screen.ANALYTICS, "📊", "آمار", "Analytics"),
    NavItem(Screen.PROFILE, "👤", "پروفایل", "Profile")
)

@Composable
fun TypflowApp() {
    val context = LocalContext.current
    val store = remember { TypflowStore(context) }
    val theme = themeById(store.themeId)
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showSplash by remember { mutableStateOf(true) }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (store.lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        if (showSplash) {
            SplashScreen(theme) { showSplash = false }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.background)
            ) {
                CinematicBackdrop(theme)

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(theme, store) { target ->
                                screen = when (target) {
                                    "typing" -> Screen.TYPING
                                    "reading" -> Screen.READING
                                    "settings" -> Screen.SETTINGS
                                    else -> Screen.HOME
                                }
                            }
                            Screen.TYPING -> TypingScreen(theme, store)
                            Screen.READING -> ReadingScreen(theme, store)
                            Screen.ANALYTICS -> AnalyticsScreen(theme, store)
                            Screen.PROFILE -> ProfileScreen(theme, store) { target ->
                                if (target == "settings") screen = Screen.SETTINGS
                            }
                            Screen.SETTINGS -> SettingsScreen(theme, store)
                        }
                    }
                    BottomBar(theme, store.lang, screen) { screen = it }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    theme: TypflowTheme,
    lang: String,
    current: Screen,
    onSelect: (Screen) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(theme.surface.copy(alpha = 0.75f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NAV_ITEMS.forEach { item ->
            val selected = current == item.screen
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) Brush.horizontalGradient(theme.gradient)
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(item.screen) }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    text = item.icon,
                    fontSize = 19.sp,
                    color = if (selected) Color.Black else theme.onSurface
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = if (lang == "fa") item.fa else item.en,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                    color = if (selected) Color.Black else theme.onSurface.copy(alpha = 0.75f)
                )
            }
        }
    }
}
