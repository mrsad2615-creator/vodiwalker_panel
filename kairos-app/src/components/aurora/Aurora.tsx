import { useEffect, useId, useRef, useState, type ReactNode } from 'react';

/**
 * Aurora primitives — the shapes the dashboard is allowed to use.
 *
 * Every one of them draws *real* data and animates on mount. Nothing here
 * fabricates a value: if the series is empty the shape is an honest flat line,
 * not a decorative squiggle.
 */

// Read from the ladder, never hard-coded: the shapes must follow the palette.
const VIOLET = 'var(--v-400)';
const LILAC = 'var(--v-200)';

/* ── Count-up ───────────────────────────────────────────────────────────────
   Numbers that jump read as data; numbers that arrive read as a story. The
   animation is short (700ms), eased, and always lands exactly on the real value. */
export function useCountUp(target: number, duration = 700): number {
  const [value, setValue] = useState(0);
  const from = useRef(0);

  useEffect(() => {
    const start = performance.now();
    const origin = from.current;
    const delta = target - origin;
    let frame = 0;

    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      // easeOutCubic
      const eased = 1 - Math.pow(1 - t, 3);
      setValue(origin + delta * eased);
      if (t < 1) frame = requestAnimationFrame(tick);
      else from.current = target;
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target, duration]);

  return value;
}

export function CountUp({
  value,
  className = '',
  suffix = '',
}: {
  value: number;
  className?: string;
  suffix?: string;
}) {
  const animated = useCountUp(value);
  return (
    <span className={`num-geo tabular-nums ${className}`} aria-label={String(value)}>
      {Math.round(animated)}
      {suffix}
    </span>
  );
}

/* ── Ring ───────────────────────────────────────────────────────────────────
   One ring, always the same: violet gradient stroke on a faint track. It is the
   only circular thing in the product, which is why it can carry the hero. */
export function Ring({
  value,
  size = 116,
  stroke = 9,
  children,
}: {
  /** 0…1 */
  value: number;
  size?: number;
  stroke?: number;
  children?: ReactNode;
}) {
  const id = useId().replace(/:/g, '');
  const clamped = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0));
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const dash = c * (1 - clamped);

  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <defs>
          <linearGradient id={`ring-${id}`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={LILAC} />
            <stop offset="55%" stopColor={VIOLET} />
            <stop offset="100%" stopColor="var(--v-600)" />
          </linearGradient>
          <filter id={`glow-${id}`} x="-40%" y="-40%" width="180%" height="180%">
            <feGaussianBlur stdDeviation="3.4" result="b" />
            <feMerge>
              <feMergeNode in="b" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="color-mix(in srgb, var(--v-200) 13%, transparent)"
          strokeWidth={stroke}
        />
        {clamped > 0 && (
          <circle
            className="ring-progress"
            cx={size / 2}
            cy={size / 2}
            r={r}
            fill="none"
            stroke={`url(#ring-${id})`}
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={c}
            strokeDashoffset={dash}
            filter={`url(#glow-${id})`}
            transform={`rotate(-90 ${size / 2} ${size / 2})`}
            style={{ ['--dash' as string]: `${c}` }}
          />
        )}
      </svg>
      <div className="absolute inset-0 grid place-items-center">{children}</div>
    </div>
  );
}

/* ── Sparkline ──────────────────────────────────────────────────────────────
   Fourteen days, drawn as one line with an area beneath it, ignoring leading and
   trailing empty days so a fresh install does not open with a dead chart. */
export function Sparkline({
  values,
  height = 54,
  width = 148,
}: {
  values: number[];
  height?: number;
  width?: number;
}) {
  const id = useId().replace(/:/g, '');
  const first = values.findIndex((v) => v > 0);
  const lastIndex = values.reduce((acc, v, i) => (v > 0 ? i : acc), -1);
  const window = first === -1 ? [] : values.slice(first, lastIndex + 1);
  const series = window.length >= 2 ? window : [0, ...window];
  const max = Math.max(1, ...series);

  const stepX = width / Math.max(1, series.length - 1);
  const points = series.map((v, i) => {
    const x = i * stepX;
    const y = height - 6 - (v / max) * (height - 14);
    return [x, y] as const;
  });

  // Smooth the line with a light cardinal spline: straight segments read as a
  // chart, curves read as a pulse.
  const path = points.reduce((acc, [x, y], i) => {
    if (i === 0) return `M ${x} ${y}`;
    const [px, py] = points[i - 1];
    const cx = (px + x) / 2;
    return `${acc} C ${cx} ${py}, ${cx} ${y}, ${x} ${y}`;
  }, '');

  const area = `${path} L ${width} ${height} L 0 ${height} Z`;

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden="true">
      <defs>
        <linearGradient id={`spark-${id}`} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stopColor="var(--v-500)" />
          <stop offset="100%" stopColor={LILAC} />
        </linearGradient>
        <linearGradient id={`fill-${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--v-400)" stopOpacity="0.35" />
          <stop offset="100%" stopColor="var(--v-400)" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#fill-${id})`} />
      <path
        className="spark-line"
        d={path}
        fill="none"
        stroke={`url(#spark-${id})`}
        strokeWidth={2}
        strokeLinecap="round"
        pathLength={1}
      />
      {points.length > 0 && (
        <circle
          cx={points[points.length - 1][0]}
          cy={points[points.length - 1][1]}
          r={2.6}
          fill={LILAC}
          filter="none"
        />
      )}
    </svg>
  );
}

/* ── MiniBars ───────────────────────────────────────────────────────────────
   Seven days of focus, each bar growing from the floor with its own delay. */
export function MiniBars({
  values,
  height = 44,
  labels,
}: {
  values: number[];
  height?: number;
  labels?: string[];
}) {
  const max = Math.max(1, ...values);
  return (
    <div className="flex items-end gap-1.5" style={{ height }} aria-hidden="true">
      {values.map((v, i) => {
        const ratio = v / max;
        return (
          <div key={i} className="flex flex-1 flex-col items-center justify-end gap-1">
            <div
              className="bar-grow w-full rounded-t-[3px]"
              style={{
                height: `${Math.max(6, ratio * (height - 12))}px`,
                background:
                  ratio > 0.66
                    ? 'linear-gradient(180deg,var(--v-200),var(--v-500))'
                    : ratio > 0.2
                      ? 'linear-gradient(180deg,var(--v-400),var(--v-600))'
                      : 'color-mix(in srgb, var(--v-300) 22%, transparent)',
                animationDelay: `${120 + i * 55}ms`,
              }}
            />
            {labels && (
              <span className="text-[10px] leading-none text-[var(--text-3)]">{labels[i]}</span>
            )}
          </div>
        );
      })}
    </div>
  );
}

/* ── Heat dots ──────────────────────────────────────────────────────────────
   A week of any binary thing (a habit, a completed day) as seven dots. */
export function HeatDots({
  active,
  size = 5,
}: {
  active: boolean[];
  size?: number;
}) {
  return (
    <span className="flex shrink-0 items-center gap-1" aria-hidden="true">
      {active.map((on, i) => (
        <span
          key={i}
          className="rounded-full transition-all"
          style={{
            width: size,
            height: size,
            background: on
              ? 'linear-gradient(160deg,var(--v-200),var(--v-500))'
              : 'color-mix(in srgb, var(--v-200) 16%, transparent)',
            boxShadow: on ? '0 0 6px color-mix(in srgb, var(--v-400) 70%, transparent)' : 'none',
          }}
        />
      ))}
    </span>
  );
}
