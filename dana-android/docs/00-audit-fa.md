# دانا — فاز ۰: ممیزی و طرح معماری

**تاریخ ممیزی:** ۱۷ نوامبر ۲۰۲۶، UTC · **پایه:** `78a6084` · **شاخه:** `arena/13014174-vodiwalker-panel`

## ۱. وضعیت مخزن و محیط ساخت

مخزن پایه متعلق به پنل Python پروژهٔ Vodiwalker است؛ پروژهٔ اندروید، Gradle یا دستورالعمل `AGENTS.md` ندارد. دانا در `dana-android/` یک پروژهٔ مستقل خواهد بود. کد، کانفیگ، داده و مسیر استقرار پنل به دانا متصل نمی‌شوند.

| مورد | نتیجهٔ ممیزی | تصمیم |
| --- | --- | --- |
| میزبان | Linux x86-64، ۲ CPU، حدود ۳٫۸ GiB RAM و ۲۰ GiB فضای خالی | بیلد بزرگ و امولاتور را به محیط مجهز SDK واگذار می‌کنیم. |
| Java | در ابتدا `java` و `javac` نصب نبودند. یک Temurin 21.0.8 کاهش‌یافته از PyPI با SHA-256 بررسی شد؛ `java` اجرا شد ولی محیط JDK کامل نیست. | برای ساخت Android، JDK کامل ۲۱ و برای کد مستقل، کامپایلر CLI Kotlin بررسی شده است. |
| Gradle | نصب نبود. اسکریپت‌ها، JAR رسمی Wrapper و هش توزیع 9.7.1 از GitHub در دسترس‌اند. | Wrapper با هش ثابت. دانلود توزیع در محیط فعلی قابل اتکا نیست. |
| SDK، ADB و شبیه‌ساز | `sdkmanager`، `adb`، `emulator`، `ANDROID_HOME` و `/dev/kvm` وجود ندارند. | SDK Platform 36 و Build Tools سازگار در Android Studio یا CI. تست روی دستگاه از تست JVM جداست. |
| شبکهٔ ساخت | GitHub API، codeload، npm و PyPI پاسخ دادند. اتصال TLS به Google Maven، مخزن SDK، Maven Central و `services.gradle.org` برقرار نشد؛ فایل‌های Release از GitHub به میزبان خارج از دسترس هدایت شدند. | از مخازن رسمی در بیلد استفاده می‌کنیم، نه Mirror ناشناس. این محیط نمایندهٔ شبکهٔ ایران نیست. |
| Kotlin | بستهٔ CLI رسمی JetBrains برای Kotlin 2.3.21 از npm با SHA-512 بررسی شد و `kotlinc-jvm -version` اجرا شد. | برای بررسی کد مستقل از SDK از CLI استفاده می‌شود. این جایگزین بیلد Android نیست. |

## ۲. استک مصوب

| لایه | انتخاب | دلیل |
| --- | --- | --- |
| ساخت و زبان | JDK 21، JVM target 17، Gradle 9.7.1، AGP 9.3.2، Kotlin 2.3.21/K2، KSP 2.3.7 | Kotlin داخلی AGP برای ماژول‌های Android و Kotlin/JVM برای دامنه؛ بدون KAPT. نسخه‌ها پایدار و پین شده‌اند. انتخاب ترکیب محافظه‌کارانه بر دنبال‌کردن مستقل نسخهٔ آخر هر کتابخانه اولویت دارد. |
| دستگاه | `minSdk 26`، `compileSdk/targetSdk 36` | Android 8 به بالا؛ تست جداگانه برای رفتارهای هدف‌گیری اندروید جدید. |
| نمایش و وضعیت | Compose BOM 2025.12.00 + Material 3، ViewModel و StateFlow، Vazirmatn (SIL OFL 1.1) | فارسی RTL بومی، وضعیت قابل‌مشاهده مطابق چرخهٔ حیات و اجزای شیشه‌ای بدون موتور گرافیکی خارجی. |
| تزریق وابستگی | Hilt 2.60.1 با KSP | گراف بررسی‌شونده در زمان ساخت و جداسازی UI از ذخیره‌ساز و APIهای پلتفرم. |
| ذخیرهٔ محلی | Room 2.8.4، Preferences DataStore 1.2.0 | تراکنش و کلید خارجی برای تاریخچه؛ ذخیرهٔ واکنشی تنظیمات، جدا از داده‌های کاربر. قرارداد کش دارای TTL و تفکیک حساب پس از تعیین سیاست حریم خصوصی شبکه اضافه می‌شود. |
| کلید API | Android Keystore و AES-256-GCM + ciphertext در Room | جایگزین API منسوخ `EncryptedSharedPreferences`. کلید رمزنگاری استخراج‌پذیر نیست؛ شناسه و مقصد ارائه‌دهنده در AAD. ذخیرهٔ پروفایل و ciphertext اتمیک است. |
| شبکه (فاز ۳) | OkHttp + SSE و kotlinx.serialization، دو adapter مستقل | پشتیبانی از HTTPS سفارشی و استریم قابل‌لغو؛ بدون SDK اختصاصی برای هر درگاه. |
| کار پس‌زمینه (در فاز مربوطه) | WorkManager برای کار قابل‌تعویق؛ FGS فقط برای عمل مشهود و شروع‌شده توسط کاربر | رعایت دسترسی حین استفاده، محدودیت‌های API 34+ و آزادسازی منابع پس از پایان جلسه. |

مرجع بررسی انتخاب‌ها: [نمونهٔ Android با AGP 9.3](https://github.com/android/gradle-recipes/tree/agp-9.3)، [ترکیب AGP و Wrapper در Now in Android](https://github.com/android/nowinandroid)، [Kotlin 2.3.21](https://github.com/JetBrains/kotlin/releases/tag/v2.3.21)، [Hilt 2.60.1](https://github.com/google/dagger/releases/tag/dagger-2.60.1) و [هش‌های توزیع Gradle](https://github.com/gradle/gradle-distributions/releases/tag/v9.7.1). به‌روزترین‌بودن همهٔ artifacts یا نتیجهٔ Sync Android در این محیط تأیید نشده است.

## ۳. مسیر اتصال از ایران و مدل اعتماد

در فاز ۱، دو **فرمت پیکربندی** در برنامه قابل‌ذخیره‌اند: `OpenAI-compatible` و `Gemini native`. کاربر نام، مدل و Base URL معتبر HTTPS را تعیین می‌کند و کلید را در فیلد مجزا وارد می‌کند. این‌ها اتصال تست‌شده یا موتور فعال نیستند. فاز ۱ هیچ درخواست API، Probe یا ارسال داده انجام نمی‌دهد.

| مسیر | قالب/هزینهٔ مورد انتظار | شرط استفاده، نه تضمین |
| --- | --- | --- |
| درگاه‌های ایرانی مانند 1xai / Gisoo | فقط اگر `/chat/completions` سازگار ارائه دهند؛ BYOK، مطابق تعرفه یا اعتبار همان درگاه | نشانی، پشتیبانی از استریم/مدل و پلن رایگان از شبکهٔ ایران تأیید نشده‌اند؛ هیچ نشانی یا کلید پیش‌فرض برای آن‌ها در کد تعبیه نمی‌شود. |
| [Ollama](https://github.com/ollama/ollama) / [LM Studio](https://github.com/lmstudio-ai/docs) | OpenAI-compatible روی LAN؛ بدون هزینهٔ درخواست ابری، نیازمند مدل و میزبان به هزینهٔ کاربر | در نسخهٔ عمومی مسیر HTTPS با گواهی مورد اعتماد Android، حتی در LAN؛ برای Ollama/LM Studio معمولاً نیاز به reverse proxy محلی و DNS است. ورودی آزاد `http://192.168…` در این نسخه پذیرفته نمی‌شود. |
| Groq، OpenRouter، Hugging Face | OpenAI-compatible برای endpointهای مستند همان سرویس؛ سهمیهٔ رایگان محدود و متغیر | قابلیت مدل‌ها، نوع سهمیه، پشتیبانی از تصویر و دسترسی از شبکهٔ کاربر باید در زمان اجرا بررسی شوند. |
| Google AI Studio | adapter بومی Gemini (`streamGenerateContent` و `alt=sse`)؛ API key کاربر | مدل و سهمیه براساس حساب/منطقه. برنامه تغییر جغرافیا را وعده نمی‌دهد. |
| درگاه HTTPS شخصی | Base URL دلخواه برای درگاهی که کاربر مالک یا به آن اعتماد دارد | مقصد می‌تواند متن و کلید ارسالی به خودش را ببیند؛ کلید یک سرویس خودکار به سرویس دیگر یا به redirect فرستاده نمی‌شود. |

جریان پیش‌بینی‌شدهٔ فاز ۳: `Intent → سیاست آفلاین/رضایت → طبقهٔ ساده/استدلال/بینایی → انتخاب adapter دارای قابلیت → SSE → UI`. `NetworkMonitor` فاز ۱ فقط شبکهٔ پیش‌فرض و `NET_CAPABILITY_VALIDATED` را می‌سنجد؛ نه دسترسی به ارائه‌دهنده و نه نوع فیلترینگ. حالت کاملاً آفلاین پیش‌فرض است. در آینده روشن بودن آن باید همهٔ مسیرهای WAN و LAN را مسدود کند. در حالت شبکه‌ای، `DanaAiRouter` در برابر timeout، 403 یا 429 **پیش از دریافت نخستین بخش قابل‌نمایش** می‌تواند فقط به یک موتور جایگزین مورد تأیید کاربر برود. تغییر مقصد برای دادهٔ صفحه یا تصویر رضایت مجزا می‌خواهد. پس از دریافت بخشی از متن، خروجی از دو مدل به هم چسبانده نمی‌شود. `403` ممکن است ناشی از سیاست حساب، مدل یا شبکه باشد؛ `429` لزوماً تمام‌شدن اعتبار نیست. Health check باید نام مقصد، زمان و نتیجهٔ HTTP را نمایش دهد و تشخیص «فیلترینگ» یا «سهمیهٔ باقی‌مانده» را از روی حدس گزارش نکند.


**قرارداد adapterهای فاز ۳:**

1. Base URL شامل پیشوند API طبق مستندات همان سرویس است (معمولاً `/v1` یا `/v1beta`). OpenAI-compatible مسیر `chat/completions`، `stream: true` و در صورت نیاز هدر `Authorization: Bearer …` را استفاده می‌کند. Gemini native مسیر `models/{model}:streamGenerateContent` با `alt=sse` و کلید در هدر `x-goog-api-key` خواهد داشت، نه در URL.
2. هر تلاش مهلت محدود، لغو coroutine و گزارش نتیجه بدون body، کلید یا سایر دادهٔ حساس دارد. خطای گواهی یا نام میزبان با پایین آوردن امنیت حل نمی‌شود. `401` نیازمند اصلاح کلید است؛ `429` با `Retry-After` و سقف انتظار بررسی می‌شود، نه با تکرار بی‌حد درخواست.
3. تشخیص قابلیت و افشای مقصد قبل از ارسال انجام می‌شود. نبود اینترنتِ تأییدشده می‌تواند مسیر WAN را کنار بگذارد، اما به‌خودی خود LAN را ناممکن نمی‌کند؛ LAN باید روی مسیر محلیِ مورد رضایت کاربر برقرار و روی دستگاه تأیید شود. گزینهٔ «کاملاً آفلاین» حتی LAN را کنار می‌گذارد. ادعای «بدون VPN» فقط برای ترکیب سرویس/خط اینترنتی‌ای معتبر است که واقعاً آزمون شده باشد.

`network_security_config.xml` در فاز ۱ به‌طور سراسری cleartext را ممنوع می‌کند. در فاز ۳ محدود کردن TLS به 1.2/1.3، بستن redirect خودکار، مهلت و backoff، محدودیت اندازه و لغو body استریم در OkHttp اعمال می‌شوند. Network Security Config نمی‌تواند استثنای CIDR یا IP واردشده در زمان اجرا را تعریف کند. در صورت نیاز به HTTP آزمایشگاهی، تنها راه پذیرفتنی یک پیکربندی build جدا با allowlist ثابت میزبان و **بدون کلید** خواهد بود؛ نه اجازهٔ cleartext عمومی. `localhost` روی گوشی خود گوشی است، نه رایانهٔ کاربر. دسترسی LAN و رفتار VPN/شبکهٔ چندگانه باید روی Android واقعی بررسی شوند.

## ۴. OCR، TTS و سیاست دسترسی

- **متن فارسی صفحه:** نخست خواندن درخت Accessibility فقط به‌درخواست کاربر، بدون جمع‌آوری خودکار رویدادها. برنامهٔ میزبان ممکن است متن را ناقص ارائه کند؛ درخت UI معادل اسکرین‌شات نیست و دقت ثابت ندارد. فیلد رمز و صفحهٔ حفاظت‌شده از مسیر استخراج حذف می‌شوند. اگر وضعیت حفاظت قابل اثبات نباشد، به‌خصوص در نماهای ساختاری، دانا نباید به کاربر وعدهٔ تشخیص قطعی بدهد یا از مسیر دیگر آن را دور بزند. انتشار Accessibility نیازمند افشای درون‌برنامه‌ای و بررسی سیاست فروشگاه است.
- **OCR روی تصویر:** برای فارسی Tesseract LSTM با [`fas.traineddata`](https://github.com/tesseract-ocr/tessdata_fast/blob/4.1.0/fas.traineddata) (در آزمایش فارسی/انگلیسی ترکیبی می‌توان `fas+eng` را مقایسه کرد). ML Kit لاتین فقط در صورت نیاز؛ جایگزین مدل فارسی نیست. معیار پذیرش با نمونه‌های مجاز و متنوع شامل خطای نویسه، نیم‌فاصله، ترتیب راست‌به‌چپ و مصرف حافظه است. در فاز ۵، فریم/برش در RAM، چرخهٔ رضایت MediaProjection جدید برای هر جلسه و پایان projection در توقف؛ محتوایی که سیستم محافظت کرده ثبت یا بازیابی نمی‌شود. برای ارسال برش فشرده به مدل بینایی تأیید جداگانه لازم است.
- **گفتار:** `SpeechRecognizer` با `fa-IR` و نتایج موقت؛ حالت کاملاً آفلاین فقط از recognizer واقعی on-device، اگر موجود باشد. برای TTS بررسی `isLanguageAvailable` و انتخاب Voice فارسی با `isNetworkConnectionRequired == false` برای حالت آفلاین. در نبود صوت فارسی، نمایش توضیح نصب صدا، نه تعویض پنهانی به زبان دیگر. `TextToSpeech` در فاز ۴ به‌صورت جمله‌به‌جمله با آزادسازی منابع و قطع با لمس؛ قطع با گفتار در زمان پخش نیازمند آزمون اکوی واقعی است. Wake word دائمی و جایگزینی دستیار به پشتیبانی OEM و نقش سیستم وابسته‌اند.
- **مانیفست:** فعلاً فقط ACCESS_NETWORK_STATE؛ INTERNET با اولین انتقال واقعی در فاز ۳ اضافه می‌شود. هیچ سرویس یا مجوز حساسی بدون پیاده‌سازی و آزمون قابلیت مربوطه ثبت نمی‌شود. ذخیرهٔ فایل‌ها/DB برنامه در فضای خصوصی و از backup و device transfer خارج می‌شوند. این جایگزین رمزنگاری بدنهٔ یادداشت یا پیام‌ها نیست.

## ۵. درخت تحویل فاز ۱

```text
vodiwalker_panel/
├── .github/workflows/dana-android.yml
├── [پنل و مسیر استقرار موجود — بدون تغییر]
└── dana-android/
    ├── .gitignore
    ├── README.md
    ├── docs/00-audit-fa.md
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── gradle/
    │   ├── libs.versions.toml
    │   └── wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
    ├── core/
    │   ├── domain/
    │   │   ├── build.gradle.kts
    │   │   └── src/
    │   │       ├── main/kotlin/ai/dana/domain/{LocalData,Network,Provider,Validation}.kt
    │   │       └── test/kotlin/ai/dana/domain/DomainTest.kt
    │   └── data/
    │       ├── build.gradle.kts
    │       ├── schemas/   # خروجی واقعی Room پس از نخستین بیلد SDK
    │       └── src/
    │           ├── main/AndroidManifest.xml
    │           ├── main/kotlin/ai/dana/data/
    │           │   ├── di/DataModule.kt
    │           │   ├── local/{DanaDatabase,LocalRepositories,PreferencesStore}.kt
    │           │   ├── network/AndroidNetworkMonitor.kt
    │           │   └── provider/{EngineRepository,KeystoreVault}.kt
    │           └── androidTest/kotlin/ai/dana/data/LocalDataTest.kt
    └── app/
        ├── build.gradle.kts
        ├── proguard-rules.pro
        └── src/main/
            ├── AndroidManifest.xml
            ├── kotlin/ai/dana/{DanaApplication,MainActivity}.kt
            ├── kotlin/ai/dana/ui/{DanaApp,DanaErrorHandler,DanaTheme,DanaViewModel,Editors}.kt
            ├── assets/licenses/vazirmatn_ofl.txt
            └── res/
                ├── drawable/ic_dana.xml
                ├── font/vazirmatn_{light,regular,medium,bold}.ttf
                ├── values/{strings,themes}.xml
                └── xml/{backup_rules,data_extraction_rules,network_security_config}.xml
```

مرز وابستگی: `app → domain` برای قراردادها و `app → data` برای گراف Hilt؛ `data → domain` و Android؛ دامنه وابسته به Android، UI یا مدل خارجی نیست. ماژول‌های آتی در همین پروژه اضافه می‌شوند: فاز ۲ `ui/chat`, `ui/history`, `ui/designsystem`؛ فاز ۳ `core/ai`؛ فاز ۴ `feature/voice` و `feature/actions`؛ فاز ۵ `feature/screen` و `feature/vision`؛ فاز ۶ `feature/memory` و `feature/privacy`؛ فاز ۷ تست‌های یکپارچه و راهنمای انتشار. این مسیرهای آتی نه سرویس ثبت‌شده‌اند و نه ادعای قابلیت آمادهٔ اجرا.
