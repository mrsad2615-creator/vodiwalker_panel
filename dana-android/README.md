# mrsad · دستیار فارسی Android

`mrsad` ادامهٔ دانا استودیو است: برنامهٔ بومی Kotlin و Compose برای گفتگو، فرمان‌های گوشی و کار با تصویر. این پوشه از پنل Python مخزن مستقل است.

## پیش‌نمایش آزموده‌شدهٔ ۰٫۶

[دانلود مستقیم APK](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/mrsad-preview-44326152274-1/mrsad-preview.apk) · [SHA-256](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/mrsad-preview-44326152274-1/SHA256SUMS.txt) · [راهنمای فارسی](docs/06-mrsad-fa.md) · [بیلد و آزمون‌ها](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44326152274) · [تصاویر شبیه‌ساز](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44326152274/artifacts/14438305984)

فایل **پیش‌نمایش دیباگ** است، نه نسخهٔ Play Store؛ Android 8 یا بالاتر. `ai.dana.preview` / `0.6.0-preview` / کد `6`. اندازه `45,741,523` بایت، commit `3e368592f232fa78b52507a76470f4078cd2246a`.

```text
8e509ef8c1075f1b00a0136b942dc283c1e9c6bb15d24f46bdd2d707b2d3c7d3  mrsad-preview.apk
```

بسته و Room تغییر نکرده‌اند، ولی نصب روی پیش‌نمایش قبلی به یکسان بودن امضا نیاز دارد. اگر Android نصب درجا را قبول نمی‌کند، **پیش از حذف** متن‌های مهم را از نسخهٔ قبلی کپی کنید؛ حذف برنامه دادهٔ محلی را پاک می‌کند. [نسخهٔ قبلی ۰٫۵ و راهنمای آن](docs/05-studio-fa.md) همچنان در دسترس‌اند.

## مسیر سریع

- **فرمان گوشی**: باتری، صدای رسانه، چراغ‌قوه، تایمر، تنظیمات، شماره‌گیر، یادداشت و پیش‌نویس‌ها. فرمان‌ها قبل از اجرا بازبینی می‌شوند؛ متن تولیدشده از مدل اجرا نمی‌شود. در گوشی سازگار، صفحهٔ دستیار با ژست Android هم باز می‌شود.
- **گفت‌وگوی صوتی**: با فشار دکمه و بازبینی متن، نوبت را ادامه دهید. تشخیص و خواندن فارسی به سرویس‌های صوتی دستگاه بستگی دارند. حالت شبکه‌ای سرویس گفتار فقط با انتخاب جداگانه و وقتی آفلاین خاموش است در دسترس است.
- **تصویر**: گالری یا عکس دوربین، برش و OCR محلی؛ ارسال تصویر به مدل فقط با پیوست و تأیید مقصد.
- **موتورها**: OpenRouter Free، Gemini، Groq، Mistral، DeepSeek، Cerebras، API سفارشی و گزینه‌های HTTPS محلی. کلید شخصی و شرایط ارائه‌دهنده لازم است. در گفتگو می‌توان ۲ یا ۳ موتور را هم‌زمان اجرا و پاسخ دلخواه را برای ادامه انتخاب کرد.

نصب تازه در حالت آفلاین آغاز می‌شود. OpenRouter Free سقف قیمت صفر دارد و مسیر پولی خودکار جایگزین نمی‌شود. [راهنمای فارسی ۰٫۶](docs/06-mrsad-fa.md) مسیر راه‌اندازی، محدودیت‌ها و منابع رسمی را توضیح می‌دهد. حساب ایمیلی اختیاری است: پیش‌نمایش به سرویس عمومی ایمیل متصل نیست و مالک برنامه باید [Supabase و SMTP](docs/05-studio-fa.md#راهاندازی-برای-مالک-برنامه) را تنظیم کند. حساب، گفتگوها را همگام نمی‌کند.

کلیدهای موتور در Android Keystore و با قید مقصد محافظت می‌شوند. نوشته‌های محلی در دیتابیس خصوصی برنامه، نه در دیتابیس جداگانهٔ رمزنگاری‌شده، هستند. پشتیبان‌گیری و انتقال Android غیرفعال است.

## ساخت از سورس

پیش‌نیاز: JDK کامل ۲۱، Android SDK Platform 36 و Build Tools 36.0.0. ساخت به Gradle، Google Maven، Maven Central و JitPack (فقط Tesseract4Android) نیاز دارد. `local.properties` و فایل و رمز امضا را commit نکنید.

```bash
cd dana-android
sdkmanager --licenses
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
./gradlew --no-daemon :core:domain:test :core:data:testDebugUnitTest :core:data:lintDebug :app:lintDebug :app:assembleDebug :app:assembleRelease
./gradlew :app:installDebug
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

در Windows از `gradlew.bat` استفاده کنید. خروجی دیباگ `app/build/outputs/apk/debug/app-debug.apk` است. برای انتشار `ai.dana`، از کلید انتشار پایدار خودتان در **Generate Signed Bundle / APK** استفاده کنید.

## راستی‌آزمایی

CI شامل build/lint، قراردادهای سرویس، Room، امضا و هم‌ترازی ۱۶ KiB، ذخیره‌سازی/Keystore و نصب همان APK در Android 15 است. مسیر `ACTION_ASSIST`، فرمان باتری با تأیید، ممانعت آفلاین، تصویر موقت، OCR و امکانات قبلی آزمایش شدند. این شواهد به‌معنی تست میکروفون/دوربین فیزیکی، تولید زنده با حساب ارائه‌دهنده یا ارسال ایمیل عمومی نیستند.

فونت [Vazirmatn v33.003](https://github.com/rastikerdar/vazirmatn/tree/v33.003) با SIL OFL 1.1 و دادهٔ [tessdata_fast 4.1.0](https://github.com/tesseract-ocr/tessdata_fast/tree/4.1.0) فارسی و انگلیسی با Apache 2.0 در APK قرار دارند. مجوزها در `app/src/main/assets/licenses/` همراه APK هستند.
