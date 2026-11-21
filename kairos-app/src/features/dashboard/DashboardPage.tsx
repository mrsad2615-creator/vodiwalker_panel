import { useEffect, useMemo, useState } from 'react';
import { ArrowUpLeft, Check, Plus, Timer } from '@/components/ui/icons';
import type { Goal, Habit, Task } from '@/types/domain';
import {
  clearAllUserData,
  clearHabitLog,
  setTaskStatus,
  updateGoal,
  updateSettings,
  upsertHabitLog,
} from '@/lib/db';
import {
  useFocusSessions,
  useGoals,
  useHabitLogs,
  useHabits,
  useTasks,
} from '@/lib/queries';
import {
  computeDailyDashboardSummary,
  computeHabitStats,
  isHabitScheduledOnDate,
} from '@/lib/analytics';
import {
  formatClockHHMM,
  formatJalaaliFull,
  getPersianGreeting,
  getTodayKey,
} from '@/lib/date';
import { formatDurationShort, PRIORITY_META } from '@/lib/format';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { Numeric } from '@/components/ui/Numeric';
import { HabitIcon } from '@/components/ui/KairosMark';
import { GoalModal } from './GoalModal';

export function DashboardPage() {
  const { setActiveTab, openCreateTask, openEditTask, settings, pushToast } = useApp();
  const [clock, setClock] = useState(() => formatClockHHMM());
  const [goalModalState, setGoalModalState] = useState<{ open: boolean; goal?: Goal | null }>({
    open: false,
  });

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

  const summary = useMemo(
    () =>
      computeDailyDashboardSummary(
        tasks,
        habits,
        habitLogs,
        focusSessions,
        todayKey,
        settings
      ),
    [tasks, habits, habitLogs, focusSessions, todayKey, settings]
  );

  // Prioritized today's tasks (incomplete first sorted by quadrant -> time, then completed)
  const todayTasks = useMemo(() => {
    const qWeight = { q1: 0, q2: 1, q3: 2, q4: 3 };
    return tasks
      .filter(
        (t) =>
          !t.archived &&
          (t.dueDate === todayKey || (t.status === 'completed' && t.completedDate === todayKey))
      )
      .sort((a, b) => {
        if (a.status === 'completed' && b.status !== 'completed') return 1;
        if (a.status !== 'completed' && b.status === 'completed') return -1;
        if (qWeight[a.quadrant] !== qWeight[b.quadrant]) {
          return qWeight[a.quadrant] - qWeight[b.quadrant];
        }
        return (a.dueTime ?? '99:99').localeCompare(b.dueTime ?? '99:99');
      });
  }, [tasks, todayKey]);

  const todayHabits = useMemo(() => {
    const active = habits.filter((h) => !h.archived && isHabitScheduledOnDate(h, todayKey));
    const logMap = new Map(
      habitLogs.filter((l) => l.dateKey === todayKey).map((l) => [l.habitId, l])
    );
    return active.map((habit) => ({
      habit,
      log: logMap.get(habit.id),
      stats: computeHabitStats(habit, habitLogs, todayKey),
    }));
  }, [habits, habitLogs, todayKey]);

  const activeGoals = useMemo(() => goals.filter((g) => !g.completed).slice(0, 4), [goals]);

  const handleToggleTask = async (task: Task) => {
    const nextStatus = task.status === 'completed' ? 'todo' : 'completed';
    const { spawnedNext } = await setTaskStatus(task.id, nextStatus);
    void triggerHaptic(nextStatus === 'completed' ? 'success' : 'light', settings.hapticsEnabled);
    if (spawnedNext) {
      pushToast({
        title: 'تکرار بعدی زمان‌بندی شد',
        description: `وظیفه تکرارشونده «${task.title}» برای نوبت بعد ایجاد شد.`,
        tone: 'success',
      });
    }
  };

  const handleQuickHabitAction = async (habit: Habit, currentVal: number, isCompleted: boolean) => {
    if (habit.trackingMode === 'boolean') {
      if (isCompleted) {
        await clearHabitLog(habit.id, todayKey);
        void triggerHaptic('light', settings.hapticsEnabled);
      } else {
        await upsertHabitLog(habit.id, todayKey, 'completed', habit.targetValue);
        void triggerHaptic('success', settings.hapticsEnabled);
      }
      return;
    }

    // Quantitative habit quick step
    if (isCompleted) {
      await clearHabitLog(habit.id, todayKey);
      void triggerHaptic('light', settings.hapticsEnabled);
      return;
    }
    const step = habit.targetValue >= 20 ? Math.max(5, Math.round(habit.targetValue / 4)) : 1;
    const nextVal = Math.min(habit.targetValue, currentVal + step);
    const done = nextVal >= habit.targetValue;
    await upsertHabitLog(habit.id, todayKey, done ? 'completed' : 'partial', nextVal);
    void triggerHaptic(done ? 'success' : 'light', settings.hapticsEnabled);
  };

  const handleIncrementGoal = async (goal: Goal) => {
    const next = Math.min(goal.targetValue, goal.currentValue + 1);
    await updateGoal(goal.id, { currentValue: next });
    void triggerHaptic(next >= goal.targetValue ? 'success' : 'light', settings.hapticsEnabled);
  };

  const taskPct = summary.todayTasksTotal
    ? Math.round((summary.todayTasksCompleted / summary.todayTasksTotal) * 100)
    : 0;
  const habitPct = summary.todayHabitsScheduled
    ? Math.round((summary.todayHabitsCompleted / summary.todayHabitsScheduled) * 100)
    : 0;
  const focusPct = Math.min(
    100,
    Math.round((summary.todayFocusMinutes / Math.max(1, summary.focusGoalMinutes)) * 100)
  );

  return (
    <div className="space-y-5 pb-2">
      {/* ── MASTHEAD ────────────────────────────────────────────────────────
          Home has its own voice: an editorial masthead on a light shaft, not a
          card. Latin eyebrow in the display serif, Persian greeting in the body
          face, and the local clock as a live tabular read-out. */}
      <header
        className="relative -mx-4 overflow-hidden px-4 pb-4 pt-1"
        style={{
          background:
            'linear-gradient(174deg, color-mix(in srgb, var(--accent) 13%, transparent) 0%, transparent 62%)',
        }}
      >
        <div className="flex items-center justify-between">
          <span className="font-display-caps text-[8.5px] font-semibold text-[var(--accent-fg)]">
            KAIROS · TODAY
          </span>
          <span className="num-geo text-[11px] tabular-nums text-[var(--text-2)]">{clock}</span>
        </div>

        <h1 className="mt-2 text-[21px] font-bold leading-snug tracking-tight text-[var(--text)]">
          {getPersianGreeting()}، {settings.displayName}
        </h1>

        <div className="mt-1 flex items-center gap-2 text-[11px] text-[var(--text-3)]">
          <span>{dateInfo.weekday}</span>
          <span className="h-[3px] w-[3px] rounded-full bg-[var(--text-3)] opacity-60" />
          <Numeric value={dateInfo.day} variant="geo" className="text-[var(--text-2)]" />
          <span>{dateInfo.month}</span>
          <Numeric value={dateInfo.year} variant="geo" className="text-[var(--text-3)]" />
        </div>
      </header>

      {/* ── DAY RAIL ────────────────────────────────────────────────────────
          One band, three hairlines, three real ratios. No second ring, no tile
          grid: the numbers are the composition. */}
      <section aria-label="پیشرفت امروز" className="border-y border-[var(--border)] py-4">
        <div className="flex items-end justify-between gap-4">
          <div>
            <span className="font-display-caps text-[8.5px] font-semibold text-[var(--text-3)]">
              PROGRESS
            </span>
            <div className="mt-1 flex items-baseline gap-0.5">
              <Numeric
                value={summary.dailyProgressPercent}
                variant="display"
                className="text-[2.75rem] font-bold leading-none text-[var(--text)]"
              />
              <span className="num-display text-base font-medium text-[var(--text-3)]">%</span>
            </div>
            <p className="mt-1 text-[10px] text-[var(--text-3)]">پیشرفت امروز</p>
          </div>

          <button
            type="button"
            onClick={() => setActiveTab('focus')}
            className="text-end"
          >
            <span className="block text-[9.5px] text-[var(--text-3)]">تمرکز امروز</span>
            <Numeric
              value={formatDurationShort(summary.todayFocusMinutes)}
              className="mt-0.5 block text-[15px] font-semibold text-[var(--accent-fg)]"
            />
            <span className="num-geo mt-0.5 block text-[9px] text-[var(--text-3)]">
              هدف <Numeric value={summary.focusGoalMinutes} variant="geo" /> دقیقه
            </span>
          </button>
        </div>

        <div className="mt-4 grid grid-cols-3 gap-3">
          {[
            { label: 'کارها', done: summary.todayTasksCompleted, total: summary.todayTasksTotal, pct: taskPct, tab: 'tasks' as const },
            { label: 'عادت‌ها', done: summary.todayHabitsCompleted, total: summary.todayHabitsScheduled, pct: habitPct, tab: 'habits' as const },
            { label: 'تمرکز', done: summary.todayFocusMinutes, total: summary.focusGoalMinutes, pct: focusPct, tab: 'focus' as const },
          ].map((row) => (
            <button
              key={row.label}
              type="button"
              onClick={() => setActiveTab(row.tab)}
              className="group text-start"
            >
              <span className="flex items-baseline gap-1">
                <Numeric
                  value={row.done}
                  variant="geo"
                  className="text-[13px] font-bold text-[var(--text)]"
                />
                <span className="num-geo text-[10px] text-[var(--text-3)]">/ {row.total}</span>
              </span>
              <span className="mt-1.5 block h-[2px] w-full overflow-hidden rounded-full bg-white/8">
                <span
                  className="block h-full rounded-full transition-[width] duration-500"
                  style={{ width: `${Math.max(row.pct, 2)}%`, background: 'var(--accent-fg)' }}
                />
              </span>
              <span className="mt-1.5 block text-[9.5px] text-[var(--text-3)] group-hover:text-[var(--text-2)]">
                {row.label}
              </span>
            </button>
          ))}
        </div>
      </section>

      {/* ── PRIMARY ACTION ─────────────────────────────────────────────────── */}
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => setActiveTab('focus')}
          className="btn-royal flex flex-1 items-center justify-between gap-2 rounded-[14px] px-4 py-3"
        >
          <span className="flex items-center gap-2 text-[12px] font-semibold">
            <Timer className="h-4 w-4" strokeWidth={2} />
            شروع تمرکز
          </span>
          <span className="num-geo text-[11px] font-medium opacity-80">
            <Numeric value={settings.pomodoroFocusMin} variant="geo" /> دقیقه
          </span>
        </button>
        <button
          type="button"
          onClick={() => openCreateTask({ defaultDueDate: todayKey })}
          aria-label="افزودن سریع کار"
          className="grid h-[42px] w-[42px] place-items-center rounded-[14px] border border-[var(--border-strong)] text-[var(--accent-fg)] transition-colors hover:border-[var(--accent-fg)]/45"
        >
          <Plus className="h-4 w-4" strokeWidth={2} />
        </button>
      </div>

      {/* ── OVERDUE / SAMPLE NOTICE — hairline rows, not cards ─────────────── */}
      {summary.overdueCount > 0 && (
        <button
          type="button"
          onClick={() => setActiveTab('tasks')}
          className="flex w-full items-center justify-between border-s-2 border-[var(--danger)] px-3 py-2 text-start"
        >
          <span className="text-[11px] text-[var(--text-2)]">
            <Numeric value={summary.overdueCount} className="font-semibold text-[#EF9095]" /> وظیفه
            عقب‌افتاده
          </span>
          <ArrowUpLeft className="h-3.5 w-3.5 text-[var(--text-3)]" />
        </button>
      )}

      {settings.hasSampleData && !settings.sampleBannerDismissed && (
        <div className="flex items-center justify-between gap-3 border-s-2 border-[var(--accent-fg)]/40 px-3 py-2">
          <p className="text-[10.5px] leading-4 text-[var(--text-3)]">
            داده‌های نمونه بارگذاری شده‌اند؛ همهٔ نمودارها واقعی‌اند.
          </p>
          <div className="flex shrink-0 items-center gap-1">
            <button
              type="button"
              onClick={async () => {
                await clearAllUserData();
                pushToast({
                  title: 'فضای کاری پاک‌سازی شد',
                  description: 'می‌توانید داده‌های واقعی خودتان را ثبت کنید.',
                  tone: 'success',
                });
              }}
              className="rounded-lg border border-[var(--border-strong)] px-2 py-1 text-[10px] font-medium text-[var(--text)]"
            >
              شروع خالی
            </button>
            <button
              type="button"
              onClick={() => updateSettings({ sampleBannerDismissed: true })}
              aria-label="بستن پیشنهاد"
              className="px-1.5 py-1 text-[10px] text-[var(--text-3)]"
            >
              بستن
            </button>
          </div>
        </div>
      )}

      {/* ── TASK LEDGER ─────────────────────────────────────────────────────
          A ledger: rows on hairlines, priority as a 2px rule, time right-aligned.
          Deliberately not a stack of cards. */}
      <section>
        <div className="flex items-baseline justify-between">
          <h2 className="text-[13px] font-bold text-[var(--text)]">کارهای مهم امروز</h2>
          <button
            type="button"
            onClick={() => setActiveTab('tasks')}
            className="flex items-center gap-1 text-[10.5px] font-medium text-[var(--accent-fg)]"
          >
            همه
            <ArrowUpLeft className="h-3 w-3" />
          </button>
        </div>

        {todayTasks.length === 0 ? (
          <p className="mt-2 border-y border-[var(--border)] py-4 text-[11px] text-[var(--text-3)]">
            برای امروز کاری ثبت نشده است.{' '}
            <button
              type="button"
              onClick={() => openCreateTask({ defaultDueDate: todayKey })}
              className="font-medium text-[var(--accent-fg)] underline-offset-4 hover:underline"
            >
              اولین کار را اضافه کن
            </button>
          </p>
        ) : (
          <ul className="mt-2 divide-y divide-[var(--border)] border-y border-[var(--border)]">
            {todayTasks.slice(0, 6).map((task) => {
              const isDone = task.status === 'completed';
              const doneSubs = task.subtasks.filter((s) => s.completed).length;
              return (
                <li key={task.id} className="flex items-center gap-3 py-2.5">
                  <button
                    type="button"
                    onClick={() => handleToggleTask(task)}
                    aria-label={isDone ? `بازگرداندن ${task.title}` : `تکمیل ${task.title}`}
                    aria-pressed={isDone}
                    className={`grid h-[18px] w-[18px] shrink-0 place-items-center rounded-[5px] border transition-colors ${
                      isDone
                        ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-on)]'
                        : 'border-white/20 text-transparent hover:border-[var(--accent-fg)]'
                    }`}
                  >
                    <Check className="h-3 w-3" strokeWidth={3} />
                  </button>

                  <span
                    aria-hidden="true"
                    className="h-[18px] w-[2px] shrink-0 rounded-full"
                    style={{ background: PRIORITY_META[task.priority].dotColor, opacity: isDone ? 0.3 : 0.9 }}
                  />

                  <button
                    type="button"
                    onClick={() => openEditTask(task)}
                    className="min-w-0 flex-1 text-start"
                  >
                    <span
                      className={`block truncate text-[12.5px] ${
                        isDone ? 'text-[var(--text-3)] line-through' : 'text-[var(--text)]'
                      }`}
                    >
                      {task.title}
                    </span>
                    {(doneSubs > 0 || task.dueTime) && (
                      <span className="mt-0.5 flex items-center gap-1.5 text-[9.5px] text-[var(--text-3)]">
                        {task.dueTime && <span className="num-geo">{task.dueTime}</span>}
                        {doneSubs > 0 && (
                          <span>
                            <Numeric value={`${doneSubs}/${task.subtasks.length}`} variant="geo" /> زیرکار
                          </span>
                        )}
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* ── HABIT RAIL ──────────────────────────────────────────────────────
          Horizontal chips: a different gesture from the vertical ledger above,
          and glanceable with one thumb. */}
      <section>
        <div className="flex items-baseline justify-between">
          <h2 className="text-[13px] font-bold text-[var(--text)]">عادت‌های امروز</h2>
          <button
            type="button"
            onClick={() => setActiveTab('habits')}
            className="flex items-center gap-1 text-[10.5px] font-medium text-[var(--accent-fg)]"
          >
            همه
            <ArrowUpLeft className="h-3 w-3" />
          </button>
        </div>

        {todayHabits.length === 0 ? (
          <p className="mt-2 text-[11px] text-[var(--text-3)]">عادتی برای امروز برنامه‌ریزی نشده است.</p>
        ) : (
          <div className="no-scrollbar -mx-4 mt-2 flex gap-2 overflow-x-auto px-4 pb-1">
            {todayHabits.map(({ habit, log, stats }) => {
              const isCompleted = log?.status === 'completed';
              const currentVal = log?.value ?? 0;
              return (
                <button
                  key={habit.id}
                  type="button"
                  onClick={() => handleQuickHabitAction(habit, currentVal, isCompleted)}
                  className="flex shrink-0 items-center gap-2.5 rounded-[14px] border px-3 py-2.5 transition-colors"
                  style={{
                    borderColor: isCompleted
                      ? 'color-mix(in srgb, var(--accent-fg) 45%, transparent)'
                      : 'var(--border)',
                    background: isCompleted
                      ? 'color-mix(in srgb, var(--accent) 14%, transparent)'
                      : 'transparent',
                  }}
                >
                  <span
                    className="grid h-6 w-6 place-items-center rounded-full"
                    style={{
                      background: isCompleted
                        ? 'var(--accent)'
                        : 'color-mix(in srgb, var(--accent-fg) 12%, transparent)',
                      color: isCompleted ? 'var(--accent-on)' : 'var(--accent-fg)',
                    }}
                  >
                    {isCompleted ? (
                      <Check className="h-3 w-3" strokeWidth={3} />
                    ) : (
                      <HabitIcon name={habit.icon} className="h-3 w-3" color="currentColor" />
                    )}
                  </span>
                  <span className="text-start">
                    <span className="block max-w-[104px] truncate text-[11px] font-medium text-[var(--text)]">
                      {habit.name}
                    </span>
                    <span className="mt-0.5 flex items-center gap-1 text-[9px] text-[var(--text-3)]">
                      {habit.trackingMode === 'quantitative' ? (
                        <span>
                          <Numeric value={`${currentVal}/${habit.targetValue}`} variant="geo" /> {habit.unit}
                        </span>
                      ) : (
                        <span>{isCompleted ? 'ثبت شد' : 'ثبت نشده'}</span>
                      )}
                      <span className="opacity-60">·</span>
                      <Numeric value={stats.currentStreak} variant="geo" />
                      <span>روز</span>
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </section>

      {/* ── GOALS — two compact meters ─────────────────────────────────────── */}
      <section>
        <div className="flex items-baseline justify-between">
          <h2 className="text-[13px] font-bold text-[var(--text)]">اهداف فعال</h2>
          <button
            type="button"
            onClick={() => setGoalModalState({ open: true, goal: null })}
            className="flex items-center gap-1 text-[10.5px] font-medium text-[var(--accent-fg)]"
          >
            <Plus className="h-3 w-3" />
            هدف جدید
          </button>
        </div>

        {activeGoals.length === 0 ? (
          <p className="mt-2 text-[11px] text-[var(--text-3)]">
            هدفی تعریف نشده است؛ یک چشم‌انداز فصلی بساز.
          </p>
        ) : (
          <div className="mt-2 grid grid-cols-2 gap-2">
            {activeGoals.slice(0, 4).map((goal) => {
              const pct =
                goal.targetValue > 0
                  ? Math.min(100, Math.round((goal.currentValue / goal.targetValue) * 100))
                  : 0;
              return (
                <div
                  key={goal.id}
                  className="rounded-[12px] border border-[var(--border)] px-3 py-2.5 transition-colors hover:border-[var(--border-strong)]"
                >
                  <div className="flex items-start justify-between gap-1">
                    <button
                      type="button"
                      onClick={() => setGoalModalState({ open: true, goal })}
                      className="min-w-0 flex-1 text-start"
                    >
                      <span className="block truncate text-[11px] font-medium text-[var(--text)]">
                        {goal.title}
                      </span>
                      <span className="mt-0.5 flex items-baseline gap-1">
                        <Numeric
                          value={pct}
                          suffix="%"
                          variant="display"
                          className="text-[15px] font-bold text-[var(--accent-fg)]"
                        />
                        <span className="num-geo text-[9px] text-[var(--text-3)]">
                          <Numeric value={`${goal.currentValue}/${goal.targetValue}`} variant="geo" />
                        </span>
                      </span>
                    </button>
                    <button
                      type="button"
                      onClick={() => handleIncrementGoal(goal)}
                      aria-label={`افزایش پیشرفت ${goal.title}`}
                      className="grid h-5 w-5 shrink-0 place-items-center rounded-md border border-[var(--accent-fg)]/25 text-[var(--accent-fg)] transition-colors hover:bg-[color-mix(in_srgb,var(--accent)_22%,transparent)]"
                    >
                      <Plus className="h-2.5 w-2.5" />
                    </button>
                  </div>
                  <span className="mt-1.5 block h-[2px] w-full overflow-hidden rounded-full bg-white/8">
                    <span
                      className="block h-full rounded-full"
                      style={{ width: `${Math.max(pct, 2)}%`, background: 'var(--accent)' }}
                    />
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* ── FOOTER LINE ────────────────────────────────────────────────────── */}
      <button
        type="button"
        onClick={() => setActiveTab('analytics')}
        className="flex w-full items-center justify-between border-t border-[var(--border)] pt-3 text-start"
      >
        <span className="text-[10.5px] text-[var(--text-3)]">
          گزارش ماهانه و تحلیل واقعی عملکرد
        </span>
        <ArrowUpLeft className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
      </button>

      <GoalModal
        open={goalModalState.open}
        goal={goalModalState.goal}
        onClose={() => setGoalModalState({ open: false, goal: null })}
      />
    </div>
  );
}
