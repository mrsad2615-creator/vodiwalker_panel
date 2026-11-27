import { useMemo, useState } from 'react';
import {
  Check,
  CheckCircle2,
  Maximize2,
  Minimize2,
  Minus,
  Pause,
  Play,
  Plus,
  RotateCcw,
  SkipForward,
  Sliders,
  Trash2,
} from '@/components/ui/icons';
import { deleteFocusSession, setTaskStatus, updateSettings } from '@/lib/db';
import { useFocusSessions, useTasks } from '@/lib/queries';
import { addDays, formatJalaaliShort, getTodayKey } from '@/lib/date';
import { formatDurationShort } from '@/lib/format';
import { useApp } from '@/context/AppContext';
import { Numeric } from '@/components/ui/Numeric';
import { ComparisonChart } from '@/components/charts/ComparisonChart';
import { NumericInput } from '@/components/ui/NumericInput';
import { CinematicFocusTimer } from '@/components/focus/CinematicFocusTimer';
import { ChronoTimeline } from '@/components/focus/ChronoTimeline';
import { SoundStudio } from '@/components/focus/SoundStudio';
import { SOUND_PROFILES } from '@/lib/audio/layers';
import { requestSound } from '@/lib/audio/commands';
import { CHRONO_MODES } from '@/lib/chrono/plan';
import { isNativeAndroid } from '@/lib/native';
import { chronoConfigFromSettings, modeHint, modeLabel } from '@/lib/chrono/config';
import { phaseDurationSeconds, segmentProgress } from '@/lib/focusTimer';
import { hasProgress } from '@/lib/chrono/persistence';

const PRESET_DURATIONS = [15, 25, 45, 60, 90] as const;

export function FocusPage() {
  const {
    settings,
    focus,
    setFocusMode,
    setFocusDurations,
    setChronoConfig,
    seekFocusSegment,
    setAutoAdvance,
    setDndInFocus,
    setFocusLinkedTask,
    startOrResumeFocus,
    pauseFocus,
    resetFocus,
    finishAndSaveFocus,
    skipPomodoroPhase,
    pushToast,
  } = useApp();

  const [showCustomConfig, setShowCustomConfig] = useState(false);
  const [cinemaMode, setCinemaMode] = useState(false);
  const [confirmation, setConfirmation] = useState<{ text: string; run: () => void | Promise<void> } | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);
  const requestSessionChange = (text: string, run: () => void) => {
    if (hasProgress(focus)) setConfirmation({ text, run });
    else run();
  };

  const todayKey = getTodayKey();
  const tasks = useTasks();
  const sessions = useFocusSessions();

  const availableTasks = useMemo(
    () => tasks.filter((t) => !t.archived && t.status !== 'completed'),
    [tasks]
  );

  const linkedTask = useMemo(
    () => tasks.find((t) => t.id === focus.linkedTaskId) ?? null,
    [tasks, focus.linkedTaskId]
  );

  const recentSessions = useMemo(
    () =>
      sessions
        .slice()
        .sort((a, b) => b.endedAt.localeCompare(a.endedAt))
        .slice(0, 8),
    [sessions]
  );

  const todayTotalMin = useMemo(
    () =>
      Math.round(
        sessions
          .filter((s) => s.dateKey === todayKey)
          .reduce((acc, s) => acc + s.durationMinutes, 0)
      ),
    [sessions, todayKey]
  );

  const dailyGoalMin = Math.max(15, settings.dailyFocusGoalMinutes || 120);
  const dailyGoalPercent = Math.min(100, Math.round((todayTotalMin / dailyGoalMin) * 100));

  const plan = focus.plan ?? [];
  const chronoConfig = chronoConfigFromSettings(settings);
  const totalPhaseSeconds = phaseDurationSeconds(focus);

  // Every mode is a plan now, so progress is simply "how far into this segment".
  // The open stopwatch has no total, so it sweeps an hour and wraps — it is a
  // clock face, not a countdown.
  const progressPercent = Math.max(
    0,
    Math.min(
      100,
      totalPhaseSeconds > 0
        ? segmentProgress(focus, focus.remainingSeconds) * 100
        : ((focus.elapsedSeconds % 3600) / 3600) * 100
    )
  );

  const adjustFocusMinutes = (delta: number) => {
    const next = Math.max(5, Math.min(180, focus.focusMinutes + delta));
    setFocusDurations(next, focus.shortBreakMinutes, focus.longBreakMinutes);
    void updateSettings({ pomodoroFocusMin: next });
  };

  return (
    <div className="space-y-4">
      {confirmation && (
        <div role="dialog" aria-modal="true" aria-label="تأیید تغییر جلسه" className="fixed inset-0 z-[100] flex items-center justify-center bg-black/75 p-4">
          <div className="w-full max-w-sm space-y-4 rounded-2xl border border-[var(--border)] bg-[var(--surface-1)] p-5 text-sm leading-7 text-[var(--text)]">
            <p>{confirmation.text}</p>
            <div className="flex gap-2">
              <button type="button" disabled={confirmBusy} onClick={async () => {
                setConfirmBusy(true);
                try { await confirmation.run(); setConfirmation(null); }
                catch { pushToast({ title: 'عملیات انجام نشد', tone: 'warning' }); }
                finally { setConfirmBusy(false); }
              }} className="btn-royal min-h-11 rounded-xl px-4 disabled:opacity-50">تأیید</button>
              <button type="button" disabled={confirmBusy} onClick={() => setConfirmation(null)} className="btn-ghost-luxury min-h-11 rounded-xl px-4">انصراف</button>
            </div>
          </div>
        </div>
      )}
      {/* FULL-SCREEN CINEMA FOCUS SANCTUARY OVERLAY */}
      {cinemaMode && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="حالت سینمایی تمرکز"
          className="fixed inset-0 z-[80] flex flex-col items-center justify-between overflow-y-auto bg-[var(--bg)] px-6 py-8"
          style={{
            backgroundImage:
              'radial-gradient(ellipse 70% 40% at 50% 40%, color-mix(in srgb, var(--accent) 10%, transparent), transparent 70%)',
          }}
        >
          {/* Cinema Top Bar */}
          <div className="flex w-full max-w-[440px] items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="font-brand text-[10.5px] font-bold text-[var(--accent-fg)]">
                KAIROS CINEMA
              </span>
              <span className="text-[11px] text-[var(--text-3)]">•</span>
              <span className="text-[11px] text-[var(--text-2)]">
                امروز{' '}
                <Numeric
                  value={formatDurationShort(todayTotalMin)}
                  className="font-semibold text-[var(--text)]"
                />
              </span>
            </div>

            <button
              type="button"
              onClick={() => setCinemaMode(false)}
              aria-label="خروج از نمای سینمایی"
              className="flex items-center gap-1.5 rounded-xl border border-[var(--accent-fg)]/18 bg-[var(--surface-2)]/90 px-3 py-1.5 text-[11px] font-medium text-[var(--text)]"
            >
              <Minimize2 className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
              <span>خروج</span>
            </button>
          </div>

          {/* Centerpiece Enlarged Chronometer */}
          <div className="my-4 flex flex-col items-center">
            <CinematicFocusTimer
              mode={focus.mode}
              phase={focus.phase}
              isRunning={focus.isRunning}
              remainingSeconds={focus.remainingSeconds}
              elapsedSeconds={focus.elapsedSeconds}
              progressPercent={progressPercent}
              completedCycles={focus.completedCycles}
              cyclesBeforeLong={settings.pomodoroCyclesBeforeLong}
              linkedTaskTitle={linkedTask?.title ?? null}
              nextFocusMinutes={focus.focusMinutes}
              cinemaMode
            />

            {/* Sound profiles inside Cinema Mode — same engine as the studio */}
            <div className="mt-6 flex flex-wrap items-center justify-center gap-1.5">
              {SOUND_PROFILES.map((profile) => (
                <button
                  key={profile.id}
                  type="button"
                  onClick={() => requestSound({ type: 'profile', profileId: profile.id })}
                  className="rounded-full border border-[var(--accent-fg)]/18 bg-[var(--surface-2)]/80 px-3 py-1.5 text-[11px] font-medium text-[var(--text-2)] transition-colors hover:border-[var(--accent-fg)]/45 hover:text-[var(--text)]"
                >
                  {profile.label}
                </button>
              ))}
              <button
                type="button"
                onClick={() => requestSound({ type: 'stop' })}
                className="rounded-full border border-[var(--border)] px-3 py-1.5 text-[11px] text-[var(--text-3)]"
              >
                سکوت
              </button>
            </div>
          </div>

          {/* Cinema Primary Action Bar */}
          <div className="flex w-full max-w-[380px] flex-col items-center gap-3 pb-2">
            <div className="flex w-full items-center justify-center gap-3.5">
              <button
                type="button"
                onClick={() => requestSessionChange('زمان ثبت‌نشدهٔ جلسه از دست می‌رود. تایمر بازنشانی شود؟', resetFocus)}
                aria-label="بازنشانی تایمر"
                className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[var(--accent-fg)]/16 bg-[var(--surface-2)]/85 text-[var(--text-2)] transition hover:text-[var(--text)]"
              >
                <RotateCcw className="h-5 w-5" />
              </button>

              <button
                type="button"
                onClick={focus.isRunning ? pauseFocus : startOrResumeFocus}
                className="btn-royal flex h-14 min-w-[172px] items-center justify-center gap-2 rounded-2xl px-7 text-sm font-bold"
              >
                {focus.isRunning ? (
                  <>
                    <Pause className="h-5 w-5" />
                    <span>توقف موقت</span>
                  </>
                ) : (
                  <>
                    <Play className="h-5 w-5" />
                    <span>{focus.elapsedSeconds > 0 ? 'ادامه تمرکز' : 'شروع تمرکز'}</span>
                  </>
                )}
              </button>

              {focus.mode === 'pomodoro' ? (
                <button
                  type="button"
                  onClick={() => requestSessionChange('با رفتن به مرحلهٔ بعد، زمان ثبت‌نشدهٔ مرحلهٔ جاری از بین می‌رود. ادامه می‌دهید؟', skipPomodoroPhase)}
                  aria-label="مرحله بعد"
                  className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[var(--accent-fg)]/16 bg-[var(--surface-2)]/85 text-[var(--text-2)] transition hover:text-[var(--text)]"
                >
                  <SkipForward className="h-5 w-5" />
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => void finishAndSaveFocus()}
                  aria-label="پایان و ثبت جلسه"
                  className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[var(--accent)]/50 bg-[var(--surface-3)] text-[var(--accent-fg)]"
                >
                  <Check className="h-5 w-5" />
                </button>
              )}
            </div>

            {focus.elapsedSeconds >= 60 && focus.phase === 'focus' && (
              <button
                type="button"
                onClick={() => void finishAndSaveFocus()}
                className="text-xs font-medium text-[var(--accent-fg)] underline-offset-4 hover:underline"
              >
                پایان و ثبت{' '}
                <Numeric value={Math.max(1, Math.round(focus.elapsedSeconds / 60))} /> دقیقه تمرکز
              </button>
            )}
          </div>
        </div>
      )}

      {/* ── THRESHOLD ────────────────────────────────────────────────────────
          Focus opens like a room you step into, not a dashboard: a Latin
          eyebrow, the section title, and a single hairline that carries today's
          real minutes. The stage below owns the rest of the screen. */}
      <header className="-mx-4 px-4 pb-3 pt-1">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <span className="font-display-caps text-[8.5px] font-semibold text-[var(--accent-fg)]">
              FOCUS
            </span>
            <h1 className="mt-1 text-[19px] font-bold tracking-tight text-[var(--text)]">
              محیط تمرکز
            </h1>
            <p className="mt-1 flex flex-wrap items-center gap-1 text-[10.5px] text-[var(--text-3)]">
              <Numeric
                value={formatDurationShort(todayTotalMin)}
                className="font-semibold text-[var(--text)]"
              />
              <span>از</span>
              <Numeric value={formatDurationShort(dailyGoalMin)} className="text-[var(--text-2)]" />
              <span className="opacity-50">·</span>
              <Numeric value={focus.completedCycles} variant="geo" className="text-[var(--text-2)]" />
              <span>چرخه</span>
            </p>
          </div>

          <div className="flex shrink-0 items-center gap-1.5">
            <button
              type="button"
              onClick={() => setCinemaMode(true)}
              aria-label="ورود به حالت سینمایی تمام‌صفحه"
              title="حالت سینمایی تمام‌صفحه"
              className="grid h-8 w-8 place-items-center rounded-full border border-[var(--accent-fg)]/35 text-[var(--accent-fg)] transition hover:bg-[color-mix(in_srgb,var(--accent)_22%,transparent)]"
            >
              <Maximize2 className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              onClick={() => setShowCustomConfig((v) => !v)}
              aria-label="تنظیم زمان‌های تمرکز"
              aria-pressed={showCustomConfig}
              className={`grid h-8 w-8 place-items-center rounded-full border transition ${
                showCustomConfig
                  ? 'border-[var(--accent)] text-[var(--accent-fg)]'
                  : 'border-[var(--border)] text-[var(--text-2)] hover:border-[var(--border-strong)]'
              }`}
            >
              <Sliders className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>

        <div className="mt-3 flex items-center gap-2">
          <span className="font-display-caps text-[8px] font-semibold text-[var(--text-3)]">TODAY</span>
          <span className="h-[2px] flex-1 overflow-hidden rounded-full bg-white/8">
            <span
              className="block h-full rounded-full transition-[width] duration-500"
              style={{ width: `${Math.max(dailyGoalPercent, 1.5)}%`, background: 'var(--accent)' }}
            />
          </span>
          <Numeric
            value={dailyGoalPercent}
            suffix="%"
            className="text-[10px] font-semibold text-[var(--accent-fg)]"
          />
        </div>
      </header>

      {/* ── MODE LINE ────────────────────────────────────────────────────────
          Four modes as editorial tabs on a rule — no segmented pill. The hint is
          the actual configuration, so switching mode never hides a number. */}
      <div className="flex items-center gap-4 overflow-x-auto border-b border-[var(--border)] pb-0">
        {CHRONO_MODES.map((mode) => {
          const active = focus.mode === mode.id;
          return (
            <button
              key={mode.id}
              type="button"
              onClick={() => { if (!active) requestSessionChange('تغییر حالت، زمان ثبت‌نشدهٔ جلسه را از بین می‌برد. ادامه می‌دهید؟', () => setFocusMode(mode.id)); }}
              aria-pressed={active}
              title={mode.hint}
              className="relative flex shrink-0 items-baseline gap-1.5 pb-2 pt-0.5"
            >
              <span
                className={`text-[12px] font-semibold transition-colors ${
                  active ? 'text-[var(--text)]' : 'text-[var(--text-3)] hover:text-[var(--text-2)]'
                }`}
              >
                {mode.label}
              </span>
              <span className="num-geo text-[8.5px] text-[var(--text-3)]">
                {modeHint(mode.id, chronoConfig)}
              </span>
              {active && (
                <span
                  aria-hidden="true"
                  className="absolute inset-x-0 -bottom-px h-[2px] rounded-full"
                  style={{ background: 'var(--accent)' }}
                />
              )}
            </button>
          );
        })}
      </div>

      {/* ── CUSTOM DURATIONS — a quiet strip, never a card ───────────────────
          The fields follow the mode: a pomodoro has breaks, a deep-work plan has
          blocks, a countdown has one number. Showing all of them at once is how
          a config panel becomes a form. */}
      {showCustomConfig && hasProgress(focus) && <p role="status" className="text-xs text-[var(--text-2)]">برای تغییر زمان‌ها ابتدا جلسه را ثبت یا با تأیید بازنشانی کنید.</p>}
      {showCustomConfig && !hasProgress(focus) && focus.mode !== 'free' && (
        <div className="grid grid-cols-3 gap-2 border-b border-[var(--border)] pb-3">
          {(focus.mode === 'pomodoro'
            ? [
                { label: 'تمرکز', value: focus.focusMinutes, min: 1, max: 180, fallback: 25, patch: (v: number) => ({ focusMinutes: v }) },
                { label: 'استراحت کوتاه', value: focus.shortBreakMinutes, min: 1, max: 45, fallback: 5, patch: (v: number) => ({ shortBreakMinutes: v }) },
                { label: 'استراحت بلند', value: focus.longBreakMinutes, min: 5, max: 60, fallback: 15, patch: (v: number) => ({ longBreakMinutes: v }) },
              ]
            : focus.mode === 'interval'
            ? [
                { label: 'تعداد بلوک', value: chronoConfig.blocks, min: 1, max: 12, fallback: 4, patch: (v: number) => ({ blocks: v }) },
                { label: 'دقیقهٔ بلوک', value: chronoConfig.blockMinutes, min: 5, max: 180, fallback: 45, patch: (v: number) => ({ blockMinutes: v }) },
                { label: 'استراحت', value: chronoConfig.restMinutes, min: 1, max: 45, fallback: 10, patch: (v: number) => ({ restMinutes: v }) },
              ]
            : [
                { label: 'دقیقهٔ شمارش', value: chronoConfig.countdownMinutes, min: 1, max: 240, fallback: 30, patch: (v: number) => ({ countdownMinutes: v }) },
              ]
          ).map((field) => (
            <div key={field.label}>
              <label className="mb-1 block text-[9.5px] text-[var(--text-3)]">{field.label}</label>
              <NumericInput
                min={field.min}
                max={field.max}
                fallback={field.fallback}
                value={field.value}
                onChange={(v) => setChronoConfig(field.patch(v))}
                className="luxury-input num-geo py-1.5 text-center text-xs"
              />
            </div>
          ))}
        </div>
      )}

      {/* ── THE STAGE ────────────────────────────────────────────────────────
          Full-bleed, no card: one horizon of light behind the Chrono Core, a
          ghost phase word, and the transport resting under the dial. */}
      <section
        aria-label="صحنهٔ تمرکز"
        className="relative -mx-4 overflow-hidden px-4 pb-5 pt-1"
        style={{
          background:
            'radial-gradient(78% 58% at 50% 40%, color-mix(in srgb, var(--accent) 11%, transparent) 0%, transparent 72%)',
        }}
      >
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 top-[16%] select-none text-center font-display text-[56px] font-semibold leading-none tracking-[0.08em] text-white/[0.028]"
        >
          {focus.phase === 'focus' ? 'FOCUS' : 'BREAK'}
        </span>
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-7 top-[47%] h-px"
          style={{
            background:
              'linear-gradient(90deg, transparent, color-mix(in srgb, var(--accent) 42%, transparent), transparent)',
          }}
        />

        <div className="relative flex justify-center pt-1">
          <CinematicFocusTimer
            mode={focus.mode}
            phase={focus.phase}
            isRunning={focus.isRunning}
            remainingSeconds={focus.remainingSeconds}
            elapsedSeconds={focus.elapsedSeconds}
            progressPercent={progressPercent}
            completedCycles={focus.completedCycles}
            cyclesBeforeLong={settings.pomodoroCyclesBeforeLong}
            linkedTaskTitle={linkedTask?.title ?? null}
            nextFocusMinutes={focus.focusMinutes}
          />
        </div>

        {/* Fine-tune: presets as text on the rule, invisible while running */}
        {focus.mode === 'pomodoro' && !hasProgress(focus) && focus.phase === 'focus' && (
          <div className="relative mt-4 flex items-center justify-center gap-1">
            <button
              type="button"
              onClick={() => adjustFocusMinutes(-5)}
              aria-label="کاهش ۵ دقیقه"
              className="grid h-7 w-7 place-items-center rounded-full text-[var(--text-3)] transition hover:text-[var(--text)]"
            >
              <Minus className="h-3 w-3" />
            </button>
            {PRESET_DURATIONS.map((mins) => {
              const active = focus.focusMinutes === mins;
              return (
                <button
                  key={mins}
                  type="button"
                  onClick={() => {
                    setFocusDurations(mins, focus.shortBreakMinutes, focus.longBreakMinutes);
                    void updateSettings({ pomodoroFocusMin: mins });
                  }}
                  aria-pressed={active}
                  className={`num-geo rounded-full px-2.5 py-1 text-[11px] font-semibold transition ${
                    active
                      ? 'bg-[color-mix(in_srgb,var(--accent)_26%,transparent)] text-[var(--text)]'
                      : 'text-[var(--text-3)] hover:text-[var(--text-2)]'
                  }`}
                >
                  {mins}
                </button>
              );
            })}
            <button
              type="button"
              onClick={() => adjustFocusMinutes(5)}
              aria-label="افزایش ۵ دقیقه"
              className="grid h-7 w-7 place-items-center rounded-full text-[var(--text-3)] transition hover:text-[var(--text)]"
            >
              <Plus className="h-3 w-3" />
            </button>
          </div>
        )}

        {/* Transport */}
        <div className="relative mt-4 flex items-center justify-center gap-4">
          <button
            type="button"
            onClick={() => requestSessionChange('زمان ثبت‌نشدهٔ جلسه از دست می‌رود. تایمر بازنشانی شود؟', resetFocus)}
            aria-label="بازنشانی تایمر"
            className="grid h-11 w-11 place-items-center rounded-full border border-[var(--border)] text-[var(--text-2)] transition hover:border-[var(--border-strong)] hover:text-[var(--text)]"
          >
            <RotateCcw className="h-4 w-4" />
          </button>

          <button
            type="button"
            onClick={focus.isRunning ? pauseFocus : startOrResumeFocus}
            className="btn-royal flex h-13 items-center justify-center gap-2 rounded-full px-7 text-[13px] font-bold"
          >
            {focus.isRunning ? (
              <>
                <Pause className="h-4 w-4" />
                <span>توقف موقت</span>
              </>
            ) : (
              <>
                <Play className="h-4 w-4" />
                <span>{focus.elapsedSeconds > 0 ? 'ادامه تمرکز' : 'شروع تمرکز'}</span>
              </>
            )}
          </button>

          {focus.mode !== 'free' ? (
            <button
              type="button"
              onClick={() => requestSessionChange('با رفتن به مرحلهٔ بعد، زمان ثبت‌نشدهٔ مرحلهٔ جاری از بین می‌رود. ادامه می‌دهید؟', skipPomodoroPhase)}
              aria-label="مرحله بعد"
              title="انتقال به مرحله بعد"
              className="grid h-11 w-11 place-items-center rounded-full border border-[var(--border)] text-[var(--text-2)] transition hover:border-[var(--border-strong)] hover:text-[var(--text)]"
            >
              <SkipForward className="h-4 w-4" />
            </button>
          ) : (
            <button
              type="button"
              onClick={() => void finishAndSaveFocus()}
              aria-label="پایان و ثبت جلسه"
              title="پایان و ثبت جلسه"
              className="grid h-11 w-11 place-items-center rounded-full border border-[var(--accent)]/50 text-[var(--accent-fg)] transition hover:bg-[color-mix(in_srgb,var(--accent)_24%,transparent)]"
            >
              <Check className="h-4 w-4" />
            </button>
          )}
        </div>

        {focus.elapsedSeconds >= 60 && focus.phase === 'focus' && (
          <button
            type="button"
            onClick={() => void finishAndSaveFocus()}
            className="relative mx-auto mt-3 block text-[10.5px] font-medium text-[var(--accent-fg)] underline-offset-4 hover:underline"
          >
            پایان و ثبت <Numeric value={Math.max(1, Math.round(focus.elapsedSeconds / 60))} /> دقیقه
          </button>
        )}

        {/* Sound Studio — real synthesised layers, mixer, sleep timer, breath */}
        <details className="relative mt-5 rounded-2xl border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3">
          <summary className="cursor-pointer text-sm font-semibold text-[var(--text)]">فضای صوتی و تنفس</summary>
          <div className="mt-3"><SoundStudio /></div>
        </details>
      </section>

      {/* ── LINKED TASK — one line on the rule ─────────────────────────────── */}
      <div className="flex items-center gap-3 border-y border-[var(--border)] py-2.5">
        <label htmlFor="focus-task-select" className="shrink-0 text-[10px] text-[var(--text-3)]">
          وظیفهٔ این جلسه
        </label>
        <select
          id="focus-task-select"
          value={focus.linkedTaskId ?? ''}
          onChange={(e) => setFocusLinkedTask(e.target.value || null)}
          className="min-w-0 flex-1 bg-transparent text-[11.5px] text-[var(--text)] outline-none"
        >
          <option value="" className="bg-[var(--surface-2)]">
            تمرکز آزاد
          </option>
          {availableTasks.map((t) => (
            <option key={t.id} value={t.id} className="bg-[var(--surface-2)]">
              {t.title}
            </option>
          ))}
        </select>
        {linkedTask && (
          <button
            type="button"
            onClick={async () => {
              await setTaskStatus(linkedTask.id, 'completed');
              setFocusLinkedTask(null);
              pushToast({
                title: 'وظیفه تکمیل شد',
                description: linkedTask.title,
                tone: 'success',
              });
            }}
            aria-label="تکمیل وظیفهٔ مرتبط"
            className="grid h-6 w-6 shrink-0 place-items-center rounded-full text-[var(--accent-fg)] transition hover:bg-[color-mix(in_srgb,var(--accent)_22%,transparent)]"
          >
            <CheckCircle2 className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      {/* ── THE PLAN ────────────────────────────────────────────────────────
          What the session actually is: every segment, what is done, what runs now. */}
      <ChronoTimeline
        plan={plan}
        segmentIndex={focus.segmentIndex ?? 0}
        remainingSeconds={focus.remainingSeconds}
        running={focus.isRunning}
        onSeek={(index) => { if (index !== (focus.segmentIndex ?? 0)) requestSessionChange('با جابه‌جایی مرحله، زمان ثبت‌نشدهٔ مرحلهٔ جاری از بین می‌رود. ادامه می‌دهید؟', () => seekFocusSegment(index)); }}
      />

      {/* ── CONTINUITY ──────────────────────────────────────────────────────
          One switch, one meaning: does the clock walk into the next segment on its
          own, or wait for a tap? Stored with the rest of the settings. */}
      <div className="flex items-center justify-between border-b border-[var(--border)] pb-2.5">
        <div className="min-w-0 pr-3">
          <p className="text-[11px] font-medium text-[var(--text-2)]">ادامهٔ خودکار</p>
          <p className="mt-0.5 text-[9.5px] leading-relaxed text-[var(--text-3)]">
            {focus.autoAdvance
              ? 'بدون توقف از تمرکز به استراحت و برعکس می‌رود.'
              : 'در پایان هر بخش می‌ایستد تا خودتان شروع کنید.'}
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={focus.autoAdvance === true}
          aria-label="ادامهٔ خودکار بخش‌ها"
          onClick={() => setAutoAdvance(!focus.autoAdvance)}
          className={`relative h-5 w-9 shrink-0 rounded-full border transition-colors ${
            focus.autoAdvance
              ? 'border-[var(--accent)] bg-[color-mix(in_srgb,var(--accent)_55%,transparent)]'
              : 'border-[var(--border)] bg-[var(--surface-2)]'
          }`}
        >
          <span
            aria-hidden="true"
            className="absolute top-0.5 h-3.5 w-3.5 rounded-full bg-[var(--text)] transition-all"
            style={{ left: focus.autoAdvance ? '1.15rem' : '0.15rem' }}
          />
        </button>
      </div>

      {/* ── NO INTERRUPTIONS ───────────────────────────────────────────────
          Off by default: silencing a phone is the reader's call, and the system
          grant is theirs to give. */}
      {isNativeAndroid() && (
        <div className="flex items-center justify-between border-b border-[var(--border)] pb-2.5">
          <div className="min-w-0 pr-3">
            <p className="text-[11px] font-medium text-[var(--text-2)]">بدون مزاحمت در جلسه</p>
            <p className="mt-0.5 text-[9.5px] leading-relaxed text-[var(--text-3)]">
              {settings.dndInFocus
                ? 'اعلان‌ها در طول تمرکز ساکت می‌شوند و در پایان برمی‌گردند.'
                : 'در طول جلسه اعلان‌ها را قطع می‌کند (نیاز به اجازهٔ سیستم).'}
            </p>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={settings.dndInFocus === true}
            aria-label="بدون مزاحمت در جلسه"
            onClick={() => void setDndInFocus(!settings.dndInFocus)}
            className={`relative h-5 w-9 shrink-0 rounded-full border transition-colors ${
              settings.dndInFocus
                ? 'border-[var(--accent)] bg-[color-mix(in_srgb,var(--accent)_55%,transparent)]'
                : 'border-[var(--border)] bg-[var(--surface-2)]'
            }`}
          >
            <span
              aria-hidden="true"
              className="absolute top-0.5 h-3.5 w-3.5 rounded-full bg-[var(--text)] transition-all"
              style={{ left: settings.dndInFocus ? '1.15rem' : '0.15rem' }}
            />
          </button>
        </div>
      )}


      <details className="rounded-2xl border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3 text-[11px] leading-6 text-[var(--text-2)]">
        <summary className="cursor-pointer font-semibold text-[var(--text)]">راهنمای استفاده از تمرکز و صدا</summary>
        <ol className="mt-2 list-inside list-decimal space-y-1">
          <li>یک حالت انتخاب کنید: پومودورو برای تمرکز و استراحت، بلوک‌های عمیق برای چند نوبت کار، شمارش معکوس برای یک بازه و زمان‌سنج آزاد برای زمان نامحدود.</li>
          <li>برای تنظیم زمان، دکمهٔ تنظیمات کنار عنوان را باز کنید. در صورت نیاز، یک وظیفه را پایین صفحه به جلسه پیوند دهید.</li>
          <li>«شروع تمرکز» را بزنید. با «توقف موقت» می‌توانید مکث کنید. «بازنشانی» شمارش جاری را از نو آماده می‌کند.</li>
          <li>برای پایان زودتر، پس از یک دقیقه «پایان و ثبت» را انتخاب کنید. در حالت آزاد، دکمهٔ تیک نیز جلسه را ثبت می‌کند.</li>
          <li>در فضای صوتی، یک ترکیب را انتخاب کنید و صدای کلی یا هر لایه را کم و زیاد کنید؛ برای سکوت «توقف صدا» را بزنید.</li>
        </ol>
        <p className="mt-2 text-[var(--text-3)]">صداهای طبیعت ضبط‌شده‌اند و نویز و تُن تولید می‌شوند. تایمر خواب صوتی به صفحه وابسته است و در پس‌زمینه ممکن است دقیق متوقف نشود. با صدای پایین شروع کنید.</p>
      </details>

      <ComparisonChart title="تمرکز هفت روز اخیر" subtitle="دقیقه‌های جلسه‌های ثبت‌شده در هر روز؛ زمان در حال اجرا تا ثبت‌شدن محاسبه نمی‌شود." unit="دقیقه" data={Array.from({ length: 7 }, (_, index) => {
        const date = addDays(todayKey, index - 6);
        return { label: formatJalaaliShort(date), value: Math.round(sessions.filter((session) => session.dateKey === date).reduce((total, session) => total + session.durationMinutes, 0)) };
      })} />

      {/* ── SESSION LEDGER ─────────────────────────────────────────────────── */}
      <section>
        <div className="flex items-baseline justify-between">
          <h2 className="text-[13px] font-bold text-[var(--text)]">گزارش جلسات تمرکز</h2>
          <Numeric
            value={sessions.length}
            variant="geo"
            className="text-[10px] text-[var(--text-3)]"
          />
        </div>

        {recentSessions.length === 0 ? (
          <p className="mt-2 border-y border-[var(--border)] py-4 text-[10.5px] text-[var(--text-3)]">
            هنوز جلسه‌ای ثبت نشده است؛ اولین تمرکز امروز را شروع کن.
          </p>
        ) : (
          <ul className="mt-1.5 divide-y divide-[var(--border)] border-y border-[var(--border)]">
            {recentSessions.map((s) => (
              <li key={s.id} className="flex items-center gap-3 py-2.5">
                <span
                  aria-hidden="true"
                  className="h-1.5 w-1.5 shrink-0 rounded-full"
                  style={{ background: 'color-mix(in srgb, var(--accent-fg) 55%, transparent)' }}
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[11.5px] text-[var(--text)]">
                    {s.taskTitleSnapshot || `جلسهٔ ${modeLabel(s.mode)}`}
                  </p>
                  <p className="mt-0.5 text-[9.5px] text-[var(--text-3)]">
                    {formatJalaaliShort(s.dateKey)} · {modeLabel(s.mode)}
                  </p>
                </div>
                <Numeric
                  value={formatDurationShort(s.durationMinutes)}
                  variant="display"
                  className="shrink-0 text-[15px] font-semibold text-[var(--text)]"
                />
                <button
                  type="button"
                  onClick={() => setConfirmation({ text: 'این جلسه از گزارش تمرکز حذف می‌شود و بازگردانی ندارد. فایل‌های پشتیبان قبلی تغییر نمی‌کنند. حذف شود؟', run: async () => { await deleteFocusSession(s.id, s); pushToast({ title: 'جلسه حذف شد', tone: 'default' }); } })}
                  aria-label="حذف جلسه"
                  className="grid h-11 w-11 shrink-0 place-items-center rounded-md text-[var(--text-3)] transition hover:text-[var(--danger-fg)]"
                >
                  <Trash2 className="h-3 w-3" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

    </div>
  );
}
