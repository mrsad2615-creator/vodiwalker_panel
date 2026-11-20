import { useMemo } from 'react';
import { Sparkles } from 'lucide-react';
import type { FocusModeType } from '@/types/domain';
import type { PomodoroPhase } from '@/context/AppContext';
import { Numeric } from '@/components/ui/Numeric';
import { toEnDigits } from '@/lib/format';

interface CinematicFocusTimerProps {
  mode: FocusModeType;
  phase: PomodoroPhase;
  isRunning: boolean;
  remainingSeconds: number;
  elapsedSeconds: number;
  progressPercent: number;
  completedCycles: number;
  cyclesBeforeLong: number;
  linkedTaskTitle: string | null;
  cinemaMode?: boolean;
}

const TICK_COUNT = 60;

function formatSplitTime(totalSeconds: number): { main: string; sub: string; hours: string | null } {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hrs = Math.floor(safe / 3600);
  const mins = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  return {
    hours: hrs > 0 ? String(hrs).padStart(2, '0') : null,
    main: String(mins).padStart(2, '0'),
    sub: String(secs).padStart(2, '0'),
  };
}

function formatFinishClock(remainingSeconds: number): string {
  const target = new Date(Date.now() + remainingSeconds * 1000);
  const hh = String(target.getHours()).padStart(2, '0');
  const mm = String(target.getMinutes()).padStart(2, '0');
  return toEnDigits(`${hh}:${mm}`);
}

export function CinematicFocusTimer({
  mode,
  phase,
  isRunning,
  remainingSeconds,
  elapsedSeconds,
  progressPercent,
  completedCycles,
  cyclesBeforeLong,
  linkedTaskTitle,
  cinemaMode = false,
}: CinematicFocusTimerProps) {
  const clamped = Math.max(0, Math.min(100, progressPercent));
  const size = cinemaMode ? 316 : 278;
  const viewSize = 300;
  const center = viewSize / 2;
  const arcRadius = 114;
  const circumference = 2 * Math.PI * arcRadius;
  const dashOffset = circumference * (1 - clamped / 100);

  // Exact orbital tip coordinates (starting at 12 o'clock = -90 deg)
  const angleRad = ((clamped / 100) * 360 - 90) * (Math.PI / 180);
  const tipX = center + arcRadius * Math.cos(angleRad);
  const tipY = center + arcRadius * Math.sin(angleRad);

  const activeTicks = Math.round((clamped / 100) * TICK_COUNT);

  const ticks = useMemo(() => {
    return Array.from({ length: TICK_COUNT }, (_, i) => {
      const deg = (i / TICK_COUNT) * 360 - 90;
      const rad = (deg * Math.PI) / 180;
      const isMajor = i % 5 === 0;
      const innerR = isMajor ? 127 : 131;
      const outerR = 138;
      return {
        index: i,
        isMajor,
        x1: center + innerR * Math.cos(rad),
        y1: center + innerR * Math.sin(rad),
        x2: center + outerR * Math.cos(rad),
        y2: center + outerR * Math.sin(rad),
      };
    });
  }, [center]);

  const timeParts =
    mode === 'pomodoro'
      ? formatSplitTime(remainingSeconds)
      : formatSplitTime(elapsedSeconds);

  const phaseLabel =
    mode === 'free'
      ? 'تایمر آزاد عمیق'
      : phase === 'focus'
      ? 'تمرکز عمیق'
      : phase === 'short_break'
      ? 'استراحت کوتاه'
      : 'استراحت بلند';

  const etaText =
    mode === 'pomodoro' && remainingSeconds > 0
      ? formatFinishClock(remainingSeconds)
      : null;

  const isBreak = mode === 'pomodoro' && phase !== 'focus';
  const totalDots = Math.max(2, Math.min(6, cyclesBeforeLong || 4));
  const filledInBlock = completedCycles % totalDots;

  return (
    <div
      className="relative flex items-center justify-center select-none"
      style={{ width: size, height: size }}
    >
      {/* Pre-baked Radial Obsidian-Purple Stage Spotlight (Zero blur filter overhead) */}
      <div
        aria-hidden="true"
        className={`pointer-events-none absolute -inset-8 rounded-full transition-opacity duration-700 ${
          isRunning ? 'focus-cinema-halo opacity-100' : 'opacity-45'
        }`}
        style={{
          background: isBreak
            ? 'radial-gradient(circle, rgba(169, 155, 240, 0.10) 0%, rgba(10, 10, 12, 0) 70%)'
            : 'radial-gradient(circle, rgba(110, 86, 207, 0.16) 0%, rgba(10, 10, 12, 0) 70%)',
        }}
      />

      {/* Precision Chronometer SVG Bezel */}
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${viewSize} ${viewSize}`}
        className="relative z-10 overflow-visible"
        aria-hidden="true"
      >
        <defs>
          <radialGradient id="chronoFaceGrad" cx="50%" cy="28%" r="72%">
            <stop offset="0%" stopColor="#1D1630" />
            <stop offset="56%" stopColor="#101014" />
            <stop offset="100%" stopColor="#0A0A0C" />
          </radialGradient>

          <linearGradient id="chronoArcGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#ECECEF" />
            <stop offset="32%" stopColor="#A99BF0" />
            <stop offset="100%" stopColor="#6E56CF" />
          </linearGradient>

          <linearGradient id="chronoBreakGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#ECECEF" />
            <stop offset="60%" stopColor="#A99BF0" />
            <stop offset="100%" stopColor="#6D44BA" />
          </linearGradient>
        </defs>

        {/* Outer Architectural Hairline Ring */}
        <circle
          cx={center}
          cy={center}
          r={144}
          fill="none"
          stroke="rgba(182, 154, 239, 0.10)"
          strokeWidth="1"
        />

        {/* 60 Watchmaker Precision Ticks */}
        {ticks.map((t) => {
          const lit = t.index < activeTicks;
          const stroke = lit
            ? t.isMajor
              ? '#A99BF0'
              : '#6E56CF'
            : t.isMajor
            ? 'rgba(182, 154, 239, 0.28)'
            : 'rgba(182, 154, 239, 0.11)';

          return (
            <line
              key={t.index}
              x1={t.x1}
              y1={t.y1}
              x2={t.x2}
              y2={t.y2}
              stroke={stroke}
              strokeWidth={t.isMajor ? 2 : 1.1}
              strokeLinecap="round"
            />
          );
        })}

        {/* Recessed Sculpted Obsidian Watch Face */}
        <circle
          cx={center}
          cy={center}
          r={104}
          fill="url(#chronoFaceGrad)"
          stroke="rgba(182, 154, 239, 0.15)"
          strokeWidth="1"
        />

        {/* Delicate Inner Decorative Rim */}
        <circle
          cx={center}
          cy={center}
          r={96}
          fill="none"
          stroke="rgba(244, 241, 248, 0.04)"
          strokeWidth="1"
          strokeDasharray="3 6"
        />

        {/* Progress Track Groove */}
        <circle
          cx={center}
          cy={center}
          r={arcRadius}
          fill="none"
          stroke="rgba(182, 154, 239, 0.09)"
          strokeWidth="5.5"
        />

        {/* Soft Wide Ambient Arc Backing (Zero filter cost!) */}
        {clamped > 0.3 && (
          <circle
            cx={center}
            cy={center}
            r={arcRadius}
            fill="none"
            stroke="rgba(128, 82, 214, 0.24)"
            strokeWidth="12"
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={dashOffset}
            transform={`rotate(-90 ${center} ${center})`}
            style={{ transition: 'stroke-dashoffset 0.55s cubic-bezier(0.22, 1, 0.36, 1)' }}
          />
        )}

        {/* Primary Razor-Sharp Luminous Progress Arc */}
        <circle
          cx={center}
          cy={center}
          r={arcRadius}
          fill="none"
          stroke={isBreak ? 'url(#chronoBreakGrad)' : 'url(#chronoArcGrad)'}
          strokeWidth="5.5"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={dashOffset}
          transform={`rotate(-90 ${center} ${center})`}
          style={{ transition: 'stroke-dashoffset 0.55s cubic-bezier(0.22, 1, 0.36, 1)' }}
        />

        {/* Orbiting Luminous Pearl Tip */}
        {clamped > 0.3 && (
          <g>
            <circle cx={tipX} cy={tipY} r={10} fill="rgba(182, 154, 239, 0.22)" />
            <circle cx={tipX} cy={tipY} r={4.5} fill="#ECECEF" />
          </g>
        )}
      </svg>

      {/* Centerpiece Editorial Telemetry Overlay */}
      <div className="pointer-events-none absolute inset-0 z-20 flex flex-col items-center justify-center px-8 text-center">
        {/* Phase Status Micro-Badge */}
        <div className="mb-2 inline-flex items-center gap-1.5 rounded-full border border-[#A99BF0]/22 bg-[#1B152A]/90 px-2.5 py-0.5">
          <span
            className={`h-1.5 w-1.5 rounded-full ${
              isRunning
                ? 'focus-live-dot bg-[#A99BF0]'
                : 'bg-[#6B6B75]'
            }`}
          />
          <span className="text-[10px] font-semibold text-[#CFC6F0]">{phaseLabel}</span>
        </div>

        {/* Monumental Condensed Tabular Clock */}
        <div
          dir="ltr"
          className="num-display flex items-baseline justify-center font-extrabold tracking-tight"
        >
          {timeParts.hours && (
            <>
              <span
                className={
                  cinemaMode
                    ? 'text-[4.2rem] text-[#ECECEF]'
                    : 'text-[3.5rem] text-[#ECECEF]'
                }
              >
                {timeParts.hours}
              </span>
              <span className="mx-0.5 text-[2.6rem] font-bold text-[#6E56CF]">:</span>
            </>
          )}
          <span
            className={
              cinemaMode
                ? 'text-[5.25rem] text-[#ECECEF]'
                : 'text-[4.45rem] text-[#ECECEF]'
            }
          >
            {timeParts.main}
          </span>
          <span
            className={`mx-1 font-bold text-[#A99BF0]/85 ${
              cinemaMode ? 'text-[3.6rem]' : 'text-[3.1rem]'
            }`}
          >
            :
          </span>
          <span
            className={
              cinemaMode
                ? 'text-[5.25rem] text-[#E6E6EA]'
                : 'text-[4.45rem] text-[#E6E6EA]'
            }
          >
            {timeParts.sub}
          </span>
        </div>

        {/* Finish ETA or Elapsed Status */}
        <div className="mt-1.5 flex items-center justify-center gap-1.5 text-[10.5px] text-[#9C9CA6]">
          {etaText ? (
            <>
              <span>پایان</span>
              <Numeric value={etaText} className="font-semibold text-[#A99BF0]" />
            </>
          ) : (
            <span className="inline-flex items-center gap-1 text-[#9C9CA6]">
              <Sparkles className="h-3 w-3 text-[#6E56CF]" />
              جریان تمرکز آزاد
            </span>
          )}
        </div>

        {/* Pomodoro Cycle Constellation Dots */}
        {mode === 'pomodoro' && (
          <div className="mt-2.5 flex items-center gap-1.5" aria-label="چرخه‌های پومودورو">
            {Array.from({ length: totalDots }, (_, idx) => {
              const done = idx < filledInBlock;
              const current = idx === filledInBlock && phase === 'focus';
              return (
                <span
                  key={idx}
                  className={`h-1.5 rounded-full transition-all ${
                    done
                      ? 'w-3.5 bg-[#A99BF0]'
                      : current
                      ? 'w-2.5 bg-[#6E56CF]'
                      : 'w-1.5 bg-[#A99BF0]/20'
                  }`}
                />
              );
            })}
          </div>
        )}

        {/* Linked Task Ribbon Inside Dial */}
        {linkedTaskTitle && (
          <p className="mt-2 max-w-[162px] truncate rounded-lg bg-[#0A0A0C]/70 px-2.5 py-0.5 text-[10px] font-medium text-[#CFC6F0]">
            {linkedTaskTitle}
          </p>
        )}
      </div>
    </div>
  );
}
