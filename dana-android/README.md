# mrsad · دستیار فارسی Android

اپ بومی Android با Kotlin و Compose: گفتگو، ساخت تصویر، OCR و فرمان‌های گوشی. کد اپ در این پوشه [با مجوز MIT](LICENSE) است؛ [مجوزهای اجزای ثالث](NOTICE.md) جداگانه‌اند. به پنل Python مخزن وابسته نیست.

## پیش‌نمایش ۰٫۸

**[دانلود مستقیم APK](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/mrsad-preview-44351667548-1/mrsad-preview.apk)** · [SHA-256](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/mrsad-preview-44351667548-1/SHA256SUMS.txt) · [راهنمای فارسی](docs/08-mrsad-fa.md) · [بیلد و آزمون‌ها](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44351667548) · [تصاویر شبیه‌ساز](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/44351667548/artifacts/14448044067)

Android 8+ · `ai.dana.mrsad.workspace` / `0.8.0-preview` / کد ۸. این APK دیباگ کنار نسخهٔ ۰٫۷ نصب می‌شود و داده‌ها خودکار منتقل نمی‌شوند. سورس دقیق: `fe6b7cfa7bf5cfdd035a93928d35b92d97ab8686` · فایل `45,927,570` بایت.

- **آفلاین:** «فرمان ← خواندن وضعیت باتری». به اینترنت، کلید یا مجوز میکروفون نیاز ندارد.
- **گفتگو:** «اتصال به هوش مصنوعی» ← انتخاب سرویس ← کلید شخصی ← «تست و اتصال». «سریع» زمینه و طول جواب را محدود می‌کند و مقصد را تغییر نمی‌دهد.
- **تصویر:** توصیف ← قالب ← ساخت ← ذخیرهٔ فایل اصلی. کلید AvalAI با دسترسی هر دو مدل می‌تواند برای گفتگو و تصویر استفاده شود.
- طراحی فارسی از مسیرهای عمومی هوشا الهام گرفته است؛ API یا حساب هوشا در برنامه استفاده نمی‌شود. OpenRouter Free به مدل پولی سوییچ نمی‌کند.

```text
108bb42dfe37670575078a715e70e7b7b5000a2cdff0d2ba59a477f5f6d58e4d  mrsad-preview.apk
```

APK در CI با پاسخ‌های HTTP کنترل‌شده و روی شبیه‌ساز Android 15 آزموده شده است؛ حساب آنلاین واقعی، انتخابگر فایل همهٔ دستگاه‌ها و سخت‌افزار گوشی تأیید نشده‌اند. [راهنمای ۰٫۸](docs/08-mrsad-fa.md) جزئیات راه‌اندازی و رفع خطا را دارد. [راهنمای ۰٫۷](docs/07-mrsad-fa.md) برای نسخهٔ قبلی است.

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
