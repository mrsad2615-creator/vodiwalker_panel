/**
 * Chrono Core — plans.
 *
 * A "plan" is the whole shape of a timed session: an ordered list of segments,
 * each either work or rest, each with an exact length. Every mode in the app is
 * just a different plan:
 *
 *   pomodoro   → work / short rest … work / long rest   (repeats)
 *   interval   → N work blocks separated by rests, optional long rest
 *   countdown  → one work segment
 *   free       → one open-ended segment (counts up, never ends by itself)
 *
 * Keeping this as pure data means the clock (lib/focusTimer.ts), the UI, the
 * notification and the tests all read the same session definition, and a session
 * can be paused, restored from disk or resumed days later without ambiguity.
 */

export type ChronoMode = 'pomodoro' | 'interval' | 'countdown' | 'free';

export interface ClockSegment {
  /** `focus` is work, `break` is rest. */
  kind: 'focus' | 'break';
  /** Persian label shown on the timeline and in the notification. */
  label: string;
  /** Planned length. `0` marks an open-ended segment (counts up). */
  seconds: number;
  /** A break long enough to be the between-cycles one. */
  long?: boolean;
}

export const CHRONO_MODES: { id: ChronoMode; label: string; hint: string }[] = [
  { id: 'pomodoro', label: 'پومودورو', hint: 'تمرکز و استراحت متناوب، با استراحت بلند پس از چند دور' },
  { id: 'interval', label: 'بلوک‌های عمیق', hint: 'چند بلوک کار برابر، با استراحت کوتاه میان آن‌ها' },
  { id: 'countdown', label: 'شمارش معکوس', hint: 'یک بازهٔ مشخص، بدون استراحت خودکار' },
  { id: 'free', label: 'زمان‌سنج آزاد', hint: 'از صفر بالا می‌رود تا خودتان متوقف کنید' },
];

export const MINUTE = 60;

export interface ChronoConfig {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  cyclesBeforeLong: number;
  /** Interval mode: how many work blocks. */
  blocks: number;
  /** Interval mode: length of a work block, in minutes. */
  blockMinutes: number;
  /** Interval mode: rest between blocks, in minutes. */
  restMinutes: number;
  /** Countdown mode: the single length, in minutes. */
  countdownMinutes: number;
}

export const DEFAULT_CHRONO_CONFIG: ChronoConfig = {
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  cyclesBeforeLong: 4,
  blocks: 4,
  blockMinutes: 45,
  restMinutes: 10,
  countdownMinutes: 30,
};

const clampMinutes = (value: number, fallback: number): number => {
  const n = Number.isFinite(value) ? Math.round(value) : fallback;
  return Math.min(600, Math.max(1, n));
};

const clampCount = (value: number, fallback: number, max = 24): number => {
  const n = Number.isFinite(value) ? Math.floor(value) : fallback;
  return Math.min(max, Math.max(1, n));
};

/**
 * Builds the segment list for a mode. Deterministic and side-effect free, so a
 * restored session can be rebuilt from settings and compared field by field.
 */
export function buildPlan(mode: ChronoMode, config: ChronoConfig): ClockSegment[] {
  const focusMinutes = clampMinutes(config.focusMinutes, DEFAULT_CHRONO_CONFIG.focusMinutes);
  const shortBreak = clampMinutes(config.shortBreakMinutes, DEFAULT_CHRONO_CONFIG.shortBreakMinutes);
  const longBreak = clampMinutes(config.longBreakMinutes, DEFAULT_CHRONO_CONFIG.longBreakMinutes);
  const cyclesBeforeLong = clampCount(config.cyclesBeforeLong, DEFAULT_CHRONO_CONFIG.cyclesBeforeLong, 12);

  if (mode === 'free') {
    return [{ kind: 'focus', label: 'زمان‌سنج آزاد', seconds: 0 }];
  }

  if (mode === 'countdown') {
    return [
      {
        kind: 'focus',
        label: 'شمارش معکوس',
        seconds: clampMinutes(config.countdownMinutes, DEFAULT_CHRONO_CONFIG.countdownMinutes) * MINUTE,
      },
    ];
  }

  if (mode === 'interval') {
    const blocks = clampCount(config.blocks, DEFAULT_CHRONO_CONFIG.blocks);
    const blockSeconds = clampMinutes(config.blockMinutes, DEFAULT_CHRONO_CONFIG.blockMinutes) * MINUTE;
    const restSeconds = clampMinutes(config.restMinutes, DEFAULT_CHRONO_CONFIG.restMinutes) * MINUTE;
    const segments: ClockSegment[] = [];
    for (let block = 0; block < blocks; block += 1) {
      segments.push({ kind: 'focus', label: `بلوک ${block + 1}`, seconds: blockSeconds });
      // No rest after the final block: the session ends on the work that counts.
      if (block < blocks - 1) {
        segments.push({ kind: 'break', label: 'استراحت', seconds: restSeconds });
      }
    }
    return segments;
  }

  // Pomodoro: each cycle is one work segment followed by one rest; the rest that
  // closes every Nth cycle is the long one. The plan covers exactly one full set
  // of cycles and then repeats implicitly via `cycleLength`.
  const segments: ClockSegment[] = [];
  for (let cycle = 0; cycle < cyclesBeforeLong; cycle += 1) {
    segments.push({ kind: 'focus', label: 'تمرکز', seconds: focusMinutes * MINUTE });
    const isLast = cycle === cyclesBeforeLong - 1;
    segments.push({
      kind: 'break',
      label: isLast ? 'استراحت بلند' : 'استراحت کوتاه',
      seconds: (isLast ? longBreak : shortBreak) * MINUTE,
      long: isLast,
    });
  }
  return segments;
}

/** Total planned seconds. `0` when the plan contains an open-ended segment. */
export function planTotalSeconds(plan: ClockSegment[]): number {
  if (plan.some((segment) => segment.seconds <= 0)) return 0;
  return plan.reduce((sum, segment) => sum + segment.seconds, 0);
}

/** Planned work seconds only — what actually lands in the statistics. */
export function planFocusSeconds(plan: ClockSegment[]): number {
  return plan
    .filter((segment) => segment.kind === 'focus' && segment.seconds > 0)
    .reduce((sum, segment) => sum + segment.seconds, 0);
}

export function planWorkSegments(plan: ClockSegment[]): number {
  return plan.filter((segment) => segment.kind === 'focus').length;
}

/** `"۲ بلوک ۴۵ دقیقه‌ای"`-style one-liner for the mode card. */
export function describePlan(plan: ClockSegment[]): string {
  if (plan.length === 0) return 'بدون برنامه';
  if (plan.length === 1 && plan[0].seconds === 0) return 'بدون پایان خودکار';
  const work = plan.filter((segment) => segment.kind === 'focus');
  const total = planTotalSeconds(plan);
  const minutes = Math.round(total / MINUTE);
  return `${work.length} بخش کار · مجموع ${minutes} دقیقه`;
}
