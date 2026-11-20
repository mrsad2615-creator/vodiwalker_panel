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

const EASE = [0.22, 1, 0.36, 1] as const;

/**
 * Launch screen — one mark, one wordmark, one hairline.
 *
 * Motion is choreographed on the compositor only (transform / opacity /
 * clip-path), runs ~900ms end-to-end and collapses to a static frame when the
 * user asked for reduced motion. Progress is real: it follows font readiness and
 * store hydration, so the bar never lies about what the app is doing.
 */
export function LaunchScreen({ exiting, progress, onSkip }: LaunchScreenProps) {
  const reducedMotion = useReducedMotion();
  const skippedRef = useRef(false);

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

  const animated = !reducedMotion;

  return (
    <motion.div
      role="status"
      aria-label="در حال آماده‌سازی KAIROS"
      aria-live="polite"
      onClick={skip}
      initial={false}
      animate={exiting ? { opacity: 0, scale: animated ? 1.012 : 1 } : { opacity: 1, scale: 1 }}
      transition={{ duration: 0.24, ease: 'easeOut' }}
      className="app-safe-frame fixed inset-0 z-[100] flex select-none flex-col items-center justify-center overflow-hidden bg-[var(--bg)]"
      style={{ contain: 'layout style paint' }}
    >
      {/* Soft brand glow — 6% of the screen, fades in once. */}
      <motion.div
        aria-hidden="true"
        className="kairos-launch-glow pointer-events-none absolute inset-0"
        initial={animated ? { opacity: 0 } : false}
        animate={{ opacity: 0.55 }}
        transition={{ duration: 1.1, ease: 'easeOut' }}
      />

      <div className="relative flex flex-col items-center">
        <motion.div
          className="relative"
          initial={animated ? { opacity: 0, scale: 0.94, clipPath: 'inset(0% 0% 100% 0%)' } : false}
          animate={{ opacity: 1, scale: 1, clipPath: 'inset(0% 0% 0% 0%)' }}
          transition={{ duration: 0.55, ease: EASE, delay: 0.05 }}
        >
          {/* The screen itself is labelled, so the mark stays decorative. */}
          <KairosLogoMark size={58} className="text-[var(--accent-fg)]" />

          {animated && (
            <motion.span
              aria-hidden="true"
              className="kairos-launch-sweep pointer-events-none absolute inset-0"
              initial={{ x: '-130%' }}
              animate={{ x: '130%' }}
              transition={{ duration: 1.05, ease: 'easeInOut', delay: 0.34 }}
            />
          )}
        </motion.div>

        <motion.span
          className="font-brand mt-7 text-[15px] font-semibold text-[var(--text)]"
          initial={animated ? { opacity: 0, y: 6, letterSpacing: '0.42em' } : false}
          animate={{ opacity: 1, y: 0, letterSpacing: '0.3em' }}
          transition={{ duration: 0.5, ease: EASE, delay: 0.24 }}
        >
          KAIROS
        </motion.span>

        <motion.span
          className="mt-2 text-[11px] tracking-wide text-[var(--text-3)]"
          initial={animated ? { opacity: 0 } : false}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.4, ease: 'easeOut', delay: 0.38 }}
        >
          نظم، عادت، تمرکز
        </motion.span>

        <motion.div
          className="mt-8 flex w-[104px] flex-col items-center gap-2"
          initial={animated ? { opacity: 0 } : false}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.35, ease: 'easeOut', delay: 0.46 }}
        >
          <LinearProgress
            value={progress}
            thickness={2}
            label="آماده‌سازی KAIROS"
            className="bg-[var(--surface-3)]"
          />
          <span className="text-[9px] tabular-nums text-[var(--text-3)]" aria-hidden="true">
            {Math.round(Math.min(1, Math.max(0, progress)) * 100)}%
          </span>
        </motion.div>
      </div>

      <span className="num-geo absolute bottom-[calc(1rem+var(--safe-bottom))] text-[9px] tracking-widest text-[var(--text-3)]">
        v{APP_VERSION} · b{BUILD_NUMBER}
      </span>

      <span className="sr-only">
        {progress >= 1 ? 'آماده شد' : `در حال آماده‌سازی، ${Math.round(progress * 100)} درصد`}
      </span>
    </motion.div>
  );
}
