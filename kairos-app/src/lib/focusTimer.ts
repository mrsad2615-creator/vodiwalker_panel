import type { FocusModeType } from '@/types/domain';

/** Mirrors `PomodoroPhase` in AppContext (kept local so this module stays pure). */
export type PomodoroPhase = 'focus' | 'short_break' | 'long_break';

/**
 * Pure focus-clock arithmetic.
 *
 * The timer is wall-clock driven: `deltaSeconds` is the real time that passed
 * since the previous tick (measured with `Date.now()`), so a locked screen or a
 * throttled WebView can never stretch or shrink a session. Keeping the maths pure
 * makes it testable and keeps side effects (chime, haptics, toasts, DB writes)
 * out of a React state updater.
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
}

export interface FocusClockResult {
  state: FocusClockState;
  /** Set when this tick crossed the end of a phase. */
  completedPhase: 'focus' | 'break' | null;
}

export function advanceFocusClock(
  state: FocusClockState,
  deltaSeconds: number,
  cyclesBeforeLong: number
): FocusClockResult {
  const delta = Math.max(0, Math.floor(deltaSeconds));
  if (!state.isRunning || delta === 0) return { state, completedPhase: null };

  if (state.mode === 'free') {
    return {
      state: { ...state, elapsedSeconds: state.elapsedSeconds + delta },
      completedPhase: null,
    };
  }

  const remaining = state.remainingSeconds - delta;

  if (remaining > 0) {
    return {
      state: {
        ...state,
        remainingSeconds: remaining,
        elapsedSeconds:
          state.phase === 'focus' ? state.elapsedSeconds + delta : state.elapsedSeconds,
      },
      completedPhase: null,
    };
  }

  if (state.phase === 'focus') {
    const nextCycles = state.completedCycles + 1;
    const cycleLength = Math.max(1, Math.floor(cyclesBeforeLong) || 1);
    const isLong = nextCycles % cycleLength === 0;
    const nextPhase: PomodoroPhase = isLong ? 'long_break' : 'short_break';
    const nextDuration = Math.max(1, isLong ? state.longBreakMinutes : state.shortBreakMinutes) * 60;

    return {
      state: {
        ...state,
        isRunning: false,
        phase: nextPhase,
        completedCycles: nextCycles,
        remainingSeconds: nextDuration,
        elapsedSeconds: 0,
        sessionStartedAt: null,
      },
      completedPhase: 'focus',
    };
  }

  return {
    state: {
      ...state,
      isRunning: false,
      phase: 'focus',
      remainingSeconds: Math.max(1, state.focusMinutes) * 60,
      elapsedSeconds: 0,
      sessionStartedAt: null,
    },
    completedPhase: 'break',
  };
}

/** Total focused seconds for a completing focus phase (handles catch-up ticks). */
export function focusSecondsAtCompletion(state: FocusClockState, deltaSeconds: number): number {
  return state.elapsedSeconds + Math.max(0, Math.floor(deltaSeconds));
}
