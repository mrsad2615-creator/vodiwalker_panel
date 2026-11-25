// NEUROX offline compile stubs - navigation-compose.
package androidx.navigation.compose

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController

/** The DSL receiver for a nav graph. Extensions below attach destinations to it. */
class NavGraphBuilder

/**
 * composable() is an extension on NavGraphBuilder in the real library, declared in
 * androidx.navigation.compose. It is a top-level extension here for the same reason,
 * which also makes the import in app/nav a real check.
 */
fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<Any> = emptyList(),
    enterTransition: (AnimatedVisibilityScope.() -> EnterTransition)? = null,
    exitTransition: (AnimatedVisibilityScope.() -> ExitTransition)? = null,
    popEnterTransition: (AnimatedVisibilityScope.() -> EnterTransition)? = null,
    popExitTransition: (AnimatedVisibilityScope.() -> ExitTransition)? = null,
    content: @Composable (NavBackStackEntry) -> Unit,
) {}

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
    graph: NavGraph,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {}

@Composable
fun NavHostController.currentBackStackEntryAsState(): State<NavBackStackEntry?> = error("stub")

@Composable
fun rememberNavController(vararg navigators: Any): NavHostController = NavHostController()
