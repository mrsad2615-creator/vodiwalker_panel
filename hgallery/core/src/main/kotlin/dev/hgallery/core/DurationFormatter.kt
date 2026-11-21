package dev.hgallery.core

/** Duration formatting for video tiles and the player. */
object DurationFormatter {

    /** Tile badge: `0:07`, `1:42`, `1:02:03`. */
    fun badge(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            "$hours:${pad(minutes)}:${pad(seconds)}"
        } else {
            "$minutes:${pad(seconds)}"
        }
    }

    /** Detail row: `1 min 42 s`, `1 h 02 min`. */
    fun detail(ms: Long, plural: (String) -> String = { it }): String {
        if (ms <= 0) return "0 s"
        val total = ms / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return when {
            hours > 0 -> "$hours h ${pad(minutes)} min"
            minutes > 0 -> "$minutes ${plural("min")} ${pad(seconds)} s"
            else -> "$seconds s"
        }
    }

    private fun pad(value: Long): String = value.toString().padStart(2, '0')
}
