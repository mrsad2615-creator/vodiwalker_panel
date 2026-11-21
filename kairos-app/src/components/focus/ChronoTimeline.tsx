import { Check } from '@/components/ui/icons';
import { Numeric } from '@/components/ui/Numeric';
import type { ClockSegment } from '@/lib/chrono/plan';
import { planFocusSeconds } from '@/lib/chrono/plan';

interface ChronoTimelineProps {
  plan: ClockSegment[];
  segmentIndex: number;
  /** Seconds left in the current segment, for the live chip. */
  remainingSeconds: number;
  running: boolean;
  onSeek: (index: number) => void;
}

const minutes = (seconds: number) => Math.round(seconds / 60);

/**
 * The shape of the session, laid out as a rail.
 *
 * A countdown alone hides the thing readers most need to know — "how many blocks
 * are left?" — so the plan is always visible: what is done, what runs now, and
 * what is still ahead. Every chip is a button, which is also how you move around
 * a session without abandoning it.
 */
export function ChronoTimeline({
  plan,
  segmentIndex,
  remainingSeconds,
  running,
  onSeek,
}: ChronoTimelineProps) {
  if (plan.length === 0) return null;

  const focusTotal = planFocusSeconds(plan);

  return (
    <section aria-label="برنامهٔ جلسه" className="space-y-2">
      <div className="flex items-baseline justify-between">
        <span className="font-display-caps text-[8px] font-semibold text-[var(--text-3)]">
          SESSION PLAN
        </span>
        <span className="text-[10px] text-[var(--text-3)]">
          {focusTotal > 0 ? (
            <>
              مجموع تمرکز{' '}
              <Numeric
                value={minutes(focusTotal)}
                className="font-semibold text-[var(--text-2)]"
              />{' '}
              دقیقه
            </>
          ) : (
            'بی‌پایان'
          )}
        </span>
      </div>

      <ol className="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-1">
        {plan.map((segment, index) => {
          const isCurrent = index === segmentIndex;
          const isDone = index < segmentIndex;
          const isBreak = segment.kind === 'break';
          const shownSeconds = isCurrent ? remainingSeconds : segment.seconds;

          return (
            <li key={`${segment.label}-${index}`} className="shrink-0">
              <button
                type="button"
                onClick={() => onSeek(index)}
                aria-current={isCurrent ? 'step' : undefined}
                aria-label={`بخش ${index + 1}: ${segment.label}، ${
                  segment.seconds > 0 ? `${minutes(segment.seconds)} دقیقه` : 'بی‌پایان'
                }${isCurrent ? '، در جریان' : ''}`}
                className={`relative flex min-w-[74px] flex-col items-start gap-0.5 rounded-lg border px-2.5 py-1.5 text-right transition-colors ${
                  isCurrent
                    ? 'border-[var(--accent)] bg-[color-mix(in_srgb,var(--accent)_14%,transparent)]'
                    : isBreak
                    ? 'border-dashed border-[var(--border)] bg-transparent'
                    : 'border-[var(--border)] bg-[var(--surface-2)]/40'
                } ${isDone ? 'opacity-55' : ''}`}
              >
                <span className="flex w-full items-center gap-1">
                  <span
                    className={`num-geo text-[9px] font-semibold ${
                      isCurrent ? 'text-[var(--accent-fg)]' : 'text-[var(--text-3)]'
                    }`}
                  >
                    {index + 1}
                  </span>
                  {isDone && <Check className="h-2.5 w-2.5 text-[var(--text-3)]" />}
                  {isCurrent && running && (
                    <span
                      aria-hidden="true"
                      className="ml-auto h-1.5 w-1.5 animate-pulse rounded-full"
                      style={{ background: 'var(--accent)' }}
                    />
                  )}
                </span>
                <span
                  className={`max-w-[86px] truncate text-[10.5px] font-medium ${
                    isCurrent ? 'text-[var(--text)]' : 'text-[var(--text-2)]'
                  }`}
                >
                  {segment.label}
                </span>
                <span className="num-geo text-[9px] text-[var(--text-3)]">
                  {segment.seconds > 0 ? `${minutes(shownSeconds)}′` : '∞'}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
