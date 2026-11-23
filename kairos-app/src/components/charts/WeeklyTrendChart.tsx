import { useId, useMemo, useState } from 'react';
import { Numeric } from '@/components/ui/Numeric';
import { useMotionLevel } from '@/lib/useAppearance';

export interface WeeklyTrendDatum {
  key: string;
  /** Weekday letter, e.g. «ش». */
  label: string;
  /** Full Jalali date for the tooltip, e.g. «۲۸ آبان». */
  dateLabel: string;
  value: number;
  /** Same weekday last week, when it exists — used for the honest delta. */
  previous?: number | null;
  isToday?: boolean;
}

interface WeeklyTrendChartProps {
  data: WeeklyTrendDatum[];
  /** Daily target, drawn as a reference line. Omit when there is no target. */
  target?: number;
  unitSuffix?: string;
  height?: number;
  /** What the value is, for the accessible label («دقیقه تمرکز»). */
  valueLabel?: string;
}

const WIDTH = 360;
const GUTTER = 30;
const PAD_TOP = 16;
const PAD_BOTTOM = 22;
const PLOT_RIGHT = 10;

/** Axis step that lands on 1/2/5 × power of ten, so the grid reads like a chart. */
function niceAxis(max: number): { top: number; ticks: number[] } {
  if (max <= 0) return { top: 4, ticks: [0, 2, 4] };
  const rough = max / 3;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const normalized = rough / magnitude;
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude;
  const top = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let value = 0; value <= top + step * 0.001; value += step) ticks.push(Math.round(value));
  return { top, ticks };
}

const fa = (value: number | string) =>
  String(value).replace(/[0-9]/g, (digit) => '۰۱۲۳۴۵۶۷۸۹'[Number(digit)]);

/**
 * The weekly trend, drawn the way a serious analytics product draws it.
 *
 * What makes it readable rather than decorative:
 *   • one baseline, three grid lines, values on the axis — no floating bars;
 *   • a **target** line, so a number has something to be judged against;
 *   • the **average** of the week as a second, quieter reference;
 *   • today is marked, not merely coloured;
 *   • the tooltip compares the same weekday last week, which is the only fair
 *     comparison in a 7-point chart;
 *   • a screen-reader table of the same numbers, so the chart is not the only
 *     way to reach the data.
 */
export function WeeklyTrendChart({
  data,
  target,
  unitSuffix = '',
  height = 176,
  valueLabel = 'مقدار',
}: WeeklyTrendChartProps) {
  const gradientId = useId().replace(/:/g, '');
  const [activeKey, setActiveKey] = useState<string | null>(null);
  const animate = useMotionLevel() !== 'standard';

  const plotW = WIDTH - GUTTER - PLOT_RIGHT;
  const plotH = height - PAD_TOP - PAD_BOTTOM;

  const values = data.map((datum) => datum.value);
  const peak = Math.max(...values, 0);
  const axisMaxSource = Math.max(peak, target ?? 0);
  const { top: axisTop, ticks } = useMemo(() => niceAxis(axisMaxSource), [axisMaxSource]);

  const total = values.reduce((sum, value) => sum + value, 0);
  const average = data.length > 0 ? total / data.length : 0;
  const bestIndex = values.indexOf(peak);
  const active = data.find((datum) => datum.key === activeKey) ?? null;

  const slot = data.length > 0 ? plotW / data.length : plotW;
  const barW = Math.min(30, Math.max(10, slot * 0.46));
  const yOf = (value: number) => PAD_TOP + plotH - (Math.max(0, value) / axisTop) * plotH;
  const xCenter = (index: number) => GUTTER + slot * index + slot / 2;

  const delta =
    active && active.previous !== null && active.previous !== undefined && active.previous > 0
      ? Math.round(((active.value - active.previous) / active.previous) * 100)
      : null;

  return (
    <div className="space-y-2" data-testid="weekly-trend">
      {/* Header: what is being shown, and the week's honest summary */}
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-[10px] text-[var(--text-3)]">
            {active ? (
              <span className="text-[var(--text-2)]">
                {active.label} · {active.dateLabel}
              </span>
            ) : (
              <span>
                مجموع هفته <Numeric value={total} variant="geo" suffix={unitSuffix} /> · میانگین روزانه{' '}
                <Numeric value={Math.round(average)} variant="geo" suffix={unitSuffix} />
              </span>
            )}
          </p>
          <p className="mt-0.5 text-[11.5px] font-semibold text-[var(--text)]">
            {active ? (
              <>
                <Numeric value={active.value} variant="geo" suffix={unitSuffix} />
                {delta !== null && (
                  <span
                    className={`num-geo ms-1.5 text-[9.5px] font-medium ${
                      delta >= 0 ? 'text-[var(--success)]' : 'text-[var(--danger-fg)]'
                    }`}
                  >
                    {delta >= 0 ? '+' : ''}
                    {fa(delta)}٪ نسبت به هفتهٔ قبل
                  </span>
                )}
              </>
            ) : peak > 0 ? (
              <>
                بهترین روز: {data[bestIndex]?.label}{' '}
                <Numeric value={peak} variant="geo" suffix={unitSuffix} />
              </>
            ) : (
              'این هفته چیزی ثبت نشده'
            )}
          </p>
        </div>
        {target !== undefined && target > 0 && (
          <span className="shrink-0 rounded-full border border-[var(--border)] px-2 py-0.5 text-[9px] text-[var(--text-3)]">
            هدف <Numeric value={target} variant="geo" suffix={unitSuffix} />
          </span>
        )}
      </div>

      <svg
        viewBox={`0 0 ${WIDTH} ${height}`}
        className="w-full touch-pan-y"
        style={{ height }}
        role="img"
        aria-label={`نمودار ${valueLabel} هفت روز اخیر. مجموع ${fa(total)}، میانگین ${fa(
          Math.round(average)
        )}.`}
        onPointerLeave={() => setActiveKey(null)}
      >
        <defs>
          <linearGradient id={`${gradientId}-bar`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--accent-fg)" stopOpacity="0.95" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.42" />
          </linearGradient>
          <linearGradient id={`${gradientId}-today`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.65" />
          </linearGradient>
        </defs>

        {/* Grid + value axis */}
        {ticks.map((tick) => {
          const y = yOf(tick);
          return (
            <g key={tick}>
              <line
                x1={GUTTER}
                x2={WIDTH - PLOT_RIGHT}
                y1={y}
                y2={y}
                stroke="var(--border)"
                strokeOpacity={tick === 0 ? 0.9 : 0.45}
                strokeDasharray={tick === 0 ? undefined : '2 4'}
              />
              <text
                x={GUTTER - 6}
                y={y + 3}
                textAnchor="end"
                className="num-geo"
                fill="var(--text-3)"
                fontSize="8.5"
              >
                {fa(tick)}
              </text>
            </g>
          );
        })}

        {/* Average reference */}
        {peak > 0 && (
          <g>
            <line
              x1={GUTTER}
              x2={WIDTH - PLOT_RIGHT}
              y1={yOf(average)}
              y2={yOf(average)}
              stroke="var(--text-3)"
              strokeOpacity="0.5"
              strokeWidth="1"
              strokeDasharray="5 3"
            />
            <text
              x={WIDTH - PLOT_RIGHT}
              y={yOf(average) - 3}
              textAnchor="end"
              fontSize="7.5"
              fill="var(--text-3)"
            >
              میانگین
            </text>
          </g>
        )}

        {/* Target reference — the line that makes the bars mean something */}
        {target !== undefined && target > 0 && (
          <g>
            <line
              x1={GUTTER}
              x2={WIDTH - PLOT_RIGHT}
              y1={yOf(target)}
              y2={yOf(target)}
              stroke="var(--accent-fg)"
              strokeOpacity="0.55"
              strokeWidth="1.2"
              strokeDasharray="6 4"
            />
            <circle cx={WIDTH - PLOT_RIGHT - 2} cy={yOf(target)} r="2" fill="var(--accent-fg)" />
          </g>
        )}

        {/* Active column spotlight */}
        {active && (
          <rect
            x={xCenter(data.findIndex((datum) => datum.key === active.key)) - slot / 2}
            y={PAD_TOP - 6}
            width={slot}
            height={plotH + 6}
            fill="var(--accent-fg)"
            opacity="0.07"
            rx="6"
          />
        )}

        {/* Bars */}
        {data.map((datum, index) => {
          const height_ = Math.max(datum.value > 0 ? 2.5 : 0, PAD_TOP + plotH - yOf(datum.value));
          const x = xCenter(index) - barW / 2;
          const y = PAD_TOP + plotH - height_;
          const isActive = active?.key === datum.key;
          return (
            <g key={datum.key}>
              {/* Touch/hover hit area — 44px tall targets even for a short bar */}
              <rect
                x={GUTTER + slot * index}
                y={PAD_TOP - 6}
                width={slot}
                height={plotH + 6}
                fill="transparent"
                onPointerEnter={() => setActiveKey(datum.key)}
                onPointerDown={() => setActiveKey(datum.key)}
                onPointerMove={() => setActiveKey(datum.key)}
                style={{ cursor: 'pointer' }}
              />
              {datum.value > 0 && (
                <rect
                  x={x}
                  y={y}
                  width={barW}
                  height={height_}
                  rx={Math.min(6, barW / 2)}
                  fill={
                    datum.isToday
                      ? `url(#${gradientId}-today)`
                      : `url(#${gradientId}-bar)`
                  }
                  opacity={isActive || !active ? 1 : 0.55}
                  style={
                    animate
                      ? {
                          transformOrigin: `${xCenter(index)}px ${PAD_TOP + plotH}px`,
                          animation: `kairos-bar-grow 520ms cubic-bezier(0.22,1,0.36,1) ${
                            index * 45
                          }ms both`,
                        }
                      : undefined
                  }
                />
              )}
              {datum.isToday && (
                <circle
                  cx={xCenter(index)}
                  cy={PAD_TOP + plotH + 7}
                  r="1.8"
                  fill="var(--accent-fg)"
                />
              )}
              <text
                x={xCenter(index)}
                y={height - 4}
                textAnchor="middle"
                fontSize="9"
                fill={datum.isToday ? 'var(--accent-fg)' : 'var(--text-3)'}
                className="num-geo"
              >
                {datum.label}
              </text>
            </g>
          );
        })}
      </svg>

      {/* The same numbers, for anyone who cannot use the picture */}
      <table className="sr-only">
        <caption>{valueLabel} هفت روز اخیر</caption>
        <thead>
          <tr>
            <th>روز</th>
            <th>مقدار</th>
          </tr>
        </thead>
        <tbody>
          {data.map((datum) => (
            <tr key={datum.key}>
              <td>{datum.dateLabel}</td>
              <td>{datum.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
