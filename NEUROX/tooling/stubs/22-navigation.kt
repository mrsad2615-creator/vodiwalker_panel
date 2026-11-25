// NEUROX offline compile stubs - Navigation Compose.
package androidx.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner

open class NavBackStackEntry : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    override val lifecycle: Lifecycle = Lifecycle()

    /** Route arguments, as a Bundle, exactly like the real API. */
    open val arguments: android.os.Bundle? get() = null

    /** The destination this entry represents. */
    open val destination: NavDestination get() = NavDestination()
}

/** A destination pattern; `route` is the pattern, e.g. "session/{exerciseId}". */
open class NavDestination {
    open val route: String? = null
}

class NavGraph(val route: String? = null)

/**
 * NavType.
 *
 * The real class is abstract with one instance per argument type. The app only names
 * StringType, so a nominal type with that constant is enough to type-check the DSL.
 */
open class NavType<T> {
    companion object {
        val StringType: NavType<String> = NavType()
    }
}

/** Builds one route argument; the DSL's `arguments = listOf(navArgument("x") { ... })`. */
fun navArgument(name: String, builder: NavArgumentBuilder.() -> Unit = {}): NamedNavArgument =
    NamedNavArgument(name, NavArgumentBuilder().apply(builder).build())

class NamedNavArgument(val name: String, val argument: NavArgument)

class NavArgument(val type: NavType<*>?, val nullable: Boolean, val defaultValue: Any?)

class NavArgumentBuilder {
    var type: NavType<*>? = null
    var nullable: Boolean = false
    var defaultValue: Any? = null

    fun build(): NavArgument = NavArgument(type, nullable, defaultValue)
}

open class NavHostController {
    var graph: NavGraph = NavGraph()
    open val currentBackStackEntry: NavBackStackEntry? get() = null

    fun navigate(route: String) {}
    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit) {}

    fun popBackStack(): Boolean = true
    fun popBackStack(route: String, inclusive: Boolean = false): Boolean = true
}

/** The navigate { } options DSL: popUpTo, launchSingleTop, inclusive. */
class NavOptionsBuilder {
    var launchSingleTop: Boolean = false
    var restoreState: Boolean = false
    var popUpToRoute: String? = null

    fun popUpTo(route: String, builder: PopUpToBuilder.() -> Unit = {}) {
        popUpToRoute = route
    }
}

class PopUpToBuilder {
    var inclusive: Boolean = false
    var saveState: Boolean = false
}
