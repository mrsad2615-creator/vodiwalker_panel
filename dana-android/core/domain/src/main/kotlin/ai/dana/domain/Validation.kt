package ai.dana.domain

enum class InputIssue {
    EMPTY_NOTE,
    NOTE_TOO_LONG,
    INVALID_CONVERSATION_TITLE,
    INVALID_MESSAGE,
    INVALID_MEMORY,
    INVALID_PROVIDER_NAME,
    INVALID_MODEL,
    INVALID_BASE_URL,
    INVALID_API_KEY,
    CREDENTIAL_DESTINATION_CHANGED,
    RECORD_NOT_FOUND,
}

class InputRejected(val issue: InputIssue) : IllegalArgumentException(issue.name)

internal fun reject(issue: InputIssue): Nothing = throw InputRejected(issue)

object LocalText {
    const val NOTE_LIMIT = 10_000
    const val TITLE_LIMIT = 160
    const val MESSAGE_LIMIT = 65_536
    const val MEMORY_LIMIT = 2_000

    fun note(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) reject(InputIssue.EMPTY_NOTE)
        if (text.length > NOTE_LIMIT) reject(InputIssue.NOTE_TOO_LONG)
        return text
    }

    fun title(raw: String) = bounded(raw, TITLE_LIMIT, InputIssue.INVALID_CONVERSATION_TITLE)
    fun message(raw: String) = bounded(raw, MESSAGE_LIMIT, InputIssue.INVALID_MESSAGE)
    fun memory(raw: String) = bounded(raw, MEMORY_LIMIT, InputIssue.INVALID_MEMORY)

    private fun bounded(raw: String, max: Int, issue: InputIssue): String = raw.trim().also {
        if (it.isEmpty() || it.length > max) reject(issue)
    }
}
