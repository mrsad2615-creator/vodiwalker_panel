import { useMemo } from 'react';
import type { FocusModeType } from '@/types/domain';
import type { PomodoroPhase } from '@/context/AppContext';
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
  /** Focus length of the next session, shown during a break. */
  nextFocusMinutes?: number;
}

/* ---------------------------------------------------------------------------
   Chrono Core
   A chronograph dial, not a dashboard ornament:
     · one bezel hairline, 60 minute ticks (every 5th major), one progress arc
     · a recessed face with a single soft light — no mesh gradients, no pearls
     · numerals in fixed-width cells with tabular figures, so a digit change can
       never shift the layout (the old version re-flowed every second)
     · information is state-driven: focus shows the countdown + finish time,
       break switches the kicker and dims the arc instead of adding new widgets
   --------------------------------------------------------------------------- */

const VIEW = 300;
const C = VIEW / 2;
const BEZEL_R = 146;
const TICK_OUTER = 141;
const TICK_INNER_MAJOR = 129;
const TICK_INNER_MINOR = 135;
const ARC_R = 117;
const FACE_R = 111;
const ARC_CIRCUMFERENCE = 2 * Math.PI * ARC_R;
const TICK_COUNT = 60;

function splitParts(totalSeconds: number): { hours: string | null; minutes: string; seconds: string } {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;
  return {
    hours: hours > 0 ? String(hours).padStart(2, '0') : null,
    minutes: String(minutes).padStart(2, '0'),
    seconds: String(seconds).padStart(2, '0'),
  };
}

function finishClock(remainingSeconds: number): string {
  const target = new Date(Date.now() + remainingSeconds * 1000);
  return toEnDigits(
    `${String(target.getHours()).padStart(2, '0')}:${String(target.getMinutes()).padStart(2, '0')}`
  );
}

/** Every glyph sits in its own fixed cell: no reflow, no jitter, ever. */
function Digits({ value, className }: { value: string; className?: string }) {
  return (
    <span className={`inline-flex ${className ?? ''}`} dir="ltr">
      {value.split('').map((char, index) => (
        <span key={`${char}-${index}`} className="inline-block w-[0.56em] text-center tabular-nums">
          {char}
        </span>
      ))}
    </span>
  );
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
  nextFocusMinutes = 25,
}: CinematicFocusTimerProps) {
  const clamped = Math.max(0, Math.min(100, progressPercent));
  const dashOffset = ARC_CIRCUMFERENCE * (1 - clamped / 100);
  const activeTicks = Math.round((clamped / 100) * TICK_COUNT);
  const size = cinemaMode ? 318 : 276;

  const shown = mode === 'pomodoro' ? remainingSeconds : elapsedSeconds;
  const parts = splitParts(shown);
  const isBreak = mode === 'pomodoro' && phase !== 'focus';

  const kicker =
    mode === 'free'
      ? 'جریان آزاد'
      : phase === 'focus'
      ? 'تمرکز'
      : phase === 'short_break'
      ? 'استراحت کوتاه'
      : 'استراحت بلند';

  const meta = isBreak
    ? `تمرکز بعدی · ${toEnDigits(String(Math.max(1, Math.round(nextFocusMinutes))))} دقیقه`
    : mode === 'pomodoro' && remainingSeconds > 0
    ? `پایان · ${finishClock(remainingSeconds)}`
    : 'بدون محدودیت زمان';

  const ticks = useMemo(
    () =>
      Array.from({ length: TICK_COUNT }, (_, index) => {
        const angle = (index / TICK_COUNT) * Math.PI * 2 - Math.PI / 2;
        const major = index % 5 === 0;
        const inner = major ? TICK_INNER_MAJOR : TICK_INNER_MINOR;
        return {
          index,
          major,
          x1: C + inner * Math.cos(angle),
          y1: C + inner * Math.sin(angle),
          x2: C + TICK_OUTER * Math.cos(angle),
          y2: C + TICK_OUTER * Math.sin(angle),
        };
      }),
    []
  );

  const cycleCount = Math.max(2, Math.min(6, cyclesBeforeLong || 4));
  const cyclesDone = completedCycles % cycleCount;
  const minutesLabel = Math.max(1, Math.round((mode === 'pomodoro' ? shown : elapsedSeconds) / 60));

  return (
    <div
      className="relative select-none"
      style={{ width: size, height: size }}
      role="group"
      aria-label={`${kicker}، ${minutesLabel} دقیقه`}
    >
      <svg width={size} height={size} viewBox={`0 0 ${VIEW} ${VIEW}`} aria-hidden="true" focusable="false">
        <defs>
          <radialGradient id="chrono-face" cx="50%" cy="26%" r="78%">
            <stop offset="0%" stopColor="var(--surface-2)" />
            <stop offset="72%" stopColor="var(--surface-input)" />
            <stop offset="100%" stopColor="var(--bg)" />
          </radialGradient>
        </defs>

        {/* Bezel */}
        <circle cx={C} cy={C} r={BEZEL_R} fill="none" stroke="rgba(255,255,255,0.07)" strokeWidth="1" />

        {/* Indices: real minute marks, lit by actual progress */}
        {ticks.map((tick) => {
          const lit = tick.index < activeTicks;
          return (
            <line
              key={tick.index}
              x1={tick.x1}
              y1={tick.y1}
              x2={tick.x2}
              y2={tick.y2}
              stroke={
                lit
                  ? tick.major
                    ? 'var(--accent-fg)'
                    : 'color-mix(in srgb, var(--accent-fg) 55%, transparent)'
                  : tick.major
                  ? 'rgba(255,255,255,0.20)'
                  : 'rgba(255,255,255,0.09)'
              }
              strokeWidth={tick.major ? 1.6 : 0.9}
              strokeLinecap="butt"
            />
          );
        })}

        {/* Recessed face */}
        <circle
          cx={C}
          cy={C}
          r={FACE_R}
          fill="url(#chrono-face)"
          stroke="rgba(255,255,255,0.08)"
          strokeWidth="1"
        />

        {/* Progress: a single arc. One update per second, linear sweep. */}
        <circle
          cx={C}
          cy={C}
          r={ARC_R}
          fill="none"
          stroke="rgba(255,255,255,0.07)"
          strokeWidth="3"
        />
        <circle
          cx={C}
          cy={C}
          r={ARC_R}
          fill="none"
          stroke="var(--accent-fg)"
          strokeOpacity={isBreak ? 0.5 : 1}
          strokeWidth="3"
          strokeLinecap="round"
          strokeDasharray={ARC_CIRCUMFERENCE}
          strokeDashoffset={dashOffset}
          transform={`rotate(-90 ${C} ${C})`}
          style={{ transition: isRunning ? 'stroke-dashoffset 1s linear' : 'none' }}
        />
      </svg>

      {/* Read-out */}
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
        <span
          className="font-display-caps text-[9px] font-semibold text-[var(--text-3)]"
          style={{ letterSpacing: '0.28em' }}
        >
          {isRunning ? kicker : `متوقف · ${kicker}`}
        </span>

        <div
          className="mt-1 flex items-baseline justify-center text-[var(--text)]"
          style={{ fontFamily: 'var(--font-numeric)', fontWeight: 500, lineHeight: 1 }}
        >
          {parts.hours && (
            <>
              <Digits value={parts.hours} className="text-[1.6rem] opacity-80" />
              <span className="mx-[0.08em] text-[1.4rem] text-[var(--text-3)]">:</span>
            </>
          )}
          <Digits value={parts.minutes} className={cinemaMode ? 'text-[4.4rem]' : 'text-[3.9rem]'} />
          <span className="mx-[0.06em] text-[2.6rem] text-[var(--text-3)]">:</span>
          <Digits value={parts.seconds} className={cinemaMode ? 'text-[4.4rem]' : 'text-[3.9rem]'} />
        </div>

        <span className="num-geo mt-1.5 text-[10px] tracking-wider text-[var(--text-2)]">{meta}</span>

        {mode === 'pomodoro' && (
          <div className="mt-3 flex items-center gap-[5px]" aria-hidden="true">
            {Array.from({ length: cycleCount }, (_, index) => {
              const done = index < cyclesDone;
              const current = index === cyclesDone && phase === 'focus';
              return (
                <span
                  key={index}
                  className="block rounded-[1px] transition-colors"
                  style={{
                    width: 14,
                    height: 2,
                    background: done
                      ? 'var(--accent-fg)'
                      : current
                      ? 'color-mix(in srgb, var(--accent-fg) 55%, transparent)'
                      : 'rgba(255,255,255,0.12)',
                  }}
                />
              );
            })}
          </div>
        )}

        {linkedTaskTitle && (
          <span className="mt-2.5 max-w-[170px] truncate text-[10px] text-[var(--text-3)]">
            {linkedTaskTitle}
          </span>
        )}
      </div>
    </div>
  );
}
