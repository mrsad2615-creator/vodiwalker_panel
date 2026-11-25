package com.neurox.common.json

/**
 * Minimal, strict, dependency-free JSON tree.
 *
 * WHY THIS EXISTS (decision DEC-0002):
 * NEUROX persists small, known documents (session checkpoints, exercise
 * configurations, settings, backup payloads). A reflective serialization library
 * would add a compiler plugin, runtime reflection and a probe for R8. A small,
 * fully tested codec gives deterministic output, exact error positions and zero
 * reflection, which also keeps the APK smaller.
 *
 * GUARANTEES
 * - Parsing is strict: trailing content, unquoted keys, NaN and control
 *   characters in strings are rejected with [JsonParseException].
 * - Nesting depth is limited to [MAX_DEPTH], so a hostile backup file cannot
 *   exhaust the stack.
 * - Writing escapes every character that JSON requires, and never emits invalid
 *   numbers.
 */
/**
 * Marks a data class as having hand-written, deterministic JSON mapping.
 *
 * Hand-written mappers are mandatory here (decision DEC-0002): no reflection, no
 * compiler plugin, and a broken field is a compile error instead of a runtime
 * `MissingFieldException`.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class JsonSerializable

sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

class JsonParseException(message: String, val position: Int) : Exception("$message at position $position")

/** Reader helpers with explicit defaults; a missing key returns the default. */
fun JsonValue?.asObj(): Map<String, JsonValue> = (this as? JsonValue.Obj)?.fields ?: emptyMap()
fun JsonValue?.asArr(): List<JsonValue> = (this as? JsonValue.Arr)?.items ?: emptyList()
fun JsonValue?.asStringOrNull(): String? = (this as? JsonValue.Str)?.value
fun JsonValue?.asDoubleOrNull(): Double? = (this as? JsonValue.Num)?.value
fun JsonValue?.asBoolOrNull(): Boolean? = (this as? JsonValue.Bool)?.value
fun JsonValue?.asLongOrDefault(fallback: Long): Long = asDoubleOrNull()?.toLong() ?: fallback
fun JsonValue?.asIntOrDefault(fallback: Int): Int = asDoubleOrNull()?.toInt() ?: fallback
fun JsonValue?.asDoubleOrDefault(fallback: Double): Double = asDoubleOrNull() ?: fallback
fun JsonValue?.asStringOrDefault(fallback: String): String = asStringOrNull() ?: fallback
fun JsonValue?.asBoolOrDefault(fallback: Boolean): Boolean = asBoolOrNull() ?: fallback

/** Requires a field; throws [JsonParseException] when absent or of the wrong type. */
fun Map<String, JsonValue>.required(key: String): JsonValue =
    this[key] ?: throw JsonParseException("missing field '$key'", -1)

fun Map<String, JsonValue>.requiredString(key: String): String =
    required(key).asStringOrNull() ?: throw JsonParseException("field '$key' must be a string", -1)

fun Map<String, JsonValue>.requiredInt(key: String): Int =
    required(key).asIntOrDefault(Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        ?: throw JsonParseException("field '$key' must be a number", -1)

fun Map<String, JsonValue>.requiredLong(key: String): Long =
    required(key).asDoubleOrNull()?.toLong() ?: throw JsonParseException("field '$key' must be a number", -1)

fun Map<String, JsonValue>.requiredDouble(key: String): Double =
    required(key).asDoubleOrNull() ?: throw JsonParseException("field '$key' must be a number", -1)

fun Map<String, JsonValue>.requiredBool(key: String): Boolean =
    required(key).asBoolOrNull() ?: throw JsonParseException("field '$key' must be a boolean", -1)

fun Map<String, JsonValue>.requiredArray(key: String): List<JsonValue> =
    (required(key) as? JsonValue.Arr)?.items
        ?: throw JsonParseException("field '$key' must be an array", -1)

/** Small builder to keep mapper code readable. */
class JsonObjectBuilder {
    private val fields = LinkedHashMap<String, JsonValue>()

    infix fun String.string(value: String) { fields[this] = JsonValue.Str(value) }
    infix fun String.number(value: Double) {
        require(value.isFinite()) { "JSON cannot store a non-finite number" }
        fields[this] = JsonValue.Num(value)
    }
    infix fun String.integer(value: Int) { fields[this] = JsonValue.Num(value.toDouble()) }
    infix fun String.long(value: Long) { fields[this] = JsonValue.Num(value.toDouble()) }
    infix fun String.bool(value: Boolean) { fields[this] = JsonValue.Bool(value) }
    infix fun String.value(value: JsonValue) { fields[this] = value }
    infix fun String.strings(values: Collection<String>) {
        fields[this] = JsonValue.Arr(values.map { JsonValue.Str(it) })
    }
    infix fun String.integers(values: Collection<Int>) {
        fields[this] = JsonValue.Arr(values.map { JsonValue.Num(it.toDouble()) })
    }
    infix fun String.array(values: List<JsonValue>) { fields[this] = JsonValue.Arr(values) }
    fun opt(key: String, value: String?) { if (value != null) fields[key] = JsonValue.Str(value) }
    fun build(): JsonValue.Obj = JsonValue.Obj(fields)
}

fun jsonObject(block: JsonObjectBuilder.() -> Unit): JsonValue.Obj =
    JsonObjectBuilder().apply(block).build()

fun jsonArray(values: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(values)

/** Serializes a tree to compact JSON text. */
object JsonWriter {
    fun write(value: JsonValue): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonValue.Obj -> {
                out.append('{')
                var first = true
                for ((k, v) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k)
                    out.append(':')
                    append(out, v)
                }
                out.append('}')
            }
            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            is JsonValue.Str -> writeString(out, value.value)
            is JsonValue.Num -> out.append(numberToString(value.value))
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            JsonValue.Null -> out.append("null")
        }
    }

    /** Writes integral doubles without a fractional part, which keeps files small. */
    fun numberToString(value: Double): String {
        require(value.isFinite()) { "JSON cannot store a non-finite number" }
        return if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private fun writeString(out: StringBuilder, text: String) {
        out.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch < ' ') {
                    out.append("\\u")
                    out.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }
}

/** Strict recursive-descent parser. */
object JsonParser {
    const val MAX_DEPTH = 32

    fun parse(text: String): JsonValue {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue(0)
        reader.skipWhitespace()
        if (!reader.atEnd()) throw JsonParseException("trailing content", reader.position)
        return value
    }

    private class Reader(private val text: String) {
        var position = 0

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (!atEnd()) {
                when (text[position]) {
                    ' ', '\t', '\n', '\r' -> position += 1
                    else -> return
                }
            }
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) throw JsonParseException("nesting too deep", position)
            if (atEnd()) throw JsonParseException("unexpected end of input", position)
            return when (val ch = text[position]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> readLiteral("true", JsonValue.Bool(true))
                'f' -> readLiteral("false", JsonValue.Bool(false))
                'n' -> readLiteral("null", JsonValue.Null)
                else -> if (ch == '-' || ch in '0'..'9') readNumber()
                else throw JsonParseException("unexpected character '$ch'", position)
            }
        }

        private fun readLiteral(literal: String, value: JsonValue): JsonValue {
            if (!text.startsWith(literal, position)) {
                throw JsonParseException("invalid literal", position)
            }
            position += literal.length
            return value
        }

        private fun readObject(depth: Int): JsonValue {
            position += 1 // '{'
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (!atEnd() && text[position] == '}') {
                position += 1
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[position] != '"') {
                    throw JsonParseException("object key must be a string", position)
                }
                val key = readString()
                skipWhitespace()
                if (atEnd() || text[position] != ':') {
                    throw JsonParseException("expected ':'", position)
                }
                position += 1
                skipWhitespace()
                val value = readValue(depth + 1)
                if (fields.containsKey(key)) {
                    throw JsonParseException("duplicate key '$key'", position)
                }
                fields[key] = value
                skipWhitespace()
                if (atEnd()) throw JsonParseException("unterminated object", position)
                when (text[position]) {
                    ',' -> position += 1
                    '}' -> {
                        position += 1
                        return JsonValue.Obj(fields)
                    }
                    else -> throw JsonParseException("expected ',' or '}'", position)
                }
            }
        }

        private fun readArray(depth: Int): JsonValue {
            position += 1 // '['
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (!atEnd() && text[position] == ']') {
                position += 1
                return JsonValue.Arr(items)
            }
            while (true) {
                skipWhitespace()
                items += readValue(depth + 1)
                skipWhitespace()
                if (atEnd()) throw JsonParseException("unterminated array", position)
                when (text[position]) {
                    ',' -> position += 1
                    ']' -> {
                        position += 1
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonParseException("expected ',' or ']'", position)
                }
            }
        }

        private fun readString(): String {
            position += 1 // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonParseException("unterminated string", position)
                val ch = text[position]
                when {
                    ch == '"' -> {
                        position += 1
                        return out.toString()
                    }
                    ch == '\\' -> {
                        position += 1
                        if (atEnd()) throw JsonParseException("unterminated escape", position)
                        when (val esc = text[position]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (position + 4 >= text.length) {
                                    throw JsonParseException("invalid unicode escape", position)
                                }
                                val hex = text.substring(position + 1, position + 5)
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonParseException("invalid unicode escape", position)
                                out.append(code.toChar())
                                position += 4
                            }
                            else -> throw JsonParseException("invalid escape '\\$esc'", position)
                        }
                        position += 1
                    }
                    ch < ' ' -> throw JsonParseException("control character in string", position)
                    else -> {
                        out.append(ch)
                        position += 1
                    }
                }
            }
        }

        private fun readNumber(): JsonValue {
            val start = position
            if (text[position] == '-') position += 1
            if (atEnd()) throw JsonParseException("invalid number", start)
            if (text[position] == '0') {
                position += 1
            } else if (text[position] in '1'..'9') {
                while (!atEnd() && text[position] in '0'..'9') position += 1
            } else {
                throw JsonParseException("invalid number", start)
            }
            if (!atEnd() && text[position] == '.') {
                position += 1
                if (atEnd() || text[position] !in '0'..'9') throw JsonParseException("invalid fraction", position)
                while (!atEnd() && text[position] in '0'..'9') position += 1
            }
            if (!atEnd() && (text[position] == 'e' || text[position] == 'E')) {
                position += 1
                if (!atEnd() && (text[position] == '+' || text[position] == '-')) position += 1
                if (atEnd() || text[position] !in '0'..'9') throw JsonParseException("invalid exponent", position)
                while (!atEnd() && text[position] in '0'..'9') position += 1
            }
            val raw = text.substring(start, position)
            val parsed = raw.toDoubleOrNull() ?: throw JsonParseException("invalid number '$raw'", start)
            if (!parsed.isFinite()) throw JsonParseException("number out of range", start)
            return JsonValue.Num(parsed)
        }
    }
}
