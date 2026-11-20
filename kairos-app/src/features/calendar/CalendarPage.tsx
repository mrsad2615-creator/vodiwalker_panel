import { useMemo, useState } from 'react';
import {
  ArrowLeftRight,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  Flame,
  Plus,
  Timer,
} from 'lucide-react';
import type { Task } from '@/types/domain';
import {
  addDays,
  dateKeyToJalaali,
  formatJalaaliFull,
  getJalaaliMonthDaysCount,
  getJalaaliMonthName,
  getPersianWeekdayIndex,
  getPersianWeekKeys,
  getTodayKey,
  jalaaliToDateKey,
  PERSIAN_WEEKDAYS_SHORT,
  shiftJalaaliMonth,
} from '@/lib/date';
import {
  clearHabitLog,
  setTaskStatus,
  updateTask,
  upsertHabitLog,
} from '@/lib/db';
import { useFocusSessions, useHabitLogs, useHabits, useTasks } from '@/lib/queries';
import { isHabitScheduledOnDate } from '@/lib/analytics';
import { CATEGORY_META, formatDurationShort } from '@/lib/format';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { JalaliDatePicker } from '@/components/ui/JalaliDatePicker';
import { EmptyState } from '@/components/ui/EmptyState';

type CalendarViewMode = 'month' | 'week' | 'day';

export function CalendarPage() {
  const { openCreateTask, openEditTask, pushToast, settings } = useApp();
  const todayKey = getTodayKey();
  const todayJ = dateKeyToJalaali(todayKey);

  const [viewMode, setViewMode] = useState<CalendarViewMode>('month');
  const [selectedDateKey, setSelectedDateKey] = useState(todayKey);
  const [viewYear, setViewYear] = useState(todayJ.jy);
  const [viewMonth, setViewMonth] = useState(todayJ.jm);
  const [rescheduleTask, setRescheduleTask] = useState<Task | null>(null);

  const tasks = useTasks();
  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const focusSessions = useFocusSessions();

  const activeTasks = useMemo(() => tasks.filter((t) => !t.archived), [tasks]);
  const activeHabits = useMemo(() => habits.filter((h) => !h.archived), [habits]);

  // Index tasks count by dueDate for fast calendar dot markers
  const tasksByDate = useMemo(() => {
    const map = new Map<string, Task[]>();
    for (const t of activeTasks) {
      if (!t.dueDate) continue;
      const arr = map.get(t.dueDate);
      if (arr) arr.push(t);
      else map.set(t.dueDate, [t]);
    }
    return map;
  }, [activeTasks]);

  const completedHabitsByDate = useMemo(() => {
    const map = new Map<string, number>();
    for (const l of habitLogs) {
      if (l.status === 'completed') {
        map.set(l.dateKey, (map.get(l.dateKey) ?? 0) + 1);
      }
    }
    return map;
  }, [habitLogs]);

  const selectedDateTasks = useMemo(() => {
    return (tasksByDate.get(selectedDateKey) ?? []).slice().sort((a, b) => {
      if (a.status === 'completed' && b.status !== 'completed') return 1;
      if (a.status !== 'completed' && b.status === 'completed') return -1;
      return (a.dueTime ?? '99:99').localeCompare(b.dueTime ?? '99:99');
    });
  }, [tasksByDate, selectedDateKey]);

  const selectedDateHabits = useMemo(() => {
    const scheduled = activeHabits.filter((h) => isHabitScheduledOnDate(h, selectedDateKey));
    const logMap = new Map(
      habitLogs.filter((l) => l.dateKey === selectedDateKey).map((l) => [l.habitId, l])
    );
    return scheduled.map((habit) => ({ habit, log: logMap.get(habit.id) }));
  }, [activeHabits, habitLogs, selectedDateKey]);

  const selectedDateFocusMinutes = useMemo(() => {
    return Math.round(
      focusSessions
        .filter((s) => s.dateKey === selectedDateKey)
        .reduce((acc, s) => acc + s.durationMinutes, 0)
    );
  }, [focusSessions, selectedDateKey]);

  const monthDaysCount = getJalaaliMonthDaysCount(viewYear, viewMonth);
  const firstDayKey = jalaaliToDateKey(viewYear, viewMonth, 1);
  const leadingBlanks = getPersianWeekdayIndex(firstDayKey);

  const weekKeys = useMemo(() => getPersianWeekKeys(selectedDateKey), [selectedDateKey]);
  const selectedInfo = formatJalaaliFull(selectedDateKey);

  const navigatePrev = () => {
    if (viewMode === 'month') {
      const s = shiftJalaaliMonth(viewYear, viewMonth, -1);
      setViewYear(s.jy);
      setViewMonth(s.jm);
    } else if (viewMode === 'week') {
      const next = addDays(selectedDateKey, -7);
      setSelectedDateKey(next);
      const j = dateKeyToJalaali(next);
      setViewYear(j.jy);
      setViewMonth(j.jm);
    } else {
      const next = addDays(selectedDateKey, -1);
      setSelectedDateKey(next);
      const j = dateKeyToJalaali(next);
      setViewYear(j.jy);
      setViewMonth(j.jm);
    }
  };

  const navigateNext = () => {
    if (viewMode === 'month') {
      const s = shiftJalaaliMonth(viewYear, viewMonth, 1);
      setViewYear(s.jy);
      setViewMonth(s.jm);
    } else if (viewMode === 'week') {
      const next = addDays(selectedDateKey, 7);
      setSelectedDateKey(next);
      const j = dateKeyToJalaali(next);
      setViewYear(j.jy);
      setViewMonth(j.jm);
    } else {
      const next = addDays(selectedDateKey, 1);
      setSelectedDateKey(next);
      const j = dateKeyToJalaali(next);
      setViewYear(j.jy);
      setViewMonth(j.jm);
    }
  };

  const jumpToToday = () => {
    setSelectedDateKey(todayKey);
    setViewYear(todayJ.jy);
    setViewMonth(todayJ.jm);
  };

  const handleReschedule = async (newDateKey: string | null) => {
    if (!rescheduleTask || !newDateKey) return;
    await updateTask(rescheduleTask.id, { dueDate: newDateKey });
    void triggerHaptic('success', settings.hapticsEnabled);
    const jInfo = formatJalaaliFull(newDateKey);
    pushToast({
      title: 'برنامه جابه‌جا شد',
      description: `«${rescheduleTask.title}» به ${jInfo.day} ${jInfo.month} منتقل شد.`,
      tone: 'success',
    });
    setRescheduleTask(null);
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-[#ECECEF]">تقویم و برنامه‌ریزی</h1>
          <p className="mt-0.5 text-[11px] text-[#9C9CA6]">
            تقویم شمسی دقیق • جابه‌جایی سریع برنامه‌ها
          </p>
        </div>

        <button
          type="button"
          onClick={() => openCreateTask({ defaultDueDate: selectedDateKey })}
          className="btn-royal flex items-center gap-1.5 rounded-2xl px-3.5 py-2.5 text-xs font-semibold"
        >
          <Plus className="h-4 w-4" />
          <span>رویداد جدید</span>
        </button>
      </div>

      {/* View Switcher (Monthly / Weekly / Daily) + Today shortcut */}
      <div className="flex items-center gap-2">
        <div className="grid flex-1 grid-cols-3 gap-1 rounded-2xl border border-[#A99BF0]/12 bg-[#16161A] p-1">
          {(
            [
              { id: 'month', label: 'ماهانه' },
              { id: 'week', label: 'هفتگی' },
              { id: 'day', label: 'روزانه' },
            ] as const
          ).map((m) => (
            <button
              key={m.id}
              type="button"
              onClick={() => setViewMode(m.id)}
              className={`rounded-xl py-2 text-xs font-medium transition ${
                viewMode === m.id
                  ? 'bg-[#1C1C21] text-[#ECECEF] shadow-[inset_0_1px_0_rgba(182,154,239,0.2)]'
                  : 'text-[#9C9CA6] hover:text-[#ECECEF]'
              }`}
            >
              {m.label}
            </button>
          ))}
        </div>

        <button
          type="button"
          onClick={jumpToToday}
          className="btn-ghost-luxury rounded-2xl px-3.5 py-2.5 text-xs font-medium text-[#A99BF0]"
        >
          امروز
        </button>
      </div>

      {/* Navigator Bar */}
      <LuxuryCard className="p-4">
        <div className="mb-3 flex items-center justify-between">
          <button
            type="button"
            onClick={navigatePrev}
            aria-label="دوره قبل"
            className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#0E0E11] text-[#9C9CA6] hover:text-[#ECECEF]"
          >
            <ChevronRight className="h-4 w-4" />
          </button>

          <div className="text-center">
            <h2 className="flex items-center justify-center gap-1.5 text-sm font-bold text-[#ECECEF]">
              <span>{getJalaaliMonthName(viewMonth)}</span>
              <Numeric value={viewYear} className="text-[#A99BF0]" />
            </h2>
          </div>

          <button
            type="button"
            onClick={navigateNext}
            aria-label="دوره بعد"
            className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#0E0E11] text-[#9C9CA6] hover:text-[#ECECEF]"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
        </div>

        {/* MONTHLY VIEW */}
        {viewMode === 'month' && (
          <>
            <div className="mb-1.5 grid grid-cols-7 text-center text-[10.5px] font-medium text-[#6B6B75]">
              {PERSIAN_WEEKDAYS_SHORT.map((d) => (
                <div key={d} className="py-1">
                  {d}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7 gap-1">
              {Array.from({ length: leadingBlanks }).map((_, i) => (
                <div key={`b_${i}`} />
              ))}
              {Array.from({ length: monthDaysCount }, (_, i) => {
                const day = i + 1;
                const dk = jalaaliToDateKey(viewYear, viewMonth, day);
                const isSelected = dk === selectedDateKey;
                const isToday = dk === todayKey;
                const dayTasks = tasksByDate.get(dk) ?? [];
                const habitCount = completedHabitsByDate.get(dk) ?? 0;

                return (
                  <button
                    key={dk}
                    type="button"
                    onClick={() => setSelectedDateKey(dk)}
                    className={`flex h-11 flex-col items-center justify-center rounded-xl border transition ${
                      isSelected
                        ? 'border-[#A99BF0]/60 bg-[#6E56CF] text-[#ECECEF] shadow-[0_0_14px_rgba(128,82,214,0.48)]'
                        : isToday
                        ? 'border-[#6E56CF]/60 bg-[#1C1C21] text-[#ECECEF]'
                        : 'border-transparent bg-[#0E0E11]/60 text-[#ECECEF]/85 hover:border-[#A99BF0]/20'
                    }`}
                  >
                    <Numeric value={day} className="text-xs font-semibold" />
                    <div className="mt-1 flex items-center gap-0.5">
                      {dayTasks.length > 0 && (
                        <span
                          className={`h-1 w-1 rounded-full ${
                            isSelected ? 'bg-[#ECECEF]' : 'bg-[#A99BF0]'
                          }`}
                        />
                      )}
                      {habitCount > 0 && (
                        <span
                          className={`h-1 w-1 rounded-full ${
                            isSelected ? 'bg-[#ECECEF]/70' : 'bg-[#6E56CF]'
                          }`}
                        />
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          </>
        )}

        {/* WEEKLY VIEW */}
        {viewMode === 'week' && (
          <div className="grid grid-cols-7 gap-1.5">
            {weekKeys.map((dk, idx) => {
              const j = dateKeyToJalaali(dk);
              const isSelected = dk === selectedDateKey;
              const isToday = dk === todayKey;
              const dayTasksCount = (tasksByDate.get(dk) ?? []).length;

              return (
                <button
                  key={dk}
                  type="button"
                  onClick={() => setSelectedDateKey(dk)}
                  className={`flex flex-col items-center gap-1.5 rounded-2xl border py-3 transition ${
                    isSelected
                      ? 'border-[#A99BF0]/60 bg-[#6E56CF] text-[#ECECEF] shadow-[0_0_16px_rgba(128,82,214,0.45)]'
                      : isToday
                      ? 'border-[#6E56CF]/50 bg-[#1C1C21] text-[#ECECEF]'
                      : 'border-[#A99BF0]/10 bg-[#0E0E11] text-[#9C9CA6]'
                  }`}
                >
                  <span className="text-[10px]">{PERSIAN_WEEKDAYS_SHORT[idx]}</span>
                  <Numeric value={j.jd} variant="display" className="text-xl font-bold text-[#ECECEF]" />
                  <Numeric
                    value={dayTasksCount}
                    className={`rounded-md px-1.5 py-0.5 text-[9.5px] ${
                      isSelected ? 'bg-[#0A0A0C]/30 text-[#ECECEF]' : 'bg-[#16161A] text-[#A99BF0]'
                    }`}
                  />
                </button>
              );
            })}
          </div>
        )}

        {/* DAILY VIEW HEADER SUMMARY */}
        {viewMode === 'day' && (
          <div className="flex items-center justify-between rounded-2xl border border-[#A99BF0]/12 bg-[#0E0E11] p-3.5">
            <div>
              <p className="text-sm font-bold text-[#ECECEF]">
                {selectedInfo.weekday}،{' '}
                <Numeric value={selectedInfo.day} className="text-[#A99BF0]" />{' '}
                {selectedInfo.month}
              </p>
              <p className="mt-0.5 text-[11px] text-[#9C9CA6]">
                <Numeric value={selectedDateTasks.length} className="text-[#ECECEF]" /> وظیفه •{' '}
                <Numeric value={selectedDateHabits.length} className="text-[#ECECEF]" /> عادت
              </p>
            </div>
            <div className="flex items-center gap-1.5 rounded-xl bg-[#1C1C21] px-3 py-1.5 text-xs text-[#A99BF0]">
              <Timer className="h-3.5 w-3.5" />
              <Numeric value={formatDurationShort(selectedDateFocusMinutes)} />
            </div>
          </div>
        )}
      </LuxuryCard>

      {/* SELECTED DAY AGENDA (Tasks + Habits + Reschedule) */}
      <section className="space-y-3">
        <div className="flex items-center justify-between px-0.5">
          <h2 className="text-xs font-bold text-[#ECECEF]">
            برنامه {selectedInfo.weekday}،{' '}
            <Numeric value={selectedInfo.day} className="text-[#A99BF0]" /> {selectedInfo.month}
          </h2>
          {selectedDateFocusMinutes > 0 && (
            <span className="text-[11px] text-[#9C9CA6]">
              تمرکز ثبت‌شده:{' '}
              <Numeric
                value={formatDurationShort(selectedDateFocusMinutes)}
                className="font-semibold text-[#A99BF0]"
              />
            </span>
          )}
        </div>

        {/* Tasks on Selected Date */}
        {selectedDateTasks.length === 0 ? (
          <EmptyState
            icon={<CalendarDays className="h-5 w-5" />}
            title="برنامه‌ای در این تاریخ ثبت نشده است"
            description="برای این روز وظیفه یا رویداد جدیدی ثبت کنید."
            action={
              <button
                type="button"
                onClick={() => openCreateTask({ defaultDueDate: selectedDateKey })}
                className="btn-ghost-luxury rounded-xl px-4 py-2 text-xs font-medium text-[#A99BF0]"
              >
                + افزودن به این روز
              </button>
            }
          />
        ) : (
          <div className="space-y-2">
            {selectedDateTasks.map((task) => {
              const isDone = task.status === 'completed';
              const cat = CATEGORY_META[task.category];
              return (
                <LuxuryCard key={task.id} className="flex items-center gap-2.5 px-3.5 py-3">
                  <button
                    type="button"
                    onClick={async () => {
                      await setTaskStatus(task.id, isDone ? 'todo' : 'completed');
                      void triggerHaptic(isDone ? 'light' : 'success', settings.hapticsEnabled);
                    }}
                    aria-label="تغییر وضعیت وظیفه"
                    className={`flex h-5.5 w-5.5 shrink-0 items-center justify-center rounded-lg border transition ${
                      isDone
                        ? 'border-[#6E56CF] bg-[#6E56CF] text-[#ECECEF]'
                        : 'border-[#A99BF0]/28 bg-[#0E0E11]'
                    }`}
                  >
                    {isDone && <Check className="h-3.5 w-3.5" />}
                  </button>

                  <button
                    type="button"
                    onClick={() => openEditTask(task)}
                    className="min-w-0 flex-1 text-right"
                  >
                    <p
                      className={`truncate text-xs font-medium ${
                        isDone ? 'text-[#9C9CA6] line-through' : 'text-[#ECECEF]'
                      }`}
                    >
                      {task.title}
                    </p>
                    <div className="mt-1 flex items-center gap-2 text-[10px]">
                      <span
                        className="rounded px-1.5 py-0.5"
                        style={{ backgroundColor: cat.badgeBg, color: cat.badgeText }}
                      >
                        {cat.label}
                      </span>
                      {task.dueTime && (
                        <span className="inline-flex items-center gap-1 text-[#9C9CA6]">
                          <Clock className="h-3 w-3 text-[#6B6B75]" />
                          <Numeric value={task.dueTime} className="text-[10px]" />
                        </span>
                      )}
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setRescheduleTask(task)}
                    title="جابه‌جایی تاریخ"
                    aria-label="جابه‌جایی تاریخ"
                    className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-[#A99BF0]/12 bg-[#0E0E11] text-[#9C9CA6] transition hover:border-[#A99BF0]/35 hover:text-[#A99BF0]"
                  >
                    <ArrowLeftRight className="h-3.5 w-3.5" />
                  </button>
                </LuxuryCard>
              );
            })}
          </div>
        )}

        {/* Habits on Selected Date */}
        {selectedDateHabits.length > 0 && (
          <LuxuryCard className="space-y-2.5 p-4">
            <div className="flex items-center gap-1.5 text-xs font-bold text-[#ECECEF]">
              <Flame className="h-3.5 w-3.5 text-[#6E56CF]" />
              <span>عادت‌های این روز</span>
            </div>

            <div className="space-y-2">
              {selectedDateHabits.map(({ habit, log }) => {
                const isDone = log?.status === 'completed';
                return (
                  <div
                    key={habit.id}
                    className="flex items-center justify-between rounded-xl border border-[#A99BF0]/10 bg-[#0E0E11] px-3 py-2"
                  >
                    <div>
                      <p className="text-xs font-medium text-[#ECECEF]">{habit.name}</p>
                      <p className="text-[10px] text-[#6B6B75]">
                        {!log
                          ? 'ثبت‌نشده'
                          : log.status === 'completed'
                          ? 'انجام شده'
                          : log.status === 'partial'
                          ? `جزئی (${log.value}/${habit.targetValue})`
                          : 'انجام‌نشده'}
                      </p>
                    </div>
                    {selectedDateKey <= todayKey && (
                      <button
                        type="button"
                        onClick={async () => {
                          if (isDone) {
                            await clearHabitLog(habit.id, selectedDateKey);
                          } else {
                            await upsertHabitLog(
                              habit.id,
                              selectedDateKey,
                              'completed',
                              habit.targetValue
                            );
                          }
                          void triggerHaptic(isDone ? 'light' : 'success', settings.hapticsEnabled);
                        }}
                        className={`rounded-lg px-3 py-1 text-[11px] font-medium transition ${
                          isDone
                            ? 'bg-[#6E56CF] text-[#ECECEF]'
                            : 'bg-[#16161A] text-[#9C9CA6] hover:text-[#ECECEF]'
                        }`}
                      >
                        {isDone ? 'انجام شد' : 'ثبت'}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </LuxuryCard>
        )}
      </section>

      {/* Reschedule Bottom Sheet */}
      <ModalSheet
        open={Boolean(rescheduleTask)}
        onClose={() => setRescheduleTask(null)}
        title="جابه‌جایی تاریخ برنامه"
        subtitle={rescheduleTask?.title}
      >
        {rescheduleTask && (
          <JalaliDatePicker
            value={rescheduleTask.dueDate}
            onChange={(dk) => {
              void handleReschedule(dk);
            }}
            allowClear={false}
          />
        )}
      </ModalSheet>
    </div>
  );
}
