import type { FocusModeType } from '@/types/domain';

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
}

export interface FocusTickResult {
  state: FocusClockState;
  /** Set on the tick that ends a phase. */
  completedPhase: 'focus' | 'break' | null;
  /** Focused seconds for the run that just completed (0 otherwise). */
  completedFocusSeconds: number;
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
  if (state.mode === 'free') {
    return { remainingSeconds: 0, elapsedSeconds: state.anchorElapsed + whole };
  }
  return {
    remainingSeconds: Math.max(0, state.anchorRemaining - whole),
    elapsedSeconds: state.anchorElapsed + whole,
  };
}

/** Fresh anchors for a run that starts (or resumes) at `nowMs`. */
export function startClock(state: FocusClockState, nowMs: number): FocusClockState {
  return {
    ...state,
    isRunning: true,
    anchorMs: nowMs,
    anchorRemaining: state.remainingSeconds,
    anchorElapsed: state.elapsedSeconds,
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

function nextPhaseAfterFocus(
  state: FocusClockState,
  cyclesBeforeLong: number
): { phase: PomodoroPhase; durationSeconds: number; completedCycles: number } {
  const completedCycles = state.completedCycles + 1;
  const cycleLength = Math.max(1, Math.floor(cyclesBeforeLong) || 1);
  const isLong = completedCycles % cycleLength === 0;
  return {
    phase: isLong ? 'long_break' : 'short_break',
    durationSeconds: Math.max(1, isLong ? state.longBreakMinutes : state.shortBreakMinutes) * 60,
    completedCycles,
  };
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

  const whole = elapsedWholeSeconds(state, nowMs);

  if (state.mode === 'free') {
    const elapsedSeconds = state.anchorElapsed + whole;
    if (elapsedSeconds === state.elapsedSeconds) {
      return { state, completedPhase: null, completedFocusSeconds: 0 };
    }
    return {
      state: { ...state, elapsedSeconds, remainingSeconds: 0 },
      completedPhase: null,
      completedFocusSeconds: 0,
    };
  }

  const remaining = state.anchorRemaining - whole;
  if (remaining > 0) {
    const elapsedSeconds = state.anchorElapsed + whole;
    if (remaining === state.remainingSeconds && elapsedSeconds === state.elapsedSeconds) {
      return { state, completedPhase: null, completedFocusSeconds: 0 };
    }
    return {
      state: { ...state, remainingSeconds: remaining, elapsedSeconds },
      completedPhase: null,
      completedFocusSeconds: 0,
    };
  }

  // Phase finished. `-remaining` is how far past the boundary we are, which is
  // exactly how many seconds were actually focused (catch-up ticks included).
  const focusSeconds = state.anchorElapsed + whole + Math.min(0, remaining);

  if (state.phase === 'focus') {
    const next = nextPhaseAfterFocus(state, cyclesBeforeLong);
    return {
      state: {
        ...state,
        isRunning: false,
        anchorMs: null,
        phase: next.phase,
        completedCycles: next.completedCycles,
        remainingSeconds: next.durationSeconds,
        anchorRemaining: next.durationSeconds,
        elapsedSeconds: 0,
        anchorElapsed: 0,
        sessionStartedAt: null,
      },
      completedPhase: 'focus',
      completedFocusSeconds: Math.max(0, focusSeconds),
    };
  }

  const focusSecondsNext = Math.max(1, state.focusMinutes) * 60;
  return {
    state: {
      ...state,
      isRunning: false,
      anchorMs: null,
      phase: 'focus',
      remainingSeconds: focusSecondsNext,
      anchorRemaining: focusSecondsNext,
      elapsedSeconds: 0,
      anchorElapsed: 0,
      sessionStartedAt: null,
    },
    completedPhase: 'break',
    completedFocusSeconds: 0,
  };
}

/** Focused seconds for a phase that is being ended manually (finish & save). */
export function focusSecondsNow(state: FocusClockState, nowMs: number): number {
  return readClock(state, nowMs).elapsedSeconds;
}

export function phaseDurationSeconds(state: FocusClockState): number {
  if (state.mode === 'free') return 0;
  if (state.phase === 'focus') return Math.max(1, state.focusMinutes) * 60;
  if (state.phase === 'short_break') return Math.max(1, state.shortBreakMinutes) * 60;
  return Math.max(1, state.longBreakMinutes) * 60;
}
