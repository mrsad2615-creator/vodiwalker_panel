/**
 * Breathing pacer.
 *
 * Self-awareness work in this app is a *paced breath*, not a claim about brain
 * waves: the user sees and hears exactly which phase they are in, for a pattern
 * they chose, and the maths below is the whole implementation.
 */

export type BreathPhase = 'inhale' | 'hold' | 'exhale' | 'rest';

export interface BreathPattern {
  id: string;
  label: string;
  hint: string;
  steps: { phase: BreathPhase; seconds: number }[];
}

export const BREATH_PATTERNS: BreathPattern[] = [
  {
    id: 'box',
    label: 'جعبه‌ای ۴-۴-۴-۴',
    hint: 'تنفس متوازن؛ لنگر توجه',
    steps: [
      { phase: 'inhale', seconds: 4 },
      { phase: 'hold', seconds: 4 },
      { phase: 'exhale', seconds: 4 },
      { phase: 'rest', seconds: 4 },
    ],
  },
  {
    id: '478',
    label: 'آرام‌سازی ۴-۷-۸',
    hint: 'دم کوتاه، نگه‌داشت بلند، بازدم کشیده',
    steps: [
      { phase: 'inhale', seconds: 4 },
      { phase: 'hold', seconds: 7 },
      { phase: 'exhale', seconds: 8 },
    ],
  },
  {
    id: 'coherent',
    label: 'هماهنگ ۵-۵',
    hint: 'دم و بازدم برابر؛ ساده‌ترین شروع',
    steps: [
      { phase: 'inhale', seconds: 5 },
      { phase: 'exhale', seconds: 5 },
    ],
  },
];

export const PHASE_LABEL: Record<BreathPhase, string> = {
  inhale: 'دم',
  hold: 'نگه‌داشت',
  exhale: 'بازدم',
  rest: 'توقف',
};

export function patternDuration(pattern: BreathPattern): number {
  return pattern.steps.reduce((acc, step) => acc + step.seconds, 0);
}

export interface BreathState {
  /** Index of the active step. */
  stepIndex: number;
  phase: BreathPhase;
  /** 0–1 through the current step. */
  progress: number;
  /** Completed full cycles since the pacer started. */
  cycles: number;
  /** Seconds left in the current step. */
  secondsLeft: number;
}

/**
 * Resolves the pacer state for a given elapsed time. Stateless, so a paused tab,
 * a slow frame or a re-mount can never desynchronise the animation.
 */
export function breathStateAt(pattern: BreathPattern, elapsedMs: number): BreathState {
  const total = Math.max(1, patternDuration(pattern));
  const safeElapsed = Math.max(0, Number.isFinite(elapsedMs) ? elapsedMs : 0);
  const seconds = safeElapsed / 1000;

  const cycleIndex = Math.floor(seconds / total);
  let intoCycle = seconds - cycleIndex * total;

  for (let i = 0; i < pattern.steps.length; i += 1) {
    const step = pattern.steps[i];
    if (intoCycle < step.seconds) {
      return {
        stepIndex: i,
        phase: step.phase,
        progress: intoCycle / step.seconds,
        cycles: cycleIndex,
        secondsLeft: Math.max(0, Math.ceil(step.seconds - intoCycle)),
      };
    }
    intoCycle -= step.seconds;
  }

  // Exactly on a boundary: report the first step of the next cycle.
  const first = pattern.steps[0];
  return {
    stepIndex: 0,
    phase: first.phase,
    progress: 0,
    cycles: cycleIndex + 1,
    secondsLeft: first.seconds,
  };
}
