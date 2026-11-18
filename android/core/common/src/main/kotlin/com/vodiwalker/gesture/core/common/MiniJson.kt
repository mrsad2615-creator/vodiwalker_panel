package com.vodiwalker.gesture.core.common

/**
 * Minimal, dependency-free JSON document model used for Room persistence payloads and the
 * zero-network execution journal.
 *
 * Design constraints:
 * - **Pure Kotlin**: no external serializer (keeps `:core:*` free of third-party runtime bloat and
 *   removes a supply-chain surface from a security-sensitive automation app).
 * - **Deterministic output**: key insertion order preserved, floats emitted with the shortest
 *   round-trip representation, so payload hashing/telemetry comparison is stable.
 * - **Hardened parsing**: depth-limited recursion, bounded input length, and explicit rejection of
 *   malformed escapes — untrusted vault contents can never cause `StackOverflowError` or ANR.
 */
sealed interface JsonNode {

    /** JSON object with insertion-ordered [entries]. */
    data class Obj(val entries: Map<String, JsonNode> = emptyMap()) : JsonNode

    /** JSON array with ordered [items]. */
    data class Arr(val items: List<JsonNode> = emptyList()) : JsonNode

    /**
     * JSON number.
     *
     * @property value Numeric value as a double.
     * @property preferFloat When true the writer emits the shortest `Float` round-trip form so
     *   pixel coordinates stay compact and lossless across encode/decode cycles.
     */
    data class Num(val value: Double, val preferFloat: Boolean = false) : JsonNode

    /** JSON boolean. */
    data class Bool(val value: Boolean) : JsonNode

    /** JSON string. */
    data class Str(val value: String) : JsonNode

    /** JSON null. */
    data object Null : JsonNode
}

/** Typed string accessor returning `null` for missing keys or non-string nodes. */
fun JsonNode?.string(path: String): String? = (this?.field(path) as? JsonNode.Str)?.value

/** Typed float accessor with coercion for integral numbers. */
fun JsonNode?.float(path: String): Float? = when (val node = this?.field(path)) {
    is JsonNode.Num -> node.value.toFloat()
    else -> null
}

/** Typed long accessor with truncation for fractional numbers. */
fun JsonNode?.long(path: String): Long? = when (val node = this?.field(path)) {
    is JsonNode.Num -> node.value.toLong()
    else -> null
}

/** Typed int accessor with truncation for fractional numbers. */
fun JsonNode?.int(path: String): Int? = when (val node = this?.field(path)) {
    is JsonNode.Num -> node.value.toInt()
    else -> null
}

/** Typed boolean accessor. */
fun JsonNode?.bool(path: String): Boolean? = (this?.field(path) as? JsonNode.Bool)?.value

/** Typed array accessor. */
fun JsonNode?.array(path: String): List<JsonNode>? = (this?.field(path) as? JsonNode.Arr)?.items

/** Typed object accessor. */
fun JsonNode?.objectValue(path: String): JsonNode.Obj? = this?.field(path) as? JsonNode.Obj

/** Object member lookup supporting nested `a.b.c` paths. */
fun JsonNode.field(path: String): JsonNode? {
    if (this !is JsonNode.Obj) return null
    var cursor: JsonNode? = this
    for (segment in path.split('.')) {
        cursor = (cursor as? JsonNode.Obj)?.entries?.get(segment) ?: return null
    }
    return cursor
}

/** Builds a [JsonNode.Obj] from key/value [pairs] preserving declaration order. */
fun jsonObjectOf(vararg pairs: Pair<String, JsonNode>): JsonNode.Obj =
    JsonNode.Obj(linkedMapOf(*pairs))

/** Builds a [JsonNode.Arr] from [items]. */
fun jsonArrayOf(vararg items: JsonNode): JsonNode.Arr = JsonNode.Arr(items.toList())

/** Convenience factories keeping call sites free of constructor noise. */
object Json {
    /**
     * String node, or JSON null when [value] is null.
     */
    fun str(value: String?): JsonNode = if (value == null) JsonNode.Null else JsonNode.Str(value)
    /**
     * Integer number node.
     */
    fun int(value: Int): JsonNode = JsonNode.Num(value.toDouble())
    /**
     * Long number node.
     */
    fun long(value: Long): JsonNode = JsonNode.Num(value.toDouble())
    /**
     * Float number node preserving the shortest round-trip form.
     */
    fun float(value: Float): JsonNode = JsonNode.Num(value.toDouble(), preferFloat = true)
    /**
     * Boolean node.
     */
    fun bool(value: Boolean): JsonNode = JsonNode.Bool(value)
    /**
     * Array node.
     */
    fun arr(items: List<JsonNode>): JsonNode = JsonNode.Arr(items)
    /**
     * Object node.
     */
    fun obj(entries: Map<String, JsonNode>): JsonNode = JsonNode.Obj(entries)
    /**
     * Array of strings, e.g. package-name sets.
     */
    fun strs(values: Collection<String>): JsonNode = JsonNode.Arr(values.map { JsonNode.Str(it) })
}

/**
 * Recursive-descent JSON reader/writer with strict resource bounds.
 *
 * Errors are returned as [CodecError] values instead of exceptions so callers on the
 * AccessibilityService binder thread can degrade gracefully (fall back to a default template)
 * rather than crash the user's session.
 */
object JsonCodec {

    /** Maximum accepted payload length in characters. */
    const val MAX_INPUT_LENGTH: Int = 1_048_576

    /** Maximum structural nesting depth accepted by the parser. */
    const val MAX_DEPTH: Int = 32

    /** Structured decode failure with the offending character offset. */
    data class CodecError(val message: String, val offset: Int)

    /**
     * Decodes [raw] into a [JsonNode], returning `null` when the document is malformed.
     */
    fun decodeOrNull(raw: String): JsonNode? =
        when (val result = decode(raw)) {
            is AppResult.Ok -> result.value
            is AppResult.Err -> null
        }

    /** Decodes [raw] into a [JsonNode] wrapped in [AppResult]. */
    fun decode(raw: String): AppResult<JsonNode, CodecError> {
        if (raw.length > MAX_INPUT_LENGTH) {
            return AppResult.Err(CodecError("payload exceeds $MAX_INPUT_LENGTH chars", 0))
        }
        return try {
            val parser = Parser(raw)
            val value = parser.parseValue(depth = 0)
            parser.skipWhitespace()
            if (!parser.exhausted()) {
                AppResult.Err(CodecError("trailing characters", parser.position))
            } else {
                AppResult.Ok(value)
            }
        } catch (failure: ParseFailure) {
            AppResult.Err(CodecError(failure.message ?: "malformed json", failure.offset))
        }
    }

    /** Serializes [node] to a compact, deterministic JSON string. */
    fun encode(node: JsonNode): String = StringBuilder(128).also { append(it, node) }.toString()

    private fun append(out: StringBuilder, node: JsonNode) {
        when (node) {
            is JsonNode.Obj -> {
                out.append('{')
                var first = true
                for ((key, value) in node.entries) {
                    if (!first) out.append(',')
                    first = false
                    appendString(out, key)
                    out.append(':')
                    append(out, value)
                }
                out.append('}')
            }
            is JsonNode.Arr -> {
                out.append('[')
                node.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            is JsonNode.Num -> out.append(formatNumber(node))
            is JsonNode.Bool -> out.append(if (node.value) "true" else "false")
            is JsonNode.Str -> appendString(out, node.value)
            JsonNode.Null -> out.append("null")
        }
    }

    private fun formatNumber(node: JsonNode.Num): String {
        if (!node.value.isFinite()) return "0"
        if (node.preferFloat) {
            val asFloat = node.value.toFloat()
            if (asFloat.isFinite()) return asFloat.toString()
        }
        val asLong = node.value.toLong()
        return if (node.value == asLong.toDouble() && kotlin.math.abs(node.value) < 9.007_199_254_740_992E15) {
            asLong.toString()
        } else {
            node.value.toString()
        }
    }

    private fun appendString(out: StringBuilder, value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }

    private class ParseFailure(message: String, val offset: Int) : Exception(message)

    private class Parser(private val input: String) {
        var position: Int = 0
            private set

        /**
         * True once the cursor has consumed the whole input.
         */
        fun exhausted(): Boolean = position >= input.length

        /**
         * Advances past JSON insignificant whitespace.
         */
        fun skipWhitespace() {
            while (position < input.length && input[position].isJsonWhitespace()) position++
        }

        /**
         * Parses any JSON value, enforcing [JsonCodec.MAX_DEPTH].
         */
        fun parseValue(depth: Int): JsonNode {
            if (depth > MAX_DEPTH) throw ParseFailure("nesting exceeds $MAX_DEPTH", position)
            skipWhitespace()
            if (exhausted()) throw ParseFailure("unexpected end of input", position)
            return when (val char = input[position]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> JsonNode.Str(parseString())
                't' -> parseLiteral("true", JsonNode.Bool(true))
                'f' -> parseLiteral("false", JsonNode.Bool(false))
                'n' -> parseLiteral("null", JsonNode.Null)
                else -> if (char == '-' || char.isDigit()) parseNumber() else {
                    throw ParseFailure("unexpected character '$char'", position)
                }
            }
        }

        private fun parseObject(depth: Int): JsonNode.Obj {
            position++ // '{'
            val entries = LinkedHashMap<String, JsonNode>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return JsonNode.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw ParseFailure("expected object key", position)
                val key = parseString()
                skipWhitespace()
                if (peek() != ':') throw ParseFailure("expected ':'", position)
                position++
                entries[key] = parseValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return JsonNode.Obj(entries)
                    }
                    else -> throw ParseFailure("expected ',' or '}'", position)
                }
            }
        }

        private fun parseArray(depth: Int): JsonNode.Arr {
            position++ // '['
            val items = ArrayList<JsonNode>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return JsonNode.Arr(items)
            }
            while (true) {
                items.add(parseValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return JsonNode.Arr(items)
                    }
                    else -> throw ParseFailure("expected ',' or ']'", position)
                }
            }
        }

        private fun parseString(): String {
            position++ // opening quote
            val buffer = StringBuilder()
            while (true) {
                if (exhausted()) throw ParseFailure("unterminated string", position)
                when (val char = input[position]) {
                    '"' -> {
                        position++
                        return buffer.toString()
                    }
                    '\\' -> {
                        position++
                        if (exhausted()) throw ParseFailure("unterminated escape", position)
                        when (val escape = input[position]) {
                            '"' -> buffer.append('"')
                            '\\' -> buffer.append('\\')
                            '/' -> buffer.append('/')
                            'b' -> buffer.append('\b')
                            'f' -> buffer.append('\u000C')
                            'n' -> buffer.append('\n')
                            'r' -> buffer.append('\r')
                            't' -> buffer.append('\t')
                            'u' -> {
                                if (position + 4 >= input.length) {
                                    throw ParseFailure("truncated unicode escape", position)
                                }
                                val hex = input.substring(position + 1, position + 5)
                                val code = hex.toIntOrNull(radix = 16)
                                    ?: throw ParseFailure("invalid unicode escape", position)
                                buffer.append(code.toChar())
                                position += 4
                            }
                            else -> throw ParseFailure("invalid escape '\\$escape'", position)
                        }
                        position++
                    }
                    else -> {
                        buffer.append(char)
                        position++
                    }
                }
            }
        }

        private fun parseNumber(): JsonNode.Num {
            val start = position
            if (peek() == '-') position++
            while (!exhausted() && (input[position].isDigit() || input[position] in ".eE+-")) {
                position++
            }
            val token = input.substring(start, position)
            val value = token.toDoubleOrNull()
                ?: throw ParseFailure("invalid number '$token'", start)
            if (!value.isFinite()) throw ParseFailure("non-finite number '$token'", start)
            // Only tokens with an explicit fractional part round-trip through the Float formatter;
            // integral literals must re-encode identically ("3" → 3 → "3", never "3.0").
            val preferFloat = token.contains('.')
            return JsonNode.Num(value, preferFloat)
        }

        private fun parseLiteral(token: String, node: JsonNode): JsonNode {
            if (!input.startsWith(token, position)) {
                throw ParseFailure("expected '$token'", position)
            }
            position += token.length
            return node
        }

        private fun peek(): Char? = if (exhausted()) null else input[position]
    }

    private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
}
