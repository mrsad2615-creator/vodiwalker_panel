package dev.mrsad.hr

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.*
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Entity
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val duration: Long,
)

@Dao
interface Presets {
    @Query("SELECT * FROM Preset ORDER BY id") fun all(): Flow<List<Preset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: Preset)

    @Delete suspend fun delete(p: Preset)
}

@Database(entities = [Preset::class], version = 1, exportSchema = false)
abstract class Library : RoomDatabase() {
    abstract fun presets(): Presets
}

val Context.options by preferencesDataStore("studio")

class Studio : Application() {
    val clocks by lazy { ClockStore(this) }
    val presets by lazy {
        Room.databaseBuilder(this, Library::class.java, "library.db").build().presets()
    }
}

class ClockStore(ctx: Context) {
    private val db = ctx.getSharedPreferences("transport", 0)
    private val boot = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun load(w: Boolean): Clock {
        val k = w.toString()
        val c =
            Clock(
                db.getLong(k + "goal", 300000),
                db.getLong(k + "bank", 0),
                db.getLong(k + "anchor", 0),
                Phase.valueOf(db.getString(k + "phase", "READY")!!),
                w,
                db.getString(k + "laps", "")!!.split(',').mapNotNull(String::toLongOrNull),
            )
        return if (c.phase == Phase.RUNNING && db.getInt(k + "boot", boot) != boot) c.reset() else c
    }

    val timer = MutableStateFlow(load(false))
    val watch = MutableStateFlow(load(true))

    @Synchronized
    fun update(w: Boolean, f: (Clock) -> Clock) {
        val stream = if (w) watch else timer
        val c = f(stream.value)
        if (c == stream.value) return
        val k = w.toString()
        db.edit()
            .putLong(k + "goal", c.goal)
            .putLong(k + "bank", c.bank)
            .putLong(k + "anchor", c.anchor)
            .putString(k + "phase", c.phase.name)
            .putString(k + "laps", c.laps.joinToString(","))
            .putInt(k + "boot", boot)
            .commit()
        stream.value = c
    }
}

class Desk(val studio: Studio) : AndroidViewModel(studio) {
    val repo = studio.clocks
    val prefs = studio.options.data
    val presets = studio.presets.all()

    fun pref(key: String, value: String) {
        viewModelScope.launch { studio.options.edit { it[stringPreferencesKey(key)] = value } }
    }

    fun save(p: Preset) {
        viewModelScope.launch { studio.presets.save(p) }
    }

    fun delete(p: Preset) {
        viewModelScope.launch { studio.presets.delete(p) }
    }

    fun action(w: Boolean, act: String) {
        val n = SystemClock.elapsedRealtime()
        repo.update(w) {
            when (act) {
                "start" -> it.start(n)
                "pause" -> it.pause(n)
                "lap" -> it.lap(n)
                "clear" -> it.copy(laps = emptyList())
                else -> it.reset()
            }
        }
        if (!w) {
            if (repo.timer.value.phase == Phase.RUNNING)
                studio.startForegroundService(
                    android.content.Intent(studio, TimerService::class.java),
                )
            else studio.stopService(android.content.Intent(studio, TimerService::class.java))
        }
    }
}
