import { motion } from 'framer-motion';
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
        <span className="text-[10.5px] font-medium text-[var(--text-2)]">برنامهٔ جلسه</span>
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

      {/* Where the session sits overall: one bar, so the rail has a "you are here"
          even before you read a single chip. */}
      <div className="h-[3px] overflow-hidden rounded-full bg-[var(--border)]" aria-hidden="true">
        <motion.div
          className="h-full rounded-full"
          style={{ background: 'var(--accent)' }}
          initial={false}
          animate={{
            width: `${Math.round(
              ((segmentIndex + (plan[segmentIndex]?.seconds
                ? 1 - remainingSeconds / plan[segmentIndex].seconds
                : 0)) /
                Math.max(1, plan.length)) *
                100
            )}%`,
          }}
          transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
        />
      </div>

      <ol className="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-1">
        {plan.map((segment, index) => {
          const isCurrent = index === segmentIndex;
          const isDone = index < segmentIndex;
          const isBreak = segment.kind === 'break';
          const shownSeconds = isCurrent ? remainingSeconds : segment.seconds;

          return (
            <motion.li
              key={`${segment.label}-${index}`}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.035, type: 'spring', stiffness: 320, damping: 28 }}
              className="shrink-0"
            >
              <motion.button
                type="button"
                whileTap={{ scale: 0.95 }}
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

                {/* The current chip drains as the block runs: motion is the state. */}
                {isCurrent && segment.seconds > 0 && (
                  <motion.span
                    aria-hidden="true"
                    className="absolute inset-x-0 bottom-0 h-[2px] rounded-full"
                    style={{ background: 'var(--accent)' }}
                    initial={false}
                    animate={{
                      width: `${Math.max(0, Math.min(100, (remainingSeconds / segment.seconds) * 100))}%`,
                    }}
                    transition={{ duration: 0.5, ease: 'linear' }}
                  />
                )}
              </motion.button>
            </motion.li>
          );
        })}
      </ol>
    </section>
  );
}
