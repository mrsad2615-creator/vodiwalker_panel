// NEUROX offline compile stubs - lifecycle.
package androidx.lifecycle

/** Lifecycle.Event is nested inside Lifecycle in the real library. */
class Lifecycle {

    enum class Event {
        ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ON_ANY,
    }

    fun addObserver(observer: LifecycleObserver) {}
    fun removeObserver(observer: LifecycleObserver) {}
}

interface LifecycleObserver

fun interface LifecycleEventObserver : LifecycleObserver {
    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event)
}

interface LifecycleOwner {
    val lifecycle: Lifecycle
}

interface ViewModelStoreOwner

interface ViewModel
