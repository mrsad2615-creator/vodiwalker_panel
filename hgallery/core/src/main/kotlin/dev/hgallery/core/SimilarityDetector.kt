package dev.hgallery.core

/**
 * Traditional (non-AI) similarity used by the duplicate finder.
 *
 * Two stages, cheapest first:
 *  1. an exact-content hash groups byte-identical files;
 *  2. a 64-bit difference hash (dHash) then catches re-encoded, resized or
 *     re-saved copies that no longer match byte for byte.
 *
 * Nothing here guesses at a photo's subject. It compares pixels, which is what
 * makes the result explainable.
 */
object SimilarityDetector {

    /** Hamming distance at or below which two dHashes are the same picture. */
    const val DEFAULT_THRESHOLD: Int = 6

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Groups items whose dHash is within [threshold] of each other.
     *
     * Single-link grouping is intentional: a burst of near-identical frames
     * collapses into one group instead of producing pairs.
     */
    fun groupSimilar(
        hashes: Map<Long, Long>,
        threshold: Int = DEFAULT_THRESHOLD,
    ): List<List<Long>> {
        val parent = hashMapOf<Long, Long>()
        fun find(x: Long): Long {
            var root = x
            while (parent[root] != null && parent[root] != root) root = parent[root]!!
            // path compression
            var cursor = x
            while (parent[cursor] != null && parent[cursor] != cursor) {
                val next = parent[cursor]!!
                parent[cursor] = root
                cursor = next
            }
            return root
        }
        fun union(a: Long, b: Long) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        hashes.keys.forEach { parent[it] = it }

        val entries = hashes.entries.toList()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                if (hammingDistance(entries[i].value, entries[j].value) <= threshold) {
                    union(entries[i].key, entries[j].key)
                }
            }
        }

        return hashes.keys
            .groupBy { find(it) }
            .values
            .filter { it.size > 1 }
            .sortedByDescending { it.size }
    }

    /** Exact duplicates: identical size *and* identical content hash. */
    fun groupExact(hashes: Map<Long, String>): List<List<Long>> = hashes.entries
        .groupBy { it.value }
        .values
        .map { entry -> entry.map { it.key } }
        .filter { it.size > 1 }
        .sortedByDescending { it.size }
}
