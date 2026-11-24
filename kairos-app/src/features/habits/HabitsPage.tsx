import { ComparisonChart } from '@/components/charts/ComparisonChart';
import { useMemo, useState } from 'react';
import {
  Archive,
  ArchiveRestore,
  ArrowUpDown,
  BarChart3,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Clock,
  Edit3,
  Flame,
  LayoutGrid,
  LayoutList,
  ListChecks,
  Minus,
  Plus,
  Trash2,
  X,
} from '@/components/ui/icons';
import type { Habit, HabitLog } from '@/types/domain';
import { clearHabitLog, deleteHabit, updateHabit, upsertHabitLog } from '@/lib/db';
import { useFocusSessions, useHabitLogs, useHabits, useTasks } from '@/lib/queries';
import {
  computeAnnualHeatmap,
  computeHabitStats,
  isHabitScheduledOnDate,
  type HeatmapDayCell,
} from '@/lib/analytics';
import {
  addDays,
  dateKeyToJalaali,
  formatJalaaliFull,
  getPersianWeekdayIndex,
  getTodayKey,
  PERSIAN_WEEKDAYS_SHORT,
} from '@/lib/date';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { SectionHeader } from '@/components/ui/SectionHeader';
import { sectionEyebrow } from '@/lib/sections';
import { HabitIcon } from '@/components/ui/KairosMark';
import {
  ActivityHeatmap,
  type HeatmapOrientation,
} from '@/components/charts/ActivityHeatmap';
import { EmptyState } from '@/components/ui/EmptyState';
import { HabitModal } from './HabitModal';
import { HabitDetailView } from './HabitDetailView';

type HabitLayoutMode = 'vertical' | 'compact' | 'grid';
type HabitFilterMode = 'all' | 'scheduled' | 'pending' | 'completed' | 'archived';
type HabitSortMode = 'default' | 'streak' | 'adherence_desc' | 'needs_focus';

function readStoredPref<T extends string>(key: string, fallback: T): T {
  try {
    const val = window.localStorage.getItem(key);
    return (val as T) || fallback;
  } catch {
    return fallback;
  }
}

function writeStoredPref(key: string, val: string): void {
  try {
    window.localStorage.setItem(key, val);
  } catch {
    // ignore private browsing quota errors
  }
}

function getScheduleBadge(habit: Habit): string {
  if (habit.scheduleType === 'daily') return 'روزانه';
  if (habit.scheduleType === 'weekly_target') return `${habit.weeklyTarget} بار در هفته`;
  return `${habit.scheduledDays.length} روز هفته`;
}

export function HabitsPage() {
  const { settings, pushToast } = useApp();
  const todayKey = getTodayKey();
  const currentJYear = dateKeyToJalaali(todayKey).jy;

  const [selectedYear, setSelectedYear] = useState(currentJYear);
  const [selectedDateKey, setSelectedDateKey] = useState(todayKey);
  const [detailHabitId, setDetailHabitId] = useState<string | null>(null);
  const [modalState, setModalState] = useState<{ open: boolean; habit?: Habit | null }>({
    open: false,
  });

  // Pro Management Controls (persisted across sessions)
  const [heatmapOrientation, setHeatmapOrientation] = useState<HeatmapOrientation>(() =>
    readStoredPref<HeatmapOrientation>('kairos_heatmap_orient', 'vertical')
  );
  const [layoutMode, setLayoutMode] = useState<HabitLayoutMode>(() =>
    readStoredPref<HabitLayoutMode>('kairos_habit_layout', 'vertical')
  );
  const [filterMode, setFilterMode] = useState<HabitFilterMode>('all');
  const [sortMode, setSortMode] = useState<HabitSortMode>('default');
  const [heatmapExpanded, setHeatmapExpanded] = useState(true);

  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const tasks = useTasks();
  const focusSessions = useFocusSessions();

  const activeHabits = useMemo(() => habits.filter((h) => !h.archived), [habits]);
  const archivedHabits = useMemo(() => habits.filter((h) => h.archived), [habits]);

  const detailHabit = useMemo(
    () => habits.find((h) => h.id === detailHabitId) ?? null,
    [habits, detailHabitId]
  );

  // Fast O(1) lookup map: `${habitId}::${dateKey}` -> HabitLog
  const logLookup = useMemo(() => {
    const map = new Map<string, HabitLog>();
    for (const l of habitLogs) {
      map.set(`${l.habitId}::${l.dateKey}`, l);
    }
    return map;
  }, [habitLogs]);

  const heatmap = useMemo(
    () =>
      computeAnnualHeatmap({
        jalaaliYear: selectedYear,
        habits: activeHabits,
        habitLogs,
        tasks,
        focusSessions,
        todayKey,
        mode: 'habits',
      }),
    [selectedYear, activeHabits, habitLogs, tasks, focusSessions, todayKey]
  );

  const selectedCell = useMemo(
    () => heatmap.cells.find((c) => c.dateKey === selectedDateKey) ?? null,
    [heatmap.cells, selectedDateKey]
  );

  const habitStatsMap = useMemo(() => {
    const map = new Map<string, ReturnType<typeof computeHabitStats>>();
    activeHabits.forEach((h) => {
      map.set(h.id, computeHabitStats(h, habitLogs, todayKey));
    });
    return map;
  }, [activeHabits, habitLogs, todayKey]);

  // Last 7 days ending on selectedDateKey (for interactive 7-day vertical matrix strip)
  const last7Days = useMemo(() => {
    const anchor = selectedDateKey <= todayKey ? selectedDateKey : todayKey;
    return Array.from({ length: 7 }, (_, i) => {
      const dk = addDays(anchor, i - 6);
      const j = dateKeyToJalaali(dk);
      return {
        dateKey: dk,
        dayNum: j.jd,
        weekdayShort: PERSIAN_WEEKDAYS_SHORT[getPersianWeekdayIndex(dk)],
      };
    });
  }, [selectedDateKey, todayKey]);

  // Daily Executive Summary for selectedDateKey
  const daySummary = useMemo(() => {
    const scheduled = activeHabits.filter((h) => isHabitScheduledOnDate(h, selectedDateKey));
    let completed = 0;
    let missed = 0;
    for (const h of scheduled) {
      const log = logLookup.get(`${h.id}::${selectedDateKey}`);
      if (log?.status === 'completed') completed += 1;
      else if (log?.status === 'missed') missed += 1;
    }
    const percent = scheduled.length > 0 ? Math.round((completed / scheduled.length) * 100) : 0;
    let topStreak = 0;
    let avgAdherenceSum = 0;
    for (const h of activeHabits) {
      const st = habitStatsMap.get(h.id);
      if (st) {
        if (st.currentStreak > topStreak) topStreak = st.currentStreak;
        avgAdherenceSum += st.adherencePercent;
      }
    }
    const avgAdherence =
      activeHabits.length > 0 ? Math.round(avgAdherenceSum / activeHabits.length) : 0;
    return {
      scheduledCount: scheduled.length,
      completedCount: completed,
      missedCount: missed,
      pendingCount: Math.max(0, scheduled.length - completed),
      percent,
      topStreak,
      avgAdherence,
    };
  }, [activeHabits, selectedDateKey, logLookup, habitStatsMap]);

  // Filtered & sorted habit list
  const visibleHabits = useMemo(() => {
    if (filterMode === 'archived') {
      return archivedHabits;
    }

    let list = activeHabits.filter((h) => {
      const log = logLookup.get(`${h.id}::${selectedDateKey}`);
      if (filterMode === 'scheduled') return isHabitScheduledOnDate(h, selectedDateKey);
      if (filterMode === 'completed') return log?.status === 'completed';
      if (filterMode === 'pending') {
        return isHabitScheduledOnDate(h, selectedDateKey) && log?.status !== 'completed';
      }
      return true;
    });

    if (sortMode === 'streak') {
      list = list.slice().sort((a, b) => {
        const sa = habitStatsMap.get(a.id)?.currentStreak ?? 0;
        const sb = habitStatsMap.get(b.id)?.currentStreak ?? 0;
        return sb - sa;
      });
    } else if (sortMode === 'adherence_desc') {
      list = list.slice().sort((a, b) => {
        const aa = habitStatsMap.get(a.id)?.adherencePercent ?? 0;
        const ab = habitStatsMap.get(b.id)?.adherencePercent ?? 0;
        return ab - aa;
      });
    } else if (sortMode === 'needs_focus') {
      list = list.slice().sort((a, b) => {
        const aa = habitStatsMap.get(a.id)?.adherencePercent ?? 100;
        const ab = habitStatsMap.get(b.id)?.adherencePercent ?? 100;
        return aa - ab;
      });
    }

    return list;
  }, [
    filterMode,
    sortMode,
    activeHabits,
    archivedHabits,
    logLookup,
    selectedDateKey,
    habitStatsMap,
  ]);

  const handleSetDayHabit = async (
    habit: Habit,
    dateKey: string,
    action: 'completed' | 'step_up' | 'step_down' | 'missed' | 'unlogged'
  ) => {
    if (dateKey > todayKey) return;
    const current = logLookup.get(`${habit.id}::${dateKey}`);
    if (action === 'unlogged') {
      await clearHabitLog(habit.id, dateKey);
      void triggerHaptic('light', settings.hapticsEnabled);
      return;
    }
    if (action === 'missed') {
      await upsertHabitLog(habit.id, dateKey, 'missed', 0);
      void triggerHaptic('warning', settings.hapticsEnabled);
      return;
    }
    if ((action === 'step_up' || action === 'step_down') && habit.trackingMode === 'quantitative') {
      const step = habit.targetValue >= 20 ? Math.max(5, Math.round(habit.targetValue / 4)) : 1;
      const curVal = current?.value ?? 0;
      const nextVal =
        action === 'step_up'
          ? Math.min(habit.targetValue, curVal + step)
          : Math.max(0, curVal - step);
      if (nextVal <= 0) {
        await clearHabitLog(habit.id, dateKey);
        void triggerHaptic('light', settings.hapticsEnabled);
        return;
      }
      const done = nextVal >= habit.targetValue;
      await upsertHabitLog(habit.id, dateKey, done ? 'completed' : 'partial', nextVal);
      void triggerHaptic(done ? 'success' : 'light', settings.hapticsEnabled);
      return;
    }

    await upsertHabitLog(habit.id, dateKey, 'completed', habit.targetValue);
    void triggerHaptic('success', settings.hapticsEnabled);
  };

  // Cycle single day cell on the 7-day strip: unlogged -> completed -> missed -> unlogged
  const handleCycleStripDay = async (habit: Habit, dateKey: string) => {
    if (dateKey > todayKey) return;
    const current = logLookup.get(`${habit.id}::${dateKey}`);
    if (!current || current.status === 'partial') {
      await handleSetDayHabit(habit, dateKey, 'completed');
    } else if (current.status === 'completed') {
      await handleSetDayHabit(habit, dateKey, 'missed');
    } else {
      await handleSetDayHabit(habit, dateKey, 'unlogged');
    }
  };

  const handleToggleArchive = async (habit: Habit) => {
    const nextArchived = !habit.archived;
    await updateHabit(habit.id, { archived: nextArchived });
    void triggerHaptic('light', settings.hapticsEnabled);
    pushToast({
      title: nextArchived ? 'عادت به آرشیو منتقل شد' : 'عادت بازگردانی شد',
      description: habit.name,
      tone: 'default',
    });
  };

  const handlePermanentDelete = async (habit: Habit) => {
    await deleteHabit(habit.id);
    void triggerHaptic('warning', settings.hapticsEnabled);
    pushToast({
      title: 'عادت برای همیشه حذف شد',
      description: habit.name,
      tone: 'warning',
    });
  };

  const shiftSelectedDate = (delta: number) => {
    const next = addDays(selectedDateKey, delta);
    if (next > todayKey) return;
    setSelectedDateKey(next);
    const nextJYear = dateKeyToJalaali(next).jy;
    if (nextJYear !== selectedYear) setSelectedYear(nextJYear);
  };

  const handleHeatmapOrient = (next: HeatmapOrientation) => {
    setHeatmapOrientation(next);
    writeStoredPref('kairos_heatmap_orient', next);
  };

  const handleLayoutChange = (next: HabitLayoutMode) => {
    setLayoutMode(next);
    writeStoredPref('kairos_habit_layout', next);
  };

  // Dedicated Habit Detail Screen
  if (detailHabit) {
    return (
      <>
        <HabitDetailView
          habit={detailHabit}
          allLogs={habitLogs}
          tasks={tasks}
          focusSessions={focusSessions}
          onBack={() => setDetailHabitId(null)}
          onEdit={() => setModalState({ open: true, habit: detailHabit })}
        />
        <HabitModal
          open={modalState.open}
          habit={modalState.habit}
          onClose={() => setModalState({ open: false, habit: null })}
          onDeleted={() => setDetailHabitId(null)}
        />
      </>
    );
  }

  const selectedDateInfo = formatJalaaliFull(selectedDateKey);
  const isViewingToday = selectedDateKey === todayKey;

  return (
    <div className="space-y-4">
      {/* Page header — one rhythm across the product */}
      <SectionHeader
        eyebrow={sectionEyebrow('habits')}
        title="مدیریت حرفه‌ای عادت‌ها"
        meta={<span>چیدمان عمودی و افقی، ثبت ۷ روزه و تفکیک روزهای ثبت‌نشده</span>}
        action={
          <button
            type="button"
            onClick={() => setModalState({ open: true, habit: null })}
            className="btn-royal flex items-center gap-1.5 rounded-[var(--r-field)] px-3 py-2 text-[11px] font-semibold"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>عادت جدید</span>
          </button>
        }
      />

      <ComparisonChart title="وضعیت عادت‌ها در روز انتخاب‌شده" subtitle="فقط عادت‌های برنامه‌ریزی‌شده برای این روز شمرده می‌شوند." unit="عادت" data={(() => {
        const scheduled = activeHabits.filter((habit) => isHabitScheduledOnDate(habit, selectedDateKey));
        const count = (status: HabitLog['status'] | undefined) => scheduled.filter((habit) => logLookup.get(`${habit.id}::${selectedDateKey}`)?.status === status).length;
        return [
          { label: 'انجام‌شده', value: count('completed') }, { label: 'ناقص', value: count('partial') },
          { label: 'انجام‌نشده', value: count('missed') }, { label: 'ثبت‌نشده', value: count(undefined) },
        ];
      })()} />

      {/* EXECUTIVE DAILY COMMAND BAR (Date Stepper + Live Daily Mastery) */}
      <LuxuryCard variant="hero" className="space-y-3.5 p-4">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => shiftSelectedDate(-1)}
              aria-label="روز قبل"
              className="flex h-8 w-8 items-center justify-center rounded-xl border border-[var(--accent-fg)]/14 bg-[var(--bg)]/70 text-[var(--text-2)] transition hover:border-[var(--accent-fg)]/35 hover:text-[var(--text)]"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
            <div className="px-1">
              <p className="text-xs font-bold text-[var(--text)]">
                {isViewingToday ? 'امروز • ' : ''}
                {selectedDateInfo.weekday}،{' '}
                <Numeric value={selectedDateInfo.day} className="text-[var(--accent-fg)]" />{' '}
                {selectedDateInfo.month}
              </p>
              <p className="text-[10px] text-[var(--text-2)]">
                <Numeric
                  value={daySummary.completedCount}
                  className="font-semibold text-[var(--text)]"
                />{' '}
                از <Numeric value={daySummary.scheduledCount} /> عادت انجام شده
                {daySummary.missedCount > 0 && (
                  <>
                    {' '}
                    • <Numeric value={daySummary.missedCount} className="text-[var(--danger-fg)]" /> انجام‌نشده
                  </>
                )}
              </p>
            </div>
            <button
              type="button"
              disabled={isViewingToday}
              onClick={() => shiftSelectedDate(1)}
              aria-label="روز بعد"
              className="flex h-8 w-8 items-center justify-center rounded-xl border border-[var(--accent-fg)]/14 bg-[var(--bg)]/70 text-[var(--text-2)] transition hover:border-[var(--accent-fg)]/35 hover:text-[var(--text)] disabled:opacity-35"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
          </div>

          <div className="flex items-center gap-2">
            {!isViewingToday && (
              <button
                type="button"
                onClick={() => {
                  setSelectedDateKey(todayKey);
                  setSelectedYear(currentJYear);
                }}
                className="rounded-lg border border-[var(--accent-fg)]/28 bg-[var(--surface-3)] px-2.5 py-1 text-[10.5px] font-semibold text-[var(--accent-fg)]"
              >
                امروز
              </button>
            )}
            <Numeric
              value={daySummary.percent}
              suffix="%"
              variant="display"
              className="text-3xl font-extrabold text-[var(--accent-fg)]"
            />
          </div>
        </div>

        {/* Daily Progress Bar */}
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-[var(--bg)]/80">
          <div
            className="h-full rounded-full bg-[var(--accent)] transition-all duration-300"
            style={{ width: `${daySummary.percent}%` }}
          />
        </div>

        {/* 3 Compact Pro KPI Chips */}
        <div className="grid grid-cols-3 gap-2 pt-0.5">
          <div className="rounded-xl border border-[var(--accent-fg)]/10 bg-[var(--bg)]/55 px-2.5 py-2 text-center">
            <Numeric
              value={daySummary.pendingCount}
              variant="display"
              className="text-xl font-bold text-[var(--text)]"
            />
            <span className="mt-0.5 block text-[9.5px] text-[var(--text-2)]">مانده این روز</span>
          </div>
          <div className="rounded-xl border border-[var(--accent)]/24 bg-[var(--surface-3)]/55 px-2.5 py-2 text-center">
            <Numeric
              value={daySummary.topStreak}
              suffix="d"
              variant="display"
              className="text-xl font-bold text-[var(--accent-fg)]"
            />
            <span className="mt-0.5 block text-[9.5px] text-[var(--accent-fg)]">بالاترین رکورد</span>
          </div>
          <div className="rounded-xl border border-[var(--accent-fg)]/10 bg-[var(--bg)]/55 px-2.5 py-2 text-center">
            <Numeric
              value={daySummary.avgAdherence}
              suffix="%"
              variant="display"
              className="text-xl font-bold text-[var(--text)]"
            />
            <span className="mt-0.5 block text-[9.5px] text-[var(--text-2)]">میانگین پایبندی</span>
          </div>
        </div>
      </LuxuryCard>

      {/* PRO HABIT MANAGEMENT TOOLBAR (Layout Mode + Filters + Sort) */}
      <div className="space-y-2.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          {/* Layout Switcher: Vertical Pro / Compact / 2-Col Grid */}
          <div className="inline-flex items-center gap-0.5 rounded-xl border border-[var(--accent-fg)]/12 bg-[var(--surface-2)] p-1">
            <button
              type="button"
              onClick={() => handleLayoutChange('vertical')}
              aria-label="نمای عمودی حرفه‌ای"
              className={`flex items-center gap-1 rounded-lg px-2.5 py-1 text-[11px] font-medium transition ${
                layoutMode === 'vertical'
                  ? 'bg-[var(--surface-3)] text-[var(--text)]'
                  : 'text-[var(--text-2)] hover:text-[var(--text)]'
              }`}
            >
              <LayoutList className="h-3.5 w-3.5" />
              <span>عمودی</span>
            </button>
            <button
              type="button"
              onClick={() => handleLayoutChange('compact')}
              aria-label="نمای فشرده"
              className={`flex items-center gap-1 rounded-lg px-2.5 py-1 text-[11px] font-medium transition ${
                layoutMode === 'compact'
                  ? 'bg-[var(--surface-3)] text-[var(--text)]'
                  : 'text-[var(--text-2)] hover:text-[var(--text)]'
              }`}
            >
              <ListChecks className="h-3.5 w-3.5" />
              <span>فشرده</span>
            </button>
            <button
              type="button"
              onClick={() => handleLayoutChange('grid')}
              aria-label="نمای کارتی"
              className={`flex items-center gap-1 rounded-lg px-2.5 py-1 text-[11px] font-medium transition ${
                layoutMode === 'grid'
                  ? 'bg-[var(--surface-3)] text-[var(--text)]'
                  : 'text-[var(--text-2)] hover:text-[var(--text)]'
              }`}
            >
              <LayoutGrid className="h-3.5 w-3.5" />
              <span>کارتی</span>
            </button>
          </div>

          {/* Smart Sort Control */}
          <div className="flex items-center gap-1.5 rounded-xl border border-[var(--accent-fg)]/12 bg-[var(--surface-2)] px-2.5 py-1.5">
            <ArrowUpDown className="h-3.5 w-3.5 text-[var(--accent)]" />
            <select
              aria-label="مرتب‌سازی عادت‌ها"
              value={sortMode}
              onChange={(e) => setSortMode(e.target.value as HabitSortMode)}
              className="bg-transparent text-[11px] font-medium text-[var(--text)] outline-none"
            >
              <option value="default" className="bg-[var(--surface-2)]">
                ترتیب پیش‌فرض
              </option>
              <option value="streak" className="bg-[var(--surface-2)]">
                بیشترین رکورد
              </option>
              <option value="adherence_desc" className="bg-[var(--surface-2)]">
                بالاترین پایبندی
              </option>
              <option value="needs_focus" className="bg-[var(--surface-2)]">
                نیازمند توجه
              </option>
            </select>
          </div>
        </div>

        {/* Filter Pills Strip */}
        <div className="no-scrollbar flex items-center gap-1.5 overflow-x-auto pb-0.5">
          {(
            [
              { id: 'all', label: 'همه فعال', count: activeHabits.length },
              { id: 'scheduled', label: 'برنامه این روز', count: daySummary.scheduledCount },
              { id: 'pending', label: 'مانده', count: daySummary.pendingCount },
              { id: 'completed', label: 'تکمیل‌شده', count: daySummary.completedCount },
              { id: 'archived', label: 'آرشیو', count: archivedHabits.length },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setFilterMode(tab.id)}
              className={`flex shrink-0 items-center gap-1.5 rounded-xl border px-3 py-1.5 text-[11px] font-medium transition ${
                filterMode === tab.id
                  ? 'border-[var(--accent)] bg-[var(--surface-3)] text-[var(--text)]'
                  : 'border-[var(--accent-fg)]/10 bg-[var(--surface-2)] text-[var(--text-2)] hover:text-[var(--text)]'
              }`}
            >
              <span>{tab.label}</span>
              <Numeric
                value={tab.count}
                className={`text-[10px] ${
                  filterMode === tab.id ? 'text-[var(--accent-fg)]' : 'text-[var(--text-3)]'
                }`}
              />
            </button>
          ))}
        </div>
      </div>

      {/* ===================== HABITS LIST / MATRIX ===================== */}
      <section className="space-y-2.5">
        {visibleHabits.length === 0 ? (
          <EmptyState
            icon={<Flame className="h-5 w-5" />}
            title={
              filterMode === 'archived'
                ? 'آرشیو عادت‌ها خالی است'
                : activeHabits.length === 0
                ? 'هنوز عادتی تعریف نکرده‌اید'
                : 'عادتی در این فیلتر یافت نشد'
            }
            description={
              filterMode === 'archived'
                ? 'عادت‌هایی که آرشیو می‌کنید برای نگهداری سابقه در اینجا نمایش داده می‌شوند.'
                : activeHabits.length === 0
                ? 'اولین عادت روزانه یا هفتگی خود را ایجاد کنید تا پایبندی و رکورد متوالی شما محاسبه شود.'
                : 'فیلتر دیگری را انتخاب کنید یا عادت جدیدی بسازید.'
            }
            action={
              filterMode === 'all' && activeHabits.length === 0 ? (
                <button
                  type="button"
                  onClick={() => setModalState({ open: true, habit: null })}
                  className="btn-royal rounded-xl px-4 py-2 text-xs font-semibold"
                >
                  ساخت اولین عادت
                </button>
              ) : undefined
            }
          />
        ) : filterMode === 'archived' ? (
          <div className="space-y-2">
            {visibleHabits.map((habit) => (
              <LuxuryCard key={habit.id} variant="subtle" className="flex items-center justify-between gap-3 p-3.5">
                <div className="flex min-w-0 items-center gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-[var(--accent-fg)]/12 bg-[var(--surface-input)]">
                    <HabitIcon name={habit.icon} color={habit.accentColor} className="h-4.5 w-4.5 opacity-75" />
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-xs font-semibold text-[var(--text)]">{habit.name}</p>
                    <p className="text-[10.5px] text-[var(--text-3)]">{getScheduleBadge(habit)}</p>
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => void handleToggleArchive(habit)}
                    className="btn-ghost-luxury flex items-center gap-1 rounded-xl px-2.5 py-1.5 text-[11px] font-medium text-[var(--accent-fg)]"
                  >
                    <ArchiveRestore className="h-3.5 w-3.5" />
                    بازگردانی
                  </button>
                  <button
                    type="button"
                    onClick={() => void handlePermanentDelete(habit)}
                    aria-label="حذف کامل عادت"
                    className="flex h-8 w-8 items-center justify-center rounded-xl border border-[var(--danger)]/25 bg-[var(--danger)]/10 text-[var(--danger-fg)]"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </LuxuryCard>
            ))}
          </div>
        ) : layoutMode === 'compact' ? (
          /* ---------- COMPACT VERTICAL FAST-CHECK LIST ---------- */
          <div className="space-y-2">
            {visibleHabits.map((habit) => {
              const stats = habitStatsMap.get(habit.id);
              const dayLog = logLookup.get(`${habit.id}::${selectedDateKey}`);
              const isDone = dayLog?.status === 'completed';
              const isMissed = dayLog?.status === 'missed';
              const currentVal = dayLog?.value ?? 0;

              return (
                <LuxuryCard
                  key={habit.id}
                  className="flex items-center justify-between gap-2.5 px-3 py-2.5"
                >
                  <button
                    type="button"
                    onClick={() =>
                      void handleSetDayHabit(
                        habit,
                        selectedDateKey,
                        isDone ? 'unlogged' : 'completed'
                      )
                    }
                    aria-label={isDone ? 'لغو تکمیل عادت' : 'تکمیل عادت'}
                    className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border transition ${
                      isDone
                        ? 'border-[var(--accent-fg)]/50 bg-[var(--accent)] text-[var(--accent-on)] shadow-[0_0_12px_color-mix(in_srgb,var(--accent)_45%,transparent)]'
                        : isMissed
                        ? 'border-[var(--danger)]/45 bg-[var(--surface-3)] text-[var(--danger-fg)]'
                        : 'border-[var(--accent-fg)]/16 bg-[var(--surface-input)] text-[var(--text-3)]'
                    }`}
                  >
                    {isDone ? (
                      <Check className="h-4.5 w-4.5" />
                    ) : isMissed ? (
                      <X className="h-4 w-4" />
                    ) : (
                      <HabitIcon name={habit.icon} color={habit.accentColor} className="h-4 w-4" />
                    )}
                  </button>

                  <button
                    type="button"
                    onClick={() => setDetailHabitId(habit.id)}
                    className="min-w-0 flex-1 text-right"
                  >
                    <p
                      className={`truncate text-[13px] font-semibold ${
                        isDone ? 'text-[var(--accent-fg)]' : 'text-[var(--text)]'
                      }`}
                    >
                      {habit.name}
                    </p>
                    <div className="mt-0.5 flex items-center gap-2 text-[10.5px] text-[var(--text-2)]">
                      <span className="inline-flex items-center gap-0.5">
                        <Flame className="h-3 w-3 text-[var(--accent)]" />
                        <Numeric value={stats?.currentStreak ?? 0} />d
                      </span>
                      <Numeric
                        value={stats?.adherencePercent ?? 0}
                        suffix="%"
                        className="text-[var(--accent-fg)]"
                      />
                      {habit.trackingMode === 'quantitative' && (
                        <Numeric
                          value={`${currentVal}/${habit.targetValue}`}
                          className="text-[var(--text)]"
                        />
                      )}
                    </div>
                  </button>

                  <div className="flex shrink-0 items-center gap-1">
                    {habit.trackingMode === 'quantitative' && !isDone && (
                      <button
                        type="button"
                        onClick={() => void handleSetDayHabit(habit, selectedDateKey, 'step_up')}
                        aria-label="افزایش پیشرفت"
                        className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--accent-fg)]/22 bg-[var(--surface-3)] text-[var(--accent-fg)]"
                      >
                        <Plus className="h-3.5 w-3.5" />
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() =>
                        void handleSetDayHabit(
                          habit,
                          selectedDateKey,
                          isMissed ? 'unlogged' : 'missed'
                        )
                      }
                      title="ثبت انجام‌نشده"
                      className={`flex h-7 w-7 items-center justify-center rounded-lg border transition ${
                        isMissed
                          ? 'border-[var(--danger)]/50 bg-[var(--danger)]/20 text-[var(--danger-fg)]'
                          : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-3)]'
                      }`}
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => setModalState({ open: true, habit })}
                      aria-label="ویرایش سریع"
                      className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-2)]"
                    >
                      <Edit3 className="h-3 w-3" />
                    </button>
                  </div>
                </LuxuryCard>
              );
            })}
          </div>
        ) : layoutMode === 'grid' ? (
          /* ---------- 2-COLUMN TALL VERTICAL BENTO CARDS ---------- */
          <div className="grid grid-cols-2 gap-2.5">
            {visibleHabits.map((habit) => {
              const stats = habitStatsMap.get(habit.id);
              const dayLog = logLookup.get(`${habit.id}::${selectedDateKey}`);
              const isDone = dayLog?.status === 'completed';
              const isMissed = dayLog?.status === 'missed';
              const currentVal = dayLog?.value ?? 0;
              const quantPct =
                habit.trackingMode === 'quantitative'
                  ? Math.min(100, Math.round((currentVal / Math.max(1, habit.targetValue)) * 100))
                  : isDone
                  ? 100
                  : 0;

              return (
                <LuxuryCard
                  key={habit.id}
                  className={`flex flex-col justify-between p-3.5 transition ${
                    isDone ? 'border-[var(--accent)]/45' : ''
                  }`}
                >
                  <div>
                    <div className="flex items-start justify-between gap-2">
                      <div
                        className="flex h-10 w-10 items-center justify-center rounded-2xl border"
                        style={{
                          backgroundColor: isDone
                            ? 'color-mix(in srgb, var(--accent) 28%, transparent)'
                            : 'rgba(30, 22, 60, 0.55)',
                          borderColor: isDone
                            ? 'color-mix(in srgb, var(--accent-fg) 48%, transparent)'
                            : 'color-mix(in srgb, var(--accent-fg) 14%, transparent)',
                        }}
                      >
                        <HabitIcon name={habit.icon} color={habit.accentColor} className="h-4.5 w-4.5" />
                      </div>
                      <button
                        type="button"
                        onClick={() => setDetailHabitId(habit.id)}
                        aria-label="تحلیل عادت"
                        className="rounded-lg p-1 text-[var(--text-2)] hover:bg-[var(--surface-3)] hover:text-[var(--text)]"
                      >
                        <BarChart3 className="h-3.5 w-3.5" />
                      </button>
                    </div>

                    <button
                      type="button"
                      onClick={() => setDetailHabitId(habit.id)}
                      className="mt-2.5 block w-full text-right"
                    >
                      <h3 className="line-clamp-2 min-h-[34px] text-xs font-bold leading-snug text-[var(--text)]">
                        {habit.name}
                      </h3>
                    </button>

                    <div className="mt-2 flex items-center justify-between text-[10.5px] text-[var(--text-2)]">
                      <span className="inline-flex items-center gap-0.5">
                        <Flame className="h-3 w-3 text-[var(--accent)]" />
                        <Numeric value={stats?.currentStreak ?? 0} className="text-[var(--text)]" />d
                      </span>
                      <Numeric
                        value={stats?.adherencePercent ?? 0}
                        suffix="%"
                        className="font-semibold text-[var(--accent-fg)]"
                      />
                    </div>

                    {/* Mini progress bar */}
                    <div className="mt-2 h-1 w-full overflow-hidden rounded-full bg-[var(--bg)]">
                      <div
                        className="h-full rounded-full bg-[var(--accent)] transition-all"
                        style={{ width: `${quantPct}%` }}
                      />
                    </div>
                  </div>

                  <div className="mt-3 flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() =>
                        void handleSetDayHabit(
                          habit,
                          selectedDateKey,
                          isDone ? 'unlogged' : 'completed'
                        )
                      }
                      className={`flex flex-1 items-center justify-center gap-1 rounded-xl py-1.5 text-[11px] font-semibold transition ${
                        isDone
                          ? 'bg-[var(--accent)] text-[var(--accent-on)]'
                          : 'border border-[var(--accent-fg)]/18 bg-[var(--surface-input)] text-[var(--text)]'
                      }`}
                    >
                      <Check className="h-3.5 w-3.5" />
                      <span>{isDone ? 'انجام شد' : 'تکمیل'}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        void handleSetDayHabit(
                          habit,
                          selectedDateKey,
                          isMissed ? 'unlogged' : 'missed'
                        )
                      }
                      aria-label="انجام نشد"
                      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-xl border ${
                        isMissed
                          ? 'border-[var(--danger)]/50 bg-[var(--danger)]/20 text-[var(--danger-fg)]'
                          : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-3)]'
                      }`}
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </LuxuryCard>
              );
            })}
          </div>
        ) : (
          /* ---------- VERTICAL PRO MANAGEMENT CARDS (Interactive 7-Day Strip + Steppers) ---------- */
          <div className="space-y-3">
            {visibleHabits.map((habit) => {
              const stats = habitStatsMap.get(habit.id);
              const dayLog = logLookup.get(`${habit.id}::${selectedDateKey}`);
              const isCompleted = dayLog?.status === 'completed';
              const isMissed = dayLog?.status === 'missed';
              const currentVal = dayLog?.value ?? 0;
              const quantPercent =
                habit.trackingMode === 'quantitative'
                  ? Math.min(100, Math.round((currentVal / Math.max(1, habit.targetValue)) * 100))
                  : isCompleted
                  ? 100
                  : 0;

              return (
                <LuxuryCard
                  key={habit.id}
                  className={`space-y-3.5 p-4 transition ${
                    isCompleted ? 'border-[var(--accent)]/40' : ''
                  }`}
                >
                  {/* Card Top Header */}
                  <div className="flex items-start justify-between gap-2.5">
                    <button
                      type="button"
                      onClick={() => setDetailHabitId(habit.id)}
                      className="flex min-w-0 flex-1 items-start gap-3 text-right focus:outline-none"
                    >
                      <div
                        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border"
                        style={{
                          backgroundColor: isCompleted
                            ? 'color-mix(in srgb, var(--accent) 25%, transparent)'
                            : 'rgba(30, 22, 60, 0.55)',
                          borderColor: isCompleted
                            ? 'color-mix(in srgb, var(--accent-fg) 48%, transparent)'
                            : 'color-mix(in srgb, var(--accent-fg) 14%, transparent)',
                        }}
                      >
                        <HabitIcon name={habit.icon} color={habit.accentColor} className="h-5 w-5" />
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <h3 className="truncate text-[13.5px] font-bold text-[var(--text)]">
                            {habit.name}
                          </h3>
                        </div>

                        <div className="mt-1 flex flex-wrap items-center gap-2 text-[10.5px] text-[var(--text-2)]">
                          <span className="rounded-md bg-[var(--surface-input)] px-1.5 py-0.5 text-[10px] text-[var(--text-2)]">
                            {getScheduleBadge(habit)}
                          </span>
                          <span className="inline-flex items-center gap-0.5">
                            <Flame className="h-3 w-3 text-[var(--accent)]" />
                            <Numeric
                              value={stats?.currentStreak ?? 0}
                              className="font-semibold text-[var(--text)]"
                            />
                            d
                          </span>
                          <span>
                            پایبندی{' '}
                            <Numeric
                              value={stats?.adherencePercent ?? 0}
                              suffix="%"
                              className="font-semibold text-[var(--accent-fg)]"
                            />
                          </span>
                          {habit.reminderTime && (
                            <span className="inline-flex items-center gap-0.5 text-[var(--text-3)]">
                              <Clock className="h-3 w-3" />
                              <Numeric value={habit.reminderTime} />
                            </span>
                          )}
                        </div>
                      </div>
                    </button>

                    {/* Quick Pro Management Actions */}
                    <div className="flex shrink-0 items-center gap-1">
                      <button
                        type="button"
                        onClick={() => setDetailHabitId(habit.id)}
                        title="آمار و نمودار کامل"
                        aria-label="آمار و نمودار کامل"
                        className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--accent-fg)]/12 bg-[var(--surface-input)] text-[var(--accent-fg)] hover:border-[var(--accent-fg)]/35"
                      >
                        <BarChart3 className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => setModalState({ open: true, habit })}
                        title="ویرایش عادت"
                        aria-label="ویرایش عادت"
                        className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--accent-fg)]/12 bg-[var(--surface-input)] text-[var(--text-2)] hover:text-[var(--text)]"
                      >
                        <Edit3 className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleToggleArchive(habit)}
                        title="انتقال به آرشیو"
                        aria-label="انتقال به آرشیو"
                        className="flex h-7 w-7 items-center justify-center rounded-lg border border-[var(--accent-fg)]/12 bg-[var(--surface-input)] text-[var(--text-3)] hover:text-[var(--text-2)]"
                      >
                        <Archive className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* Interactive Quantitative Progress Stepper */}
                  {habit.trackingMode === 'quantitative' && (
                    <div className="space-y-1.5 rounded-xl border border-[var(--accent-fg)]/10 bg-[var(--bg)] px-3 py-2.5">
                      <div className="flex items-center justify-between text-[11px]">
                        <span className="text-[var(--text-2)]">
                          پیشرفت {isViewingToday ? 'امروز' : `${selectedDateInfo.day} ${selectedDateInfo.month}`}:
                        </span>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() =>
                              void handleSetDayHabit(habit, selectedDateKey, 'step_down')
                            }
                            aria-label="کاهش مقدار"
                            className="flex h-6 w-6 items-center justify-center rounded-md border border-[var(--accent-fg)]/16 bg-[var(--surface-2)] text-[var(--text-2)] hover:text-[var(--text)]"
                          >
                            <Minus className="h-3 w-3" />
                          </button>
                          <span className="font-semibold text-[var(--text)]">
                            <Numeric value={`${currentVal}/${habit.targetValue}`} />{' '}
                            <span className="text-[10px] font-normal text-[var(--text-2)]">
                              {habit.unit}
                            </span>
                          </span>
                          <button
                            type="button"
                            onClick={() =>
                              void handleSetDayHabit(habit, selectedDateKey, 'step_up')
                            }
                            aria-label="افزایش مقدار"
                            className="flex h-6 w-6 items-center justify-center rounded-md border border-[var(--accent)]/45 bg-[var(--surface-3)] text-[var(--accent-fg)] hover:bg-[var(--accent)] hover:text-[var(--accent-on)]"
                          >
                            <Plus className="h-3 w-3" />
                          </button>
                        </div>
                      </div>
                      <div className="h-1.5 w-full overflow-hidden rounded-full bg-[var(--surface-2)]">
                        <div
                          className="h-full rounded-full bg-[var(--accent)] transition-all duration-200"
                          style={{ width: `${quantPercent}%` }}
                        />
                      </div>
                    </div>
                  )}

                  {/* Interactive 7-Day Quick Matrix Strip (Tap any day to cycle state directly!) */}
                  <div className="grid grid-cols-7 gap-1.5">
                    {last7Days.map((d) => {
                      const dLog = logLookup.get(`${habit.id}::${d.dateKey}`);
                      const isSel = d.dateKey === selectedDateKey;
                      const st = dLog?.status;

                      return (
                        <button
                          key={d.dateKey}
                          type="button"
                          onClick={() => void handleCycleStripDay(habit, d.dateKey)}
                          title={`${d.dayNum} — کلیک برای تغییر وضعیت`}
                          className={`flex flex-col items-center justify-center rounded-xl border py-1.5 transition active:scale-95 ${
                            st === 'completed'
                              ? 'border-[var(--accent-fg)]/45 bg-[var(--accent)]/90 text-[var(--accent-on)]'
                              : st === 'partial'
                              ? 'border-[var(--accent)]/45 bg-[var(--surface-3)] text-[var(--accent-fg)]'
                              : st === 'missed'
                              ? 'border-[var(--danger)]/40 bg-[var(--surface-3)] text-[var(--danger-fg)]'
                              : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-3)]'
                          } ${isSel ? 'ring-1.5 ring-[var(--text)]/80' : ''}`}
                        >
                          <span className="text-[9px] leading-none opacity-80">
                            {d.weekdayShort}
                          </span>
                          <span className="num-geo mt-1 text-[11px] font-bold leading-none">
                            {d.dayNum}
                          </span>
                        </button>
                      );
                    })}
                  </div>

                  {/* Action Footer for Selected Date */}
                  <div className="flex items-center justify-between gap-2 border-t border-[var(--accent-fg)]/10 pt-2.5">
                    <button
                      type="button"
                      onClick={() =>
                        void handleSetDayHabit(
                          habit,
                          selectedDateKey,
                          isCompleted ? 'unlogged' : 'completed'
                        )
                      }
                      className={`flex items-center gap-1.5 rounded-xl px-3.5 py-1.5 text-[11px] font-semibold transition ${
                        isCompleted
                          ? 'bg-[var(--accent)] text-[var(--accent-on)] shadow-[0_0_14px_color-mix(in_srgb,var(--accent)_42%,transparent)]'
                          : 'border border-[var(--accent-fg)]/18 bg-[var(--surface-input)] text-[var(--text)] hover:border-[var(--accent-fg)]/45'
                      }`}
                    >
                      <Check className="h-3.5 w-3.5" />
                      <span>
                        {isCompleted
                          ? 'انجام شد'
                          : isViewingToday
                          ? 'تکمیل امروز'
                          : 'تکمیل این روز'}
                      </span>
                    </button>

                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() =>
                          void handleSetDayHabit(
                            habit,
                            selectedDateKey,
                            isMissed ? 'unlogged' : 'missed'
                          )
                        }
                        className={`flex items-center gap-1 rounded-xl border px-2.5 py-1.5 text-[10.5px] font-medium transition ${
                          isMissed
                            ? 'border-[var(--danger)]/50 bg-[var(--danger)]/20 text-[var(--danger-fg)]'
                            : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-3)] hover:text-[var(--danger-fg)]'
                        }`}
                      >
                        <X className="h-3 w-3" />
                        <span>انجام نشد</span>
                      </button>

                      {dayLog && (
                        <button
                          type="button"
                          onClick={() =>
                            void handleSetDayHabit(habit, selectedDateKey, 'unlogged')
                          }
                          title="برگرداندن به ثبت‌نشده"
                          className="flex h-7 w-7 items-center justify-center rounded-xl border border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-3)] hover:text-[var(--text-2)]"
                        >
                          <Minus className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                  </div>
                </LuxuryCard>
              );
            })}
          </div>
        )}
      </section>

      {/* ===================== 365-DAY VERTICAL / HORIZONTAL HEATMAP CARD ===================== */}
      <LuxuryCard className="space-y-3.5 p-4">
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => setHeatmapExpanded((v) => !v)}
            className="flex items-center gap-2 text-right focus:outline-none"
          >
            <div className="flex h-8 w-8 items-center justify-center rounded-xl border border-[var(--accent-fg)]/16 bg-[var(--surface-3)]/75 text-[var(--accent-fg)]">
              <CalendarDays className="h-4 w-4" />
            </div>
            <div>
              <h2 className="text-xs font-bold text-[var(--text)]">تقویم حرارتی سالانه</h2>
              <p className="text-[10px] text-[var(--text-2)]">
                <Numeric value={heatmap.activeDays} className="font-semibold text-[var(--accent-fg)]" /> روز
                فعال •{' '}
                <Numeric value={heatmap.perfectDays} className="font-semibold text-[var(--text)]" /> روز
                کامل
              </p>
            </div>
          </button>

          <div className="flex items-center gap-1.5">
            {heatmapExpanded && (
              <div className="flex items-center gap-0.5 rounded-xl border border-[var(--accent-fg)]/14 bg-[var(--bg)]/70 p-0.5">
                {[currentJYear - 1, currentJYear].map((yr) => (
                  <button
                    key={yr}
                    type="button"
                    onClick={() => {
                      setSelectedYear(yr);
                      if (yr === currentJYear) setSelectedDateKey(todayKey);
                    }}
                    className={`num-geo rounded-lg px-2 py-1 text-[10.5px] font-semibold transition ${
                      selectedYear === yr
                        ? 'bg-[var(--accent)] text-[var(--accent-on)]'
                        : 'text-[var(--text-2)] hover:text-[var(--text)]'
                    }`}
                  >
                    {yr}
                  </button>
                ))}
              </div>
            )}
            <button
              type="button"
              onClick={() => setHeatmapExpanded((v) => !v)}
              aria-label={heatmapExpanded ? 'بستن تقویم حرارتی' : 'باز کردن تقویم حرارتی'}
              className="flex h-8 w-8 items-center justify-center rounded-xl border border-[var(--accent-fg)]/12 bg-[var(--surface-input)] text-[var(--text-2)]"
            >
              {heatmapExpanded ? (
                <ChevronUp className="h-4 w-4" />
              ) : (
                <ChevronDown className="h-4 w-4" />
              )}
            </button>
          </div>
        </div>

        {heatmapExpanded && (
          <>
            <ActivityHeatmap
              cells={heatmap.cells}
              selectedDateKey={selectedDateKey}
              todayKey={todayKey}
              orientation={heatmapOrientation}
              onOrientationChange={handleHeatmapOrient}
              onSelectDay={(cell: HeatmapDayCell) => setSelectedDateKey(cell.dateKey)}
            />

            {/* Selected Day Inspector */}
            {selectedCell && (
              <div className="flex items-center justify-between rounded-2xl border border-[var(--accent-fg)]/12 bg-[var(--bg)]/90 px-3.5 py-2.5">
                <div>
                  <p className="text-xs font-bold text-[var(--text)]">
                    {selectedDateInfo.weekday}،{' '}
                    <Numeric value={selectedDateInfo.day} className="text-[var(--accent-fg)]" />{' '}
                    {selectedDateInfo.month}{' '}
                    <Numeric value={selectedDateInfo.year} className="text-[var(--text-2)]" />
                  </p>
                  <p className="mt-0.5 text-[10px] text-[var(--text-2)]">
                    {selectedCell.state === 'future'
                      ? 'تاریخ آینده'
                      : selectedCell.state === 'unlogged'
                      ? 'هیچ فعالیتی برای این روز ثبت نشده است'
                      : `موفق: ${selectedCell.habitsCompleted} • انجام‌نشده: ${selectedCell.habitsMissed} • ثبت‌نشده: ${selectedCell.habitsUnlogged}`}
                  </p>
                </div>
                {selectedCell.state !== 'future' && (
                  <Numeric
                    value={selectedCell.score}
                    suffix="%"
                    variant="display"
                    className="text-2xl font-bold text-[var(--accent-fg)]"
                  />
                )}
              </div>
            )}
          </>
        )}
      </LuxuryCard>

      <HabitModal
        open={modalState.open}
        habit={modalState.habit}
        onClose={() => setModalState({ open: false, habit: null })}
      />
    </div>
  );
}
