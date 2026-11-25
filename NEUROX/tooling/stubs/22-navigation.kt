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
}

class NavGraph(val route: String? = null)

open class NavHostController {
    var graph: NavGraph = NavGraph()
    fun navigate(route: String, builder: Any? = null) {}
    fun popBackStack(): Boolean = true
    fun popBackStack(route: String, inclusive: Boolean = false): Boolean = true
}
