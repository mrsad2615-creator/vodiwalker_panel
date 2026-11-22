import { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  Calendar,
  Check,
  ChevronLeft,
  Flame,
  Plus,
  Settings,
  Sparkles,
  Target,
  Timer,
} from '@/components/ui/icons';
import { setTaskStatus, upsertHabitLog, clearHabitLog } from '@/lib/db';
import { useFocusSessions, useGoals, useHabitLogs, useHabits, useTasks } from '@/lib/queries';
import {
  computeDailyDashboardSummary,
  computeHabitStats,
  isHabitScheduledOnDate,
} from '@/lib/analytics';
import {
  addDays,
  formatClockHHMM,
  formatJalaaliFull,
  getPersianGreeting,
  getTodayKey,
} from '@/lib/date';
import { formatDurationShort } from '@/lib/format';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { Numeric } from '@/components/ui/Numeric';
import { HabitIcon } from '@/components/ui/KairosMark';
import {
  CountUp,
  HeatDots,
  MiniBars,
  Ring,
  Sparkline,
  useCountUp,
} from '@/components/aurora/Aurora';
import { buildDailySeries, compareWindows, taskBreakdown, upcomingByDay } from '@/lib/homeMetrics';
import type { Goal, Task } from '@/types/domain';
import { GoalModal } from './GoalModal';

/**
 * Home — the whole product on one screen.
 *
 * 3.0 made this page quiet; it was honest and it was lifeless. 3.1 keeps the
 * honesty and restores the light:
 *
 *   · a violet field behind everything, lit from above, breathing;
 *   · glass plates that arrive one after another (40ms apart) with a single
 *     sheen crossing each of them;
 *   · a hero ring that fills to today's real progress while the number counts up;
 *   · every module of the product present, live, and *actionable*: finish a task,
 *     log a habit, start focus, open the calendar, read the week.
 *
 * Nothing here is a picture. Each card is a door or a button.
 */

export function DashboardPage() {
  const {
    setActiveTab,
    openCreateTask,
    openEditTask,
    settings,
    focus,
    startOrResumeFocus,
  } = useApp();

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

  /* ── Today, from the one function that owns the definition ──────────────── */
  const summary = useMemo(
    () => computeDailyDashboardSummary(tasks, habits, habitLogs, focusSessions, todayKey, settings),
    [tasks, habits, habitLogs, focusSessions, todayKey, settings]
  );

  const heroProgress = useCountUp(summary.dailyProgressPercent) / 100;

  const todayOpen = useMemo(
    () =>
      tasks
        .filter(
          (t) =>
            !t.archived && t.status !== 'completed' && t.dueDate !== null && t.dueDate <= todayKey
        )
        .sort((a, b) => {
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

  const series = useMemo(
    () => buildDailySeries({ tasks, habitLogs, focusSessions }, todayKey, 14),
    [tasks, habitLogs, focusSessions, todayKey]
  );
  const week = useMemo(() => compareWindows(series, 7), [series]);
  const breakdown = useMemo(() => taskBreakdown(tasks), [tasks]);
  const upcoming = useMemo(
    () =>
      upcomingByDay(tasks, addDays(todayKey, 1), 3).map((day) => {
        const info = formatJalaaliFull(day.dateKey);
        return {
          dateKey: day.dateKey,
          label: info.weekday,
          dayNumber: info.day,
          month: info.month,
          count: day.tasks.length,
        };
      }),
    [tasks, todayKey]
  );

  const weekKeys = useMemo(
    () => Array.from({ length: 7 }, (_, i) => addDays(todayKey, -(6 - i))),
    [todayKey]
  );
  const weekdayLabels = useMemo(() => weekKeys.map((k) => formatJalaaliFull(k).weekday[0]), [weekKeys]);

  const focusByDay = useMemo(
    () =>
      weekKeys.map((key) =>
        focusSessions
          .filter((s) => s.dateKey === key)
          .reduce((sum, s) => sum + s.durationMinutes, 0)
      ),
    [focusSessions, weekKeys]
  );

  const sparkValues = useMemo(
    () =>
      series.map(
        (point) =>
          point.focusMinutes + point.tasksDone * 10 + point.habitsDone * 6
      ),
    [series]
  );

  const focusPct = summary.focusGoalMinutes
    ? Math.min(1, summary.todayFocusMinutes / summary.focusGoalMinutes)
    : 0;

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

  const activeGoals = goals.filter((goal) => !goal.completed);
  const nothingLeft = todayOpen.length === 0 && scheduledHabits.length === 0;

  const launchers = [
    { tab: 'focus' as const, icon: Timer, label: 'تمرکز' },
    { tab: 'tasks' as const, icon: Check, label: 'وظایف' },
    { tab: 'habits' as const, icon: Flame, label: 'عادت‌ها' },
    { tab: 'calendar' as const, icon: Calendar, label: 'تقویم' },
    { tab: 'analytics' as const, icon: BarChart3, label: 'تحلیل' },
    { tab: 'settings' as const, icon: Settings, label: 'تنظیمات' },
  ];

  return (
    <div className="space-y-4 pb-8" data-testid="home-ready">
      {/* ══ HERO ══════════════════════════════════════════════════════════════ */}
      <section
        className="rise rise-1 aurora-card aurora-card--glass p-5"
        aria-labelledby="home-greeting"
      >
        <div className="flex items-start justify-between gap-4">
          {/* The day is the hero, not the interface: a greeting in small type, the
              date at 30px in the violet gradient, the clock underneath. It reads
              like the top of a journal page. */}
          <div className="min-w-0 pt-1">
            <p className="truncate text-[11px] font-medium text-[var(--text-2)]">
              {getPersianGreeting()}، {settings.displayName}
            </p>
            <h1 id="home-greeting" className="t-hero grad-text mt-1.5">
              <Numeric value={dateInfo.day} variant="geo" /> {dateInfo.month}
            </h1>
            <p className="mt-2 flex items-center gap-1.5 text-[11px] text-[var(--text-3)]">
              <span>{dateInfo.weekday}</span>
              <span className="opacity-40">·</span>
              <span className="num-geo tabular-nums" dir="ltr">
                {clock}
              </span>
              <Sparkles className="h-3 w-3 text-[var(--v-300)]" />
            </p>
          </div>

          <Ring value={heroProgress} size={118} stroke={9}>
            <div className="text-center">
              <div className="grad-text text-[26px] font-extrabold leading-none tabular-nums">
                <Numeric value={summary.dailyProgressPercent} variant="geo" />
                <span className="text-[13px]">٪</span>
              </div>
              <div className="mt-1 text-[11px] text-[var(--text-3)]">پیشرفت</div>
            </div>
          </Ring>
        </div>

        {/* The three numbers of the day, as pressable plates. */}
        <div className="mt-5 grid grid-cols-3 gap-2">
          {[
            {
              tab: 'tasks' as const,
              done: summary.todayTasksCompleted,
              total: summary.todayTasksTotal,
              label: 'کار',
            },
            {
              tab: 'habits' as const,
              done: summary.todayHabitsCompleted,
              total: summary.todayHabitsScheduled,
              label: 'عادت',
            },
            {
              tab: 'focus' as const,
              done: summary.todayFocusMinutes,
              total: summary.focusGoalMinutes,
              label: 'دقیقه تمرکز',
            },
          ].map((stat) => {
            const complete = stat.total > 0 && stat.done >= stat.total;
            return (
              <button
                key={stat.label}
                type="button"
                onClick={() => setActiveTab(stat.tab)}
                className="rounded-[14px] bg-[linear-gradient(160deg,rgba(167,139,250,0.14),rgba(23,12,58,0.55))] px-3 py-2.5 text-start ring-1 ring-inset ring-[rgba(167,139,250,0.12)] transition-transform active:scale-[0.98]"
              >
                <span className="flex items-baseline gap-1">
                  <CountUp
                    value={stat.done}
                    className={`text-[17px] font-bold leading-none ${
                      complete ? 'grad-text' : 'text-[var(--text)]'
                    }`}
                  />
                  <span className="text-[11px] text-[var(--text-3)]">
                    {stat.total > 0 ? (
                      <>
                        / <Numeric value={stat.total} variant="geo" />
                      </>
                    ) : (
                      ''
                    )}
                  </span>
                </span>
                <span className="mt-1.5 block truncate text-[11px] font-medium text-[var(--text-2)]">
                  {stat.label}
                </span>
              </button>
            );
          })}
        </div>
      </section>

      {/* ══ FOCUS ═════════════════════════════════════════════════════════════ */}
      <section
        className="rise rise-2 aurora-card aurora-card--glass overflow-hidden p-5"
        aria-label={focus.isRunning ? 'همین حالا' : undefined}
      >
        <div className="flex items-center justify-between">
          <h2 className="flex items-center gap-1.5 text-[13px] font-semibold text-[var(--text)]">
            <Timer className="h-3.5 w-3.5 text-[var(--v-300)]" />
            <span>تمرکز</span>
          </h2>
          {focus.isRunning ? (
            <span className="flex items-center gap-2 text-[11px] text-[var(--v-200)]">
              <span className="pulse-dot h-1.5 w-1.5 rounded-full bg-[var(--v-300)]" />
              در حال اجرا
            </span>
          ) : (
            <button
              type="button"
              onClick={() => setActiveTab('focus')}
              className="text-[11px] text-[var(--text-3)]"
            >
              تاریخچه
            </button>
          )}
        </div>

        <div className="mt-4 flex items-end justify-between gap-3">
          <div>
            <div className="flex items-baseline gap-1.5">
              <span className="grad-text text-[30px] font-extrabold leading-none tabular-nums">
                {focus.isRunning ? (
                  <Numeric
                    value={Math.max(1, Math.round(focus.remainingSeconds / 60))}
                    variant="geo"
                  />
                ) : (
                  <Numeric value={summary.todayFocusMinutes} variant="geo" />
                )}
              </span>
              <span className="text-[11px] text-[var(--text-2)]">
                {focus.isRunning ? 'دقیقه مانده' : 'دقیقه امروز'}
              </span>
            </div>
            <p className="mt-1.5 text-[11px] text-[var(--text-3)]">
              هدف روز: <Numeric value={summary.focusGoalMinutes} variant="geo" /> دقیقه
            </p>
          </div>

          <button
            type="button"
            onClick={() => {
              if (!focus.isRunning) startOrResumeFocus();
              setActiveTab('focus');
            }}
            className="rounded-full bg-[var(--grad-violet)] px-4 py-2 text-[12px] font-semibold text-white shadow-[0_10px_30px_-12px_rgba(109,74,255,0.9)] transition-transform active:scale-[0.97]"
          >
            {focus.isRunning ? 'ادامه' : 'شروع'}
          </button>
        </div>

        {/* Goal bar + the week's real minutes. */}
        <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-[rgba(196,181,253,0.14)]">
          <div
            className="h-full rounded-full bg-[var(--grad-violet)] transition-[width] duration-1000 ease-out"
            style={{ width: `${Math.round(focusPct * 100)}%` }}
          />
        </div>

        <div className="mt-4">
          <MiniBars values={focusByDay} labels={weekdayLabels} />
        </div>
      </section>

      {/* ══ TASKS ═════════════════════════════════════════════════════════════ */}
      <section className="rise rise-3 aurora-card px-5 py-4" aria-label="کارهای امروز">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-[13px] font-semibold text-[var(--text)]">امروز</h2>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setActiveTab('tasks')}
              className="text-[11px] text-[var(--text-3)]"
            >
              همهٔ کارها
            </button>
            <button
              type="button"
              onClick={() => openCreateTask({ defaultDueDate: todayKey })}
              aria-label="افزودن کار تازه"
              className="grid h-7 w-7 place-items-center rounded-full bg-[rgba(167,139,250,0.14)] text-[var(--v-200)] transition-transform active:scale-90"
            >
              <Plus className="h-3.5 w-3.5" strokeWidth={2.4} />
            </button>
          </div>
        </div>

        {todayOpen.length === 0 && todayDone.length === 0 ? (
          <div className="py-2">
            <p className="text-[13px] text-[var(--text-2)]">امروز کاری برنامه‌ریزی نشده است.</p>
            <button
              type="button"
              onClick={() => openCreateTask({ defaultDueDate: todayKey })}
              className="mt-2 text-[12px] font-medium text-[var(--v-200)]"
            >
              یک کار برای امروز بسازید
            </button>
          </div>
        ) : (
          <div className="-mx-1">
            {todayOpen.slice(0, 5).map((task, i) => {
              const overdue = task.dueDate !== null && task.dueDate < todayKey;
              return (
                <div
                  key={task.id}
                  className="rise flex items-center gap-3 rounded-[14px] px-1 py-2.5 transition-colors active:bg-[rgba(167,139,250,0.08)]"
                  style={{ animationDelay: `${140 + i * 45}ms` }}
                >
                  <button
                    type="button"
                    onClick={() => void completeTask(task)}
                    aria-label={`تکمیل ${task.title}`}
                    className="grid h-6 w-6 shrink-0 place-items-center rounded-full border border-[rgba(196,181,253,0.35)] text-transparent transition-all active:border-[var(--v-300)] active:bg-[var(--v-300)] active:text-[var(--v-950)]"
                  >
                    <Check className="h-3 w-3" strokeWidth={2.8} />
                  </button>
                  <button
                    type="button"
                    onClick={() => openEditTask(task)}
                    className="min-w-0 flex-1 text-start"
                  >
                    <span className="block truncate text-[14px] text-[var(--text)]">
                      {task.title}
                    </span>
                    {(overdue || task.dueTime) && (
                      <span className="mt-0.5 block text-[11px] text-[var(--text-2)]">
                        {overdue ? 'از روزهای گذشته' : `امروز ${task.dueTime}`}
                      </span>
                    )}
                  </button>
                  <ChevronLeft className="h-3.5 w-3.5 shrink-0 text-[var(--text-3)] opacity-40" />
                </div>
              );
            })}

            {todayDone.length > 0 && (
              <div className="mt-1 flex items-center justify-between border-t border-[rgba(196,181,253,0.10)] pt-2.5">
                <span className="text-[11px] text-[var(--text-3)]">
                  <Numeric value={todayDone.length} variant="geo" /> کار امروز انجام شد
                </span>
                <span className="grid h-5 w-5 place-items-center rounded-full bg-[rgba(139,107,255,0.18)] text-[var(--v-200)]">
                  <Check className="h-3 w-3" strokeWidth={2.8} />
                </span>
              </div>
            )}
          </div>
        )}
      </section>

      {/* ══ HABITS ════════════════════════════════════════════════════════════ */}
      {scheduledHabits.length > 0 && (
        <section className="rise rise-4 aurora-card px-5 py-4" aria-label="عادت‌ها">
          <div className="mb-1 flex items-center justify-between">
            <h2 className="flex items-center gap-1.5 text-[13px] font-semibold text-[var(--text)]">
              <Flame className="h-3.5 w-3.5 text-[var(--v-300)]" />
              <span>عادت‌ها</span>
            </h2>
            <span className="text-[11px] text-[var(--text-3)]">
              <Numeric value={summary.todayHabitsCompleted} variant="geo" /> از{' '}
              <Numeric value={summary.todayHabitsScheduled} variant="geo" />
            </span>
          </div>

          {scheduledHabits.slice(0, 4).map((habit) => {
            const log = todayLogs.get(habit.id);
            const done = log?.status === 'completed';
            const streak = computeHabitStats(habit, habitLogs, todayKey).currentStreak;
            const week = weekKeys.map((key) =>
              habitLogs.some(
                (l) => l.habitId === habit.id && l.dateKey === key && l.status === 'completed'
              )
            );
            return (
              <div
                key={habit.id}
                className="flex items-center gap-3 border-t border-[rgba(196,181,253,0.08)] py-2.5 first:border-t-0"
              >
                <button
                  type="button"
                  onClick={() => void toggleHabit(habit.id)}
                  aria-pressed={done}
                  aria-label={`${habit.name} — ${done ? 'انجام شد' : 'ثبت نشده'}`}
                  className={`grid h-8 w-8 shrink-0 place-items-center rounded-full transition-all active:scale-90 ${
                    done
                      ? 'bg-[var(--grad-violet)] text-white shadow-[0_0_18px_-4px_rgba(139,107,255,0.8)]'
                      : 'bg-[rgba(167,139,250,0.12)] text-[var(--v-200)]'
                  }`}
                >
                  {done ? (
                    <Check className="h-3.5 w-3.5" strokeWidth={2.8} />
                  ) : (
                    <HabitIcon name={habit.icon} className="h-3.5 w-3.5" />
                  )}
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('habits')}
                  className="min-w-0 flex-1 text-start"
                >
                  <span className="block truncate text-[13px] text-[var(--text)]">{habit.name}</span>
                  {streak > 0 && (
                    <span className="mt-0.5 block text-[11px] text-[var(--v-300)]">
                      <Numeric value={streak} variant="geo" /> روز پیوسته
                    </span>
                  )}
                </button>

                <HeatDots active={week} />
              </div>
            );
          })}
        </section>
      )}

      {/* ══ CALENDAR + GOALS (bento pair) ═════════════════════════════════════ */}
      <div className="rise rise-5 grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={() => setActiveTab('calendar')}
          className="aurora-card flex flex-col items-start p-4 text-start"
        >
          <span className="flex items-center gap-1.5 text-[11px] font-medium text-[var(--v-200)]">
            <Calendar className="h-3 w-3" />
            پیش رو
          </span>
          <span className="mt-3 flex w-full flex-col gap-1.5">
            {upcoming.map((day) => (
              <span key={day.dateKey} className="flex items-baseline justify-between gap-1">
                <span className="truncate text-[11px] text-[var(--text-2)]">{day.label}</span>
                <span
                  className={`text-[11px] ${
                    day.count > 0 ? 'text-[var(--text)]' : 'text-[var(--text-3)]'
                  }`}
                >
                  {day.count > 0 ? <Numeric value={day.count} variant="geo" /> : '—'}
                </span>
              </span>
            ))}
          </span>
          <span className="mt-3 flex items-center gap-0.5 text-[11px] text-[var(--text-3)]">
            تقویم
            <ChevronLeft className="h-2.5 w-2.5" />
          </span>
        </button>

        <button
          type="button"
          onClick={() => setGoalModal({ open: true, goal: null })}
          className="aurora-card flex flex-col items-start p-4 text-start"
        >
          <span className="flex items-center gap-1.5 text-[11px] font-medium text-[var(--v-200)]">
            <Target className="h-3 w-3" />
            اهداف
          </span>

          {activeGoals.length === 0 ? (
            <span className="mt-3 text-[11px] leading-5 text-[var(--text-2)]">
              هدفی ثبت نشده. یکی بساز.
            </span>
          ) : (
            <span className="mt-3 flex w-full flex-col gap-2">
              {activeGoals.slice(0, 2).map((goal) => {
                const pct =
                  goal.targetValue > 0
                    ? Math.min(100, Math.round((goal.currentValue / goal.targetValue) * 100))
                    : 0;
                return (
                  <span key={goal.id} className="block w-full">
                    <span className="flex items-baseline justify-between gap-1">
                      <span className="truncate text-[11px] text-[var(--text)]">{goal.title}</span>
                      <span className="text-[11px] text-[var(--v-200)]">
                        <Numeric value={pct} variant="geo" />٪
                      </span>
                    </span>
                    <span className="mt-1 block h-1 overflow-hidden rounded-full bg-[rgba(196,181,253,0.14)]">
                      <span
                        className="block h-full rounded-full bg-[var(--grad-violet)]"
                        style={{ width: `${pct}%` }}
                      />
                    </span>
                  </span>
                );
              })}
            </span>
          )}

          <span className="mt-3 flex items-center gap-0.5 text-[11px] text-[var(--text-3)]">
            هدف تازه
            <ChevronLeft className="h-2.5 w-2.5" />
          </span>
        </button>
      </div>

      {/* ══ THE WEEK ══════════════════════════════════════════════════════════ */}
      <section className="rise rise-6 aurora-card p-5" aria-label="این هفته">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="flex items-center gap-1.5 text-[13px] font-semibold text-[var(--text)]">
              <BarChart3 className="h-3.5 w-3.5 text-[var(--v-300)]" />
              <span>این هفته</span>
            </h2>
            <p className="mt-1.5 text-[11px] leading-5 text-[var(--text-2)]">
              <Numeric value={formatDurationShort(week.focusMinutes)} variant="geo" /> تمرکز ·{' '}
              <Numeric value={week.tasksDone} variant="geo" /> کار ·{' '}
              <Numeric value={week.habitsDone} variant="geo" /> عادت
              {week.deltaPercent !== null && (
                <>
                  {' '}
                  <span className={week.deltaPercent >= 0 ? 'text-[var(--v-200)]' : 'text-[var(--text-3)]'}>
                    {week.deltaPercent >= 0 ? '▲' : '▼'}{' '}
                    <Numeric value={Math.abs(week.deltaPercent)} variant="geo" />٪
                  </span>
                </>
              )}
            </p>
          </div>
          <Sparkline values={sparkValues} width={104} height={46} />
        </div>

        {breakdown.total > 0 && (
          <p className="mt-3 text-[11px] text-[var(--text-3)]">
            <Numeric value={breakdown.completionPercent} variant="geo" />٪ از کل کارها بسته شده است.
          </p>
        )}

        <button
          type="button"
          onClick={() => setActiveTab('analytics')}
          className="mt-3 flex items-center gap-1 text-[12px] font-medium text-[var(--v-200)]"
        >
          <span>تحلیل کامل</span>
          <ChevronLeft className="h-3 w-3" />
        </button>
      </section>

      {/* ══ THE WHOLE PRODUCT ═════════════════════════════════════════════════ */}
      <section className="rise rise-7" aria-label="همهٔ بخش‌ها">
        <h2 className="mb-2 px-1 text-[11px] font-medium text-[var(--text-3)]">همهٔ بخش‌ها</h2>
        <div className="grid grid-cols-6 gap-2">
          {launchers.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.tab}
                type="button"
                onClick={() => setActiveTab(item.tab)}
                className="aurora-card flex flex-col items-center gap-1.5 px-1 py-3 transition-transform active:scale-95"
                aria-label={`بخش ${item.label}`}
              >
                <Icon className="h-4 w-4 text-[var(--v-200)]" />
                <span className="w-full truncate text-center text-[11px] text-[var(--text-2)]">{item.label}</span>
              </button>
            );
          })}
        </div>
      </section>

      {nothingLeft && (
        <p className="rise rise-8 px-1 text-[11px] leading-5 text-[var(--text-3)]">
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
