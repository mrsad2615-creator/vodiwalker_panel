package com.neurox.common.format

/**
 * Locale-independent numeric and duration formatting helpers.
 *
 * Presentation layers use Android's `NumberFormat` so Persian shows Persian
 * digits. These helpers exist for logs, CSV export, test goldens and any place
 * where a stable ASCII format is required.
 */
object Numbers {

    /** Rounds to [decimals] digits and renders with `Locale.ROOT` (ASCII digits). */
    fun format(value: Double, decimals: Int): String {
        require(decimals in 0..6) { "decimals must be in 0..6" }
        if (value.isNaN() || value.isInfinite()) return "-"
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        val rounded = kotlin.math.round(kotlin.math.abs(value) * factor) / factor
        val text = if (decimals == 0) {
            rounded.toLong().toString()
        } else {
            val whole = rounded.toLong()
            val frac = kotlin.math.round((rounded - whole) * factor).toLong()
                .toString().padStart(decimals, '0')
            "$whole.$frac"
        }
        return if (value < 0 && rounded != 0.0) "-$text" else text
    }

    /** Renders a compact duration such as `1:04` or `12:00`. */
    fun duration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "0:00"
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    /** Percentage with one decimal place. */
    fun percent(fraction: Double): String = format(fraction * 100.0, 1) + "%"
}

/**
 * Computes a bounded influence factor for adaptive decisions.
 *
 * The adaptive engine never reacts to a single result at full strength; it uses
 * a sample weight that grows sub-linearly and is capped. Base 4 samples give a
 * weight of 0.5; 16 samples reach 1.0 and never exceed it.
 */
object SampleWeight {
    const val FULL_AT_SAMPLES = 16.0

    fun of(sampleCount: Int): Double {
        if (sampleCount <= 0) return 0.0
        val raw = kotlin.math.sqrt(sampleCount.toDouble() / 4.0) / 2.0
        return raw.coerceIn(0.0, 1.0)
    }
}
