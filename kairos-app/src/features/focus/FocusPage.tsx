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
import { LuxuryCard } from '@/components/ui/LuxuryCard';
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
              'radial-gradient(ellipse 70% 40% at 50% 40%, rgba(110, 86, 207, 0.10), transparent 70%)',
          }}
        >
          {/* Cinema Top Bar */}
          <div className="flex w-full max-w-[440px] items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="font-brand text-[10.5px] font-bold text-[#A99BF0]">
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
              className="flex items-center gap-1.5 rounded-xl border border-[#A99BF0]/18 bg-[#16161A]/90 px-3 py-1.5 text-[11px] font-medium text-[#ECECEF]"
            >
              <Minimize2 className="h-3.5 w-3.5 text-[#A99BF0]" />
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
                        ? 'border-[#A99BF0]/45 bg-[#1C1C21] text-[#ECECEF]'
                        : 'border-[#A99BF0]/12 bg-[#16161A]/80 text-[#9C9CA6]'
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5 text-[#A99BF0]" />
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
                className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[#A99BF0]/16 bg-[#16161A]/85 text-[#9C9CA6] transition hover:text-[#ECECEF]"
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
                  className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[#A99BF0]/16 bg-[#16161A]/85 text-[#9C9CA6] transition hover:text-[#ECECEF]"
                >
                  <SkipForward className="h-5 w-5" />
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => void finishAndSaveFocus()}
                  aria-label="پایان و ثبت جلسه"
                  className="flex h-13 w-13 items-center justify-center rounded-2xl border border-[#6E56CF]/50 bg-[#1C1C21] text-[#A99BF0]"
                >
                  <Check className="h-5 w-5" />
                </button>
              )}
            </div>

            {focus.elapsedSeconds >= 60 && focus.phase === 'focus' && (
              <button
                type="button"
                onClick={() => void finishAndSaveFocus()}
                className="text-xs font-medium text-[#A99BF0] underline-offset-4 hover:underline"
              >
                پایان و ثبت{' '}
                <Numeric value={Math.max(1, Math.round(focus.elapsedSeconds / 60))} /> دقیقه تمرکز
              </button>
            )}
          </div>
        </div>
      )}

      {/* Header + Daily Focus Goal Bar */}
      <div className="flex items-center justify-between gap-2">
        <div>
          <h1 className="text-lg font-bold text-[#ECECEF]">محیط تمرکز</h1>
          <p className="mt-0.5 text-[11px] text-[#9C9CA6]">
            تمرکز امروز:{' '}
            <Numeric
              value={formatDurationShort(todayTotalMin)}
              className="font-semibold text-[#A99BF0]"
            />{' '}
            از{' '}
            <Numeric
              value={formatDurationShort(dailyGoalMin)}
              className="text-[#ECECEF]"
            />{' '}
            •{' '}
            <Numeric value={focus.completedCycles} className="text-[#ECECEF]" /> چرخه
          </p>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setCinemaMode(true)}
            aria-label="ورود به حالت سینمایی تمام‌صفحه"
            title="حالت سینمایی تمام‌صفحه"
            className="flex h-9 items-center gap-1.5 rounded-xl border border-[#6E56CF]/40 bg-[#1C1C21]/80 px-2.5 text-[11px] font-semibold text-[#A99BF0] transition hover:bg-[#6E56CF] hover:text-[#ECECEF]"
          >
            <Maximize2 className="h-3.5 w-3.5" />
            <span>سینمایی</span>
          </button>

          <button
            type="button"
            onClick={() => setShowCustomConfig((v) => !v)}
            aria-label="تنظیم زمان‌های تمرکز"
            className={`flex h-9 w-9 items-center justify-center rounded-xl border transition ${
              showCustomConfig
                ? 'border-[#6E56CF] bg-[#1C1C21] text-[#A99BF0]'
                : 'border-[#A99BF0]/12 bg-[#16161A] text-[#9C9CA6]'
            }`}
          >
            <Sliders className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Subtle Daily Focus Goal Progress Rail */}
      <div className="flex items-center gap-2.5 px-0.5">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#16161A]">
          <div
            className="h-full rounded-full bg-[var(--accent)] transition-all duration-300"
            style={{ width: `${dailyGoalPercent}%` }}
          />
        </div>
        <Numeric
          value={dailyGoalPercent}
          suffix="%"
          className="text-[10.5px] font-semibold text-[#A99BF0]"
        />
      </div>

      {/* Mode Switcher (Pomodoro vs Free Deep Timer) */}
      <div className="grid grid-cols-2 gap-1 rounded-2xl border border-[#A99BF0]/12 bg-[#16161A] p-1">
        <button
          type="button"
          onClick={() => setFocusMode('pomodoro')}
          className={`rounded-xl py-2 text-xs font-semibold transition ${
            focus.mode === 'pomodoro'
              ? 'bg-[#1C1C21] text-[#ECECEF]'
              : 'text-[#9C9CA6] hover:text-[#ECECEF]'
          }`}
        >
          پومودورو
        </button>
        <button
          type="button"
          onClick={() => setFocusMode('free')}
          className={`rounded-xl py-2 text-xs font-semibold transition ${
            focus.mode === 'free'
              ? 'bg-[#1C1C21] text-[#ECECEF]'
              : 'text-[#9C9CA6] hover:text-[#ECECEF]'
          }`}
        >
          تایمر آزاد
        </button>
      </div>

      {/* Optional Custom Duration Drawer */}
      {showCustomConfig && focus.mode === 'pomodoro' && (
        <LuxuryCard variant="subtle" className="space-y-3 p-4">
          <div className="grid grid-cols-3 gap-2.5">
            <div>
              <label className="mb-1 block text-[10.5px] text-[#9C9CA6]">تمرکز (دقیقه)</label>
              <NumericInput
                min={1}
                max={180}
                fallback={25}
                value={focus.focusMinutes}
                onChange={(v) => {
                  setFocusDurations(v, focus.shortBreakMinutes, focus.longBreakMinutes);
                  void updateSettings({ pomodoroFocusMin: v });
                }}
                className="luxury-input num-geo py-1.5 text-center text-xs"
              />
            </div>
            <div>
              <label className="mb-1 block text-[10.5px] text-[#9C9CA6]">استراحت کوتاه</label>
              <NumericInput
                min={1}
                max={45}
                fallback={5}
                value={focus.shortBreakMinutes}
                onChange={(v) => {
                  setFocusDurations(focus.focusMinutes, v, focus.longBreakMinutes);
                  void updateSettings({ pomodoroShortBreakMin: v });
                }}
                className="luxury-input num-geo py-1.5 text-center text-xs"
              />
            </div>
            <div>
              <label className="mb-1 block text-[10.5px] text-[#9C9CA6]">استراحت بلند</label>
              <NumericInput
                min={5}
                max={60}
                fallback={15}
                value={focus.longBreakMinutes}
                onChange={(v) => {
                  setFocusDurations(focus.focusMinutes, focus.shortBreakMinutes, v);
                  void updateSettings({ pomodoroLongBreakMin: v });
                }}
                className="luxury-input num-geo py-1.5 text-center text-xs"
              />
            </div>
          </div>
        </LuxuryCard>
      )}

      {/* CINEMATIC OBSIDIAN CHRONOMETER STAGE CARD */}
      <LuxuryCard
        variant="hero"
        className="relative flex flex-col items-center overflow-hidden px-5 py-6 text-center"
      >
        {/* Subtle Architectural Top/Bottom Stage Hairlines */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute top-0 inset-x-10 h-px"
          style={{
            background:
              'linear-gradient(90deg, transparent, var(--border-strong), transparent)',
          }}
        />

        {/* Sculptural Swiss Chronometer Dial */}
        <div className="my-2">
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
          />
        </div>

        {/* Fine-Tune (-5m / Presets / +5m) — Hidden gracefully while running for stillness */}
        {focus.mode === 'pomodoro' && !focus.isRunning && focus.phase === 'focus' && (
          <div className="mt-3 mb-4 w-full space-y-2.5">
            <div className="flex items-center justify-center gap-1.5">
              <button
                type="button"
                onClick={() => adjustFocusMinutes(-5)}
                aria-label="کاهش ۵ دقیقه"
                className="flex h-8 w-8 items-center justify-center rounded-xl border border-[#A99BF0]/14 bg-[#0A0A0C]/70 text-[#9C9CA6] transition hover:border-[#A99BF0]/40 hover:text-[#ECECEF]"
              >
                <Minus className="h-3.5 w-3.5" />
              </button>

              {PRESET_DURATIONS.map((mins) => (
                <button
                  key={mins}
                  type="button"
                  onClick={() => {
                    setFocusDurations(mins, focus.shortBreakMinutes, focus.longBreakMinutes);
                    void updateSettings({ pomodoroFocusMin: mins });
                  }}
                  className={`num-geo rounded-xl border px-2.5 py-1.5 text-[11.5px] font-semibold transition ${
                    focus.focusMinutes === mins
                      ? 'border-[#A99BF0]/55 bg-[#1C1C21] text-[#ECECEF]'
                      : 'border-[#A99BF0]/10 bg-[#0A0A0C]/60 text-[#9C9CA6] hover:text-[#ECECEF]'
                  }`}
                >
                  {mins}m
                </button>
              ))}

              <button
                type="button"
                onClick={() => adjustFocusMinutes(5)}
                aria-label="افزایش ۵ دقیقه"
                className="flex h-8 w-8 items-center justify-center rounded-xl border border-[#A99BF0]/14 bg-[#0A0A0C]/70 text-[#9C9CA6] transition hover:border-[#A99BF0]/40 hover:text-[#ECECEF]"
              >
                <Plus className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}

        {/* Primary Luxury Control Bar */}
        <div className="mt-2 flex w-full items-center justify-center gap-3">
          <button
            type="button"
            onClick={resetFocus}
            aria-label="بازنشانی تایمر"
            className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#A99BF0]/14 bg-[#0A0A0C]/75 text-[#9C9CA6] transition hover:border-[#A99BF0]/35 hover:text-[#ECECEF]"
          >
            <RotateCcw className="h-4.5 w-4.5" />
          </button>

          <button
            type="button"
            onClick={focus.isRunning ? pauseFocus : startOrResumeFocus}
            className="btn-royal flex h-13 min-w-[162px] items-center justify-center gap-2 rounded-2xl px-6 text-sm font-bold"
          >
            {focus.isRunning ? (
              <>
                <Pause className="h-4.5 w-4.5" />
                <span>توقف موقت</span>
              </>
            ) : (
              <>
                <Play className="h-4.5 w-4.5" />
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
              className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#A99BF0]/14 bg-[#0A0A0C]/75 text-[#9C9CA6] transition hover:border-[#A99BF0]/35 hover:text-[#ECECEF]"
            >
              <SkipForward className="h-4.5 w-4.5" />
            </button>
          ) : (
            <button
              type="button"
              onClick={() => void finishAndSaveFocus()}
              aria-label="پایان و ثبت جلسه"
              title="پایان و ثبت جلسه"
              className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#6E56CF]/45 bg-[#1C1C21] text-[#A99BF0] transition hover:bg-[#6E56CF] hover:text-[#ECECEF]"
            >
              <Check className="h-4.5 w-4.5" />
            </button>
          )}
        </div>

        {/* Manual Save Early Button when >= 60s focused */}
        {focus.elapsedSeconds >= 60 && focus.phase === 'focus' && (
          <button
            type="button"
            onClick={() => void finishAndSaveFocus()}
            className="mt-4 text-xs font-medium text-[#A99BF0] underline-offset-4 hover:underline"
          >
            پایان و ثبت{' '}
            <Numeric value={Math.max(1, Math.round(focus.elapsedSeconds / 60))} /> دقیقه تمرکز
          </button>
        )}

        {/* Offline Ambient Soundscape Bar */}
        <div className="mt-5 w-full border-t border-[#A99BF0]/10 pt-4">
          <div className="mb-2 flex items-center justify-between text-[10.5px] text-[#9C9CA6]">
            <span className="inline-flex items-center gap-1.5 font-medium text-[#ECECEF]">
              <Volume2 className="h-3.5 w-3.5 text-[#6E56CF]" />
              فضای صوتی تمرکز (آفلاین)
            </span>
            <span className="text-[10px] text-[#6B6B75]">
              {soundType === 'off'
                ? 'خاموش'
                : focus.isRunning
                ? 'در حال پخش آرام'
                : 'آماده هنگام شروع'}
            </span>
          </div>
          <div className="grid grid-cols-4 gap-1.5">
            {SOUNDSCAPES.map((sc) => {
              const Icon = sc.icon;
              const active = soundType === sc.id;
              return (
                <button
                  key={sc.id}
                  type="button"
                  onClick={() => handleSelectSound(sc.id)}
                  className={`flex flex-col items-center gap-1 rounded-xl border py-2 text-[10px] font-medium transition ${
                    active
                      ? 'border-[#A99BF0]/45 bg-[#1C1C21] text-[#ECECEF]'
                      : 'border-[#A99BF0]/10 bg-[#0A0A0C]/60 text-[#9C9CA6] hover:text-[#ECECEF]'
                  }`}
                >
                  <Icon className="h-3.5 w-3.5 text-[#A99BF0]" />
                  <span>{sc.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      </LuxuryCard>

      {/* LINKED TASK SELECTOR */}
      <LuxuryCard className="space-y-2.5 p-4">
        <div className="flex items-center justify-between">
          <label htmlFor="focus-task-select" className="text-xs font-bold text-[#ECECEF]">
            وظیفه مرتبط با این جلسه
          </label>
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
              className="inline-flex items-center gap-1 text-[11px] font-medium text-[#A99BF0] hover:text-[#ECECEF]"
            >
              <CheckCircle2 className="h-3.5 w-3.5" />
              تکمیل این وظیفه
            </button>
          )}
        </div>

        <select
          id="focus-task-select"
          value={focus.linkedTaskId ?? ''}
          onChange={(e) => setFocusLinkedTask(e.target.value || null)}
          className="luxury-input py-2.5 text-xs"
        >
          <option value="" className="bg-[#16161A]">
            تمرکز آزاد (بدون اتصال به وظیفه خاص)
          </option>
          {availableTasks.map((t) => (
            <option key={t.id} value={t.id} className="bg-[#16161A]">
              {t.title}
            </option>
          ))}
        </select>
      </LuxuryCard>

      {/* RECENT FOCUS SESSIONS LOG */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between px-0.5">
          <h2 className="text-sm font-bold text-[#ECECEF]">گزارش جلسات تمرکز</h2>
          <Numeric
            value={sessions.length}
            variant="geo"
            className="rounded-lg bg-[#16161A] px-2 py-0.5 text-[11px] text-[#9C9CA6]"
          />
        </div>

        {recentSessions.length === 0 ? (
          <LuxuryCard variant="subtle" className="py-6 text-center text-xs text-[#6B6B75]">
            هنوز جلسه تمرکزی ثبت نشده است. اولین جلسه خود را شروع کنید.
          </LuxuryCard>
        ) : (
          <div className="space-y-2">
            {recentSessions.map((s) => (
              <LuxuryCard
                key={s.id}
                className="flex items-center justify-between gap-3 px-3.5 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-xs font-medium text-[#ECECEF]">
                    {s.taskTitleSnapshot ||
                      (s.mode === 'pomodoro' ? 'جلسه پومودورو' : 'تمرکز آزاد')}
                  </p>
                  <p className="mt-0.5 text-[10.5px] text-[#9C9CA6]">
                    {formatJalaaliShort(s.dateKey)} •{' '}
                    {s.mode === 'pomodoro' ? 'پومودورو' : 'تایمر آزاد'}
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-2">
                  <Numeric
                    value={formatDurationShort(s.durationMinutes)}
                    variant="display"
                    className="text-xl font-bold text-[#A99BF0]"
                  />
                  <button
                    type="button"
                    onClick={() => deleteFocusSession(s.id)}
                    aria-label="حذف جلسه"
                    className="rounded-lg p-1.5 text-[#6B6B75] hover:bg-[#E5484D]/15 hover:text-[#EF9095]"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </LuxuryCard>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
