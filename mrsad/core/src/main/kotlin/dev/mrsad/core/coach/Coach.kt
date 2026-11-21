package dev.mrsad.core.coach

import dev.mrsad.core.random.SplitMix64
import kotlin.math.roundToInt

/**
 * Offline coaching intelligence for MRSAD AI.
 *
 * Everything here is deterministic and rule/statistics based: it runs without
 * network, without a language model, and it only ever speaks from the data it
 * is handed. When data is insufficient it says so instead of inventing facts.
 */
data class SkillSnapshot(
    val typingSessions: Int = 0,
    val avgWpm: Double = 0.0,
    val avgAccuracy: Double = 0.0,
    val recentWpm: Double = 0.0,
    val previousWpm: Double = 0.0,
    val readingSessions: Int = 0,
    val avgReadingWpm: Int = 0,
    val avgComprehension: Int = 0,
    val mindSessions: Int = 0,
    val avgMindScore: Double = 0.0,
    val focusMinutesLast7Days: Int = 0,
    val activeDaysLast7Days: Int = 0,
    /** Expected characters the user mistypes most often, strongest first. */
    val topMistakes: List<Pair<Char, Int>> = emptyList(),
    /** Per-module session counts used for variety decisions. */
    val sessionsThisWeek: Map<Module, Int> = emptyMap(),
) {
    val totalSessions: Int get() = typingSessions + readingSessions + mindSessions
}

enum class Module { TYPE, READ, MIND, FOCUS }

enum class Confidence { LOW, MEDIUM, HIGH }

data class Recommendation(
    val module: Module,
    val titleFa: String,
    val reasonFa: String,
    val actionFa: String,
    /** Higher = more urgent. Derived from measured weakness, never random. */
    val priority: Int,
)

sealed interface CoachAnswer {
    /** Emitted when there is not enough data for an honest analysis. */
    data class NeedsData(val guidanceFa: String) : CoachAnswer

    data class Analysis(
        val headlineFa: String,
        val linesFa: List<String>,
        val recommendations: List<Recommendation>,
        val confidence: Confidence,
    ) : CoachAnswer
}

/** Static Persian copy shared by coach outputs; UI can reuse for consistency. */
object CoachCopy {
    const val WEAK_ACCURACY = "دقت تایپ"
    const val WEAK_READING = "درک مطلب"
    const val WEAK_MIND = "تمرین‌های ذهنی"
    const val WEAK_CONSISTENCY = "تداوم تمرین"
    const val FOCUS = "تمرکز"
}

object Coach {

    private const val MIN_SESSIONS_FOR_ANALYSIS = 3

    /** Builds a coaching answer purely from the supplied snapshot. */
    fun analyze(snapshot: SkillSnapshot): CoachAnswer {
        if (snapshot.totalSessions < MIN_SESSIONS_FOR_ANALYSIS) {
            val remaining = MIN_SESSIONS_FOR_ANALYSIS - snapshot.totalSessions
            return CoachAnswer.NeedsData(
                guidanceFa = "برای یک تحلیل واقعی به دادهٔ بیشتری نیاز است. " +
                    "حداقل ${persianNumber(MIN_SESSIONS_FOR_ANALYSIS)} جلسهٔ تمرین لازم است؛ " +
                    "هنوز ${persianNumber(remaining)} جلسه باقی مانده. پس از آن، تحلیل بر پایهٔ داده‌های واقعی شما ساخته می‌شود."
            )
        }

        val recommendations = buildRecommendations(snapshot)
        val lines = buildLines(snapshot)
        val headline = when {
            recommendations.isEmpty() -> "همه‌چیز متعادل است؛ برای حفظ روند، برنامهٔ هفتگی را ادامه بده."
            snapshot.topMistakes.isNotEmpty() && snapshot.avgAccuracy in 1.0..92.0 ->
                "مهم‌ترین فرصت بهبود: ${CoachCopy.WEAK_ACCURACY} — با تمرین هدفمند حروف، سریع‌تر از افزایش سرعت نتیجه می‌دهد."
            snapshot.avgComprehension in 1..59 ->
                "سرعت خواندن بدون درک مطلب کافی نیست؛ تمرین درک مطلب اولویت دارد."
            snapshot.activeDaysLast7Days <= 2 ->
                "تداوم، مهم‌ترین عامل پیشرفت است؛ تمرین‌های کوتاه روزانه مؤثرتر از تمرین‌های طولانی پراکنده است."
            else -> "روند تحلیل: نقاط قوت و ضعف بر اساس داده‌های اخیر شما."
        }
        return CoachAnswer.Analysis(
            headlineFa = headline,
            linesFa = lines,
            recommendations = recommendations,
            confidence = when {
                snapshot.totalSessions >= 20 -> Confidence.HIGH
                snapshot.totalSessions >= 8 -> Confidence.MEDIUM
                else -> Confidence.LOW
            },
        )
    }

    private fun buildRecommendations(s: SkillSnapshot): List<Recommendation> {
        val recs = ArrayList<Recommendation>()

        if (s.typingSessions >= 2 && s.avgAccuracy < 92.0) {
            val priority = (92.0 - s.avgAccuracy).roundToInt() + 60
            val chars = s.topMistakes.take(3).joinToString("، ") { "«${it.first}»" }
            recs += Recommendation(
                module = Module.TYPE,
                titleFa = "تمرین دقت تایپ",
                reasonFa = "میانگین دقت شما ${percentFa(s.avgAccuracy)} است؛ هدف سالم بالای ۹۵٪ است.",
                actionFa = if (chars.isNotEmpty())
                    "یک تمرین ۵ دقیقه‌ای با تمرکز روی حروف $chars انجام بده."
                else "یک تمرین ۵ دقیقه‌ای با سرعت کمتر و دقت بیشتر انجام بده.",
                priority = priority,
            )
        }

        if (s.readingSessions >= 2 && s.avgComprehension in 1..59) {
            recs += Recommendation(
                module = Module.READ,
                titleFa = "تمرین درک مطلب",
                reasonFa = "میانگین درک مطلب شما ${percentFa(s.avgComprehension.toDouble())} است؛ " +
                    "افزایش سرعت پیش از تثبیت درک مطلب، مسیر پیشرفت را کُند می‌کند.",
                actionFa = "یک جلسهٔ خواندن با سرعت پایین‌تر و ارزیابی درک مطلب انجام بده.",
                priority = 70 - s.avgComprehension,
            )
        }

        if (s.mindSessions >= 2 && s.avgMindScore in 1.0..49.0) {
            recs += Recommendation(
                module = Module.MIND,
                titleFa = "تمرین حافظهٔ کاری",
                reasonFa = "میانگین امتیاز تمرین‌های ذهنی شما ${percentFa(s.avgMindScore)} است.",
                actionFa = "دو تمرین کوتاه از دستهٔ حافظه و یکی از دستهٔ توجه انجام بده.",
                priority = 50 - s.avgMindScore.roundToInt(),
            )
        }

        if (s.activeDaysLast7Days <= 2) {
            recs += Recommendation(
                module = Module.FOCUS,
                titleFa = "برنامهٔ تمرین کوتاه روزانه",
                reasonFa = "در ۷ روز گذشته فقط ${persianNumber(s.activeDaysLast7Days)} روز فعال بوده‌اید.",
                actionFa = "هر روز ۱۰ دقیقه تمرین برنامه‌ریزی کن؛ یک جلسهٔ Focus Space روزانه بساز.",
                priority = 65,
            )
        }

        if (s.focusMinutesLast7Days == 0 && s.totalSessions >= MIN_SESSIONS_FOR_ANALYSIS) {
            recs += Recommendation(
                module = Module.FOCUS,
                titleFa = "شروع Focus Space",
                reasonFa = "در هفتهٔ گذشته جلسهٔ تمرکز ثبت نشده است.",
                actionFa = "یک جلسهٔ تمرکز ۲۵ دقیقه‌ای اجرا کن.",
                priority = 40,
            )
        }

        if (s.typingSessions >= 4 && s.previousWpm > 0) {
            val change = (s.recentWpm - s.previousWpm) / s.previousWpm * 100
            if (change < -5) {
                recs += Recommendation(
                    module = Module.TYPE,
                    titleFa = "بازگشت به ریتم سرعت",
                    reasonFa = "سرعت تایپ شما نسبت به هفتهٔ قبل ${percentFa(change)} تغییر کرده است.",
                    actionFa = "دو تمرین کوتاه تایپ با متن آشنا انجام بده تا ریتم برگردد.",
                    priority = 45,
                )
            }
        }

        return recs.sortedByDescending { it.priority }.take(3)
    }

    private fun buildLines(s: SkillSnapshot): List<String> {
        val lines = ArrayList<String>()
        if (s.typingSessions > 0) {
            val trendFa = when {
                s.previousWpm <= 0.0 -> "داده‌ای برای مقایسهٔ هفتگی موجود نیست"
                s.recentWpm > s.previousWpm -> "رو به بهبود"
                s.recentWpm < s.previousWpm -> "کمی کاهش"
                else -> "ثابت"
            }
            lines += "تایپ: میانگین ${persianNumber(s.avgWpm.roundToInt())} کلمه در دقیقه با دقت " +
                "${percentFa(s.avgAccuracy)} — روند $trendFa."
        }
        if (s.readingSessions > 0) {
            lines += "خواندن: سرعت ${persianNumber(s.avgReadingWpm)} کلمه در دقیقه " +
                "و درک مطلب ${percentFa(s.avgComprehension.toDouble())}."
        }
        if (s.mindSessions > 0) {
            lines += "تمرین‌های ذهنی: میانگین امتیاز ${percentFa(s.avgMindScore)}."
        }
        lines += "تداوم: ${persianNumber(s.activeDaysLast7Days)} روز فعال در ۷ روز گذشته؛ " +
            "تمرکز: ${persianNumber(s.focusMinutesLast7Days)} دقیقه."
        return lines
    }

    /**
     * Builds a day plan: weakest measured area first, then variety, seeded by
     * the date so the plan is stable for a whole day.
     */
    fun dailyPlan(snapshot: SkillSnapshot, dateIso: String): List<PlanItem> {
        val rng = SplitMix64(SplitMix64.seedFor("plan:$dateIso"))
        val items = ArrayList<PlanItem>()

        val weaknesses = ArrayList<Pair<Module, Int>>()
        if (snapshot.typingSessions > 0) weaknesses += Module.TYPE to (100 - snapshot.avgAccuracy).roundToInt()
        if (snapshot.readingSessions > 0) weaknesses += Module.READ to (100 - snapshot.avgComprehension)
        if (snapshot.mindSessions > 0) weaknesses += Module.MIND to (100 - snapshot.avgMindScore).roundToInt()
        if (snapshot.focusMinutesLast7Days < 75) weaknesses += Module.FOCUS to 55

        val ordered = if (weaknesses.isEmpty()) {
            rng.shuffle(listOf(Module.TYPE, Module.READ, Module.MIND, Module.FOCUS))
        } else {
            weaknesses.sortedByDescending { it.second }.map { it.first }
                .plus(rng.shuffle(listOf(Module.TYPE, Module.READ, Module.MIND, Module.FOCUS)))
                .distinct()
        }

        for (module in ordered.take(3)) {
            items += when (module) {
                Module.TYPE -> PlanItem(
                    "تمرین دقت تایپ", Module.TYPE, 5,
                    "برای تثبیت دقت، این جلسه کوتاه و متمرکز طراحی شده است.", dateIso,
                )
                Module.READ -> PlanItem(
                    "خواندن با درک مطلب", Module.READ, 10,
                    "خواندن همراه با ارزیابی، یادگیری پایدارتری ایجاد می‌کند.", dateIso,
                )
                Module.MIND -> PlanItem(
                    "تمرین ذهنی", Module.MIND, 6,
                    "تمرین‌های کوتاه روزانه، بهترین اثر را روی حافظهٔ کاری دارند.", dateIso,
                )
                Module.FOCUS -> PlanItem(
                    "جلسهٔ تمرکز", Module.FOCUS, 25,
                    "یک بلوک تمرکز، برای شروع عادت روزانه.", dateIso,
                )
            }
        }
        return items
    }

    fun persianNumber(value: Int): String =
        dev.mrsad.core.text.PersianText.toPersianDigits(value.toString())

    fun percentFa(value: Double): String =
        "${persianNumber(value.roundToInt())}٪"
}

data class PlanItem(
    val titleFa: String,
    val module: Module,
    val minutes: Int,
    val reasonFa: String,
    val dateIso: String,
)
