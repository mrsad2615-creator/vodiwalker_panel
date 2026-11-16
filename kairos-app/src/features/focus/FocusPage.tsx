import { useMemo, useState } from 'react';
import {
  Check,
  CheckCircle2,
  Pause,
  Play,
  RotateCcw,
  SkipForward,
  Sliders,
  Trash2,
} from 'lucide-react';
import { deleteFocusSession, setTaskStatus, updateSettings } from '@/lib/db';
import { useFocusSessions, useTasks } from '@/lib/queries';
import { formatJalaaliShort, getTodayKey } from '@/lib/date';
import { formatDurationShort, formatTimerSeconds } from '@/lib/format';
import { useApp } from '@/context/AppContext';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { ProgressRing } from '@/components/charts/ProgressRing';

const PRESET_DURATIONS = [15, 25, 45, 60] as const;

export function FocusPage() {
  const {
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
          Math.min(100, ((totalPhaseSeconds - focus.remainingSeconds) / Math.max(1, totalPhaseSeconds)) * 100)
        )
      : Math.min(100, (focus.elapsedSeconds / (60 * 60)) * 100);

  const displayTime =
    focus.mode === 'pomodoro'
      ? formatTimerSeconds(focus.remainingSeconds)
      : formatTimerSeconds(focus.elapsedSeconds);

  const phaseLabel =
    focus.mode === 'free'
      ? 'تایمر آزاد عمیق'
      : focus.phase === 'focus'
      ? 'جلسه تمرکز'
      : focus.phase === 'short_break'
      ? 'استراحت کوتاه'
      : 'استراحت بلند';

  return (
    <div className="space-y-5">
      {/* Serene Minimal Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-[#F4F1F8]">محیط تمرکز</h1>
          <p className="mt-0.5 text-[11px] text-[#A79EB9]">
            تمرکز امروز:{' '}
            <Numeric
              value={formatDurationShort(todayTotalMin)}
              className="font-semibold text-[#B69AEF]"
            />{' '}
            • چرخه‌های تکمیل‌شده:{' '}
            <Numeric value={focus.completedCycles} className="text-[#F4F1F8]" />
          </p>
        </div>

        <button
          type="button"
          onClick={() => setShowCustomConfig((v) => !v)}
          aria-label="تنظیم زمان‌های تمرکز"
          className={`flex h-9 w-9 items-center justify-center rounded-xl border transition ${
            showCustomConfig
              ? 'border-[#8052D6] bg-[#241936] text-[#B69AEF]'
              : 'border-[#B69AEF]/12 bg-[#15141B] text-[#A79EB9]'
          }`}
        >
          <Sliders className="h-4 w-4" />
        </button>
      </div>

      {/* Mode Switcher (Pomodoro vs Free Timer) */}
      <div className="grid grid-cols-2 gap-1 rounded-2xl border border-[#B69AEF]/12 bg-[#15141B] p-1">
        <button
          type="button"
          onClick={() => setFocusMode('pomodoro')}
          className={`rounded-xl py-2 text-xs font-medium transition ${
            focus.mode === 'pomodoro'
              ? 'bg-[#241936] text-[#F4F1F8]'
              : 'text-[#A79EB9] hover:text-[#F4F1F8]'
          }`}
        >
          پومودورو
        </button>
        <button
          type="button"
          onClick={() => setFocusMode('free')}
          className={`rounded-xl py-2 text-xs font-medium transition ${
            focus.mode === 'free'
              ? 'bg-[#241936] text-[#F4F1F8]'
              : 'text-[#A79EB9] hover:text-[#F4F1F8]'
          }`}
        >
          تایمر آزاد
        </button>
      </div>

      {/* Optional Duration Config Drawer */}
      {showCustomConfig && focus.mode === 'pomodoro' && (
        <LuxuryCard variant="subtle" className="space-y-3 p-4">
          <div className="grid grid-cols-3 gap-2.5">
            <div>
              <label className="mb-1 block text-[10.5px] text-[#A79EB9]">تمرکز (دقیقه)</label>
              <input
                type="number"
                min={1}
                max={180}
                value={focus.focusMinutes}
                onChange={(e) => {
                  const v = Math.max(1, Math.min(180, Number(e.target.value) || 25));
                  setFocusDurations(v, focus.shortBreakMinutes, focus.longBreakMinutes);
                  void updateSettings({ pomodoroFocusMin: v });
                }}
                className="luxury-input num-geo py-1.5 text-center text-xs"
              />
            </div>
            <div>
              <label className="mb-1 block text-[10.5px] text-[#A79EB9]">استراحت کوتاه</label>
              <input
                type="number"
                min={1}
                max={45}
                value={focus.shortBreakMinutes}
                onChange={(e) => {
                  const v = Math.max(1, Math.min(45, Number(e.target.value) || 5));
                  setFocusDurations(focus.focusMinutes, v, focus.longBreakMinutes);
                  void updateSettings({ pomodoroShortBreakMin: v });
                }}
                className="luxury-input num-geo py-1.5 text-center text-xs"
              />
            </div>
            <div>
              <label className="mb-1 block text-[10.5px] text-[#A79EB9]">استراحت بلند</label>
              <input
                type="number"
                min={5}
                max={60}
                value={focus.longBreakMinutes}
                onChange={(e) => {
                  const v = Math.max(5, Math.min(60, Number(e.target.value) || 15));
                  setFocusDurations(focus.focusMinutes, focus.shortBreakMinutes, v);
                  void updateSettings({ pomodoroLongBreakMin: v });
                }}
                className="luxury-input num-geo py-1.5 text-center text-xs"
              />
            </div>
          </div>
        </LuxuryCard>
      )}

      {/* CINEMATIC CALM FOCUS SANCTUARY CARD (Zero distracting animations while running) */}
      <LuxuryCard
        variant="hero"
        className="flex flex-col items-center px-5 py-7 text-center"
      >
        <span className="rounded-full border border-[#B69AEF]/22 bg-[#241936]/80 px-3 py-1 text-[11px] font-medium text-[#B69AEF]">
          {phaseLabel}
        </span>

        {/* Architectural Ring + Massive Non-Jittering Tabular Timer */}
        <div className="my-6">
          <ProgressRing
            percent={progressPercent}
            size={238}
            strokeWidth={7}
            trackColor="rgba(182, 154, 239, 0.09)"
          >
            <Numeric
              value={displayTime}
              variant="display"
              className="text-[4.1rem] font-extrabold tracking-tight text-[#F4F1F8]"
            />
            {linkedTask && (
              <p className="mt-2 max-w-[170px] truncate text-[11px] text-[#A79EB9]">
                {linkedTask.title}
              </p>
            )}
          </ProgressRing>
        </div>

        {/* Quick Pomodoro Presets (hidden cleanly while actively running for stillness) */}
        {focus.mode === 'pomodoro' && !focus.isRunning && focus.phase === 'focus' && (
          <div className="mb-5 flex items-center gap-2">
            {PRESET_DURATIONS.map((mins) => (
              <button
                key={mins}
                type="button"
                onClick={() => {
                  setFocusDurations(mins, focus.shortBreakMinutes, focus.longBreakMinutes);
                  void updateSettings({ pomodoroFocusMin: mins });
                }}
                className={`num-geo rounded-xl border px-3 py-1.5 text-xs font-semibold transition ${
                  focus.focusMinutes === mins
                    ? 'border-[#B69AEF]/50 bg-[#241936] text-[#F4F1F8]'
                    : 'border-[#B69AEF]/10 bg-[#09090D]/60 text-[#A79EB9] hover:text-[#F4F1F8]'
                }`}
              >
                {mins}m
              </button>
            ))}
          </div>
        )}

        {/* Calm Primary Controls */}
        <div className="flex w-full items-center justify-center gap-3">
          <button
            type="button"
            onClick={resetFocus}
            aria-label="بازنشانی تایمر"
            className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#B69AEF]/14 bg-[#09090D]/70 text-[#A79EB9] transition hover:border-[#B69AEF]/35 hover:text-[#F4F1F8]"
          >
            <RotateCcw className="h-4.5 w-4.5" />
          </button>

          <button
            type="button"
            onClick={focus.isRunning ? pauseFocus : startOrResumeFocus}
            className="btn-royal flex h-13 min-w-[152px] items-center justify-center gap-2 rounded-2xl px-6 text-sm font-bold"
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
              className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#B69AEF]/14 bg-[#09090D]/70 text-[#A79EB9] transition hover:border-[#B69AEF]/35 hover:text-[#F4F1F8]"
            >
              <SkipForward className="h-4.5 w-4.5" />
            </button>
          ) : (
            <button
              type="button"
              onClick={() => void finishAndSaveFocus()}
              aria-label="پایان و ثبت جلسه"
              title="پایان و ثبت جلسه"
              className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#8052D6]/45 bg-[#241936] text-[#B69AEF] transition hover:bg-[#8052D6] hover:text-[#F4F1F8]"
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
            className="mt-4 text-xs font-medium text-[#B69AEF] underline-offset-4 hover:underline"
          >
            پایان و ثبت{' '}
            <Numeric value={Math.max(1, Math.round(focus.elapsedSeconds / 60))} /> دقیقه تمرکز
          </button>
        )}
      </LuxuryCard>

      {/* LINKED TASK SELECTOR */}
      <LuxuryCard className="space-y-2.5 p-4">
        <div className="flex items-center justify-between">
          <label htmlFor="focus-task-select" className="text-xs font-bold text-[#F4F1F8]">
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
              className="inline-flex items-center gap-1 text-[11px] font-medium text-[#B69AEF] hover:text-[#F4F1F8]"
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
          <option value="" className="bg-[#15141B]">
            تمرکز آزاد (بدون اتصال به وظیفه خاص)
          </option>
          {availableTasks.map((t) => (
            <option key={t.id} value={t.id} className="bg-[#15141B]">
              {t.title}
            </option>
          ))}
        </select>
      </LuxuryCard>

      {/* RECENT FOCUS SESSIONS LOG */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between px-0.5">
          <h2 className="text-sm font-bold text-[#F4F1F8]">گزارش جلسات تمرکز</h2>
          <Numeric
            value={sessions.length}
            variant="geo"
            className="rounded-lg bg-[#15141B] px-2 py-0.5 text-[11px] text-[#A79EB9]"
          />
        </div>

        {recentSessions.length === 0 ? (
          <LuxuryCard variant="subtle" className="py-6 text-center text-xs text-[#6E6680]">
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
                  <p className="truncate text-xs font-medium text-[#F4F1F8]">
                    {s.taskTitleSnapshot || (s.mode === 'pomodoro' ? 'جلسه پومودورو' : 'تمرکز آزاد')}
                  </p>
                  <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">
                    {formatJalaaliShort(s.dateKey)} •{' '}
                    {s.mode === 'pomodoro' ? 'پومودورو' : 'تایمر آزاد'}
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-2">
                  <Numeric
                    value={formatDurationShort(s.durationMinutes)}
                    variant="display"
                    className="text-xl font-bold text-[#B69AEF]"
                  />
                  <button
                    type="button"
                    onClick={() => deleteFocusSession(s.id)}
                    aria-label="حذف جلسه"
                    className="rounded-lg p-1.5 text-[#6E6680] hover:bg-[#D9778B]/15 hover:text-[#EB9BAB]"
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
