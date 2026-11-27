import type { FocusModeType } from '@/types/domain';
import type { ClockSegment } from '@/lib/chrono/plan';

/** Mirrors `PomodoroPhase` in AppContext (kept local so this module stays pure). */
export type PomodoroPhase = 'focus' | 'short_break' | 'long_break';

/**
 * Pure focus-clock arithmetic — anchored to absolute time.
 *
 * ── Why this file was rewritten ──────────────────────────────────────────────
 * The previous version advanced by `Math.max(1, Math.round(deltaMs / 1000))` on
 * every 250 ms tick. `Math.round(250/1000)` is 0, so the clamp turned *every*
 * tick into a full second: the countdown ran ~4× faster than wall-clock and the
 * digits flickered four times a second.
 *
 * The clock is now anchored: starting a run records `anchorMs` (epoch ms) plus
 * the value the run started from. Any tick — 250 ms later, five minutes later,
 * after the WebView was frozen, after the phone slept — is answered by the same
 * one-line formula:
 *
 *     wholeSeconds = floor((now - anchorMs) / 1000)
 *
 * That is monotonic, drift-free, immune to throttled timers, and it makes the
 * display change exactly once per second. There is no accumulator to fall out
 * of sync and no rounding to lose time.
 *
 * ── Chrono Core (1.8.0) ──────────────────────────────────────────────────────
 * The clock is now **segment driven**. A session is a list of `ClockSegment`s
 * (see lib/chrono/plan.ts); pomodoro, interval blocks, a single countdown and
 * the open stopwatch are all just different plans. A state without an explicit
 * `plan` derives the pomodoro plan from its own duration fields, which is why
 * the 1.5.0 behaviour and tests are preserved bit for bit.
 *
 * Boundaries are crossed by *walking the plan*, not by one step per tick: an app
 * that was frozen for an hour returns inside the correct segment with the
 * correct remaining time, instead of ten segments behind.
 */

export interface FocusClockState {
  mode: FocusModeType;
  phase: PomodoroPhase;
  isRunning: boolean;
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  remainingSeconds: number;
  elapsedSeconds: number;
  completedCycles: number;
  linkedTaskId: string | null;
  sessionStartedAt: string | null;
  /** Epoch ms when the current run segment started. `null` while paused. */
  anchorMs: number | null;
  /** `remainingSeconds` captured at the anchor (pomodoro). */
  anchorRemaining: number;
  /** `elapsedSeconds` captured at the anchor (free mode / session total). */
  anchorElapsed: number;

  // ── Chrono Core ────────────────────────────────────────────────────────────
  /** Explicit session shape. Absent → derived from the duration fields. */
  plan?: ClockSegment[];
  /** Cursor into `plan`. */
  segmentIndex?: number;
  /** Walk into the next segment automatically instead of waiting for a tap. */
  autoAdvance?: boolean;
  /** Every plan segment has been consumed. */
  finished?: boolean;
  /** Focus seconds banked from segments already completed in this session. */
  accruedFocusSeconds?: number;
  /** Portion already recorded in focus history, to avoid counting it twice. */
  recordedFocusSeconds?: number;
}

export interface FocusTickResult {
  state: FocusClockState;
  /** Set on the tick that ends a phase. */
  completedPhase: 'focus' | 'break' | null;
  /** Focused seconds for the run that just completed (0 otherwise). */
  completedFocusSeconds: number;
  /** Set when the tick ended the whole plan. */
  planFinished?: boolean;
}

/** Whole seconds a running clock has advanced since its anchor. */
export function elapsedWholeSeconds(state: FocusClockState, nowMs: number): number {
  if (!state.isRunning || state.anchorMs === null) return 0;
  return Math.max(0, Math.floor((nowMs - state.anchorMs) / 1000));
}

/** Display values for "right now", without mutating or emitting anything. */
export function readClock(
  state: FocusClockState,
  nowMs: number
): { remainingSeconds: number; elapsedSeconds: number } {
  const whole = elapsedWholeSeconds(state, nowMs);
  const segment = currentSegment(state);
  if (state.mode === 'free' || (segment && segment.seconds <= 0)) {
    return { remainingSeconds: 0, elapsedSeconds: state.anchorElapsed + whole };
  }
  return {
    remainingSeconds: Math.max(0, state.anchorRemaining - whole),
    elapsedSeconds: state.anchorElapsed + whole,
  };
}

/**
 * Fresh anchors for a run that starts (or resumes) at `nowMs`.
 *
 * The anchor always describes the **segment start**, never "now": the clock
 * remembers how far into the segment we already were and back-dates the anchor by
 * that much. One invariant then covers the whole engine — `anchorMs` is the moment
 * the segment began, `anchorRemaining` its full length, `anchorElapsed` zero — so
 * pausing, resuming and walking into the next segment cannot double-count.
 */
export function startClock(state: FocusClockState, nowMs: number): FocusClockState {
  if (state.isRunning) return state;
  const total = phaseDurationSeconds(state);
  const elapsed = Math.max(0, total > 0 ? total - state.remainingSeconds : state.elapsedSeconds);
  return {
    ...state,
    isRunning: true,
    anchorMs: nowMs - elapsed * 1000,
    anchorRemaining: total > 0 ? total : 0,
    anchorElapsed: 0,
    sessionStartedAt: state.sessionStartedAt ?? new Date(nowMs).toISOString(),
  };
}

/** Freeze the current values so a later resume continues from the same second. */
export function pauseClock(state: FocusClockState, nowMs: number): FocusClockState {
  if (!state.isRunning) return state;
  const display = readClock(state, nowMs);
  return {
    ...state,
    isRunning: false,
    anchorMs: null,
    remainingSeconds: display.remainingSeconds,
    elapsedSeconds: display.elapsedSeconds,
    anchorRemaining: display.remainingSeconds,
    anchorElapsed: display.elapsedSeconds,
  };
}

// ── Plan helpers ────────────────────────────────────────────────────────────

const BREAK_PHASE = (segment: ClockSegment): PomodoroPhase =>
  segment.long ? 'long_break' : 'short_break';

/** The plan a state runs on: explicit when given, otherwise derived pomodoro. */
export function resolvePlan(state: FocusClockState, cyclesBeforeLong = 4): ClockSegment[] {
  if (state.plan && state.plan.length > 0) return state.plan;
  const focus = Math.max(1, Math.round(state.focusMinutes)) * 60;
  const shortBreak = Math.max(1, Math.round(state.shortBreakMinutes)) * 60;
  const longBreak = Math.max(1, Math.round(state.longBreakMinutes)) * 60;
  const cycles = Math.min(12, Math.max(1, Math.floor(cyclesBeforeLong) || 1));
  const plan: ClockSegment[] = [];
  for (let cycle = 0; cycle < cycles; cycle += 1) {
    plan.push({ kind: 'focus', label: 'تمرکز', seconds: focus });
    const isLast = cycle === cycles - 1;
    plan.push({
      kind: 'break',
      label: isLast ? 'استراحت بلند' : 'استراحت کوتاه',
      seconds: isLast ? longBreak : shortBreak,
      long: isLast,
    });
  }
  return plan;
}

/**
 * Cursor into the plan. An explicit plan carries its own index; a derived
 * pomodoro plan is positioned from `phase` + `completedCycles`, which is what
 * makes a restored or hand-built state land on the right segment.
 */
export function segmentIndexFor(state: FocusClockState, plan: ClockSegment[]): number {
  const raw = Math.floor(state.segmentIndex ?? -1);
  if (raw >= 0) return Math.min(plan.length - 1, Math.max(0, raw));

  const cycles = Math.max(1, plan.filter((segment) => segment.kind === 'focus').length);
  const focusCount = Math.max(0, Math.floor(state.completedCycles));

  if (state.phase === 'long_break') return plan.length - 1;
  if (state.phase === 'short_break') {
    // After the k-th finished focus (k ≥ 1) we are in the break of cycle k.
    const k = focusCount > 0 ? focusCount : 1;
    return Math.min(plan.length - 1, ((k - 1) % cycles) * 2 + 1);
  }
  return Math.min(plan.length - 1, (focusCount % cycles) * 2);
}

export function currentSegment(state: FocusClockState, cyclesBeforeLong = 4): ClockSegment | null {
  const plan = resolvePlan(state, cyclesBeforeLong);
  if (plan.length === 0) return null;
  return plan[segmentIndexFor(state, plan)];
}

/** True when the mode counts up and has no natural end. */
export function isOpenEnded(state: FocusClockState): boolean {
  if (state.mode === 'free') return true;
  const segment = currentSegment(state);
  return !segment || segment.seconds <= 0;
}

/** Next cursor, or `null` when the plan is over. Pomodoro loops back to cycle 1. */
function nextIndex(plan: ClockSegment[], index: number, cyclic: boolean): number | null {
  const next = index + 1;
  if (next < plan.length) return next;
  return cyclic ? 0 : null;
}

/**
 * Advances the clock to `nowMs`. Returns the *same* object when nothing the UI
 * can see has changed, so React never re-renders four times a second.
 */
export function tickClock(
  state: FocusClockState,
  nowMs: number,
  cyclesBeforeLong: number
): FocusTickResult {
  if (!state.isRunning || state.anchorMs === null) {
    return { state, completedPhase: null, completedFocusSeconds: 0 };
  }

  const plan = resolvePlan(state, cyclesBeforeLong);
  const cyclic = state.mode === 'pomodoro';
  const autoAdvance = state.autoAdvance === true;
  const accrued = state.accruedFocusSeconds ?? 0;

  let index = segmentIndexFor(state, plan);
  // Absolute moment the current segment began. `anchorElapsed` is how far into
  // the segment we already were when the run started, so subtracting it puts the
  // cursor back at the true segment start.
  let segmentStartMs = state.anchorMs - state.anchorElapsed * 1000;

  let remaining = remainingFor(plan[index], segmentStartMs, nowMs);
  let completedPhase: 'focus' | 'break' | null = null;
  let focusSeconds = 0;
  let cycles = state.completedCycles;
  let advanced = false;
  let finished = false;
  let guard = 0;

  while (remaining <= 0 && guard < 512) {
    guard += 1;
    const segment = plan[index];
    // An open-ended segment never completes, however long it runs.
    if (segment.seconds <= 0) break;

    const segmentsEnd = segmentStartMs + segment.seconds * 1000;
    if (segment.kind === 'focus') {
      focusSeconds += segment.seconds;
      cycles += 1;
    }
    completedPhase = segment.kind === 'focus' ? 'focus' : 'break';

    const next = nextIndex(plan, index, cyclic);
    if (next === null) {
      finished = true;
      break;
    }

    index = next;
    segmentStartMs = segmentsEnd;
    advanced = true;
    remaining = remainingFor(plan[index], segmentStartMs, nowMs);

    // Without auto-advance the next segment is not started by the clock: it waits,
    // full length, for the reader to press start. That is also why a session left
    // overnight resumes on a clean segment instead of a half-eaten one.
    if (!autoAdvance) break;
    if (plan[index].seconds <= 0) break;
  }

  const segment = plan[index];

  if (finished) {
    return {
      state: {
        ...state,
        isRunning: false,
        anchorMs: null,
        finished: true,
        remainingSeconds: 0,
        elapsedSeconds: segment.seconds,
        anchorRemaining: 0,
        anchorElapsed: segment.seconds,
        completedCycles: cycles,
        accruedFocusSeconds: accrued + focusSeconds,
        sessionStartedAt: null,
      },
      completedPhase,
      completedFocusSeconds: focusSeconds,
      planFinished: true,
    };
  }

  if (!advanced) {
    // Still inside the same segment: only the display can have changed.
    const elapsedSeconds =
      segment.seconds <= 0
        ? state.anchorElapsed + Math.max(0, Math.floor((nowMs - state.anchorMs) / 1000))
        : Math.max(0, segment.seconds - Math.max(0, remaining));
    if (elapsedSeconds === state.elapsedSeconds) {
      return { state, completedPhase: null, completedFocusSeconds: 0 };
    }
    return {
      state: {
        ...state,
        remainingSeconds: Math.max(0, remaining),
        elapsedSeconds,
      },
      completedPhase: null,
      completedFocusSeconds: 0,
    };
  }

  // A boundary (or several) was crossed. When the clock keeps running, re-anchor
  // at the exact boundary so the next tick keeps whole-second precision instead
  // of inheriting this arithmetic. When it stops, the new segment starts clean.
  const elapsedThisSegment = autoAdvance
    ? Math.max(0, segment.seconds - Math.max(0, remaining))
    : 0;
  const shownRemaining = autoAdvance ? Math.max(0, remaining) : segment.seconds;

  // The anchor describes the *segment start*, so `anchorRemaining` is the full
  // segment length and `anchorElapsed` is zero. Anchoring to "now, with what is
  // left" would double-count: the display and the tick would each subtract the
  // same seconds, and a resume after a pause would lose a minute.
  const segmentStartAnchor = segmentStartMs + 0;

  return {
    state: {
      ...state,
      phase: segment.kind === 'focus' ? 'focus' : BREAK_PHASE(segment),
      segmentIndex: index,
      isRunning: autoAdvance,
      anchorMs: autoAdvance ? segmentStartAnchor : null,
      remainingSeconds: shownRemaining,
      elapsedSeconds: elapsedThisSegment,
      anchorRemaining: segment.seconds,
      anchorElapsed: 0,
      completedCycles: cycles,
      accruedFocusSeconds: accrued + focusSeconds,
      sessionStartedAt: autoAdvance ? state.sessionStartedAt : null,
      finished: false,
    },
    completedPhase,
    completedFocusSeconds: focusSeconds,
  };
}

/** Seconds left in a segment that began at `segmentStartMs`. */
function remainingFor(segment: ClockSegment, segmentStartMs: number, nowMs: number): number {
  if (segment.seconds <= 0) return 0;
  const into = Math.floor((nowMs - segmentStartMs) / 1000);
  return segment.seconds - into;
}

/**
 * Total focused seconds banked by this session *so far*: completed segments plus
 * the part of the current segment that has already run. This — not the current
 * segment's elapsed time — is what gets written to the statistics.
 */
export function totalFocusSeconds(
  state: FocusClockState,
  nowMs: number,
  cyclesBeforeLong = 4
): number {
  const accrued = state.accruedFocusSeconds ?? 0;
  // A finished session has already banked its last segment; adding it twice is
  // how a 45-minute block turns into 90 in the statistics.
  if (state.finished) return accrued;
  const segment = currentSegment(state, cyclesBeforeLong);
  if (!segment || segment.kind !== 'focus') return accrued;
  return accrued + readClock(state, nowMs).elapsedSeconds;
}

/** Focused seconds for a phase that is being ended manually (finish & save). */
export function focusSecondsNow(state: FocusClockState, nowMs: number): number {
  return readClock(state, nowMs).elapsedSeconds;
}

export function phaseDurationSeconds(state: FocusClockState): number {
  if (state.plan && state.plan.length > 0) {
    const segment = state.plan[segmentIndexFor(state, state.plan)];
    return Math.max(0, segment?.seconds ?? 0);
  }
  if (state.mode === 'free') return 0;
  if (state.phase === 'focus') return Math.max(1, state.focusMinutes) * 60;
  if (state.phase === 'short_break') return Math.max(1, state.shortBreakMinutes) * 60;
  return Math.max(1, state.longBreakMinutes) * 60;
}

/** 0…1 progress through the current segment (0 for open-ended segments). */
export function segmentProgress(state: FocusClockState, remainingSeconds: number): number {
  const total = phaseDurationSeconds(state);
  if (total <= 0) return 0;
  return Math.min(1, Math.max(0, (total - remainingSeconds) / total));
}

/** Positions the clock on a specific segment, paused at its start. */
export function seekSegment(
  state: FocusClockState,
  index: number,
  cyclesBeforeLong = 4
): FocusClockState {
  const plan = resolvePlan(state, cyclesBeforeLong);
  if (plan.length === 0) return state;
  const safe = Math.min(plan.length - 1, Math.max(0, Math.floor(index)));
  const segment = plan[safe];
  return {
    ...state,
    segmentIndex: safe,
    phase: segment.kind === 'focus' ? 'focus' : BREAK_PHASE(segment),
    isRunning: false,
    anchorMs: null,
    remainingSeconds: segment.seconds,
    elapsedSeconds: 0,
    anchorRemaining: segment.seconds,
    anchorElapsed: 0,
    sessionStartedAt: null,
    finished: false,
  };
}
