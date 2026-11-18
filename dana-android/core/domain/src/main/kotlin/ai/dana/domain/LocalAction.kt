package ai.dana.domain


/** Only these validated types can be shown for approval. No model output becomes an Intent. */
sealed interface LocalAction {
    data class Note(val text: String) : LocalAction
    data class Timer(val seconds: Int) : LocalAction
    data class Alarm(val hour: Int, val minute: Int) : LocalAction
    data object Wifi : LocalAction
    data object Bluetooth : LocalAction
    data object Sound : LocalAction
    data object Clock : LocalAction
    data class CalendarDraft(val title: String) : LocalAction
    data class SmsDraft(val number: String, val text: String) : LocalAction
    data class Share(val text: String) : LocalAction
}

object LocalCommands {
    private val timer = Regex("(?:تایمر|timer)\\s+(\\d{1,5})\\s*(ثانیه|دقیقه|ساعت|s|m|h)")
    private val alarm = Regex("(?:آلارم|زنگ|alarm)\\s+(\\d{1,2})[:٫](\\d{2})")
    private val note = Regex("(?:یادداشت|note)\\s+(.+)", RegexOption.DOT_MATCHES_ALL)
    private val calendar = Regex("(?:رویداد|event)\\s+(.+)")
    private val sms = Regex("(?:پیامک|sms)\\s+(\\+?[0-9۰-۹٠-٩]{5,15})\\s+(.+)", RegexOption.DOT_MATCHES_ALL)
    private val share = Regex("(?:اشتراک|share)\\s+(.+)", RegexOption.DOT_MATCHES_ALL)

    fun parse(raw: String): LocalAction? {
        val text = raw.trim()
        if (text.length !in 1..LocalText.NOTE_LIMIT) return null
        val normalized = normalize(text)
        when (normalized) {
            "وای فای", "وای‌فای", "wifi" -> return LocalAction.Wifi
            "بلوتوث", "bluetooth" -> return LocalAction.Bluetooth
            "تنظیمات صدا", "sound" -> return LocalAction.Sound
            "باز کردن ساعت", "clock" -> return LocalAction.Clock
        }
        timer.matchEntire(normalized)?.let { match ->
            val amount = match.groupValues[1].toInt()
            val factor = when (match.groupValues[2]) { "ثانیه", "s" -> 1; "دقیقه", "m" -> 60; else -> 3_600 }
            val seconds = amount * factor
            return if (seconds in 1..86_400) LocalAction.Timer(seconds) else null
        }
        alarm.matchEntire(normalized)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            return if (hour in 0..23 && minute in 0..59) LocalAction.Alarm(hour, minute) else null
        }
        fun original(value: MatchResult, group: Int) = text.substring(checkNotNull(value.groups[group]).range).trim()
        note.matchEntire(normalized)?.let { return LocalAction.Note(original(it, 1)) }
        calendar.matchEntire(normalized)?.let { return original(it, 1).takeIf { title -> title.length in 1..160 }?.let(LocalAction::CalendarDraft) }
        sms.matchEntire(normalized)?.let {
            return original(it, 2).takeIf { body -> body.length in 1..500 }?.let { body -> LocalAction.SmsDraft(it.groupValues[1], body) }
        }
        share.matchEntire(normalized)?.let { return LocalAction.Share(original(it, 1)) }
        return null
    }

    private fun normalize(value: String) = value.map { c ->
        when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            'ي' -> 'ی'
            'ك' -> 'ک'
            else -> if (c in 'A'..'Z') c.lowercaseChar() else c
        }
    }.joinToString("")

}
