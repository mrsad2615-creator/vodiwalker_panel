package dev.mrsad.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.mrsad.app.data.AppContainer

class MrsadApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // One source of truth for "is the app visible" used to suspend
        // animations, timers and heavy work when backgrounded (spec §7, §26).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.setProcessForeground(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                container.setProcessForeground(false)
            }
        })
    }
}
