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

    val mediaStore = MediaStore(repository, thumbnails)

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
