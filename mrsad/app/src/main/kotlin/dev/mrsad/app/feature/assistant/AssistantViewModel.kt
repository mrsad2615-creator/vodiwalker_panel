package dev.mrsad.app.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.util.MrsadClock
import dev.mrsad.core.analytics.AnalyticsMath
import dev.mrsad.core.coach.Coach
import dev.mrsad.core.coach.CoachAnswer
import dev.mrsad.core.coach.SkillSnapshot
import dev.mrsad.core.text.PersianText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

enum class Role { USER, ASSISTANT }

data class Message(val role: Role, val text: String)

data class AssistantUiState(
    val messages: List<Message> = emptyList(),
)

/**
 * Offline coach conversation.
 *
 * Intent routing is deterministic keyword matching (Persian + English), and
 * every answer is generated from the user's real aggregate data through
 * [Coach]. Questions the engine cannot ground in data receive an honest
 * "insufficient data / not enough information" answer — never a fabricated one.
 */
class AssistantViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    fun ask(question: String, persianNumerals: Boolean) {
        _state.update { it.copy(messages = it.messages + Message(Role.USER, question)) }
        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = loadSnapshot()
            val answer = answerFor(question, snapshot, persianNumerals)
            _state.update { it.copy(messages = it.messages + Message(Role.ASSISTANT, answer)) }
        }
    }

    private suspend fun loadSnapshot(): SkillSnapshot {
        val activityDao = container.database.activityDao()
        val typingDao = container.database.typingSessionDao()
        val now = MrsadClock.nowEpochMillis()
        val recentWpm = typingDao.averageWpmSince(now - DAY * 7) ?: 0.0
        val previousWpm = typingDao.averageWpmBetween(now - DAY * 14, now - DAY * 7) ?: 0.0
        val accuracy = typingDao.averageAccuracySince(now - DAY * 7) ?: 0.0
        val typingSessions = typingDao.totalCount().first()
        val activeDates = activityDao.activeDates().first()
        val recent = activityDao.recentEvents(now - DAY * 30, 200).first()
        val readEvents = recent.filter { it.module == "READ" }
        val mindEvents = recent.filter { it.module == "MIND" }
        val focusMinutes = recent.filter { it.module == "FOCUS" }.sumOf { it.minutes }
        val mistakes = typingDao.mistakesSince(now - DAY * 14)
            .flatMap { encoded ->
                encoded.split('|').mapNotNull { part ->
                    val code = part.substringBefore(':').toIntOrNull()
                    val count = part.substringAfter(':', "").toIntOrNull()
                    if (code == null || count == null) null else code.toChar() to count
                }
            }
            .groupingBy { it.first }
            .fold(0) { acc, pair -> acc + pair.second }
            .entries.sortedByDescending { it.value }
            .take(4)
            .map { it.key to it.value }

        return SkillSnapshot(
            typingSessions = typingSessions,
            avgWpm = recentWpm,
            avgAccuracy = accuracy,
            recentWpm = recentWpm,
            previousWpm = previousWpm,
            readingSessions = readEvents.size,
            avgReadingWpm = readEvents.map { it.primaryMetric }.averageOrZero(),
            avgComprehension = readEvents.map { it.secondaryMetric }.averageOrZero(),
            mindSessions = mindEvents.size,
            avgMindScore = mindEvents.map { it.secondaryMetric * 100 }.averageOrZero(),
            focusMinutesLast7Days = focusMinutes,
            activeDaysLast7Days = AnalyticsMath.lastDays(MrsadClock.todayIso(), 7).count { it in activeDates },
            topMistakes = mistakes,
        )
    }

    private fun answerFor(question: String, snapshot: SkillSnapshot, persianNumerals: Boolean): String {
        val q = PersianText.normalizeForDisplay(question.lowercase())
        val wantsPlan = listOf("برنامه", "تمرین", "امروز", "چه کار", "پیشنهاد", "پلن", "plan").any { it in q }
        val wantsTyping = listOf("تایپ", "سرعت", "دقت", "type", "wpm", "کیبورد").any { it in q || it in question.lowercase() }
        val wantsFeatures = listOf("امکان", "مجوز", " چه‌کار", "کمک", "چیست", "چی‌کار", "ویژگی", " اینترنت", "آفلاین", "حریم").any { it in q }
        val wantsReading = listOf("خواندن", "درک", "مطالعه", "read").any { it in q }
        val wantsMind = listOf("ذهن", "حافظه", "تمرکز حواس", "mind", "شناختی").any { it in q }

        if (wantsFeatures) return featuresAnswer()
        if (wantsPlan) return planAnswer(snapshot, persianNumerals)
        if (wantsTyping) return typingAnswer(snapshot)
        if (wantsReading) return readingAnswer(snapshot)
        if (wantsMind) return mindAnswer(snapshot)

        // Default: a full analysis, or an honest needs-data response.
        return when (val answer = Coach.analyze(snapshot)) {
            is CoachAnswer.NeedsData -> answer.guidanceFa +
                "\n\nدر این فاصله می‌توانم دربارهٔ امکانات برنامه پاسخ دهم: بنویسید «مرصاد چه امکاناتی دارد؟»"
            is CoachAnswer.Analysis -> buildString {
                append(answer.headlineFa).append("\n\n")
                answer.linesFa.forEach { append("• $it\n") }
                if (answer.recommendations.isNotEmpty()) {
                    append("\nپیشنهادها:\n")
                    answer.recommendations.forEach { append("— ${it.titleFa}: ${it.actionFa}\n") }
                }
            }.trim()
        }
    }

    private fun typingAnswer(snapshot: SkillSnapshot): String {
        if (snapshot.typingSessions == 0) {
            return "هنوز جلسهٔ تایپی ثبت نشده است، بنابراین آماری برای تحلیل ندارم. " +
                "یک تمرین ۶۰ ثانیه‌ای در MRSAD TYPE انجام دهید تا تحلیل واقعی ساخته شود."
        }
        val accuracyLine = if (snapshot.avgAccuracy < 92) {
            "دقت شما پایین‌تر از هدف ۹۵٪ است؛ پیشنهاد می‌کنم یک تمرین با سرعت پایین‌تر و تمرکز روی حروف دشوار انجام دهید."
        } else {
            "دقت شما در محدودهٔ سالم است؛ می‌توانید سرعت را به‌تدریج بالا ببرید."
        }
        val mistakesLine = if (snapshot.topMistakes.isNotEmpty()) {
            "\nحروفی که بیشتر اشتباه می‌شوند: " + snapshot.topMistakes.joinToString("، ") { "«${it.first}»" } + "."
        } else ""
        val trend = if (snapshot.previousWpm > 0) {
            val change = (snapshot.recentWpm - snapshot.previousWpm) / snapshot.previousWpm * 100
            "\nتغییر نسبت به هفتهٔ قبل: ${if (change >= 0) "+" else ""}${change.roundToInt()}٪."
        } else ""
        return "میانگین هفتهٔ اخیر شما ${snapshot.avgWpm.roundToInt()} کلمه در دقیقه با دقت " +
            "${snapshot.avgAccuracy.roundToInt()}٪ است.$trend\n$accuracyLine$mistakesLine"
    }

    private fun readingAnswer(snapshot: SkillSnapshot): String {
        if (snapshot.readingSessions == 0) {
            return "جلسهٔ خواندنی ثبت نشده است. در MRSAD READ یک متن را انتخاب کنید؛ پس از خواندن، " +
                "درک مطلب سنجیده می‌شود و سرعت مؤثر (سرعت × درک) به‌عنوان معیار اصلی ثبت می‌گردد."
        }
        return "میانگین درک مطلب شما ${snapshot.avgComprehension.roundToInt()}٪ با سرعت ${snapshot.avgReadingWpm} کلمه در دقیقه است. " +
            if (snapshot.avgComprehension < 60)
                "برای تثبیت درک مطلب، سرعت را کمی پایین بیاورید و پس از هر جلسه به پرسش‌ها دقیق پاسخ دهید."
            else "درک مطلب شما در سطح قابل قبولی است؛ افزایش تدریجی سرعت منطقی است."
    }

    private fun mindAnswer(snapshot: SkillSnapshot): String {
        if (snapshot.mindSessions == 0) {
            return "تمرین ذهنی ثبت نشده است. از MRSAD MIND با تمرین «حافظهٔ عددی» شروع کنید؛ " +
                "سطح تمرین‌ها بر اساس نتایج خودکار تنظیم می‌شود."
        }
        return "میانگین امتیاز تمرین‌های ذهنی شما ${snapshot.avgMindScore.roundToInt()}٪ است. " +
            if (snapshot.avgMindScore < 50) "تمرین‌های حافظه و توجه را با جلسات کوتاه روزانه تکرار کنید."
            else "سطح فعلی خوب است؛ سطح بعدی را روی ضعیف‌ترین دسته (توجه یا منطق) بگذارید."
    }

    private fun planAnswer(snapshot: SkillSnapshot, persianNumerals: Boolean): String {
        if (snapshot.totalSessions == 0) {
            return "برای ساختن برنامه، ابتدا به داده نیاز دارم. با اولین تمرین‌ها برنامهٔ روزانه در صفحهٔ خانه شکل می‌گیرد."
        }
        val plan = Coach.dailyPlan(snapshot, MrsadClock.todayIso())
        return "برنامهٔ پیشنهادی امروز بر پایهٔ داده‌های شما:\n" +
            plan.joinToString("\n") { "— ${it.titleFa} (${it.minutes} دقیقه): ${it.reasonFa}" }
    }

    private fun featuresAnswer(): String = buildString {
        appendLine("مرصاد یک محیط کامل تمرین و رشد شناختی است و همه‌چیز روی همین دستگاه و بدون اینترنت کار می‌کند:")
        appendLine("— TYPE: تمرین تایپ فارسی و انگلیسی با سنجش سرعت، دقت و تحلیل خطاها")
        appendLine("— READ: خواندن سریع (RSVP) همراه با ارزیابی درک مطلب")
        appendLine("— MIND: شش تمرین شناختی واقعی (حافظه، توجه، پردازش، منطق)")
        appendLine("— ANALYTICS: تحلیل داده‌های واقعی تمرین")
        appendLine("— AI: همین مربی محلی که از داده‌های شما تحلیل می‌سازد")
        appendLine("— STUDIO: شش تم بصری و تنظیمات کامل ظاهری")
        append("بخش‌های ARENA، FOCUS SPACE، برنامه‌ریز و کتابخانه در فاز بعدی توسعه فعال می‌شوند.")
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AssistantViewModel(container) as T
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
