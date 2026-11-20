import { useId, useMemo, useState } from 'react';
import { Numeric } from '@/components/ui/Numeric';

export interface AreaChartPoint {
  key: string;
  label: string;
  value: number;
}

interface LuxuryAreaChartProps {
  points: AreaChartPoint[];
  maxValue?: number;
  height?: number;
  unitSuffix?: string;
}

/** Monotone-ish cubic curve: no overshoot, no fake dips between real points. */
function buildSmoothPath(coords: { x: number; y: number }[]): string {
  if (coords.length === 0) return '';
  if (coords.length === 1) return `M ${coords[0].x} ${coords[0].y}`;
  let path = `M ${coords[0].x} ${coords[0].y}`;
  for (let i = 0; i < coords.length - 1; i++) {
    const p0 = coords[i - 1] ?? coords[i];
    const p1 = coords[i];
    const p2 = coords[i + 1];
    const p3 = coords[i + 2] ?? p2;
    const t = 0.18;
    const c1x = p1.x + (p2.x - p0.x) * t;
    const c1y = p1.y + (p2.y - p0.y) * t;
    const c2x = p2.x - (p3.x - p1.x) * t;
    const c2y = p2.y - (p3.y - p1.y) * t;
    path += ` C ${c1x} ${c1y}, ${c2x} ${c2y}, ${p2.x} ${p2.y}`;
  }
  return path;
}

function niceMax(value: number): number {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const step = normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return step * magnitude;
}

/**
 * Area chart — smooth curve, gradient fill, axis grid, animated draw-in and a
 * scrubber: drag or tap anywhere on the plot to read the exact value of that day.
 */
export function LuxuryAreaChart({
  points,
  maxValue,
  height = 148,
  unitSuffix = '%',
}: LuxuryAreaChartProps) {
  const gradientId = useId().replace(/:/g, '');
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  const width = 340;
  const gutter = 26;
  const padTop = 14;
  const padBottom = 14;
  const plotW = width - gutter - 8;
  const plotH = height - padTop - padBottom;

  const axisMax = useMemo(
    () => Math.max(1, maxValue ?? niceMax(Math.max(...points.map((p) => p.value), 1))),
    [maxValue, points]
  );

  if (points.length === 0) return null;

  const coords = points.map((pt, idx) => ({
    x: gutter + (points.length === 1 ? plotW / 2 : (idx / (points.length - 1)) * plotW),
    y: padTop + plotH - (Math.max(0, Math.min(pt.value, axisMax)) / axisMax) * plotH,
    pt,
  }));

  const linePath = buildSmoothPath(coords);
  const areaPath = `${linePath} L ${coords[coords.length - 1].x} ${padTop + plotH} L ${coords[0].x} ${padTop + plotH} Z`;

  const peak = coords.reduce((best, cur) => (cur.pt.value > best.pt.value ? cur : best), coords[0]);
  const active = activeIndex !== null ? coords[activeIndex] : null;
  const shown = active ?? peak;

  const ticks = [0, 0.5, 1];

  const pickIndex = (clientX: number, target: SVGSVGElement) => {
    const box = target.getBoundingClientRect();
    const ratio = (clientX - box.left) / box.width;
    const localX = ratio * width;
    const clamped = Math.min(plotW, Math.max(0, localX - gutter));
    const idx = Math.round((clamped / plotW) * (points.length - 1));
    setActiveIndex(Math.min(points.length - 1, Math.max(0, idx)));
  };

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-[10.5px] text-[var(--text-3)]">
        <span>{active ? active.pt.label : `اوج: ${peak.pt.label}`}</span>
        <Numeric
          value={shown.pt.value}
          suffix={unitSuffix}
          variant="geo"
          className="font-semibold text-[var(--accent-fg)]"
        />
      </div>

      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="w-full touch-pan-y"
        style={{ height }}
        onPointerDown={(event) => pickIndex(event.clientX, event.currentTarget)}
        onPointerMove={(event) => {
          if (event.pointerType === 'mouse') pickIndex(event.clientX, event.currentTarget);
        }}
        onPointerLeave={() => setActiveIndex(null)}
        role="img"
        aria-label="نمودار روند"
      >
        <defs>
          <linearGradient id={`${gradientId}-fill`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.38" />
            <stop offset="70%" stopColor="var(--accent)" stopOpacity="0.06" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
          </linearGradient>
          <linearGradient id={`${gradientId}-line`} x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="var(--accent)" />
            <stop offset="100%" stopColor="var(--accent-fg)" />
          </linearGradient>
        </defs>

        {ticks.map((t) => {
          const y = padTop + plotH * t;
          return (
            <g key={t}>
              <line
                x1={gutter}
                x2={width}
                y1={y}
                y2={y}
                stroke="var(--border)"
                strokeDasharray={t === 1 ? undefined : '2 5'}
              />
              <text
                x={gutter - 7}
                y={y + 3}
                textAnchor="end"
                className="num-geo"
                fill="var(--text-3)"
                fontSize="8.5"
              >
                {Math.round(axisMax * (1 - t))}
              </text>
            </g>
          );
        })}

        <path d={areaPath} fill={`url(#${gradientId}-fill)`} />
        <path
          d={linePath}
          fill="none"
          stroke={`url(#${gradientId}-line)`}
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{
            strokeDasharray: 2000,
            strokeDashoffset: 2000,
            animation: 'kairos-line-draw 900ms cubic-bezier(0.22, 1, 0.36, 1) forwards',
          }}
        />

        {points.length <= 40 &&
          coords.map((c) => (
            <circle key={c.pt.key} cx={c.x} cy={c.y} r={1.6} fill="var(--accent-fg)" fillOpacity={0.55} />
          ))}

        {/* Active / peak marker */}
        <line
          x1={shown.x}
          x2={shown.x}
          y1={padTop}
          y2={padTop + plotH}
          stroke="var(--accent-fg)"
          strokeOpacity={active ? 0.35 : 0.15}
          strokeDasharray="3 3"
        />
        <circle cx={shown.x} cy={shown.y} r={7} fill="var(--accent)" fillOpacity={0.18} />
        <circle cx={shown.x} cy={shown.y} r={3.4} fill="var(--accent-fg)" stroke="var(--bg)" strokeWidth={1.6} />
      </svg>

      <div className="flex items-center justify-between px-1 text-[9.5px] text-[var(--text-3)]">
        <span>{points[0]?.label}</span>
        {points.length > 2 && <span>{points[Math.floor(points.length / 2)]?.label}</span>}
        <span>{points[points.length - 1]?.label}</span>
      </div>
    </div>
  );
}
