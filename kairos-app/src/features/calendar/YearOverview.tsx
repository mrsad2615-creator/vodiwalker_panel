import { useMemo } from 'react';
import { ChevronLeft, ChevronRight } from '@/components/ui/icons';
import type { Task } from '@/types/domain';
import {
  getJalaaliMonthDaysCount,
  getJalaaliMonthName,
  getPersianWeekdayIndex,
  isJalaaliLeap,
  jalaaliToDateKey,
  PERSIAN_WEEKDAYS_SHORT,
} from '@/lib/date';
import { formatDurationShort } from '@/lib/format';
import { Numeric } from '@/components/ui/Numeric';

interface YearOverviewProps {
  year: number;
  todayKey: string;
  selectedDateKey: string;
  tasksByDate: Map<string, Task[]>;
  habitCountByDate: Map<string, number>;
  focusMinutesByDate: Map<string, number>;
  onShiftYear: (delta: number) => void;
  onPickDate: (dateKey: string) => void;
  onPickMonth: (month: number) => void;
}

/**
 * YearOverview — the twelve-month Jalali year, at a size you can actually read.
 *
 * Desktop calendars (Windows, Fantastical) earn their keep in the year view:
 * every month is a full grid, every day carries real data, and the whole year is
 * legible without a hover tooltip. On a phone that means one wide month per row
 * (7 columns × ~52px cells ≈ 50dpi targets), a strict 2px load bar per day, and
 * one honest footer of numbers per month.
 */
export function YearOverview({
  year,
  todayKey,
  selectedDateKey,
  tasksByDate,
  habitCountByDate,
  focusMinutesByDate,
  onShiftYear,
  onPickDate,
  onPickMonth,
}: YearOverviewProps) {
  const months = useMemo(() => {
    return Array.from({ length: 12 }, (_, index) => {
      const month = index + 1;
      const daysInMonth = getJalaaliMonthDaysCount(year, month);
      const firstKey = jalaaliToDateKey(year, month, 1);
      const leadingBlanks = getPersianWeekdayIndex(firstKey);

      const days = Array.from({ length: daysInMonth }, (_, dayIndex) => {
        const day = dayIndex + 1;
        const key = jalaaliToDateKey(year, month, day);
        const dayTasks = tasksByDate.get(key) ?? [];
        return {
          day,
          key,
          taskCount: dayTasks.length,
          openTaskCount: dayTasks.filter((t) => t.status !== 'completed').length,
          habitCount: habitCountByDate.get(key) ?? 0,
          focusMinutes: focusMinutesByDate.get(key) ?? 0,
          isFriday: getPersianWeekdayIndex(key) === 6,
        };
      });

      const monthTasks = days.reduce((acc, d) => acc + d.taskCount, 0);
      const monthFocus = days.reduce((acc, d) => acc + d.focusMinutes, 0);
      const activeDays = days.filter((d) => d.taskCount > 0 || d.habitCount > 0 || d.focusMinutes > 0).length;

      return {
        month,
        name: getJalaaliMonthName(month),
        daysInMonth,
        leadingBlanks,
        days,
        monthTasks,
        monthFocus,
        activeDays,
        maxTaskLoad: days.reduce((acc, d) => Math.max(acc, d.taskCount), 0),
      };
    });
  }, [year, tasksByDate, habitCountByDate, focusMinutesByDate]);

  const totals = useMemo(() => {
    return months.reduce(
      (acc, m) => ({
        tasks: acc.tasks + m.monthTasks,
        focus: acc.focus + m.monthFocus,
        activeDays: acc.activeDays + m.activeDays,
      }),
      { tasks: 0, focus: 0, activeDays: 0 }
    );
  }, [months]);

  return (
    <div className="space-y-3">
      {/* ── YEAR HEADER ──────────────────────────────────────────────────── */}
      <div className="rail p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <span className="eyebrow block">YEAR</span>
            <div className="mt-1.5 flex items-baseline gap-2">
              <Numeric
                value={year}
                variant="display"
                className="text-[2.1rem] font-bold leading-none text-[var(--text)]"
              />
              <span className="text-[10px] text-[var(--text-3)]">
                {isJalaaliLeap(year) ? 'سال کبیسه' : 'سال عادی'}
              </span>
            </div>
            <p className="mt-1.5 text-[10px] text-[var(--text-3)]">
              تقویم شمسی · ۱۲ ماه · همهٔ روزها با دادهٔ واقعی
            </p>
          </div>

          <div className="flex shrink-0 items-center gap-1.5">
            <button
              type="button"
              onClick={() => onShiftYear(-1)}
              aria-label="سال قبل"
              className="grid h-8 w-8 place-items-center rounded-full border border-[var(--border)] text-[var(--text-2)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text)]"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => onShiftYear(1)}
              aria-label="سال بعد"
              className="grid h-8 w-8 place-items-center rounded-full border border-[var(--border)] text-[var(--text-2)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text)]"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
          </div>
        </div>

        <div className="mt-3 grid grid-cols-3 gap-3 border-t border-[var(--border)] pt-3">
          {[
            { label: 'وظیفه', value: String(totals.tasks), suffix: '' },
            { label: 'تمرکز', value: formatDurationShort(totals.focus), suffix: '' },
            { label: 'روز فعال', value: String(totals.activeDays), suffix: '' },
          ].map((stat) => (
            <div key={stat.label}>
              <Numeric
                value={stat.value}
                variant="geo"
                className="block text-[14px] font-bold text-[var(--text)]"
              />
              <span className="mt-0.5 block text-[9px] text-[var(--text-3)]">{stat.label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── MONTH JUMP STRIP ─────────────────────────────────────────────── */}
      <div className="no-scrollbar -mx-4 flex gap-1.5 overflow-x-auto px-4">
        {months.map((m) => (
          <button
            key={m.month}
            type="button"
            onClick={() => onPickMonth(m.month)}
            className="shrink-0 rounded-full border border-[var(--border)] px-2.5 py-1 text-[10px] font-medium text-[var(--text-2)] transition-colors hover:border-[var(--accent-fg)]/50 hover:text-[var(--text)]"
          >
            {m.name}
          </button>
        ))}
      </div>

      {/* ── TWELVE MONTHS ────────────────────────────────────────────────── */}
      {months.map((m) => (
        <section key={m.month} className="rail p-3" aria-label={`${m.name} ${year}`}>
          <header className="flex items-baseline justify-between gap-2 px-0.5">
            <div className="flex items-baseline gap-1.5">
              <h3 className="text-[13px] font-bold text-[var(--text)]">{m.name}</h3>
              <Numeric value={m.daysInMonth} variant="geo" className="text-[9px] text-[var(--text-3)]" />
              <span className="text-[9px] text-[var(--text-3)]">روز</span>
            </div>
            <button
              type="button"
              onClick={() => onPickMonth(m.month)}
              className="text-[9.5px] font-medium text-[var(--accent-fg)]"
            >
              ماه کامل
            </button>
          </header>

          <div className="mt-2 grid grid-cols-7 text-center">
            {PERSIAN_WEEKDAYS_SHORT.map((d) => (
              <span key={d} className="py-1 text-[9px] font-medium text-[var(--text-3)]">
                {d}
              </span>
            ))}
          </div>

          <div className="grid grid-cols-7 gap-[3px]">
            {Array.from({ length: m.leadingBlanks }).map((_, i) => (
              <span key={`blank-${i}`} aria-hidden="true" />
            ))}

            {m.days.map((d) => {
              const isToday = d.key === todayKey;
              const isSelected = d.key === selectedDateKey;
              const hasTasks = d.taskCount > 0;
              const load = m.maxTaskLoad > 0 ? d.taskCount / m.maxTaskLoad : 0;

              return (
                <button
                  key={d.key}
                  type="button"
                  onClick={() => onPickDate(d.key)}
                  aria-label={`${d.day} ${m.name} ${year}${isToday ? ' (امروز)' : ''}`}
                  aria-current={isToday ? 'date' : undefined}
                  className={`relative flex aspect-square flex-col items-center justify-center rounded-[9px] border transition-colors ${
                    isSelected
                      ? 'border-[var(--accent-fg)]/70 bg-[var(--accent)]'
                      : isToday
                      ? 'border-[var(--accent)] bg-[color-mix(in_srgb,var(--accent)_16%,transparent)]'
                      : 'border-transparent hover:bg-[color-mix(in_srgb,var(--text)_6%,transparent)]'
                  }`}
                >
                  <Numeric
                    value={d.day}
                    variant="geo"
                    className={`text-[13px] font-semibold leading-none ${
                      isSelected
                        ? 'text-[var(--accent-on)]'
                        : isToday
                        ? 'text-[var(--text)]'
                        : d.isFriday
                        ? 'text-[var(--text-2)]'
                        : 'text-[var(--text)]'
                    }`}
                  />

                  {/* Load bar: width = how loaded the day is, never a fake value. */}
                  <span className="absolute inset-x-[18%] bottom-[5px] h-[2px] overflow-hidden rounded-full bg-[color-mix(in_srgb,var(--text)_10%,transparent)]">
                    {hasTasks && (
                      <span
                        className="block h-full rounded-full"
                        style={{
                          width: `${Math.max(22, Math.round(load * 100))}%`,
                          background: isSelected ? 'var(--accent-on)' : 'var(--accent-fg)',
                        }}
                      />
                    )}
                  </span>

                  {d.habitCount > 0 && (
                    <span
                      aria-hidden="true"
                      className="absolute right-[5px] top-[5px] h-[3px] w-[3px] rounded-full"
                      style={{
                        background: isSelected ? 'var(--accent-on)' : 'var(--accent)',
                      }}
                    />
                  )}

                  {d.focusMinutes > 0 && (
                    <span
                      aria-hidden="true"
                      className="absolute left-[5px] top-[5px] h-[3px] w-[3px] rounded-full"
                      style={{
                        background: isSelected
                          ? 'color-mix(in srgb, var(--accent-on) 70%, transparent)'
                          : 'var(--text-2)',
                      }}
                    />
                  )}
                </button>
              );
            })}
          </div>

          <footer className="mt-2 flex items-center justify-between border-t border-[var(--border)] pt-1.5 text-[9px] text-[var(--text-3)]">
            <span className="flex items-center gap-1">
              <Numeric value={m.monthTasks} variant="geo" className="text-[var(--text-2)]" />
              وظیفه
              <span className="opacity-40">·</span>
              <Numeric value={m.activeDays} variant="geo" className="text-[var(--text-2)]" />
              روز فعال
            </span>
            <span className="flex items-center gap-1">
              <Numeric
                value={formatDurationShort(m.monthFocus)}
                variant="geo"
                className="text-[var(--text-2)]"
              />
              تمرکز
            </span>
          </footer>
        </section>
      ))}

      <p className="px-1 pb-1 text-[9px] leading-4 text-[var(--text-3)]">
        راهنما: نوار پایین هر روز = بار وظیفه‌های همان روز · نقطهٔ راست = عادت ثبت‌شده · نقطهٔ چپ
        = تمرکز ثبت‌شده.
      </p>
    </div>
  );
}
