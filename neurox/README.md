# NEUROX — TRAIN YOUR POTENTIAL

پروژهٔ بومی Android برای تمرین شناختی و مهارتی، مستقل از پنل Python موجود در ریشهٔ مخزن.

**وضعیت: نقطهٔ عطف توسعهٔ 0.5.0-dev؛ محصول نهایی نیست.**

این پوشه شامل کد Kotlin/Compose، چهارده تمرین اجرایی در هسته، لایهٔ Room، تنظیمات DataStore، پوسته‌های فارسی/انگلیسی، استودیو رنگ، تمرین‌ساز محلی، رمزنگاری پشتیبان و آزمون‌ها است. وجود کد رابط کاربری به معنی تأیید اجرای آن روی Android نیست.

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

**APK توسعهٔ 0.5.0-dev ساخته شد و هر دو job ساخت و آزمون شبیه‌ساز موفق شدند.** [دریافت APK آزمایشی](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/45497991213/artifacts/14968932757)؛ ممکن است ورود به GitHub لازم باشد. کامپایل Android، تولید Room و lint موفق‌اند و ۴۴ آزمون شبیه‌ساز بدون شکست یا ردشدن اجرا شدند. آزمون‌های محلی نیز شامل ۲۳۰ مورد هسته/امنیت، ۴۴ بررسی ایستا، پنج بررسی SQLite و شش بررسی ابزار شواهد CI هستند. این APK با کلید debug امضا شده و نسخهٔ نهایی نیست. اندازه، هش، commit آزموده‌شده و محدودیت‌ها در [گزارش آزمون](docs/08-test-report.md) ثبت شده‌اند. ساخت در میزبان GitHub انجام شد؛ محدودیت دریافت Gradle/SDK و فایل APK در محیط محلی همچنان وجود دارد.

## بازنگری کیفیت پس از نقد کاربر

طراحی و کیفیت محتوای 0.5 مورد پذیرش کاربر نیست. موفقیت آزمون‌های زیر، این نقد را نقض نمی‌کند. بازنگری برای بزرگسالان، فارسی‌محور و با رنگ‌های خالص و ملایم شروع شده است. [دفتر بندبه‌بند درخواست‌ها](docs/15-reset-acceptance-fa.md) و [تفکیک تمرین/آزمون معتبر](docs/16-assessment-and-content-policy.md) وضعیت واقعی را ثبت می‌کنند. محیط `design/content-review/` فقط ابزار بررسی محتوای پیش‌نویس است؛ محصول نهایی، آزمون معتبر یا جایگزین برنامهٔ Android نیست.

## طراحی و پیشرفت تاریخی 0.5

نمونهٔ مرورگری ۲۰ صفحه، سیستم طراحی Quiet Precision، آشنایی قابل ردکردن، چهار استودیوی هم‌سطح، هدف روزانهٔ جدا، نمودار واقعی و شش دستاورد محاسبه‌شده افزوده شده‌اند. ۳۲۰ ترکیب چیدمان و ۸۰ ممیزی axe بدون شکست اجرا شدند؛ این شواهد، پذیرش Android نیستند. هنوز ۲۰ تمرین و قابلیت‌های اصلی مانند صدای مستقل و بازیابی یکپارچه باقی است.

- [پژوهش و نقد طراحی](docs/13-design-research.md)
- [سیستم طراحی و روش بازتولید نمونه](docs/14-design-system.md)
- [شواهد ساخت 0.5](docs/build-evidence-0.5.0-dev.json)

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
