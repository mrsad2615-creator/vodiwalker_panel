import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowUpLeft, Check, ChevronLeft, Plus, Timer } from '@/components/ui/icons';
import type { Goal, Habit, Task } from '@/types/domain';
import {
  clearAllUserData,
  clearHabitLog,
  setTaskStatus,
  updateGoal,
  updateTask,
  updateSettings,
  upsertHabitLog,
} from '@/lib/db';
import {
  useFocusSessions,
  useGoals,
  useHabitLogs,
  useHabits,
  useJournal,
  useProjectStages,
  useProjects,
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
import { Rail, RailRow } from '@/components/ui/Rail';
import {
  AnalyticsShowcase,
  CalendarShowcase,
  FocusShowcase,
  HabitsShowcase,
  InsightShowcase,
  PlanShowcase,
  TasksShowcase,
} from './Showcases';
import {
  buildDailySeries,
  compareWindows,
  habitWeekMatrix,
  taskBreakdown,
  upcomingByDay,
} from '@/lib/homeMetrics';
import type { HomeMode } from '@/types/domain';
import { SectionHeader } from '@/components/ui/SectionHeader';
import { sectionEyebrow } from '@/lib/sections';
import { GoalModal } from './GoalModal';
import { JournalCard } from '@/features/journal/JournalCard';
import { computeGoalProgress } from '@/lib/goals';
import { nextDataStep, pickInsights } from '@/lib/insights';
import { buildDayPlan, schedulePlan } from '@/lib/planner';

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

  // If the setting changes elsewhere (settings screen, a restored backup), follow
  // it — but never fight the local value during a tap.
  useEffect(() => {
    const stored = readStoredMode(settings.homeMode);
    setBoardMode((current) => (stored === current ? current : stored));
  }, [settings.homeMode]);

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

  // ── Board data: arithmetic over the user's own rows, nothing else ──────────
  // The mode is owned locally first and written to settings afterwards. The
  // board used to read only `settings.homeMode`, so a slow or refused Dexie write
  // (or a live-query that had not fired yet) made the chips look dead: you tapped
  // «فشرده» and nothing changed. A tap must be instant, and the persistence is
  // an implementation detail the reader should never have to wait for.
  const [boardMode, setBoardMode] = useState<HomeMode>(() => readStoredMode(settings.homeMode));
  const series = useMemo(
    () => buildDailySeries({ focusSessions, tasks, habitLogs }, todayKey, 14),
    [focusSessions, tasks, habitLogs, todayKey]
  );
  const comparison = useMemo(() => compareWindows(series, 7), [series]);
  const breakdown = useMemo(() => taskBreakdown(tasks), [tasks]);
  const habitMatrix = useMemo(
    () => habitWeekMatrix(habits, habitLogs, todayKey, 7),
    [habits, habitLogs, todayKey]
  );
  const upcoming = useMemo(() => upcomingByDay(tasks, todayKey, 5), [tasks, todayKey]);

  // ── Intelligence on the home board ────────────────────────────────────────
  // Two things are computed here and nowhere else: the strongest insight the
  // data supports, and the plan the measured capacity allows. Both are pure
  // functions over the same rows the cards already show, so nothing can disagree.
  const journal = useJournal();
  const projects = useProjects();
  const stages = useProjectStages();
  const insights = useMemo(
    () =>
      pickInsights({ sessions: focusSessions, tasks, habits, habitLogs, journal, goals, todayKey }),
    [focusSessions, tasks, habits, habitLogs, journal, goals, todayKey]
  );
  const dataStep = useMemo(
    () =>
      nextDataStep({ sessions: focusSessions, tasks, habits, habitLogs, journal, goals, todayKey }),
    [focusSessions, tasks, habits, habitLogs, journal, goals, todayKey]
  );
  const plan = useMemo(
    () =>
      buildDayPlan({
        tasks,
        sessions: focusSessions,
        todayKey,
        goalMinutes: settings.dailyFocusGoalMinutes,
      }),
    [tasks, focusSessions, todayKey, settings.dailyFocusGoalMinutes]
  );

  const applyPlan = async () => {
    const existing = new Map(tasks.map((task) => [task.id, task.dueTime]));
    const { scheduled } = schedulePlan(plan, existing);
    if (scheduled.length === 0) {
      pushToast({ title: 'چیزی برای زمان‌بندی نبود', description: 'همهٔ کارها وقت داشتند.', tone: 'default' });
      return;
    }
    for (const entry of scheduled) {
      await updateTask(entry.taskId, { dueTime: entry.dueTime });
    }
    void triggerHaptic('success', settings.hapticsEnabled);
    pushToast({
      title: 'برنامه اعمال شد',
      description: `${scheduled.length} کار زمان گرفت؛ هر کدام با یک بار «برگرد» قابل بازگشت است.`,
      tone: 'success',
    });
  };
  const nextTasks = useMemo(
    () =>
      todayTasks
        .filter((t) => t.status !== 'completed')
        .slice(0, 3)
        .map((t) => ({ id: t.id, title: t.title, dueTime: t.dueTime })),
    [todayTasks]
  );

  const toggleTaskById = (id: string) => {
    const task = tasks.find((t) => t.id === id);
    if (task) void handleToggleTask(task);
  };
  const editTaskById = (id: string) => {
    const task = tasks.find((t) => t.id === id);
    if (task) openEditTask(task);
  };
  const toggleHabitById = (habitId: string) => {
    const today = habitLogs.find((log) => log.habitId === habitId && log.dateKey === todayKey);
    void triggerHaptic('light', settings.hapticsEnabled);
    if (today?.status === 'completed') void clearHabitLog(habitId, todayKey);
    else void upsertHabitLog(habitId, todayKey, 'completed', 1);
  };

  const chooseMode = (mode: HomeMode) => {
    setBoardMode(mode);
    storeMode(mode);
    void triggerHaptic('light', settings.hapticsEnabled);
    void updateSettings({ homeMode: mode }).catch(() => undefined);
  };

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
    // `home-ready` is the stable hook the suite waits on: the visual language has
    // changed twice behind it, and tests should not care which one is current.
    <div className="space-y-5 pb-2" data-testid="home-ready">
      {/* ── PAGE HEADER ───────────────────────────────────────────────────── */}
      <div
        className="relative -mx-4 px-4 pb-1 pt-1"
        style={{
          background:
            'linear-gradient(180deg, color-mix(in srgb, var(--accent) 9%, transparent) 0%, transparent 78%)',
        }}
      >
        <SectionHeader
          eyebrow={sectionEyebrow('dashboard')}
          title={`${getPersianGreeting()}، ${settings.displayName}`}
          meta={
            <>
              <span>{dateInfo.weekday}</span>
              <span className="opacity-50">·</span>
              <Numeric value={dateInfo.day} variant="geo" className="text-[var(--text-2)]" />
              <span>{dateInfo.month}</span>
              <Numeric value={dateInfo.year} variant="geo" />
              <span className="opacity-50">·</span>
              <span className="num-geo tabular-nums text-[var(--text-2)]">{clock}</span>
            </>
          }
          action={
            <button
              type="button"
              onClick={() => openCreateTask({ defaultDueDate: todayKey })}
              aria-label="افزودن سریع کار"
              className="grid h-9 w-9 place-items-center rounded-[var(--r-field)] border border-[var(--border-strong)] text-[var(--accent-fg)] transition-colors hover:border-[var(--accent-fg)]/45"
            >
              <Plus className="h-4 w-4" strokeWidth={2} />
            </button>
          }
        />
      </div>

      {/* ── BOARD MODE ─────────────────────────────────────────────────────
          The first screen is a board, not a fixed page: three honest layouts
          over the same data. */}
      <div className="flex items-center gap-1.5">
        {(
          [
            { id: 'cinematic', label: 'سینمایی' },
            { id: 'compact', label: 'فشرده' },
            { id: 'detailed', label: 'جزئی' },
          ] as const
        ).map((mode) => {
          const active = boardMode === mode.id;
          return (
            <button
              key={mode.id}
              type="button"
              onClick={() => chooseMode(mode.id)}
              aria-pressed={active}
              className={`min-h-[30px] rounded-full border px-3 py-1.5 text-[11px] font-medium transition-all active:scale-95 ${
                active
                  ? 'border-[var(--accent-fg)]/60 bg-[color-mix(in_srgb,var(--accent)_18%,transparent)] text-[var(--text)]'
                  : 'border-[var(--border)] text-[var(--text-3)] hover:text-[var(--text-2)]'
              }`}
            >
              {mode.label}
            </button>
          );
        })}
        <span className="ms-auto font-display-caps text-[8px] text-[var(--text-3)]">BOARD</span>
      </div>

      {/* ── TODAY ─────────────────────────────────────────────────────────── */}
      <Rail className="p-4">
        <div className="flex items-end justify-between gap-4">
          <div>
            <span className="eyebrow block">PROGRESS</span>
            <div className="mt-1.5 flex items-baseline gap-0.5">
              <Numeric
                value={summary.dailyProgressPercent}
                variant="display"
                className="text-[2.75rem] font-bold leading-none text-[var(--text)]"
              />
              <span className="num-display text-base font-medium text-[var(--text-3)]">%</span>
            </div>
            <p className="mt-1 text-[10px] text-[var(--text-3)]">پیشرفت امروز</p>
          </div>

          <button type="button" onClick={() => setActiveTab('focus')} className="text-end">
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
      </Rail>

      {/* ── PRIMARY ACTION ─────────────────────────────────────────────────── */}
      <button
        type="button"
        onClick={() => setActiveTab('focus')}
        className="btn-royal flex w-full items-center justify-between gap-2 rounded-[var(--r-rail)] px-4 py-3"
      >
        <span className="flex items-center gap-2 text-[12px] font-semibold">
          <Timer className="h-4 w-4" strokeWidth={2} />
          شروع تمرکز
        </span>
        <span className="num-geo text-[11px] font-medium opacity-80">
          <Numeric value={settings.pomodoroFocusMin} variant="geo" /> دقیقه
        </span>
      </button>

      {/* ── JOURNAL — the day, in your own words ────────────────────────────── */}
      <JournalCard />

      {/* ── NOTICES — hairline rows, never cards ───────────────────────────── */}
      {summary.overdueCount > 0 && (
        <button
          type="button"
          onClick={() => setActiveTab('tasks')}
          className="flex w-full items-center justify-between border-s-2 border-[var(--danger)] px-3 py-2 text-start"
        >
          <span className="text-[11px] text-[var(--text-2)]">
            <Numeric value={summary.overdueCount} className="font-semibold text-[var(--danger-fg)]" />{' '}
            وظیفه عقب‌افتاده
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

      {/* ── SHOWCASE BOARD ───────────────────────────────────────────────── */}
      <motion.div
        key={boardMode}
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
      >
      {boardMode === 'compact' ? (
        <div className="space-y-2">
          <div className="grid grid-cols-2 gap-2">
          <FocusShowcase
            dense
            minutes={summary.todayFocusMinutes}
            goalMinutes={summary.focusGoalMinutes}
            series={series}
            onStart={() => setActiveTab('focus')}
          />
          <TasksShowcase
            dense
            breakdown={breakdown}
            nextTasks={nextTasks}
            onToggle={toggleTaskById}
            onOpenTask={editTaskById}
            onOpenAll={() => setActiveTab('tasks')}
          />
          <HabitsShowcase
            dense
            matrix={habitMatrix}
            onToggle={toggleHabitById}
            onOpenAll={() => setActiveTab('habits')}
            onStart={() => setActiveTab('habits')}
          />
          <AnalyticsShowcase
            dense
            series={series}
            comparison={comparison}
            onOpen={() => setActiveTab('analytics')}
          />
          </div>
          <InsightShowcase
            dense
            insight={insights[0] ?? null}
            nextStep={dataStep}
            onAction={() => {
              const tab = insights[0]?.action?.tab;
              if (tab) setActiveTab(tab);
            }}
            onOpenAnalytics={() => setActiveTab('analytics')}
          />
        </div>
      ) : (
        <div className="space-y-2.5">
          <InsightShowcase
            insight={insights[0] ?? null}
            nextStep={dataStep}
            onAction={() => {
              const tab = insights[0]?.action?.tab;
              if (tab) setActiveTab(tab);
            }}
            onOpenAnalytics={() => setActiveTab('analytics')}
          />
          <PlanShowcase
            plan={plan}
            onApply={() => void applyPlan()}
            onOpenTasks={() => setActiveTab('tasks')}
          />
          <FocusShowcase
            minutes={summary.todayFocusMinutes}
            goalMinutes={summary.focusGoalMinutes}
            series={series}
            onStart={() => setActiveTab('focus')}
          />
          {boardMode === 'detailed' && (
            <CalendarShowcase
              upcoming={upcoming}
              onOpen={() => setActiveTab('calendar')}
            />
          )}
          <TasksShowcase
            breakdown={breakdown}
            nextTasks={nextTasks}
            onToggle={toggleTaskById}
            onOpenTask={editTaskById}
            onOpenAll={() => setActiveTab('tasks')}
          />
          <HabitsShowcase
            matrix={habitMatrix}
            onToggle={toggleHabitById}
            onOpenAll={() => setActiveTab('habits')}
            onStart={() => setActiveTab('habits')}
          />
          <AnalyticsShowcase
            series={series}
            comparison={comparison}
            onOpen={() => setActiveTab('analytics')}
          />
        </div>
      )}
      </motion.div>

      {/* ── TASKS ─────────────────────────────────────────────────────────── */}
      {boardMode === 'detailed' && (
        <>
      <section className="space-y-2">
        <SectionHeader
          size="section"
          title="کارهای مهم امروز"
          action={
            <button
              type="button"
              onClick={() => setActiveTab('tasks')}
              className="flex items-center gap-1 text-[10.5px] font-medium text-[var(--accent-fg)]"
            >
              همه
              <ArrowUpLeft className="h-3 w-3" />
            </button>
          }
        />

        {todayTasks.length === 0 ? (
          <Rail>
            <RailRow as="button" onClick={() => openCreateTask({ defaultDueDate: todayKey })}>
              <span className="flex-1 text-[11px] text-[var(--text-3)]">
                برای امروز کاری ثبت نشده است.
              </span>
              <span className="text-[10.5px] font-medium text-[var(--accent-fg)]">افزودن</span>
            </RailRow>
          </Rail>
        ) : (
          <Rail>
            {todayTasks.slice(0, 6).map((task) => {
              const isDone = task.status === 'completed';
              const doneSubs = task.subtasks.filter((s) => s.completed).length;
              return (
                <RailRow key={task.id}>
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
                    style={{
                      background: PRIORITY_META[task.priority].dotColor,
                      opacity: isDone ? 0.3 : 0.9,
                    }}
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

                  <ChevronLeft className="h-3.5 w-3.5 shrink-0 text-[var(--text-3)]" />
                </RailRow>
              );
            })}
          </Rail>
        )}
      </section>

      {/* ── HABITS (horizontal rail) ───────────────────────────────────────── */}
      <section className="space-y-2">
        <SectionHeader
          size="section"
          title="عادت‌های امروز"
          action={
            <button
              type="button"
              onClick={() => setActiveTab('habits')}
              className="flex items-center gap-1 text-[10.5px] font-medium text-[var(--accent-fg)]"
            >
              همه
              <ArrowUpLeft className="h-3 w-3" />
            </button>
          }
        />

        {todayHabits.length === 0 ? (
          <Rail>
            <RailRow>
              <span className="text-[11px] text-[var(--text-3)]">
                عادتی برای امروز برنامه‌ریزی نشده است.
              </span>
            </RailRow>
          </Rail>
        ) : (
          <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 pb-1">
            {todayHabits.map(({ habit, log, stats }) => {
              const isCompleted = log?.status === 'completed';
              const currentVal = log?.value ?? 0;
              return (
                <button
                  key={habit.id}
                  type="button"
                  onClick={() => handleQuickHabitAction(habit, currentVal, isCompleted)}
                  className="flex shrink-0 items-center gap-2.5 rounded-[var(--r-rail)] border px-3 py-2.5 transition-colors"
                  style={{
                    borderColor: isCompleted
                      ? 'color-mix(in srgb, var(--accent-fg) 45%, transparent)'
                      : 'var(--border)',
                    background: isCompleted
                      ? 'color-mix(in srgb, var(--accent) 12%, transparent)'
                      : 'var(--surface-1)',
                  }}
                >
                  <span
                    className="grid h-7 w-7 place-items-center rounded-full"
                    style={{
                      background: isCompleted
                        ? 'var(--accent)'
                        : 'color-mix(in srgb, var(--accent-fg) 12%, transparent)',
                      color: isCompleted ? 'var(--accent-on)' : 'var(--accent-fg)',
                    }}
                  >
                    {isCompleted ? (
                      <Check className="h-3.5 w-3.5" strokeWidth={3} />
                    ) : (
                      <HabitIcon name={habit.icon} className="h-3.5 w-3.5" color="currentColor" />
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

      {/* ── GOALS ─────────────────────────────────────────────────────────── */}
      <section className="space-y-2">
        <SectionHeader
          size="section"
          title="اهداف فعال"
          action={
            <button
              type="button"
              onClick={() => setGoalModalState({ open: true, goal: null })}
              className="flex items-center gap-1 text-[10.5px] font-medium text-[var(--accent-fg)]"
            >
              <Plus className="h-3 w-3" />
              هدف جدید
            </button>
          }
        />

        {activeGoals.length === 0 ? (
          <Rail>
            <RailRow as="button" onClick={() => setGoalModalState({ open: true, goal: null })}>
              <span className="flex-1 text-[11px] text-[var(--text-3)]">
                هدفی تعریف نشده است؛ یک چشم‌انداز فصلی بساز.
              </span>
              <span className="text-[10.5px] font-medium text-[var(--accent-fg)]">افزودن</span>
            </RailRow>
          </Rail>
        ) : (
          <Rail>
            {activeGoals.slice(0, 4).map((goal) => {
              // Progress is earned from linked tasks when there are any, and
              // self-reported only when the goal has no task-shaped work.
              const progress = computeGoalProgress(goal, tasks, { projects, stages });
              const pct = progress.percent;
              return (
                <RailRow key={goal.id}>
                  <button
                    type="button"
                    onClick={() => setGoalModalState({ open: true, goal })}
                    className="min-w-0 flex-1 text-start"
                  >
                    <span className="flex items-baseline justify-between gap-2">
                      <span className="truncate text-[11.5px] font-medium text-[var(--text)]">
                        {goal.title}
                      </span>
                      <span className="num-geo shrink-0 text-[9.5px] text-[var(--text-3)]">
                        <Numeric
                          value={`${progress.current}/${progress.target}`}
                          variant="geo"
                        />
                        {progress.mode === 'linked' && (
                          <span className="ms-1 text-[8.5px] text-[var(--text-3)]">کار</span>
                        )}
                        {progress.mode === 'projects' && (
                          <span className="ms-1 text-[8.5px] text-[var(--text-3)]">از پروژه</span>
                        )}
                      </span>
                    </span>
                    <span className="mt-1.5 flex items-center gap-2">
                      <span className="h-[2px] flex-1 overflow-hidden rounded-full bg-white/8">
                        <span
                          className="block h-full rounded-full"
                          style={{ width: `${Math.max(pct, 2)}%`, background: 'var(--accent)' }}
                        />
                      </span>
                      <Numeric
                        value={pct}
                        suffix="%"
                        className="w-8 text-end text-[10px] font-semibold text-[var(--accent-fg)]"
                      />
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => handleIncrementGoal(goal)}
                    aria-label={`افزایش پیشرفت ${goal.title}`}
                    className="grid h-7 w-7 shrink-0 place-items-center rounded-full border border-[var(--border)] text-[var(--text-2)] transition-colors hover:border-[var(--accent-fg)]/50 hover:text-[var(--accent-fg)]"
                  >
                    <Plus className="h-3 w-3" />
                  </button>
                </RailRow>
              );
            })}
          </Rail>
        )}
      </section>

      {/* ── MONTHLY REPORT LINK ───────────────────────────────────────────── */}
      <Rail>
        <RailRow as="button" onClick={() => setActiveTab('analytics')}>
          <span className="flex-1 text-[11px] text-[var(--text-2)]">
            گزارش ماهانه و تحلیل واقعی عملکرد
          </span>
          <ArrowUpLeft className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
        </RailRow>
      </Rail>
        </>
      )}

      <GoalModal
        open={goalModalState.open}
        goal={goalModalState.goal}
        onClose={() => setGoalModalState({ open: false, goal: null })}
      />
    </div>
  );
}

/* ── Home-mode persistence ────────────────────────────────────────────────────
   Two places on purpose: `localStorage` answers instantly (and survives a hard
   reload even before Dexie opens), while the settings row is what backup, restore
   and cross-device import carry. localStorage wins only when Dexie has no opinion. */

const HOME_MODE_KEY = 'kairos:home-mode';

function readStoredMode(fromSettings: HomeMode | undefined): HomeMode {
  try {
    const raw = window.localStorage.getItem(HOME_MODE_KEY);
    if (raw === 'cinematic' || raw === 'compact' || raw === 'detailed') return raw;
  } catch {
    // storage blocked: fall through to the settings row
  }
  return fromSettings ?? 'cinematic';
}

function storeMode(mode: HomeMode): void {
  try {
    window.localStorage.setItem(HOME_MODE_KEY, mode);
  } catch {
    // the DB write still carries it
  }
}
