import { useEffect } from 'react';
import { motion } from 'framer-motion';

interface MrsadSplashIntroProps {
  ready: boolean;
  onFinish: () => void;
}

const BRAND_LETTERS = ['m', 'r', 's', 'a', 'd'] as const;

export function MrsadSplashIntro({ ready, onFinish }: MrsadSplashIntroProps) {
  useEffect(() => {
    if (!ready) return;
    const timer = window.setTimeout(() => {
      onFinish();
    }, 2450);
    return () => window.clearTimeout(timer);
  }, [ready, onFinish]);

  return (
    <motion.div
      role="status"
      aria-label="در حال بارگذاری mrsad KAIROS"
      onClick={() => {
        if (ready) onFinish();
      }}
      initial={{ opacity: 1 }}
      exit={{ opacity: 0, scale: 1.035 }}
      transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
      className="fixed inset-0 z-[100] flex flex-col items-center justify-center overflow-hidden bg-[#09090D] select-none"
    >
      {/* Expanding Atmospheric Royal Purple Aura */}
      <motion.div
        aria-hidden="true"
        initial={{ scale: 0.35, opacity: 0 }}
        animate={{ scale: [0.35, 1.15, 1], opacity: [0, 0.85, 0.55] }}
        transition={{ duration: 1.8, ease: [0.16, 1, 0.3, 1] }}
        className="pointer-events-none absolute h-[420px] w-[420px] rounded-full"
        style={{
          background:
            'radial-gradient(circle, rgba(128, 82, 214, 0.34) 0%, rgba(36, 25, 54, 0.22) 45%, transparent 72%)',
          filter: 'blur(18px)',
        }}
      />

      {/* Subtle Expanding Pulse Ring */}
      <motion.div
        aria-hidden="true"
        initial={{ scale: 0.5, opacity: 0 }}
        animate={{ scale: [0.5, 1.45], opacity: [0.45, 0] }}
        transition={{ duration: 1.7, delay: 0.25, ease: 'easeOut' }}
        className="pointer-events-none absolute h-52 w-52 rounded-full border border-[#B69AEF]/35"
      />

      {/* Center Motion-Graphic Emblem + Typography Stack */}
      <div className="relative z-10 flex flex-col items-center">
        {/* Razor-Sharp 100% Vector Architectural Emblem */}
        <div className="relative flex h-32 w-32 items-center justify-center">
          {/* Slow Orbital Specular Ring */}
          <motion.svg
            viewBox="0 0 140 140"
            className="h-32 w-32 overflow-visible"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <defs>
              <linearGradient id="mrsadRingGrad" x1="12" y1="12" x2="128" y2="128" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#B69AEF" />
                <stop offset="52%" stopColor="#8052D6" />
                <stop offset="100%" stopColor="#3B2369" />
              </linearGradient>
              <linearGradient id="mrsadMGrad" x1="34" y1="36" x2="106" y2="104" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#F4F1F8" />
                <stop offset="68%" stopColor="#E6DCF7" />
                <stop offset="100%" stopColor="#B69AEF" />
              </linearGradient>
              <filter id="mrsadGlow" x="-30%" y="-30%" width="160%" height="160%">
                <feGaussianBlur stdDeviation="3.2" result="blur" />
                <feMerge>
                  <feMergeNode in="blur" />
                  <feMergeNode in="SourceGraphic" />
                </feMerge>
              </filter>
            </defs>

            {/* Inner dark glass disc */}
            <motion.circle
              cx="70"
              cy="70"
              r="54"
              fill="#12101A"
              stroke="rgba(182, 154, 239, 0.14)"
              strokeWidth="1"
              initial={{ scale: 0.72, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ duration: 0.55, ease: [0.22, 1, 0.36, 1] }}
            />

            {/* Architectural Octagon/Diamond Accent Frame */}
            <motion.rect
              x="31"
              y="31"
              width="78"
              height="78"
              rx="18"
              stroke="rgba(182, 154, 239, 0.2)"
              strokeWidth="1"
              initial={{ rotate: -25, scale: 0.8, opacity: 0 }}
              animate={{ rotate: 45, scale: 1, opacity: 1 }}
              transition={{ duration: 0.9, delay: 0.12, ease: [0.22, 1, 0.36, 1] }}
              style={{ transformOrigin: '70px 70px' }}
            />

            {/* Primary Glowing Animated Arc */}
            <motion.circle
              cx="70"
              cy="70"
              r="60"
              stroke="url(#mrsadRingGrad)"
              strokeWidth="2.8"
              strokeLinecap="round"
              filter="url(#mrsadGlow)"
              initial={{ pathLength: 0, rotate: -115, opacity: 0 }}
              animate={{ pathLength: 0.82, rotate: -35, opacity: 1 }}
              transition={{ duration: 1.05, delay: 0.08, ease: [0.22, 1, 0.36, 1] }}
              style={{ transformOrigin: '70px 70px' }}
            />

            {/* Secondary Delicate Counter-Arc */}
            <motion.circle
              cx="70"
              cy="70"
              r="65"
              stroke="#B69AEF"
              strokeOpacity="0.32"
              strokeWidth="1"
              strokeLinecap="round"
              initial={{ pathLength: 0, rotate: 90, opacity: 0 }}
              animate={{ pathLength: 0.28, rotate: 15, opacity: 1 }}
              transition={{ duration: 1.15, delay: 0.22, ease: [0.22, 1, 0.36, 1] }}
              style={{ transformOrigin: '70px 70px' }}
            />

            {/* Sculptural Vector Monogram "M" drawn smoothly in real time */}
            <motion.path
              d="M44 92 V49 L70 76 L96 49 V92"
              stroke="url(#mrsadMGrad)"
              strokeWidth="5.4"
              strokeLinecap="round"
              strokeLinejoin="round"
              filter="url(#mrsadGlow)"
              initial={{ pathLength: 0, opacity: 0 }}
              animate={{ pathLength: 1, opacity: 1 }}
              transition={{ duration: 0.95, delay: 0.3, ease: [0.22, 1, 0.36, 1] }}
            />

            {/* Crown Accent Diamond Node above M */}
            <motion.circle
              cx="70"
              cy="43"
              r="3.6"
              fill="#B69AEF"
              filter="url(#mrsadGlow)"
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: [0, 1.35, 1], opacity: 1 }}
              transition={{ duration: 0.45, delay: 0.95, ease: 'easeOut' }}
            />
          </motion.svg>

          {/* Cinematic Light Flare Sweep across Emblem */}
          <motion.div
            aria-hidden="true"
            initial={{ x: -90, opacity: 0 }}
            animate={{ x: 90, opacity: [0, 0.7, 0] }}
            transition={{ duration: 0.85, delay: 0.82, ease: 'easeInOut' }}
            className="pointer-events-none absolute h-24 w-7 -skew-x-12 rounded-full bg-gradient-to-r from-transparent via-[#F4F1F8]/35 to-transparent blur-[4px]"
          />
        </div>

        {/* Staggered Editorial Reveal of "mrsad" */}
        <div
          dir="ltr"
          className="mt-6 flex items-baseline justify-center overflow-hidden px-4 py-1"
        >
          {BRAND_LETTERS.map((ch, idx) => (
            <motion.span
              key={ch + idx}
              initial={{ y: 42, opacity: 0, scale: 0.9 }}
              animate={{ y: 0, opacity: 1, scale: 1 }}
              transition={{
                duration: 0.52,
                delay: 0.68 + idx * 0.085,
                ease: [0.16, 1, 0.3, 1],
              }}
              className="num-geo inline-block text-[2.65rem] font-bold tracking-[0.14em]"
              style={{
                background:
                  idx === 0 || idx === 4
                    ? 'linear-gradient(180deg, #F4F1F8 15%, #B69AEF 100%)'
                    : 'linear-gradient(180deg, #F4F1F8 30%, #D9CAF5 100%)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                filter: 'drop-shadow(0 0 18px rgba(128, 82, 214, 0.38))',
              }}
            >
              {ch}
            </motion.span>
          ))}
        </div>

        {/* Expanding Luminous Royal Purple Horizon Line */}
        <motion.div
          aria-hidden="true"
          initial={{ scaleX: 0, opacity: 0 }}
          animate={{ scaleX: 1, opacity: 1 }}
          transition={{ duration: 0.65, delay: 1.18, ease: [0.22, 1, 0.36, 1] }}
          className="mt-2 h-[1.5px] w-40 rounded-full"
          style={{
            background:
              'linear-gradient(90deg, transparent 0%, #8052D6 35%, #B69AEF 50%, #8052D6 65%, transparent 100%)',
          }}
        />

        {/* Subtitle Reveal */}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 1.32, ease: 'easeOut' }}
          className="mt-3.5 flex flex-col items-center gap-1 text-center"
        >
          <span className="font-brand text-[10.5px] font-semibold text-[#B69AEF]/90">
            KAIROS STUDIO
          </span>
          <span className="text-[11px] font-medium text-[#A79EB9]">
            استودیوی نظم، عادت و تمرکز
          </span>
        </motion.div>
      </div>

      {/* Ultra-minimal animated bottom progress bar */}
      <div className="absolute bottom-10 h-[2px] w-28 overflow-hidden rounded-full bg-[#241936]">
        <motion.div
          initial={{ scaleX: 0 }}
          animate={{ scaleX: 1 }}
          transition={{ duration: 2.25, ease: [0.22, 1, 0.36, 1] }}
          style={{ transformOrigin: 'left center' }}
          className="h-full w-full bg-gradient-to-r from-[#8052D6] to-[#B69AEF]"
        />
      </div>
    </motion.div>
  );
}
