package ai.dana.domain

/** An utterance can produce a proposal or a question, never an Android operation. */
sealed interface PhoneIntent {
    data class Propose(val action: LocalAction) : PhoneIntent
    data class NeedDuration(val target: DurationTarget) : PhoneIntent
    data object Unknown : PhoneIntent
}

enum class DurationTarget {
    TIMER, WIFI_OFF, WIFI_ON;

    fun action(seconds: Int): LocalAction? = if (seconds !in 1..86_400) null else when (this) {
        TIMER -> LocalAction.Timer(seconds)
        WIFI_OFF, WIFI_ON -> LocalAction.WifiReminder(seconds, turnOn = this == WIFI_ON)
    }
}

/** Small, bounded Persian grammar for offline commands. Match the whole request; do not execute a fragment. */
object PhoneUtterances {
    private val leading = Regex("^(?:(?:لطفا|میشه|می شه|میتونی|می تونی|میتوانی|می توانی|برام|واسم|برای من|بهم بگو)[ ،,]+)+")
    private val trailing = Regex("[ ،,]+(?:لطفا|برام|واسم|برای من)$")
    private val wifiSpelling = Regex("(?<![\\p{L}])(?:وای[ -]*فای|wi-?fi)(?![\\p{L}])")
    private val boundary = Regex("\\s*[،,]\\s*")
    private val meta = Regex("^(?:اگر |چگونه |چطوری |چطور |فقط توضیح |نمی[ ]?خو[ا]?[هم] |درباره )")
    private const val SWITCH = "(خاموش|قطع|غیر ?فعال|روشن|وصل|فعال)"
    private const val EXECUTE = "(?: (?:کن|بکن|کنید|کردن))?"
    private const val TIMER_VERB = "(?: (?:بذار|بزار|بگذار|بذاری|بزاری|بگذاری|بذارید|بگذارید|تنظیم کن|تنظیم کنید|شروع کن|بزن|بگیر))?"
    private const val SLOT = "(.{1,64}?)"
    private const val AFTER = "(?:بعد از|پس از)"
    private const val LATER = "(?:بعد|دیگه|دیگر)"
    private const val TIMER_START = "(?:(?:یه|یک) )?(?:تایمر|شمارش معکوس|timer)(?: (?:رو|را))?"

    private val wifiNow = listOf(
        Regex("وای فای(?: را| رو)?(?: الان| همین حالا)? $SWITCH$EXECUTE"),
        Regex("$SWITCH(?: کردن)? وای فای(?: را| رو)?$EXECUTE"),
    )
    private val wifiBefore = listOf(
        Regex("$AFTER $SLOT,? (.+)"),
        Regex("(?:تا )?$SLOT $LATER,? (.+)"),
    )
    private val wifiWithin = listOf(
        Regex("وای فای(?: را| رو)? $AFTER $SLOT $SWITCH$EXECUTE"),
        Regex("وای فای(?: را| رو)? $SLOT $LATER $SWITCH$EXECUTE"),
    )
    private val wifiAtEnd = Regex("(.+?) $AFTER $SLOT")
    private val wifiTimer = Regex("$TIMER_START(?: برای)? (خاموش|روشن) کردن وای فای(?: (?:برای )?$SLOT)?$TIMER_VERB")
    private val timer = listOf(
        Regex("$TIMER_START(?: برای)? $SLOT$TIMER_VERB"),
        Regex("(?:برای )?$SLOT $TIMER_START$TIMER_VERB"),
        Regex("$TIMER_START (?:بذار|بزار|بگذار|تنظیم کن) (?:برای )?$SLOT"),
    )
    private val noTimerDuration = Regex("$TIMER_START$TIMER_VERB")
    private val battery = Regex("(?:وضعیت (?:شارژ )?باتری(?: رو بگو| را بگو)?|چقدر (?:شارژ|باتری) (?:دارم|مونده|مانده)|چند درصد (?:شارژ|باتری)(?: (?:دارم|مونده|مانده))?|(?:گوشیم|گوشی) چقدر شارژ (?:داره|دارد))")
    private val durationPart = Regex("(.+?)\\s*(ثانیه|دقیقه|ساعت|s|m|h)(?: ای)?(?: و (.+))?")
    private val quarter = Regex("(?:(یک|یه|دو|سه|[1-3]) )?ربع(?: ساعت)?")

    fun interpret(raw: String): PhoneIntent {
        if (!ReviewMessage.acceptsDraft(raw) || raw.isBlank()) return PhoneIntent.Unknown
        val text = surface(raw)
        if (meta.containsMatchIn(text)) return PhoneIntent.Unknown
        wifiTimer.matchEntire(text)?.let {
            return timed(wifiTarget(it.groupValues[1]), it.groupValues[2])
        }
        for (pattern in wifiBefore) pattern.matchEntire(text)?.let {
            wifiSwitch(it.groupValues[2])?.let { on -> return timed(wifiTarget(on), it.groupValues[1]) }
        }
        for (pattern in wifiWithin) pattern.matchEntire(text)?.let {
            return timed(wifiTarget(it.groupValues[2]), it.groupValues[1])
        }
        wifiAtEnd.matchEntire(text)?.let {
            wifiSwitch(it.groupValues[1])?.let { on -> return timed(wifiTarget(on), it.groupValues[2]) }
        }
        wifiSwitch(text)?.let { return PhoneIntent.Propose(LocalAction.WifiChange(it)) }
        if (noTimerDuration.matches(text)) return PhoneIntent.NeedDuration(DurationTarget.TIMER)
        for (pattern in timer) pattern.matchEntire(text)?.let {
            val seconds = duration(it.groupValues[1])
            if (seconds != null) return PhoneIntent.Propose(LocalAction.Timer(seconds))
        }
        // An unknown suffix may be a second command or a restriction, not merely an omitted time.
        if (battery.matches(text)) return PhoneIntent.Propose(LocalAction.Battery)
        val local = LocalCommands.parse(raw)
        return if (local == null || local is LocalAction.WebSearch || local is LocalAction.Share) PhoneIntent.Unknown
        else PhoneIntent.Propose(local)
    }

    /** Follow-up replies require a unit. No assumed minutes, rounded times, or partial matches. */
    fun duration(raw: String): Int? {
        if (raw.length !in 1..80) return null
        val value = surface(raw)
        quarter.matchEntire(value)?.let {
            val amount = when (it.groupValues[1]) { "دو", "2" -> 2; "سه", "3" -> 3; else -> 1 }
            return amount * 900
        }
        return durationPart(value, previousFactor = 3_601, remainingParts = 3)?.takeIf { it in 1..86_400 }?.toInt()
    }

    private fun durationPart(value: String, previousFactor: Int, remainingParts: Int): Long? {
        if (remainingParts == 0) return null
        val match = durationPart.matchEntire(value) ?: return null
        val factor = when (match.groupValues[2]) { "ثانیه", "s" -> 1; "دقیقه", "m" -> 60; else -> 3_600 }
        if (factor >= previousFactor) return null
        val quantity = match.groupValues[1].trim()
        val base = if (quantity in setOf("نیم", "نصف") && factor > 1) factor / 2L else {
            val amount = if (quantity == "یه") 1 else LocalCommands.commandNumber(quantity) ?: return null
            amount.toLong() * factor
        }
        if (base !in 0..86_400) return null
        val rest = match.groupValues[3]
        val tail = when {
            rest.isEmpty() -> 0L
            rest == "نیم" && factor > 1 && quantity !in setOf("نیم", "نصف") -> factor / 2L
            else -> durationPart(rest, factor, remainingParts - 1) ?: return null
        }
        return base + tail
    }

    private fun wifiSwitch(text: String): Boolean? = wifiNow.firstNotNullOfOrNull { pattern ->
        pattern.matchEntire(text)?.groupValues?.get(1)?.let(::isOn)
    }

    private fun isOn(value: String) = value in setOf("روشن", "وصل", "فعال")
    private fun wifiTarget(value: String) = wifiTarget(isOn(value))
    private fun wifiTarget(on: Boolean) = if (on) DurationTarget.WIFI_ON else DurationTarget.WIFI_OFF
    private fun timed(target: DurationTarget, raw: String): PhoneIntent = when {
        raw.isBlank() -> PhoneIntent.NeedDuration(target)
        else -> duration(raw)?.let(target::action)?.let(PhoneIntent::Propose) ?: PhoneIntent.NeedDuration(target)
    }

    private fun surface(raw: String): String = LocalCommands.appLabel(raw)
        .trimEnd(' ', '.', '!', '?', '؟', '،')
        .replaceFirst(leading, "").replace(trailing, "")
        .replace(wifiSpelling, "وای فای").replace(boundary, ", ")
        .trim()
}
