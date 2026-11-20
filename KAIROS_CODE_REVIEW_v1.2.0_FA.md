# ریویو فنی KAIROS — نسخه ۱.۲.۰ (Android)

**مخزن:** `mrsad2615-creator/vodiwalker_panel` · **تگ:** `kairos-v1.2.0` · **کامیت:** `74f67dd`
**مسیر کد:** `kairos-app/` · **تاریخ ریویو:** ۲۰۲۶-۱۱-۲۰ · **دامنه:** کل سورس (۱۲٬۹۲۱ خط TS/TSX + ۴۴۲ خط CSS + لایه‌ی نیتیو Capacitor)

---

## ۰) مهم‌ترین یافته‌ی ساختاری: کد KAIROS روی هیچ برنچی نیست

این اولین چیزی است که باید حل شود، چون بقیه‌ی کارهای تیمی به آن وابسته است:

| بررسی | نتیجه |
|---|---|
| `git ls-tree main -- kairos-app` | **۰ فایل** — روی `main` هیچ اثری از KAIROS نیست |
| آیا `kairos-v1.2.0` جد `main` است؟ | **خیر** (`merge-base --is-ancestor` → NO) |
| برنچ‌های حاوی این تگ | **هیچ برنچی** (نه لوکال، نه `origin/*`) |

زنجیره‌ی واقعی کامیت‌ها یک شاخه‌ی جانبی است:

```
74f67dd  feat(kairos): splash 120fps, habits, cinematic focus (v1.2.0)   <- kairos-v1.2.0
04c2357  fix(kairos): Android IME typing/backspace (v1.1.0)             <- kairos-v1.1.0
095a376  Publish direct-download KAIROS APK to GitHub Releases
ba9b4d3  Add KAIROS luxury productivity Android app and APK build workflow
78a6084  Enhance protocol labels and message formatting                  <- main / HEAD
```

**پیامدها:** کد فقط از طریق «تگ ریلیز» قابل دسترسی است؛ Code Review/PR ممکن نیست، `main` هرگز این فیچر را نمی‌گیرد، و هر بار build روی یک شاخه‌ی یتیم انجام می‌شود. همین موضوع باعث شد در گام اول پیدا نشود.

**راه‌حل پیشنهادی:** یک برنچ `feat/kairos-app` از `74f67dd` بساز و PR به `main` بزن (یا اگر KAIROS محصول مستقل است، ریپوی جدا با `kairos-app/` به‌عنوان ریشه). تا این کار انجام نشود، هر اصلاحی در ریسک گم‌شدن است.

> کد نسخه‌ی ۱.۲.۰ برای بررسی، در ورک‌اسپیس داخل `kairos-app/` استخراج شد (`git archive kairos-v1.2.0 kairos-app`).

---

## ۱) خلاصه‌ی مدیریتی

KAIROS یک اپ اندروید **آفلاین‌محور** با پوسته‌ی Capacitor 8 و رابط React 19 + TypeScript strict است: وظایف (ماتریس آیزنهاور)، عادت‌ها (روزانه/روزهای خاص/هدف هفتگی)، تقویم شمسی، تایمر تمرکز پومودورو/آزاد، تحلیل و مرور ماهانه/سالانه، پشتیبان‌گیری JSON. داده‌ها در IndexedDB (Dexie) روی دستگاه می‌مانند؛ اپ **هیچ مجوز شبکه‌ای ندارد**.

| محور | ارزیابی |
|---|---|
| معماری و ساختار | ۶/۱۰ — تفکیک feature/lib/context خوب، اما چند فایل غول‌آسا و Context چندنقشه |
| کیفیت کد | ۸/۱۰ — TypeScript strict، بدون `any`، بدون `@ts-ignore`، lint پاک |
| صحت منطق (توابع خالص) | ۸.۵/۱۰ — محاسبات تاریخ/تحلیل با گارد تقسیم‌برصفر و کلید ترکیبی یکتا |
| رفتار نیتیو اندروید | ۵/۱۰ — دکمه‌ی Back، پس‌زمینه‌رفتن تایمر، و زمان‌بندی اعلان‌ها مشکل دارند |
| CI/CD و انتشار | ۴/۱۰ — انتشار **APK دیباگ**، نسخه‌ی هاردکد، `typecheck` بی‌اثر |
| تست | ۵/۱۰ — ۱۳ تست، ۱ تست شکننده که در اجرای تمیز **fail** می‌شود |
| دسترس‌پذیری | ۵/۱۰ — `aria-label` برای ۷۱ دکمه از ۱۷۰ دکمه، زوم غیرفعال |
| حریم خصوصی/امنیت | ۸/۱۰ — بدون شبکه/ردیاب؛ اما `allowBackup` بدون قاعده‌ی استخراج داده |

**امتیاز کلی: ۶.۹/۱۰** — پایه‌ی مهندسی‌شده و تمیزی است؛ ایرادها بیشتر «یکپارچگی با اندروید» و «انتشار» هستند تا منطق دامنه.

---

## ۲) نقشه‌ی معماری

```
kairos-app/
├─ src/
│  ├─ App.tsx                 چرخه‌ی splash → AppShell → روت تب‌ها
│  ├─ context/AppContext.tsx  ★ ۴۹۶ خط: bootstrap DB + اعلان‌ها + تایمر تمرکز + مودال + Toast
│  ├─ features/               dashboard | tasks | habits | calendar | focus | analytics | settings
│  ├─ components/             charts (4) · focus/CinematicFocusTimer · layout/AppShell · ui (9)
│  ├─ lib/                    db (Dexie) · queries (liveQuery) · date (جلالی) · analytics (۱۰۲۱ خط) · notifications · native · ambientAudio · seed
│  ├─ types/domain.ts         مدل دامنه‌ی کامل و مستند
│  └─ test/                   4 فایل (analytics, date, db, integration)
└─ android/                   Capacitor 8 · minSdk 24 · target/compile 36 · versionName 1.2.0 · versionCode 3
```

جریان داده یک‌طرفه و درست است: UI → `db.ts` (نوشتن) → Dexie → `useLiveQuery` (`queries.ts`) → re-render. هیچ لایه‌ی سرور/شبکه‌ای وجود ندارد و این برای آفلاین‌بودن، انتخاب درستی است.

**نقاط قوت واقعی (نه تعارف):**
- `date.ts` کلید تاریخ محلی می‌سازد و صریحاً از `toISOString()` پرهیز می‌کند تا جابه‌جایی UTC روز را عوض نکند؛ به‌همراه `jalaali-js` و منطق هفته‌ی شنبه‌محور. اکثر اپ‌های فارسی همین‌جا باگ دارند.
- `HabitLog.id = ${habitId}::${dateKey}` — کلید قطعی، پس رکورد تکراری از نظر ساختاری ممکن نیست.
- تفکیک «ثبت‌نشده» از «missed» در مدل دامنه (`HabitLogStatus`) و در محاسبه‌ی adherence.
- پیکربندی Capacitor با دلیل مستند: `captureInput: false` برای IME فارسی، `webContentsDebuggingEnabled: false`، `allowMixedContent: false`.
- بکاپ نسخه‌دار (`version: 1`, `app:'KAIROS'`) و اعتبارسنجی حداقلی در import.
- ۱۸ `catch {}` بی‌صدا (ایراد پایین‌تر) ولی هیچ `as any` و `@ts-ignore` نداریم — انضباط تایپ خوب است.

---

## ۳) نتایج اجرای واقعی ابزارها (محیط تمیز، Node 22.22، نصب با `npm ci`)

| فرمان | نتیجه |
|---|---|
| `npm ci` | ✅ ۶۴۸ پکیج در ۱۲ ثانیه |
| `npm run typecheck` | ✅ سبز — **اما بی‌اثر است** (بند P1-۱۰) |
| `npx tsc -p tsconfig.app.json --noEmit` | ✅ سبز (این همان بررسی واقعی است) |
| `npm run lint` (eslint) | ✅ بدون هیچ خطا/هشدار |
| `npm test` (vitest) | ❌ **۱۲ pass / ۱ fail** — `app.integration.test.tsx` با `Test timed out in 5000ms` |
| `npm run build` | ✅ ۵.۲۶ ثانیه · `index` ۴۵۶.۵KB (gzip ۱۲۵.۶) · `vendor` ۱۴۰.۵KB · `db` ۱۰۳KB · CSS ۷۷KB |
| PWA precache | ۱۰۹ فایل / ۲٬۰۹۳ KiB |

---

## ۴) یافته‌ها به‌ترتیب اولویت

### 🔴 P0 — باگ‌هایی که کاربر واقعی حس می‌کند

**P0-1) دکمه‌ی Back اندروید مدیریت نمی‌شود؛ اپ ناگهان بسته می‌شود**
`@capacitor/app@8.1.2` در `package.json` هست و در `android/capacitor.build.gradle` هم لینک شده، اما در کل `src` **هیچ‌جا import نشده** (جست‌وجوی `@capacitor/app|backButton` → صفر نتیجه).
نتیجه: در هر صفحه‌ای (حتی وسط تایمر تمرکز یا مودال باز) با Back کل Activity بسته می‌شود.
**اصلاح:** در `native.ts` یک `setupBackButton()` با `App.addListener('backButton', …)` که به‌ترتیب: بستن مودال/نمای جزئیات → برگشت به تب داشبورد → `App.minimizeApp()`.

**P0-2) تایمر تمرکز با ساعت واقعی همگام نیست و با رفتن به پس‌زمینه از دست می‌رود**
`AppContext.tsx:224` هر ثانیه با `setInterval` از `remainingSeconds` کم می‌کند و `elapsedSeconds` را زیاد می‌کند؛ کل وضعیت در `useState` است (هیچ‌جا persist نمی‌شود). اندروید تایمرهای WebView در پس‌زمینه/صفحه‌خاموش را throttle می‌کند و با مرگ پروسه همه‌چیز صفر می‌شود. هیچ شنونده‌ی `visibilitychange`/AppState هم وجود ندارد.
نتیجه: جلسه‌ی ۲۵ دقیقه‌ای وقتی کاربر اپ را در پس‌زمینه می‌گذارد، ممکن است ۴ دقیقه ثبت شود یا کلاً از بین برود.
**اصلاح:** ذخیره‌ی `startedAt` + `accumulatedMs` و محاسبه‌ی elapsed از `Date.now()` در هر tick و در لحظه‌ی resume؛ persist کردن وضعیت در Dexie/`settings`؛ در صورت لزوم Foreground Service (پلاگین جدا) برای شمارش مطمئن.

**P0-3) اعلان‌ها بدون گرفتن مجوز «روشن» نشان داده می‌شوند**
`DEFAULT_SETTINGS.notificationsEnabled = true` (`db.ts:25`) اما `requestNotificationPermission()` فقط با دکمه‌ی تنظیمات صدا زده می‌شود (`SettingsPage.tsx:84`)؛ هیچ onboarding‌ای وجود ندارد. در `notifications.ts:166` اگر مجوز granted نباشد، بی‌صدا `return` می‌کند.
نتیجه: کاربر فکر می‌کند یادآور فعال است، هیچ یادآوری نمی‌گیرد.
**اصلاح:** در اولین ورود، درخواست مجوز + نمایش وضعیت واقعی مجوز در تنظیمات (الان `permState` فقط برای نمایش است و با `notificationsEnabled` قفل نشده).

**P0-4) هر sync، «همه‌ی» اعلان‌های معلق را لغو می‌کند**
`notifications.ts:159-161`: `getPending()` → `cancel()` روی **همه** اعلان‌ها، سپس حداکثر ۴۸ یادآور آینده را دوباره می‌سازد. این تابع با هر تغییر `allTasks` یا سوییچ اعلان اجرا می‌شود (`AppContext.tsx:150`).
نتیجه: اعلان آزمایشی (که با تأخیر ۴۰۰ms در `fireInstantNotification` زمان‌بندی می‌شود) و هر اعلان غیروظیفه‌ای ممکن است وسط کار حذف شود؛ همچنین churn مکرر AlarmManager روی هر ویرایش وظیفه.
**اصلاح:** شناسه‌های متعلق به خودمان را تگ بزن (`extra.kind='task-reminder'`) و فقط همان‌ها را diff/cancel کن؛ برای اعلان فوری از `schedule` با تأخیر استفاده نکن، مستقیم نمایش بده.

**P0-5) خروجی گرفتن از بکاپ در اندروید عملاً کار نمی‌کند، ولی پیام موفقیت می‌دهد**
`SettingsPage.tsx:113-124`: ساخت `Blob` + `a.download` + `a.click()`. WebView کپسیتور دانلود `blob:` را به DownloadManager منتقل نمی‌کند ⇒ فایلی ذخیره نمی‌شود، اما `pushToast('فایل پشتیبان ذخیره شد')` بلافاصله اجرا می‌شود.
**اصلاح:** `@capacitor/filesystem` (نوشتن در `Documents/`) + `@capacitor/share` (Share Sheet)، و toast فقط بعد از resolve موفق.

**P0-6) تله‌ی صفحه‌ی سیاه در Splash**
`App.tsx:29-33,61,73`: در ۳.۲ ثانیه `phase='done'` می‌شود ولی رندر اپ مشروط به `ready && phase !== 'playing'` است. اگر bootstrap (Dexie/seed) هرگز resolve نکند، splash محو می‌شود و کاربر یک صفحه‌ی خالی/مشکی دائمی می‌بیند. مسیر خطا هم در `AppContext` با `catch { setReady(true) }` پوشانده شده، بدون هیچ پیام.
**اصلاح:** در failsafe، اگر `ready=false` است، حالت «راه‌اندازی مجدد / پاک‌سازی داده» نشان بده.

**P0-7) هیچ ErrorBoundary وجود ندارد**
React 19 در صورت خطای رندر، کل درخت را unmount می‌کند ⇒ صفحه‌ی سیاه بدون هیچ ردی. جست‌وجوی `ErrorBoundary|componentDidCatch` → صفر.
**اصلاح:** یک کلاس ErrorBoundary با UI فارسی + دکمه‌ی «ارسال گزارش خطا» که در یک جدول Dexie ذخیره شود (چون شبکه نداریم).

### 🟠 P1 — ریسک محصولی و CI

**P1-8) ریلیز، APK **دیباگ** است**
`.github/workflows/kairos-android-apk.yml` (فقط داخل تگ): `./gradlew assembleDebug` و همان `app-debug.apk` به‌عنوان asset ریلیز آپلود می‌شود. `build.gradle:21` هم `minifyEnabled false` دارد.
پیامد: `android:debuggable=true`، بدون R8/Shrinker، حجم بیشتر، امضای debug، و قابل دیباگ بودن روی دستگاه کاربر.
**اصلاح:** keystore در Secrets + `assembleRelease` + `signingConfigs` + `minifyEnabled true` با `proguard-rules.pro`.

**P1-9) نسخه سه‌جا هاردکد شده و ناسازگار است**
`package.json:4` → `"1.0.0"` ｜ `android/app/build.gradle:10-11` → `versionCode 3` / `versionName "1.2.0"` ｜ workflow: نام فایل `KAIROS-v1.2.0.apk` و تگ `kairos-v1.2.0` در ۳ نقطه.
**اصلاح:** یک منبع حقیقت (`version.json` یا `git describe`) + تزریق در build و workflow؛ `versionCode` خودکار.

**P1-10) `npm run typecheck` عملاً هیچ چیزی را چک نمی‌کند**
`package.json` → `tsc --noEmit`؛ اما `tsconfig.json` ریشه `"files": []` + `references` است ⇒ این فرمان صفر فایل کامپایل می‌کند و همیشه سبز است (همین حالا هم سبز شد، درحالی‌که بررسی واقعی با `-p tsconfig.app.json` هم سبز است، ولی گارد از کار افتاده).
**اصلاح:** `"typecheck": "tsc -b --noEmit"` یا `tsc -p tsconfig.app.json --noEmit`.

**P1-11) تست یکپارچه شکننده/قرمز است**
اجرای تمیز: `12 passed / 1 failed` با `Test timed out in 5000ms` در `src/test/app.integration.test.tsx:27` (به تایمرهای واقعی splash وابسته است). چون CI همین `npm test` را اجرا می‌کند، build یا تصادفی قرمز می‌شود یا تصادفی سبز.
**اصلاح:** `vi.useFakeTimers()` + `advanceTimersByTime(2000)`؛ یا `testTimeout: 15000` در `vitest.config.ts`؛ و تست‌های مستقل برای focus/reminders.

**P1-12) Service Worker در پوسته‌ی نیتیو**
`vite-plugin-pwa` در `vite.config.ts` فعال است و ۱۰۹ فایل را precache می‌کند. در کپسیتور، SW روی origin محلی باقی می‌ماند و می‌تواند بعد از آپدیت اپ، assetهای قدیمی را سرو کند (کلاسیک‌ترین باگ «نسخه‌ی جدید نصب شد ولی چیزی عوض نشد»).
**اصلاح:** در build نیتیو PWA را غیرفعال کن (`mode === 'capacitor'`/env) یا در native، `navigator.serviceWorker.getRegistrations().then(r => r.forEach(x => x.unregister()))`.

**P1-13) Import بکاپ بدون تأیید و بدون اعتبارسنجی عمیق داده را پاک می‌کند**
`SettingsPage.tsx:129` + `db.ts importBackup`: فقط `app==='KAIROS'` و `Array.isArray(tasks)` چک می‌شود؛ سپس همه‌ی جداول `clear()` و bulkPut بدون هیچ تأیید کاربر. یک JSON ناقص/دستکاری‌شده = از دست رفتن داده.
**اصلاح:** تأیید دو مرحله‌ای، اعتبارسنجی shape هر آیتم (zod/valibot)، و گرفتن snapshot قابل بازگشت قبل از import.

**P1-14) `allowBackup="true"` بدون `dataExtractionRules`**
`AndroidManifest.xml:5`: داده‌ی IndexedDB در WebView storage می‌تواند در بکاپ ابری/`adb backup` قرار بگیرد؛ برای اپ شخصی/سلامت‌محور باید صریح تصمیم بگیری.
**اصلاح:** `android:dataExtractionRules` (API 31+) و `android:fullBackupContent` با `<include>/<exclude>` مشخص.

**P1-15) یادآور «از دست رفته» بازیابی نمی‌شود**
`AppContext.tsx:167-182`: فقط در بازه‌ی ۶۰ ثانیه‌ای که اپ در پیش‌زمینه است شلیک می‌کند و `setInterval` ۲۰ ثانیه‌ای دارد. اگر اپ بسته باشد و کاربر ۱۰ دقیقه بعد باز کند، هیچ یادآوری‌ای ثبت/نمایش نمی‌شود (بدون catch-up).
**اصلاح:** در bootstrap، وظایف سررسیدشده‌ی بی‌اعلان را به‌صورت «missed reminders» گزارش کن؛ اطمینان به `schedule` نیتیو به‌جای حلقه‌ی JS.

**P1-16) `SCHEDULE_EXACT_ALARM` بدون بررسی مجوز**
`AndroidManifest.xml:41`. از اندروید ۱۳/۱۴ این مجوز باید کاربر بدهد (یا `USE_EXACT_ALARM` با سیاست گوگل‌پلی). هیچ `checkExactAlarmPermission` ای وجود ندارد ⇒ اعلان‌ها به‌طور نامحسوس inexact می‌شوند.

### 🟡 P2 — کیفیت ساختار و عملکرد

**P2-17) فایل‌های غول‌آسا و Context چندنقشه**
`HabitsPage.tsx` ۱۲۶۳ خط · `analytics.ts` ۱۰۲۱ · `AnalyticsPage.tsx` ۷۳۴ · `FocusPage.tsx` ۷۰۲ · `DashboardPage.tsx` ۶۹۱ · `AppContext.tsx` ۴۹۶ (هم‌زمان: bootstrap، اعلان‌ها، تایمر تمرکز، مودال وظیفه، Toast).
**اصلاح:** استخراج هوک‌ها: `useFocusTimer`, `useReminders`, `useToasts`؛ و شکستن `analytics.ts` به `analytics/habits.ts`, `analytics/tasks.ts`, `analytics/reviews.ts`.

**P2-18) بدون code splitting**
`App.tsx` هر ۷ صفحه را eager ایمپورت می‌کند ⇒ چانک اصلی ۴۵۶KB (۱۲۵KB gzip) و همه‌ی صفحات+چارت‌ها در بوت لود می‌شوند.
**اصلاح:** `React.lazy` برای هر صفحه + `Suspense`، و lazy برای `lib/analytics` سنگین.

**P2-19) کوئری‌های تمام‌جدولی**
`queries.ts` همه‌جا `db.tasks.toArray()` / `db.habitLogs.toArray()` است و در ۲+ نقطه (AppContext و صفحات) تکرار می‌شود. برای ۲ سال داده یعنی هر تغییر کوچک، کل جداول را می‌خواند و re-render می‌کند.
**اصلاح:** برای داشبورد/هیت‌مپ از ایندکس‌های موجود (`dateKey`, `habitId`, `[habitId+dateKey]`) با `where().between()` استفاده کن.

**P2-20) حجم فونت‌ها و precache**
۴ خانواده فونت (Vazirmatn، Barlow Condensed، Space Grotesk، Manrope) با ۱۱ وزن، ۱۰۹ فایل precache ≈ ۲MB — فونت لاتین `barlow` فقط برای تیترهای لاتین استفاده می‌شود.
**اصلاح:** Vazirmatn variable یا subset، و حذف وزن‌های بی‌استفاده.

**P2-21) دسترس‌پذیری**
`index.html:7`: `maximum-scale=1.0, user-scalable=no` ⇒ زوم ممنوع (نقض WCAG 1.4.4). ۷۱ `aria-label` برای ~۱۷۰ دکمه (بیشتر دکمه‌های آیکونی بدون نام). `ModalSheet` focus trap ندارد و `Esc`/back فقط بسته‌ی تکی دارد.
**اصلاح:** حذف محدودیت زوم، `aria-label` برای همه‌ی دکمه‌های آیکونی، `role="dialog" aria-modal`, focus trap و بازگرداندن فوکوس.

**P2-22) اعداد جادویی در choreography**
`App.tsx:29,33` (۱۲۲۰/۳۲۰۰ms) + ۳۲۰/۳۴۰ms پراکنده، جدا از CSS اسپلش. با تغییر duration انیمیشن، همگام‌سازی می‌شکند.
**اصلاح:** یک ماژول `splashTimings.ts` با export مشترک و استفاده در CSS variable.

**P2-23) وابستگی مرده و ذخیره‌سازی ناهمگون**
`@capacitor/app` بدون استفاده (بند P0-1)؛ تنظیمات UI عادت‌ها در `localStorage` (`HabitsPage.tsx:60,69`) ولی بقیه در Dexie ⇒ این تنظیمات در بکاپ نمی‌آیند.
**اصلاح:** انتقال به جدول `settings` (یا یک جدول `prefs`) و حذف وابستگی مرده.

**P2-24) بدون اسکفولد مهاجرت دیتابیس**
`db.ts:43` فقط `version(1)` دارد. اولین تغییر schema بدون `version(2).upgrade()` باعث خطا/از دست رفتن داده می‌شود.
**اصلاح:** از همین حالا `version(2)` را با الگوی upgrade اضافه کن.

### 🔵 P3 — پولیش و نگهداشت

- **P3-25)** ۱۸ بلوک `catch {}` خالی — در فیلد هیچ ردی از خطا نمی‌ماند. حداقل یک لاگ حلقوی داخل Dexie.
- **P3-26)** `firedRemindersRef` (`AppContext.tsx:92`) هرگز پاک نمی‌شود؛ در نشست‌های طولانی رشد می‌کند.
- **P3-27)** `.slice(0, 48)` در `notifications.ts:172` بی‌صدا یادآورهای بیشتر از ۴۸ را حذف می‌کند؛ باید صفحه‌بندی/اولویت‌بندی شود.
- **P3-28)** متن‌های فارسی به‌صورت inline در کل UI (برای تک‌زبانه ایراد نیست، ولی هر i18n آینده را پرهزینه می‌کند).
- **P3-29)** کامنت‌های انگلیسی/فارسی مخلوط و برخی توضیحات طولانی درون‌خطی؛ بهتر است در `docs/` منتقل شوند.
- **P3-30)** `seed.ts` ۵۷۹ خط داده‌ی نمونه در باندل نهایی؛ می‌توان با `import()` فقط در اولین اجرا لود کرد.

---

## ۵) امنیت و حریم خصوصی (نگاه جداگانه)

**خوب:** هیچ `INTERNET` در manifest، `allowMixedContent:false`، `webContentsDebuggingEnabled:false`، بدون تحلیل‌گر/تبلیغ، بدون کلید/توکن هاردکد، بدون `dangerouslySetInnerHTML`/`eval`، ورودی‌های عددی نرمال‌سازی‌شده (`NumericInput` ارقام فارسی → لاتین).

**نیازمند تصمیم:** `allowBackup=true` + `POST_NOTIFICATIONS` + `SCHEDULE_EXACT_ALARM` (P1-14, P1-16)؛ و اینکه بکاپ JSON رمزنگاری‌نشده است — برای داده‌ی شخصی/سلامت، AES-GCM با کلید مشتق از passphrase کاربر ارزش دارد (نسخه‌ی ۲ فرمت بکاپ).

---

## ۶) برنامه‌ی پیشنهادی نسخه‌ی بعدی (v1.3.0)

| اولویت | کار | تخمین |
|---|---|---|
| ۱ | ادغام `kairos-app` در یک برنچ واقعی + PR (بند ۰) | ۳۰ دقیقه |
| ۲ | P0-1 Back button، P0-4 cancel-all، P0-5 بکاپ با Filesystem/Share | ۱ روز |
| ۳ | P0-2 تایمر مبتنی بر زمان واقعی + persist + reconcile پس از resume | ۱ روز |
| ۴ | P0-3/P0-6/P0-7 مجوز اعلان در onboarding + ErrorBoundary + بازیابی از Splash | ۰.۵ روز |
| ۵ | P1-8/P1-9 release signing + نسخه‌ی واحد | ۰.۵ روز |
| ۶ | P1-10/P1-11 typecheck واقعی + پایدارسازی تست‌ها | ۰.۵ روز |
| ۷ | P1-12/P1-13/P1-14 SW نیتیو، import امن، قواعد بکاپ | ۰.۵ روز |
| ۸ | P2-18/P2-19 lazy loading + کوئری ایندکسی | ۱ روز |
| ۹ | P2-21 دسترس‌پذیری + P2-17 شکستن فایل‌های بزرگ | ۲ روز |

**ممنتوم خوبی هست:** منطق دامنه و تاریخ/تحلیل واقعاً تمیز نوشته شده؛ بیشترین بازده از سه چیز می‌آید: (۱) کد روی برنچ واقعی، (۲) رفتار نیتیو اندروید (Back/پس‌زمینه/اعلان)، (۳) انتشار release امضاشده.
