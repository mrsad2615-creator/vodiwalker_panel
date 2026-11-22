# KAIROS — prompts in English, and one for a coding model

Three ready-to-paste prompts:

1. **A. English product prompt** — same brief as the Persian one, for models that reason better in English.
2. **B. Coding-model prompt** — a spec-driven brief that names the real files, the real data model, and the real gates in this repo, so a code model proposes changes that can actually be applied.
3. **C. Short "diff-only" variant** — when you already know which feature you want and just need the implementation plan.

---

## A. English product prompt (paste as one message)

```text
You are a product architect and UX designer for an offline, Persian-language productivity app.
The app is KAIROS, used daily by one power user on Android. It already exists and works.
My goal is deeper and more capable — not prettier.

Answer in English. Keep technical terms in English.

──────────────── HARD FACTS (do not change these) ────────────────
• Android app built with Capacitor. UI: React + TypeScript + Vite + framer-motion, fully RTL Persian.
• Data: IndexedDB via Dexie. No backend, no account, no cloud, no third-party API. 100% offline.
• Backup: JSON export/import. Also CSV export for spreadsheets.
• Audio: native Java engine (AudioTrack, 48 kHz stereo, foreground service). No audio files at all —
  everything is synthesised, and playback survives a locked screen.
• Timer: native service with a chronometer notification, synthesised end-of-session bell, headset buttons.
• Calendar: true Jalali (Solar Hijri), not a converted Gregorian one.
• APK ≈ 5 MB. 179 automated tests.
• The visual language "Lumen" is fixed (single accent identity across the app).

──────────────── WHAT ALREADY EXISTS ────────────────
1) HOME — three board modes (cinematic / compact / detailed, instant and persistent); live cards: today's
   focus ring + 14-day strip, today's tasks, a horizontal habit rail (one tap logs today), trend sparkline,
   upcoming calendar, active goals; today's journal (mood 1–5, one highlight line, note) with a day streak;
   daily progress %, overdue warning, quick-start focus, global search.
2) TASKS — kanban + list + Eisenhower matrix over one dataset; Jalali due date, time, priority, quadrant,
   category, tags, subtasks; recurrence; reminders with custom lead time; archive/restore; postpone counter;
   filters (today / upcoming / overdue / unscheduled / recently completed / archive).
3) HABITS — schedule: daily, chosen weekdays (Sat–Fri), or "N times per week"; tracking: boolean, numeric
   with a unit, or partial; per-habit icon and accent; daily reminder; archive keeps history; home rail +
   week matrix + annual heatmap (horizontal and vertical) + card/compact layouts + per-habit detail with
   adherence and best streak.
4) FOCUS — pomodoro (configurable blocks), interval (block count + focus/rest), free countdown;
   auto-advance; do-not-disturb during a session; attach a session to a task; real duration recorded;
   full-screen cinema mode; session timeline; sound studio with 11 layers (rain, ocean, wind, fire, forest,
   cafe, brown/pink/white noise, drone, binaural), independent mixing, presets, master; native engine that
   keeps playing behind the lock screen.
5) CALENDAR — Jalali month view, full-year view (all 365 days, dense and precise), day view; tasks and
   habits on their days; rescheduling; .ics import.
6) ANALYTICS — overview (trend, two-window comparison, task breakdown), monthly review, annual review.
   Every number is computed from the user's own rows; nothing is invented. CSV export, shareable text report.
7) SETTINGS — display name, daily focus goal, daily task goal, pomodoro/interval defaults, per-type
   notification toggles, haptics, sound, JSON backup/restore, CSV, sample data.

──────────────── NON-NEGOTIABLE CONSTRAINTS ────────────────
• Fully offline: no server, no account, no sync, no online AI.
• Persian + RTL, Jalali dates. Timer digits must never look English or left-aligned.
• "More features" means depth, not decoration: each proposal must produce or consume real data.
• No fabricated metrics. If the data isn't there, the UI must stay honestly empty.
• One accent identity. No per-section colour schemes. Navigation stays the grouped rail.
• Audio is synthesis only — no audio assets.
• One hand, at most three taps for any action.
• Android-first; APK size must not jump.
• No feature may break existing user data (a Dexie migration is required if the model changes).

──────────────── WHAT I WANT BACK ────────────────
STEP 1 — Name the three real structural weaknesses of this product (unclosed loops, data that is captured
but never used, decisions the user still makes by hand every day).
STEP 2 — 15 genuinely deeper features in one table: name · what problem it solves · which section it lives in ·
new data required · effort S/M/L · risk · why it fits the constraints. At least 4 must be "local
intelligence" (analysis over the user's own rows, no server: prediction, pattern discovery, coaching) and at
least 3 must connect two existing sections into one new loop.
STEP 3 — For the top 5: step-by-step user flow; data-model change plus migration; exact UI placement
(which tab, which card, when it appears); empty and error states; acceptance criteria written as
"When … then …"; and 2–3 automated tests I should write.
STEP 4 — A short list of tempting features that would be poison here, and why (subscriptions, manual-entry
burden, decorative dashboards, cloud anything).

RULES
• Ask at most 3 clarifying questions, then proceed.
• Never justify a feature with "could be useful". Say which task it removes from the user's day.
• If you borrow from Todoist, Things, TickTick, Forest or Notion, also say what must be dropped so it fits —
  do not copy.
```

---

## B. Coding-model prompt (for a model that will edit this repo)

```text
You are working in an existing React + TypeScript + Capacitor repo: kairos-app/ (Persian RTL productivity
app, Dexie storage, native Java audio + timing services under android/app/src/main/java/app/kairos/productivity/).

Map of the code you must respect:
  src/App.tsx, src/components/layout/AppShell.tsx      — shell, tab routing, grouped nav rail
  src/context/AppContext.tsx                           — boot, settings, toasts, active tab
  src/types/domain.ts                                  — Task, Habit, HabitLog, FocusSession, Goal,
                                                         JournalEntry, UserSettings (source of truth)
  src/lib/db.ts                                        — Dexie schema, versions 1–3, all write helpers
  src/lib/queries.ts                                   — useLiveQuery hooks (useTasks, useHabits, …)
  src/lib/homeMetrics.ts, src/lib/analytics.ts         — pure arithmetic behind every number on screen
  src/lib/date.ts, format.ts, journal.ts, ics.ts, csv.ts, backup.ts, search.ts, sections.ts
  src/features/dashboard/*, tasks/*, habits/*, focus/*, calendar/*, analytics/*, journal/*, settings/*
  src/lib/chrono/*                                     — focus timer config/plan/persistence/native
  src/lib/audio/*                                      — controller, web engine, layers, breathing
  src/test/*                                           — 179 tests; these are the contract

Gates that must stay green (run locally, and CI runs the same):
  npm run typecheck   (tsc -b --noEmit)
  npm run lint        (eslint src)
  npx vitest run      (all files)

Rules for any change you propose or write:
1. Every new number on screen must be computed in src/lib/* from real rows. No placeholder metrics.
2. New persisted fields go through a new Dexie version() with an explicit migration; never mutate v1–v3.
3. Persian copy, RTL-correct, Jalali dates via src/lib/date.ts. Never render raw ISO dates or English digits
   in a timer.
4. Reuse existing primitives (Rail, LuxuryCard, SectionHeader, Numeric, ModalSheet, EmptyState, JalaliDatePicker)
   and the single accent variable system. Do not introduce a new colour per section.
5. Nothing may depend on the network at runtime. No new remote calls, no fonts, no analytics SDKs.
6. Audio must remain synthesis-only; native changes go in NativeSoundscape/KairosAudioPlugin/SoundscapeService
   and must keep the foreground-service behaviour.
7. Add tests in src/test/ for anything user-visible: one for the happy path, one for the empty state, and one
   for persistence where relevant.

Task: <نام قابلیت مورد نظر را اینجا بگذار>
Deliver, in this order:
  (a) files to touch, one line each on why;
  (b) the data-model diff (new fields/tables + migration code sketch);
  (c) the component-level diff sketch (props, state, where it mounts);
  (d) empty/error/edge cases;
  (e) the acceptance criteria as "When … then …";
  (f) the test list.
Do not rewrite unrelated files. Keep diffs minimal and reviewable.
```

---

## C. Short variant (when you already know what you want)

```text
In kairos-app (React+TS+Capacitor, Dexie, Persian RTL, offline), implement: <feature>.
Constraints: real data only, no network, Jalali dates, Persian copy, one accent, new Dexie version for new
fields, tests in src/test, typecheck+lint+vitest must pass.
Give me: files to touch, data-model diff, component diff, edge cases, acceptance criteria, tests.
```

> اگر پرامت را عوض کردی (مثلاً فقط «ایده بده، کد نه»)، بگو تا نسخهٔ کوتاه‌شده‌اش را بسازم.
