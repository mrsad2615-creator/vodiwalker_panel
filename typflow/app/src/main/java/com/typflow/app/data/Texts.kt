package com.typflow.app.data

data class QuizQuestion(val question: String, val options: List<String>, val answerIndex: Int)

data class ReadingText(
    val title: String,
    val body: String,
    val questions: List<QuizQuestion>
)

/**
 * Built-in offline practice content — Persian & English.
 */
object Texts {

    val typingFa: List<String> = listOf(
        "تایپ سریع مهارتی است که با تمرین روزانه به دست می آید. هر روز چند دقیقه تمرین کنید تا سرعت و دقت شما بیشتر شود. تمرکز و آرامش دو عامل مهم در یادگیری تایپ هستند.",
        "خواندن سریع به شما کمک می کند در زمان کمتر اطلاعات بیشتری کسب کنید. با تمرین مداوم می توانید سرعت مطالعه خود را به طرز چشمگیری افزایش دهید و درک مطلب بهتری داشته باشید.",
        "فناوری هر روز در حال پیشرفت است و ابزارهای جدیدی برای یادگیری ساخته می شوند. استفاده درست از این ابزارها می تواند مسیر پیشرفت شما را بسیار هموارتر کند و زمان را برای شما ذخیره کند.",
        "هدف گذاری درست اولین قدم برای رسیدن به موفقیت است. وقتی هدف شما مشخص باشد مسیر حرکت نیز روشن می شود. هر روز یک قدم کوچک بردارید و به خودتان اعتماد کنید."
    )

    val typingEn: List<String> = listOf(
        "Fast typing is a skill that grows with daily practice. Spend a few minutes every day to improve your speed and accuracy. Focus and calm are the two keys to learning.",
        "Speed reading helps you gain more information in less time. With steady practice you can dramatically increase your reading speed and improve your comprehension.",
        "Technology improves every single day and new learning tools appear. Using these tools wisely can make your path much smoother and save you a lot of time.",
        "Setting clear goals is the first step toward success. When your goal is clear the path becomes visible. Take one small step every day and trust yourself."
    )

    val readingFa: List<ReadingText> = listOf(
        ReadingText(
            title = "هنر تمرکز",
            body = "تمرکز یعنی توانایی نگه داشتن ذهن روی یک کار مشخص بدون پرت شدن به سمت حواس پرتی ها. در دنیای امروز که هر لحظه اعلان جدیدی از گوشی و شبکه های اجتماعی می رسد حفظ تمرکز سخت تر از همیشه شده است. پژوهش ها نشان می دهند افرادی که تمرین تمرکز انجام می دهند در کارهای شناختی عملکرد بهتری دارند. یکی از ساده ترین روش ها تمرین تنفس عمیق و مدیتیشن کوتاه روزانه است. حتی پنج دقیقه سکوت در روز می تواند کیفیت توجه شما را به شکل محسوسی بالا ببرد و خستگی ذهنی را کم کند.",
            questions = listOf(
                QuizQuestion(
                    "طبق متن تمرکز چه کمکی به عملکرد شناختی می کند؟",
                    listOf("هیچ اثری ندارد", "عملکرد را بهتر می کند", "فقط خستگی را زیاد می کند", "باعث خواب آلودگی می شود"),
                    1
                ),
                QuizQuestion(
                    "یکی از روش های ساده تقویت تمرکز چیست؟",
                    listOf("تماشای بیشتر گوشی", "تنفس عمیق و مدیتیشن کوتاه", "کار کردن همزمان چند کار", "شنیدن موسیقی بلند"),
                    1
                ),
                QuizQuestion(
                    "چند دقیقه سکوت در روز طبق متن کافی است؟",
                    listOf("یک ساعت", "پنج دقیقه", "یک دقیقه", "اصلاً لازم نیست"),
                    1
                )
            )
        ),
        ReadingText(
            title = "قدرت عادت های کوچک",
            body = "موفقیت معمولاً نتیجه یک تصمیم بزرگ نیست بلکه حاصل عادت های کوچک و تکرارشونده روزانه است. وقتی کاری را هر روز در ساعت مشخصی انجام می دهید مغز آن را به صورت خودکار درمی آورد و انرژی ذهنی کمتری مصرف می شود. برای ساختن یک عادت جدید اول آن را بسیار کوچک کنید تا شروع کردن آسان شود. سپس به تدریج اندازه آن را بزرگ تر کنید. مهم ترین نکته استمرار است نه شدت. کسی که هر روز کمی پیش می رود در پایان سال از کسی که گاهی خیلی زیاد تلاش می کند جلو می زند.",
            questions = listOf(
                QuizQuestion(
                    "موفقیت طبق متن بیشتر نتیجه چیست؟",
                    listOf("یک تصمیم بزرگ", "عادت های کوچک روزانه", "شانس", "کمک دیگران"),
                    1
                ),
                QuizQuestion(
                    "برای ساختن عادت جدید اول باید چه کرد؟",
                    listOf("آن را خیلی بزرگ شروع کرد", "آن را بسیار کوچک کرد", "صبر کرد تا انگیزه بیاید", "همه کارها را یکجا انجام داد"),
                    1
                ),
                QuizQuestion(
                    "مهم ترین نکته در ساختن عادت چیست؟",
                    listOf("شدت", "استمرار", "سرعت", "هزینه"),
                    1
                )
            )
        )
    )

    val readingEn: List<ReadingText> = listOf(
        ReadingText(
            title = "The Art of Focus",
            body = "Focus is the ability to keep your mind on one task without drifting toward distractions. In a world where a new notification arrives every moment keeping focus is harder than ever. Research shows that people who practice focus perform better in cognitive tasks. One of the simplest methods is deep breathing and short daily meditation. Even five minutes of silence a day can noticeably improve the quality of your attention and reduce mental fatigue.",
            questions = listOf(
                QuizQuestion(
                    "What does focus practice improve?",
                    listOf("Nothing", "Cognitive performance", "Only fatigue", "Sleepiness"),
                    1
                ),
                QuizQuestion(
                    "Which is a simple way to strengthen focus?",
                    listOf("More phone time", "Deep breathing and short meditation", "Multitasking", "Loud music"),
                    1
                ),
                QuizQuestion(
                    "How much silence per day is enough?",
                    listOf("One hour", "Five minutes", "One minute", "None"),
                    1
                )
            )
        ),
        ReadingText(
            title = "The Power of Small Habits",
            body = "Success is usually not the result of one big decision but of small repeated daily habits. When you do something at the same time every day your brain turns it into an automatic routine and uses less mental energy. To build a new habit first make it very small so starting is easy. Then gradually make it bigger. The most important thing is consistency not intensity. Someone who moves a little every day will pass someone who tries very hard only sometimes.",
            questions = listOf(
                QuizQuestion(
                    "Success is mostly the result of what?",
                    listOf("One big decision", "Small daily habits", "Luck", "Other people"),
                    1
                ),
                QuizQuestion(
                    "To build a new habit you should first:",
                    listOf("Start very big", "Make it very small", "Wait for motivation", "Do everything at once"),
                    1
                ),
                QuizQuestion(
                    "The most important thing is:",
                    listOf("Intensity", "Consistency", "Speed", "Cost"),
                    1
                )
            )
        )
    )
}
