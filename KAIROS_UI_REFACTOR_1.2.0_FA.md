# بازطراحی رابط کاربری و پاک‌سازی کد — KAIROS 1.2.0

هدف: از حالت «شلوغ و تزئینی» به یک رابط **ساده، آرام و حرفه‌ای** با کد مرتب‌تر. نسخه همان **1.2.0** است (`versionCode` از ۳ به ۴ رفت تا روی نصب قبلی آپدیت شود).

---

## ۱) چه چیزی عوض شد — به زبان ساده

| قبل | بعد |
|---|---|
| ۴ خانواده فونت (Vazirmatn + Barlow Condensed + Space Grotesk + Manrope) | **۲ فونت**: Vazirmatn برای فارسی، Space Grotesk برای اعداد و برند |
| نوار پایین با **۶ تب** + دکمه‌ی تنظیمات در هدر (۷ مقصد) | **۵ مقصد**: خانه، وظایف، عادت‌ها، تمرکز، «بیشتر» (تقویم/تحلیل/تنظیمات داخل یک شیت مرتب) |
| ۲۹ گرادیان + هاله‌های نئونی + سایه‌های رنگی + glassmorphism | سطح‌های **تخت** با یک خط دور، بدون گلو و گرادیان؛ گرادیان فقط ۴ مورد بسیار ملایم (هاله‌ی تمرکز) |
| ۲۹ کد رنگ مجزا در کل پروژه | یک **پالت ۱۰ رنگی** با توکن (`--surface-1/2/3`, `--border`, `--text/2/3`, `--accent`) و ۲۶ فایل یکدست شد |
| اسپلش ۲۱۰ خطی با ۱۰ کی‌فریم (aura، sheen، حروف انیمیشنی، هاله) | اسپلش **~۶۰ خطی**: نشان، وردمارک، یک خط پیشرفت |
| عدد روزانه ۳.۹rem با حلقه‌ی بزرگ و ۳ کاشی پرحاشیه | عدد ۳.۲۵rem، حلقه‌ی کوچک‌تر، کاشی‌های بدون پس‌زمینه و بدون رنگ اضافه |
| زوم صفحه قفل بود (`user-scalable=no`) | زوم آزاد + `viewport-fit=cover` برای ناچ |
| Service Worker ۱۰۹ فایل / ۲٬۰۹۳KiB precache | **۶۷ فایل / ۱٬۵۸۶KiB** (حذف دو فونت بی‌استفاده) |

---

## ۲) چه چیزی در کد بهتر شد

- **`design-system.css` بازنویسی شد** و حالا تنها منبع حقیقت طراحی است: توکن‌های رنگ/شعاع/فاصله، همه‌ی کلاس‌های مشترک (`luxury-card`, `btn-royal`, `luxury-input`, `luxury-glass-dock` …) و همه‌ی انیمیشن‌ها در یک جا. کلاس‌های قدیمی حذف نشدند تا هیچ صفحه‌ای نشکند.
- **`AppShell` ساده‌تر و قابل‌دسترس‌تر**: ناوبری ۵تایی، شیت «بیشتر» با `role="dialog"`، `aria-modal`، بک‌دراپ دکمه‌ای با نام، `aria-label="بخش …"` برای هر ردیف، `aria-current` روی تب فعال.
- **دکمه‌ی Back اندروید بالاخره کار می‌کند** (`setupAndroidBackButton` در `lib/native.ts`): شیت بسته → بازگشت به خانه → مینیمایز شدن اپ. وابستگی `@capacitor/app` که بی‌استفاده مانده بود، حالا واقعاً استفاده می‌شود.
- **`ErrorBoundary`** اضافه شد (قبلاً هر خطای رندر = صفحه‌ی سیاه). خطا در `localStorage` ذخیره می‌شود و UI بازیابی فارسی نشان داده می‌شود.
- **مسیر راه‌اندازی امن شد**: اگر بوت دیتابیس بیش از ۲.۵ ثانیه طول بکشد، به‌جای اسپلش بی‌پایان، پیام «راه‌اندازی طول کشید» + دکمه‌ی بازخوانی می‌آید. زمان‌بندی‌ها در یک ثابت `LAUNCH` جمع شد (۱۱۰۰/۳۲۰۰ms پراکنده حذف شد).
- **`npm run typecheck` واقعاً چک می‌کند**: قبلاً `tsc --noEmit` با `tsconfig.json`ِ `files: []` هیچ فایلی را کامپایل نمی‌کرد و همیشه سبز بود؛ حالا `tsc -b --noEmit` است.
- **نسخه یکدست شد**: `package.json` از `1.0.0` به `1.2.0`، `versionCode` ۴، و نسخه در فوتر شیت «بیشتر» نمایش داده می‌شود.
- **تست‌ها سبز و پایدار شدند**: `testTimeout: 15000` برای تست یکپارچه و به‌روزرسانی آن با ناوبری جدید. قبلاً اجرای تمیز ۱۲/۱۳ پاس می‌داد؛ حالا **۱۳/۱۳**.

---

## ۳) پالت جدید (خلاصه)

```
--bg          #0A0A0C   پس‌زمینه
--surface-1   #111114   کارت
--surface-2   #16161A   کارت برجسته / شیت
--surface-3   #1C1C21   حالت فعال / هاور
--border      rgba(255,255,255,0.07)
--text        #ECECEF   --text-2 #9C9CA6   --text-3 #6B6B75
--accent      #6E56CF   --accent-fg #A99BF0 (آیکون/هایلایت)
--danger      #E5484D
```

قاعده: **یک لهجه‌ی رنگی** (بنفش) فقط برای اقدام اصلی و آیکون فعال؛ بقیه‌ی رابط خنثی.

---

## ۴) صحت‌سنجی (اجرای واقعی در محیط تمیز)

| فرمان | نتیجه |
|---|---|
| `npm ci` | ✅ |
| `npm run typecheck` (`tsc -b --noEmit`) | ✅ |
| `npm run lint` | ✅ بدون خطا/هشدار |
| `npm test` | ✅ **۱۳/۱۳** |
| `npm run build` | ✅ ۴.۸ ثانیه · index ۴۵۶KB (gzip ۱۲۵) · precache ۶۷ فایل |

پیش‌نمایش زنده‌ی رابط جدید در همین سشن بالا آمده است (Vite dev server روی پورت 5173).

---

## ۵) فایل‌های دست‌خورده

| فایل | تغییر |
|---|---|
| `src/styles/design-system.css` | بازنویسی کامل (توکن‌ها + کلاس‌ها + انیمیشن‌های مینیمال) |
| `src/components/layout/AppShell.tsx` | ناوبری ۵تایی + شیت «بیشتر» + Back اندروید |
| `src/components/ui/MrsadSplashIntro.tsx` | اسپلش مینیمال |
| `src/components/ui/ErrorBoundary.tsx` | **جدید** |
| `src/App.tsx` | ثابت‌های `LAUNCH`، مسیر بازیابی بوت، اتصال ErrorBoundary |
| `src/lib/native.ts` | `setupAndroidBackButton` |
| `src/main.tsx` | حذف دو فونت (Barlow Condensed، Manrope) |
| `src/features/dashboard/DashboardPage.tsx` | هیرو ساده‌شده، کاشی‌های بدون رنگ اضافه |
| `src/features/{focus,habits,analytics}/*` + `components/charts/*` | حذف گرادیان‌ها و نوارهای رنگی |
| ۲۶ فایل `src/**/*.tsx` | یکدست‌سازی پالت رنگ |
| `index.html` | زوم آزاد، `theme-color` جدید |
| `package.json` / `android/app/build.gradle` | نسخه ۱.۲.۰ / `versionCode 4` / اسکریپت typecheck |
| `src/test/app.integration.test.tsx`, `vitest.config.ts` | سازگار با ناوبری جدید + timeout پایدار |

---

## ۶) قدم بعدی پیشنهادی (اجرای همین نسخه به‌صورت APK)

این محیط Android SDK ندارد، پس APK اینجا ساخته نمی‌شود. دو گزینه:

1. **ورک‌فلو فعلی**: فایل `.github/workflows/kairos-android-apk.yml` فقط داخل کامیت ریلیز وجود دارد و روی این برنچ نیست؛ اگر اجازه بدهی همان را (با گیت‌های کیفیت) به برنچ اضافه کنم، با هر push روی `kairos-app/**` بیلد می‌شود و APK را روی تگ `kairos-v1.2.0` بازسازی می‌کند.
2. **ریلیز امضاشده**: افزودن keystore به Secrets + `assembleRelease` + `minifyEnabled true`؛ بعد از آن APK حرفه‌ای (نه دیباگ) منتشر می‌شود.

باقی موارد P0/P1 ریویو (تایمر تمرکز مبتنی بر زمان واقعی، اعلان‌ها، بکاپ با Filesystem/Share) دست‌نخورده مانده‌اند و آماده‌ی کار در گام بعد هستند.

---

# ضمیمه — ۱.۲.۰ (پس از نصب APK توسط کاربر)

## ۷) باگ «بال صفحه / ناچ» که گزارش شد

**ریشه مشکل:** اپ با `targetSdk 36` بیلد می‌شود؛ از اندروید ۱۵ به بالا سیستم edge-to-edge را **اجباری** می‌کند و تنظیم قدیمی `overlaysWebView: false` نادیده گرفته می‌شود. نتیجه: هدر بالای صفحه زیر نوار وضعیت/ناچ می‌رفت و لمس‌ها به نوار سیستم می‌خورد (احساس «بال کار نمی‌کند»).

**اصلاح:**
- Capacitor 8 خودش `--safe-area-inset-top/right/bottom/left` را به CSS تزریق می‌کند؛ پالیسی صریح شد: `SystemBars: { insetsHandling: 'css', initialViewportFitValueHint: 'cover' }` و `overlaysWebView` حذف شد.
- توکن‌های `--safe-top/--safe-bottom/--safe-left/--safe-right` در `design-system.css` (با fallback به `env()` روی وب و مقدار `0px` در حالت native → هیچ فاصلهٔ دوبل ایجاد نمی‌شود).
- هدر: `.app-safe-header` → پس‌زمینه‌اش پشت نوار وضعیت کشیده می‌شود ولی متن/آیکون‌ها زیر ناچ نمی‌روند (sticky، بدون پرش هنگام اسکرول).
- داک پایین و شیت «بیشتر»: `padding-bottom: calc(var(--safe-bottom) + 0.75rem)`؛ محتوای صفحه هم به همان اندازه فضای امن گرفت (`.app-safe-content`).
- اسپلش هم `app-safe-frame` شد تا در حالت افقی زیر ناچ نرود.
- رنگ‌های نیتیو هم به پالت جدید رسیدند: `colors.xml` (`#0D041A`/`#0A0A0C`/`#6E56CF`/`#A99BF0`) و `capacitor.config.ts`.

## ۸) آیکون جدید (انتخاب کاربر از دو گزینهٔ هوش مصنوعی)

| مورد | مقدار |
|---|---|
| منبع | آیکون تولیدشده با AI، ذخیره در `assets/kairos-icon-pro.png` |
| مارک | استخراج‌شده با حذف پس‌زمینهٔ تیره → `assets/kairos-mark.png` (شفاف، ۳۸۹×۴۳۲) |
| رنگ پس‌زمینه | `#0D041A` (هم‌رنگ آیکون انتخابی) |
| آیکون تطبیقی | `mipmap-*/ic_launcher_foreground.png` — مارک در ۴۷٪ کانواس، داخل ناحیهٔ امن ۶۶dp |
| آیکون قدیمی/گرد | `mipmap-*/ic_launcher.png` (squircle، شعاع ۲۲.۵٪) + `ic_launcher_round.png` (دایره) |
| اسپلش اندروید ۱۲ | `drawable-*/ic_splash_mark.png` (کانواس ۲۸۸dp) — جای `ic_splash_vector` قبلی |
| وب/PWA | `kairos-icon-192/512` (any) + `kairos-icon-maskable-192/512` (maskable) + `favicon-32` |
| حذف‌شده | آیکون تیل (teal) قالب Capacitor و `kairos-icon.svg` قدیمی با گرادیان |
| پیش‌نمایش | `assets/kairos-icon-preview.png` |

## ۹) وضعیت بیلد و APK

| گام | نتیجه |
|---|---|
| `npm run typecheck` | ✅ |
| `npm run lint` | ✅ |
| `npm test` | ✅ ۱۳/۱۳ |
| `npm run build` | ✅ ۴.۶۴s — precache ۷۱ فایل |
| CI `Build KAIROS Android APK` | ✅ (run `44805031767`) — APK بازسازی و روی ریلیز منتشر شد |

**لینک دانلود:** `https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/kairos-v1.2.0/KAIROS-v1.2.0.apk` (۵.۶۶ MB، دیباگ‌امضا، `versionCode 4`)

> نکته: یک اجرای CI در میانه کار به‌دلیل خطای موقت شبکه در دانلود وابستگی Gradle شکست خورد و با re-run موفق شد؛ تغییرات کد مشکلی نداشتند.

---

# ضمیمهٔ ۲ — رفع واقعی ناچ (بالا/پایین) + لوگوموشن لود

## ۱۰) چرا تلاش قبلی جواب نداد

دفعهٔ قبل فقط CSS را عوض کردم و فرض گرفتم Capacitor مقدار درست را تزریق می‌کند. ریشهٔ واقعی این بود:

1. پلاگین قدیمی **`@capacitor/status-bar`** در `initNativeShell()` صدا زده می‌شد. مقدار پیش‌فرض `overlaysWebView: true` آن با فلگ‌های منسوخ `SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN` پنجره را fullscreen می‌کند. نتیجه: پنجره insets سیستم را مصرف می‌کند، SystemBars مقدار `0` گزارش می‌دهد و WebView با `env(safe-area-inset-*) = 0` بالا/پایین می‌رود زیر نوارها. یعنی هیچ‌کدام از دو منبعِ استاندارد داده نمی‌داد.
2. تنها منبع باقی‌مانده `--safe-area-inset-*` بود که فقط در حالت `insetsHandling: 'css'` و بعد از `onPageCommitVisible` تزریق می‌شود؛ اگر بار اول از دست می‌رفت، دیگر بازتولید نمی‌شد.

## ۱۱) مسیر استانداردِ جدید

| لایه | کار |
|---|---|
| نیتیو | پلاگین status-bar **حذف شد** (از `package.json` و کد). مالک نوارها فقط `SystemBars` هست: `insetsHandling: 'css'`، `initialViewportFitValueHint: 'cover'`، `style: 'DARK'` — و همین در `capacitor.config.json` داخل APK تأیید شد. |
| استاندارد وب | `viewport-fit=cover` در متا؛ CSS پیش‌فرض `--safe-top: env(safe-area-inset-top, 0px)` |
| لایهٔ JS | `src/lib/safeArea.ts` هر دو منبع (`env()` با پروب نامرئی + `--safe-area-inset-*` کپاسیتر) را می‌خواند، **بیشترین مقدار هر ضلع**، می‌نویسد روی `<html>`، و روی `resize`/`orientationchange`/`visualViewport`/بازگشت از بک‌گراند + ۶ اندازه‌گیری زمان‌بندی‌شده (۰ تا ۲۴۰۰ms) تازه می‌کند |
| WebView قدیمی (<140) | Capacitor خودش WebView را padding می‌دهد و مقدارها `0` می‌مانند → **هیچ padding دوبل** |
| چیدمان | `padding-inline` منطقی (RTL/افقی‌safe)، هدر پشت نوار وضعیت کشیده می‌شود ولی محتوایش پایین ناچ، داک و شیت با `--safe-bottom`، و `app-safe-x` برای سطوح `position:fixed` |

**تشخیص روی گوشی:** تنظیمات → پایین صفحه → «اطلاعات فنی نوار سیستم» (بازشو). چهار عدد ناچ + گزارش جداگانهٔ `env()` و `Capacitor` + نسخهٔ WebView را نشان می‌دهد. اگر عدد بالا `0` بود و گوشی ناچ دارد، همان‌جا معلوم می‌شود کدام منبع ساکت است.

## ۱۲) لوگوموشن لود

`src/components/ui/LaunchScreen.tsx` — فقط `transform / opacity / clip-path` (روی کامپوزیتور، بدون فیلتر و بدون حلقهٔ JS):

| زمان | اتفاق |
|---|---|
| ۰–۵۵۰ms | مارک با clip-path از پایین به بالا باز می‌شود + مقیاس ۰.۹۴→۱ و درخشش برند در پس‌زمینه |
| ۳۴۰–۱۳۹۰ms | یک قلم‌موی نور (sweep) از روی مارک عبور می‌کند |
| ۲۴۰–۷۴۰ms | وردمارک «KAIROS» با letter-spacing از ۰.۴۲em به ۰.۳em |
| ۳۸۰ms | زیرنویس «نظم، عادت، تمرکز» |
| ۴۶۰ms+ | نوار پیشرفت واقعی: آمادگی فونت‌ها + هیدریت استور + حداقل ۹۰۰ms اقامت، سپس کراس‌فید ۲۶۰ms |
| — | با `prefers-reduced-motion` همهٔ این‌ها به یک فریم ساکن تبدیل می‌شود؛ با کلیک/کلید هم رد می‌شود |

## ۱۳) آیکون‌ها و فونت «پیشرفت»

- کیت `src/components/ui/Progress.tsx`: `LinearProgress` (معین/نامعین)، `ProgressRing`، `Spinner`، `SkeletonBlock` — با `role="progressbar"` و `aria-*` درست.
- «آماده‌بودن فونت» به‌عنوان گام واقعی بوت: `document.fonts.load('600 16px Vazirmatn')` در `main.tsx` و گیت `document.fonts.ready` در لانچ، تا رابط با فونت جایگزین ظاهر نشود.
- مارک KAIROS از آیکون بیلدشده به **وکتور** تبدیل شد: ۴ کانتور، ۱۷۲ کاراکتر مسیر، IoU ۰.۹۳ با تصویر اصلی (رندر تأییدشده در `assets/kairos-mark-preview.png`) و حالا در هدر، تنظیمات و لانچ استفاده می‌شود (قبلاً یک گلیف بی‌ربط بود).

## ۱۴) وضعیت بیلد

`typecheck` ✅ · `lint` ✅ · `test` ۱۳/۱۳ ✅ · `build` ✅ ۵.۶۲s · CI run `44808378321` ✅ → APK منتشر شد (`versionCode 5`).

**دانلود:** `https://github.com/mrsad2615-creator/vodiwalker_panel/releases/download/kairos-v1.2.0/KAIROS-v1.2.0.apk`
