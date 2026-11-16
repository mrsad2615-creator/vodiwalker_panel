import { useMemo, useState } from 'react';
import {
  ArrowRight,
  Check,
  Edit3,
  Flame,
  Minus,
  Trophy,
  X,
} from 'lucide-react';
import type { FocusSession, Habit, HabitLog, Task } from '@/types/domain';
import { computeAnnualHeatmap, computeHabitStats, type HeatmapDayCell } from '@/lib/analytics';
import {
  addDays,
  dateKeyToJalaali,
  formatJalaaliFull,
  formatJalaaliShort,
  getTodayKey,
} from '@/lib/date';
import { clearHabitLog, upsertHabitLog } from '@/lib/db';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { HabitIcon } from '@/components/ui/KairosMark';
import { ActivityHeatmap } from '@/components/charts/ActivityHeatmap';
import { LuxuryBarChart } from '@/components/charts/LuxuryBarChart';
import { LuxuryAreaChart } from '@/components/charts/LuxuryAreaChart';

interface HabitDetailViewProps {
  habit: Habit;
  allLogs: HabitLog[];
  tasks: Task[];
  focusSessions: FocusSession[];
  onBack: () => void;
  onEdit: () => void;
}

export function HabitDetailView({
  habit,
  allLogs,
  tasks,
  focusSessions,
  onBack,
  onEdit,
}: HabitDetailViewProps) {
  const { settings } = useApp();
  const todayKey = getTodayKey();
  const currentJYear = dateKeyToJalaali(todayKey).jy;
  const [selectedYear, setSelectedYear] = useState(currentJYear);
  const [selectedCellKey, setSelectedCellKey] = useState<string>(todayKey);

  const stats = useMemo(
    () => computeHabitStats(habit, allLogs, todayKey),
    [habit, allLogs, todayKey]
  );

  const heatmap = useMemo(
    () =>
      computeAnnualHeatmap({
        jalaaliYear: selectedYear,
        habits: [habit],
        habitLogs: allLogs,
        tasks,
        focusSessions,
        todayKey,
        mode: 'habits',
        habitId: habit.id,
      }),
    [selectedYear, habit, allLogs, tasks, focusSessions, todayKey]
  );

  const logByDate = useMemo(() => {
    const map = new Map<string, HabitLog>();
    allLogs
      .filter((l) => l.habitId === habit.id)
      .forEach((l) => map.set(l.dateKey, l));
    return map;
  }, [allLogs, habit.id]);

  const selectedLog = logByDate.get(selectedCellKey);
  const selectedDateInfo = formatJalaaliFull(selectedCellKey);

  const recentHistoryDays = useMemo(() => {
    return Array.from({ length: 14 }, (_, i) => addDays(todayKey, -i));
  }, [todayKey]);

  const setDayStatus = async (dateKey: string, next: 'completed' | 'missed' | 'unlogged') => {
    if (next === 'unlogged') {
      await clearHabitLog(habit.id, dateKey);
    } else if (next === 'completed') {
      await upsertHabitLog(habit.id, dateKey, 'completed', habit.targetValue);
    } else {
      await upsertHabitLog(habit.id, dateKey, 'missed', 0);
    }
    void triggerHaptic(next === 'completed' ? 'success' : 'light', settings.hapticsEnabled);
  };

  return (
    <div className="space-y-4">
      {/* Top Navigation */}
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={onBack}
          className="btn-ghost-luxury flex items-center gap-1.5 rounded-xl px-3 py-2 text-xs font-medium text-[#F4F1F8]"
        >
          <ArrowRight className="h-4 w-4" />
          <span>بازگشت به عادت‌ها</span>
        </button>

        <button
          type="button"
          onClick={onEdit}
          className="btn-ghost-luxury flex items-center gap-1.5 rounded-xl px-3 py-2 text-xs font-medium text-[#B69AEF]"
        >
          <Edit3 className="h-3.5 w-3.5" />
          <span>ویرایش</span>
        </button>
      </div>

      {/* Hero Header Card */}
      <LuxuryCard variant="hero" className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#B69AEF]/28 bg-[#241936]">
              <HabitIcon name={habit.icon} color={habit.accentColor} className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-base font-bold text-[#F4F1F8]">{habit.name}</h1>
              {habit.description && (
                <p className="mt-0.5 text-xs text-[#A79EB9]">{habit.description}</p>
              )}
            </div>
          </div>

          <div className="text-left">
            <Numeric
              value={stats.adherencePercent}
              suffix="%"
              variant="display"
              className="text-4xl font-extrabold text-[#B69AEF]"
            />
            <span className="block text-[10px] text-[#A79EB9]">نرخ پایبندی</span>
          </div>
        </div>

        {/* 4 Core KPI Tiles — Strictly Separating Completed, Missed, Unlogged, Best Streak */}
        <div className="mt-5 grid grid-cols-4 gap-2">
          <div className="rounded-xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-2.5 text-center">
            <Numeric
              value={stats.completedDays}
              variant="display"
              className="text-2xl font-bold text-[#F4F1F8]"
            />
            <span className="mt-1 block text-[9.5px] text-[#A79EB9]">روز موفق</span>
          </div>
          <div className="rounded-xl border border-[#D9778B]/18 bg-[#09090D]/60 p-2.5 text-center">
            <Numeric
              value={stats.missedDays}
              variant="display"
              className="text-2xl font-bold text-[#EB9BAB]"
            />
            <span className="mt-1 block text-[9.5px] text-[#A79EB9]">ازدست‌رفته</span>
          </div>
          <div className="rounded-xl border border-[#B69AEF]/10 bg-[#09090D]/60 p-2.5 text-center">
            <Numeric
              value={stats.unloggedDays}
              variant="display"
              className="text-2xl font-bold text-[#A79EB9]"
            />
            <span className="mt-1 block text-[9.5px] text-[#6E6680]">ثبت‌نشده</span>
          </div>
          <div className="rounded-xl border border-[#8052D6]/28 bg-[#241936]/60 p-2.5 text-center">
            <Numeric
              value={stats.bestStreak}
              variant="display"
              className="text-2xl font-bold text-[#B69AEF]"
            />
            <span className="mt-1 block text-[9.5px] text-[#B69AEF]">بهترین رکورد</span>
          </div>
        </div>

        <div className="mt-3 flex items-center justify-between text-[11px] text-[#A79EB9]">
          <span className="inline-flex items-center gap-1">
            <Flame className="h-3.5 w-3.5 text-[#8052D6]" />
            رکورد متوالی فعلی:{' '}
            <Numeric value={stats.currentStreak} className="font-semibold text-[#F4F1F8]" /> روز
          </span>
          <span className="inline-flex items-center gap-1">
            <Trophy className="h-3.5 w-3.5 text-[#B69AEF]" />
            ثبت‌شده:{' '}
            <Numeric value={stats.loggedDaysCount} className="font-semibold text-[#F4F1F8]" /> از{' '}
            <Numeric value={stats.scheduledDaysCount} />
          </span>
        </div>
      </LuxuryCard>

      {/* Individual Habit 365-Day Heatmap */}
      <LuxuryCard className="space-y-3 p-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xs font-bold text-[#F4F1F8]">تقویم فعالیت سالانه</h2>
          <div className="flex items-center gap-1">
            {[currentJYear - 1, currentJYear].map((yr) => (
              <button
                key={yr}
                type="button"
                onClick={() => setSelectedYear(yr)}
                className={`num-geo rounded-lg px-2 py-1 text-[11px] font-medium transition ${
                  selectedYear === yr
                    ? 'bg-[#8052D6] text-[#F4F1F8]'
                    : 'bg-[#100F16] text-[#A79EB9]'
                }`}
              >
                {yr}
              </button>
            ))}
          </div>
        </div>

        <ActivityHeatmap
          cells={heatmap.cells}
          selectedDateKey={selectedCellKey}
          todayKey={todayKey}
          onSelectDay={(cell: HeatmapDayCell) => setSelectedCellKey(cell.dateKey)}
        />

        {/* Selected Day Status Control (Completed / Missed / Clear to Unlogged) */}
        <div className="rounded-2xl border border-[#B69AEF]/12 bg-[#100F16] p-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-[#F4F1F8]">
                {selectedDateInfo.weekday}، <Numeric value={selectedDateInfo.day} />{' '}
                {selectedDateInfo.month} <Numeric value={selectedDateInfo.year} className="text-[#A79EB9]" />
              </p>
              <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">
                وضعیت فعلی:{' '}
                <span className="text-[#B69AEF]">
                  {!selectedLog
                    ? 'ثبت‌نشده (بدون اطلاعات)'
                    : selectedLog.status === 'completed'
                    ? `موفق (${selectedLog.value} ${habit.unit})`
                    : selectedLog.status === 'partial'
                    ? `پیشرفت جزئی (${selectedLog.value}/${habit.targetValue} ${habit.unit})`
                    : 'انجام‌نشده'}
                </span>
              </p>
            </div>
          </div>

          {selectedCellKey <= todayKey && (
            <div className="mt-2.5 grid grid-cols-3 gap-1.5">
              <button
                type="button"
                onClick={() => setDayStatus(selectedCellKey, 'completed')}
                className={`flex items-center justify-center gap-1 rounded-xl py-1.5 text-[11px] font-medium transition ${
                  selectedLog?.status === 'completed'
                    ? 'bg-[#8052D6] text-[#F4F1F8]'
                    : 'bg-[#1B1924] text-[#A79EB9] hover:text-[#F4F1F8]'
                }`}
              >
                <Check className="h-3.5 w-3.5" />
                انجام شد
              </button>
              <button
                type="button"
                onClick={() => setDayStatus(selectedCellKey, 'missed')}
                className={`flex items-center justify-center gap-1 rounded-xl py-1.5 text-[11px] font-medium transition ${
                  selectedLog?.status === 'missed'
                    ? 'bg-[#D9778B]/25 text-[#EB9BAB] ring-1 ring-[#D9778B]/40'
                    : 'bg-[#1B1924] text-[#A79EB9] hover:text-[#EB9BAB]'
                }`}
              >
                <X className="h-3.5 w-3.5" />
                انجام نشد
              </button>
              <button
                type="button"
                onClick={() => setDayStatus(selectedCellKey, 'unlogged')}
                className={`flex items-center justify-center gap-1 rounded-xl py-1.5 text-[11px] font-medium transition ${
                  !selectedLog
                    ? 'bg-[#241936] text-[#B69AEF]'
                    : 'bg-[#1B1924] text-[#6E6680] hover:text-[#A79EB9]'
                }`}
              >
                <Minus className="h-3.5 w-3.5" />
                ثبت‌نشده
              </button>
            </div>
          )}
        </div>
      </LuxuryCard>

      {/* 14-Day Progress Trend */}
      <LuxuryCard className="p-4">
        <h2 className="mb-2 text-xs font-bold text-[#F4F1F8]">روند پیشرفت 14 روز اخیر</h2>
        <LuxuryAreaChart
          points={stats.last14Days.map((d) => ({
            key: d.dateKey,
            label: formatJalaaliShort(d.dateKey),
            value: d.ratio,
          }))}
          maxValue={100}
          unitSuffix="%"
        />
      </LuxuryCard>

      {/* 6-Month Comparison */}
      <LuxuryCard className="p-4">
        <h2 className="mb-2 text-xs font-bold text-[#F4F1F8]">مقایسه پایبندی ماهانه</h2>
        <LuxuryBarChart
          data={stats.monthlyComparison.map((m) => ({
            key: `${m.jy}-${m.jm}`,
            label: m.label,
            sublabel: `${m.label} ${m.jy} • ${m.completedDays} موفق / ${m.missedDays} ازدست‌رفته / ${m.unloggedDays} ثبت‌نشده`,
            value: m.adherence,
            highlighted: m.adherence >= 80,
          }))}
          maxValue={100}
          unitSuffix="%"
        />
      </LuxuryCard>

      {/* Full Interactive Recent History */}
      <LuxuryCard className="space-y-2.5 p-4">
        <h2 className="text-xs font-bold text-[#F4F1F8]">تاریخچه 14 روز اخیر</h2>
        <div className="space-y-1.5">
          {recentHistoryDays.map((dk) => {
            const log = logByDate.get(dk);
            const info = formatJalaaliFull(dk);
            return (
              <div
                key={dk}
                className="flex items-center justify-between rounded-xl border border-[#B69AEF]/8 bg-[#100F16] px-3 py-2"
              >
                <div className="text-xs">
                  <span className="font-medium text-[#F4F1F8]">{info.weekday}</span>
                  <span className="mx-1.5 text-[#6E6680]">•</span>
                  <Numeric value={info.day} className="text-[#B69AEF]" />{' '}
                  <span className="text-[#A79EB9]">{info.month}</span>
                </div>

                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setDayStatus(dk, 'completed')}
                    aria-label="ثبت انجام شد"
                    className={`rounded-lg px-2 py-1 text-[10px] font-medium ${
                      log?.status === 'completed'
                        ? 'bg-[#8052D6] text-[#F4F1F8]'
                        : 'bg-[#1B1924] text-[#6E6680]'
                    }`}
                  >
                    موفق
                  </button>
                  <button
                    type="button"
                    onClick={() => setDayStatus(dk, 'missed')}
                    aria-label="ثبت انجام نشد"
                    className={`rounded-lg px-2 py-1 text-[10px] font-medium ${
                      log?.status === 'missed'
                        ? 'bg-[#D9778B]/25 text-[#EB9BAB]'
                        : 'bg-[#1B1924] text-[#6E6680]'
                    }`}
                  >
                    نشد
                  </button>
                  <button
                    type="button"
                    onClick={() => setDayStatus(dk, 'unlogged')}
                    aria-label="بدون ثبت"
                    className={`rounded-lg px-2 py-1 text-[10px] font-medium ${
                      !log
                        ? 'bg-[#241936] text-[#B69AEF]'
                        : 'bg-[#1B1924] text-[#6E6680]'
                    }`}
                  >
                    ثبت‌نشده
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </LuxuryCard>
    </div>
  );
}
