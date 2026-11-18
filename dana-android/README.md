# mrsad · دستیار فارسی Android

اپ بومی Android با Kotlin و Compose: گفتگو، ساخت تصویر، OCR و فرمان‌های گوشی. کد اپ در این پوشه [با مجوز MIT](LICENSE) است؛ [مجوزهای اجزای ثالث](NOTICE.md) جداگانه‌اند. به پنل Python مخزن وابسته نیست.

## پیش‌نمایش ۰٫۷

**[دانلود مستقیم APK](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/mrsad-preview-44333657505-1/mrsad-preview.apk)** · [SHA-256](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/mrsad-preview-44333657505-1/SHA256SUMS.txt) · [راهنمای فارسی](docs/07-mrsad-fa.md) · [بیلد و آزمون‌ها](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44333657505) · [تصاویر شبیه‌ساز](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44333657505/artifacts/14441835128)

Android 8+؛ `ai.dana.mrsad` / `0.7.0-preview` / کد `7`. فایل `45,870,174` بایت؛ از commit `7715eeac1afddeafd765e009fd3ca1a2b835b207`. این پیش‌نمایش دیباگ کنار ۰٫۶ نصب می‌شود. حذف نسخهٔ قبلی لازم نیست و داده‌های آن خودکار منتقل نمی‌شوند. برای انتشار با امضای پایدار، APK انتشار خودتان را بسازید.

```text
383238fd44b98327c859b94b19f305312e8df6fc01d390c9ff3b423f8fa09145  mrsad-preview.apk
```

- **آزمایش بدون اینترنت:** در گفتگو `شارژ گوشی چقدره؟` را بفرستید.
- **اتصال:** «موتورها ← اتصال سریع»، سرویس، کلید یا کد مرورگر OpenRouter، «تست و اتصال». مدل پیشنهادی به‌صورت واقعی آزموده می‌شود؛ حساب ایمیلی mrsad لازم نیست. بعد از اجازهٔ اولیه به همان مقصد، ارسال متن یک‌مرحله‌ای است.
- **تولید تصویر:** «خانه ← ساخت تصویر» با سهمیهٔ تصویری Gemini یا OpenAI. پیش‌نمایش و ذخیرهٔ فایل اصلی؛ خواندن عکس با OCR بخش دیگری است.
- **مقایسه:** پاسخ‌ها و زمان مدل‌ها جدا دیده می‌شوند. یک فایل UTF-8 از سؤال، نتایج و وضعیت‌ها ذخیره کنید.

OpenRouter Free فقط به مسیر صفرهزینهٔ خودش می‌رود. حساب و دسترسی مدل‌ها تابع ارائه‌دهنده است. این نسخه با پاسخ‌های آزمونی و روی شبیه‌ساز Android 15 راستی‌آزمایی شده است؛ حساب‌های زنده یا رفتار گوشی فیزیکی تأیید نشده‌اند. [راهنمای ۰٫۷](docs/07-mrsad-fa.md) مراحل، خطاها و مستندات رسمی را دارد. [راهنمای ۰٫۶](docs/06-mrsad-fa.md) فقط برای نسخهٔ قبلی است.

## ساخت از سورس

JDK کامل ۲۱، Android SDK Platform 36 و Build Tools 36.0.0 لازم است. ساخت از Google Maven، Maven Central و JitPack (Tesseract4Android) استفاده می‌کند. هیچ کلید AI برای بیلد/آزمون fixture لازم نیست.

```bash
cd dana-android
sdkmanager --licenses
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
./gradlew --no-daemon :core:domain:test :core:data:testDebugUnitTest :core:data:lintDebug :app:lintDebug :app:assembleDebug :app:assembleRelease
./gradlew :app:installDebug
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

در Windows از `gradlew.bat` استفاده کنید. مسیر فایل دیباگ `app/build/outputs/apk/debug/app-debug.apk` است. برای `ai.dana` از **Generate Signed Bundle / APK** و کلید خصوصی انتشار خودتان استفاده کنید. `local.properties`، فایل امضا و کلید سرویس را commit نکنید. تنظیم [حساب ایمیلی اختیاری](docs/05-studio-fa.md) به یک پروژهٔ Supabase و SMTP عملیاتی نیاز دارد. هیچ متن یا کلیدی با ایمیل همگام نمی‌شود.
