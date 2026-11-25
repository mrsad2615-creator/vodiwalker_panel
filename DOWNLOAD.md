# NEUROX SENSE — دانلود / Download

<div dir="rtl">

## ✅ ساخت APK کار می‌کند — فقط انتشار یک تنظیم لازم دارد

بعد از یازده اجرای CI، **APK واقعاً ساخته می‌شود**: `assembleDebug` موفق می‌شود،
بستهٔ برنامه با `aapt2` و امضایش با `apksigner` بررسی می‌شود، و فایل به‌عنوان
artifact همان اجرا آپلود می‌شود. این اولین بار در تاریخ پروژه است.

اما مرحلهٔ آخر — گذاشتن فایل در یک Release برای لینک یک‌کلیکی — با خطای HTTP ۴۲۲
متوقف می‌شود، چون توکن پیش‌فرض GitHub Actions در این مخزن فقط **خواندن** دارد.

### برای فعال کردن لینک یک‌کلیکی (یک دقیقه)

1. برو به **Settings** مخزن → **Actions** → **General**
2. پایین صفحه، **Workflow permissions**
3. **Read and write permissions** را انتخاب کن و ذخیره کن
4. تب [Actions](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
   → **Run workflow**

بعد از آن، این لینک برای همیشه کار می‌کند:

```
https://github.com/mrsad2615-creator/vodiwalker_panel/releases/latest/download/NEUROX-sense-debug.apk
```

### تا آن موقع، APK را از کجا بگیرم؟

تب [Actions](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
→ آخرین اجرای سبز → پایین صفحه، بخش **Artifacts** → `neurox-sense-debug-apk`.
یک فایل zip می‌گیری که داخلش APK است.

</div>

---

## ✅ The APK builds. Publishing needs one setting.

After eleven CI runs, **the APK is real**: `assembleDebug` succeeds, the package is
checked with `aapt2`, the signature with `apksigner`, and the file is uploaded as a
workflow artifact. That is the first time in this project's history.

The final step — putting it in a Release so there is a one-click link — stops with
HTTP 422, because this repository's default `GITHUB_TOKEN` permission is **read**.

### To turn on the one-click link (one minute)

1. Repository **Settings** → **Actions** → **General**
2. Under **Workflow permissions**, choose **Read and write permissions**
3. Save, then re-run
   [Build APK](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)

After that this URL is permanent:

```
https://github.com/mrsad2615-creator/vodiwalker_panel/releases/latest/download/NEUROX-sense-debug.apk
```

### Until then

[Actions](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/workflows/build-apk.yml)
→ the latest green run → **Artifacts** → `neurox-sense-debug-apk`. The zip contains
the APK.

## Install

1. Open the APK on an Android 8.0+ device.
2. Allow install from this source when Android asks.
3. It is a **debug** build with a throwaway signature, installing as
   `com.neurox.app.debug`. It will not upgrade over another copy — uninstall the old
   one first.

## What this build is, honestly

**Eleven distinct defects** were found by running the real build, none of which the
six pre-existing offline checks could see:

| # | First real failure | What it revealed |
| --- | --- | --- |
| 1 | `Setup Android SDK` | tooling step, not code |
| 2 | `Unresolved reference: ksp` | `core:data` used Room's processor without applying KSP |
| 3 | `Could not resolve :core:audio` | an android.library required by a JVM module |
| 4 | `mainManifest ... doesn't exist` | **no manifest, no resources, no icon — at all** |
| 5 | `Unresolved reference: presentation` | undeclared module dependencies |
| 6 | `Unresolved reference: JournalMode` | Room's enum is nested; my stub said otherwise |
| 7 | `Unresolved reference 'platform'` | a fully-qualified reference no import check would see |
| 8 | KSP plugin not found | `com.google.*` regex sent KSP to Google Maven |
| 9 | `com.google.testing.platform` | the previous fix went too narrow |
| 10 | `installSplashScreen` | stub had the wrong API shape — twice |
| 11 | `unexpected minSdk` | my own verification, not the app |

Full detail in the commit log. The pattern is worth stating: every one of these was
invisible to checks that had been reporting green for the project's whole life, and
several were *created by* my stubs being wrong in the permissive direction — the
documented risk of compiling against hand-written API declarations.

## What is verified

- 323 unit tests on the pure layers (domain, games, security, audio, presentation).
- 20 modules compiling against API stubs with zero diagnostics, 5 manifests present,
  every module dependency declared.
- **The real Gradle build**, now: `assembleDebug` succeeds, and the resulting file is
  a signed APK with the expected package id and `minSdk`.

## What is still not verified

Room's generated DAOs have never executed, Compose has never rendered a frame, and
nothing has run on a device. `NEUROX/docs/TEST_STATUS.md` lists the rest.

The app may well crash on launch. If it does, that is the next thing to fix, and it
is now fixable — the build pipeline works.
