package dev.mrsad.core.text

/**
 * Persian-first text utilities.
 *
 * All training text (typing targets, reading material, library content) flows
 * through these helpers so that Arabic-vs-Persian codepoints, ZWNJ and
 * punctuation behave consistently across every module.
 */
object PersianText {

    const val ZWNJ: Char = '\u200C'      // نیم‌فاصله
    const val TATWEEL: Char = '\u0640'   // کشیده

    /** Arabic Yeh and Alef Maksura → Persian Yeh. */
    private const val ARABIC_YEH = '\u064A'
    private const val ALEF_MAKSURA = '\u0649'
    const val PERSIAN_YEH = '\u06CC'

    /** Arabic Kaf → Persian Keheh. */
    private const val ARABIC_KAF = '\u0643'
    const val PERSIAN_KEHEH = '\u06A9'

    private const val ARABIC_HEH = '\u0647'
    private const val ARABIC_TEH_MARBUTA = '\u0629'

    /** Persian digits ۰..۹ and Arabic-Indic digits ٠..٩. */
    private const val PERSIAN_ZERO = '\u06F0'
    private const val ARABIC_ZERO = '\u0660'

    /** Typical «keyboard reality»: users type a normal space where a ZWNJ belongs. */
    private const val SPACE = ' '

    private val PERSIAN_LETTERS: Regex = Regex("[\\u0600-\\u06FF]")

    fun isPersian(ch: Char): Boolean = PERSIAN_LETTERS.matches(ch.toString())

    /** True when the string contains at least one Persian/Arabic codepoint. */
    fun containsPersian(text: String): Boolean = text.any { isPersian(it) }

    /**
     * Canonical normalisation used when comparing typed input with a target.
     *
     * - Arabic Yeh / Alef Maksura → Persian Yeh
     * - Arabic Kaf → Persian Keheh
     * - Arabic Teh Marbuta → Heh (people type Heh on a Persian keyboard)
     * - removes Tatweel
     * - converts Arabic-Indic digits to Persian digits
     * - collapses repeated spaces (ZWNJ is preserved: it is meaningful)
     */
    fun normalizeForComparison(text: String): String {
        val out = StringBuilder(text.length)
        for (raw in text) {
            val ch = when (raw) {
                ARABIC_YEH, ALEF_MAKSURA -> PERSIAN_YEH
                ARABIC_KAF -> PERSIAN_KEHEH
                ARABIC_TEH_MARBUTA -> ARABIC_HEH
                TATWEEL -> continue
                else -> raw
            }
            val normalized = if (ch in ARABIC_ZERO..(ARABIC_ZERO + 9)) {
                PERSIAN_ZERO + (ch - ARABIC_ZERO)
            } else {
                ch
            }
            if (normalized == SPACE && out.isNotEmpty() && out.last() == SPACE) continue
            out.append(normalized)
        }
        return out.toString().trim()
    }

    /**
     * Whether a typed character [typed] is an acceptable key press for the
     * target character [expected] (both already normalised).
     *
     * Typing trainers must accept Space for ZWNJ — a Persian keyboard cannot
     * physically produce ZWNJ with one key on most Android IMEs.
     */
    fun matches(typed: Char, expected: Char, acceptSpaceForZwnj: Boolean = true): Boolean {
        if (typed == expected) return true
        if (acceptSpaceForZwnj && expected == ZWNJ && typed == SPACE) return true
        return false
    }

    /** Display normalisation (does not collapse spaces inside the source text). */
    fun normalizeForDisplay(text: String): String = text
        .replace(ARABIC_YEH, PERSIAN_YEH)
        .replace(ALEF_MAKSURA, PERSIAN_YEH)
        .replace(ARABIC_KAF, PERSIAN_KEHEH)
        .replace("\r\n", "\n")

    /** Splits normalised text into words without losing ZWNJ compounds. */
    fun words(text: String): List<String> =
        normalizeForComparison(text).split(' ').filter { it.isNotEmpty() }

    /**
     * Persian-aware word count: chains joined by ZWNJ count as one word.
     */
    fun wordCount(text: String): Int = words(text).size

    /** Converts Latin digits to Persian digits for display. */
    fun toPersianDigits(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(if (ch in '0'..'9') PERSIAN_ZERO + (ch - '0') else ch)
        }
    }

    /** Converts Persian/Arabic-Indic digits to Latin digits (for parsing). */
    fun toLatinDigits(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(
                when (ch) {
                    in PERSIAN_ZERO..(PERSIAN_ZERO + 9) -> '0' + (ch - PERSIAN_ZERO)
                    in ARABIC_ZERO..(ARABIC_ZERO + 9) -> '0' + (ch - ARABIC_ZERO)
                    else -> ch
                }
            )
        }
    }
}
