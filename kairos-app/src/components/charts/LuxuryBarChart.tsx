import { useId, useMemo, useState } from 'react';
import { Numeric } from '@/components/ui/Numeric';

export interface BarChartDatum {
  key: string;
  label: string;
  value: number;
  sublabel?: string;
  highlighted?: boolean;
}

interface LuxuryBarChartProps {
  data: BarChartDatum[];
  maxValue?: number;
  unitSuffix?: string;
  height?: number;
}

/** Rounded tick values so the grid reads like a real chart axis. */
function niceTicks(max: number, count = 3): number[] {
  if (max <= 0) return [0];
  const step = Math.max(1, Math.ceil(max / count / 5) * 5);
  const ticks: number[] = [];
  for (let v = 0; v <= max + step * 0.001; v += step) ticks.push(v);
  if (ticks.length < 2) ticks.push(step);
  return ticks;
}

/**
 * Bar chart — SVG, token colours, axis ticks, rounded caps and a tap/hover
 * tooltip. Bars grow with a short staggered transition so the chart feels alive
 * without a JS frame loop.
 */
export function LuxuryBarChart({
  data,
  maxValue,
  unitSuffix = '',
  height = 168,
}: LuxuryBarChartProps) {
  const gradientId = useId().replace(/:/g, '');
  const [activeKey, setActiveKey] = useState<string | null>(null);

  const width = 340;
  const gutter = 26; // left space for the value axis
  const padTop = 14;
  const padBottom = 6;
  const plotW = width - gutter - 8;
  const plotH = height - padTop - padBottom;

  const maxValue_ = Math.max(1, maxValue ?? Math.max(...data.map((d) => d.value), 1));
  const ticks = useMemo(() => niceTicks(maxValue_), [maxValue_]);
  const axisMax = ticks[ticks.length - 1] || maxValue_;

  const slot = data.length > 0 ? plotW / data.length : plotW;
  const barW = Math.min(26, Math.max(8, slot * 0.52));
  const activeItem = data.find((d) => d.key === activeKey) ?? null;

  const yOf = (value: number) => padTop + plotH - (Math.max(0, value) / axisMax) * plotH;

  return (
    <div className="space-y-2">
      <div className="flex h-5 items-center justify-between text-[11px]">
        {activeItem ? (
          <>
            <span className="truncate text-[var(--text-2)]">{activeItem.sublabel || activeItem.label}</span>
            <Numeric
              value={activeItem.value}
              suffix={unitSuffix}
              variant="geo"
              className="font-semibold text-[var(--accent-fg)]"
            />
          </>
        ) : (
          <span className="text-[10.5px] text-[var(--text-3)]">برای جزئیات، روی ستون بزن</span>
        )}
      </div>

      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="w-full"
        style={{ height }}
        role="img"
        aria-label="نمودار ستونی"
      >
        <defs>
          <linearGradient id={`${gradientId}-peak`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--accent-fg)" stopOpacity="1" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.75" />
          </linearGradient>
          <linearGradient id={`${gradientId}-bar`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#ffffff" stopOpacity="0.26" />
            <stop offset="100%" stopColor="#ffffff" stopOpacity="0.08" />
          </linearGradient>
        </defs>

        {/* Grid + value axis */}
        {ticks.map((tick) => {
          const y = yOf(tick);
          return (
            <g key={tick}>
              <line
                x1={gutter}
                x2={width}
                y1={y}
                y2={y}
                stroke="var(--border)"
                strokeDasharray={tick === 0 ? undefined : '2 5'}
                strokeWidth={1}
              />
              <text
                x={gutter - 7}
                y={y + 3}
                textAnchor="end"
                className="num-geo"
                fill="var(--text-3)"
                fontSize="8.5"
              >
                {tick}
              </text>
            </g>
          );
        })}

        {data.map((item, index) => {
          const isActive = activeKey === item.key;
          const isToday = Boolean(item.highlighted);
          const highlighted = isActive || isToday;
          const barH = Math.max(item.value > 0 ? 6 : 3, (Math.max(0, item.value) / axisMax) * plotH);
          const x = gutter + index * slot + (slot - barW) / 2;
          const y = padTop + plotH - barH;

          return (
            <g
              key={item.key}
              onPointerEnter={() => setActiveKey(item.key)}
              onPointerDown={() => setActiveKey(item.key)}
              onClick={() => setActiveKey(isActive ? null : item.key)}
              style={{ cursor: 'pointer' }}
            >
              {/* full-height hit area */}
              <rect x={gutter + index * slot} y={padTop} width={slot} height={plotH} fill="transparent" />

              {item.value === 0 ? (
                <rect
                  x={x}
                  y={padTop + plotH - 3}
                  width={barW}
                  height={3}
                  rx={1.5}
                  fill="var(--surface-3)"
                />
              ) : (
                <rect
                  x={x}
                  y={y}
                  width={barW}
                  height={barH}
                  rx={Math.min(7, barW / 2)}
                  fill={highlighted ? `url(#${gradientId}-peak)` : `url(#${gradientId}-bar)`}
                  style={{
                    transformOrigin: `center ${padTop + plotH}px`,
                    animation: `kairos-bar-grow 520ms cubic-bezier(0.22, 1, 0.36, 1) ${index * 45}ms both`,
                  }}
                />
              )}

              {highlighted && item.value > 0 && (
                <text
                  x={x + barW / 2}
                  y={Math.max(10, y - 5)}
                  textAnchor="middle"
                  className="num-geo"
                  fill={isActive ? 'var(--accent-fg)' : 'var(--text-2)'}
                  fontSize="8.5"
                  fontWeight={600}
                >
                  {item.value}
                </text>
              )}

              {isActive && (
                <line
                  x1={x + barW / 2}
                  x2={x + barW / 2}
                  y1={padTop}
                  y2={padTop + plotH}
                  stroke="var(--accent-fg)"
                  strokeOpacity={0.28}
                  strokeWidth={1}
                />
              )}
            </g>
          );
        })}
      </svg>

      <div className="flex items-center justify-between px-0.5">
        {data.map((item) => {
          const isActive = activeKey === item.key;
          return (
            <span
              key={item.key}
              className={`flex-1 truncate text-center text-[9.5px] transition-colors ${
                isActive ? 'font-semibold text-[var(--text)]' : 'text-[var(--text-3)]'
              }`}
            >
              {item.label}
            </span>
          );
        })}
      </div>
    </div>
  );
}
