package ir.aegis.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import ir.aegis.core.model.*
import ir.aegis.core.ui.AegisModuleIcon
import ir.aegis.core.ui.AegisTheme
import ir.aegis.core.ui.LocalObsidianColors
import ir.aegis.core.ui.ObsidianBackground
import ir.aegis.core.ui.tr
import ir.aegis.feature.academy.PreparednessAcademyScreen
import ir.aegis.feature.ai.LocalAiScreen
import ir.aegis.feature.analytics.AdvancedAnalyticsScreen
import ir.aegis.feature.crisis.CrisisManagementScreen
import ir.aegis.feature.emergency.EmergencyResponseScreen
import ir.aegis.feature.family.FamilySafetyScreen
import ir.aegis.feature.maps.OfflineMapScreen
import ir.aegis.feature.personal.PersonalSecurityScreen
import ir.aegis.feature.privacy.PrivacySecurityScreen
import ir.aegis.feature.utilities.AdvancedUtilitiesScreen
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AegisApplication).container

        setContent {
            val prefs by container.preferencesRepository.preferences.collectAsState(initial = AppPreferences())
            val scope = rememberCoroutineScope()

            LaunchedEffect(prefs.flagSecureEnabled) {
                if (prefs.flagSecureEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            var currentModule by remember { mutableStateOf(AegisModule.HOME) }
            var isLocked by remember { mutableStateOf(false) }

            var initialCheckDone by remember { mutableStateOf(false) }
            LaunchedEffect(prefs.pinEnabled) {
                if (!initialCheckDone && prefs.pinEnabled) {
                    isLocked = true
                }
                initialCheckDone = true
            }

            BackHandler(enabled = currentModule != AegisModule.HOME && !isLocked) {
                currentModule = AegisModule.HOME
            }

            AegisTheme(themeMode = prefs.themeMode, language = prefs.language) {
                if (isLocked && prefs.pinEnabled) {
                    LockGateOverlay(
                        preferences = prefs,
                        onUnlocked = { isLocked = false },
                    )
                } else {
                    ObsidianBackground {
                        Scaffold(
                            containerColor = Color.Transparent,
                            bottomBar = {
                                AegisBottomNavBar(
                                    currentModule = currentModule,
                                    onSelect = { currentModule = it },
                                )
                            },
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(bottom = innerPadding.calculateBottomPadding()),
                            ) {
                                val isFav = currentModule in prefs.favoriteModules
                                val toggleFav: () -> Unit = {
                                    scope.launch { container.preferencesRepository.toggleFavoriteModule(currentModule) }
                                }
                                val goHome: () -> Unit = { currentModule = AegisModule.HOME }

                                when (currentModule) {
                                    AegisModule.HOME -> CommandHomeScreen(
                                        repository = container.repository,
                                        preferences = prefs,
                                        onOpenModule = { currentModule = it },
                                        onToggleTheme = {
                                            val next = when (prefs.themeMode) {
                                                ThemeMode.SYSTEM -> ThemeMode.DARK
                                                ThemeMode.DARK -> ThemeMode.LIGHT
                                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                            }
                                            scope.launch { container.preferencesRepository.updateThemeMode(next) }
                                        },
                                        onToggleLanguage = {
                                            val next = if (prefs.language == AppLanguage.FA) AppLanguage.EN else AppLanguage.FA
                                            scope.launch { container.preferencesRepository.updateLanguage(next) }
                                        },
                                        onToggleWidget = { w ->
                                            val nextDisabled = if (w in prefs.disabledWidgets) prefs.disabledWidgets - w else prefs.disabledWidgets + w
                                            scope.launch { container.preferencesRepository.updateDashboardWidgets(prefs.widgetOrder, nextDisabled) }
                                        },
                                    )
                                    AegisModule.PERSONAL -> PersonalSecurityScreen(
                                        repository = container.repository,
                                        reminderScheduler = container.reminderScheduler,
                                        preferences = prefs,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.CRISIS -> CrisisManagementScreen(
                                        repository = container.repository,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.MAPS -> OfflineMapScreen(
                                        repository = container.repository,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.EMERGENCY -> EmergencyResponseScreen(
                                        repository = container.repository,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.AI -> LocalAiScreen(
                                        repository = container.repository,
                                        preferences = prefs,
                                        onModelImported = { path, name, sha ->
                                            scope.launch { container.preferencesRepository.setLocalModelInfo(path, name, sha) }
                                        },
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.FAMILY -> FamilySafetyScreen(
                                        repository = container.repository,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.ACADEMY -> PreparednessAcademyScreen(
                                        repository = container.repository,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.ANALYTICS -> AdvancedAnalyticsScreen(
                                        repository = container.repository,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.PRIVACY -> PrivacySecurityScreen(
                                        repository = container.repository,
                                        preferencesRepository = container.preferencesRepository,
                                        preferences = prefs,
                                        onLockAppNow = { isLocked = true },
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
                                    AegisModule.UTILITIES -> AdvancedUtilitiesScreen(
                                        repository = container.repository,
                                        reminderScheduler = container.reminderScheduler,
                                        preferences = prefs,
                                        isFavorite = isFav,
                                        onToggleFavorite = toggleFav,
                                        onBack = goHome,
                                    )
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
private fun AegisBottomNavBar(
    currentModule: AegisModule,
    onSelect: (AegisModule) -> Unit,
) {
    val colors = LocalObsidianColors.current
    val items = listOf(
        AegisModule.HOME to tr("Home", "خانه"),
        AegisModule.EMERGENCY to tr("Anti-Hack", "ضد هک"),
        AegisModule.UTILITIES to tr("Scanner", "آنتی‌ویروس"),
        AegisModule.AI to tr("Phishing", "فیشینگ"),
        AegisModule.MAPS to tr("Map", "نقشه"),
    )

    val selectedTab = when (currentModule) {
        AegisModule.HOME -> AegisModule.HOME
        AegisModule.EMERGENCY -> AegisModule.EMERGENCY
        AegisModule.UTILITIES -> AegisModule.UTILITIES
        AegisModule.AI -> AegisModule.AI
        AegisModule.MAPS -> AegisModule.MAPS
        else -> AegisModule.HOME
    }

    NavigationBar(
        containerColor = if (colors.isDark) Color(0xDC0D141C) else Color(0xECFFFFFF),
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(
                color = colors.glassTopSheen,
                start = Offset(0f, 1f),
                end = Offset(size.width, 1f),
                strokeWidth = 1.5f,
            )
        },
    ) {
        items.forEach { (mod, label) ->
            val selected = selectedTab == mod
            val activeAccent = if (mod == AegisModule.EMERGENCY) colors.accentCrimson else colors.accentCyan
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(mod) },
                icon = {
                    AegisModuleIcon(
                        module = mod,
                        tint = if (selected) activeAccent else colors.textSecondary,
                        size = 21.dp,
                    )
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = activeAccent,
                    selectedTextColor = activeAccent,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                    indicatorColor = activeAccent.copy(alpha = 0.16f),
                ),
            )
        }
    }
}
