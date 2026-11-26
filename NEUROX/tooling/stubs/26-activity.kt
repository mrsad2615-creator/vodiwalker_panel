// NEUROX offline compile stubs - activity-component.
package androidx.activity

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner

open class ComponentActivity : android.app.Activity(), LifecycleOwner, SavedStateRegistryOwner {
    override val lifecycle: Lifecycle = Lifecycle()
    override fun onCreate(savedInstanceState: Bundle?) {}

    /**
     * The Activity result bridge. Stubbed with the real registration shape: the
     * callback is stored and would be invoked when the launched intent returns.
     */
    fun <I, O> registerForActivityResult(
        contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
        callback: (O) -> Unit,
    ): androidx.activity.result.ActivityResultLauncher<I> =
        androidx.activity.result.ActivityResultLauncher()
}

