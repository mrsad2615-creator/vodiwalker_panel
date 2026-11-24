package com.neurox.domain

data class Passage(val title: String, val text: String, val question: String, val options: List<String>, val correctOption: Int)

/** Original bundled material. No remote content and no health claims. */
object Content {
    fun passages(language: Language): List<Passage> = if (language == Language.FA) listOf(
        Passage("باغ روی بام", "لیلا روی بام خانه یک باغ کوچک ساخت. او گلدان‌ها را کنار دیوار شرقی گذاشت تا صبح آفتاب بگیرند. برای کاهش مصرف آب، خاک را با برگ‌های خشک پوشاند. پس از چند هفته، نخستین برگ‌های ریحان آماده برداشت شد.", "لیلا چرا گلدان‌ها را کنار دیوار شرقی گذاشت؟", listOf("برای دریافت آفتاب صبح", "برای دوری از باران", "برای پنهان کردن گلدان‌ها"), 0),
        Passage("ساعت ایستگاه", "ساعت قدیمی ایستگاه هر روز دو دقیقه عقب می‌ماند. آرمان پیش از آغاز شیفت، آن را با ساعت دقیق تنظیم می‌کرد. یک روز تصمیم گرفت مشکل را بررسی کند. او دریافت که گردوغبار حرکت چرخ‌دنده‌ها را کند کرده است.", "علت کندی ساعت چه بود؟", listOf("سرمای هوا", "گردوغبار در چرخ‌دنده‌ها", "باتری تلفن آرمان"), 1),
        Passage("راه کتابخانه", "کتابخانه محله روزهای پنجشنبه یک ساعت زودتر باز می‌شود. سارا آن روزها پیاده به کتابخانه می‌رود. مسیر کنار رودخانه کمی طولانی‌تر است، اما او آن را انتخاب می‌کند چون آرام‌تر است. او معمولاً یک کتاب تاریخ به امانت می‌گیرد.", "چرا سارا مسیر کنار رودخانه را انتخاب می‌کند؟", listOf("کوتاه‌تر است", "کتابخانه در آن مسیر است", "آرام‌تر است"), 2),
    ) else listOf(
        Passage("A rooftop garden", "Leila built a small garden on the roof. She placed the pots near the east wall so they would receive morning sunlight. To reduce water use, she covered the soil with dry leaves. After several weeks, the first basil leaves were ready to pick.", "Why did Leila choose the east wall?", listOf("To receive morning sunlight", "To avoid all rain", "To hide the pots"), 0),
        Passage("The station clock", "The old station clock lost two minutes each day. Before his shift, Arman reset it to the correct time. One day he inspected the mechanism. Dust had collected between the gears and slowed their movement. He cleaned the gears before testing the clock again.", "What slowed the clock?", listOf("Cold air", "Dust between the gears", "A phone battery"), 1),
        Passage("The library route", "The local library opens an hour early on Thursdays. Sara walks there on those mornings. The path beside the river takes a little longer, but she prefers it because it is quiet. She usually borrows a history book and reads a chapter before returning home.", "Why does Sara prefer the riverside path?", listOf("It is shorter", "The library moved there", "It is quieter"), 2),
    )
    fun typing(language: Language): List<String> = if (language == Language.FA) listOf(
        "هر روز فرصتی برای یادگیری است.", "با دقت بنویس و سپس سرعت را بیشتر کن.", "تمرین کوتاه و منظم ارزشمند است."
    ) else listOf(
        "Each day is a chance to learn.", "Write with care, then build your speed.", "Short and regular practice has value."
    )
    fun code(language: Language, difficulty: Int, round: Int): String {
        require(difficulty in 1..5)
        val greeting = if (language == Language.FA) "سلام" else "Hello"
        val examples = listOf(
            "val count = ${round + 1}",
            "val message = \"$greeting\"\nprintln(message)",
            "fun square(n: Int): Int {\n    return n * n\n}",
            "val values = listOf(1, 2, 3)\nval total = values.sum()\nprintln(total)",
            "fun greet(name: String): String {\n    val prefix = \"$greeting\"\n    return prefix + \" \" + name\n}"
        )
        return examples[difficulty - 1]
    }

}

data class ExerciseDefinition(val exercise: Exercise, val en: String, val fa: String, val instructionEn: String, val instructionFa: String)
object Catalogue {
    val runnable = listOf(
        ExerciseDefinition(Exercise.FOCUS_LOCK, "Focus Lock", "قفل تمرکز", "Find and tap the marked cell. Each missed target counts as an error.", "خانهٔ نشانه‌دار را پیدا و لمس کنید. هر هدف ازدست‌رفته یک خطا است."),
        ExerciseDefinition(Exercise.QUICK_TAP, "Quick Tap", "واکنش سریع", "Wait for GO. Then tap. Early taps count as errors. Screen and touch latency affect results.", "تا نمایش «بزن» صبر کنید، سپس لمس کنید. لمس زودهنگام خطا است. تأخیر نمایشگر و لمس روی نتیجه اثر دارد."),
        ExerciseDefinition(Exercise.COMPREHENSION, "Reading Comprehension", "درک مطلب", "Read each passage. Open the question when ready. Speed and comprehension are shown separately.", "متن را بخوانید و وقتی آماده بودید پرسش را باز کنید. سرعت و درک مطلب جداگانه گزارش می‌شوند."),
        ExerciseDefinition(Exercise.KEY_PRECISION, "Key Precision", "دقت تایپ", "Type the full sentence, then submit. Spaces and punctuation count. WPM = correct characters ÷ 5 ÷ minutes.", "جمله را کامل بنویسید و ثبت کنید. فاصله و نشانه‌ها شمرده می‌شوند. سرعت برابر نویسهٔ درست تقسیم بر ۵ تقسیم بر دقیقه است."),
        ExerciseDefinition(Exercise.VISUAL_SEARCH, "Visual Search", "جست‌وجوی دیداری", "Find the upright T among rotated T and L shapes. Tap its cell before time expires.", "حرف T ایستاده را میان T و L چرخیده پیدا کنید و پیش از پایان زمان خانهٔ آن را لمس کنید."),
        ExerciseDefinition(Exercise.GO_NO_GO, "Go / No-Go Challenge", "چالش برو / نرو", "Wait for a signal. Tap GO / circle. Do not tap NO-GO / cross. A full no-tap window counts as correct inhibition, not reaction time.", "منتظر نشانه بمانید. برای «بزن / دایره» لمس کنید. برای «نزن / ضربدر» تا پایان زمان لمس نکنید. خودداری درست جدا از زمان واکنش ثبت می‌شود."),
        ExerciseDefinition(Exercise.PHRASE_READING, "Phrase Reading", "خواندن عبارت", "Read each timed three-word phrase. Answer the comprehension question after the last phrase. Higher levels increase presentation pace. The reported WPM uses actual display time.", "هر عبارت سه‌واژه‌ای را بخوانید. پس از آخرین عبارت به پرسش درک مطلب پاسخ دهید. سطح بالاتر نمایش سریع‌تری دارد. سرعت گزارش‌شده بر پایهٔ زمان واقعی نمایش است."),
        ExerciseDefinition(Exercise.WORD_SPRINT, "Word Sprint", "تایپ واژه", "Type and submit each word separately. Finish every word to complete a round. More difficult rounds contain more words. All active round time counts toward WPM.", "هر واژه را جداگانه تایپ و ثبت کنید. برای پایان دور، همهٔ واژه‌ها را بنویسید. دور دشوارتر واژه‌های بیشتری دارد. تمام زمان فعال دور در محاسبهٔ سرعت حساب می‌شود."),
        ExerciseDefinition(Exercise.PATTERN_MATCH, "Pattern Match", "تطبیق الگو", "Compare the sample with four patterns. Choose the exact match. Filled-cell counts are equal in all options.", "الگوی نمونه را با چهار گزینه مقایسه کنید و نمونهٔ دقیق را انتخاب کنید. تعداد خانه‌های پُر در همهٔ گزینه‌ها برابر است."),
        ExerciseDefinition(Exercise.SHAPE_REACTION, "Shape Reaction", "واکنش به شکل", "Wait for a shape. Choose the matching circle, triangle or square. Early answers and wrong shapes count as errors.", "منتظر شکل بمانید. دایره، مثلث یا مربعِ مطابق را انتخاب کنید. پاسخ زودهنگام و انتخاب شکل نادرست خطا است."),
        ExerciseDefinition(Exercise.RSVP_READER, "RSVP Reader", "خواندن تک‌واژه", "Read one word at a time. After the full passage, answer a comprehension question. Actual display time sets the reading-speed metric.", "هر بار یک واژه را بخوانید. پس از پایان متن به پرسش درک مطلب پاسخ دهید. سرعت خواندن بر پایهٔ زمان واقعی نمایش است."),
        ExerciseDefinition(Exercise.CODE_TYPING, "Code Typing", "تایپ کد", "Copy the code exactly, including spaces and newlines. No code is executed. Higher levels use longer snippets.", "کد را با فاصله‌ها و خط‌های جدید دقیق بنویسید. هیچ کدی اجرا نمی‌شود. سطح بالاتر قطعهٔ طولانی‌تری دارد."),
    )
    val primary = Module.entries.map { module -> runnable.first { it.exercise.module == module } }
    fun get(exercise: Exercise) = runnable.first { it.exercise == exercise }
}
