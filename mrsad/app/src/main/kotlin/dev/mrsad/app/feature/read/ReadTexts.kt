package dev.mrsad.app.feature.read

import dev.mrsad.core.reading.ReadingEngine

/**
 * Local reading corpus with comprehension questions authored for MRSAD.
 * Each text has four questions whose answers are supported by the text itself —
 * no trivia, no confusion, and no copyrighted material.
 */
data class ReadText(
    val id: String,
    val title: String,
    val category: String,
    val body: String,
    val questions: List<ReadingEngine.ComprehensionQuestion>,
) {
    val wordCount: Int get() = dev.mrsad.core.text.PersianText.wordCount(body)
}

object ReadTexts {

    val all: List<ReadText> = listOf(
        ReadText(
            id = "read-flow",
            title = "جریان آرام",
            category = "تمرکز",
            body = "خواندن سریع به معنای دیدن کلمات بیشتر نیست؛ به معنای ساختن معنا در زمان کمتر است. " +
                "وقتی چشم‌ها در یک نقطه ثابت می‌مانند و متن به‌صورت پیوسته نمایش داده می‌شود، ذهن فرصت ندارد " +
                "به عقب برگردد و این کار باعث می‌شود تمرکز روی کلمهٔ بعدی بیشتر شود. " +
                "در این روش که نمایش سریال سریع نامیده می‌شود، هر کلمه برای مدت کوتاهی در مرکز دید ظاهر می‌شود. " +
                "مغز ما عادت دارد هنگام خواندن معمولی چند بار در خط حرکت کند و هر بار کمی از تمرکز خود را از دست بدهد. " +
                "نمایش پیوسته این حرکت‌های اضافه را حذف می‌کند. اما نکتهٔ مهم این است که سرعت بدون فهم، فقط یک عدد است. " +
                "اگر پس از خواندن نتوانیم دربارهٔ متن حرف بزنیم، سرعت بالا هیچ ارزشی ندارد. " +
                "به همین دلیل، هر جلسهٔ خواندن در مرصاد با چند پرسش کوتاه همراه است.",
            questions = listOf(
                ReadingEngine.ComprehensionQuestion(
                    prompt = "طبق متن، خواندن سریع به چه معناست؟",
                    options = listOf("دیدن کلمات بیشتر", "ساختن معنا در زمان کمتر", "حفظ کردن متن"),
                    correctIndex = 1,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "در خواندن معمولی، مغز چه کاری انجام می‌دهد؟",
                    options = listOf("در خط چند بار حرکت می‌کند", "کمتر درک می‌کند", "به مرکز دید تکیه می‌کند"),
                    correctIndex = 0,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "طبق متن، سرعت بدون فهم چه ارزشی دارد؟",
                    options = listOf("ارزش زیادی", "فقط یک عدد است", "ارزش متوسط"),
                    correctIndex = 1,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "هر جلسهٔ خواندن در مرصاد با چه چیزی همراه است؟",
                    options = listOf("چند پرسش کوتاه", "موسیقی پس‌زمینه", "تمرین تایپ"),
                    correctIndex = 0,
                ),
            ),
        ),
        ReadText(
            id = "read-memory",
            title = "حافظهٔ کاری",
            category = "شناخت",
            body = "حافظهٔ کاری بخشی از ذهن است که اطلاعات را برای مدت کوتاهی نگه می‌دارد تا بتوانیم با آن کار کنیم. " +
                "همان وقتی که یک شمارهٔ تلفن را در ذهن نگه می‌داریم تا آن را بنویسیم، از حافظهٔ کاری استفاده می‌کنیم. " +
                "ظرفیت این حافظه محدود است و به همین دلیل، وقتی هم‌زمان چند کار انجام می‌دهیم، کیفیت کارها پایین می‌آید. " +
                "پژوهش‌ها نشان می‌دهند که تمرین‌های کوتاه و منظم می‌توانند به تمرکز بهتر کمک کنند. " +
                "مهم این است که تمرین‌ها متناسب با توانایی فعلی باشند؛ تمرین بیش از حد سخت یا بیش از حد آسان " +
                "به سرعت خسته‌کننده می‌شود. سیستم تمرینی مرصاد سطح تمرین را با توجه به نتایج قبلی تنظیم می‌کند " +
                "تا هر جلسه کمی چالش‌برانگیز باشد. ",
            questions = listOf(
                ReadingEngine.ComprehensionQuestion(
                    prompt = "حافظهٔ کاری چه کاری انجام می‌دهد؟",
                    options = listOf("اطلاعات را برای زمان کوتاهی نگه می‌دارد", "اطلاعات را برای همیشه ذخیره می‌کند", "حواس را تقویت می‌کند"),
                    correctIndex = 0,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "ظرفیت حافظهٔ کاری چگونه است؟",
                    options = listOf("بی‌نهایت", "محدود", "متغیر و نامحدود"),
                    correctIndex = 1,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "چرا انجام هم‌زمان چند کار کیفیت را پایین می‌آورد؟",
                    options = listOf("چون ظرفیت حافظهٔ کاری محدود است", "چون مغز استراحت می‌کند", "چون تمرین کافی نیست"),
                    correctIndex = 0,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "مرصاد سطح تمرین را چگونه تنظیم می‌کند؟",
                    options = listOf("تصادفی", "همیشه سخت", "با توجه به نتایج قبلی"),
                    correctIndex = 2,
                ),
            ),
        ),
        ReadText(
            id = "read-habit",
            title = "قانون عادت",
            category = "رشد",
            body = "عادت‌ها با تکرار ساخته می‌شوند، نه با تصمیم. " +
                "بسیاری از افراد تصمیم می‌گیرند از فردا تمرین کنند، اما چند روز بعد متوقف می‌شوند. " +
                "دلیل این توقف معمولاً نبود انگیزه نیست؛ نبود زمان مشخص و کوتاه‌بودن شروع است. " +
                "تحقیقات رفتاری نشان داده‌اند که تمرین‌های کوچک و قابل انجام، بیشتر از برنامه‌های بلند دوام می‌آورند. " +
                "وقتی تمرین پنج دقیقه‌ای را هر روز در ساعت مشخصی انجام می‌دهیم، مغز آن را به بخشی از روال روز تبدیل می‌کند. " +
                "پس از چند هفته، انجام ندادن تمرین حس ناخوشایندی ایجاد می‌کند و این خودش نشانهٔ شکل‌گیری عادت است. " +
                "راز پیشرفت در ثبت و دیدن پیشرفت است؛ وقتی نتیجه را می‌بینیم، ادامه دادن آسان‌تر می‌شود.",
            questions = listOf(
                ReadingEngine.ComprehensionQuestion(
                    prompt = "عادت‌ها چگونه ساخته می‌شوند؟",
                    options = listOf("با انگیزهٔ لحظه‌ای", "با تکرار", "با خواندن کتاب"),
                    correctIndex = 1,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "دلیل متوقف شدن افراد اغلب چیست؟",
                    options = listOf("کمبود انگیزه", "نبود زمان مشخص و طولانی‌بودن شروع", "نداشتن هدف"),
                    correctIndex = 1,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "کدام تمرین‌ها دوام بیشتری دارند؟",
                    options = listOf("برنامه‌های بلند", "تمرین‌های کوچک روزانه", "تمرین‌های آخر هفته"),
                    correctIndex = 1,
                ),
                ReadingEngine.ComprehensionQuestion(
                    prompt = "نشانهٔ شکل‌گیری عادت طبق متن چیست؟",
                    options = listOf("حس ناخوشایند در انجام ندادن تمرین", "انگیزهٔ زیاد", "سرعت بیشتر"),
                    correctIndex = 0,
                ),
            ),
        ),
    )

    fun byId(id: String): ReadText = all.firstOrNull { it.id == id } ?: all.first()
}
