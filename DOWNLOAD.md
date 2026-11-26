# NEUROX SENSE — دانلود / Download

<div dir="rtl">

## ⬇️ دانلود با یک کلیک

### [دانلود NEUROX-sense-debug.apk](https://raw.githubusercontent.com/mrsad2615-creator/vodiwalker_panel/apk-latest/NEUROX-sense-debug.apk)

روی همین لینک بزن. فایل ۱۳ مگابایتی شروع به دانلود می‌کند. این لینک **همیشه**
آخرین نسخه را می‌دهد و تغییر نمی‌کند.

**لینک مستقیم برای کپی کردن:**

```
https://raw.githubusercontent.com/mrsad2615-creator/vodiwalker_panel/apk-latest/NEUROX-sense-debug.apk
```

## نصب

1. فایل را روی گوشی اندروید **۸.۰ یا بالاتر** باز کن.
2. اندروید می‌پرسد «نصب از این منبع را مجاز می‌دانی؟» → **مجاز**.
3. اگر نسخهٔ قبلی نصب است، اول حذفش کن (نسخهٔ debug روی خودش به‌روزرسانی نمی‌شود).

شناسهٔ بسته `com.neurox.app.debug` است، پس در کنار نسخهٔ اصلی هم می‌نشیند.

</div>

---

## ⬇️ One-click download

### [Download NEUROX-sense-debug.apk](https://raw.githubusercontent.com/mrsad2615-creator/vodiwalker_panel/apk-latest/NEUROX-sense-debug.apk)

**Direct link:**

```
https://raw.githubusercontent.com/mrsad2615-creator/vodiwalker_panel/apk-latest/NEUROX-sense-debug.apk
```

13 MB. The link always serves the newest build and never changes.

## Install

1. Open it on Android **8.0+**.
2. Allow installs from this source when prompted.
3. Uninstall any previous copy first — a debug build will not upgrade over itself.

Package id is `com.neurox.app.debug`, so it installs beside a release build.

---

## About this file

It is the **first APK this project has ever produced.** Gradle had never run
before this pipeline existed.

### Verified inside the file

| Check | Result |
| --- | --- |
| File size | 13,003,999 bytes |
| Zip structure | 554 entries, valid |
| `AndroidManifest.xml` | present |
| `classes*.dex` | 19 files |
| `resources.arsc` | present |
| Vazirmatn fonts | 5 |
| Signature | `APK Sig Block 42`, scheme v2 — **installable** |
| Package id | `com.neurox.app.debug` |
| `minSdk` / `targetSdk` | 26 / 35 |

### Why it lives on a branch, not in Releases

Three separate repository settings rule out a Release, each discovered through a
different HTTP error:

| Setting | Error | Effect |
| --- | --- | --- |
| ruleset on ref creation | 422 `pre_receive` | no new tag may be created |
| immutable releases | 422 `already_exists` | an existing tag cannot gain assets |
| workflow permission = read | 422 `Validation Failed` | the token may not create releases |

Branch refs are still allowed, so the workflow commits the APK to an `apk-latest`
branch and `raw.githubusercontent.com` serves it. One click, no tag, no release.

## What the real build found

**Eleven defects**, none visible to the six offline checks that had reported green
for the project's entire life:

| # | First failure | What it revealed |
| --- | --- | --- |
| 1 | `Setup Android SDK` | a fragile CI step, not code |
| 2 | `Unresolved reference: ksp` | `core:data` used Room's processor without applying KSP — Room's SQL was never validated |
| 3 | `Could not resolve :core:audio` | an `android.library` required by a JVM module |
| 4 | `mainManifest ... doesn't exist` | **no manifest, no resources, no icon at all** |
| 5 | `Unresolved reference: presentation` | undeclared module dependencies |
| 6 | `Unresolved reference: JournalMode` | Room's enum is nested; my stub said otherwise |
| 7 | `Unresolved reference 'platform'` | a fully-qualified reference no import check sees |
| 8 | KSP plugin not found | `com.google.*` regex claimed KSP for Google Maven |
| 9 | `com.google.testing.platform` | the previous fix went too narrow |
| 10 | `installSplashScreen` | stub had the wrong API shape — twice |
| 11 | `unexpected minSdk` | my own verification, not the app |

Three were *caused by my stubs being wrong in the permissive direction* — the cost
of compiling against hand-written API declarations. Each is fixed in the code and
in the stub.

## The honest caveat

**This app has never run on a device.** Room's generated DAOs have never executed,
Compose has never drawn a frame, the fonts have never been measured, and no audio
cue has ever been played. The build is correct; the runtime is unknown.

It may crash on the splash screen. If it does, that is the next fix — and it is now
a fixable problem, because the pipeline produces a real signed APK on every push.

`NEUROX/docs/TEST_STATUS.md` lists precisely what is and is not verified.
