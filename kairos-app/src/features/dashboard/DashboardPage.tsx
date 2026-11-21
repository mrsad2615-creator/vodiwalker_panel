import { useEffect, useMemo, useState } from 'react';
import {
  ArrowUpLeft,
  Check,
  Clock,
  Flame,
  Plus,
  Sparkles,
  Target,
  Timer,
  AlertCircle,
} from '@/components/ui/icons';
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
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { ProgressRing } from '@/components/charts/ProgressRing';
import { HabitIcon } from '@/components/ui/KairosMark';
import { EmptyState } from '@/components/ui/EmptyState';
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

  return (
    <div className="space-y-5">
      {/* Greeting & Jalali Date Header */}
      <section className="flex items-end justify-between pt-1">
        <div>
          <p className="text-xs font-medium text-[#9C9CA6]">
            {getPersianGreeting()}،{' '}
            <span className="text-[#ECECEF]">{settings.displayName}</span>
          </p>
          <h1 className="mt-1 flex items-baseline gap-1.5 text-[1.28rem] font-bold tracking-tight text-[#ECECEF]">
            <span>{dateInfo.weekday}،</span>
            <Numeric value={dateInfo.day} variant="geo" className="text-[1.32rem] text-[var(--accent-fg)]" />
            <span>{dateInfo.month}</span>
            <Numeric value={dateInfo.year} variant="geo" className="text-sm font-medium text-[#9C9CA6]" />
          </h1>
        </div>

        <div className="flex flex-col items-end">
          <Numeric
            value={clock}
            variant="geo"
            className="text-lg font-semibold tracking-tight text-[#ECECEF]/90"
          />
          <span className="text-[10px] text-[#6B6B75]">زمان محلی</span>
        </div>
      </section>

      {/* Optional First-Visit Starter Dataset Banner (1-tap clear to empty personal slate) */}
      {settings.hasSampleData && !settings.sampleBannerDismissed && (
        <div className="flex items-center justify-between gap-3 rounded-2xl border border-[var(--accent)]/28 bg-[#1C1C21]/55 px-3.5 py-2.5">
          <div className="min-w-0">
            <p className="text-[11.5px] font-semibold text-[#ECECEF]">
              داده‌های نمونه اولیه بارگذاری شده‌اند
            </p>
            <p className="mt-0.5 text-[10.5px] text-[#9C9CA6]">
              تمامی نمودارها واقعی‌اند؛ می‌توانید همین حالا با فضای کاملاً خالی شروع کنید.
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-1.5">
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
              className="rounded-lg border border-[var(--accent-fg)]/25 bg-[#16161A] px-2.5 py-1.5 text-[10.5px] font-medium text-[#ECECEF] hover:border-[var(--accent-fg)]/50"
            >
              شروع خالی
            </button>
            <button
              type="button"
              onClick={() => updateSettings({ sampleBannerDismissed: true })}
              className="rounded-lg px-2 py-1.5 text-[10.5px] text-[#9C9CA6] hover:text-[#ECECEF]"
            >
              بستن
            </button>
          </div>
        </div>
      )}

      {/* HERO CARD — Daily Mastery Progress & Signature Display Numerals */}
      <LuxuryCard variant="hero" className="p-5">
        <div className="flex items-center justify-between gap-4">
          <div className="min-w-0 flex-1 space-y-2.5">
            <div className="inline-flex items-center gap-1.5 text-[10.5px] font-medium text-[var(--text-3)]">
              <Sparkles className="h-3 w-3" />
              <span>پیشرفت امروز</span>
            </div>

            <div className="flex items-baseline gap-1">
              <Numeric
                value={summary.dailyProgressPercent}
                variant="display"
                className="text-[3.25rem] font-bold text-[var(--text)]"
              />
              <span className="num-display text-[1.4rem] font-semibold text-[var(--text-2)]">%</span>
            </div>

            <p className="text-[11px] leading-5 text-[var(--text-3)]">
              {summary.dailyProgressPercent >= 80
                ? 'ریتم فوق‌العاده‌ای دارید؛ روز درخشان خود را تثبیت کنید.'
                : summary.dailyProgressPercent >= 45
                ? 'در مسیر درست هستید؛ اولویت‌های کلیدی امروز را ببندید.'
                : summary.todayTasksTotal === 0 && summary.todayHabitsScheduled === 0
                ? 'امروز هنوز برنامه‌ای ثبت نشده است. اولین گام را بردارید.'
                : 'شروع قدرتمند با یک جلسه تمرکز کوتاه تفاوت بزرگی می‌سازد.'}
            </p>
          </div>

          <ProgressRing percent={summary.dailyProgressPercent} size={108} strokeWidth={8.5}>
            <Numeric
              value={`${summary.todayTasksCompleted}/${summary.todayTasksTotal}`}
              variant="geo"
              className="text-base font-bold text-[#ECECEF]"
            />
            <span className="mt-0.5 text-[9.5px] text-[#9C9CA6]">کار امروز</span>
          </ProgressRing>
        </div>

        {/* 3 Core Daily KPI Tiles */}
        <div className="mt-5 grid grid-cols-3 gap-2.5">
          <button
            type="button"
            onClick={() => setActiveTab('tasks')}
            className="rounded-[12px] border border-[var(--border)] bg-transparent px-3 py-2.5 text-right transition-colors hover:border-[var(--border-strong)]"
          >
            <span className="block text-[10px] text-[#9C9CA6]">تکمیل‌شده</span>
            <Numeric
              value={summary.todayTasksCompleted}
              variant="display"
              className="mt-1 text-[1.4rem] font-bold text-[var(--text)]"
              suffix={
                <span className="num-geo ml-0.5 text-xs font-medium text-[#6B6B75]">
                  /{summary.todayTasksTotal}
                </span>
              }
            />
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('habits')}
            className="rounded-[12px] border border-[var(--border)] bg-transparent px-3 py-2.5 text-right transition-colors hover:border-[var(--border-strong)]"
          >
            <span className="block text-[10px] text-[#9C9CA6]">عادت امروز</span>
            <Numeric
              value={summary.todayHabitsCompleted}
              variant="display"
              className="mt-1 text-[1.4rem] font-bold text-[var(--text)]"
              suffix={
                <span className="num-geo ml-0.5 text-xs font-medium text-[#6B6B75]">
                  /{summary.todayHabitsScheduled}
                </span>
              }
            />
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('focus')}
            className="rounded-[12px] border border-[var(--border)] bg-transparent px-3 py-2.5 text-right transition-colors hover:border-[var(--border-strong)]"
          >
            <span className="block text-[10px] text-[#9C9CA6]">زمان تمرکز</span>
            <Numeric
              value={formatDurationShort(summary.todayFocusMinutes)}
              variant="display"
              className="mt-1 text-[1.65rem] font-bold text-[#ECECEF]"
            />
          </button>
        </div>

        {/* Quick Actions Row */}
        <div className="mt-4 flex items-center gap-2.5">
          <button
            type="button"
            onClick={() => openCreateTask({ defaultDueDate: todayKey })}
            className="btn-royal flex flex-1 items-center justify-center gap-2 rounded-2xl py-3 text-xs font-semibold"
          >
            <Plus className="h-4 w-4" />
            <span>افزودن سریع کار</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('focus')}
            className="btn-ghost-luxury flex items-center justify-center gap-1.5 rounded-2xl px-4 py-3 text-xs font-medium text-[var(--accent-fg)]"
          >
            <Timer className="h-4 w-4" />
            <span>تمرکز</span>
          </button>
        </div>
      </LuxuryCard>

      {/* Overdue Gentle Notice if any */}
      {summary.overdueCount > 0 && (
        <button
          type="button"
          onClick={() => setActiveTab('tasks')}
          className="flex w-full items-center justify-between rounded-2xl border border-[#E5484D]/25 bg-[#1B141D] px-4 py-3 text-right transition hover:border-[#E5484D]/45"
        >
          <div className="flex items-center gap-2.5">
            <AlertCircle className="h-4 w-4 text-[#EF9095]" />
            <span className="text-xs font-medium text-[#ECECEF]">
              <Numeric value={summary.overdueCount} className="font-bold text-[#EF9095]" /> وظیفه
              عقب‌افتاده نیاز به بازبینی دارد
            </span>
          </div>
          <ArrowUpLeft className="h-4 w-4 text-[#9C9CA6]" />
        </button>
      )}

      {/* TODAY'S PRIORITY TASKS */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold text-[#ECECEF]">کارهای مهم امروز</h2>
          <button
            type="button"
            onClick={() => setActiveTab('tasks')}
            className="flex items-center gap-1 text-[11.5px] font-medium text-[var(--accent-fg)] hover:text-[#ECECEF]"
          >
            <span>مدیریت همه</span>
            <ArrowUpLeft className="h-3.5 w-3.5" />
          </button>
        </div>

        {todayTasks.length === 0 ? (
          <EmptyState
            icon={<Check className="h-5 w-5" />}
            title="وظیفه‌ای برای امروز ثبت نشده است"
            description="کارهای اولویت‌دار امروز خود را اضافه کنید تا پیشرفت روزانه شما دقیق محاسبه شود."
            action={
              <button
                type="button"
                onClick={() => openCreateTask({ defaultDueDate: todayKey })}
                className="btn-royal rounded-xl px-4 py-2 text-xs font-semibold"
              >
                افزودن اولین کار امروز
              </button>
            }
          />
        ) : (
          <div className="space-y-2">
            {todayTasks.slice(0, 5).map((task) => {
              const isDone = task.status === 'completed';
              const doneSubs = task.subtasks.filter((s) => s.completed).length;
              return (
                <LuxuryCard
                  key={task.id}
                  className={`flex items-center gap-3 px-3.5 py-3 transition ${
                    isDone ? 'opacity-65' : 'hover:border-[var(--accent-fg)]/22'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => handleToggleTask(task)}
                    aria-label={isDone ? 'لغو تکمیل' : 'تکمیل وظیفه'}
                    className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border transition ${
                      isDone
                        ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-on)] shadow-[0_0_12px_color-mix(in_srgb,var(--accent)_45%,transparent)]'
                        : 'border-[var(--accent-fg)]/28 bg-[#0E0E11] hover:border-[var(--accent-fg)]'
                    }`}
                  >
                    {isDone && <Check className="h-3.5 w-3.5" strokeWidth={2.6} />}
                  </button>

                  <button
                    type="button"
                    onClick={() => openEditTask(task)}
                    className="min-w-0 flex-1 text-right focus:outline-none"
                  >
                    <p
                      className={`truncate text-[13px] font-medium ${
                        isDone ? 'text-[#9C9CA6] line-through' : 'text-[#ECECEF]'
                      }`}
                    >
                      {task.title}
                    </p>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-[10.5px] text-[#9C9CA6]">
                      <span className="inline-flex items-center gap-1">
                        <span
                          className="h-1.5 w-1.5 rounded-full"
                          style={{ backgroundColor: PRIORITY_META[task.priority].dotColor }}
                        />
                        {PRIORITY_META[task.priority].label}
                      </span>
                      {task.dueTime && (
                        <span className="inline-flex items-center gap-1 text-[#9C9CA6]">
                          <Clock className="h-3 w-3 text-[#6B6B75]" />
                          <Numeric value={task.dueTime} variant="geo" className="text-[10.5px]" />
                        </span>
                      )}
                      {task.subtasks.length > 0 && (
                        <span className="text-[var(--accent-fg)]">
                          زیرکار{' '}
                          <Numeric
                            value={`${doneSubs}/${task.subtasks.length}`}
                            variant="geo"
                            className="text-[10.5px]"
                          />
                        </span>
                      )}
                    </div>
                  </button>
                </LuxuryCard>
              );
            })}
          </div>
        )}
      </section>

      {/* TODAY'S HABITS */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold text-[#ECECEF]">عادت‌های امروز</h2>
          <button
            type="button"
            onClick={() => setActiveTab('habits')}
            className="flex items-center gap-1 text-[11.5px] font-medium text-[var(--accent-fg)] hover:text-[#ECECEF]"
          >
            <span>تقویم حرارتی</span>
            <ArrowUpLeft className="h-3.5 w-3.5" />
          </button>
        </div>

        {todayHabits.length === 0 ? (
          <EmptyState
            icon={<Flame className="h-5 w-5" />}
            title="عادتی برای امروز برنامه‌ریزی نشده است"
            description="عادت‌های روزانه یا هفتگی خود را بسازید و روند آن‌ها را در نقشه 365 روزه ببینید."
            action={
              <button
                type="button"
                onClick={() => setActiveTab('habits')}
                className="btn-ghost-luxury rounded-xl px-4 py-2 text-xs font-medium text-[var(--accent-fg)]"
              >
                ایجاد عادت جدید
              </button>
            }
          />
        ) : (
          <div className="grid grid-cols-1 gap-2">
            {todayHabits.map(({ habit, log, stats }) => {
              const isCompleted = log?.status === 'completed';
              const isPartial = log?.status === 'partial';
              const currentVal = log?.value ?? 0;
              const pct =
                habit.targetValue > 0
                  ? Math.min(100, Math.round((currentVal / habit.targetValue) * 100))
                  : isCompleted
                  ? 100
                  : 0;

              return (
                <LuxuryCard
                  key={habit.id}
                  className="flex items-center justify-between gap-3 px-3.5 py-3"
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <div
                      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border"
                      style={{
                        backgroundColor: isCompleted
                          ? 'color-mix(in srgb, var(--accent) 24%, transparent)'
                          : 'rgba(30, 22, 60, 0.5)',
                        borderColor: isCompleted
                          ? 'color-mix(in srgb, var(--accent-fg) 40%, transparent)'
                          : 'color-mix(in srgb, var(--accent-fg) 10%, transparent)',
                      }}
                    >
                      <HabitIcon name={habit.icon} color={habit.accentColor} className="h-[18px] w-[18px]" />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate text-[13px] font-medium text-[#ECECEF]">
                        {habit.name}
                      </p>
                      <div className="mt-1 flex items-center gap-2.5 text-[10.5px] text-[#9C9CA6]">
                        {habit.trackingMode === 'quantitative' ? (
                          <span>
                            <Numeric
                              value={`${currentVal}/${habit.targetValue}`}
                              variant="geo"
                              className="font-semibold text-[var(--accent-fg)]"
                            />{' '}
                            {habit.unit}
                          </span>
                        ) : (
                          <span>{isCompleted ? 'تکمیل شد' : 'در انتظار ثبت'}</span>
                        )}
                        <span className="inline-flex items-center gap-0.5 text-[#9C9CA6]">
                          <Flame className="h-3 w-3 text-[var(--accent)]" />
                          <Numeric value={stats.currentStreak} variant="geo" className="text-[10.5px]" />
                          <span>d</span>
                        </span>
                      </div>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleQuickHabitAction(habit, currentVal, isCompleted)}
                    className={`flex h-9 min-w-[68px] items-center justify-center rounded-xl px-3 text-xs font-semibold transition ${
                      isCompleted
                        ? 'bg-[var(--accent)] text-[var(--accent-on)] shadow-[0_0_16px_color-mix(in_srgb,var(--accent)_45%,transparent)]'
                        : isPartial
                        ? 'border border-[var(--accent)]/50 bg-[#1C1C21] text-[var(--accent-fg)]'
                        : 'border border-[var(--accent-fg)]/16 bg-[#0E0E11] text-[#9C9CA6] hover:border-[var(--accent-fg)]/40 hover:text-[#ECECEF]'
                    }`}
                  >
                    {isCompleted ? (
                      <Check className="h-4 w-4" strokeWidth={2.5} />
                    ) : habit.trackingMode === 'quantitative' ? (
                      <Numeric value={`${pct}%`} variant="geo" className="text-xs" />
                    ) : (
                      <span>ثبت</span>
                    )}
                  </button>
                </LuxuryCard>
              );
            })}
          </div>
        )}
      </section>

      {/* ACTIVE GOALS */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold text-[#ECECEF]">اهداف فعال</h2>
          <button
            type="button"
            onClick={() => setGoalModalState({ open: true, goal: null })}
            className="flex items-center gap-1 text-[11.5px] font-medium text-[var(--accent-fg)] hover:text-[#ECECEF]"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>هدف جدید</span>
          </button>
        </div>

        {activeGoals.length === 0 ? (
          <EmptyState
            icon={<Target className="h-5 w-5" />}
            title="هدف فعالی ثبت نشده است"
            description="اهداف فصلی یا سالانه خود را تعریف کنید تا همیشه چشم‌اندازتان روشن بماند."
            action={
              <button
                type="button"
                onClick={() => setGoalModalState({ open: true, goal: null })}
                className="btn-ghost-luxury rounded-xl px-4 py-2 text-xs font-medium text-[var(--accent-fg)]"
              >
                تعریف هدف جدید
              </button>
            }
          />
        ) : (
          <div className="space-y-2.5">
            {activeGoals.map((goal) => {
              const pct =
                goal.targetValue > 0
                  ? Math.min(100, Math.round((goal.currentValue / goal.targetValue) * 100))
                  : 0;
              return (
                <LuxuryCard key={goal.id} className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <button
                      type="button"
                      onClick={() => setGoalModalState({ open: true, goal })}
                      className="min-w-0 flex-1 text-right focus:outline-none"
                    >
                      <h3 className="truncate text-[13px] font-semibold text-[#ECECEF]">
                        {goal.title}
                      </h3>
                      <p className="mt-0.5 text-[11px] text-[#9C9CA6]">
                        <Numeric
                          value={`${goal.currentValue} / ${goal.targetValue}`}
                          variant="geo"
                          className="font-medium text-[#ECECEF]"
                        />{' '}
                        {goal.unit}
                      </p>
                    </button>

                    <div className="flex items-center gap-2">
                      <Numeric
                        value={pct}
                        suffix="%"
                        variant="display"
                        className="text-xl font-bold text-[var(--accent-fg)]"
                      />
                      <button
                        type="button"
                        onClick={() => handleIncrementGoal(goal)}
                        aria-label="افزایش پیشرفت هدف"
                        className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--accent-fg)]/20 bg-[#1C1C21] text-[var(--accent-on)] transition hover:bg-[var(--accent)]"
                      >
                        <Plus className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>

                  <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-[#0A0A0C]">
                    <div
                      className="h-full rounded-full transition-all duration-300"
                      style={{
                        width: `${pct}%`,
                        background: 'var(--accent)',
                      }}
                    />
                  </div>
                </LuxuryCard>
              );
            })}
          </div>
        )}
      </section>

      {/* DAILY ACTIVITY SUMMARY & ANALYTICS QUICK-LINK */}
      <LuxuryCard
        variant="subtle"
        className="flex items-center justify-between gap-3 p-4"
      >
        <div>
          <p className="text-xs font-semibold text-[#ECECEF]">خلاصه فعالیت و گزارش ماهانه</p>
          <p className="mt-1 text-[11px] leading-5 text-[#9C9CA6]">
            هدف تمرکز امروز:{' '}
            <Numeric
              value={`${summary.todayFocusMinutes}/${summary.focusGoalMinutes}`}
              variant="geo"
              className="text-[var(--accent-fg)]"
            />{' '}
            دقیقه • مشاهده تحلیل واقعی عملکرد
          </p>
        </div>
        <button
          type="button"
          onClick={() => setActiveTab('analytics')}
          className="btn-ghost-luxury shrink-0 rounded-xl px-3.5 py-2 text-xs font-medium text-[var(--accent-fg)]"
        >
          گزارش ماه
        </button>
      </LuxuryCard>

      <GoalModal
        open={goalModalState.open}
        goal={goalModalState.goal}
        onClose={() => setGoalModalState({ open: false, goal: null })}
      />
    </div>
  );
}
