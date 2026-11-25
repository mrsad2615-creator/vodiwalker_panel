package com.neurox.games.content

import com.neurox.games.definition.TextNormalizer

/** A reading text bundled with the application. No network is required. */
data class ReadingText(
    val id: String,
    val languageTag: String,
    val title: String,
    val body: String,
    val difficulty: Int,
    val topic: String,
    /** Comprehension questions; empty when the text has none. */
    val questions: List<ComprehensionQuestion> = emptyList(),
) {
    init {
        require(difficulty in 1..5) { "difficulty must be in 1..5" }
        require(languageTag in SUPPORTED_LANGUAGES) { "unsupported language $languageTag" }
        require(title.isNotBlank() && body.isNotBlank())
    }

    val wordCount: Int get() = TextNormalizer.words(body)
    val characterCount: Int get() = TextNormalizer.measurableCharacters(body)
    val hasQuestions: Boolean get() = questions.isNotEmpty()

    companion object {
        val SUPPORTED_LANGUAGES = setOf("en", "fa")
    }
}

/** One multiple-choice comprehension question. */
data class ComprehensionQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
) {
    init {
        require(options.size in 2..4) { "a question needs 2..4 options" }
        require(correctIndex in options.indices)
    }
}

/**
 * Offline reading library.
 *
 * All texts are original NEUROX content, written for the product. They contain no
 * third-party copyrighted material. Lengths and difficulties are fixed so tests
 * and analytics stay stable.
 */
object ReadingLibrary {

    /** Title keys are localized by the presentation layer. */
    private const val LANG_EN = "en"
    private const val LANG_FA = "fa"

    val texts: List<ReadingText> = listOf(
        ReadingText(
            id = "read.en.attention",
            languageTag = LANG_EN,
            title = "The Quiet Power of Attention",
            body = """
                Attention is a limited resource. A focused mind can hold only a small
                amount of information at one time. When we train, we learn to choose one
                signal and ignore the rest. This skill improves with short, regular
                practice. Speed without control is not progress. Control with speed is.
            """.trimIndent(),
            difficulty = 1,
            topic = "focus",
            questions = listOf(
                ComprehensionQuestion(
                    id = "q1",
                    prompt = "What does the passage say about attention?",
                    options = listOf("It is unlimited", "It is a limited resource", "It cannot be trained"),
                    correctIndex = 1,
                ),
                ComprehensionQuestion(
                    id = "q2",
                    prompt = "According to the passage, what is progress?",
                    options = listOf("Speed alone", "Control with speed", "Ignoring signals"),
                    correctIndex = 1,
                ),
            ),
        ),
        ReadingText(
            id = "read.en.reaction",
            languageTag = LANG_EN,
            title = "Reaction and Readiness",
            body = """
                A reaction is the time between a signal and an answer. Many things
                affect it: sleep, light, the device, and how well the task is known.
                Because the same person can react differently on different days, one
                measurement proves little. Look at a trend over several sessions. A
                steady trend tells more than a single fast score.
            """.trimIndent(),
            difficulty = 2,
            topic = "reaction",
            questions = listOf(
                ComprehensionQuestion(
                    id = "q1",
                    prompt = "What does a steady trend tell you?",
                    options = listOf("Nothing useful", "More than one fast score", "Only today's state"),
                    correctIndex = 1,
                ),
            ),
        ),
        ReadingText(
            id = "read.fa.tamarkoz",
            languageTag = LANG_FA,
            title = "تمرکز در زندگی روزمره",
            body = """
                تمرکز یعنی انتخاب یک چیز و نادیده گرفتن بقیه. ذهن ما نمی‌تواند همه‌چیز را با هم ببیند؛
                پس یاد می‌گیریم مهم را از کم‌اهمیت جدا کنیم. تمرین کوتاه و روزانه از جلسه طولانی
                و گاه‌به‌گاه بهتر است. اگر خسته هستید، استراحت کنید و بعد ادامه بدهید.
                سرعت بدون دقت پیشرفت نیست.
            """.trimIndent(),
            difficulty = 2,
            topic = "focus",
            questions = listOf(
                ComprehensionQuestion(
                    id = "q1",
                    prompt = "بر اساس متن، تمرین کوتاه و روزانه چگونه است؟",
                    options = listOf(
                        "بهتر از جلسه طولانی و گاه‌به‌گاه",
                        "بی‌فایده است",
                        "فقط برای سالمندان است",
                    ),
                    correctIndex = 0,
                ),
                ComprehensionQuestion(
                    id = "q2",
                    prompt = "نویسنده درباره سرعت بدون دقت چه می‌گوید؟",
                    options = listOf("پیشرفت است", "پیشرفت نیست", "تنها هدف است"),
                    correctIndex = 1,
                ),
            ),
        ),
        ReadingText(
            id = "read.fa.khandan",
            languageTag = LANG_FA,
            title = "خواندن بهتر",
            body = """
                خواندن سریع فقط به چشم بستگی ندارد. مغز ما باید کلمه‌ها را بشناسد و
                معنی جمله را بسازد. وقتی چشمتان روی یک نقطه بایستد، درک بهتر می‌شود.
                خواندن با فهم، بهتر از خواندن با سرعت است. هر روز چند دقیقه بخوانید.
            """.trimIndent(),
            difficulty = 1,
            topic = "reading",
            questions = listOf(
                ComprehensionQuestion(
                    id = "q1",
                    prompt = "نویسنده کدام را بهتر می‌داند؟",
                    options = listOf("خواندن با سرعت", "خواندن با فهم", "نخواندن"),
                    correctIndex = 1,
                ),
            ),
        ),
        ReadingText(
            id = "read.en.habits",
            languageTag = LANG_EN,
            title = "Small Habits, Long Journeys",
            body = """
                A habit grows from repetition, not from intensity. Five minutes every
                day beats one hour every second week. Start with a goal that is easy
                to keep. When the small goal becomes automatic, make it slightly
                harder. Progress is a series of small, boring decisions that add up.
            """.trimIndent(),
            difficulty = 3,
            topic = "habits",
            questions = listOf(
                ComprehensionQuestion(
                    id = "q1",
                    prompt = "According to the passage, a habit grows from what?",
                    options = listOf("Intensity", "Repetition", "Talent"),
                    correctIndex = 1,
                ),
                ComprehensionQuestion(
                    id = "q2",
                    prompt = "What should you do when a small goal becomes automatic?",
                    options = listOf("Stop training", "Make it slightly harder", "Change the activity"),
                    correctIndex = 1,
                ),
            ),
        ),
        ReadingText(
            id = "read.fa.paydari",
            languageTag = LANG_FA,
            title = "پایداری در تمرین",
            body = """
                پایداری مهم‌تر از شدت است. هر روز چند دقیقه تمرین، از یک ساعت تمرین هفته‌ای
                یک بار بهتر است. گاهی پیشرفت سریع است و گاهی کند. این طبیعی است.
                مهم این است که روند کلی رو به جلو باشد. به یک عدد تکیه نکنید؛ روند را ببینید.
            """.trimIndent(),
            difficulty = 3,
            topic = "habits",
            questions = listOf(
                ComprehensionQuestion(
                    id = "q1",
                    prompt = "نویسنده درباره یک عدد تنها چه می‌گوید؟",
                    options = listOf("به آن تکیه نکنید", "تنها معیار است", "همیشه درست است"),
                    correctIndex = 0,
                ),
            ),
        ),
    )

    fun byId(id: String): ReadingText? = texts.firstOrNull { it.id == id }

    fun forLanguage(languageTag: String): List<ReadingText> = texts.filter { it.languageTag == languageTag }

    fun forDifficulty(level: Int): List<ReadingText> = texts.filter { it.difficulty == level }

    fun withQuestions(): List<ReadingText> = texts.filter { it.hasQuestions }

    /** Deterministic pick for a difficulty band, used by the planner. */
    fun pick(languageTag: String, difficulty: Int, seed: Long): ReadingText {
        val pool = forLanguage(languageTag).ifEmpty { texts }
        val nearest = pool.minByOrNull { kotlin.math.abs(it.difficulty - difficulty) } ?: texts.first()
        val sameBand = pool.filter { kotlin.math.abs(it.difficulty - nearest.difficulty) <= 1 }
        return sameBand[kotlin.math.abs(seed.toInt()) % sameBand.size]
    }

    /** All question ids, used to validate that content is complete. */
    fun questionIds(): Set<String> = texts.flatMap { text -> text.questions.map { text.id + "/" + it.id } }.toSet()
}

/**
 * Short word lists for word-level exercises.
 */
object WordLists {

    val english: List<String> = listOf(
        "focus", "light", "pause", "steady", "signal", "quiet", "pattern", "motion",
        "recall", "rhythm", "clarity", "control", "balance", "gentle", "silent",
        "orbit", "prism", "copper", "velvet", "harbor", "amber", "calm",
    )

    val persian: List<String> = listOf(
        "تمرکز", "نور", "آرام", "حرکت", "الگو", "دقت", "سرعت", "حافظه", "تمرین",
        "پایداری", "روشن", "سکوت", "توازن", "آرامش", "پیشرفت", "کوشش", "مدار",
    )

    fun forLanguage(languageTag: String): List<String> =
        if (languageTag == "fa") persian else english

    /** Fixed phrases used by phrase reading. */
    val phrases: Map<String, List<String>> = mapOf(
        "en" to listOf(
            "keep your eyes steady",
            "small steps every day",
            "a clear and quiet mind",
            "measure the trend not the day",
            "speed follows control",
        ),
        "fa" to listOf(
            "چشم‌ها را آرام نگه دار",
            "هر روز یک قدم کوچک",
            "ذهن روشن و آرام",
            "روند را ببین نه یک روز را",
            "سرعت پس از کنترل می‌آید",
        ),
    )
}
