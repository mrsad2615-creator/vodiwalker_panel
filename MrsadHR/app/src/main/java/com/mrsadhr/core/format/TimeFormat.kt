package com.mrsadhr.core.format

/** Time formatting + localised numeral helpers (Persian digits when the UI is Persian). */
object TimeFormat {

    fun parts(ms: Long): Triple<Int, Int, Int> {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return Triple((total / 3600L).toInt(), ((total % 3600L) / 60L).toInt(), (total % 60L).toInt())
    }

    /** 04:32 for short durations, 1:04:32 when hours are involved. */
    fun hms(ms: Long): String {
        val (h, m, s) = parts(ms)
        return if (h > 0) "${h}:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
    }

    fun hmsAlways(ms: Long): String {
        val (h, m, s) = parts(ms)
        return "${two(h)}:${two(m)}:${two(s)}"
    }

    /** Stopwatch readout with centiseconds. */
    fun stopwatch(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val cs = (total % 1000L) / 10L
        val (h, m, s) = parts(total)
        return if (h > 0) "${h}:${two(m)}:${two(s)}.${two(cs.toInt())}" else "${two(m)}:${two(s)}.${two(cs.toInt())}"
    }

    fun two(v: Int): String = if (v < 10) "0$v" else v.toString()

    fun two(v: Long): String = two(v.toInt())

    /** Compact preset label: 45s / ۴۵ث, 5m / ۵د, 12:30. */
    fun presetLabel(ms: Long, persian: Boolean = false): String {
        val (h, m, s) = parts(ms)
        val secUnit = "s"
        val minUnit = "m"
        return when {
            h == 0 && m == 0 -> "$s$secUnit"
            h == 0 && s == 0 -> "$m$minUnit"
            h == 0 -> "$m:${two(s)}"
            else -> "$h:${two(m)}:${two(s)}"
        }
    }

    fun spokenDuration(ms: Long, persian: Boolean): String {
        val (h, m, s) = parts(ms)
        val parts = mutableListOf<String>()
        if (h > 0) parts += if (persian) "$h ساعت" else "$h h"
        if (m > 0) parts += if (persian) "$m دقیقه" else "$m min"
        if (s > 0 && h == 0) parts += if (persian) "$s ثانیه" else "$s s"
        return if (parts.isEmpty()) (if (persian) "صفر" else "0") else parts.joinToString(" ")
    }
}

object Numbers {
    private val persian = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun localize(text: String, toPersian: Boolean): String {
        if (!toPersian) return text
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(if (ch in '0'..'9') persian[ch - '0'] else ch)
        }
        return sb.toString()
    }
}
