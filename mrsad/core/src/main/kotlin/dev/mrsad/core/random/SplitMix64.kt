package dev.mrsad.core.random

/**
 * SplitMix64: tiny, fast, high-quality deterministic PRNG.
 *
 * Used for reproducible daily content selection (same day ⇒ same exercise
 * set on every device) and for procedural exercise generation. Dependency-free
 * so the core stays pure Kotlin.
 */
class SplitMix64(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Uniform value in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun nextInt(fromInclusive: Int, toExclusive: Int): Int =
        fromInclusive + nextInt(toExclusive - fromInclusive)

    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /** Fisher–Yates shuffle copy. */
    fun <T> shuffle(items: List<T>): List<T> {
        val out = items.toMutableList()
        for (i in out.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = out[i]; out[i] = out[j]; out[j] = tmp
        }
        return out
    }

    /** Deterministic seed from a date key such as "2026-11-19" plus a salt. */
    companion object {
        fun seedFor(key: String, salt: Int = 0): Long {
            var h = 0xcbf29ce484222325uL.toLong() // FNV-1a 64 offset basis
            for (ch in key) {
                h = h xor ch.code.toLong()
                h *= 0x100000001b3L
            }
            h = h xor (salt.toLong() * 0x9E3779B97F4A7C15uL.toLong())
            return h
        }
    }
}
