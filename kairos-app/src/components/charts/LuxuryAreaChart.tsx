import { useId } from 'react';
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

export function LuxuryAreaChart({
  points,
  maxValue,
  height = 132,
  unitSuffix = '%',
}: LuxuryAreaChartProps) {
  const gradId = useId();
  if (points.length === 0) return null;

  const width = 340;
  const padX = 12;
  const padY = 14;
  const chartW = width - padX * 2;
  const chartH = height - padY * 2;
  const maxV = Math.max(1, maxValue ?? Math.max(...points.map((p) => p.value), 1));

  const coords = points.map((pt, idx) => {
    const x =
      points.length === 1
        ? width / 2
        : padX + (idx / (points.length - 1)) * chartW;
    const y = padY + chartH - (Math.max(0, pt.value) / maxV) * chartH;
    return { x, y, pt };
  });

  // Smooth cubic bezier path
  let linePath = `M ${coords[0].x} ${coords[0].y}`;
  for (let i = 1; i < coords.length; i++) {
    const prev = coords[i - 1];
    const curr = coords[i];
    const midX = (prev.x + curr.x) / 2;
    linePath += ` C ${midX} ${prev.y}, ${midX} ${curr.y}, ${curr.x} ${curr.y}`;
  }

  const areaPath = `${linePath} L ${coords[coords.length - 1].x} ${padY + chartH} L ${coords[0].x} ${padY + chartH} Z`;
  const maxPoint = coords.reduce((best, cur) => (cur.pt.value > best.pt.value ? cur : best), coords[0]);

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-[10.5px] text-[#6B6B75]">
        <span>اوج عملکرد: {maxPoint.pt.label}</span>
        <Numeric
          value={maxPoint.pt.value}
          suffix={unitSuffix}
          variant="geo"
          className="font-semibold text-[#A99BF0]"
        />
      </div>

      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="w-full overflow-visible"
        style={{ height }}
        aria-hidden="true"
      >
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#6E56CF" stopOpacity="0.34" />
            <stop offset="100%" stopColor="#6E56CF" stopOpacity="0.01" />
          </linearGradient>
        </defs>

        {/* Subtle horizontal reference lines */}
        {[0, 0.5, 1].map((t) => {
          const y = padY + chartH * t;
          return (
            <line
              key={t}
              x1={padX}
              x2={width - padX}
              y1={y}
              y2={y}
              stroke="rgba(182, 154, 239, 0.08)"
              strokeDasharray="3 4"
            />
          );
        })}

        <path d={areaPath} fill={`url(#${gradId})`} />
        <path
          d={linePath}
          fill="none"
          stroke="#9B72E6"
          strokeWidth="2.2"
          strokeLinecap="round"
        />

        {/* Highlight peak */}
        {maxPoint.pt.value > 0 && (
          <g>
            <circle
              cx={maxPoint.x}
              cy={maxPoint.y}
              r="6"
              fill="rgba(182, 154, 239, 0.22)"
            />
            <circle
              cx={maxPoint.x}
              cy={maxPoint.y}
              r="3.2"
              fill="#A99BF0"
              stroke="#0A0A0C"
              strokeWidth="1.5"
            />
          </g>
        )}
      </svg>

      {/* First / Middle / Last Axis Labels */}
      <div className="flex items-center justify-between px-1 text-[9.5px] text-[#6B6B75]">
        <span>{points[0]?.label}</span>
        {points.length > 2 && <span>{points[Math.floor(points.length / 2)]?.label}</span>}
        <span>{points[points.length - 1]?.label}</span>
      </div>
    </div>
  );
}
