package dev.hgallery.app.ui.photos

import dev.hgallery.core.DateSection
import dev.hgallery.core.MediaItem

/**
 * The timeline flattened into the rows a single `LazyVerticalGrid` can render.
 *
 * Nesting a lazy grid per day would look identical but break recycling: every
 * day would keep its own scroll state and its own measured height, which is
 * exactly the pattern that makes third-party galleries stutter on long
 * libraries.
 */
sealed interface TimelineRows {
    data class Header(val label: String) : TimelineRows
    data class Cells(val items: List<MediaItem>) : TimelineRows
}

/** Separator object so the sealed interface stays a pure value type. */
object TimelineRowFactory {
    fun build(sections: List<DateSection>, columns: Int = 4): List<TimelineRows> {
        val out = ArrayList<TimelineRows>(sections.size * 4)
        for (section in sections) {
            out += TimelineRows.Header(section.header)
            out += section.items.chunked(columns).map { TimelineRows.Cells(it) }
        }
        return out
    }
}
