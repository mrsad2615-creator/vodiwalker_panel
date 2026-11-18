package ir.aegis.feature.academy

import ir.aegis.core.model.*

object AcademyCurriculum {

    val courses: List<AcademyCourse> = listOf(
        AcademyCourse(
            id = "course-digital",
            domain = AcademyDomain.DIGITAL_SAFETY,
            title = "استاندارد OWASP MASVS و رمزنگاری موبایل",
            subtitle = "معماری امنیت کلیدها، قفل بایومتریک، FLAG_SECURE و حفاظت از ذخیره‌سازی محلی (OWASP MASVS v2).",
            estimatedMinutes = 14,
            lessons = listOf(
                AcademyLesson(
                    id = "masvs-1",
                    title = "ذخیره‌سازی امن با AES-256-GCM و PBKDF2",
                    durationMinutes = 7,
                    summary = "نگهداری اسرار بدون رمزنگاری احرازشده در حافظه گوشی با روت یا بکاپ فیزیکی قابل سرقت است.",
                    keySteps = listOf(
                        "همیشه از الگوریتم رمزنگاری احرازشده AES-256-GCM با کلید مشتق‌شده PBKDF2 استفاده کنید.",
                        "قابلیت FLAG_SECURE را برای جلوگیری از اسکرین‌شات و پیش‌نمایش Recent Apps فعال نگه دارید.",
                        "تایید امضای SHA-256 بسته‌های پشتیبان پیش از بازیابی ضروری است.",
                    ),
                    warningNote = "هرگز کلیدهای بازیابی را به‌صورت متن ساده در یادداشت‌های معمولی ننویسید.",
                    reviewedSource = "OWASP MASVS-STORAGE / MASVS-CRYPTO v2.1",
                ),
                AcademyLesson(
                    id = "masvs-2",
                    title = "کنترل اعلانات صفحه قفل و قفل خودکار",
                    durationMinutes = 7,
                    summary = "کدهای پیامکی OTP روی صفحه قفل بدون نیاز به باز کردن رمز گوشی قابل خواندن هستند.",
                    keySteps = listOf(
                        "نمایش محتوای حساس اعلان‌ها در صفحه قفل را غیرفعال کنید.",
                        "از قفل حداقل ۶ رقمی یا رمز ترکیبی به همراه اثرانگشت استفاده نمایید.",
                    ),
                    warningNote = "قفل الگوی ساده روی صفحه به‌راحتی از رد انگشت تشخیص داده می‌شود.",
                    reviewedSource = "OWASP MASVS-AUTH · NIST SP 800-124",
                ),
            ),
            quiz = listOf(
                AcademyQuizQuestion(
                    "dgq-1",
                    "چرا بکاپ‌های رمزنگاری‌شده باید دارای تگ اصالت GCM یا هش SHA-256 باشند؟",
                    listOf("فقط برای طولانی شدن فایل", "تشخیص فوری دستکاری فایل یا رمز اشتباه پیش از بازیابی", "ارسال خودکار به سرور"),
                    1,
                    "رمزنگاری AEAD علاوه بر محرمانگی، یکپارچگی و عدم دستکاری را تضمین می‌کند.",
                ),
                AcademyQuizQuestion(
                    "dgq-2",
                    "ویژگی FLAG_SECURE در اپلیکیشن‌های امنیتی و بانکی چه نقشی دارد؟",
                    listOf("افزایش روشنایی صفحه", "جلوگیری از اسکرین‌شات و ریکورد صفحه توسط بدافزارهای Overlay", "تغییر زبان برنامه"),
                    1,
                    "مطابق OWASP MASVS-PLATFORM از نشت اطلاعات روی بافر صفحه جلوگیری می‌کند.",
                ),
            ),
            scenario = AcademyScenario(
                id = "dg-sc",
                title = "تمرین افشای پیامک OTP روی صفحه قفل",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "گوشی شما قفل است اما پیامک کد تایید بانکی روی صفحه قفل کاملاً خوانا نمایش داده می‌شود. بهترین واکنش امنیتی چیست؟",
                        choices = listOf(
                            AcademyScenarioChoice("غیرفعال کردن محتوای حساس اعلان روی Lockscreen و جایگزینی با Authenticator", "کاملاً صحیح: مسیر سرقت فیزیکی OTP بسته شد.", 100, null),
                            AcademyScenarioChoice("تغییر ندادن تنظیمات به دلیل راحتی", "در صورت دسترسی چندثانیه‌ای فرد دیگر، کد تایید سرقت می‌شود.", 10, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-fire",
            domain = AcademyDomain.FIRE_SAFETY,
            title = "تشخیص بدافزار، تروجان بانکی و استاکرور",
            subtitle = "شناخت تکنیک‌های MITRE ATT&CK Mobile شامل سوءاستفاده از Accessibility، Overlay و نصب APK مخرب.",
            estimatedMinutes = 16,
            lessons = listOf(
                AcademyLesson(
                    id = "mal-1",
                    title = "الگوی حمله تروجان بانکی (Accessibility + Overlay)",
                    durationMinutes = 8,
                    summary = "تروجان‌ها برای خواندن رمز کارت و کلیک خودکار روی دکمه‌ها، دسترسی سرویس‌کمکی (Accessibility) می‌خواهند.",
                    keySteps = listOf(
                        "هرگز به برنامه‌های متفرقه، تقویم، ابزارک یا دانلودشده از تلگرام دسترسی Accessibility ندهید.",
                        "در بخش ضد هک AegisGuard، سرویس‌های فعال Accessibility و Overlay را بررسی کنید.",
                        "در بخش آنتی‌ویروس برنامه، فایل‌های نصب‌شده با منبع نامشخص (Sideloaded) را ممیزی کنید.",
                    ),
                    warningNote = "برنامه‌های جعلی با پسوند دوگانه مثل eblagh.pdf.apk تلهٔ رایج تروجان‌ها هستند.",
                    reviewedSource = "MITRE ATT&CK Mobile T1453 / T1407",
                ),
                AcademyLesson(
                    id = "mal-2",
                    title = "شناخت نشانه‌های استاکرور و شنود پنهان",
                    durationMinutes = 8,
                    summary = "جاسوس‌افزارها با ترکیب دسترسی میکروفون، مکان پس‌زمینه و پیامک، کاربر را رصد می‌کنند.",
                    keySteps = listOf(
                        "نشانگر سبز دوربین یا میکروفون در نوار بالای اندروید را جدی بگیرید.",
                        "دسترسی Device Admin برنامه‌های ناشناس را لغو کنید تا امکان حذف آن‌ها باز شود.",
                    ),
                    warningNote = "جاسوس‌افزارها اغلب خود را با نام‌های شبیه سیستم مثل System Patch Update پنهان می‌کنند.",
                    reviewedSource = "MITRE T1429 / T1401 · Coalition Against Stalkerware",
                ),
            ),
            quiz = listOf(
                AcademyQuizQuestion(
                    "malq-1",
                    "اگر یک برنامه ویدئوپلیر یا فایل‌منیجر درخواست دسترسی BIND_ACCESSIBILITY_SERVICE بدهد، نشانهٔ چیست؟",
                    listOf("عملکرد عادی برنامه", "احتمال بالای تروجان بانکی یا کنترل مخفی صفحه (MITRE T1453)", "بهینه‌سازی باتری"),
                    1,
                    "برنامه‌های عادی نیازی به خواندن محتوای کل صفحات دیگر از طریق Accessibility ندارند.",
                ),
            ),
            scenario = AcademyScenario(
                id = "mal-sc",
                title = "سناریوی فایل با پسوند دوگانه",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "فایلی با نام «ابلاغیه_دادگاه.pdf.apk» در پیام‌رسان برایتان ارسال شده و هنگام باز شدن درخواست Install Unknown Apps می‌دهد.",
                        choices = listOf(
                            AcademyScenarioChoice("لغو فوری نصب، بررسی در اسکنر فایل AegisGuard و مسدودسازی فرستنده", "عالی! این یک تلهٔ کلاسیک تروجان اندرویدی است.", 100, null),
                            AcademyScenarioChoice("تایید نصب برای مشاهده PDF", "تایید این فایل باعث آلودگی کامل دستگاه به بدافزار می‌شود.", 0, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-earthquake",
            domain = AcademyDomain.EARTHQUAKE,
            title = "ضد فیشینگ، لینک‌های جعلی و مهندسی اجتماعی",
            subtitle = "تحلیل دامنه‌های جعلی (ثنا، شاپرک، سهام عدالت)، حملات هموگراف Punycode و محافظت از OTP.",
            estimatedMinutes = 12,
            lessons = listOf(
                AcademyLesson(
                    id = "ph-1",
                    title = "کالبدشکافی لینک‌های فیشینگ و هموگراف",
                    durationMinutes = 6,
                    summary = "مهاجمان با پسوندهای ارزان (.xyz, .top, .tk) یا حروف مشابه (xn--) درگاه جعلی می‌سازند.",
                    keySteps = listOf(
                        "سامانه‌های رسمی دولتی ایران روی دامنه .ir و پروتکل https قرار دارند، نه .xyz یا IP مستقیم.",
                        "پیامک ابلاغیه ثنا و جریمه هرگز لینک دانلود مستقیم فایل .apk ارسال نمی‌کند.",
                        "پیش از لمس هر لینک مشکوک، آن را در «آزمایشگاه تشخیص فیشینگ» همین برنامه بررسی کنید.",
                    ),
                    warningNote = "حتی با داشتن آنتی‌ویروس، وارد کردن دستی رمز دوم در صفحه فیشینگ منجر به سرقت حساب می‌شود.",
                    reviewedSource = "MITRE T1660 · Anti-Phishing Standard",
                ),
            ),
            quiz = listOf(
                AcademyQuizQuestion(
                    "phq-1",
                    "کدام ویژگی در پیامک زیر قطعاً نشانهٔ فیشینگ است؟ «ابلاغیه فوری ثنا: http://adliran-sana.top/app.apk»",
                    listOf("استفاده از پروتکل امن HTTPS", "دامنه غیررسمی .top، نبود HTTPS و لینک مستقیم فایل .apk", "داشتن نام سامانه"),
                    1,
                    "سامانه رسمی عدل ایران فقط از adliran.ir استفاده می‌کند و فایل APK ارسال نمی‌نماید.",
                ),
            ),
            scenario = AcademyScenario(
                id = "ph-sc",
                title = "پیامک مسدودی حساب بانکی",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "پیامکی از شماره موبایل شخصی دریافت می‌کنید: «حساب شما مسدود شد، ظرف ۲ ساعت از لینک b2n.ir/bank-update احراز هویت کنید».",
                        choices = listOf(
                            AcademyScenarioChoice("عدم کلیک روی لینک، بررسی در تحلیلگر فیشینگ و استعلام مستقیم از اپلیکیشن رسمی بانک", "بهترین واکنش: کوتاه‌کننده لینک + سرشماره شخصی نشانه قطعی فیشینگ است.", 100, null),
                            AcademyScenarioChoice("ورود به لینک و وارد کردن شماره کارت و CVV2", "منجر به سرقت فوری موجودی حساب می‌شود.", 0, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-flood",
            domain = AcademyDomain.FLOOD,
            title = "امنیت شبکه، کشف MITM و گواهی‌نامه‌های CA",
            subtitle = "شناسایی شنود HTTPS، گواهی ریشه کاربری جعلی در AndroidCAStore و خطر VPNهای ناشناس.",
            estimatedMinutes = 12,
            lessons = listOf(
                AcademyLesson(
                    id = "net-1",
                    title = "مقابله با شنود ترافیک رمزنگاری‌شده (MITM)",
                    durationMinutes = 6,
                    summary = "اگر گواهی کاربری (User CA) ناشناس روی گوشی نصب شود، مهاجم می‌تواند ترافیک TLS را رمزگشایی کند.",
                    keySteps = listOf(
                        "بخش «گواهی کاربری SSL» را در بازرس ضد هک چک کنید؛ مقدار استاندارد برابر صفر است.",
                        "از اتصال به وای‌فای عمومی بدون رمز و VPNهای رایگان مشکوک خودداری کنید.",
                    ),
                    warningNote = "از اندروید ۷ به بعد برنامه‌ها به‌طور پیش‌فرض به User CA اعتماد نمی‌کنند، مگر مرورگر یا برنامه قدیمی.",
                    reviewedSource = "OWASP MASVS-NETWORK · MITRE T1557",
                ),
            ),
            quiz = listOf(
                AcademyQuizQuestion(
                    "netq-1",
                    "یافت شدن گواهی ناشناس در بخش User CA (AndroidCAStore) چه خطری ایجاد می‌کند؟",
                    listOf("کند شدن شارژ باتری", "امکان شنود و بازگشایی ترافیک HTTPS توسط واسط (MITM)", "پاک شدن مخاطبین"),
                    1,
                    "گواهی ریشه جعلی به پراکسی میانی اجازه امضای تقلبی گواهی سایت‌ها را می‌دهد.",
                ),
            ),
            scenario = AcademyScenario(
                id = "net-sc",
                title = "هشدار گواهی در بازرسی سیستم",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "اسکنر ضد هک AegisGuard یک User CA ناشناس و یک رابط tun0 مشکوک گزارش کرده است.",
                        choices = listOf(
                            AcademyScenarioChoice("حذف گواهی کاربری از Security Settings و توقف VPN ناشناس", "دقیقاً صحیح: کانال شنود احتمالی بسته شد.", 100, null),
                            AcademyScenarioChoice("نادیده گرفتن هشدار و ورود به همراه بانک", "اطلاعات نشست ممکن است در خطر شنود باشد.", 0, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-travel",
            domain = AcademyDomain.TRAVEL_SAFETY,
            title = "ایمن‌سازی فیزیکی: ADB، روت و شارژر عمومی",
            subtitle = "بستن USB Debugging، جلوگیری از حمله Juice Jacking و کنترل دستگاه‌های متصل.",
            estimatedMinutes = 10,
            lessons = listOf(
                AcademyLesson(
                    id = "phy-1",
                    title = "خطرات روشن ماندن USB Debugging (ADB)",
                    durationMinutes = 5,
                    summary = "درگاه USB با ADB فعال به کیوسک‌ها و سیستم‌های آلوده اجازه اجرای دستورات سطح سیستم را می‌دهد.",
                    keySteps = listOf(
                        "همیشه Developer Options و USB Debugging را خاموش نگه دارید.",
                        "حالت پیش‌فرض USB را روی «فقط شارژ» (No data transfer) تنظیم نمایید.",
                    ),
                    warningNote = "از پورت‌های USB عمومی فرودگاه و ترمینال بدون محافظ دیتا استفاده نکنید.",
                    reviewedSource = "CIS Android Benchmark · NIST SP 800-124",
                ),
            ),
            quiz = listOf(
                AcademyQuizQuestion(
                    "phyq-1",
                    "چرا نباید گزینه USB Debugging روی گوشی روزمره فعال بماند؟",
                    listOf("مصرف اینترنت را بالا می‌برد", "امکان استخراج داده و نصب مخفی APK از طریق کابل USB را فراهم می‌کند", "کیفیت دوربین را کم می‌کند"),
                    1,
                    "ADB یک درگاه قدرتمند اشکال‌زدایی برای کنترل فایل‌ها و پکیج‌هاست.",
                ),
            ),
            scenario = AcademyScenario(
                id = "phy-sc",
                title = "شارژ گوشی در ترمینال عمومی",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "باتری گوشی ۱۰٪ است و فقط یک کابل USB متصل به دیوار در مکان عمومی وجود دارد. صفحه ناگهان پیغام Trust this computer (RSA) می‌دهد.",
                        choices = listOf(
                            AcademyScenarioChoice("رد کردن فوری پیغام (Cancel)، قطع کابل و خاموش کردن ADB", "عالی! از استخراج دیتا یا نصب بدافزار جلوگیری کردید.", 100, null),
                            AcademyScenarioChoice("لمس Allow برای شارژ سریع‌تر", "مجوز کامل ADB را به سیستم پشت دیوار واگذار می‌کنید!", 0, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-first-aid",
            domain = AcademyDomain.FIRST_AID,
            title = "واکنش اضطراری و جرم‌یابی پس از نفوذ (IR)",
            subtitle = "مراحل استاندارد مهار نفوذ (Containment)، حفظ شواهد SHA-256، تغییر امن رمزها و پاک‌سازی کامل.",
            estimatedMinutes = 14,
            lessons = listOf(
                AcademyLesson(
                    id = "ir-1",
                    title = "قرنطینه شبکه و حفظ مدارک دیجیتال",
                    durationMinutes = 7,
                    summary = "حالت پرواز اولین سد برای توقف ارسال داده‌ها به سرور مهاجم است بدون آنکه شواهد پاک شوند.",
                    keySteps = listOf(
                        "گوشی را در حالت هواپیما قرار دهید.",
                        "گزارش رخداد و خروجی PDF با هش SHA-256 را از بخش جرم‌یابی AegisGuard تولید کنید.",
                        "از یک دستگاه مطمئن دیگر، رمز تمام اکانت‌ها و کلیدهای بازیابی را تغییر دهید.",
                    ),
                    warningNote = "پاک‌سازی شتاب‌زده قبل از ثبت شواهد، پیگیری حقوقی در پلیس فتا را دشوار می‌کند.",
                    reviewedSource = "NIST SP 800-61r2 Computer Security Incident Handling",
                ),
            ),
            quiz = listOf(
                AcademyQuizQuestion(
                    "irq-1",
                    "نخستین اقدام فنی پس از کشف رفتار هک یا بدافزار فعال روی گوشی چیست؟",
                    listOf("تغییر رمزها روی همان گوشی آلوده", "قطع ارتباط شبکه (Airplane Mode) و تغییر رمزها با یک دستگاه امن دیگر", "خاموش و روشن کردن ساده"),
                    1,
                    "اگر کی‌لاگر یا تروجان فعال باشد، تغییر رمز روی همان گوشی لو می‌رود.",
                ),
            ),
            scenario = AcademyScenario(
                id = "ir-sc",
                title = "حمله به گوشی و ورود مشکوک به ایمیل",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "اعلان ورود از شهر دیگر به ایمیل اصلی خود می‌بینید و برنامه ناشناسی روی گوشی پیدا شده است.",
                        choices = listOf(
                            AcademyScenarioChoice("حالت پرواز روی گوشی، ثبت شواهد آفلاین و تغییر رمز + بستن نشست‌ها از لپ‌تاپ امن", "اجرای دقیق پروتکل NIST Incident Response!", 100, null),
                            AcademyScenarioChoice("تغییر رمز ایمیل در مرورگر همان گوشی آلوده بدون حذف بدافزار", "مهاجم رمز جدید را نیز رصد خواهد کرد.", 15, null),
                        ),
                    ),
                ),
            ),
        ),
    )
}
