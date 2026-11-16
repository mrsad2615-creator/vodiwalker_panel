import { useEffect, useMemo, useRef } from 'react';
import type { HeatmapCellState, HeatmapDayCell } from '@/lib/analytics';
import { getJalaaliMonthName, PERSIAN_WEEKDAYS_SHORT } from '@/lib/date';
import { Numeric } from '@/components/ui/Numeric';

interface ActivityHeatmapProps {
  cells: HeatmapDayCell[];
  selectedDateKey: string | null;
  onSelectDay: (cell: HeatmapDayCell) => void;
  todayKey: string;
}

const STATE_STYLES: Record<
  HeatmapCellState,
  { bg: string; border: string; glow?: string }
> = {
  future: {
    bg: 'rgba(21, 20, 27, 0.32)',
    border: 'rgba(182, 154, 239, 0.03)',
  },
  unlogged: {
    bg: '#100F16',
    border: 'rgba(182, 154, 239, 0.09)',
  },
  missed: {
    bg: '#231624',
    border: 'rgba(217, 119, 139, 0.32)',
  },
  level1: {
    bg: '#2C1C48',
    border: 'rgba(128, 82, 214, 0.35)',
  },
  level2: {
    bg: '#523194',
    border: 'rgba(128, 82, 214, 0.52)',
  },
  level3: {
    bg: '#8052D6',
    border: 'rgba(182, 154, 239, 0.45)',
    glow: '0 0 8px rgba(128, 82, 214, 0.32)',
  },
  level4: {
    bg: '#B69AEF',
    border: 'rgba(244, 241, 248, 0.55)',
    glow: '0 0 10px rgba(182, 154, 239, 0.48)',
  },
};

export function ActivityHeatmap({
  cells,
  selectedDateKey,
  onSelectDay,
  todayKey,
}: ActivityHeatmapProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  // Organize 365/366 cells into week columns (each column has 7 rows: Sat=0 .. Fri=6)
  const { columns, monthHeaders, todayColIndex } = useMemo(() => {
    if (cells.length === 0) {
      return { columns: [], monthHeaders: [], todayColIndex: 0 };
    }
    const cols: (HeatmapDayCell | null)[][] = [];
    let currentCol: (HeatmapDayCell | null)[] = Array(7).fill(null);

    // Leading blanks before Farvardin 1
    let currentMonthSeen = -1;
    const headers: { colIndex: number; label: string }[] = [];
    let todayCol = 0;

    cells.forEach((cell, idx) => {
      if (idx > 0 && cell.weekdayIdx === 0) {
        cols.push(currentCol);
        currentCol = Array(7).fill(null);
      }
      currentCol[cell.weekdayIdx] = cell;

      if (cell.jm !== currentMonthSeen && cell.jd <= 7) {
        currentMonthSeen = cell.jm;
        headers.push({ colIndex: cols.length, label: getJalaaliMonthName(cell.jm) });
      }
      if (cell.dateKey === todayKey) {
        todayCol = cols.length;
      }
    });
    cols.push(currentCol);

    return { columns: cols, monthHeaders: headers, todayColIndex: todayCol };
  }, [cells, todayKey]);

  // Smoothly scroll viewport near today's column when year changes
  useEffect(() => {
    const el = scrollRef.current;
    if (!el || columns.length === 0) return;
    // Scroll container is LTR so chronological months flow cleanly left-to-right (Farvardin -> Esfand)
    const cellPitch = 15; // 12px + 3px gap
    const targetLeft = Math.max(0, todayColIndex * cellPitch - el.clientWidth + 90);
    el.scrollLeft = targetLeft;
  }, [columns.length, todayColIndex]);

  const monthByCol = useMemo(() => {
    const map = new Map<number, string>();
    monthHeaders.forEach((h) => map.set(h.colIndex, h.label));
    return map;
  }, [monthHeaders]);

  return (
    <div className="space-y-3">
      {/* Scrollable 365-Day Grid */}
      <div className="flex items-start gap-2">
        {/* Weekday Axis */}
        <div className="flex flex-col gap-[3px] pt-[22px] text-[9px] font-medium text-[#6E6680]">
          {PERSIAN_WEEKDAYS_SHORT.map((label, idx) => (
            <div
              key={label}
              className="flex h-[12px] w-4 items-center justify-center leading-none"
            >
              {idx % 2 === 0 ? label : ''}
            </div>
          ))}
        </div>

        {/* Chronological Grid Container (LTR inside RTL for authentic left->right contribution timeline) */}
        <div
          ref={scrollRef}
          dir="ltr"
          className="flex-1 overflow-x-auto pb-2"
        >
          <div className="inline-flex gap-[3px]">
            {columns.map((col, colIdx) => {
              const monthName = monthByCol.get(colIdx);
              return (
                <div key={colIdx} className="flex flex-col gap-[3px]">
                  {/* Month Header Row */}
                  <div className="relative h-[18px] w-[12px]">
                    {monthName && (
                      <span
                        dir="rtl"
                        className="absolute top-0 left-0 whitespace-nowrap text-[9.5px] font-medium text-[#A79EB9]"
                      >
                        {monthName}
                      </span>
                    )}
                  </div>

                  {/* 7 Days in Column */}
                  {col.map((cell, rowIdx) => {
                    if (!cell) {
                      return <div key={`empty_${rowIdx}`} className="h-[12px] w-[12px]" />;
                    }
                    const style = STATE_STYLES[cell.state];
                    const isSelected = cell.dateKey === selectedDateKey;
                    const isToday = cell.dateKey === todayKey;

                    return (
                      <button
                        key={cell.dateKey}
                        type="button"
                        onClick={() => onSelectDay(cell)}
                        title={`${cell.jd} ${getJalaaliMonthName(cell.jm)} ${cell.jy} — امتیاز ${cell.score}%`}
                        aria-label={`${cell.jd} ${getJalaaliMonthName(cell.jm)} ${cell.jy}`}
                        className={`relative h-[12px] w-[12px] rounded-[3px] transition-transform duration-100 hover:scale-125 focus:outline-none ${
                          isSelected
                            ? 'z-10 scale-125 ring-2 ring-[#F4F1F8]'
                            : isToday
                            ? 'ring-1 ring-[#B69AEF]'
                            : ''
                        }`}
                        style={{
                          backgroundColor: style.bg,
                          border: `1px solid ${style.border}`,
                          boxShadow: style.glow,
                        }}
                      >
                        {cell.state === 'missed' && (
                          <span className="absolute inset-0 m-auto h-[3px] w-[3px] rounded-full bg-[#D9778B]" />
                        )}
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Explicit Legend Separating Unlogged vs Missed vs Purple Mastery Scale */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#B69AEF]/10 pt-2.5 text-[10px] text-[#A79EB9]">
        <div className="flex items-center gap-3">
          <span className="inline-flex items-center gap-1.5">
            <span
              className="inline-block h-[10px] w-[10px] rounded-[2.5px]"
              style={{
                backgroundColor: STATE_STYLES.unlogged.bg,
                border: `1px solid ${STATE_STYLES.unlogged.border}`,
              }}
            />
            ثبت‌نشده
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span
              className="relative inline-flex h-[10px] w-[10px] items-center justify-center rounded-[2.5px]"
              style={{
                backgroundColor: STATE_STYLES.missed.bg,
                border: `1px solid ${STATE_STYLES.missed.border}`,
              }}
            >
              <span className="h-[3px] w-[3px] rounded-full bg-[#D9778B]" />
            </span>
            انجام‌نشده
          </span>
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-[#6E6680]">کم</span>
          {(['level1', 'level2', 'level3', 'level4'] as const).map((lvl) => (
            <span
              key={lvl}
              className="inline-block h-[10px] w-[10px] rounded-[2.5px]"
              style={{
                backgroundColor: STATE_STYLES[lvl].bg,
                border: `1px solid ${STATE_STYLES[lvl].border}`,
              }}
            />
          ))}
          <span className="text-[#B69AEF]">کامل</span>
          <span className="mr-1 text-[#6E6680]">
            (<Numeric value={cells.length} variant="geo" className="text-[10px]" /> روز)
          </span>
        </div>
      </div>
    </div>
  );
}
