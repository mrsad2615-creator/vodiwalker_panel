import { useEffect, useMemo, useRef, useState } from 'react';
import { Columns3, Rows3 } from '@/components/ui/icons';
import type { HeatmapCellState, HeatmapDayCell } from '@/lib/analytics';
import { getJalaaliMonthName, PERSIAN_WEEKDAYS_SHORT } from '@/lib/date';
import { Numeric } from '@/components/ui/Numeric';

export type HeatmapOrientation = 'vertical' | 'horizontal';

interface ActivityHeatmapProps {
  cells: HeatmapDayCell[];
  selectedDateKey: string | null;
  onSelectDay: (cell: HeatmapDayCell) => void;
  todayKey: string;
  orientation?: HeatmapOrientation;
  onOrientationChange?: (next: HeatmapOrientation) => void;
  showOrientationToggle?: boolean;
}

const STATE_STYLES: Record<
  HeatmapCellState,
  { bg: string; border: string; text: string; glow?: string }
> = {
  future: {
    bg: 'rgba(21, 20, 27, 0.34)',
    border: 'rgba(163, 140, 255, 0.04)',
    text: '#474154',
  },
  unlogged: {
    bg: '#0E0E11',
    border: 'rgba(163, 140, 255, 0.1)',
    text: '#6B6B75',
  },
  missed: {
    bg: '#1A1A1E',
    border: 'rgba(217, 119, 139, 0.36)',
    text: '#EF9095',
  },
  level1: {
    bg: '#1F1940',
    border: 'rgba(109, 74, 255, 0.38)',
    text: '#D8CAF4',
  },
  level2: {
    bg: '#4A2BC7',
    border: 'rgba(109, 74, 255, 0.56)',
    text: '#ECECEF',
  },
  level3: {
    bg: '#6D4AFF',
    border: 'rgba(163, 140, 255, 0.5)',
    text: '#ECECEF',
    glow: '0 0 8px rgba(109, 74, 255, 0.32)',
  },
  level4: {
    bg: '#B9A8FF',
    border: 'rgba(244, 241, 248, 0.62)',
    text: '#0A0A0C',
    glow: '0 0 10px rgba(163, 140, 255, 0.45)',
  },
};

export function ActivityHeatmap({
  cells,
  selectedDateKey,
  onSelectDay,
  todayKey,
  orientation: controlledOrientation,
  onOrientationChange,
  showOrientationToggle = true,
}: ActivityHeatmapProps) {
  const [internalOrientation, setInternalOrientation] = useState<HeatmapOrientation>('vertical');
  const orientation = controlledOrientation ?? internalOrientation;

  const setOrientation = (next: HeatmapOrientation) => {
    if (onOrientationChange) {
      onOrientationChange(next);
    } else {
      setInternalOrientation(next);
    }
  };

  const horizontalScrollRef = useRef<HTMLDivElement>(null);
  const verticalMonthRefs = useRef<Record<number, HTMLDivElement | null>>({});

  // Horizontal 53-week columns (classic GitHub panorama)
  const { columns, monthHeaders, todayColIndex } = useMemo(() => {
    if (cells.length === 0) {
      return { columns: [], monthHeaders: [], todayColIndex: 0 };
    }
    const cols: (HeatmapDayCell | null)[][] = [];
    let currentCol: (HeatmapDayCell | null)[] = Array(7).fill(null);

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

  // Vertical 12-month structured calendar blocks (thumb-friendly portrait layout)
  const verticalMonths = useMemo(() => {
    const groups = new Map<
      number,
      {
        jm: number;
        jy: number;
        name: string;
        activeCount: number;
        days: HeatmapDayCell[];
        leadingBlanks: number;
      }
    >();

    for (const cell of cells) {
      let g = groups.get(cell.jm);
      if (!g) {
        g = {
          jm: cell.jm,
          jy: cell.jy,
          name: getJalaaliMonthName(cell.jm),
          activeCount: 0,
          days: [],
          leadingBlanks: cell.weekdayIdx,
        };
        groups.set(cell.jm, g);
      }
      g.days.push(cell);
      if (
        cell.state === 'level1' ||
        cell.state === 'level2' ||
        cell.state === 'level3' ||
        cell.state === 'level4'
      ) {
        g.activeCount += 1;
      }
    }

    return Array.from(groups.values()).sort((a, b) => a.jm - b.jm);
  }, [cells]);

  const activeFocusMonth = useMemo(() => {
    const selected = cells.find((c) => c.dateKey === selectedDateKey);
    if (selected) return selected.jm;
    const today = cells.find((c) => c.dateKey === todayKey);
    if (today) return today.jm;
    return verticalMonths[verticalMonths.length - 1]?.jm ?? 1;
  }, [cells, selectedDateKey, todayKey, verticalMonths]);

  const [focusedMonth, setFocusedMonth] = useState<number>(activeFocusMonth);

  useEffect(() => {
    setFocusedMonth(activeFocusMonth);
  }, [activeFocusMonth]);

  // Smoothly scroll horizontal grid near today
  useEffect(() => {
    if (orientation !== 'horizontal') return;
    const el = horizontalScrollRef.current;
    if (!el || columns.length === 0) return;
    const cellPitch = 15;
    const targetLeft = Math.max(0, todayColIndex * cellPitch - el.clientWidth + 90);
    el.scrollLeft = targetLeft;
  }, [orientation, columns.length, todayColIndex]);

  // Scroll vertical month into view when user taps a month pill
  const scrollToMonth = (jm: number) => {
    setFocusedMonth(jm);
    const node = verticalMonthRefs.current[jm];
    if (node && typeof node.scrollIntoView === 'function') {
      node.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  };

  const monthByCol = useMemo(() => {
    const map = new Map<number, string>();
    monthHeaders.forEach((h) => map.set(h.colIndex, h.label));
    return map;
  }, [monthHeaders]);

  return (
    <div className="space-y-3">
      {/* Orientation Control Strip (Vertical Portrait vs Horizontal 365-Day) */}
      {showOrientationToggle && (
        <div className="flex items-center justify-between gap-2">
          <span className="text-[10.5px] font-medium text-[#9C9CA6]">
            {orientation === 'vertical'
              ? 'نمای عمودی (خوانا و لمسی)'
              : 'نمای افقی سالانه (365 روز)'}
          </span>
          <div className="inline-flex items-center gap-0.5 rounded-xl border border-[#B9A8FF]/14 bg-[#0A0A0C]/80 p-0.5">
            <button
              type="button"
              onClick={() => setOrientation('vertical')}
              aria-label="نمای عمودی تقویم"
              className={`flex items-center gap-1 rounded-lg px-2.5 py-1 text-[10.5px] font-semibold transition ${
                orientation === 'vertical'
                  ? 'bg-[#6D4AFF] text-[#ECECEF]'
                  : 'text-[#9C9CA6] hover:text-[#ECECEF]'
              }`}
            >
              <Rows3 className="h-3 w-3" />
              <span>عمودی</span>
            </button>
            <button
              type="button"
              onClick={() => setOrientation('horizontal')}
              aria-label="نمای افقی تقویم"
              className={`flex items-center gap-1 rounded-lg px-2.5 py-1 text-[10.5px] font-semibold transition ${
                orientation === 'horizontal'
                  ? 'bg-[#6D4AFF] text-[#ECECEF]'
                  : 'text-[#9C9CA6] hover:text-[#ECECEF]'
              }`}
            >
              <Columns3 className="h-3 w-3" />
              <span>افقی</span>
            </button>
          </div>
        </div>
      )}

      {/* ===================== VERTICAL PORTRAIT MODE ===================== */}
      {orientation === 'vertical' ? (
        <div className="space-y-2.5">
          {/* Quick Month Jump Strip */}
          <div className="no-scrollbar flex items-center gap-1 overflow-x-auto pb-0.5">
            {verticalMonths.map((m) => (
              <button
                key={m.jm}
                type="button"
                onClick={() => scrollToMonth(m.jm)}
                className={`shrink-0 rounded-lg border px-2.5 py-1 text-[10.5px] font-medium transition ${
                  focusedMonth === m.jm
                    ? 'border-[#B9A8FF]/50 bg-[#1C1C21] text-[#ECECEF]'
                    : 'border-[#B9A8FF]/10 bg-[#0C0C0F] text-[#9C9CA6] hover:text-[#ECECEF]'
                }`}
              >
                {m.name}
              </button>
            ))}
          </div>

          {/* Sticky Weekday Header + Vertical Scrollable Months */}
          <div className="overflow-hidden rounded-2xl border border-[#B9A8FF]/12 bg-[#0A0A0C]/90">
            <div className="grid grid-cols-7 border-b border-[#B9A8FF]/10 bg-[#13111C] px-2.5 py-2 text-center text-[10px] font-semibold text-[#9C9CA6]">
              {PERSIAN_WEEKDAYS_SHORT.map((dayLabel) => (
                <span key={dayLabel}>{dayLabel}</span>
              ))}
            </div>

            <div className="max-h-[292px] space-y-4 overflow-y-auto p-2.5">
              {verticalMonths.map((month) => (
                <div
                  key={month.jm}
                  ref={(el) => {
                    verticalMonthRefs.current[month.jm] = el;
                  }}
                  className="space-y-2"
                >
                  <div className="flex items-center justify-between px-0.5">
                    <span className="text-[11px] font-bold text-[#ECECEF]">
                      {month.name}{' '}
                      <Numeric value={month.jy} className="font-normal text-[#6B6B75]" />
                    </span>
                    <span className="text-[10px] text-[#9C9CA6]">
                      <Numeric
                        value={month.activeCount}
                        className="font-semibold text-[#B9A8FF]"
                      />{' '}
                      روز فعال
                    </span>
                  </div>

                  <div className="grid grid-cols-7 gap-1.5">
                    {Array.from({ length: month.leadingBlanks }).map((_, idx) => (
                      <div key={`blank_${month.jm}_${idx}`} className="h-8" />
                    ))}

                    {month.days.map((cell) => {
                      const style = STATE_STYLES[cell.state];
                      const isSelected = cell.dateKey === selectedDateKey;
                      const isToday = cell.dateKey === todayKey;

                      return (
                        <button
                          key={cell.dateKey}
                          type="button"
                          onClick={() => {
                            setFocusedMonth(cell.jm);
                            onSelectDay(cell);
                          }}
                          title={`${cell.jd} ${month.name} ${cell.jy} — امتیاز ${cell.score}%`}
                          aria-label={`${cell.jd} ${month.name} ${cell.jy}`}
                          className={`relative flex h-8 items-center justify-center rounded-lg transition-transform duration-100 active:scale-95 focus:outline-none ${
                            isSelected
                              ? 'z-10 ring-2 ring-[#ECECEF]'
                              : isToday
                              ? 'ring-1.5 ring-[#B9A8FF]'
                              : ''
                          }`}
                          style={{
                            backgroundColor: style.bg,
                            border: `1px solid ${style.border}`,
                            boxShadow: style.glow,
                          }}
                        >
                          <span
                            className="num-geo text-[11px] font-semibold leading-none"
                            style={{ color: style.text }}
                          >
                            {cell.jd}
                          </span>
                          {cell.state === 'missed' && (
                            <span className="absolute bottom-1 h-[3px] w-[3px] rounded-full bg-[#E5484D]" />
                          )}
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : (
        /* ===================== HORIZONTAL 365-DAY PANORAMA ===================== */
        <div className="flex items-start gap-2">
          {/* Weekday Axis */}
          <div className="flex flex-col gap-[3px] pt-[22px] text-[9px] font-medium text-[#6B6B75]">
            {PERSIAN_WEEKDAYS_SHORT.map((label, idx) => (
              <div
                key={label}
                className="flex h-[12px] w-4 items-center justify-center leading-none"
              >
                {idx % 2 === 0 ? label : ''}
              </div>
            ))}
          </div>

          <div ref={horizontalScrollRef} dir="ltr" className="flex-1 overflow-x-auto pb-2">
            <div className="inline-flex gap-[3px]">
              {columns.map((col, colIdx) => {
                const monthName = monthByCol.get(colIdx);
                return (
                  <div key={colIdx} className="flex flex-col gap-[3px]">
                    <div className="relative h-[18px] w-[12px]">
                      {monthName && (
                        <span
                          dir="rtl"
                          className="absolute top-0 left-0 whitespace-nowrap text-[9.5px] font-medium text-[#9C9CA6]"
                        >
                          {monthName}
                        </span>
                      )}
                    </div>

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
                              ? 'z-10 scale-125 ring-2 ring-[#ECECEF]'
                              : isToday
                              ? 'ring-1 ring-[#B9A8FF]'
                              : ''
                          }`}
                          style={{
                            backgroundColor: style.bg,
                            border: `1px solid ${style.border}`,
                            boxShadow: style.glow,
                          }}
                        >
                          {cell.state === 'missed' && (
                            <span className="absolute inset-0 m-auto h-[3px] w-[3px] rounded-full bg-[#E5484D]" />
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
      )}

      {/* Explicit Legend Separating Unlogged vs Missed vs Purple Mastery Scale */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#B9A8FF]/10 pt-2.5 text-[10px] text-[#9C9CA6]">
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
              <span className="h-[3px] w-[3px] rounded-full bg-[#E5484D]" />
            </span>
            انجام‌نشده
          </span>
        </div>

        <div className="flex items-center gap-1.5">
          <span className="text-[#6B6B75]">کم</span>
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
          <span className="text-[#B9A8FF]">کامل</span>
          <span className="mr-1 text-[#6B6B75]">
            (<Numeric value={cells.length} variant="geo" className="text-[10px]" /> روز)
          </span>
        </div>
      </div>
    </div>
  );
}
