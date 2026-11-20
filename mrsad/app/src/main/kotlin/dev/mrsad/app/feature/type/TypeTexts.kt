package dev.mrsad.app.feature.type

import dev.mrsad.app.data.settings.GraphicsQuality

/**
 * Curated local training corpus. All texts are original Persian prose written
 * for MRSAD (no copyrighted material) and are selected by measurable criteria,
 * not randomly: a difficulty tier and a focus (letters / words / rhythm).
 */
data class PracticeText(
    val id: String,
    val title: String,
    val body: String,
    val level: Int, // 1 = easy … 3 = hard
)

object TypeTexts {

    val persian: List<PracticeText> = listOf(
        PracticeText(
            id = "fa-basic-1",
            title = "آغاز روشن",
            level = 1,
            body = "هر تمرین کوچک، یک قدم به جلو است. دست‌ها بر صفحه می‌نشینند و ذهن آرام می‌شود. " +
                "امروز فقط یک دقیقه تمرین کن و نتیجه را با دیروز مقایسه کن.",
        ),
        PracticeText(
            id = "fa-basic-2",
            title = "ریتم روزانه",
            level = 1,
            body = "تمرین روزانه از تمرین طولانی مهم‌تر است. پنج دقیقه در هر روز، در یک ماه یک عادت می‌سازد. " +
                "سرعت با تکرار می‌آید و دقت با توجه.",
        ),
        PracticeText(
            id = "fa-mid-1",
            title = "مسیر یادگیری",
            level = 2,
            body = "یادگیری مهارت شبیه ساختن راه است؛ هر قدم که برمی‌داری، مسیر روشن‌تر می‌شود. " +
                "گاهی سرعت کم می‌شود، اما توقف لازم نیست. کافی است دوباره شروع کنی و به دقتِ خود اعتماد کنی.",
        ),
        PracticeText(
            id = "fa-mid-2",
            title = "تمرکز عمیق",
            level = 2,
            body = "تمرکز عمیق زمانی شکل می‌گیرد که حواس‌پرتی را آگاهانه کنار بگذاری. " +
                "پیش از شروع، هدف را مشخص کن؛ در طول تمرین، فقط به حرکت انگشتان و ریتم جمله‌ها توجه کن. " +
                "پس از پایان، یک نفس عمیق بکش و نتیجه را ثبت کن.",
        ),
        PracticeText(
            id = "fa-hard-1",
            title = "نیم‌فاصله و ترکیب‌ها",
            level = 3,
            body = "می‌روم، می‌آیم، می‌بینم و بازمی‌گردم؛ نیم‌فاصله‌ها قلب نوشتار فارسی هستند. " +
                "واژه‌هایی مانند خانه‌ها، کتاب‌ها و دوستانم نشان می‌دهند که پیوستگی حروف، معنا را کامل می‌کند. " +
                "در تایپ فارسی، دقت در همین جزئیات، تفاوت اصلی است.",
        ),
        PracticeText(
            id = "fa-hard-2",
            title = "دقت در جزئیات",
            level = 3,
            body = "زبان فارسی با ظرافت‌هایش شناخته می‌شود: ژ و چ و پ و گ، حروفی که در بسیاری از زبان‌ها نیستند. " +
                "وقتی انگشتان این حروف را می‌شناسند، سرعت بدون از دست رفتن دقت بالا می‌رود. " +
                "هدف، تایپ بی‌خطا با ریتمی پایدار است.",
        ),
        PracticeText(
            id = "fa-basic-3",
            title = "گام کوچک",
            level = 1,
            body = "امروز فقط یک خط بنویس. نه بیشتر، نه کمتر. " +
                "یک خط که بی‌نقص تمام شود، بیشتر از ده خطِ رهاشده ارزش دارد. " +
                "فردا دوباره برگرد و همان یک خط را تکرار کن.",
        ),
        PracticeText(
            id = "fa-mid-3",
            title = "دقت زیر فشار",
            level = 2,
            body = "تایپ سریع وقتی ارزشمند است که زیر فشار هم دقت را نگه دارد. " +
                "در تمرین‌های زمان‌دار، ذهن به دنبال راه‌های کوتاه می‌گردد و همین‌جاست که چند خطای کوچک ردیف می‌شود. " +
                "برای همین، اول با سرعت کم شروع کن تا دست‌ها راه را پیدا کنند، بعد فشار را بالا ببر.",
        ),
        PracticeText(
            id = "fa-hard-3",
            title = "جمله‌های بلند",
            level = 3,
            body = "جملهٔ بلند، آزمون واقعی ریتم است؛ چون ذهن در میانهٔ راه باید پیوند میان دو نیم‌جمله را نگه دارد. " +
                "هنگام تایپ، نقطهٔ مکث را پیش از رسیدن به آن حدس بزنید و آهسته‌تر از بخش دیگر پیش بروید. " +
                "اگر نیم‌جملهٔ اول را رها کنید، نیم‌جملهٔ دوم را هم گم می‌کنید.",
        ),
    )

    val english: List<PracticeText> = listOf(
        PracticeText(
            id = "en-basic-1",
            title = "Warm up",
            level = 1,
            body = "Small steps every day build real skill. Sit straight, relax your hands, " +
                "and let your eyes follow the text instead of the keyboard.",
        ),
        PracticeText(
            id = "en-basic-2",
            title = "Home row",
            level = 1,
            body = "The home row is where accuracy begins. Place your fingers on the keys, " +
                "keep your wrists light, and return to the home position after every reach.",
        ),
        PracticeText(
            id = "en-mid-1",
            title = "Steady rhythm",
            level = 2,
            body = "Speed is a by-product of accuracy. When your fingers learn the most common " +
                "letter combinations, whole words begin to flow as single motions.",
        ),
        PracticeText(
            id = "en-mid-2",
            title = "Deliberate practice",
            level = 2,
            body = "Deliberate practice means working just beyond your comfort zone. " +
                "Measure what is weak, train that part specifically, and keep the rest of your routine steady.",
        ),
        PracticeText(
            id = "en-hard-1",
            title = "Punctuation flow",
            level = 3,
            body = "Punctuation is part of rhythm: commas pause, periods land, and semicolons connect; " +
                "parentheses quietly hold (extra detail) without breaking the sentence's flow.",
        ),
        PracticeText(
            id = "en-basic-3",
            title = "One line",
            level = 1,
            body = "Type one clean line today. Not ten lines, not five — one. " +
                "A single line finished without a mistake is worth more than ten abandoned ones.",
        ),
        PracticeText(
            id = "en-mid-3",
            title = "Under pressure",
            level = 2,
            body = "Speed only counts when accuracy survives it. " +
                "In timed drills the mind looks for shortcuts, and that is exactly where small errors line up. " +
                "Start slower until your hands know the path, then add pressure.",
        ),
        PracticeText(
            id = "en-hard-2",
            title = "Long sentences",
            level = 3,
            body = "A long sentence is the real test of rhythm, because the mind has to hold the join " +
                "between two halves while the fingers keep moving. " +
                "Anticipate the break before you reach it and slow down on the way in.",
        ),
    )

    fun forLanguage(usePersian: Boolean): List<PracticeText> = if (usePersian) persian else english

    /** Deterministic daily pick so "today's text" is stable across app restarts. */
    fun dailyPick(usePersian: Boolean, dateIso: String, level: Int = 0): PracticeText {
        val pool = forLanguage(usePersian).let { all ->
            if (level <= 0) all else all.filter { it.level == level }.ifEmpty { all }
        }
        val seed = dev.mrsad.core.random.SplitMix64.seedFor("type:$usePersian:$dateIso:$level")
        val rng = dev.mrsad.core.random.SplitMix64(seed)
        return pool[rng.nextInt(pool.size)]
    }
}

/** Session duration presets, in seconds. */
val durationPresets = listOf(30, 60, 120, 180)

/** Typing visual quality helper (recommendation for effects budget). */
fun recommendedCursorGlow(quality: GraphicsQuality): Float = when (quality) {
    GraphicsQuality.HIGH -> 1f
    GraphicsQuality.BALANCED -> 0.6f
    GraphicsQuality.PERFORMANCE -> 0f
}
