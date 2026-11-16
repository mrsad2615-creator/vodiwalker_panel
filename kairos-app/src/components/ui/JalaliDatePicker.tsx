import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import {
  addDays,
  dateKeyToJalaali,
  getJalaaliMonthDaysCount,
  getJalaaliMonthName,
  getPersianWeekdayIndex,
  getTodayKey,
  jalaaliToDateKey,
  PERSIAN_WEEKDAYS_SHORT,
  shiftJalaaliMonth,
} from '@/lib/date';
import { Numeric } from './Numeric';

interface JalaliDatePickerProps {
  value: string | null;
  onChange: (dateKey: string | null) => void;
  allowClear?: boolean;
}

export function JalaliDatePicker({
  value,
  onChange,
  allowClear = true,
}: JalaliDatePickerProps) {
  const todayKey = getTodayKey();
  const initialJ = dateKeyToJalaali(value ?? todayKey);
  const [viewYear, setViewYear] = useState(initialJ.jy);
  const [viewMonth, setViewMonth] = useState(initialJ.jm);

  const daysCount = getJalaaliMonthDaysCount(viewYear, viewMonth);
  const firstDayKey = jalaaliToDateKey(viewYear, viewMonth, 1);
  const leadingBlanks = getPersianWeekdayIndex(firstDayKey);

  const handleShift = (delta: number) => {
    const shifted = shiftJalaaliMonth(viewYear, viewMonth, delta);
    setViewYear(shifted.jy);
    setViewMonth(shifted.jm);
  };

  return (
    <div className="rounded-2xl border border-[#B69AEF]/14 bg-[#100F16] p-3">
      {/* Quick Presets */}
      <div className="mb-3 flex flex-wrap items-center gap-1.5">
        <button
          type="button"
          onClick={() => {
            onChange(todayKey);
            const j = dateKeyToJalaali(todayKey);
            setViewYear(j.jy);
            setViewMonth(j.jm);
          }}
          className={`rounded-lg px-2.5 py-1 text-[11px] font-medium transition ${
            value === todayKey
              ? 'bg-[#8052D6] text-[#F4F1F8]'
              : 'bg-[#1B1924] text-[#A79EB9] hover:text-[#F4F1F8]'
          }`}
        >
          امروز
        </button>
        <button
          type="button"
          onClick={() => {
            const tmr = addDays(todayKey, 1);
            onChange(tmr);
            const j = dateKeyToJalaali(tmr);
            setViewYear(j.jy);
            setViewMonth(j.jm);
          }}
          className={`rounded-lg px-2.5 py-1 text-[11px] font-medium transition ${
            value === addDays(todayKey, 1)
              ? 'bg-[#8052D6] text-[#F4F1F8]'
              : 'bg-[#1B1924] text-[#A79EB9] hover:text-[#F4F1F8]'
          }`}
        >
          فردا
        </button>
        <button
          type="button"
          onClick={() => {
            const nxt = addDays(todayKey, 7);
            onChange(nxt);
            const j = dateKeyToJalaali(nxt);
            setViewYear(j.jy);
            setViewMonth(j.jm);
          }}
          className={`rounded-lg px-2.5 py-1 text-[11px] font-medium transition ${
            value === addDays(todayKey, 7)
              ? 'bg-[#8052D6] text-[#F4F1F8]'
              : 'bg-[#1B1924] text-[#A79EB9] hover:text-[#F4F1F8]'
          }`}
        >
          هفته بعد
        </button>
        {allowClear && value && (
          <button
            type="button"
            onClick={() => onChange(null)}
            className="mr-auto rounded-lg px-2 py-1 text-[11px] text-[#6E6680] hover:text-[#EB9BAB]"
          >
            حذف تاریخ
          </button>
        )}
      </div>

      {/* Month Navigator */}
      <div className="mb-2.5 flex items-center justify-between">
        <button
          type="button"
          onClick={() => handleShift(-1)}
          aria-label="ماه قبل"
          className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#1B1924] text-[#A79EB9] hover:text-[#F4F1F8]"
        >
          <ChevronRight className="h-4 w-4" />
        </button>
        <div className="flex items-center gap-1.5 text-xs font-semibold text-[#F4F1F8]">
          <span>{getJalaaliMonthName(viewMonth)}</span>
          <Numeric value={viewYear} variant="geo" className="text-[#B69AEF]" />
        </div>
        <button
          type="button"
          onClick={() => handleShift(1)}
          aria-label="ماه بعد"
          className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#1B1924] text-[#A79EB9] hover:text-[#F4F1F8]"
        >
          <ChevronLeft className="h-4 w-4" />
        </button>
      </div>

      {/* Weekdays */}
      <div className="mb-1 grid grid-cols-7 text-center text-[10px] font-medium text-[#6E6680]">
        {PERSIAN_WEEKDAYS_SHORT.map((w) => (
          <div key={w} className="py-1">
            {w}
          </div>
        ))}
      </div>

      {/* Days Grid */}
      <div className="grid grid-cols-7 gap-1">
        {Array.from({ length: leadingBlanks }).map((_, i) => (
          <div key={`blank_${i}`} />
        ))}
        {Array.from({ length: daysCount }, (_, i) => {
          const day = i + 1;
          const dk = jalaaliToDateKey(viewYear, viewMonth, day);
          const isSelected = value === dk;
          const isToday = dk === todayKey;

          return (
            <button
              key={dk}
              type="button"
              onClick={() => onChange(dk)}
              className={`flex h-8 items-center justify-center rounded-lg text-xs transition ${
                isSelected
                  ? 'bg-[#8052D6] font-bold text-[#F4F1F8] shadow-[0_0_12px_rgba(128,82,214,0.55)]'
                  : isToday
                  ? 'border border-[#B69AEF]/50 bg-[#241936] text-[#B69AEF]'
                  : 'text-[#F4F1F8]/85 hover:bg-[#241936]/70'
              }`}
            >
              <Numeric value={day} variant="geo" className="text-xs" />
            </button>
          );
        })}
      </div>
    </div>
  );
}
