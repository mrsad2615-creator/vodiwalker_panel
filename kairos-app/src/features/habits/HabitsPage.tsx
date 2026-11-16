import { useMemo, useState } from 'react';
import {
  Check,
  ChevronLeft,
  Flame,
  Minus,
  Plus,
  X,
} from 'lucide-react';
import type { Habit } from '@/types/domain';
import { clearHabitLog, upsertHabitLog } from '@/lib/db';
import { useFocusSessions, useHabitLogs, useHabits, useTasks } from '@/lib/queries';
import {
  computeAnnualHeatmap,
  computeHabitStats,
  isHabitScheduledOnDate,
  type HeatmapDayCell,
} from '@/lib/analytics';
import { dateKeyToJalaali, formatJalaaliFull, getTodayKey } from '@/lib/date';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { HabitIcon } from '@/components/ui/KairosMark';
import { ActivityHeatmap } from '@/components/charts/ActivityHeatmap';
import { EmptyState } from '@/components/ui/EmptyState';
import { HabitModal } from './HabitModal';
import { HabitDetailView } from './HabitDetailView';

export function HabitsPage() {
  const { settings } = useApp();
  const todayKey = getTodayKey();
  const currentJYear = dateKeyToJalaali(todayKey).jy;

  const [selectedYear, setSelectedYear] = useState(currentJYear);
  const [selectedDateKey, setSelectedDateKey] = useState(todayKey);
  const [detailHabitId, setDetailHabitId] = useState<string | null>(null);
  const [modalState, setModalState] = useState<{ open: boolean; habit?: Habit | null }>({
    open: false,
  });

  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const tasks = useTasks();
  const focusSessions = useFocusSessions();

  const activeHabits = useMemo(() => habits.filter((h) => !h.archived), [habits]);
  const detailHabit = useMemo(
    () => activeHabits.find((h) => h.id === detailHabitId) ?? null,
    [activeHabits, detailHabitId]
  );

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

  const selectedDayLogs = useMemo(() => {
    const map = new Map(
      habitLogs.filter((l) => l.dateKey === selectedDateKey).map((l) => [l.habitId, l])
    );
    return map;
  }, [habitLogs, selectedDateKey]);

  const habitStatsMap = useMemo(() => {
    const map = new Map<string, ReturnType<typeof computeHabitStats>>();
    activeHabits.forEach((h) => {
      map.set(h.id, computeHabitStats(h, habitLogs, todayKey));
    });
    return map;
  }, [activeHabits, habitLogs, todayKey]);

  const handleSetDayHabit = async (
    habit: Habit,
    dateKey: string,
    action: 'completed' | 'step' | 'missed' | 'unlogged'
  ) => {
    const current = habitLogs.find((l) => l.habitId === habit.id && l.dateKey === dateKey);
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
    if (action === 'step' && habit.trackingMode === 'quantitative') {
      const step = habit.targetValue >= 20 ? Math.max(5, Math.round(habit.targetValue / 4)) : 1;
      const nextVal = Math.min(habit.targetValue, (current?.value ?? 0) + step);
      const done = nextVal >= habit.targetValue;
      await upsertHabitLog(habit.id, dateKey, done ? 'completed' : 'partial', nextVal);
      void triggerHaptic(done ? 'success' : 'light', settings.hapticsEnabled);
      return;
    }

    await upsertHabitLog(habit.id, dateKey, 'completed', habit.targetValue);
    void triggerHaptic('success', settings.hapticsEnabled);
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

  return (
    <div className="space-y-5">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-[#F4F1F8]">مدیریت عادت‌ها</h1>
          <p className="mt-0.5 text-[11px] text-[#A79EB9]">
            تقویم حرارتی 365 روزه با تفکیک دقیق روزهای موفق، انجام‌نشده و ثبت‌نشده
          </p>
        </div>

        <button
          type="button"
          onClick={() => setModalState({ open: true, habit: null })}
          className="btn-royal flex items-center gap-1.5 rounded-2xl px-3.5 py-2.5 text-xs font-semibold"
        >
          <Plus className="h-4 w-4" />
          <span>عادت جدید</span>
        </button>
      </div>

      {/* 365-DAY LUXURY PURPLE HEATMAP CARD */}
      <LuxuryCard variant="hero" className="space-y-4 p-4">
        <div className="flex items-center justify-between gap-2">
          <div>
            <h2 className="text-xs font-bold text-[#F4F1F8]">تقویم حرارتی سالانه</h2>
            <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">
              <Numeric value={heatmap.activeDays} className="font-semibold text-[#B69AEF]" /> روز
              فعال •{' '}
              <Numeric value={heatmap.perfectDays} className="font-semibold text-[#F4F1F8]" /> روز
              کامل
            </p>
          </div>

          {/* Year Selector (Current & Past Jalali Years) */}
          <div className="flex items-center gap-1 rounded-xl border border-[#B69AEF]/14 bg-[#09090D]/70 p-1">
            {[currentJYear - 2, currentJYear - 1, currentJYear].map((yr) => (
              <button
                key={yr}
                type="button"
                onClick={() => {
                  setSelectedYear(yr);
                  if (yr === currentJYear) setSelectedDateKey(todayKey);
                }}
                className={`num-geo rounded-lg px-2 py-1 text-[11px] font-semibold transition ${
                  selectedYear === yr
                    ? 'bg-[#8052D6] text-[#F4F1F8]'
                    : 'text-[#A79EB9] hover:text-[#F4F1F8]'
                }`}
              >
                {yr}
              </button>
            ))}
          </div>
        </div>

        <ActivityHeatmap
          cells={heatmap.cells}
          selectedDateKey={selectedDateKey}
          todayKey={todayKey}
          onSelectDay={(cell: HeatmapDayCell) => setSelectedDateKey(cell.dateKey)}
        />

        {/* Selected Day Inspector */}
        <div className="rounded-2xl border border-[#B69AEF]/14 bg-[#0B0A11]/90 p-3.5">
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="text-xs font-bold text-[#F4F1F8]">
                {selectedDateInfo.weekday}،{' '}
                <Numeric value={selectedDateInfo.day} className="text-[#B69AEF]" />{' '}
                {selectedDateInfo.month}{' '}
                <Numeric value={selectedDateInfo.year} className="text-[#A79EB9]" />
              </p>
              <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">
                {selectedCell
                  ? selectedCell.state === 'future'
                    ? 'تاریخ آینده'
                    : selectedCell.state === 'unlogged'
                    ? 'هیچ فعالیتی برای این روز ثبت نشده است'
                    : `موفق: ${selectedCell.habitsCompleted} • انجام‌نشده: ${selectedCell.habitsMissed} • ثبت‌نشده: ${selectedCell.habitsUnlogged}`
                  : 'یک روز از تقویم انتخاب کنید'}
              </p>
            </div>
            {selectedCell && selectedCell.state !== 'future' && (
              <Numeric
                value={selectedCell.score}
                suffix="%"
                variant="display"
                className="text-2xl font-bold text-[#B69AEF]"
              />
            )}
          </div>

          {selectedDateKey <= todayKey && activeHabits.length > 0 && (
            <div className="mt-3 space-y-1.5 border-t border-[#B69AEF]/10 pt-2.5">
              {activeHabits
                .filter((h) => isHabitScheduledOnDate(h, selectedDateKey))
                .map((habit) => {
                  const log = selectedDayLogs.get(habit.id);
                  return (
                    <div
                      key={habit.id}
                      className="flex items-center justify-between gap-2 text-xs"
                    >
                      <span className="truncate text-[#F4F1F8]/90">{habit.name}</span>
                      <div className="flex shrink-0 items-center gap-1">
                        <button
                          type="button"
                          onClick={() => handleSetDayHabit(habit, selectedDateKey, 'completed')}
                          aria-label="انجام شد"
                          className={`rounded-md px-2 py-0.5 text-[10px] font-medium transition ${
                            log?.status === 'completed'
                              ? 'bg-[#8052D6] text-[#F4F1F8]'
                              : 'bg-[#15141B] text-[#6E6680] hover:text-[#F4F1F8]'
                          }`}
                        >
                          موفق
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSetDayHabit(habit, selectedDateKey, 'missed')}
                          aria-label="انجام نشد"
                          className={`rounded-md px-2 py-0.5 text-[10px] font-medium transition ${
                            log?.status === 'missed'
                              ? 'bg-[#D9778B]/25 text-[#EB9BAB]'
                              : 'bg-[#15141B] text-[#6E6680] hover:text-[#EB9BAB]'
                          }`}
                        >
                          نشد
                        </button>
                        <button
                          type="button"
                          onClick={() => handleSetDayHabit(habit, selectedDateKey, 'unlogged')}
                          aria-label="ثبت‌نشده"
                          className={`rounded-md px-2 py-0.5 text-[10px] font-medium transition ${
                            !log
                              ? 'bg-[#241936] text-[#B69AEF]'
                              : 'bg-[#15141B] text-[#6E6680]'
                          }`}
                        >
                          ثبت‌نشده
                        </button>
                      </div>
                    </div>
                  );
                })}
            </div>
          )}
        </div>
      </LuxuryCard>

      {/* HABIT CARDS LIST */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between px-0.5">
          <h2 className="text-sm font-bold text-[#F4F1F8]">عادت‌های فعال شما</h2>
          <Numeric
            value={activeHabits.length}
            variant="geo"
            className="rounded-lg bg-[#15141B] px-2 py-0.5 text-xs text-[#B69AEF]"
          />
        </div>

        {activeHabits.length === 0 ? (
          <EmptyState
            icon={<Flame className="h-5 w-5" />}
            title="هنوز عادتی تعریف نکرده‌اید"
            description="اولین عادت روزانه یا هفتگی خود را ایجاد کنید تا پایبندی و رکورد متوالی شما محاسبه شود."
            action={
              <button
                type="button"
                onClick={() => setModalState({ open: true, habit: null })}
                className="btn-royal rounded-xl px-4 py-2 text-xs font-semibold"
              >
                ساخت اولین عادت
              </button>
            }
          />
        ) : (
          <div className="space-y-2.5">
            {activeHabits.map((habit) => {
              const stats = habitStatsMap.get(habit.id);
              const todayLog = habitLogs.find(
                (l) => l.habitId === habit.id && l.dateKey === todayKey
              );
              const isCompletedToday = todayLog?.status === 'completed';
              const isMissedToday = todayLog?.status === 'missed';
              const currentVal = todayLog?.value ?? 0;

              return (
                <LuxuryCard key={habit.id} className="p-4">
                  <div className="flex items-start justify-between gap-3">
                    <button
                      type="button"
                      onClick={() => setDetailHabitId(habit.id)}
                      className="flex min-w-0 flex-1 items-start gap-3 text-right focus:outline-none"
                    >
                      <div
                        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border"
                        style={{
                          backgroundColor: isCompletedToday
                            ? 'rgba(128, 82, 214, 0.24)'
                            : 'rgba(36, 25, 54, 0.55)',
                          borderColor: isCompletedToday
                            ? 'rgba(182, 154, 239, 0.45)'
                            : 'rgba(182, 154, 239, 0.12)',
                        }}
                      >
                        <HabitIcon name={habit.icon} color={habit.accentColor} className="h-5 w-5" />
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <h3 className="truncate text-[13.5px] font-semibold text-[#F4F1F8]">
                            {habit.name}
                          </h3>
                          <ChevronLeft className="h-3.5 w-3.5 shrink-0 text-[#6E6680]" />
                        </div>

                        <div className="mt-1 flex flex-wrap items-center gap-2.5 text-[11px] text-[#A79EB9]">
                          <span className="inline-flex items-center gap-1">
                            <Flame className="h-3 w-3 text-[#8052D6]" />
                            رکورد{' '}
                            <Numeric
                              value={stats?.currentStreak ?? 0}
                              className="font-semibold text-[#F4F1F8]"
                            />
                            d
                          </span>
                          <span>
                            پایبندی{' '}
                            <Numeric
                              value={stats?.adherencePercent ?? 0}
                              suffix="%"
                              className="font-semibold text-[#B69AEF]"
                            />
                          </span>
                          {habit.trackingMode === 'quantitative' && (
                            <span>
                              امروز:{' '}
                              <Numeric
                                value={`${currentVal}/${habit.targetValue}`}
                                className="text-[#F4F1F8]"
                              />{' '}
                              {habit.unit}
                            </span>
                          )}
                        </div>
                      </div>
                    </button>
                  </div>

                  {/* Quick Today Controls: Complete / Step / Missed / Unlogged */}
                  <div className="mt-3.5 flex items-center justify-between gap-2 border-t border-[#B69AEF]/10 pt-3">
                    <div className="flex items-center gap-1.5">
                      {habit.trackingMode === 'quantitative' && !isCompletedToday && (
                        <button
                          type="button"
                          onClick={() => handleSetDayHabit(habit, todayKey, 'step')}
                          className="btn-ghost-luxury rounded-xl px-3 py-1.5 text-[11px] font-semibold text-[#B69AEF]"
                        >
                          + ثبت پیشرفت
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() =>
                          handleSetDayHabit(
                            habit,
                            todayKey,
                            isCompletedToday ? 'unlogged' : 'completed'
                          )
                        }
                        className={`flex items-center gap-1 rounded-xl px-3 py-1.5 text-[11px] font-semibold transition ${
                          isCompletedToday
                            ? 'bg-[#8052D6] text-[#F4F1F8] shadow-[0_0_14px_rgba(128,82,214,0.45)]'
                            : 'border border-[#B69AEF]/18 bg-[#100F16] text-[#F4F1F8] hover:border-[#B69AEF]/45'
                        }`}
                      >
                        <Check className="h-3.5 w-3.5" />
                        <span>{isCompletedToday ? 'انجام شد' : 'تکمیل امروز'}</span>
                      </button>
                    </div>

                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() =>
                          handleSetDayHabit(
                            habit,
                            todayKey,
                            isMissedToday ? 'unlogged' : 'missed'
                          )
                        }
                        title="ثبت انجام‌نشده"
                        className={`flex h-7 w-7 items-center justify-center rounded-lg border transition ${
                          isMissedToday
                            ? 'border-[#D9778B]/50 bg-[#D9778B]/20 text-[#EB9BAB]'
                            : 'border-[#B69AEF]/10 bg-[#100F16] text-[#6E6680] hover:text-[#EB9BAB]'
                        }`}
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                      {todayLog && (
                        <button
                          type="button"
                          onClick={() => handleSetDayHabit(habit, todayKey, 'unlogged')}
                          title="برگرداندن به حالت ثبت‌نشده"
                          className="flex h-7 w-7 items-center justify-center rounded-lg border border-[#B69AEF]/10 bg-[#100F16] text-[#6E6680] hover:text-[#A79EB9]"
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

      <HabitModal
        open={modalState.open}
        habit={modalState.habit}
        onClose={() => setModalState({ open: false, habit: null })}
      />
    </div>
  );
}
