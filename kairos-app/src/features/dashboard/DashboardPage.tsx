import { useEffect, useMemo, useState } from 'react';
import { Check, ChevronLeft, Plus, Timer } from '@/components/ui/icons';
import { setTaskStatus, upsertHabitLog, clearHabitLog } from '@/lib/db';
import { useFocusSessions, useGoals, useHabitLogs, useHabits, useTasks } from '@/lib/queries';
import { isHabitScheduledOnDate } from '@/lib/analytics';
import { addDays, formatClockHHMM, formatJalaaliFull, getPersianGreeting, getTodayKey } from '@/lib/date';
import { formatDurationShort } from '@/lib/format';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { Numeric } from '@/components/ui/Numeric';
import { HabitIcon } from '@/components/ui/KairosMark';
import { buildDailySeries, compareWindows, taskBreakdown, upcomingByDay } from '@/lib/homeMetrics';
import type { Goal, Task } from '@/types/domain';
import { GoalModal } from './GoalModal';

/**
 * Home — "امروز".
 *
 * ── Why this screen was rebuilt ──────────────────────────────────────────────
 * The 1.6–2.0 home was a *dashboard*: a hero percentage in a bordered card, three
 * progress bars, a board-mode switcher, five showcase cards with rings and
 * sparklines, and Latin eyebrow labels set at 8px. Every element was defensible
 * and together they read as noise — you could not answer "what do I do now?" in
 * one second, which is the only question the first screen exists to answer.
 *
 * The rebuild takes the opposite position, borrowed from Things 3:
 *
 *   · one heading, one date line, one row of three numbers — then the list;
 *   · the list is *real*: tapping a checkbox completes the task, tapping a dot
 *     logs the habit. Nothing on this screen is a picture of data;
 *   · two tones, hairlines, no borders, no rings, no gradients, no mode switch;
 *   · every number is the user's own, and an empty day says so in words.
 *
 * Everything removed here still exists one level down (Focus, Analytics, Habits);
 * the home screen stopped being a summary *of the interface* and became a summary
 * of the day.
 */

export function DashboardPage() {
  const { setActiveTab, openCreateTask, openEditTask, settings, focus, startOrResumeFocus } = useApp();
  const [clock, setClock] = useState(() => formatClockHHMM());
  const [goalModal, setGoalModal] = useState<{ open: boolean; goal?: Goal | null }>({ open: false });

  useEffect(() => {
    const t = window.setInterval(() => setClock(formatClockHHMM()), 30_000);
    return () => window.clearInterval(t);
  }, []);

  const todayKey = getTodayKey();
  const dateInfo = useMemo(() => formatJalaaliFull(todayKey), [todayKey]);

  const tasks = useTasks();
  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const focusSessions = useFocusSessions();
  const goals = useGoals();

  // ── The day, in numbers ───────────────────────────────────────────────────
  const todayOpen = useMemo(
    () =>
      tasks
        .filter((t) => !t.archived && t.status !== 'completed' && t.dueDate !== null && t.dueDate <= todayKey)
        .sort((a, b) => {
          // Overdue first, then by time of day, then by priority.
          const dateA = a.dueDate ?? todayKey;
          const dateB = b.dueDate ?? todayKey;
          if (dateA !== dateB) return dateA < dateB ? -1 : 1;
          return (a.dueTime ?? '99:99').localeCompare(b.dueTime ?? '99:99');
        }),
    [tasks, todayKey]
  );

  const todayDone = useMemo(
    () => tasks.filter((t) => !t.archived && t.status === 'completed' && t.completedDate === todayKey),
    [tasks, todayKey]
  );

  const scheduledHabits = useMemo(
    () => habits.filter((h) => !h.archived && isHabitScheduledOnDate(h, todayKey)),
    [habits, todayKey]
  );

  const todayLogs = useMemo(
    () => new Map(habitLogs.filter((l) => l.dateKey === todayKey).map((l) => [l.habitId, l])),
    [habitLogs, todayKey]
  );

  const habitsDone = scheduledHabits.filter(
    (h) => todayLogs.get(h.id)?.status === 'completed'
  ).length;

  const todayFocusMin = useMemo(
    () =>
      Math.round(
        focusSessions
          .filter((s) => s.dateKey === todayKey)
          .reduce((sum, s) => sum + s.durationMinutes, 0)
      ),
    [focusSessions, todayKey]
  );

  const series = useMemo(
    () => buildDailySeries({ tasks, habitLogs, focusSessions }, todayKey, 14),
    [tasks, habitLogs, focusSessions, todayKey]
  );
  const week = useMemo(() => compareWindows(series, 7), [series]);
  const breakdown = useMemo(() => taskBreakdown(tasks), [tasks]);
  // Tomorrow onward: today already has its own section.
  const upcoming = useMemo(
    () =>
      upcomingByDay(tasks, addDays(todayKey, 1), 3).map((day) => ({
        ...day,
        label: formatJalaaliFull(day.dateKey).weekday,
        dayNumber: formatJalaaliFull(day.dateKey).day,
        month: formatJalaaliFull(day.dateKey).month,
        count: day.tasks.length,
      })),
    [tasks, todayKey]
  );

  // The seven-day habit strip: did this habit happen, day by day.
  const weekKeys = useMemo(
    () => Array.from({ length: 7 }, (_, i) => addDays(todayKey, -(6 - i))),
    [todayKey]
  );

  const completeTask = async (task: Task) => {
    void triggerHaptic('success', settings.hapticsEnabled);
    await setTaskStatus(task.id, task.status === 'completed' ? 'todo' : 'completed');
  };

  const toggleHabit = async (habitId: string) => {
    void triggerHaptic('light', settings.hapticsEnabled);
    const log = todayLogs.get(habitId);
    if (log?.status === 'completed') await clearHabitLog(habitId, todayKey);
    else await upsertHabitLog(habitId, todayKey, 'completed', 1);
  };

  const nothingLeft = todayOpen.length === 0 && scheduledHabits.length === 0;

  return (
    <div className="space-y-6 pb-6" data-testid="home-ready">
      {/* ── HEADING ─────────────────────────────────────────────────────────── */}
      <header className="space-y-1.5 pt-1">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="t-title text-[var(--text)]">
              {getPersianGreeting()}، {settings.displayName}
            </h1>
            <p className="t-caption mt-1">
              {dateInfo.weekday} · <Numeric value={dateInfo.day} variant="geo" />{' '}
              {dateInfo.month} <Numeric value={dateInfo.year} variant="geo" />
              <span className="mx-1.5 opacity-40">·</span>
              <span className="num-geo tabular-nums">{clock}</span>
            </p>
          </div>
          <button
            type="button"
            onClick={() => openCreateTask({ defaultDueDate: todayKey })}
            aria-label="افزودن کار تازه"
            className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[var(--surface-1)] text-[var(--accent-fg)] transition-colors active:bg-[var(--surface-2)]"
          >
            <Plus className="h-4 w-4" strokeWidth={2.2} />
          </button>
        </div>
      </header>

      {/* ── THE DAY IN THREE NUMBERS ──────────────────────────────────────────
          No rings, no bars: three facts, one line, tappable. */}
      <section aria-label="خلاصهٔ امروز" className="grid grid-cols-3 gap-3 border-y border-[var(--rule)] py-4">
        {[
          {
            tab: 'tasks' as const,
            done: todayDone.length,
            total: todayOpen.length + todayDone.length,
            label: 'کار',
          },
          { tab: 'habits' as const, done: habitsDone, total: scheduledHabits.length, label: 'عادت' },
          {
            tab: 'focus' as const,
            done: todayFocusMin,
            total: settings.dailyFocusGoalMinutes,
            label: 'دقیقه تمرکز',
          },
        ].map((stat) => {
          const complete = stat.total > 0 && stat.done >= stat.total;
          return (
            <button
              key={stat.label}
              type="button"
              onClick={() => setActiveTab(stat.tab)}
              className="text-start"
            >
              <span className="flex items-baseline gap-1">
                <Numeric
                  value={stat.done}
                  variant="geo"
                  className={`text-[19px] font-semibold leading-none ${
                    complete ? 'text-[var(--accent-fg)]' : 'text-[var(--text)]'
                  }`}
                />
                <span className="num-geo text-[11px] text-[var(--text-3)]">
                  {stat.total > 0 ? `/ ${stat.total}` : ''}
                </span>
              </span>
              <span className="mt-1.5 block text-[11px] text-[var(--text-3)]">{stat.label}</span>
            </button>
          );
        })}
      </section>

      {/* ── NOW ───────────────────────────────────────────────────────────────
          Only a *running* session gets its own block. The next task does not need
          one: it is already the first row of the list below, and showing it twice
          is how a calm screen starts to feel like a feed. */}
      {focus.isRunning && (
        <section aria-label="همین حالا">
          <button
            type="button"
            onClick={() => setActiveTab('focus')}
            className="k-group flex w-full items-center gap-3 p-4 text-start"
          >
            <span className="focus-live-dot h-2 w-2 shrink-0 rounded-full bg-[var(--accent)]" />
            <span className="min-w-0 flex-1">
              <span className="block text-[11px] text-[var(--text-3)]">در حال تمرکز</span>
              <span className="mt-0.5 block t-item text-[var(--text)]">
                <Numeric value={formatDurationShort(Math.max(1, Math.round(focus.remainingSeconds / 60)))} variant="geo" />
                <span className="text-[var(--text-2)]"> مانده</span>
              </span>
            </span>
            <ChevronLeft className="h-4 w-4 shrink-0 text-[var(--text-3)]" />
          </button>
        </section>
      )}

      {/* ── TODAY ───────────────────────────────────────────────────────────── */}
      <section aria-label="کارهای امروز">
        <div className="k-group-head px-0 pb-2">
          <h2 className="t-label">امروز</h2>
          <button
            type="button"
            onClick={() => setActiveTab('tasks')}
            className="text-[11px] text-[var(--text-3)]"
          >
            همهٔ کارها
          </button>
        </div>

        <div className="k-group">
          {todayOpen.length === 0 && todayDone.length === 0 ? (
            <div className="px-4 py-5">
              <p className="t-meta">امروز کاری برنامه‌ریزی نشده است.</p>
              <button
                type="button"
                onClick={() => openCreateTask({ defaultDueDate: todayKey })}
                className="mt-2 text-[12px] font-medium text-[var(--accent-fg)]"
              >
                یک کار برای امروز بسازید
              </button>
            </div>
          ) : (
            <>
              {todayOpen.slice(0, 6).map((task) => {
                const overdue = task.dueDate !== null && task.dueDate < todayKey;
                return (
                  <div key={task.id} className="k-row">
                    <button
                      type="button"
                      onClick={() => void completeTask(task)}
                      aria-label={`تکمیل ${task.title}`}
                      className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-[var(--rule-strong)] text-transparent transition-colors active:border-[var(--accent)] active:text-[var(--accent)]"
                    >
                      <Check className="h-3 w-3" strokeWidth={2.6} />
                    </button>
                    <button
                      type="button"
                      onClick={() => openEditTask(task)}
                      className="min-w-0 flex-1 text-start"
                    >
                      <span className="block truncate t-body text-[var(--text)]">{task.title}</span>
                      {(overdue || task.dueTime) && (
                        <span className="mt-0.5 block text-[11px] text-[var(--text-3)]">
                          {overdue ? 'از روزهای گذشته' : `امروز ${task.dueTime}`}
                        </span>
                      )}
                    </button>
                    <ChevronLeft className="h-3.5 w-3.5 shrink-0 text-[var(--text-3)] opacity-50" />
                  </div>
                );
              })}

              <button
                type="button"
                onClick={() => {
                  if (!focus.isRunning) startOrResumeFocus();
                  setActiveTab('focus');
                }}
                className="k-row gap-2 text-[var(--accent-fg)]"
              >
                <Timer className="h-3.5 w-3.5 shrink-0" />
                <span className="text-[12px] font-medium">شروع تمرکز</span>
              </button>

              {todayDone.length > 0 && (
                <div className="k-row justify-between opacity-55">
                  <span className="t-meta">
                    <Numeric value={todayDone.length} variant="geo" /> کار امروز انجام شد
                  </span>
                  <Check className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
                </div>
              )}
            </>
          )}
        </div>
      </section>

      {/* ── HABITS ──────────────────────────────────────────────────────────── */}
      {scheduledHabits.length > 0 && (
        <section aria-label="عادت‌های امروز">
          <div className="k-group-head px-0 pb-2">
            <h2 className="t-label">عادت‌ها</h2>
            <span className="text-[11px] text-[var(--text-3)]">
              <Numeric value={habitsDone} variant="geo" /> از{' '}
              <Numeric value={scheduledHabits.length} variant="geo" />
            </span>
          </div>

          <div className="k-group divide-none">
            {scheduledHabits.slice(0, 6).map((habit) => {
              const log = todayLogs.get(habit.id);
              const done = log?.status === 'completed';
              return (
                <div key={habit.id} className="k-row">
                  <button
                    type="button"
                    onClick={() => void toggleHabit(habit.id)}
                    aria-pressed={done}
                    aria-label={`${habit.name} — ${done ? 'انجام شد' : 'ثبت نشده'}`}
                    className={`grid h-7 w-7 shrink-0 place-items-center rounded-full transition-colors ${
                      done
                        ? 'bg-[var(--accent)] text-[var(--accent-on)]'
                        : 'bg-[var(--surface-2)] text-[var(--text-3)]'
                    }`}
                  >
                    {done ? (
                      <Check className="h-3.5 w-3.5" strokeWidth={2.6} />
                    ) : (
                      <HabitIcon name={habit.icon} className="h-3.5 w-3.5" />
                    )}
                  </button>

                  <button
                    type="button"
                    onClick={() => setActiveTab('habits')}
                    className="min-w-0 flex-1 text-start"
                  >
                    <span className="block truncate t-body text-[var(--text)]">{habit.name}</span>
                  </button>

                  {/* Seven days, one dot each: the streak is visible without a chart. */}
                  <span aria-hidden="true" className="flex shrink-0 items-center gap-1">
                    {weekKeys.map((key) => {
                      const hit = habitLogs.some(
                        (l) => l.habitId === habit.id && l.dateKey === key && l.status === 'completed'
                      );
                      return (
                        <span
                          key={key}
                          className="h-1 w-1 rounded-full"
                          style={{
                            background: hit
                              ? 'var(--accent)'
                              : 'color-mix(in srgb, var(--text) 12%, transparent)',
                          }}
                        />
                      );
                    })}
                  </span>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {/* ── AHEAD ───────────────────────────────────────────────────────────── */}
      {upcoming.length > 0 && (
        <section aria-label="پیش رو">
          <div className="k-group-head px-0 pb-2">
            <h2 className="t-label">پیش رو</h2>
            <button
              type="button"
              onClick={() => setActiveTab('calendar')}
              className="text-[11px] text-[var(--text-3)]"
            >
              تقویم
            </button>
          </div>
          <div className="k-group">
            {upcoming.map((day) => (
              <button
                key={day.dateKey}
                type="button"
                onClick={() => setActiveTab('calendar')}
                className="k-row justify-between"
              >
                <span className="t-body text-[var(--text-2)]">
                  {day.label}
                  <span className="ms-2 text-[11px] text-[var(--text-3)]">
                    <Numeric value={day.dayNumber} variant="geo" /> {day.month}
                  </span>
                </span>
                <span className="t-meta">
                  {day.count > 0 ? (
                    <>
                      <Numeric value={day.count} variant="geo" /> کار
                    </>
                  ) : (
                    'خالی'
                  )}
                </span>
              </button>
            ))}
          </div>
        </section>
      )}

      {/* ── GOALS ─────────────────────────────────────────────────────────────
          Kept because a goal is not a chart: it is a line with a number that is
          either getting closer to its target or not. */}
      {goals.length > 0 && (
        <section aria-label="اهداف">
          <div className="k-group-head px-0 pb-2">
            <h2 className="t-label">اهداف</h2>
            <button
              type="button"
              onClick={() => setGoalModal({ open: true, goal: null })}
              className="text-[11px] text-[var(--text-3)]"
            >
              هدف تازه
            </button>
          </div>
          <div className="k-group">
            {goals
              .filter((goal) => !goal.completed)
              .slice(0, 3)
              .map((goal) => {
                const pct =
                  goal.targetValue > 0
                    ? Math.min(100, Math.round((goal.currentValue / goal.targetValue) * 100))
                    : 0;
                return (
                  <button
                    key={goal.id}
                    type="button"
                    onClick={() => setGoalModal({ open: true, goal })}
                    className="k-row justify-between"
                  >
                    <span className="min-w-0 flex-1 truncate t-body text-[var(--text)]">
                      {goal.title}
                    </span>
                    <span className="shrink-0 text-[11px] text-[var(--text-3)]">
                      <Numeric value={goal.currentValue} variant="geo" /> /{' '}
                      <Numeric value={goal.targetValue} variant="geo" />
                      <span className="ms-1.5 num-geo text-[var(--accent-fg)]">{pct}٪</span>
                    </span>
                  </button>
                );
              })}
            {goals.filter((goal) => !goal.completed).length === 0 && (
              <div className="px-4 py-4">
                <p className="t-meta">هدفی برای پیگیری نیست.</p>
              </div>
            )}
          </div>
        </section>
      )}

      {/* ── THE WEEK, IN ONE LINE ───────────────────────────────────────────── */}
      <section aria-label="این هفته" className="border-t border-[var(--rule)] pt-4">
        <p className="t-meta leading-relaxed">
          این هفته <Numeric value={formatDurationShort(week.focusMinutes)} variant="geo" /> تمرکز،{' '}
          <Numeric value={week.tasksDone} variant="geo" /> کار و{' '}
          <Numeric value={week.habitsDone} variant="geo" /> ثبت عادت
          {week.deltaPercent !== null && (
            <>
              {' '}
              — <Numeric value={Math.abs(week.deltaPercent)} variant="geo" />٪{' '}
              {week.deltaPercent >= 0 ? 'بیشتر' : 'کمتر'} از هفتهٔ قبل
            </>
          )}
          .
        </p>
        {breakdown.total > 0 && (
          <p className="t-caption mt-1">
            <Numeric value={breakdown.completionPercent} variant="geo" />٪ از کل کارها بسته شده است.
          </p>
        )}
        <button
          type="button"
          onClick={() => setActiveTab('analytics')}
          className="mt-3 flex items-center gap-1 text-[12px] font-medium text-[var(--accent-fg)]"
        >
          <span>تحلیل کامل</span>
          <ChevronLeft className="h-3 w-3" />
        </button>
      </section>

      {nothingLeft && (
        <p className="t-caption">
          روز خالی است. بهترین کار برای امروز: یک بلوک تمرکز بگذارید تا فردا چیزی برای
          اندازه‌گیری داشته باشید.
        </p>
      )}

      <GoalModal
        key={goalModal.open ? (goalModal.goal?.id ?? 'new') : 'closed'}
        open={goalModal.open}
        goal={goalModal.goal ?? null}
        onClose={() => setGoalModal({ open: false })}
      />
    </div>
  );
}
