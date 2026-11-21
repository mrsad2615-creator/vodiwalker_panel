package dev.hgallery.core

/**
 * Formats byte sizes the way Huawei Gallery's storage and detail screens do:
 * a binary divisor with a short, upper-case unit and no trailing space.
 *
 * Examples: 0 B, 999 B, 1,0 KB is *not* produced — Huawei prints `1.02 KB`,
 * using a dot as the decimal separator in the English locale.
 */
object ByteFormatter {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"

        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }

        // Two decimals for KB/MB, one for GB and above — the same reduction
        // Huawei applies so that a large disk does not read as noise.
        val decimals = if (unit <= 2) 2 else 1
        val text = roundToString(value, decimals)
        return "$text ${units[unit]}"
    }

    private fun roundToString(value: Double, decimals: Int): String {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        val scaled = kotlin.math.round(value * factor) / factor
        val whole = scaled.toLong()
        val fraction = kotlin.math.round((scaled - whole) * factor).toLong()
        if (decimals == 0) return whole.toString()
        return "$whole.${fraction.toString().padStart(decimals, '0')}"
    }
}
