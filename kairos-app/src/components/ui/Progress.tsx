import { useId } from 'react';

/**
 * Progress primitives.
 *
 * All of them are CSS/SVG only (no JS frame loop), respect `prefers-reduced-motion`
 * through the shared `kairos-*` keyframes and expose proper ARIA roles so screen
 * readers announce the state instead of silently spinning.
 */

const clamp = (value: number) => Math.min(1, Math.max(0, Number.isFinite(value) ? value : 0));

interface LinearProgressProps {
  /** 0…1. Omit or pass `indeterminate` for a shimmering bar. */
  value?: number;
  indeterminate?: boolean;
  className?: string;
  barClassName?: string;
  /** Accessible name, e.g. «آماده‌سازی داده‌ها». */
  label?: string;
  thickness?: number;
}

export function LinearProgress({
  value = 0,
  indeterminate = false,
  className = '',
  barClassName = '',
  label = 'در حال بارگذاری',
  thickness = 3,
}: LinearProgressProps) {
  const pct = Math.round(clamp(value) * 100);
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={indeterminate ? undefined : pct}
      aria-busy={indeterminate || pct < 100}
      className={`relative w-full overflow-hidden rounded-full bg-[var(--surface-3)] ${className}`}
      style={{ height: thickness }}
    >
      {indeterminate ? (
        <span className="kairos-progress-indeterminate absolute inset-y-0 w-1/3 rounded-full bg-[var(--accent)]" />
      ) : (
        <span
          className={`absolute inset-y-0 start-0 rounded-full bg-[var(--accent)] transition-[width] duration-300 ease-out ${barClassName}`}
          style={{ width: `${pct}%` }}
        />
      )}
    </div>
  );
}

interface ProgressRingProps {
  value?: number;
  size?: number;
  stroke?: number;
  className?: string;
  label?: string;
  /** Show the numeric percentage in the middle. */
  showValue?: boolean;
}

export function ProgressRing({
  value = 0,
  size = 44,
  stroke = 3,
  className = '',
  label = 'در حال بارگذاری',
  showValue = false,
}: ProgressRingProps) {
  const gradientId = useId().replace(/[:]/g, '');
  const pct = clamp(value);
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;

  return (
    <span
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(pct * 100)}
      className={`relative inline-flex items-center justify-center ${className}`}
      style={{ width: size, height: size }}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90" aria-hidden="true">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="var(--accent-fg)" />
            <stop offset="100%" stopColor="var(--accent)" />
          </linearGradient>
        </defs>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--surface-3)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={`url(#${gradientId})`}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - pct)}
          style={{ transition: 'stroke-dashoffset 320ms cubic-bezier(0.22, 1, 0.36, 1)' }}
        />
      </svg>
      {showValue && (
        <span className="absolute inset-0 flex items-center justify-center text-[10px] font-medium text-[var(--text-2)]">
          {Math.round(pct * 100)}%
        </span>
      )}
    </span>
  );
}

/** Small indeterminate spinner (used by buttons and short-lived loads). */
export function Spinner({ size = 16, className = '', label }: { size?: number; className?: string; label?: string }) {
  return (
    <span
      role="status"
      aria-label={label ?? 'در حال انجام'}
      className={`kairos-spinner inline-block shrink-0 rounded-full border-[1.5px] border-[var(--border-strong)] border-t-[var(--accent-fg)] ${className}`}
      style={{ width: size, height: size }}
    />
  );
}

/** Neutral placeholder block with a subtle shimmer. */
export function SkeletonBlock({
  className = '',
  height = 12,
  width = '100%',
}: {
  className?: string;
  height?: number;
  width?: number | string;
}) {
  return (
    <span
      aria-hidden="true"
      className={`kairos-skeleton block rounded-full bg-[var(--surface-2)] ${className}`}
      style={{ height, width }}
    />
  );
}
