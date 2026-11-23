package dev.hgallery.app.data

import android.content.Context
import dev.hgallery.core.DateGrouping
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hand-rolled dependency container.
 *
 * A DI framework would add a build plugin and generated code for four objects.
 * This is a single application-scoped holder instead, created once in
 * `HGalleryApplication`.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Exposed so the viewer can decode its own bounded bitmaps. */
    val contentResolver: android.content.ContentResolver get() = appContext.contentResolver

    val thumbnails = ThumbnailStore(appContext)

    private val repository = MediaRepository(appContext)

    /** Platform dialogs and the share sheet; needs an application context only. */
    val actions = MediaActions(appContext)

    val mediaStore = MediaStore(repository, thumbnails, actions)

    /** Theme, grid density and the remaining preferences. */
    val settings = SettingsRepository(appContext)

    /** Albums the user made, kept apart from the device's own folders. */
    val customAlbums = CustomAlbumStore(appContext)

    /** The app's own trash, for platforms that have no system one. */
    val trash = TrashStore(appContext)

    /**
     * The installed version, read from the package manager.
     *
     * Settings shows it because a user reporting a problem is asked for a version, and
     * the value must be the installed one rather than a constant copied into the build
     * file twice.
     */
    fun versionName(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    val zone: ZoneId = ZoneId.systemDefault()

    /**
     * Date labels in the reference's order: "29 August 2020" for older days,
     * with Today/Yesterday for the newest two.
     */
    fun dateHeaderFormatter(locale: Locale): (java.time.LocalDate, String) -> String {
        val yearFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
        val monthFormatter = DateTimeFormatter.ofPattern("d MMMM", locale)
        return { date, fallback ->
            when (fallback) {
                "Today", "Yesterday" -> fallback
                else -> {
                    val sameYear = date.year == java.time.LocalDate.now(zone).year
                    date.format(if (sameYear) monthFormatter else yearFormatter)
                }
            }
        }
    }

    fun sections(items: List<dev.hgallery.core.MediaItem>, locale: Locale) =
        DateGrouping.group(items, zone, java.time.LocalDate.now(zone), dateHeaderFormatter(locale))

    /** `Mal 2026` style header for the month level of the timeline. */
    fun monthFormatter(locale: Locale): (java.time.YearMonth) -> String {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        return { month -> month.format(formatter) }
    }

    fun months(items: List<dev.hgallery.core.MediaItem>, locale: Locale) =
        dev.hgallery.core.MonthGrouping.group(items, zone, monthFormatter(locale))
}
