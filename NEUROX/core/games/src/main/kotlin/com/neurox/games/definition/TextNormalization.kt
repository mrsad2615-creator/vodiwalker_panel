package com.neurox.games.definition

/**
 * Unicode and script helpers for the READ and TYPE engines.
 *
 * WHY: Persian text uses several code points that look identical but differ in
 * memory: Arabic Yeh (U+064A) vs Farsi Yeh (U+06CC), Arabic Kaf (U+0643) vs Keheh
 * (U+06A9), and the ZWNJ (U+200C) that joins or separates letters. Comparing raw
 * strings would report false typing errors and would split words incorrectly.
 *
 * The normalizer chooses the Farsi forms, which is what Persian keyboards emit.
 * English text is unaffected.
 */
object TextNormalizer {

    const val ZWNJ = '\u200C'
    const val ZWJ = '\u200D'
    const val ARABIC_YEH = '\u064A'
    const val FARSI_YEH = '\u06CC'
    const val ARABIC_KAF = '\u0643'
    const val KEHEH = '\u06A9'
    const val ARABIC_ALEF = '\u0627'
    const val ALEF_WITH_HAMZA = '\u0623'
    const val ALEF_WITH_MADDA = '\u0622'
    const val TATWEEL = '\u0640'
    const val ARABIC_INDIC_ZERO = '\u0660'
    const val PERSIAN_EXT_ZERO = '\u06F0'

    /** Persian digits, indexed 0..9. */
    val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** Arabic-Indic digits, indexed 0..9. */
    val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /**
     * Normalizes Persian/Arabic text for comparison.
     *
     * Rules, applied in order:
     * 1. Convert Arabic Yeh and Alef Maksura to Farsi Yeh.
     * 2. Convert Arabic Kaf to Keheh.
     * 3. Convert Arabic-Indic digits to the Persian digit range, then to ASCII.
     * 4. Remove Tatweel (a decorative extension that carries no meaning).
     * 5. Collapse repeated whitespace and ZWJ; keep ZWNJ because it changes meaning.
     * 6. Trim.
     */
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        var previousSpace = false
        for (raw in text) {
            var ch = raw
            when (ch) {
                ARABIC_YEH, '\u0649' -> ch = FARSI_YEH
                ARABIC_KAF -> ch = KEHEH
                TATWEEL -> continue
                ZWJ -> continue
            }
            val asciiDigit = when (ch) {
                in PERSIAN_DIGITS -> '0' + PERSIAN_DIGITS.indexOf(ch)
                in ARABIC_DIGITS -> '0' + ARABIC_DIGITS.indexOf(ch)
                else -> null
            }
            if (asciiDigit != null) ch = asciiDigit
            val isSpace = ch == ' ' || ch == '\t' || ch == '\n' || ch == '\u00A0'
            if (isSpace) {
                if (!previousSpace) builder.append(' ')
                previousSpace = true
            } else {
                builder.append(ch)
                previousSpace = false
            }
        }
        return builder.toString().trim()
    }

    /**
     * Per-character equality used by the TYPE engine.
     *
     * Diacritics (harakat) are ignored: a Persian keyboard does not emit them, so
     * requiring them would be unfair. ZWNJ is compared exactly.
     */
    fun charactersEqual(expected: Char, typed: Char): Boolean {
        if (expected == typed) return true
        if (isDiacritic(expected) || isDiacritic(typed)) return true
        val a = normalize(expected.toString())
        val b = normalize(typed.toString())
        return a.isNotEmpty() && a == b
    }

    fun isDiacritic(ch: Char): Boolean = ch in '\u064B'..'\u0652' || ch == '\u0670'

    /** True when the string contains Arabic-script letters. */
    fun containsPersian(text: String): Boolean =
        text.any { it in '\u0600'..'\u06FF' }

    /** True when the string contains Latin letters. */
    fun containsLatin(text: String): Boolean =
        text.any { it in 'a'..'z' || it in 'A'..'Z' }

    /**
     * Bidirectional class hint for the presentation layer.
     *
     * Android handles bidi automatically in `Text`; this helper exists so a UI can
     * decide alignment per paragraph, and so tests can assert the expectation.
     */
    fun baseDirection(text: String): TextDirection {
        val firstStrong = text.firstOrNull { containsPersian(it.toString()) || containsLatin(it.toString()) }
            ?: return TextDirection.NEUTRAL
        return if (containsPersian(firstStrong.toString())) TextDirection.RTL else TextDirection.LTR
    }

    /** Character count used by the single WPM definition (five characters = one word). */
    fun measurableCharacters(text: String): Int = normalize(text).count { !it.isWhitespace() }

    /** Word count for content where words are the natural unit. */
    fun words(text: String): Int = normalize(text).split(' ').count { it.isNotBlank() }

    /**
     * Splits text into words without breaking on ZWNJ.
     *
     * A Persian word such as «می‌روم» must stay one token.
     */
    fun splitWords(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotBlank() }

    /** Splits text into units for RSVP and phrase reading. */
    fun chunk(text: String, size: Int): List<String> {
        require(size >= 1) { "chunk size must be >= 1" }
        val words = splitWords(text)
        if (words.isEmpty()) return emptyList()
        return words.chunked(size).map { it.joinToString(" ") }
    }

    /**
     * Optimal recognition point for the RSVP reader.
     *
     * Returns an index inside [word] where the pivot character sits. Short words
     * pivot near the start; long words pivot near one third of their length.
     */
    fun pivotIndex(word: String): Int {
        if (word.isEmpty()) return 0
        val visible = word.filter { !isDiacritic(it) }
        return (visible.length / 3).coerceIn(0, (visible.length - 1).coerceAtLeast(0))
    }
}

enum class TextDirection { LTR, RTL, NEUTRAL }

/**
 * Counting rules shared by READ and TYPE, so both modules report comparable units.
 */
object TextMetrics {

    /** Correct characters among the typed text, compared position by position. */
    fun correctCharacters(expected: String, typed: String): Int {
        val e = expected
        val t = typed
        var correct = 0
        val limit = minOf(e.length, t.length)
        for (i in 0 until limit) {
            if (TextNormalizer.charactersEqual(e[i], t[i])) correct += 1
        }
        return correct
    }

    /** Incorrect characters that were typed in place of an expected character. */
    fun incorrectCharacters(expected: String, typed: String): Int {
        val limit = minOf(expected.length, typed.length)
        var wrong = 0
        for (i in 0 until limit) {
            if (!TextNormalizer.charactersEqual(expected[i], typed[i])) wrong += 1
        }
        return wrong
    }

    /**
     * Error ratio in 0..1, denominator is the number of typed characters or the
     * expected length, whichever is larger. Never divides by zero.
     */
    fun errorRatio(expected: String, typed: String): Double {
        val denominator = maxOf(expected.length, typed.length)
        if (denominator == 0) return 0.0
        return (incorrectCharacters(expected, typed).toDouble() / denominator.toDouble()).coerceIn(0.0, 1.0)
    }

    /**
     * Weak keys: for every expected character, the number of times it was typed
     * wrongly. Used by the WEAK KEYS exercise and the analytics screen.
     */
    fun weakKeys(expected: String, typed: String): Map<Char, Int> {
        val out = LinkedHashMap<Char, Int>()
        val limit = minOf(expected.length, typed.length)
        for (i in 0 until limit) {
            if (!TextNormalizer.charactersEqual(expected[i], typed[i])) {
                out[expected[i]] = (out[expected[i]] ?: 0) + 1
            }
        }
        return out
    }

    /** Accuracy in 0..1 for a typed text. */
    fun accuracy(expected: String, typed: String): Double {
        if (typed.isEmpty()) return 0.0
        val correct = correctCharacters(expected, typed)
        return (correct.toDouble() / typed.length.toDouble()).coerceIn(0.0, 1.0)
    }
}
