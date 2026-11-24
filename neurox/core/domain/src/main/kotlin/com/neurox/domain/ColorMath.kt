package com.neurox.domain

import kotlin.math.pow

object ColorMath {
    fun parseHex(text: String): Long? {
        val digits = text.trim().removePrefix("#")
        if (!Regex("[0-9a-fA-F]{6}").matches(digits)) return null
        return 0xFF000000 or digits.toLong(16)
    }
    private fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 255) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return channel(16) * 0.2126 + channel(8) * 0.7152 + channel(0) * 0.0722
    }
    fun contrast(first: Long, second: Long): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
    fun accessibleAccent(requested: Long, surface: Long, dark: Boolean): Long {
        if (contrast(requested, surface) >= 4.5) return requested
        val target = if (dark) 255 else 0
        for (step in 1..100) {
            val t = step / 100.0
            fun mix(shift: Int): Long {
                val c = ((requested shr shift) and 255).toInt()
                return (c + (target - c) * t).toLong().coerceIn(0, 255)
            }
            val result = 0xFF000000 or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            if (contrast(result, surface) >= 4.5) return result
        }
        return if (dark) 0xFFFFFFFF else 0xFF000000
    }
}
