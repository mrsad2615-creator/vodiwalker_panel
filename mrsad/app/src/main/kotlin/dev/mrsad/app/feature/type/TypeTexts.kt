package dev.mrsad.app.feature.type

import dev.mrsad.core.random.SplitMix64
import dev.mrsad.core.text.PersianText
import dev.mrsad.core.typing.TypingLanguage

data class PracticeText(
    val id: String,
    val language: TypingLanguage,
    val level: Int,
    val title: String,
    val body: String,
    val categoryFa: String = "عمومی",
    val targetWpm: Int = 35,
)

/**
 * Authoritative bilingual typing corpus organized across 5 progressive
 * ergonomic & linguistic difficulty tiers (ISIRI 9147 standard layout for
 * Persian; ANSI QWERTY for English).
 */
object TypeTexts {

    val persian: List<PracticeText> = listOf(
        PracticeText(
            id = "fa-1",
            language = TypingLanguage.PERSIAN,
            level = 1,
            categoryFa = "ردیف میانی (Home Row)",
            targetWpm = 25,
            title = "تثبیت ردیف میانی و تعادل دو دست",
            body = "شسیب نمکگ شسیب نمکگ تنفس آرام و استقرار انگشتان روی ردیف میانی کیبورد استاندارد فارسی پایهٔ اصلی سرعت پایدار است. هر کلید را با کمترین جابه‌جایی مچ بزنید و بلافاصله به خانهٔ اصلی بازگردید.",
        ),
        PracticeText(
            id = "fa-2",
            language = TypingLanguage.PERSIAN,
            level = 1,
            categoryFa = "واژگان پایه و ریتم یکنواخت",
            targetWpm = 30,
            title = "ریتم پیوسته بدون نگاه به صفحه‌کلید",
            body = "تمرین روزانه و کوتاه، بسیار مؤثرتر از تمرین‌های سنگین و پراکنده است. حافظهٔ حرکتی زمانی شکل می‌گیرد که چشم فقط به متن روی صفحه نگاه کند و انگشتان مسیر هر حرف را به صورت خودکار پیدا کنند.",
        ),
        PracticeText(
            id = "fa-3",
            language = TypingLanguage.PERSIAN,
            level = 2,
            categoryFa = "روانشناسی شناختی",
            targetWpm = 38,
            title = "معماری توجه و کنترل شناختی",
            body = "تمرکز عمیق یک مهارت اکتسابی است که با حذف عوامل مزاحم و تمرین آگاهانه تقویت می‌شود. زمانی که ذهن تنها بر یک تکلیف مشخص متمرکز می‌ماند، کیفیت پردازش اطلاعات افزایش یافته و خستگی شناختی به حداقل می‌رسد.",
        ),
        PracticeText(
            id = "fa-4",
            language = TypingLanguage.PERSIAN,
            level = 2,
            categoryFa = "علم تمرین و یادگیری حرکتی",
            targetWpm = 42,
            title = "تقدم دقت بر سرعت خام",
            body = "در یادگیری مهارت‌های حرکتی ظریف، دقت همیشه مقدم بر سرعت است. هر خطای تایپی نه تنها زمان اصلاح را تحمیل می‌کند بلکه الگوی عصبی نادرستی را در حافظهٔ عضلانی ثبت می‌نماید؛ پس ابتدا بی‌نقص بنویسید تا سرعت به طور طبیعی رشد کند.",
        ),
        PracticeText(
            id = "fa-5",
            language = TypingLanguage.PERSIAN,
            level = 3,
            categoryFa = "نیم‌فاصله و نگارش معیار",
            targetWpm = 45,
            title = "کاربرد دقیق نیم‌فاصله در خط فارسی",
            body = "رعایت نیم‌فاصله در واژه‌هایی مانند می‌روم، كتاب‌ها، برنامه‌نویسی، آینده‌نگری و پژوهش‌های علمی موجب خوانایی و زیبایی متن فارسی می‌شود. تایپیست حرفه‌ای مرز میان فاصلهٔ کامل و نیم‌فاصله را بدون مکث تشخیص می‌دهد.",
        ),
        PracticeText(
            id = "fa-6",
            language = TypingLanguage.PERSIAN,
            level = 3,
            categoryFa = "مهندسی نرم‌افزار",
            targetWpm = 48,
            title = "طراحی سیستم‌های پایدار و کم‌تأخیر",
            body = "کیفیت یک نرم‌افزار مهندسی‌شده در سادگی معماری، تفکیک دقیق مسئولیت‌ها و پایداری در شرایط مرزی سنجیده می‌شود. کدنویسی تمیز یعنی هر تابع یک وظیفهٔ مشخص را با بیشترین خوانایی و کمترین پیچیدگی پنهان انجام دهد.",
        ),
        PracticeText(
            id = "fa-7",
            language = TypingLanguage.PERSIAN,
            level = 4,
            categoryFa = "علوم اعصاب و حافظه",
            targetWpm = 52,
            title = "انحطاط‌پذیری عصبی و تثبیت مهارت",
            body = "پژوهش‌های علوم اعصاب نشان می‌دهد که تکرار هدفمند همراه با بازخورد فوری، میلین‌سازی رشته‌های عصبی مرتبط با حرکت انگشتان را تسریع می‌کند. استراحت‌های کوتاه میان نوبت‌های تمرین نیز به مغز فرصت می‌دهد الگوهای حرکتی تازه را تثبیت کند.",
        ),
        PracticeText(
            id = "fa-8",
            language = TypingLanguage.PERSIAN,
            level = 4,
            categoryFa = "ادبیات و نثر کلاسیک",
            targetWpm = 55,
            title = "حکمت عملی و ارزش وقت",
            body = "آدمی ساختهٔ اندیشه‌ها و عادت‌های پیوستهٔ خویش است؛ آنچه امروز با صبر و دقت می‌کاریم، فردا در قامت مهارت و تسلط آشکار می‌گردد. هیچ دستاورد ماندگاری بی‌رنج تمرین منظم و بازنگری صادقانه در خطاها به دست نیامده است.",
        ),
        PracticeText(
            id = "fa-9",
            language = TypingLanguage.PERSIAN,
            level = 5,
            categoryFa = "حروف کم‌بسامد و ترکیبی",
            targetWpm = 60,
            title = "چالش حروف چهارنقطه و زنجیره‌های پیچیده",
            body = "پژوهشگران ژرف‌اندیش در حوزه‌های زیست‌فناوری، اخترفیزیک و رمزنگاری کوانتومی نیازمند نگارش دقیق واژگان تخصصی، ترکیب‌های چندبخشی و ساختارهای نحوی منسجم با سرعت و دقت بسیار بالا هستند.",
        ),
        PracticeText(
            id = "fa-10",
            language = TypingLanguage.PERSIAN,
            level = 5,
            categoryFa = "آزمون استاندارد استقامت",
            targetWpm = 65,
            title = "آزمون جامع سرعت و پایداری تایپ فارسی",
            body = "تسلط کامل بر تایپ دهانگشتی زمانی محقق می‌شود که جریان اندیشه بدون هیچ مانع مکانیکی از ذهن به صفحه منتقل گردد. در این سطح، هماهنگی چشم و دست به چنان پایداری و روانی می‌رسد که واژه‌های ساده و تخصصی با ریتمی یکسان، بدون خستگی و با دقتی فراتر از نود و هشت درصد ثبت می‌شوند.",
        ),
    )

    val english: List<PracticeText> = listOf(
        PracticeText(
            id = "en-1",
            language = TypingLanguage.ENGLISH,
            level = 1,
            categoryFa = "Home Row Foundation",
            targetWpm = 30,
            title = "Home Row Posture and Finger Return",
            body = "Keep your fingers curved lightly over the home row keys and let your wrists float naturally. Precision on every single keystroke builds reliable muscle memory far faster than rushing through mistakes.",
        ),
        PracticeText(
            id = "en-2",
            language = TypingLanguage.ENGLISH,
            level = 2,
            categoryFa = "Cognitive Ergonomics",
            targetWpm = 42,
            title = "Deliberate Practice and Motor Learning",
            body = "Deliberate practice requires clear goals, immediate feedback, and focused repetition at the edge of your current ability. Smooth rhythm and steady breathing prevent tension in the hands during extended typing sessions.",
        ),
        PracticeText(
            id = "en-3",
            language = TypingLanguage.ENGLISH,
            level = 3,
            categoryFa = "Systems Engineering",
            targetWpm = 52,
            title = "Deterministic Architecture and Clean Code",
            body = "Reliable software systems are built from small, deterministic components whose state transitions can be verified independently. Clear naming and minimal mutable state make complex engineering problems tractable.",
        ),
        PracticeText(
            id = "en-4",
            language = TypingLanguage.ENGLISH,
            level = 4,
            categoryFa = "Visual Attention & Flow",
            targetWpm = 60,
            title = "Look-Ahead Reading in High-Speed Typing",
            body = "Expert typists read two or three words ahead of their active cursor, buffering upcoming character sequences in working memory so both hands can coordinate alternating keystrokes without hesitation.",
        ),
        PracticeText(
            id = "en-5",
            language = TypingLanguage.ENGLISH,
            level = 5,
            categoryFa = "Mastery Benchmark",
            targetWpm = 70,
            title = "Full-Spectrum Lexical Endurance Test",
            body = "Sustaining high throughput across dense technical prose demands balanced finger allocation, minimal corrective backspacing, and unwavering attentional control from the opening word to the final period.",
        ),
    )

    fun forLanguage(persian: Boolean, levelFilter: Int = 0): List<PracticeText> {
        val list = if (persian) this.persian else english
        return if (levelFilter in 1..5) list.filter { it.level == levelFilter } else list
    }

    fun dailyPick(usePersian: Boolean, dateIso: String): PracticeText {
        val list = forLanguage(usePersian)
        val idx = SplitMix64(SplitMix64.seedFor(dateIso)).nextInt(list.size)
        return list[idx]
    }

    fun levelLabelFa(level: Int): String = when (level) {
        1 -> "سطح ۱ • مقدماتی"
        2 -> "سطح ۲ • پایه"
        3 -> "سطح ۳ • متوسط"
        4 -> "سطح ۴ • پیشرفته"
        else -> "سطح ۵ • استادکار"
    }

    /**
     * Returns ergonomic 10-finger touch-typing guidance for the next expected
     * character according to ISIRI 9147 (Persian) and ANSI QWERTY (English).
     */
    fun fingerHintFa(ch: Char?, persian: Boolean): String {
        if (ch == null) return "تمرین کامل شد"
        if (ch == ' ') return "انگشت شست • کلید فاصله (Space)"
        if (ch == PersianText.ZWNJ) return "نیم‌فاصله (Shift + Space یا کلید فاصله)"
        return if (persian) {
            when (ch) {
                'ض', 'ص', 'ش', 'س', 'ظ', 'ط', 'ز' -> "دست چپ • انگشت کوچک / حلقه"
                'ث', 'ق', 'ی', 'ب', 'ر', 'ذ', 'د' -> "دست چپ • انگشت میانی / اشاره"
                'ف', 'غ', 'ل', 'ا', 'آ', 'پ', 'و' -> "دست چپ • انگشت اشاره"
                'ع', 'ه', 'ت', 'ن', 'ئ', 'م' -> "دست راست • انگشت اشاره"
                'ح', 'خ', 'ک', 'م', 'ة' -> "دست راست • انگشت میانی"
                'ج', 'چ', 'گ', 'ک', 'ژ' -> "دست راست • انگشت حلقه / کوچک"
                else -> "نگاه روی متن • ضربهٔ نرم و برگشت به ردیف میانی"
            }
        } else {
            when (ch.lowercaseChar()) {
                'q', 'a', 'z', 'w', 's', 'x' -> "دست چپ • انگشت کوچک / حلقه"
                'e', 'd', 'c', 'r', 'f', 'v', 't', 'g', 'b' -> "دست چپ • انگشت میانی / اشاره"
                'y', 'h', 'n', 'u', 'j', 'm', 'i', 'k' -> "دست راست • انگشت اشاره / میانی"
                'o', 'l', 'p' -> "دست راست • انگشت حلقه / کوچک"
                else -> "دست راست • کلید علائم نگارشی"
            }
        }
    }
}
