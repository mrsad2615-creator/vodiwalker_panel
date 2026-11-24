# راهنمای نصب و محیط توسعه

این راهنما برای نسخهٔ توسعه است. در حال حاضر فایل APK تأییدشده‌ای تحویل نشده است.

## ابزارهای قابل‌استفاده در محیط فعلی

از ریشهٔ مخزن اجرا کنید:

```bash
neurox/scripts/bootstrap-tools.sh
neurox/scripts/test-core.sh
neurox/scripts/check-kotlin-syntax.sh
```

اسکریپت اول یک محیط اجرای Java 17 و کامپایلر Kotlin نصب می‌کند. این ابزار جایگزین Android SDK یا Android Studio نیست. بسته‌ها داخل `.local/tools/` ذخیره می‌شوند. این پوشه وارد مخزن نمی‌شود. Java ارائه‌شده در این روش فقط برای آزمون هسته استفاده می‌شود؛ برای توسعهٔ Android یک JDK کامل و به‌روز نسخهٔ ۱۷ نصب کنید.

## ساخت نسخهٔ آزمایشی Android

۱. JDK 17 را نصب کنید.
۲. Android SDK را نصب کنید.
۳. Platform 36 و Build Tools 36.0.0 را نصب کنید.
۴. مسیر SDK را در `ANDROID_HOME` یا `neurox/local.properties` تعیین کنید.
۵. اتصال به Google Maven، Maven Central و سرویس Gradle را بررسی کنید.
۶. دستور زیر را اجرا کنید:

```bash
cd neurox
./gradlew :app:assembleDebug :app:lintDebug
```

فقط پس از خروج موفق، وجود `app/build/outputs/apk/debug/app-debug.apk` را بررسی کنید. سپس با دستگاه آزمایشی متصل اجرا کنید:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

کلید debug برای انتشار عمومی مناسب نیست. تنظیم امضای نهایی هنوز انجام نشده است. فایل کلید یا گذرواژه را داخل کد یا Git قرار ندهید.

## رفع مشکل

- خطای دانلود Gradle: دسترسی شبکه و گواهی TLS را بررسی کنید. اعتبارسنجی TLS را خاموش نکنید.
- خطای SDK: مسیر SDK و نصب Platform 36 را بررسی کنید.
- خطای وابستگی: دسترسی به مخازن رسمی را بررسی کنید. مخزن ناشناس اضافه نکنید.
- نبود دستگاه: خروجی `adb devices` را بررسی کنید.
- خطای پایگاه داده در برنامه: از عمل تلاش دوباره استفاده کنید. برای رفع خطا، داده‌های کاربر را خودکار پاک نکنید.

پنل Python موجود در ریشه مستقل است و با این مراحل نصب یا اجرا نمی‌شود.
