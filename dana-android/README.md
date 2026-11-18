# DANA AI · دانا

دانا یک دستیار **اندروید بومی و فارسی** با Kotlin و Compose است. این پوشه از پنل Python مخزن مستقل است. برای شروع، [راهنمای فارسی تحویل ۰٫۴](docs/04-handoff-fa.md) را ببینید. [گزارش فاز صفر](docs/00-audit-fa.md) نقشهٔ اولیه است، نه فهرست امکانات منتشرشده.

## دریافت پیش‌نمایش آزمایش‌شدهٔ ۰٫۴

[دانلود مستقیم APK](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/dana-preview-44236151860-1/DANA-AI-preview.apk) · [فایل SHA-256](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/dana-preview-44236151860-1/SHA256SUMS.txt) · [انتشار](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/tag/dana-preview-44236151860-1) · [بیلد و آزمون‌ها](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44236151860)

این فایل **پیش‌نمایش با امضای دیباگ** است، نه نسخهٔ Play Store. به Android 8 یا بالاتر نیاز دارد. مشخصات فایل: `ai.dana.preview` / `0.4.0-preview` / کد `4`، اندازهٔ `45,304,900` بایت و commit `d57a730327a4462615b99ddddc2260ee280f8138`.

```text
44e6cc48ae2e43d89823823e493bea4126419aa3f45e0cb762e90266b8924881  DANA-AI-preview.apk
```

پیش از نصب می‌توانید هر دو فایل را در یک پوشه قرار دهید و `sha256sum -c SHA256SUMS.txt` را اجرا کنید. اگر Android به‌خاطر تفاوت امضای دیباگ اجازهٔ به‌روزرسانی نمی‌دهد، ابتدا متن‌های موردنیاز را از نسخهٔ قبلی کپی کنید؛ حذف برنامه داده‌های محلی آن را پاک می‌کند. [پیش‌نمایش ۰٫۳ Studio](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/tag/dana-preview-44191207890-1) جدا باقی مانده است.

## اتصال هوش مصنوعی و کار آفلاین

1. از **تنظیمات** موتور خودتان را اضافه کنید: Gemini با کلید شخصی، هر API سازگار با OpenAI با نشانی HTTPS، یا Ollama/LM Studio روی میزبان خصوصی با HTTPS معتبر. پل اپل اختیاری است و به مک سازگار و توکن همان پل نیاز دارد. [راهنمای Gemini و مک](docs/03-engine-guide-fa.md) و [راهنمای اتصال LAN](docs/04-handoff-fa.md#اتصال-محلی-بدون-کلید-ابری) را ببینید.
2. اگر می‌خواهید مدل متصل کار کند، «کاملاً آفلاین» را خاموش کنید، از دکمهٔ دریافت مدل‌ها استفاده کنید و پروفایل را ذخیره کنید. فهرست‌خوانی بدون پرامپت است؛ دیدن فهرست، پاسخ‌دادن یا سهمیهٔ مدل را تضمین نمی‌کند.
3. در **گفتگو ← خودکار ← بازبینی** مقصد اول و حداکثر دو جایگزین را می‌بینید. پس از تأیید، موتور اول اجرا می‌شود. تنها در بعضی خطاها و **پیش از دریافت متن** به جایگزین تأییدشده می‌رود؛ بعد از پاسخ ناقص یا خطای TLS مقصد عوض نمی‌شود. برای اجرای هم‌زمان چند موتور از تب **مقایسه** استفاده کنید.

آفلاین در نصب تازه روشن است و برای درخواست‌های دانا هم WAN و هم LAN را مسدود می‌کند. بدون شبکه می‌توانید یادداشت، حافظهٔ دستی، OCR فارسی/انگلیسی و ابزارهای محلی مثل تایمر یا پیش‌نویس پیامک را استفاده کنید. گفتار روی دستگاه به سرویس و مدل فارسی نصب‌شده وابسته است؛ این APK مدل **مولد** داخل گوشی یا کلید مشترک ندارد. برای کار با تصویر یا صفحه، برش را پیش از استخراج متن یا انتخاب برای ارسال بازبینی کنید.

### حریم خصوصی

گفتگوی تازه به‌صورت موقت شروع می‌شود؛ آرشیو دورهای کامل را می‌توانید روشن کنید. زمینه، حافظهٔ اختیاری و پیوست در بازبینی مشخص‌اند. ماسک الگوهای حساس فقط در متن خروجی شبکهٔ گفتگو اعمال می‌شود و جای بررسی چشمی را نمی‌گیرد. دانا بی‌اجازه پرامپت یا تصویر نمی‌فرستد و متن مدل را اجرا نمی‌کند. کلید API با AES-256-GCM و Android Keystore به شناسهٔ پروفایل، قالب، شبکه و نشانی مقصد مقید است. یادداشت‌ها و تاریخچه در Room خصوصی ذخیره می‌شوند، **نه** در دیتابیس رمزنگاری‌شدهٔ جداگانه. پشتیبان‌گیری و انتقال Android غیرفعال است.

## ساخت از سورس

پیش‌نیاز: **JDK کامل ۲۱**، Android SDK Platform 36 و Build Tools 36.0.0. پوشهٔ `dana-android` را در Android Studio باز کنید. Wrapper و نسخه‌ها در Git هستند؛ ساخت به Gradle، Google Maven، Maven Central و JitPack (فقط ماژول Tesseract4Android) دسترسی می‌خواهد. `local.properties` یا فایل و رمز امضای انتشار را commit نکنید.

```bash
cd dana-android
sdkmanager --licenses
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
./gradlew --no-daemon :core:domain:test :core:data:testDebugUnitTest :core:data:lintDebug :app:lintDebug :app:assembleDebug :app:assembleRelease
./gradlew :app:installDebug
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

در Windows از `gradlew.bat` استفاده کنید. APK آزمایشی `app/build/outputs/apk/debug/app-debug.apk` است. برای ساخت امضاشدهٔ `ai.dana` با R8 از کلید انتشار پایدار خودتان در **Generate Signed Bundle / APK** استفاده کنید. Release امضاشده یا انتشار فروشگاه بخشی از این تحویل نیست.

## چه چیز راستی‌آزمایی شده؟

- در اجرای [44236151860](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44236151860)، بیلد debug/release و lint، ۳۱ تست JVM دامنه و ۱۲ تست JVM شبکه، ۷ تست اندروید ذخیره‌سازی و ۲ تست رسانه (برش و OCR با موتور واقعی)، و ۱۰ تست قرارداد پل مک روی Linux گذشتند.
- روی شبیه‌ساز Android API 34، نصب APK، بازشدن میز کار و فرم Gemini، آفلاینِ پیش‌فرض، و مسیر «Share متن ← پیش‌نویس ← بازبینی ← ذخیرهٔ یادداشت» آزموده شدند. آزمون OCR برای راه‌اندازی مدل ترکیبی فارسی/لاتین و خواندن یک تصویر نمونهٔ لاتین است، نه سنجش کیفیت OCR فارسی.
- Workflow امضا، نسخهٔ بسته، هم‌ترازی ۱۶ KiB ZIP و ELF ۶۴ بیتی، و schema نسخهٔ ۱ Room را کنترل می‌کند. روی شاخهٔ تحویل، push با `[dana-apk]` تنها پس از موفقیت بررسی‌ها و تطبیق دوبارهٔ SHA-256 فایل، prerelease می‌سازد. آزمون‌های HTTPS/SSE از سرورهای کنترل‌شده استفاده می‌کنند؛ به حساب یا کلید واقعی نیاز ندارند.

**خارج از شواهد این تحویل:** تولید زنده با حساب شخصی یا مک واقعی، کیفیت گفتار/دوربین/ضبط صفحه روی گوشی فیزیکی، و دسترسی از شبکهٔ ایران. بیدارباش، Accessibility، قفل بیومتریک داخل اپ و مدل مولد روی گوشی در ۰٫۴ ارائه نشده‌اند. [جزئیات قابلیت‌ها، محدودیت‌ها و مسیر فایل‌ها](docs/04-handoff-fa.md)

فونت [Vazirmatn v33.003](https://github.com/rastikerdar/vazirmatn/tree/v33.003) با SIL OFL 1.1 و دادهٔ [tessdata_fast 4.1.0](https://github.com/tesseract-ocr/tessdata_fast/tree/4.1.0) فارسی و انگلیسی با Apache 2.0 بسته‌بندی شده‌اند. متن مجوزها در `app/src/main/assets/licenses/` همراه APK است.
