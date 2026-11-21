import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { KAIROS_MARK_PATHS, KAIROS_MARK_VIEWBOX } from './KairosMark';
import { APP_VERSION, BUILD_NUMBER } from '@/lib/buildInfo';

interface LaunchScreenProps {
  /** Fade out and hand over to the shell. */
  exiting: boolean;
  /** 0…1 — real boot progress (fonts + store + first paint). */
  progress: number;
  onSkip: () => void;
}

/* ---------------------------------------------------------------------------
   Geometry
   The mark is authored in a 389 × 432 box (traced from the approved app icon).
   It is centred in a square 140-unit stage at 104 units tall, so the artwork
   keeps its exact proportions on every screen size.
   --------------------------------------------------------------------------- */

const STAGE = 156;
const MARK_HEIGHT = 120;
const SCALE = MARK_HEIGHT / KAIROS_MARK_VIEWBOX.height;
const MARK_WIDTH = KAIROS_MARK_VIEWBOX.width * SCALE;
const MARK_TRANSFORM = `translate(${((STAGE - MARK_WIDTH) / 2).toFixed(2)} ${(
  (STAGE - MARK_HEIGHT) /
  2
).toFixed(2)}) scale(${SCALE.toFixed(4)})`;

/** 2πr for the spinner ring (r = 16.5 in a 38-unit viewBox). */
const RING_CIRCUMFERENCE = 2 * Math.PI * 16.5;
const RING_DASH = `${(RING_CIRCUMFERENCE * 0.27).toFixed(2)} ${(RING_CIRCUMFERENCE * 0.73).toFixed(2)}`;

/**
 * KAIROS launch screen — cinematic dark emergence.
 *
 * Choreography (total ≈ 1.0 s, then a 240 ms fade into the shell):
 *   000–660 ms  the mark emerges from pure black under a spotlight that opens
 *   340–960 ms  a strong diagonal light sweep travels across the metal (masked
 *               to the mark, so the light is only ever *on* the logo)
 *   420–1000 ms thin white ring spinner + KAIROS wordmark fade up
 *   900 ms →    real boot progress is exposed to assistive tech only
 *
 * Why it stays cheap on mid-range Android:
 *   - no WebGL, no canvas, no per-frame JavaScript — three CSS keyframes;
 *   - no `filter: blur()` anywhere: the spotlight is a pre-painted radial
 *     gradient, the silver is a static gradient, the sweep is a translated
 *     rect inside a static SVG mask;
 *   - only opacity/transform animate, so the whole thing is composited;
 *   - the component unmounts after the fade — nothing keeps running;
 *   - reduced-motion users get the finished frame with a still ring;
 *   - when the app goes to the background the timeline is settled immediately
 *     (no wasted frames while it is not visible) and resumes as a static frame.
 */
export function LaunchScreen({ exiting, progress, onSkip }: LaunchScreenProps) {
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const [settled, setSettled] = useState(false);
  const skippedRef = useRef(false);
  const pct = Math.round(Math.min(1, Math.max(0, progress)) * 100);

  const skip = useCallback(() => {
    if (skippedRef.current) return;
    skippedRef.current = true;
    onSkip();
  }, [onSkip]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' || event.key === ' ' || event.key === 'Enter') skip();
    };
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') setSettled(true);
    };
    window.addEventListener('keydown', onKey);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      window.removeEventListener('keydown', onKey);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [skip]);

  const metalId = `kairos-metal-${uid}`;
  const sweepId = `kairos-sweep-${uid}`;
  const maskId = `kairos-mask-${uid}`;

  return (
    <div
      role="status"
      aria-label="در حال آماده‌سازی KAIROS"
      aria-live="polite"
      onClick={skip}
      className={[
        'kairos-launch app-safe-frame fixed inset-0 z-[100] flex select-none flex-col items-center justify-center overflow-hidden',
        exiting ? 'is-exiting' : '',
        settled ? 'is-settled' : '',
      ].join(' ')}
      style={{ contain: 'layout paint style' }}
    >
      {/* Focused spotlight — a pre-painted radial gradient, opened once. */}
      <div className="kairos-launch__spot pointer-events-none absolute inset-0" aria-hidden="true" />

      <div className="relative flex flex-col items-center">
        <svg
          viewBox={`0 0 ${STAGE} ${STAGE}`}
          width={STAGE}
          height={STAGE}
          className="kairos-launch__art block"
          aria-hidden="true"
          focusable="false"
        >
          <defs>
            {/* Silver: one user-space gradient, so all four contours share the
                same illumination (a per-shape gradient would break the metal). */}
            <linearGradient
              id={metalId}
              gradientUnits="userSpaceOnUse"
              x1="0"
              y1="0"
              x2={KAIROS_MARK_VIEWBOX.width}
              y2={KAIROS_MARK_VIEWBOX.height}
            >
              <stop offset="0%" stopColor="#FFFFFF" />
              <stop offset="22%" stopColor="var(--text)" />
              <stop offset="41%" stopColor="var(--text-2)" />
              <stop offset="55%" stopColor="var(--text)" />
              <stop offset="73%" stopColor="var(--text-2)" />
              <stop offset="88%" stopColor="var(--text)" />
              <stop offset="100%" stopColor="#FFFFFF" />
            </linearGradient>

            {/* The travelling highlight band. */}
            <linearGradient id={sweepId} x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0" />
              <stop offset="34%" stopColor="#FFFFFF" stopOpacity="0.55" />
              <stop offset="50%" stopColor="#FFFFFF" stopOpacity="1" />
              <stop offset="66%" stopColor="#FFFFFF" stopOpacity="0.55" />
              <stop offset="100%" stopColor="#FFFFFF" stopOpacity="0" />
            </linearGradient>

            {/* The sweep may only light the logo itself. */}
            <mask id={maskId} maskUnits="userSpaceOnUse" x="0" y="0" width={STAGE} height={STAGE}>
              <g transform={MARK_TRANSFORM} fill="#FFFFFF" fillRule="evenodd">
                {KAIROS_MARK_PATHS.map((d) => (
                  <path key={d} d={d} />
                ))}
              </g>
            </mask>
          </defs>

          {/* Static silver body — the logo never moves or rotates. */}
          <g transform={MARK_TRANSFORM} fill={`url(#${metalId})`} fillRule="evenodd">
            {KAIROS_MARK_PATHS.map((d) => (
              <path key={d} d={d} />
            ))}
          </g>

          {/* Cinematic sweep: a rotated band translated through the mask. */}
          <g mask={`url(#${maskId})`}>
            <g className="kairos-launch__sweep-track">
              <g transform={`rotate(20 ${STAGE / 2} ${STAGE / 2})`}>
                <rect
                  x={STAGE / 2 - 44}
                  y={STAGE / 2 - 95}
                  width="88"
                  height="190"
                  fill={`url(#${sweepId})`}
                  opacity="0.22"
                />
                <rect
                  x={STAGE / 2 - 15}
                  y={STAGE / 2 - 95}
                  width="30"
                  height="190"
                  fill={`url(#${sweepId})`}
                />
              </g>
            </g>
          </g>
        </svg>

        {/* Minimal circular loader, directly below the logo. */}
        <div
          role="progressbar"
          aria-label="آماده‌سازی KAIROS"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={pct}
          aria-busy={pct < 100}
          className="kairos-launch__loader mt-9 flex items-center justify-center"
        >
          <svg viewBox="0 0 38 38" width={38} height={38} aria-hidden="true" focusable="false">
            <circle className="kairos-ring__track" cx="19" cy="19" r="16.5" />
            <circle
              className="kairos-ring__arc"
              cx="19"
              cy="19"
              r="16.5"
              strokeDasharray={RING_DASH}
            />
          </svg>
        </div>

        {/* Brand wordmark */}
        <div
          className="font-brand kairos-launch__word mt-6 text-[15px] font-semibold text-white"
          aria-hidden="true"
        >
          KAIROS
        </div>
      </div>

      <span className="num-geo kairos-launch__build absolute bottom-[calc(1rem+var(--safe-bottom))] text-[9px] tracking-widest">
        v{APP_VERSION} · b{BUILD_NUMBER}
      </span>

      <span className="sr-only">{pct >= 100 ? 'آماده شد' : `در حال آماده‌سازی، ${pct} درصد`}</span>
    </div>
  );
}
