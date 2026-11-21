package dev.mrsad.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import dev.mrsad.app.ui.components.IconFacet
import dev.mrsad.app.ui.components.MrsadIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.feature.analytics.AnalyticsScreen
import dev.mrsad.app.feature.assistant.AssistantScreen
import dev.mrsad.app.feature.focus.FocusScreen
import dev.mrsad.app.feature.home.HomeScreen
import dev.mrsad.app.feature.mind.MindScreen
import dev.mrsad.app.feature.placeholder.PlaceholderScreen
import dev.mrsad.app.feature.read.ReadScreen
import dev.mrsad.app.feature.settings.SettingsScreen
import dev.mrsad.app.feature.studio.StudioScreen
import dev.mrsad.app.feature.type.TypeScreen
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MrsadBottomBar
import dev.mrsad.app.ui.components.MrsadBottomBarItem
import dev.mrsad.app.ui.theme.MrsadTheme

object Routes {
    const val HOME = "home"
    const val TYPE = "type"
    const val MIND = "mind"
    const val ANALYTICS = "analytics"
    const val AI = "assistant"
    const val READ = "read"
    const val STUDIO = "studio"
    const val SETTINGS = "settings"
    const val ARENA = "arena"
    const val FOCUS = "focus"
    const val PLANNER = "planner"
    const val LIBRARY = "library"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: List<IconFacet>,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, "خانه", MrsadIcons.Home),
    BottomDestination(Routes.TYPE, "تایپ", MrsadIcons.Type),
    BottomDestination(Routes.MIND, "ذهن", MrsadIcons.Mind),
    BottomDestination(Routes.ANALYTICS, "تحلیل", MrsadIcons.Analytics),
    BottomDestination(Routes.AI, "دستیار", MrsadIcons.Assistant),
)

@Composable
fun MrsadRoot(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val motion = MrsadTheme.motion
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    // Light themes need dark system-bar icons; this must follow the theme,
    // not the device default.
    val view = LocalView.current
    val colors = MrsadTheme.colors
    LaunchedEffect(colors.isLight) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = colors.isLight
            isAppearanceLightNavigationBars = colors.isLight
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The floating bar is measured so that the screens behind it can
        // reserve exactly the right amount of clearance at the bottom of
        // their scroll viewport.
        val density = LocalDensity.current
        var bottomBarHeight by remember { mutableStateOf(0.dp) }

        CompositionLocalProvider(LocalBottomBarClearance provides bottomBarHeight) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showBottomBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
                // The screen moves in space, so it uses the spatial
                // spring; the fade is an effect and uses the effects
                // spring. The exit is quicker than the entrance, which is
                // what makes a navigation feel responsive rather than
                // slow.
                enterTransition = {
                    fadeIn(animationSpec = motion.effectsDefault()) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = motion.spatialDefault(),
                        )
                },
                exitTransition = { fadeOut(animationSpec = motion.effectsFast()) },
                popEnterTransition = {
                    fadeIn(animationSpec = motion.effectsDefault()) +
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = motion.spatialDefault(),
                        )
                },
                popExitTransition = { fadeOut(animationSpec = motion.effectsFast()) },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        container = container,
                        settings = settings,
                        onNavigate = navController::navigateToModule,
                    )
                }
                composable(Routes.TYPE) {
                    TypeScreen(container = container, settings = settings)
                }
                composable(Routes.MIND) {
                    MindScreen(container = container, settings = settings)
                }
                composable(Routes.ANALYTICS) {
                    AnalyticsScreen(container = container, settings = settings)
                }
                composable(Routes.AI) {
                    AssistantScreen(container = container, settings = settings)
                }
                composable(Routes.READ) {
                    ReadScreen(container = container, settings = settings)
                }
                composable(Routes.STUDIO) {
                    StudioScreen(container = container, settings = settings)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        settings = settings,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.ARENA) {
                    PlaceholderScreen(
                        title = "MRSAD ARENA",
                        environment = dev.mrsad.app.ui.theme.PageEnvironment.ARENA,
                        description = "چالش‌ها، مأموریت‌های روزانه، دستاوردها و رکوردهای شخصی — در فاز ۶ پیاده‌سازی می‌شود.",
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.FOCUS) {
                    FocusScreen(
                        container = container,
                        settings = settings,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.PLANNER) {
                    PlaceholderScreen(
                        title = "برنامهٔ روزانه",
                        environment = dev.mrsad.app.ui.theme.PageEnvironment.PLANNER,
                        description = "برنامه‌ریزی فعالیت‌ها و ردیابی عادت‌ها — در فاز ۶ پیاده‌سازی می‌شود.",
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.LIBRARY) {
                    PlaceholderScreen(
                        title = "کتابخانهٔ شخصی",
                        environment = dev.mrsad.app.ui.theme.PageEnvironment.LIBRARY,
                        description = "افزودن و مدیریت متن‌های خواندنی شخصی — در فاز ۶ پیاده‌سازی می‌شود.",
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        if (showBottomBar) {
            MrsadBottomBar(
                items = bottomDestinations.map { MrsadBottomBarItem(it.route, it.label, it.icon) },
                selectedRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .onSizeChanged { size ->
                        // `size.height` is in pixels; the clearance is a Dp.
                        bottomBarHeight = with(density) { size.height.toDp() }
                    },
            )
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToModule(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}
