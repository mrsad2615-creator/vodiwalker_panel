package com.typflow.app.data

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int
)

data class ReadingText(
    val lang: String,
    val title: String,
    val body: String,
    val questions: List<QuizQuestion>
)

/**
 * All offline content: typing drills by difficulty and reading passages
 * with comprehension checks. No network, no assets.
 */
object Texts {

    // ---------------- typing: Persian ----------------

    private val faEasy = listOf(
        "تایپ سریع مهارتی است که با تمرین روزانه به دست می آید. هر روز چند دقیقه تمرین کنید تا سرعت و دقت شما بیشتر شود.",
        "کار کوچک هر روز از کار بزرگ گاهی بهتر است. ذهن ما با تکرار آرام و پیوسته شکل می گیرد و عادت می سازد.",
        "نوشتن با دست و تایپ با صفحه کلید دو مهارت جدا هستند. برای تایپ خوب باید انگشتان جای کلیدها را حفظ باشند."
    )

    private val faMedium = listOf(
        "خواندن سریع به شما کمک می کند در زمان کمتر اطلاعات بیشتری کسب کنید. با تمرین مداوم می توانید سرعت مطالعه خود را به طرز چشمگیری افزایش دهید و درک مطلب بهتری داشته باشید.",
        "فناوری هر روز در حال پیشرفت است و ابزارهای جدیدی برای یادگیری ساخته می شوند. استفاده درست از این ابزارها می تواند مسیر پیشرفت شما را هموارتر کند.",
        "هدف گذاری درست اولین قدم برای رسیدن به موفقیت است. وقتی هدف شما مشخص باشد مسیر حرکت نیز روشن می شود. هر روز یک قدم کوچک بردارید و به خودتان اعتماد کنید."
    )

    private val faHard = listOf(
        "تمرکز، توانایی نگه داشتن ذهن روی یک کار مشخص است؛ بدون پرت شدن به سمت اعلان ها، پیام ها و حواس پرتی های روزمره. پژوهش ها نشان می دهند افرادی که تمرین تمرکز انجام می دهند، در کارهای شناختی عملکرد بهتری دارند و زودتر خسته می شوند.",
        "سرعت و دقت دو نیروی مخالف نیستند: اگر ابتدا دقت را بسازید، سرعت خودش می آید. آمارها می گویند بیشترین خطاهای تایپکاران در حروف نزدیک به هم رخ می دهد، پس تمرین هدفمند روی همان کلیدها بیشترین اثر را دارد.",
        "در تندخوانی، چشم به جای حرکت خط به خط، روی نقاط کلیدی جمله می ایستد و ذهن بقیه را بازسازی می کند. این مهارت با تمرین کوتاه اما متمرکز رشد می کند؛ نه با خواندن سریع و بی دقت."
    )

    // ---------------- typing: English ----------------

    private val enEasy = listOf(
        "Fast typing is a skill that grows with daily practice. Spend a few minutes every day to improve your speed and accuracy.",
        "Small work every day is better than big work once a week. The mind learns through calm and steady repetition.",
        "Touch typing means your fingers know the keys and you stop looking down. Start slow and keep your hands relaxed."
    )

    private val enMedium = listOf(
        "Speed reading helps you cover more information in less time. With steady practice you can raise your reading rate without losing comprehension, and the gain stays with you.",
        "Technology changes every day and new tools appear for learning. Using the right tools can make your path smoother, but only practice turns them into skill.",
        "Setting a clear goal is the first step to progress. When the target is defined, the road becomes visible. Take one small step each day and trust the process."
    )

    private val enHard = listOf(
        "Focus is the ability to keep attention on one task without drifting toward notifications, messages and the noise of the day. Research suggests that people who train attention perform better in cognitive work and tire more slowly.",
        "Accuracy and speed are not opposites: build accuracy first and speed arrives on its own. Statistics show most typing errors cluster around neighbouring keys, so targeted drills beat blind repetition every time.",
        "In rapid reading the eye stops jumping line by line and instead lands on key points of a sentence while the mind reconstructs the rest. That skill grows through short, deliberate practice, not through rushing."
    )

    fun typingPool(lang: String, level: Int): List<String> = when {
        lang == "fa" && level <= 1 -> faEasy
        lang == "fa" && level == 2 -> faMedium
        lang == "fa" -> faHard
        level <= 1 -> enEasy
        level == 2 -> enMedium
        else -> enHard
    }

    // ---------------- reading ----------------

    private val faReading = listOf(
        ReadingText(
            lang = "fa",
            title = "هنر تمرکز",
            body = "تمرکز یعنی توانایی نگه داشتن ذهن روی یک کار مشخص بدون پرت شدن به سمت حواس پرتی ها. " +
                "در دنیای امروز که هر لحظه اعلان جدیدی از گوشی می رسد، حفظ تمرکز سخت تر از همیشه شده است. " +
                "پژوهش ها نشان می دهند افرادی که تمرین تمرکز انجام می دهند در کارهای شناختی عملکرد بهتری دارند. " +
                "یکی از ساده ترین روش ها، تمرین تنفس عمیق و مدیتیشن کوتاه روزانه است. " +
                "حتی پنج دقیقه سکوت در روز می تواند کیفیت توجه شما را به شکل محسوسی بالا ببرد.",
            questions = listOf(
                QuizQuestion(
                    "طبق متن، تمرکز چه کمکی به عملکرد شناختی می کند؟",
                    listOf("هیچ اثری ندارد", "عملکرد را بهتر می کند", "فقط خستگی را زیاد می کند", "باعث خواب آلودگی می شود"),
                    1
                ),
                QuizQuestion(
                    "یکی از ساده ترین روش های تقویت تمرکز چیست؟",
                    listOf("تماشای بیشتر گوشی", "تنفس عمیق و مدیتیشن کوتاه", "کار همزمان چند کار", "شنیدن موسیقی بلند"),
                    1
                ),
                QuizQuestion(
                    "طبق متن چند دقیقه سکوت روزانه کافی است؟",
                    listOf("یک ساعت", "پنج دقیقه", "یک دقیقه", "لازم نیست"),
                    1
                )
            )
        ),
        ReadingText(
            lang = "fa",
            title = "عادت های کوچک",
            body = "تغییرهای بزرگ از عادت های کوچک شروع می شوند. اگر هر روز فقط یک درصد بهتر شوید، " +
                "در پایان سال حدود سی و هفت برابر بهتر خواهید بود. راز این موضوع در پیوستگی است، نه شدت. " +
                "بسیاری از ما برنامه های سخت می نویسیم و در هفته دوم رها می کنیم. " +
                "بهتر است برنامه ای انتخاب کنیم که در بدترین روزها هم بتوانیم انجامش دهیم. " +
                "عادت کوچک و پیوسته، آرام آرام بخشی از هویت ما می شود.",
            questions = listOf(
                QuizQuestion(
                    "راز اصلی تغییرهای بزرگ چیست؟",
                    listOf("شدت زیاد", "پیوستگی", "برنامه سخت", "انگیزه لحظه ای"),
                    1
                ),
                QuizQuestion(
                    "چرا برنامه های سخت شکست می خورند؟",
                    listOf("چون زیاد آسان اند", "چون در بدترین روزها رها می شوند", "چون کوتاه اند", "چون بی فایده اند"),
                    1
                ),
                QuizQuestion(
                    "عادت کوچک پیوسته در نهایت چه می شود؟",
                    listOf("بخشی از هویت ما", "فراموش می شود", "فقط یک عادت موقت", "باعث خستگی می شود"),
                    0
                )
            )
        ),
        ReadingText(
            lang = "fa",
            title = "سرعت و دقت",
            body = "در تایپ و تندخوانی، دقت پایه سرعت است. اگر ابتدا درست نوشتن را یاد بگیرید، " +
                "سرعت به شکل طبیعی بالا می رود؛ اما اگر فقط سرعت را تمرین کنید، خطاها هم سریع تر می شوند. " +
                "بهترین روش این است که هر جلسه کوتاه را با دقت بالا شروع کنید و فقط وقتی خطا کم شد سرعت را زیاد کنید. " +
                "ثبت رکورد روزانه کمک می کند پیشرفت واقعی را ببینید، نه احساس پیشرفت را. " +
                "عملکرد پیوسته مهم تر از یک روز استثنایی است.",
            questions = listOf(
                QuizQuestion(
                    "پایه و اساس سرعت چیست؟",
                    listOf("دقت", "استرس", "نگاه به کلیدها", "موسیقی"),
                    0
                ),
                QuizQuestion(
                    "چه زمانی باید سرعت را زیاد کرد؟",
                    listOf("از همان ابتدا", "وقتی خطا کم شد", "هرگز", "فقط در آزمون ها"),
                    1
                ),
                QuizQuestion(
                    "ثبت رکورد روزانه چه کمکی می کند؟",
                    listOf("پیشرفت واقعی دیده شود", "فقط سرگرمی", "خطا بیشتر شود", "هیچ کمکی نمی کند"),
                    0
                )
            )
        )
    )

    private val enReading = listOf(
        ReadingText(
            lang = "en",
            title = "The Focus Muscle",
            body = "Focus is the ability to keep attention on one task without drifting toward notifications. " +
                "In a world where the phone buzzes every minute, holding attention has become harder than ever. " +
                "Studies suggest people who train attention perform better in cognitive work and tire more slowly. " +
                "One of the simplest methods is a short daily breathing practice. " +
                "Even five quiet minutes can noticeably improve the quality of your attention.",
            questions = listOf(
                QuizQuestion(
                    "What does training attention improve?",
                    listOf("Nothing", "Cognitive performance", "Sleep only", "Appetite"),
                    1
                ),
                QuizQuestion(
                    "Which practice is described as simplest?",
                    listOf("Watching more video", "Short daily breathing", "Multitasking", "Loud music"),
                    1
                ),
                QuizQuestion(
                    "How long can a daily pause be?",
                    listOf("One hour", "Five minutes", "Ten seconds", "Not needed"),
                    1
                )
            )
        ),
        ReadingText(
            lang = "en",
            title = "Small Habits",
            body = "Big changes begin with small habits. If you improve by just one percent each day, " +
                "you will be about thirty seven times better by the end of the year. " +
                "The secret is consistency, not intensity. Many of us write hard plans and quit in week two. " +
                "It is better to choose a plan you can still do on your worst day. " +
                "A small, steady habit slowly becomes part of your identity.",
            questions = listOf(
                QuizQuestion(
                    "What is the real secret of big change?",
                    listOf("Intensity", "Consistency", "Hard plans", "Short bursts"),
                    1
                ),
                QuizQuestion(
                    "Why do hard plans fail?",
                    listOf("They are too easy", "They break on bad days", "They are too short", "They are useless"),
                    1
                ),
                QuizQuestion(
                    "A steady small habit eventually becomes…",
                    listOf("Part of your identity", "Forgotten", "Temporary", "A burden"),
                    0
                )
            )
        ),
        ReadingText(
            lang = "en",
            title = "Accuracy First",
            body = "In typing and reading, accuracy is the base of speed. Learn to write correctly first " +
                "and speed increases naturally; train speed alone and mistakes simply get faster. " +
                "The best routine is to start every short session with high accuracy and only raise the pace " +
                "once errors drop. Logging a daily record shows real progress rather than the feeling of it. " +
                "Consistency beats one exceptional day.",
            questions = listOf(
                QuizQuestion(
                    "What is the base of speed?",
                    listOf("Accuracy", "Stress", "Looking at keys", "Music"),
                    0
                ),
                QuizQuestion(
                    "When should you increase the pace?",
                    listOf("Immediately", "When errors drop", "Never", "Only in tests"),
                    1
                ),
                QuizQuestion(
                    "What does a daily record show?",
                    listOf("Real progress", "Nothing useful", "More errors", "Only fun"),
                    0
                )
            )
        )
    )

    val reading: List<ReadingText> = faReading + enReading

    fun readingPool(lang: String): List<ReadingText> =
        reading.filter { it.lang == lang }.ifEmpty { reading }

    /** One-line focus quotes shown on the dashboard. */
    val quotesFa = listOf(
        "دقت اول، سرعت بعد.",
        "ده دقیقه امروز بهتر از یک ساعت فردا است.",
        "خطاها نقشه راه تمرین هستند.",
        "سرعت از تکرار درست می آید.",
        "چشم را روی نقطه، ذهن را روی معنا."
    )

    val quotesEn = listOf(
        "Accuracy first, speed later.",
        "Ten minutes today beats an hour tomorrow.",
        "Errors are the training map.",
        "Speed comes from correct repetition.",
        "Eye on the point, mind on the meaning."
    )
}
