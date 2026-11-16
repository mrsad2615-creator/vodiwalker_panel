import { useEffect, useRef } from 'react';

interface MrsadSplashIntroProps {
  exiting: boolean;
  onSkip: () => void;
}

const BRAND_LETTERS = ['m', 'r', 's', 'a', 'd'] as const;

/**
 * 120fps Hardware-Composited Pure-CSS Motion Graphic for "mrsad"
 * - Zero SVG <feGaussianBlur> filters (eliminates Android Skia CPU jank)
 * - Zero animated CSS blur() / drop-shadow() filters
 * - Zero Framer Motion JS frame loops on cold start
 */
export function MrsadSplashIntro({ exiting, onSkip }: MrsadSplashIntroProps) {
  const skippedRef = useRef(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' || e.key === ' ' || e.key === 'Enter') {
        if (!skippedRef.current) {
          skippedRef.current = true;
          onSkip();
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onSkip]);

  return (
    <div
      role="status"
      aria-label="در حال بارگذاری mrsad KAIROS"
      onClick={() => {
        if (!skippedRef.current) {
          skippedRef.current = true;
          onSkip();
        }
      }}
      className={`mrsad-splash-root fixed inset-0 z-[100] flex flex-col items-center justify-center overflow-hidden bg-[#09090D] select-none ${
        exiting ? 'is-exiting' : ''
      }`}
    >
      {/* Pre-baked Soft Radial Stage Halo (Zero blur filter cost) */}
      <div
        aria-hidden="true"
        className="mrsad-aura pointer-events-none absolute h-[440px] w-[440px] rounded-full"
        style={{
          background:
            'radial-gradient(circle, rgba(128, 82, 214, 0.28) 0%, rgba(36, 25, 54, 0.22) 38%, rgba(9, 9, 13, 0) 70%)',
        }}
      />

      {/* Center Vector Monogram + Editorial mrsad Stack */}
      <div className="relative z-10 flex flex-col items-center">
        <div className="mrsad-disc relative flex h-32 w-32 items-center justify-center overflow-hidden rounded-full">
          <svg
            viewBox="0 0 140 140"
            className="h-32 w-32"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <defs>
              <linearGradient
                id="mrsadOptRing"
                x1="14"
                y1="14"
                x2="126"
                y2="126"
                gradientUnits="userSpaceOnUse"
              >
                <stop offset="0%" stopColor="#B69AEF" />
                <stop offset="55%" stopColor="#8052D6" />
                <stop offset="100%" stopColor="#3D226D" />
              </linearGradient>
              <linearGradient
                id="mrsadOptM"
                x1="40"
                y1="44"
                x2="100"
                y2="96"
                gradientUnits="userSpaceOnUse"
              >
                <stop offset="0%" stopColor="#F4F1F8" />
                <stop offset="70%" stopColor="#E4D8F8" />
                <stop offset="100%" stopColor="#B69AEF" />
              </linearGradient>
              <radialGradient id="mrsadInnerFill" cx="50%" cy="32%" r="68%">
                <stop offset="0%" stopColor="#1F1633" />
                <stop offset="100%" stopColor="#0E0D14" />
              </radialGradient>
            </defs>

            {/* Sculpted Dark Obsidian Disc */}
            <circle
              cx="70"
              cy="70"
              r="55"
              fill="url(#mrsadInnerFill)"
              stroke="rgba(182, 154, 239, 0.16)"
              strokeWidth="1"
            />

            {/* Subtle Static Ambient Ring Halo (zero filter!) */}
            <circle
              cx="70"
              cy="70"
              r="60"
              stroke="rgba(128, 82, 214, 0.14)"
              strokeWidth="6"
            />

            {/* Primary Razor-Sharp Animated Orbital Arc */}
            <circle
              cx="70"
              cy="70"
              r="60"
              stroke="url(#mrsadOptRing)"
              strokeWidth="2.8"
              strokeLinecap="round"
              transform="rotate(-38 70 70)"
              className="mrsad-ring"
            />

            {/* Sculptural Vector "M" Monogram */}
            <path
              d="M44 91 V49 L70 76 L96 49 V91"
              stroke="url(#mrsadOptM)"
              strokeWidth="5.4"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="mrsad-m-path"
            />

            {/* Lavender Crown Accent Node */}
            <circle
              cx="70"
              cy="42"
              r="3.6"
              fill="#B69AEF"
              className="mrsad-crown-dot"
            />
          </svg>

          {/* Zero-blur pure gradient specular sweep */}
          <div
            aria-hidden="true"
            className="mrsad-sheen pointer-events-none absolute inset-y-2 w-10"
            style={{
              background:
                'linear-gradient(90deg, rgba(244,241,248,0) 0%, rgba(244,241,248,0.22) 50%, rgba(244,241,248,0) 100%)',
            }}
          />
        </div>

        {/* Staggered GPU-Composited "mrsad" Wordmark */}
        <div
          dir="ltr"
          className="mt-5 flex items-baseline justify-center overflow-hidden px-4 py-1"
        >
          {BRAND_LETTERS.map((ch, idx) => (
            <span
              key={ch + idx}
              className="mrsad-letter num-geo text-[2.6rem] font-bold tracking-[0.14em]"
              style={{
                animationDelay: `${450 + idx * 65}ms`,
                background:
                  idx === 0 || idx === 4
                    ? 'linear-gradient(180deg, #F4F1F8 15%, #B69AEF 100%)'
                    : 'linear-gradient(180deg, #F4F1F8 30%, #D9CAF5 100%)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              {ch}
            </span>
          ))}
        </div>

        {/* Expanding Royal Purple Horizon Hairline */}
        <div
          aria-hidden="true"
          className="mrsad-horizon mt-2 h-[1.5px] w-40 rounded-full"
          style={{
            background:
              'linear-gradient(90deg, transparent 0%, #8052D6 35%, #B69AEF 50%, #8052D6 65%, transparent 100%)',
          }}
        />

        {/* Subtitle */}
        <div className="mrsad-subtitle mt-3 flex flex-col items-center gap-1 text-center">
          <span className="font-brand text-[10.5px] font-semibold text-[#B69AEF]/90">
            KAIROS STUDIO
          </span>
          <span className="text-[11px] font-medium text-[#A79EB9]">
            استودیوی نظم، عادت و تمرکز
          </span>
        </div>
      </div>

      {/* Ultra-slim GPU-composited progress indicator */}
      <div className="absolute bottom-10 h-[2px] w-28 overflow-hidden rounded-full bg-[#241936]">
        <div className="mrsad-progress h-full w-full bg-gradient-to-r from-[#8052D6] to-[#B69AEF]" />
      </div>
    </div>
  );
}
