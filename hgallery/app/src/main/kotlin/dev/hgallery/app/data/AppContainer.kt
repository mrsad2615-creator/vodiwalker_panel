package dev.hgallery.app.data

import android.content.Context
import dev.hgallery.core.CalendarLabels
import dev.hgallery.core.DateGrouping
import dev.hgallery.core.HgCalendar
import dev.hgallery.core.PERSIAN_MONTH_NAMES
import dev.hgallery.core.PersianCalendar
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

    val mediaStore = MediaStore(appContext, repository, thumbnails, actions)

    /** Theme, grid density and the remaining preferences. */
    val settings = SettingsRepository(appContext)

    /** Albums the user made, kept apart from the device's own folders. */
    val customAlbums = CustomAlbumStore(appContext)

    /** The app's own trash, for platforms that have no system one. */
    val trash = TrashStore(appContext)

    /** Camera facts, read on demand when the details sheet opens. */
    val exif = ExifReader(appContext)

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
        val calendar = CalendarLabels.calendarFor(locale.toLanguageTag())
        return { date, fallback ->
            when {
                // Today and Yesterday are already translated by the caller.
                fallback == "Today" || fallback == "Yesterday" -> fallback
                calendar == HgCalendar.PERSIAN -> {
                    val persian = PersianCalendar.fromGregorian(date)
                    val sameYear = persian.year ==
                        PersianCalendar.fromGregorian(java.time.LocalDate.now(zone)).year
                    val monthName = PERSIAN_MONTH_NAMES[persian.month - 1]
                    if (sameYear) {
                        "${persian.day} $monthName"
                    } else {
                        "${persian.day} $monthName ${persian.year}"
                    }
                }
                else -> {
                    val formatLocale = CalendarLabels.localeFor(HgCalendar.GREGORIAN, locale)
                    val sameYear = date.year == java.time.LocalDate.now(zone).year
                    val pattern = CalendarLabels.dayPattern(includeYear = !sameYear)
                    date.format(DateTimeFormatter.ofPattern(pattern, formatLocale))
                }
            }
        }
    }

    /** `Mehr 1404` style header for the month level, in either calendar. */
    fun monthFormatter(locale: Locale): (java.time.YearMonth) -> String {
        val calendar = CalendarLabels.calendarFor(locale.toLanguageTag())
        return { month ->
            if (calendar == HgCalendar.PERSIAN) {
                val persian = PersianCalendar.fromGregorian(month.atDay(15))
                "${PERSIAN_MONTH_NAMES[persian.month - 1]} ${persian.year}"
            } else {
                month.format(
                    DateTimeFormatter.ofPattern(
                        CalendarLabels.MONTH_PATTERN,
                        CalendarLabels.localeFor(HgCalendar.GREGORIAN, locale),
                    ),
                )
            }
        }
    }

    fun sections(items: List<dev.hgallery.core.MediaItem>, locale: Locale) =
        DateGrouping.group(items, zone, java.time.LocalDate.now(zone), dateHeaderFormatter(locale))

    /** `Mal 2026` style header for the month level of the timeline. */
    fun monthFormatter(locale: Locale): (java.time.YearMonth) -> String {
        val calendar = CalendarLabels.calendarFor(locale.toLanguageTag())
        val formatter = DateTimeFormatter.ofPattern(
            CalendarLabels.MONTH_PATTERN,
            CalendarLabels.localeFor(calendar, locale),
        )
        return { month -> month.format(formatter) }
    }

    fun months(items: List<dev.hgallery.core.MediaItem>, locale: Locale) =
        dev.hgallery.core.MonthGrouping.group(items, zone, monthFormatter(locale))
}
