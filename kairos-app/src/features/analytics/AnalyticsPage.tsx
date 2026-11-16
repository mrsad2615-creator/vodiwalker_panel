import { useMemo, useState } from 'react';
import {
  ArrowDownRight,
  ArrowUpRight,
  Award,
  BarChart3,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Flame,
  Sparkles,
  TrendingUp,
} from 'lucide-react';
import { useFocusSessions, useHabitLogs, useHabits, useTasks } from '@/lib/queries';
import {
  computeAnnualHeatmap,
  computeAnnualReview,
  computeHabitStats,
  computeMonthlyReview,
  type HeatmapDayCell,
} from '@/lib/analytics';
import {
  addDays,
  dateKeyToJalaali,
  formatJalaaliFull,
  formatJalaaliShort,
  getPersianWeekdayIndex,
  getTodayKey,
  PERSIAN_WEEKDAYS_SHORT,
  shiftJalaaliMonth,
} from '@/lib/date';
import { formatDurationShort } from '@/lib/format';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { ActivityHeatmap } from '@/components/charts/ActivityHeatmap';
import { LuxuryBarChart } from '@/components/charts/LuxuryBarChart';
import { LuxuryAreaChart } from '@/components/charts/LuxuryAreaChart';
import { EmptyState } from '@/components/ui/EmptyState';

type AnalyticsTab = 'overview' | 'monthly' | 'annual';

function DeltaBadge({ value, suffix = '%' }: { value: number; suffix?: string }) {
  const positive = value >= 0;
  return (
    <span
      className={`inline-flex items-center gap-0.5 rounded-lg px-2 py-0.5 text-[10.5px] font-semibold ${
        positive
          ? 'bg-[#8052D6]/20 text-[#B69AEF]'
          : 'bg-[#D9778B]/18 text-[#EB9BAB]'
      }`}
    >
      {positive ? (
        <ArrowUpRight className="h-3 w-3" />
      ) : (
        <ArrowDownRight className="h-3 w-3" />
      )}
      <Numeric
        value={`${positive ? '+' : ''}${value}`}
        suffix={suffix}
        variant="geo"
        className="text-[10.5px]"
      />
    </span>
  );
}

export function AnalyticsPage() {
  const todayKey = getTodayKey();
  const todayJ = dateKeyToJalaali(todayKey);

  const [subTab, setSubTab] = useState<AnalyticsTab>('monthly');
  const [reviewYear, setReviewYear] = useState(todayJ.jy);
  const [reviewMonth, setReviewMonth] = useState(todayJ.jm);
  const [annualYear, setAnnualYear] = useState(todayJ.jy);
  const [selectedHeatCell, setSelectedHeatCell] = useState<HeatmapDayCell | null>(null);

  const tasks = useTasks();
  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const focusSessions = useFocusSessions();

  const activeHabits = useMemo(() => habits.filter((h) => !h.archived), [habits]);

  // 1. OVERVIEW REAL DATA
  const last7DaysData = useMemo(() => {
    return Array.from({ length: 7 }, (_, i) => {
      const dk = addDays(todayKey, i - 6);
      const tasksDone = tasks.filter((t) => t.status === 'completed' && t.completedDate === dk).length;
      const focusMin = Math.round(
        focusSessions.filter((s) => s.dateKey === dk).reduce((acc, s) => acc + s.durationMinutes, 0)
      );
      const habitsDone = habitLogs.filter((l) => l.dateKey === dk && l.status === 'completed').length;
      return {
        dateKey: dk,
        weekday: PERSIAN_WEEKDAYS_SHORT[getPersianWeekdayIndex(dk)],
        shortDate: formatJalaaliShort(dk),
        tasksDone,
        focusMin,
        habitsDone,
      };
    });
  }, [tasks, focusSessions, habitLogs, todayKey]);

  const habitAdherenceList = useMemo(() => {
    return activeHabits.map((h) => ({
      habit: h,
      stats: computeHabitStats(h, habitLogs, todayKey),
    }));
  }, [activeHabits, habitLogs, todayKey]);

  const overviewHeatmap = useMemo(
    () =>
      computeAnnualHeatmap({
        jalaaliYear: todayJ.jy,
        habits: activeHabits,
        habitLogs,
        tasks,
        focusSessions,
        todayKey,
        mode: 'overall',
      }),
    [todayJ.jy, activeHabits, habitLogs, tasks, focusSessions, todayKey]
  );

  // 2. MONTHLY REVIEW REAL DATA
  const monthlyReport = useMemo(
    () =>
      computeMonthlyReview(
        reviewYear,
        reviewMonth,
        tasks,
        habits,
        habitLogs,
        focusSessions,
        todayKey
      ),
    [reviewYear, reviewMonth, tasks, habits, habitLogs, focusSessions, todayKey]
  );

  // 3. ANNUAL REVIEW REAL DATA
  const annualReport = useMemo(
    () =>
      computeAnnualReview(annualYear, tasks, habits, habitLogs, focusSessions, todayKey),
    [annualYear, tasks, habits, habitLogs, focusSessions, todayKey]
  );

  const annualHeatmap = useMemo(
    () =>
      computeAnnualHeatmap({
        jalaaliYear: annualYear,
        habits: activeHabits,
        habitLogs,
        tasks,
        focusSessions,
        todayKey,
        mode: 'overall',
      }),
    [annualYear, activeHabits, habitLogs, tasks, focusSessions, todayKey]
  );

  const shiftReviewMonth = (delta: number) => {
    const next = shiftJalaaliMonth(reviewYear, reviewMonth, delta);
    setReviewYear(next.jy);
    setReviewMonth(next.jm);
  };

  return (
    <div className="space-y-4">
      {/* Page Title */}
      <div>
        <h1 className="text-lg font-bold text-[#F4F1F8]">تحلیل و گزارش عملکرد</h1>
        <p className="mt-0.5 text-[11px] text-[#A79EB9]">
          آمار واقعی مبتنی بر داده‌های ثبت‌شده شما • مرور ماهانه و سالانه
        </p>
      </div>

      {/* Sub-Navigation Switcher */}
      <div className="grid grid-cols-3 gap-1 rounded-2xl border border-[#B69AEF]/12 bg-[#15141B] p-1">
        {(
          [
            { id: 'overview', label: 'نمای کلی' },
            { id: 'monthly', label: 'گزارش ماهانه' },
            { id: 'annual', label: 'مرور سالانه' },
          ] as const
        ).map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setSubTab(tab.id)}
            className={`rounded-xl py-2 text-xs font-medium transition ${
              subTab === tab.id
                ? 'bg-[#241936] text-[#F4F1F8] shadow-[inset_0_1px_0_rgba(182,154,239,0.2)]'
                : 'text-[#A79EB9] hover:text-[#F4F1F8]'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* ==================== TAB 1: OVERVIEW ANALYTICS ==================== */}
      {subTab === 'overview' && (
        <div className="space-y-4">
          {/* Weekly Focus Chart */}
          <LuxuryCard className="p-4">
            <div className="mb-3 flex items-center justify-between">
              <div>
                <h2 className="text-xs font-bold text-[#F4F1F8]">زمان تمرکز 7 روز اخیر</h2>
                <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">دقیقه تمرکز ثبت‌شده در هر روز</p>
              </div>
              <Numeric
                value={formatDurationShort(
                  last7DaysData.reduce((s, d) => s + d.focusMin, 0)
                )}
                variant="display"
                className="text-2xl font-bold text-[#B69AEF]"
              />
            </div>

            <LuxuryBarChart
              data={last7DaysData.map((d) => ({
                key: d.dateKey,
                label: d.weekday,
                sublabel: `${d.shortDate} • ${d.focusMin} دقیقه تمرکز`,
                value: d.focusMin,
                highlighted: d.dateKey === todayKey,
              }))}
              unitSuffix="m"
            />
          </LuxuryCard>

          {/* Weekly Completed Tasks Chart */}
          <LuxuryCard className="p-4">
            <div className="mb-3 flex items-center justify-between">
              <div>
                <h2 className="text-xs font-bold text-[#F4F1F8]">روند تکمیل وظایف هفتگی</h2>
                <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">تعداد وظایف تکمیل‌شده در هر روز</p>
              </div>
              <Numeric
                value={last7DaysData.reduce((s, d) => s + d.tasksDone, 0)}
                variant="display"
                className="text-2xl font-bold text-[#F4F1F8]"
                suffix={<span className="num-geo ml-1 text-xs text-[#A79EB9]">کار</span>}
              />
            </div>

            <LuxuryBarChart
              data={last7DaysData.map((d) => ({
                key: d.dateKey,
                label: d.weekday,
                sublabel: `${d.shortDate} • ${d.tasksDone} وظیفه تکمیل‌شده`,
                value: d.tasksDone,
                highlighted: d.dateKey === todayKey,
              }))}
            />
          </LuxuryCard>

          {/* Habit Adherence Overview */}
          <LuxuryCard className="space-y-3 p-4">
            <div className="flex items-center justify-between">
              <h2 className="text-xs font-bold text-[#F4F1F8]">میزان پایبندی به عادت‌ها</h2>
              <Flame className="h-4 w-4 text-[#8052D6]" />
            </div>

            {habitAdherenceList.length === 0 ? (
              <p className="py-4 text-center text-xs text-[#6E6680]">
                عادتی ثبت نشده است.
              </p>
            ) : (
              <div className="space-y-3">
                {habitAdherenceList.map(({ habit, stats }) => (
                  <div key={habit.id} className="space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="truncate font-medium text-[#F4F1F8]">{habit.name}</span>
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] text-[#6E6680]">
                          رکورد <Numeric value={stats.bestStreak} />d
                        </span>
                        <Numeric
                          value={stats.adherencePercent}
                          suffix="%"
                          className="font-bold text-[#B69AEF]"
                        />
                      </div>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-[#09090D]">
                      <div
                        className="h-full rounded-full transition-all duration-300"
                        style={{
                          width: `${stats.adherencePercent}%`,
                          background: 'linear-gradient(90deg, #8052D6 0%, #B69AEF 100%)',
                        }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </LuxuryCard>

          {/* Overall Combined Activity Heatmap */}
          <LuxuryCard className="space-y-3 p-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-xs font-bold text-[#F4F1F8]">تقویم حرارتی کلی فعالیت‌ها</h2>
                <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">
                  ترکیب واقعی وظایف، عادت‌ها و جلسات تمرکز سال <Numeric value={todayJ.jy} />
                </p>
              </div>
              <Numeric
                value={overviewHeatmap.activeDays}
                variant="display"
                className="text-2xl font-bold text-[#B69AEF]"
                suffix={<span className="num-geo ml-1 text-[10px] text-[#A79EB9]">روز</span>}
              />
            </div>

            <ActivityHeatmap
              cells={overviewHeatmap.cells}
              selectedDateKey={selectedHeatCell?.dateKey ?? null}
              todayKey={todayKey}
              onSelectDay={setSelectedHeatCell}
            />

            {selectedHeatCell && (
              <div className="flex items-center justify-between rounded-xl border border-[#B69AEF]/12 bg-[#100F16] px-3 py-2 text-[11px]">
                <span className="text-[#F4F1F8]">
                  {formatJalaaliFull(selectedHeatCell.dateKey).compact}
                </span>
                <span className="text-[#A79EB9]">
                  کار: <Numeric value={selectedHeatCell.tasksCompleted} className="text-[#F4F1F8]" /> •
                  عادت: <Numeric value={selectedHeatCell.habitsCompleted} className="text-[#B69AEF]" /> •
                  تمرکز: <Numeric value={`${selectedHeatCell.focusMinutes}m`} className="text-[#F4F1F8]" />
                </span>
              </div>
            )}
          </LuxuryCard>
        </div>
      )}

      {/* ==================== TAB 2: MONTHLY REVIEW + SMART INSIGHTS ==================== */}
      {subTab === 'monthly' && (
        <div className="space-y-4">
          {/* Month Navigator */}
          <LuxuryCard className="flex items-center justify-between px-3.5 py-2.5">
            <button
              type="button"
              onClick={() => shiftReviewMonth(-1)}
              aria-label="ماه قبل"
              className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#100F16] text-[#A79EB9] hover:text-[#F4F1F8]"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
            <div className="text-center">
              <p className="text-sm font-bold text-[#F4F1F8]">{monthlyReport.monthLabel}</p>
              <p className="text-[10px] text-[#A79EB9]">مرور عملکرد ماهانه</p>
            </div>
            <button
              type="button"
              onClick={() => shiftReviewMonth(1)}
              aria-label="ماه بعد"
              className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#100F16] text-[#A79EB9] hover:text-[#F4F1F8]"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
          </LuxuryCard>

          {!monthlyReport.hasAnyData ? (
            <EmptyState
              icon={<BarChart3 className="h-5 w-5" />}
              title={`داده‌ای در ${monthlyReport.monthLabel} ثبت نشده است`}
              description="کایروس هیچ آمار ساختگی تولید نمی‌کند. برای مشاهده گزارش واقعی این ماه، وظیفه، عادت یا جلسه تمرکز ثبت کنید."
            />
          ) : (
            <>
              {/* Monthly Hero Success Card */}
              <LuxuryCard variant="hero" className="p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <span className="text-[11px] font-medium text-[#B69AEF]">
                      درصد موفقیت کل ماه
                    </span>
                    <div className="mt-1 flex items-baseline gap-1">
                      <Numeric
                        value={monthlyReport.overallSuccessRate}
                        variant="display"
                        className="text-[3.6rem] font-extrabold text-[#F4F1F8]"
                      />
                      <span className="num-display text-2xl font-bold text-[#B69AEF]">%</span>
                    </div>
                  </div>

                  {monthlyReport.comparison.hasPrevData && (
                    <div className="text-left">
                      <span className="block text-[10px] text-[#A79EB9]">
                        مقایسه با {monthlyReport.comparison.prevMonthLabel}
                      </span>
                      <div className="mt-1.5 flex flex-col items-end gap-1">
                        <DeltaBadge value={monthlyReport.comparison.habitAdherenceDelta} suffix="% عادت" />
                        <DeltaBadge value={monthlyReport.comparison.taskSuccessDelta} suffix="% وظایف" />
                      </div>
                    </div>
                  )}
                </div>

                {/* 6 Exact Monthly Metrics */}
                <div className="mt-5 grid grid-cols-3 gap-2">
                  <div className="rounded-xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-2.5">
                    <span className="block text-[10px] text-[#A79EB9]">کل وظایف</span>
                    <Numeric
                      value={monthlyReport.totalTasks}
                      variant="display"
                      className="mt-1 text-2xl font-bold text-[#F4F1F8]"
                    />
                  </div>
                  <div className="rounded-xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-2.5">
                    <span className="block text-[10px] text-[#A79EB9]">تکمیل‌شده</span>
                    <Numeric
                      value={monthlyReport.completedTasks}
                      variant="display"
                      className="mt-1 text-2xl font-bold text-[#B69AEF]"
                    />
                  </div>
                  <div className="rounded-xl border border-[#D9778B]/18 bg-[#09090D]/60 p-2.5">
                    <span className="block text-[10px] text-[#A79EB9]">عقب‌افتاده</span>
                    <Numeric
                      value={monthlyReport.overdueTasks}
                      variant="display"
                      className="mt-1 text-2xl font-bold text-[#EB9BAB]"
                    />
                  </div>

                  <div className="rounded-xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-2.5">
                    <span className="block text-[10px] text-[#A79EB9]">زمان تمرکز</span>
                    <Numeric
                      value={formatDurationShort(monthlyReport.totalFocusMinutes)}
                      variant="display"
                      className="mt-1 text-2xl font-bold text-[#F4F1F8]"
                    />
                  </div>
                  <div className="rounded-xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-2.5">
                    <span className="block text-[10px] text-[#A79EB9]">پایبندی عادت</span>
                    <Numeric
                      value={monthlyReport.habitAdherenceRate}
                      suffix="%"
                      variant="display"
                      className="mt-1 text-2xl font-bold text-[#B69AEF]"
                    />
                  </div>
                  <div className="rounded-xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-2.5">
                    <span className="block text-[10px] text-[#A79EB9]">موفقیت وظایف</span>
                    <Numeric
                      value={monthlyReport.taskSuccessRate}
                      suffix="%"
                      variant="display"
                      className="mt-1 text-2xl font-bold text-[#F4F1F8]"
                    />
                  </div>
                </div>
              </LuxuryCard>

              {/* Best Day & Most Productive Week */}
              <div className="grid grid-cols-2 gap-2.5">
                <LuxuryCard className="p-3.5">
                  <div className="flex items-center gap-1.5 text-[11px] text-[#A79EB9]">
                    <Award className="h-3.5 w-3.5 text-[#B69AEF]" />
                    <span>بهترین روز ماه</span>
                  </div>
                  {monthlyReport.bestDay ? (
                    <>
                      <p className="mt-2 text-sm font-bold text-[#F4F1F8]">
                        {monthlyReport.bestDay.label}
                      </p>
                      <Numeric
                        value={monthlyReport.bestDay.score}
                        suffix=" pts"
                        className="mt-0.5 text-xs font-semibold text-[#B69AEF]"
                      />
                    </>
                  ) : (
                    <p className="mt-2 text-xs text-[#6E6680]">ثبت نشده</p>
                  )}
                </LuxuryCard>

                <LuxuryCard className="p-3.5">
                  <div className="flex items-center gap-1.5 text-[11px] text-[#A79EB9]">
                    <TrendingUp className="h-3.5 w-3.5 text-[#8052D6]" />
                    <span>پربازده‌ترین هفته</span>
                  </div>
                  {monthlyReport.mostProductiveWeek ? (
                    <>
                      <p className="mt-2 text-sm font-bold text-[#F4F1F8]">
                        {monthlyReport.mostProductiveWeek.label}
                      </p>
                      <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">
                        <Numeric
                          value={monthlyReport.mostProductiveWeek.completedTasks}
                          className="text-[#B69AEF]"
                        />{' '}
                        کار •{' '}
                        <Numeric
                          value={formatDurationShort(monthlyReport.mostProductiveWeek.focusMinutes)}
                          className="text-[#F4F1F8]"
                        />
                      </p>
                    </>
                  ) : (
                    <p className="mt-2 text-xs text-[#6E6680]">ثبت نشده</p>
                  )}
                </LuxuryCard>
              </div>

              {/* Monthly Performance Curve */}
              <LuxuryCard className="p-4">
                <h2 className="mb-2 text-xs font-bold text-[#F4F1F8]">نمودار تغییرات عملکرد ماهانه</h2>
                <LuxuryAreaChart
                  points={monthlyReport.dailyCurve.map((pt) => ({
                    key: pt.dateKey,
                    label: `روز ${pt.day}`,
                    value: pt.score,
                  }))}
                  maxValue={100}
                  unitSuffix=" pts"
                />
              </LuxuryCard>

              {/* Month-over-Month Comparison Card */}
              {monthlyReport.comparison.hasPrevData && (
                <LuxuryCard className="space-y-2.5 p-4">
                  <h2 className="text-xs font-bold text-[#F4F1F8]">
                    مقایسه با {monthlyReport.comparison.prevMonthLabel}
                  </h2>
                  <div className="grid grid-cols-3 gap-2 text-center">
                    <div className="rounded-xl bg-[#100F16] p-2.5">
                      <span className="block text-[10px] text-[#A79EB9]">وظایف تکمیلی</span>
                      <div className="mt-1.5 flex justify-center">
                        <DeltaBadge value={monthlyReport.comparison.completedTasksDelta} suffix="" />
                      </div>
                    </div>
                    <div className="rounded-xl bg-[#100F16] p-2.5">
                      <span className="block text-[10px] text-[#A79EB9]">پایبندی عادت</span>
                      <div className="mt-1.5 flex justify-center">
                        <DeltaBadge value={monthlyReport.comparison.habitAdherenceDelta} suffix="%" />
                      </div>
                    </div>
                    <div className="rounded-xl bg-[#100F16] p-2.5">
                      <span className="block text-[10px] text-[#A79EB9]">تمرکز (دقیقه)</span>
                      <div className="mt-1.5 flex justify-center">
                        <DeltaBadge value={monthlyReport.comparison.focusMinutesDelta} suffix="m" />
                      </div>
                    </div>
                  </div>
                </LuxuryCard>
              )}

              {/* SMART INSIGHTS SECTION */}
              <section className="space-y-2.5">
                <div className="flex items-center gap-1.5 px-0.5">
                  <Sparkles className="h-4 w-4 text-[#B69AEF]" />
                  <h2 className="text-sm font-bold text-[#F4F1F8]">تحلیل هوشمند داده‌های واقعی</h2>
                </div>

                <div className="space-y-2">
                  {monthlyReport.insights.map((ins) => (
                    <LuxuryCard key={ins.id} className="p-4">
                      <div className="flex items-center justify-between gap-2">
                        <h3 className="text-xs font-bold text-[#F4F1F8]">{ins.title}</h3>
                        {ins.metricBadge && (
                          <Numeric
                            value={ins.metricBadge}
                            className="rounded-lg bg-[#241936] px-2 py-0.5 text-[10.5px] font-semibold text-[#B69AEF]"
                          />
                        )}
                      </div>
                      <p className="mt-1.5 text-xs leading-6 text-[#A79EB9]">{ins.body}</p>
                    </LuxuryCard>
                  ))}
                </div>
              </section>
            </>
          )}
        </div>
      )}

      {/* ==================== TAB 3: ANNUAL REVIEW ==================== */}
      {subTab === 'annual' && (
        <div className="space-y-4">
          {/* Year Selector */}
          <LuxuryCard className="flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2">
              <Calendar className="h-4 w-4 text-[#8052D6]" />
              <span className="text-xs font-bold text-[#F4F1F8]">گزارش سالانه شمسی</span>
            </div>
            <div className="flex items-center gap-1">
              {[todayJ.jy - 2, todayJ.jy - 1, todayJ.jy].map((yr) => (
                <button
                  key={yr}
                  type="button"
                  onClick={() => setAnnualYear(yr)}
                  className={`num-geo rounded-lg px-2.5 py-1 text-xs font-semibold transition ${
                    annualYear === yr
                      ? 'bg-[#8052D6] text-[#F4F1F8]'
                      : 'bg-[#100F16] text-[#A79EB9]'
                  }`}
                >
                  {yr}
                </button>
              ))}
            </div>
          </LuxuryCard>

          {!annualReport.hasAnyData ? (
            <EmptyState
              icon={<Calendar className="h-5 w-5" />}
              title={`گزارشی برای سال ${annualYear} ثبت نشده است`}
              description="با ثبت وظایف، عادت‌ها و جلسات تمرکز، گزارش جامع سالانه شما به‌طور خودکار ساخته می‌شود."
            />
          ) : (
            <>
              {/* Annual Hero Summary */}
              <LuxuryCard variant="hero" className="grid grid-cols-2 gap-3 p-4">
                <div className="rounded-2xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-3">
                  <span className="text-[10.5px] text-[#A79EB9]">وظایف تکمیل‌شده سال</span>
                  <Numeric
                    value={annualReport.totalCompletedTasks}
                    variant="display"
                    className="mt-1 block text-3xl font-extrabold text-[#F4F1F8]"
                  />
                </div>
                <div className="rounded-2xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-3">
                  <span className="text-[10.5px] text-[#A79EB9]">مجموع زمان تمرکز</span>
                  <Numeric
                    value={formatDurationShort(annualReport.totalFocusMinutes)}
                    variant="display"
                    className="mt-1 block text-3xl font-extrabold text-[#B69AEF]"
                  />
                </div>
                <div className="rounded-2xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-3">
                  <span className="text-[10.5px] text-[#A79EB9]">میانگین پایبندی عادت‌ها</span>
                  <Numeric
                    value={annualReport.averageHabitAdherence}
                    suffix="%"
                    variant="display"
                    className="mt-1 block text-3xl font-extrabold text-[#B69AEF]"
                  />
                </div>
                <div className="rounded-2xl border border-[#B69AEF]/12 bg-[#09090D]/60 p-3">
                  <span className="text-[10.5px] text-[#A79EB9]">روزهای فعال سال</span>
                  <Numeric
                    value={annualReport.activeDays}
                    variant="display"
                    className="mt-1 block text-3xl font-extrabold text-[#F4F1F8]"
                  />
                </div>
              </LuxuryCard>

              {/* Best & Weakest Month */}
              <div className="grid grid-cols-2 gap-2.5">
                <LuxuryCard className="p-3.5">
                  <span className="text-[10.5px] text-[#B69AEF]">بهترین ماه سال</span>
                  {annualReport.bestMonth ? (
                    <>
                      <p className="mt-1.5 text-sm font-bold text-[#F4F1F8]">
                        {annualReport.bestMonth.label}
                      </p>
                      <Numeric
                        value={annualReport.bestMonth.overallScore}
                        suffix="%"
                        variant="display"
                        className="mt-1 text-2xl font-bold text-[#B69AEF]"
                      />
                    </>
                  ) : (
                    <p className="mt-2 text-xs text-[#6E6680]">—</p>
                  )}
                </LuxuryCard>

                <LuxuryCard className="p-3.5">
                  <span className="text-[10.5px] text-[#A79EB9]">ضعیف‌ترین ماه فعال</span>
                  {annualReport.weakestMonth ? (
                    <>
                      <p className="mt-1.5 text-sm font-bold text-[#F4F1F8]">
                        {annualReport.weakestMonth.label}
                      </p>
                      <Numeric
                        value={annualReport.weakestMonth.overallScore}
                        suffix="%"
                        variant="display"
                        className="mt-1 text-2xl font-bold text-[#EB9BAB]"
                      />
                    </>
                  ) : (
                    <p className="mt-2 text-[11px] text-[#6E6680]">نیاز به حداقل 2 ماه فعال</p>
                  )}
                </LuxuryCard>
              </div>

              {/* 12-Month Comparison Bar Chart */}
              <LuxuryCard className="p-4">
                <h2 className="mb-2 text-xs font-bold text-[#F4F1F8]">
                  مقایسه 12 ماه سال <Numeric value={annualYear} className="text-[#B69AEF]" />
                </h2>
                <LuxuryBarChart
                  data={annualReport.months.map((m) => ({
                    key: `m_${m.jm}`,
                    label: m.label.slice(0, 3),
                    sublabel: `${m.label} • موفقیت ${m.overallScore}% • ${m.completedTasks} کار • ${formatDurationShort(m.focusMinutes)}`,
                    value: m.overallScore,
                    highlighted: annualReport.bestMonth?.jm === m.jm,
                  }))}
                  maxValue={100}
                  unitSuffix="%"
                />
              </LuxuryCard>

              {/* Annual 365-Day Heatmap */}
              <LuxuryCard className="space-y-3 p-4">
                <h2 className="text-xs font-bold text-[#F4F1F8]">
                  نقشه حرارتی کل سال <Numeric value={annualYear} className="text-[#B69AEF]" />
                </h2>
                <ActivityHeatmap
                  cells={annualHeatmap.cells}
                  selectedDateKey={selectedHeatCell?.dateKey ?? null}
                  todayKey={todayKey}
                  onSelectDay={setSelectedHeatCell}
                />
              </LuxuryCard>
            </>
          )}
        </div>
      )}
    </div>
  );
}
