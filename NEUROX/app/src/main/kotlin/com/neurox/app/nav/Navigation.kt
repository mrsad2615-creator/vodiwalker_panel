package com.neurox.app.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.neurox.designsystem.tokens.NxMotion
import com.neurox.domain.model.Module
import com.neurox.presentation.nav.AppDestination
import com.neurox.presentation.nav.NavigationState

/**
 * Navigation.
 *
 * THE DESTINATION LIST LIVES IN THE PRESENTATION MODULE (a pure, tested enum) and
 * this file only maps it to routes. That is deliberate: which screens exist, and
 * which are reachable, is a product decision that should be testable without a
 * device. Compose owns the back stack; the presentation module owns the graph.
 *
 * DEPTH RULE: session, vault and onboarding are "immersive" destinations - the bottom
 * bar is hidden so nothing competes with the stimulus. Every other destination keeps
 * the bar, because the four modules are meant to be one gesture apart.
 */
@Composable
fun NeuroxNavHost(
    navController: NavHostController,
    startDestination: AppDestination,
    bottomBar: @Composable (current: AppDestination) -> Unit,
    screen: @Composable (AppDestination, NavHostController) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = AppDestination.fromRoute(backStackEntry?.destination?.route) ?: startDestination
    val showBar = NavigationState.showsBottomBar(current)

    Scaffold(
        bottomBar = { if (showBar) bottomBar(current) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination.route,
                enterTransition = { fadeIn(animationSpec = tween(NxMotion.FAST_MILLIS)) },
                exitTransition = { fadeOut(animationSpec = tween(NxMotion.FAST_MILLIS)) },
            ) {
                AppDestination.entries.forEach { destination ->
                    composable(destination.route) {
                        screen(destination, navController)
                    }
                }
            }
        }
    }
}

/** Navigates to a destination, collapsing duplicates on the back stack. */
fun NavHostController.goTo(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(AppDestination.HOME.route) { inclusive = false }
        launchSingleTop = true
    }
}

/** Opens the browser for one module. Every module browser is one destination. */
fun NavHostController.goToModule(module: Module) {
    val destination = AppDestination.fromModule(module) ?: AppDestination.HOME
    navigate(destination.route) { launchSingleTop = true }
}
