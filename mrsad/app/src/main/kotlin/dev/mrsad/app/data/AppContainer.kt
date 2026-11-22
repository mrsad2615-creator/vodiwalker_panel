package dev.mrsad.app.data

import android.content.Context
import dev.mrsad.app.audio.MrsadAudioEngine
import dev.mrsad.app.data.db.MrsadDatabase
import dev.mrsad.app.data.settings.SettingsRepository
import dev.mrsad.app.data.typing.TypingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container — deliberate choice over a DI framework:
 * the graph is small, explicit and testable, and adds zero build risk.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    fun appContextOrNull(): android.content.Context? = applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: MrsadDatabase = MrsadDatabase.build(applicationContext)

    val settingsRepository = SettingsRepository(applicationContext)

    val typingRepository = TypingRepository(database.typingSessionDao(), database.activityDao())

    val audioEngine = MrsadAudioEngine()

    private val foreground = MutableStateFlow(true)
    val isForeground: kotlinx.coroutines.flow.StateFlow<Boolean> get() = foreground
    fun setProcessForeground(value: Boolean) { foreground.value = value }

    init {
        // Seed default settings once so first frame has real values (no flash).
        applicationScope.launch {
            settingsRepository.ensureDefaults()
            settingsRepository.settings.collectLatest { s ->
                audioEngine.soundEnabled = s.soundEnabled
            }
        }
    }
}
