// NEUROX offline compile stubs - activity-component.
package androidx.activity

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

open class ComponentActivity : android.content.Context(), LifecycleOwner, SavedStateRegistryOwner {
    override val lifecycle: Lifecycle = Lifecycle()
    open fun onCreate(savedInstanceState: Bundle?) {}
    fun enableEdgeToEdge() {}
}

@Composable
fun ComponentActivity.setContent(content: @Composable () -> Unit) {}
