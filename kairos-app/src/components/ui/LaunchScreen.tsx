import { useEffect, useRef } from 'react';
import { motion, useReducedMotion } from 'framer-motion';
import { KairosLogoMark } from './KairosMark';
import { LinearProgress } from './Progress';
import { APP_VERSION, BUILD_NUMBER } from '@/lib/buildInfo';

interface LaunchScreenProps {
  /** Fade out and hand over to the shell. */
  exiting: boolean;
  /** 0…1 — real boot progress (fonts + store + first paint). */
  progress: number;
  onSkip: () => void;
}

const EASE_OUT = [0.22, 1, 0.36, 1] as const;
const WORDMARK = ['K', 'A', 'I', 'R', 'O', 'S'] as const;

/**
 * Launch screen — logo motion.
 *
 * A real entrance rather than a fade: the mark springs up from below with a
 * slight rotation and a wipe, an accent arc orbits it, the glow breathes, the
 * wordmark letters rise one by one and the hairline fills with the actual boot
 * progress. Everything is transform/opacity/clip-path on the compositor, runs
 * ~1.1s and collapses to a static frame under `prefers-reduced-motion`.
 */
export function LaunchScreen({ exiting, progress, onSkip }: LaunchScreenProps) {
  const reducedMotion = useReducedMotion();
  const skippedRef = useRef(false);
  const animated = !reducedMotion;
  const pct = Math.round(Math.min(1, Math.max(0, progress)) * 100);

  const skip = () => {
    if (skippedRef.current) return;
    skippedRef.current = true;
    onSkip();
  };

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' || event.key === ' ' || event.key === 'Enter') skip();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  return (
    <motion.div
      role="status"
      aria-label="در حال آماده‌سازی KAIROS"
      aria-live="polite"
      onClick={skip}
      initial={false}
      animate={exiting ? { opacity: 0, scale: animated ? 1.02 : 1 } : { opacity: 1, scale: 1 }}
      transition={{ duration: 0.26, ease: 'easeOut' }}
      className="app-safe-frame fixed inset-0 z-[100] flex select-none flex-col items-center justify-center overflow-hidden bg-[var(--bg)]"
      style={{ contain: 'layout paint' }}
    >
      {/* Breathing brand glow */}
      <motion.div
        aria-hidden="true"
        className="kairos-launch-glow pointer-events-none absolute inset-0"
        initial={animated ? { opacity: 0, scale: 0.85 } : false}
        animate={
          animated
            ? { opacity: [0, 0.75, 0.5, 0.75], scale: [0.85, 1.04, 0.98, 1.04] }
            : { opacity: 0.7, scale: 1 }
        }
        transition={animated ? { duration: 4.2, repeat: Infinity, ease: 'easeInOut' } : { duration: 0.3 }}
      />

      <div className="relative flex flex-col items-center">
        {/* Mark + orbiting arc */}
        <div className="relative flex h-[132px] w-[132px] items-center justify-center">
          {animated && (
            <motion.svg
              aria-hidden="true"
              viewBox="0 0 132 132"
              className="absolute inset-0"
              initial={{ opacity: 0, rotate: -90 }}
              animate={{ opacity: 1, rotate: 270 }}
              transition={{
                opacity: { duration: 0.5, delay: 0.15 },
                rotate: { duration: 6.5, repeat: Infinity, ease: 'linear' },
              }}
            >
              <circle
                cx="66"
                cy="66"
                r="60"
                fill="none"
                stroke="var(--accent)"
                strokeOpacity="0.55"
                strokeWidth="1.4"
                strokeLinecap="round"
                strokeDasharray="52 325"
              />
              <circle cx="66" cy="6" r="2.6" fill="var(--accent-fg)" />
            </motion.svg>
          )}

          <motion.div
            className="relative"
            initial={
              animated
                ? { opacity: 0, scale: 0.62, y: 26, rotate: -8, clipPath: 'inset(0% 0% 100% 0%)' }
                : false
            }
            animate={{ opacity: 1, scale: 1, y: 0, rotate: 0, clipPath: 'inset(0% 0% 0% 0%)' }}
            transition={
              animated
                ? {
                    type: 'spring',
                    stiffness: 190,
                    damping: 17,
                    mass: 0.9,
                    clipPath: { duration: 0.5, ease: EASE_OUT },
                  }
                : { duration: 0.2 }
            }
          >
            <KairosLogoMark size={92} className="text-[var(--accent-fg)]" />

            {animated && (
              <motion.span
                aria-hidden="true"
                className="kairos-launch-sweep pointer-events-none absolute inset-0"
                initial={{ x: '-140%' }}
                animate={{ x: '140%' }}
                transition={{ duration: 1.15, ease: 'easeInOut', delay: 0.42 }}
              />
            )}
          </motion.div>
        </div>

        {/* Wordmark, letter by letter */}
        <div className="font-brand mt-6 flex items-baseline text-[22px] font-semibold tracking-[0.28em] text-[var(--text)]" aria-hidden="true">
          {WORDMARK.map((letter, index) => (
            <motion.span
              key={`${letter}-${index}`}
              initial={animated ? { opacity: 0, y: 12, filter: 'blur(4px)' } : false}
              animate={{ opacity: 1, y: 0, filter: 'blur(0px)' }}
              transition={{ duration: 0.45, ease: EASE_OUT, delay: 0.22 + index * 0.055 }}
              className="inline-block"
            >
              {letter}
            </motion.span>
          ))}
        </div>

        <motion.span
          className="mt-2.5 text-[11.5px] tracking-wide text-[var(--text-3)]"
          initial={animated ? { opacity: 0, y: 6 } : false}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: EASE_OUT, delay: 0.6 }}
        >
          نظم، عادت، تمرکز
        </motion.span>

        <motion.div
          className="mt-9 flex w-[132px] flex-col items-center gap-2.5"
          initial={animated ? { opacity: 0, scaleX: 0.6 } : false}
          animate={{ opacity: 1, scaleX: 1 }}
          transition={{ duration: 0.4, ease: EASE_OUT, delay: 0.66 }}
        >
          <div className="relative w-full overflow-hidden rounded-full">
            <LinearProgress
              value={progress}
              thickness={2.5}
              label="آماده‌سازی KAIROS"
              className="bg-[var(--surface-3)]"
            />
            {animated && (
              <span className="kairos-progress-sheen pointer-events-none absolute inset-y-0 start-0 w-1/3" />
            )}
          </div>
          <span className="num-geo text-[9.5px] tabular-nums text-[var(--text-3)]" aria-hidden="true">
            {pct}%
          </span>
        </motion.div>
      </div>

      <span className="num-geo absolute bottom-[calc(1rem+var(--safe-bottom))] text-[9px] tracking-widest text-[var(--text-3)]">
        v{APP_VERSION} · b{BUILD_NUMBER}
      </span>

      <span className="sr-only">{pct >= 100 ? 'آماده شد' : `در حال آماده‌سازی، ${pct} درصد`}</span>
    </motion.div>
  );
}
