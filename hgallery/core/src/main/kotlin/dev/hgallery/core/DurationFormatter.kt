package dev.hgallery.core

/** Duration formatting for video tiles and the player. */
object DurationFormatter {

    private const val MS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L

    /** Tile badge: `0:07`, `1:42`, `1:02:03`. */
    fun badge(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / MS_PER_SECOND
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0L) {
            "$hours:${pad(minutes)}:${pad(seconds)}"
        } else {
            "$minutes:${pad(seconds)}"
        }
    }

    /** Detail row: `1 min 42 s`, `1 h 02 min`. */
    fun detail(ms: Long, plural: (String) -> String = { it }): String {
        if (ms <= 0) return "0 s"
        val total = ms / MS_PER_SECOND
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return when {
            hours > 0L -> "$hours h ${pad(minutes)} min"
            minutes > 0L -> "$minutes ${plural("min")} ${pad(seconds)} s"
            else -> "$seconds s"
        }
    }

    private fun pad(value: Long): String = value.toString().padStart(2, '0')
}
