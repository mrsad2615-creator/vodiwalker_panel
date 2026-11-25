// NEUROX offline compile stubs - navigation-compose.
package androidx.navigation.compose

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController

@Composable
fun NavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    route: String? = null,
    enterTransition: (AnimatedVisibilityScope.() -> EnterTransition)? = null,
    exitTransition: (AnimatedVisibilityScope.() -> ExitTransition)? = null,
    popEnterTransition: (AnimatedVisibilityScope.() -> EnterTransition)? = null,
    popExitTransition: (AnimatedVisibilityScope.() -> ExitTransition)? = null,
    builder: NavGraphBuilder.() -> Unit,
) {}

@Composable
fun NavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    route: String? = null,
    builder: NavGraphBuilder.() -> Unit,
) {}

@Composable
fun NavHost(
    navController: NavHostController,
    graph: NavGraph,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    enterTransition: (AnimatedVisibilityScope.() -> EnterTransition)? = null,
    exitTransition: (AnimatedVisibilityScope.() -> ExitTransition)? = null,
) {}

class NavGraphBuilder {
    fun composable(route: String, content: @Composable (NavBackStackEntry) -> Unit) {}

    fun composable(
        route: String,
        arguments: List<Any> = emptyList(),
        deepLinks: List<Any> = emptyList(),
        enterTransition: (AnimatedVisibilityScope.() -> EnterTransition)? = null,
        exitTransition: (AnimatedVisibilityScope.() -> ExitTransition)? = null,
        popEnterTransition: (AnimatedVisibilityScope.() -> EnterTransition)? = null,
        popExitTransition: (AnimatedVisibilityScope.() -> ExitTransition)? = null,
        content: @Composable (NavBackStackEntry) -> Unit,
    ) {}
}

@Composable
fun NavHostController.currentBackStackEntryAsState(): State<NavBackStackEntry?> = error("stub")

@Composable
fun rememberNavController(vararg navigators: Any): NavHostController = NavHostController()
