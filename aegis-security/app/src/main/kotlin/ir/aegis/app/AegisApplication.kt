package ir.aegis.app

import android.app.Application
import ir.aegis.core.database.AegisDatabase
import ir.aegis.core.database.DataStorePreferencesRepository
import ir.aegis.core.database.RoomAegisRepository
import ir.aegis.core.database.WorkManagerReminderScheduler
import ir.aegis.core.maps.VectorMapEngine
import ir.aegis.core.model.AegisRepository
import ir.aegis.core.model.PreferencesRepository
import ir.aegis.core.model.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database: AegisDatabase = AegisDatabase.build(application)
    val roomRepository: RoomAegisRepository = RoomAegisRepository(database)
    val repository: AegisRepository = roomRepository
    val preferencesRepository: PreferencesRepository = DataStorePreferencesRepository(application)
    val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(application)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            roomRepository.ensureSeeded()
        }
    }
}

class AegisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        VectorMapEngine.registerAssetOpener { path ->
            runCatching { assets.open(path.removePrefix("/")) }.getOrNull()
        }
        container = AppContainer(this)
    }
}
