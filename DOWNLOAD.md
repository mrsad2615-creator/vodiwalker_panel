# NEUROX SENSE — دانلود / Download

<div dir="rtl">

## ⬇️ دانلود مستقیم (آخرین نسخه)

[**NEUROX-sense-debug.apk**](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/latest/download/NEUROX-sense-debug.apk)

این لینک همیشه به **آخرین APK ساخته‌شده** اشاره می‌کند. یک کلیک، فایل شروع به دانلود می‌کند.

اگر این لینک کار نکرد، یعنی هنوز هیچ build موفقی نداشته‌ایم — به تب
[Actions](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
برو و آخرین اجرا را ببین. اولین اجرا باید دستی راه بیفتد و ممکن است چند اشکال
واقعی را آشکار کند؛ هر اشکال، خودش پیشرفت است.

## نصب

1. فایل APK را روی گوشی اندروید باز کن (اندروید ۸.۰ یا بالاتر).
2. اندروید می‌پرسد که اجازهٔ نصب از این منبع را می‌دهی؛ اجازه بده.
3. این یک نسخهٔ **debug** است: امضای موقت دارد، بنابراین روی نسخهٔ قبلی
   *به‌روزرسانی نمی‌شود*. اگر نسخهٔ قبلی نصب است، اول حذفش کن.

## این فایل چیست و چیست نیست

| | |
|---|---|
| ساخته‌شده توسط | GitHub Actions، از همین مخزن |
| بستهٔ برنامه | `com.neurox.app.debug` |
| اندازه | حدود ۱۰ مگابایت |
| زبان | فارسی و انگلیسی |

**صادقانه:** این اولین APK این پروژه است. در محیط توسعه دسترسی به Google Maven
نبود، پس Gradle هرگز آنجا اجرا نشد و این build اولین اجرای واقعی آن است. ممکن است
شکست بخورد. اگر شکست خورد، لاگ آن را برایم بفرست تا درستش کنم.

آنچه بدون گوشی تأیید شده و آنچه تأیید نشده، در
[`NEUROX/docs/TEST_STATUS.md`](NEUROX/docs/TEST_STATUS.md) نوشته شده.

</div>

---

## ⬇️ One-click download (latest build)

[**NEUROX-sense-debug.apk**](https://github.com/mrsad2615-creator/vodiwalker_panel/releases/latest/download/NEUROX-sense-debug.apk)

That URL always resolves to the newest successful build, so it never needs
updating. It is produced by
[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) on every
push that touches `NEUROX/`.

### Install

1. Open the APK on an Android 8.0+ device.
2. Allow install from this source when Android asks.
3. It is a **debug** build with a throwaway signature, so it will not upgrade over
   a previous copy — uninstall the old one first.

### Honest status

This is the **first APK this project has ever produced**. The development sandbox
could not reach Google Maven, so `./gradlew assembleDebug` has never run until this
workflow; it is entirely possible that it fails on a real dependency resolution
problem the offline stub gate could not see. If the download link 404s, no build
has succeeded yet — check the
[Actions tab](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
for the failure.

What *is* verified (323 unit tests, 16 modules compiling against stubs) and what is
not (Room SQL, Compose runtime behaviour, anything on a device) is spelled out in
[`NEUROX/docs/TEST_STATUS.md`](NEUROX/docs/TEST_STATUS.md).

### Why a debug APK

A release build would need a signing keystore held in repository secrets, and a key
that ships publicly installed apps is a key that must never leak. A debug APK is
installable, needs no secrets, and cannot be mistaken for a production release. When
a real release is wanted, `NEUROX/docs/BUILD.md` describes the keystore setup and the
workflow only needs a `release` variant and four secrets.
