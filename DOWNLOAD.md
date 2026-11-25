# NEUROX SENSE — دانلود / Download

<div dir="rtl">

## ✅ APK ساخته می‌شود — یک تنظیم تا لینک دائمی

بعد از دوازده اجرای CI، **APK واقعاً ساخته و تأیید می‌شود**:

- `./gradlew assembleDebug` موفق
- بسته با `aapt2` بررسی می‌شود (`com.neurox.app.debug`، `minSdk 26`)
- امضا با `apksigner` تأیید می‌شود
- فایل به‌عنوان artifact همان اجرا آپلود می‌شود

این اولین APK در تاریخ این پروژه است. Gradle در محیط توسعه هرگز اجرا نشده بود.

### چرا لینک یک‌کلیکی هنوز نیست

مخزن سه محدودیت دارد که هر سه با یک خطای HTTP متفاوت کشف شدند:

| تنظیم | خطای HTTP | چه می‌کند |
| --- | --- | --- |
| مجوز workflow = read | ۴۲۲ Validation Failed | توکن نمی‌تواند release بسازد |
| ruleset ساخت ref | ۴۲۲ pre_receive | تگ جدید مجاز نیست |
| releaseهای immutable | ۴۲۲ already_exists | release موجود قابل تغییر نیست |

### فعال‌سازی (۳۰ ثانیه)

**Settings** → **Actions** → **General** → پایین صفحه:

**Workflow permissions** → **Read and write permissions** → **Save**

سپس تب [Actions](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
→ **Run workflow**. بعد از آن این لینک دائمی می‌شود:

```
https://github.com/mrsad2615-creator/vodiwalker_panel/releases/latest/download/NEUROX-sense-debug.apk
```

### همین حالا APK را چطور بگیرم

تب [Actions](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
→ آخرین اجرای سبز → بخش **Artifacts** → `neurox-sense-debug-apk`

## نصب

1. APK را روی گوشی اندروید ۸.۰+ باز کن.
2. اجازهٔ نصب از این منبع را بده.
3. نسخهٔ **debug** است (`com.neurox.app.debug`) — روی نسخهٔ قبلی به‌روزرسانی
   نمی‌شود. اول نسخهٔ قبلی را حذف کن.

</div>

---

## ✅ The APK builds. One setting away from a permanent link.

Twelve CI runs in, the APK is real: `assembleDebug` succeeds, the package is
verified with `aapt2` (`com.neurox.app.debug`, `minSdk 26`), the signature with
`apksigner`, and the file is uploaded as a workflow artifact. It is the first APK
this project has ever produced — Gradle had never run before.

### Why there is no one-click link yet

Three separate repository settings block automatic publishing, each found through
a different HTTP error:

| Setting | HTTP error | Effect |
| --- | --- | --- |
| workflow permission = read | 422 Validation Failed | the token may not create a release |
| ruleset on ref creation | 422 `pre_receive` | no new tag may be created |
| immutable releases | 422 `already_exists` | an existing tag cannot gain an asset |

The first is the one that matters and the only one you need to change.

### Turn it on (30 seconds)

**Settings** → **Actions** → **General** → at the bottom,
**Workflow permissions** → **Read and write permissions** → **Save**, then
[re-run the workflow](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml).

That URL then works permanently:

```
https://github.com/mrsad2615-creator/vodiwalker_panel/releases/latest/download/NEUROX-sense-debug.apk
```

### In the meantime

[Actions](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
→ the latest green run → **Artifacts** → `neurox-sense-debug-apk`.

## Install

1. Open the APK on an Android 8.0+ device.
2. Allow installs from this source.
3. It is a **debug** build (`com.neurox.app.debug`); it will not upgrade over an
   existing copy, so uninstall the old one first.

## What the real build found

**Eleven defects**, none visible to the six offline checks that had been reporting
green for this project's entire life:

| # | First failure | What it revealed |
| --- | --- | --- |
| 1 | `Setup Android SDK` | a fragile CI step, not code |
| 2 | `Unresolved reference: ksp` | `core:data` used Room's processor without applying KSP — so Room's SQL was never validated |
| 3 | `Could not resolve :core:audio` | an `android.library` required by a JVM module |
| 4 | `mainManifest ... doesn't exist` | **no manifest, no resources, no icon at all** |
| 5 | `Unresolved reference: presentation` | undeclared module dependencies |
| 6 | `Unresolved reference: JournalMode` | Room's enum is nested; my stub said otherwise |
| 7 | `Unresolved reference 'platform'` | a fully-qualified reference no import check sees |
| 8 | KSP plugin not found | `com.google.*` regex claimed KSP for Google Maven |
| 9 | `com.google.testing.platform` | the previous fix went too narrow |
| 10 | `installSplashScreen` | stub had the wrong API shape — twice |
| 11 | `unexpected minSdk` | my own verification, not the app |

Three of these were *caused by my stubs being wrong in the permissive direction* —
the documented risk of compiling against hand-written API declarations. That is
the honest cost of the offline approach, and it is now paid down: every one of
these is fixed in both the code and the stubs.

## What is verified

- 323 unit tests on the pure layers.
- 20 modules compiling against stubs: zero diagnostics, 5 manifests, every module
  dependency declared (checked by `tooling/check_gradle_deps.py`).
- **The real Gradle build**: `assembleDebug` succeeds and the output is a signed
  APK with the expected package id and `minSdk`.

## What is still not verified

Room's generated DAOs have never executed, Compose has never rendered a frame,
and nothing has run on a device. The app may crash on launch. If it does, that is
the next thing to fix — and it is now fixable, because the build pipeline works.
`NEUROX/docs/TEST_STATUS.md` lists the rest.
