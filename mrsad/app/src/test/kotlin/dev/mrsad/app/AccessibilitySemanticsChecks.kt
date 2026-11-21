package dev.mrsad.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Accessibility audit of the button semantics, run over the real sources.
 *
 * An icon-only control without a name is invisible to a screen reader, and
 * nobody notices it by looking at the screen: the control looks perfectly
 * fine. So the rule has to be checked mechanically instead of by eye.
 *
 * The rule enforced here is the one a source scan can decide on its own: a
 * button whose visible label is empty shows only its glyph, so it must
 * carry an `iconContentDescription`. A button with a visible label needs
 * none — the label is what gets announced.
 *
 * The check runs in a plain JVM unit test, with no emulator and no Compose
 * runtime, so a regression fails the build before anything is installed on
 * a device. The runtime half of the guarantee — that every *tappable* node
 * on screen is reachable by name — is covered by the instrumented
 * `everyInteractiveControlIsNamed` test.
 */
class AccessibilitySemanticsChecks {

    private val sources: List<SourceFile> by lazy {
        val root = locateSourceRoot()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("${File.separator}build${File.separator}") }
            .map { SourceFile(it) }
            .toList()
    }

    /** A source file together with the lines it contains. */
    private class SourceFile(val file: File) {
        val lines: List<String> = file.readText(Charsets.UTF_8).split("\n")
        val text: String = lines.joinToString("\n")

        /** 1-based line number of [snippet], or 0. */
        fun lineOf(snippet: String): Int {
            val trimmed = snippet.trim().take(40)
            val index = lines.indexOfFirst { it.contains(trimmed) }
            return if (index < 0) 0 else index + 1
        }
    }

    /**
     * Finds the module's `src/main/kotlin` directory.
     *
     * Gradle runs a module's unit tests with the module directory as the
     * working directory, but that is not guaranteed, so a couple of known
     * roots are tried before the audit gives up and says so loudly.
     */
    private fun locateSourceRoot(): File {
        val direct = listOf(
            File("src/main/kotlin"),
            File("app/src/main/kotlin"),
            File("../app/src/main/kotlin"),
        ).firstOrNull { it.isDirectory }
        if (direct != null) return direct

        // Walk up from the working directory looking for the module.
        var current: File? = File(".").absoluteFile
        while (current != null) {
            val candidate = File(current, "src/main/kotlin")
            if (candidate.isDirectory) return candidate
            current = current.parentFile
        }
        error(
            "cannot locate src/main/kotlin from ${File(".").absolutePath}; " +
                "the accessibility audit cannot run",
        )
    }

    @Test
    fun theAuditActuallyFoundTheSources() {
        assertTrue(
            "the accessibility audit found no Kotlin sources to check",
            sources.isNotEmpty(),
        )
        assertTrue(
            "the accessibility audit must see the app's own source set",
            sources.any { it.file.path.contains("dev${File.separator}mrsad") },
        )
    }

    @Test
    fun theAuditActuallyFoundTheButtons() {
        // If the parser stopped recognising calls, every other check in
        // this file would pass for the wrong reason.
        val buttons = sources.sumOf { buttonCalls(it).size }
        assertTrue(
            "the accessibility audit found no button calls at all",
            buttons > 10,
        )
    }

    @Test
    fun everyIconOnlyButtonHasAName() {
        val violations = mutableListOf<String>()
        sources.forEach { source ->
            buttonCalls(source).forEach { call ->
                val label = call.visibleLabel()
                // A button with no visible label shows only its icon, so
                // the icon has to carry the name.
                if (label == null || label.isBlank()) {
                    if (call.namedArgument("iconContentDescription") == null) {
                        violations += "${source.file.name}:${source.lineOf(call.snippet)} " +
                            "icon-only button has no iconContentDescription"
                    }
                }
            }
        }
        assertTrue(
            "unlabelled icon-only buttons:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun labelledButtonsDoNotDoubleUpTheirNames() {
        val violations = mutableListOf<String>()
        sources.forEach { source ->
            buttonCalls(source).forEach { call ->
                val label = call.visibleLabel()
                // A visible label is announced on its own; adding a second
                // description would make a screen reader say it twice.
                if (label != null && label.isNotBlank()) {
                    if (call.namedArgument("iconContentDescription") != null) {
                        violations += "${source.file.name}:${source.lineOf(call.snippet)} " +
                            "button has a visible label and an iconContentDescription"
                    }
                }
            }
        }
        assertTrue(
            "buttons that announce their label twice:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    // ---- parsing helpers ---------------------------------------------------

    /** A call to a button composable with its arguments. */
    private class Call(val snippet: String, val args: String) {
        /** The value of a named argument, or null when it is absent. */
        fun namedArgument(key: String): String? {
            val marker = "$key ="
            var searchFrom = 0
            while (true) {
                val start = args.indexOf(marker, searchFrom)
                if (start < 0) return null
                // The key has to be a whole word, so `text` never matches
                // `iconContentDescription` and friends.
                val before = if (start == 0) ' ' else args[start - 1]
                if (!before.isLetterOrDigit() && before != '_') {
                    var i = start + marker.length
                    while (i < args.length && args[i].isWhitespace()) i++
                    if (i >= args.length) return null
                    return when (args[i]) {
                        '"' -> {
                            val end = args.indexOf('"', i + 1)
                            if (end < 0) null else args.substring(i + 1, end)
                        }
                        else -> {
                            val end = args.indexOfFirst { ch -> ch == ',' || ch == ')' }
                            args.substring(i, if (end < 0) args.length else end).trim()
                        }
                    }
                }
                searchFrom = start + marker.length
            }
        }

        /** The first positional argument of the call. */
        fun firstPositionalArgument(): String? {
            var i = 0
            var depth = 0
            var inString = false
            val buffer = StringBuilder()
            while (i < args.length) {
                val ch = args[i]
                when {
                    ch == '"' && (i == 0 || args[i - 1] != '\\') -> {
                        inString = !inString
                        buffer.append(ch)
                    }
                    inString -> buffer.append(ch)
                    ch == '(' || ch == '[' || ch == '{' -> {
                        depth++
                        buffer.append(ch)
                    }
                    ch == ')' || ch == ']' || ch == '}' -> {
                        if (depth == 0) break
                        depth--
                        buffer.append(ch)
                    }
                    ch == ',' && depth == 0 -> break
                    else -> buffer.append(ch)
                }
                i++
            }
            val value = buffer.toString().trim()
            return when {
                value.isEmpty() -> null
                value.startsWith("\"") -> value.trim('"')
                else -> value
            }
        }

        /**
         * The label the user actually sees on the button.
         *
         * The label is either the named `text` argument or the first
         * positional one, because both spellings are used in the app.
         */
        fun visibleLabel(): String? = namedArgument("text") ?: firstPositionalArgument()
    }

    /** Every call to one of the button composables in [source]. */
    private fun buttonCalls(source: SourceFile): List<Call> {
        val out = mutableListOf<Call>()
        listOf("MrsadSecondaryButton", "MrsadPrimaryButton").forEach { name ->
            var index = source.text.indexOf(name)
            while (index >= 0) {
                val lineStart = source.text.lastIndexOf('\n', index).let {
                    if (it < 0) 0 else it + 1
                }
                val prefix = source.text.substring(lineStart, index)
                val isImport = prefix.trimStart().startsWith("import ")
                val isDeclaration = source.text
                    .substring(maxOf(0, index - 8), index)
                    .contains("fun ")
                val open = source.text.indexOf('(', index)
                if (!isImport && !isDeclaration && open > 0) {
                    val close = matchingParen(source.text, open)
                    if (close > open) {
                        out += Call(
                            snippet = source.text.substring(
                                index,
                                minOf(index + 40, source.text.length),
                            ),
                            args = source.text.substring(open + 1, close),
                        )
                    }
                }
                index = source.text.indexOf(name, index + name.length)
            }
        }
        return out
    }

    /** The index of the parenthesis that closes the one at [open]. */
    private fun matchingParen(text: String, open: Int): Int {
        var depth = 0
        var i = open
        var inString = false
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && (i == 0 || text[i - 1] != '\\') -> inString = !inString
                inString -> Unit
                ch == '(' -> depth++
                ch == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }
}
