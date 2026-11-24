# NEUROX — TRAIN YOUR POTENTIAL

پروژهٔ بومی Android برای تمرین شناختی و مهارتی، مستقل از پنل Python موجود در ریشهٔ مخزن.

**وضعیت: نقطهٔ عطف توسعهٔ 0.2.0-dev؛ محصول نهایی نیست.**

این پوشه شامل کد Kotlin/Compose، هشت تمرین اجرایی در هسته، لایهٔ Room، تنظیمات DataStore، پوسته‌های فارسی/انگلیسی، استودیو رنگ، رمزنگاری پشتیبان و آزمون‌ها است. وجود کد رابط کاربری به معنی تأیید اجرای آن روی Android نیست.

## اجرای آزمون‌های قابل‌اجرا در این محیط

از ریشهٔ مخزن:

```bash
neurox/scripts/bootstrap-tools.sh
neurox/scripts/test-core.sh
neurox/scripts/check-kotlin-syntax.sh
```

اگر JDK نصب است، `JAVA_HOME` را تعیین کنید. متغیر `KOTLINC` مسیر کامپایلر را برای `test-core.sh` عوض می‌کند. ابزارهای دانلودشده در `.local/` هستند و وارد Git نمی‌شوند. ابزار ممیزی نحو به Kotlin 2.2.21 محدود شده و فقط نحو را بررسی می‌کند؛ جایگزین کامپایل Android نیست.

## ساخت Android در محیط مناسب

1. JDK 17 را نصب کنید.
2. Android SDK Platform 36 و Build Tools 36.0.0 را نصب کنید.
3. متغیر `ANDROID_HOME` یا فایل `local.properties` را تنظیم کنید.
4. به Google Maven، Maven Central و سرویس دانلود Gradle دسترسی بدهید.
5. دستورهای زیر را اجرا کنید:

```bash
cd neurox
./gradlew :testing:run :app:assembleDebug :app:lintDebug
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

مسیر مورد انتظار پس از ساخت موفق: `app/build/outputs/apk/debug/app-debug.apk`.

**در این محیط APK ساخته نشده است.** دریافت Gradle با خطای SSL و دریافت Android SDK به دلیل محدودیت شبکه متوقف شده است. Workflow موجود نیز هنوز اجرا و تأیید نشده است. نسخهٔ نهایی نباید با کلید debug منتشر شود.

## اسناد

- [ممیزی و برنامه](docs/00-audit-and-plan.md)
- [معماری](docs/01-architecture.md)
- [نیازمندی‌های محصول](docs/02-product-requirements.md)
- [مشخصات نرم‌افزار](docs/03-software-requirements.md)
- [طراحی رابط](docs/04-ui-design.md)
- [مدل داده](docs/05-data-model.md)
- [امنیت](docs/06-security.md)
- [برنامهٔ آزمون](docs/07-test-plan.md)
- [گزارش آزمون](docs/08-test-report.md)
- [راهنمای نصب](docs/09-installation-fa.md)
- [راهنمای کاربر](docs/10-user-guide-fa.md)
- [راهنمای نگهداری](docs/11-maintenance.md)
- [گزارش تحویل و محدودیت‌ها](docs/12-release-notes.md)

هیچ ادعایی دربارهٔ تشخیص یا درمان پزشکی، انطباق کامل ASD-STE100 یا آمادگی انتشار نهایی مطرح نمی‌شود.
