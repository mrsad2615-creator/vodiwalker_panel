package ai.dana.domain

/** Local inputs resolve to these fixed types. Model output never becomes a device command. */
sealed interface LocalAction {
    data class Note(val text: String) : LocalAction
    data class Timer(val seconds: Int) : LocalAction
    data class Alarm(val hour: Int, val minute: Int) : LocalAction
    data object Wifi : LocalAction
    data object Bluetooth : LocalAction
    data object Sound : LocalAction
    data object Clock : LocalAction
    data object Internet : LocalAction
    data object Battery : LocalAction
    data object MediaVolume : LocalAction
    enum class Panel : LocalAction { SETTINGS, DISPLAY, APPS, CAMERA, CALCULATOR }
    data class SetVolume(val percent: Int) : LocalAction
    enum class VolumeStep : LocalAction { LOWER, RAISE }
    data class Torch(val on: Boolean) : LocalAction
    data class Dial(val number: String) : LocalAction
    data class WebSearch(val query: String) : LocalAction
    data class CalendarDraft(val title: String) : LocalAction
    data class SmsDraft(val number: String, val text: String) : LocalAction
    data class Share(val text: String) : LocalAction
}

object LocalCommands {
    private val timer = Regex("(?:تایمر|timer)\\s+(?:(?:را|رو) )?(?:برای )?(.{1,32}?)\\s*(ثانیه|دقیقه|ساعت|s|m|h)(?: ای)?(?: (?:بذار|بگذار|تنظیم کن))?")
    private val alarm = Regex("(?:آلارم|زنگ|alarm)\\s+(?:(?:را|رو) )?(?:(?:برای|روی) )?(?:ساعت )?(\\d{1,2})[:٫](\\d{2})(?: (?:بذار|بگذار|تنظیم کن))?")
    private val note = Regex("(?:یادداشت|note)\\s+(.+)", RegexOption.DOT_MATCHES_ALL)
    private val calendar = Regex("(?:رویداد|event)\\s+(.+)")
    private val sms = Regex("(?:پیامک|sms)\\s+(\\+?[0-9]{5,15})\\s+(.+)", RegexOption.DOT_MATCHES_ALL)
    private val share = Regex("(?:اشتراک|share)\\s+(.+)", RegexOption.DOT_MATCHES_ALL)
    private val dial = Regex("(?:شماره گیری|شماره گیر|تماس با|dial|call)\\s+(\\+?[0-9](?:[0-9 -]{3,22}[0-9])?)(?: را بگیر)?")
    private val volume = Regex("(?:صدای رسانه|ولوم رسانه|صدای موزیک|صدا(?: را| رو)|media volume|volume)\\s+(?:(?:را|رو) )?(?:(?:بذار|بگذار) )?(?:روی )?(.{1,24}?)\\s*(?:درصد|[%٪])?(?: (?:کن|بذار|بگذار|تنظیم کن))?")
    private val search = Regex("(?:جستجوی وب|جست و جوی وب|جست وجوی وب|جستجو در وب|جست وجو در وب|جستجو کن|search web)\\s+(.+)")
    private val gaps = Regex("\\s+")
    private val polite = Regex("^لطف[اًا]+[ ،]+")

    fun parse(raw: String): LocalAction? {
        val text = raw.trim()
        if (text.length !in 1..LocalText.NOTE_LIMIT) return null
        // Preserve UTF-16 indexes so literal notes, titles and query payloads don't get rewritten.
        val normalized = normalize(text)
        val command = normalized.replace(gaps, " ").replaceFirst(polite, "").trimEnd(' ', '.', '!', '?', '؟', '،')
        when (command) {
            "وای فای", "وای فای را باز کن", "تنظیمات وای فای", "wifi" -> return LocalAction.Wifi
            "بلوتوث", "بلوتوث را باز کن", "تنظیمات بلوتوث", "bluetooth" -> return LocalAction.Bluetooth
            "تنظیمات صدا", "sound", "volume panel" -> return LocalAction.Sound
            "باز کردن ساعت", "ساعت را باز کن", "clock" -> return LocalAction.Clock
            "اینترنت", "تنظیمات اینترنت", "اینترنت را باز کن", "internet" -> return LocalAction.Internet
            "باتری", "وضعیت باتری", "شارژ گوشی", "شارژ گوشی چنده", "شارژ گوشی چقدره", "چقدر شارژ دارم", "شارژ باتری چقدره", "battery" -> return LocalAction.Battery
            "صدای رسانه", "ولوم رسانه", "صدای رسانه چنده", "media volume" -> return LocalAction.MediaVolume
            "صدا را کم کن", "صدا رو کم کن", "صدای رسانه را کم کن", "صدا کم کن", "volume down" -> return LocalAction.VolumeStep.LOWER
            "صدا را زیاد کن", "صدا رو زیاد کن", "صدای رسانه را زیاد کن", "صدا زیاد کن", "volume up" -> return LocalAction.VolumeStep.RAISE
            "تنظیمات گوشی", "تنظیمات را باز کن", "settings" -> return LocalAction.Panel.SETTINGS
            "تنظیمات نمایشگر", "صفحه نمایش", "display settings" -> return LocalAction.Panel.DISPLAY
            "مدیریت برنامه ها", "تنظیمات برنامه ها", "apps settings" -> return LocalAction.Panel.APPS
            "دوربین را باز کن", "دوربین باز کن", "باز کردن دوربین", "open camera" -> return LocalAction.Panel.CAMERA
            "ماشین حساب", "ماشین حساب را باز کن", "ماشین حساب باز کن", "calculator" -> return LocalAction.Panel.CALCULATOR
            "چراغ قوه روشن", "چراغ قوه را روشن کن", "چراغ قوه رو روشن کن", "چراغ قوه روشن کن", "flashlight on", "torch on" -> return LocalAction.Torch(true)
            "چراغ قوه خاموش", "چراغ قوه را خاموش کن", "چراغ قوه رو خاموش کن", "چراغ قوه خاموش کن", "flashlight off", "torch off" -> return LocalAction.Torch(false)
        }
        timer.matchEntire(command)?.let { match ->
            val amount = commandNumber(match.groupValues[1]) ?: return null
            val factor = when (match.groupValues[2]) { "ثانیه", "s" -> 1L; "دقیقه", "m" -> 60L; else -> 3_600L }
            val seconds = amount * factor
            return if (seconds in 1..86_400) LocalAction.Timer(seconds.toInt()) else null
        }
        alarm.matchEntire(command)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            return if (hour in 0..23 && minute in 0..59) LocalAction.Alarm(hour, minute) else null
        }
        volume.matchEntire(command)?.let { match ->
            return commandNumber(match.groupValues[1])?.takeIf { it in 0..100 }?.let(LocalAction::SetVolume)
        }
        dial.matchEntire(command)?.let {
            val number = it.groupValues[1].filter { c -> c == '+' || c in '0'..'9' }
            return number.takeIf { numberPattern.matches(it) }?.let(LocalAction::Dial)
        }
        fun original(value: MatchResult, group: Int) = text.substring(checkNotNull(value.groups[group]).range).trim()
        note.matchEntire(normalized)?.let { return LocalAction.Note(original(it, 1)) }
        calendar.matchEntire(normalized)?.let { return original(it, 1).takeIf { title -> title.length in 1..160 }?.let(LocalAction::CalendarDraft) }
        sms.matchEntire(normalized)?.let {
            return original(it, 2).takeIf { body -> body.length in 1..500 }?.let { body -> LocalAction.SmsDraft(it.groupValues[1], body) }
        }
        share.matchEntire(normalized)?.let { return LocalAction.Share(original(it, 1)) }
        search.matchEntire(normalized)?.let { return original(it, 1).takeIf { q -> q.length in 1..240 }?.let(LocalAction::WebSearch) }
        return null
    }

    private val numberPattern = Regex("\\+?[0-9]{5,15}")
    private val units = listOf("صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val tens = listOf("بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private fun commandNumber(raw: String): Int? {
        val value = raw.trim()
        value.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }?.toIntOrNull()?.let { return it }
        if (value == "صد" || value == "یک صد") return 100
        units.indexOf(value).takeIf { it >= 0 }?.let { return it }
        val base = tens.indexOf(value.substringBefore(" و ")).takeIf { it >= 0 } ?: return null
        val rest = value.substringAfter(" و ", "")
        val unit = if (rest.isEmpty()) 0 else units.indexOf(rest).takeIf { it in 1..9 } ?: return null
        return (base + 2) * 10 + unit
    }

    private fun normalize(value: String) = value.map { c ->
        when (c) {
            in '۰'..'۹' -> '0' + (c - '۰')
            in '٠'..'٩' -> '0' + (c - '٠')
            'ي' -> 'ی'
            'ك' -> 'ک'
            '\u200c', '\u00a0' -> ' '
            else -> if (c in 'A'..'Z') c.lowercaseChar() else c
        }
    }.joinToString("")
}
