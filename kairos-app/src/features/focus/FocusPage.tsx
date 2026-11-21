import { useEffect, useMemo, useState } from 'react';
import {
  Check,
  CheckCircle2,
  CloudRain,
  Maximize2,
  Minimize2,
  Minus,
  Pause,
  Play,
  Plus,
  Radio,
  RotateCcw,
  SkipForward,
  Sliders,
  Trash2,
  Volume2,
  VolumeX,
  Waves,
} from '@/components/ui/icons';
import { deleteFocusSession, setTaskStatus, updateSettings } from '@/lib/db';
import { useFocusSessions, useTasks } from '@/lib/queries';
import { formatJalaaliShort, getTodayKey } from '@/lib/date';
import { formatDurationShort } from '@/lib/format';
import { ambientAudio, type AmbientSoundType } from '@/lib/ambientAudio';
import { useApp } from '@/context/AppContext';
import { Numeric } from '@/components/ui/Numeric';
import { NumericInput } from '@/components/ui/NumericInput';
import { CinematicFocusTimer } from '@/components/focus/CinematicFocusTimer';

const PRESET_DURATIONS = [15, 25, 45, 60, 90] as const;

const SOUNDSCAPES: {
  id: AmbientSoundType;
  label: string;
  icon: typeof VolumeX;
}[] = [
  { id: 'off', label: 'سکوت', icon: VolumeX },
  { id: 'rain', label: 'باران آرام', icon: CloudRain },
  { id: 'brown', label: 'نویز عمیق', icon: Waves },
  { id: 'drone', label: 'فضای سینمایی', icon: Radio },
];

export function FocusPage() {
  const {
    settings,
    focus,
    setFocusMode,
    setFocusDurations,
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
  const [soundType, setSoundType] = useState<AmbientSoundType>('off');

  const todayKey = getTodayKey();
  const tasks = useTasks();
  const sessions = useFocusSessions();

  // Sync offline ambient soundscape with running timer & clean up on unmount
  useEffect(() => {
    if (focus.isRunning && soundType !== 'off') {
      ambientAudio.start(soundType);
    } else {
      ambientAudio.stop();
    }
    return () => {
      ambientAudio.stop();
    };
  }, [focus.isRunning, soundType]);

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

  const totalPhaseSeconds =
    focus.phase === 'focus'
      ? focus.focusMinutes * 60
      : focus.phase === 'short_break'
      ? focus.shortBreakMinutes * 60
      : focus.longBreakMinutes * 60;

  const progressPercent =
    focus.mode === 'pomodoro'
      ? Math.max(
          0,
          Math.min(
            100,
            ((totalPhaseSeconds - focus.remainingSeconds) / Math.max(1, totalPhaseSeconds)) * 100
          )
        )
      : Math.min(100, ((focus.elapsedSeconds % 3600) / 3600) * 100);

  const adjustFocusMinutes = (delta: number) => {
    const next = Math.max(5, Math.min(180, focus.focusMinutes + delta));
    setFocusDurations(next, focus.shortBreakMinutes, focus.longBreakMinutes);
    void updateSettings({ pomodoroFocusMin: next });
  };

  const handleSelectSound = (next: AmbientSoundType) => {
    setSoundType(next);
    if (next === 'off') {
      ambientAudio.stop();
    } else if (focus.isRunning) {
      ambientAudio.start(next);
    }
  };

  return (
    <div className="space-y-4">
      {/* FULL-SCREEN CINEMA FOCUS SANCTUARY OVERLAY */}
      {cinemaMode && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="حالت سینمایی تمرکز"
          className="fixed inset-0 z-[80] flex flex-col items-center justify-between overflow-y-auto bg-[#0A0A0C] px-6 py-8"
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
              <span className="text-[11px] text-[#6B6B75]">•</span>
              <span className="text-[11px] text-[#9C9CA6]">
                امروز{' '}
                <Numeric
                  value={formatDurationShort(todayTotalMin)}
                  className="font-semibold text-[#ECECEF]"
                />
              </span>
            </div>

            <button
              type="button"
              onClick={() => setCinemaMode(false)}
              aria-label="خروج از نمای سینمایی"
              className="flex items-center gap-1.5 rounded-xl border border-[var(--accent-fg)]/18 bg-[#16161A]/90 px-3 py-1.5 text-[11px] font-medium text-[#ECECEF]"
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

            {/* Soundscape Selector inside Cinema Mode */}
            <div className="mt-6 flex flex-wrap items-center justify-center gap-1.5">
              {SOUNDSCAPES.map((sc) => {
                const Icon = sc.icon;
                const active = soundType === sc.id;
                return (
                  <button
                    key={sc.id}
                    type="button"
                    onClick={() => handleSelectSound(sc.id)}
                    className={`flex items-center gap-1.5 rounded-xl border px-3 py-1.5 text-[11px] font-medium transition ${
                      active
                        ? 'border-[var(--accent-fg)]/45 bg-[#1C1C21] text-[#ECECEF]'
                        : 'border-[var(--accent-fg)]/12 bg-[#16161A]/80 text-[#9C9CA6]'
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
                    <span>{sc.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Cinema Primary Action Bar */}
          <div className="flex w-full max-w-[380px] flex-col items-center gap-3 pb-2">
            <div className="flex w-full items-center justify-center gap-3.5">
              <button
                type="button"
                onClick={resetFocus}
                aria-label="بازنشانی تایمر"
                className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[var(--accent-fg)]/16 bg-[#16161A]/85 text-[#9C9CA6] transition hover:text-[#ECECEF]"
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
                  onClick={skipPomodoroPhase}
                  aria-label="مرحله بعد"
                  className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[var(--accent-fg)]/16 bg-[#16161A]/85 text-[#9C9CA6] transition hover:text-[#ECECEF]"
                >
                  <SkipForward className="h-5 w-5" />
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => void finishAndSaveFocus()}
                  aria-label="پایان و ثبت جلسه"
                  className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[var(--accent)]/50 bg-[#1C1C21] text-[var(--accent-fg)]"
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
          Two modes as editorial tabs on a rule — no segmented pill. */}
      <div className="flex items-center gap-5 border-b border-[var(--border)]">
        {(
          [
            { id: 'pomodoro', label: 'پومودورو', hint: `${focus.focusMinutes}/${focus.shortBreakMinutes}` },
            { id: 'free', label: 'تایمر آزاد', hint: 'بی‌پایان' },
          ] as const
        ).map((mode) => {
          const active = focus.mode === mode.id;
          return (
            <button
              key={mode.id}
              type="button"
              onClick={() => setFocusMode(mode.id)}
              aria-pressed={active}
              className="relative flex items-baseline gap-1.5 pb-2 pt-0.5"
            >
              <span
                className={`text-[12px] font-semibold transition-colors ${
                  active ? 'text-[var(--text)]' : 'text-[var(--text-3)] hover:text-[var(--text-2)]'
                }`}
              >
                {mode.label}
              </span>
              <span className="num-geo text-[8.5px] text-[var(--text-3)]">{mode.hint}</span>
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

      {/* ── CUSTOM DURATIONS — a quiet strip, never a card ─────────────────── */}
      {showCustomConfig && focus.mode === 'pomodoro' && (
        <div className="grid grid-cols-3 gap-2 border-b border-[var(--border)] pb-3">
          {[
            { label: 'تمرکز', value: focus.focusMinutes, min: 1, max: 180, fallback: 25, apply: (v: number) => setFocusDurations(v, focus.shortBreakMinutes, focus.longBreakMinutes), persist: (v: number) => updateSettings({ pomodoroFocusMin: v }) },
            { label: 'استراحت کوتاه', value: focus.shortBreakMinutes, min: 1, max: 45, fallback: 5, apply: (v: number) => setFocusDurations(focus.focusMinutes, v, focus.longBreakMinutes), persist: (v: number) => updateSettings({ pomodoroShortBreakMin: v }) },
            { label: 'استراحت بلند', value: focus.longBreakMinutes, min: 5, max: 60, fallback: 15, apply: (v: number) => setFocusDurations(focus.focusMinutes, focus.shortBreakMinutes, v), persist: (v: number) => updateSettings({ pomodoroLongBreakMin: v }) },
          ].map((field) => (
            <div key={field.label}>
              <label className="mb-1 block text-[9.5px] text-[var(--text-3)]">{field.label}</label>
              <NumericInput
                min={field.min}
                max={field.max}
                fallback={field.fallback}
                value={field.value}
                onChange={(v) => {
                  field.apply(v);
                  void field.persist(v);
                }}
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
        {focus.mode === 'pomodoro' && !focus.isRunning && focus.phase === 'focus' && (
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
            onClick={resetFocus}
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

          {focus.mode === 'pomodoro' ? (
            <button
              type="button"
              onClick={skipPomodoroPhase}
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

        {/* Sound rail — real offline layers, as an instrument strip */}
        <div className="relative mt-5 border-t border-[var(--border)] pt-3">
          <div className="mb-2 flex items-center justify-between text-[10px] text-[var(--text-3)]">
            <span className="inline-flex items-center gap-1.5 font-medium text-[var(--text-2)]">
              <Volume2 className="h-3 w-3 text-[var(--accent-fg)]" />
              فضای صوتی تمرکز (آفلاین)
            </span>
            <span>
              {soundType === 'off'
                ? 'خاموش'
                : focus.isRunning
                ? 'در حال پخش'
                : 'آماده هنگام شروع'}
            </span>
          </div>
          <div className="grid grid-cols-4 gap-1">
            {SOUNDSCAPES.map((sc) => {
              const Icon = sc.icon;
              const active = soundType === sc.id;
              return (
                <button
                  key={sc.id}
                  type="button"
                  onClick={() => handleSelectSound(sc.id)}
                  aria-pressed={active}
                  className={`flex flex-col items-center gap-1 rounded-[10px] border py-2 text-[9.5px] font-medium transition ${
                    active
                      ? 'border-[var(--accent)]/60 text-[var(--text)]'
                      : 'border-transparent text-[var(--text-3)] hover:text-[var(--text-2)]'
                  }`}
                  style={{
                    background: active ? 'color-mix(in srgb, var(--accent) 12%, transparent)' : 'transparent',
                  }}
                >
                  <Icon className="h-3.5 w-3.5" />
                  <span>{sc.label}</span>
                </button>
              );
            })}
          </div>
        </div>
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
          <option value="" className="bg-[#16161A]">
            تمرکز آزاد
          </option>
          {availableTasks.map((t) => (
            <option key={t.id} value={t.id} className="bg-[#16161A]">
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
                    {s.taskTitleSnapshot ||
                      (s.mode === 'pomodoro' ? 'جلسهٔ پومودورو' : 'تمرکز آزاد')}
                  </p>
                  <p className="mt-0.5 text-[9.5px] text-[var(--text-3)]">
                    {formatJalaaliShort(s.dateKey)} · {s.mode === 'pomodoro' ? 'پومودورو' : 'آزاد'}
                  </p>
                </div>
                <Numeric
                  value={formatDurationShort(s.durationMinutes)}
                  variant="display"
                  className="shrink-0 text-[15px] font-semibold text-[var(--text)]"
                />
                <button
                  type="button"
                  onClick={() => deleteFocusSession(s.id)}
                  aria-label="حذف جلسه"
                  className="shrink-0 rounded-md p-1 text-[var(--text-3)] transition hover:text-[#EF9095]"
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
