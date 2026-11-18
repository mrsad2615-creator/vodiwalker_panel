package ai.dana.media

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface CaptureResult {
    class Ready(val frame: MemoryFrame) : CaptureResult
    class Failed(val message: Int) : CaptureResult
}

/** Private one-shot handoff in the app process. Never stores a screen frame or grant on disk. */
@Singleton
internal class CaptureInbox @Inject constructor() {
    private val event = MutableStateFlow<CaptureResult?>(null)
    val results = event.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var next = 0L
    private var active = 0L
    private val expire = Runnable { event.value = null }

    @MainThread
    fun issue(): Long {
        clear()
        active = ++next
        return active
    }

    @MainThread
    fun isCurrent(ticket: Long) = ticket != 0L && active == ticket

    @MainThread
    fun publish(ticket: Long, result: CaptureResult) {
        if (!isCurrent(ticket)) return
        active = 0L
        event.value = result
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, 40_000)
    }

    @MainThread
    fun clear() {
        active = 0L
        handler.removeCallbacks(expire)
        event.value = null
    }
}
