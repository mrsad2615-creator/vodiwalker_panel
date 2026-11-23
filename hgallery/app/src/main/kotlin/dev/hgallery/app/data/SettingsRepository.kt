package dev.hgallery.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.hgallery.app.ui.theme.HgThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Every user preference, in one place.
 *
 * ## Why DataStore and not SharedPreferences
 *
 * These values drive composition: the theme, and later the grid density. SharedPreferences
 * is synchronous and unobservable, so a screen that changes the theme would have to be
 * restarted to see it. DataStore gives a `Flow`, which is what a theme controller needs.
 *
 * ## Why one store and not several
 *
 * A second DataStore on the same file throws. Grouping every preference behind one
 * object makes that mistake impossible to write.
 *
 * ## What is deliberately not here
 *
 * Media data. Favourites and playback positions belong to the media, not to the UI, and
 * are stored where the media operations can reach them with a single transaction. Mixing
 * the two would put a grid setting and a photo's state in the same file and the same
 * write path.
 */
private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "hgallery-settings")

/** The values the app reads at composition time. */
data class HgSettings(
    val themeMode: HgThemeMode = HgThemeMode.SYSTEM,
    /** Columns in the photo grid, 2 to 6. Four is the reference density. */
    val gridColumns: Int = DEFAULT_GRID_COLUMNS,
    /** Whether the video player shows its filmstrip. */
    val filmstripVisible: Boolean = true,
    /** Whether playback continues to the next video when one ends. */
    val autoplayNext: Boolean = false,
) {
    companion object {
        const val DEFAULT_GRID_COLUMNS = 4

        /** The range the pinch gesture can reach, and the range the setting validates to. */
        const val MIN_GRID_COLUMNS = 2
        const val MAX_GRID_COLUMNS = 6
    }
}

/**
 * Reads and writes [HgSettings].
 *
 * Every setter suspends and returns after the write is durable. Callers run them from a
 * coroutine scope tied to the screen, which is the only place a preference can change.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    val settings: Flow<HgSettings> = appContext.preferences.data.map { prefs ->
        HgSettings(
            themeMode = HgThemeMode.fromStored(prefs[Keys.THEME_MODE]),
            gridColumns = (prefs[Keys.GRID_COLUMNS] ?: HgSettings.DEFAULT_GRID_COLUMNS)
                .coerceIn(HgSettings.MIN_GRID_COLUMNS, HgSettings.MAX_GRID_COLUMNS),
            filmstripVisible = prefs[Keys.FILMSTRIP_VISIBLE] ?: true,
            autoplayNext = prefs[Keys.AUTOPLAY_NEXT] ?: false,
        )
    }

    suspend fun setThemeMode(mode: HgThemeMode) {
        appContext.preferences.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /**
     * Stores the column count, clamped here rather than at every call site.
     *
     * A pinch gesture computes its target from finger distance and can overshoot; storing
     * an unclamped value would leave the grid in a state the setting can never return to.
     */
    suspend fun setGridColumns(columns: Int) {
        appContext.preferences.edit {
            it[Keys.GRID_COLUMNS] = columns.coerceIn(
                HgSettings.MIN_GRID_COLUMNS,
                HgSettings.MAX_GRID_COLUMNS,
            )
        }
    }

    suspend fun setFilmstripVisible(visible: Boolean) {
        appContext.preferences.edit { it[Keys.FILMSTRIP_VISIBLE] = visible }
    }

    suspend fun setAutoplayNext(enabled: Boolean) {
        appContext.preferences.edit { it[Keys.AUTOPLAY_NEXT] = enabled }
    }

    /** The keys, named once. A typo in a key is a silently lost preference. */
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val FILMSTRIP_VISIBLE = booleanPreferencesKey("filmstrip_visible")
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
    }
}
