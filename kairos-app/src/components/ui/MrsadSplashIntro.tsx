import { useEffect, useRef } from 'react';
import { KairosLogoMark } from './KairosMark';

interface MrsadSplashIntroProps {
  exiting: boolean;
  onSkip: () => void;
}

/**
 * Launch screen: a single mark, a single wordmark, a single hairline progress bar.
 * Pure CSS on the compositor — no JS frame loop, no filters, no blur animation.
 */
export function MrsadSplashIntro({ exiting, onSkip }: MrsadSplashIntroProps) {
  const skippedRef = useRef(false);

  const skip = () => {
    if (skippedRef.current) return;
    skippedRef.current = true;
    onSkip();
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' || e.key === ' ' || e.key === 'Enter') skip();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  return (
    <div
      role="status"
      aria-label="در حال آماده‌سازی KAIROS"
      onClick={skip}
      className={`mrsad-splash-root app-safe-frame fixed inset-0 z-[100] flex select-none flex-col items-center justify-center bg-[var(--bg)] ${
        exiting ? 'is-exiting' : ''
      }`}
    >
      <div className="mrsad-mark flex flex-col items-center">
        <KairosLogoMark size={56} />
      </div>

      <div className="mrsad-wordmark mt-6 flex flex-col items-center gap-2">
        <span className="font-brand text-[15px] font-semibold text-[var(--text)]">KAIROS</span>
        <span className="text-[12px] text-[var(--text-2)]">نظم، عادت و تمرکز</span>
      </div>

      <div className="absolute bottom-[calc(3rem+var(--safe-bottom))] h-px w-24 overflow-hidden rounded-full bg-[var(--border)]">
        <div className="mrsad-progress h-full w-full bg-[var(--accent)]" />
      </div>
    </div>
  );
}
