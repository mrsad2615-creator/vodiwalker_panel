import { describe, expect, it } from 'vitest';
import { advanceFocusClock, focusSecondsAtCompletion, type FocusClockState } from '@/lib/focusTimer';

const base: FocusClockState = {
  mode: 'pomodoro',
  phase: 'focus',
  isRunning: true,
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  remainingSeconds: 25 * 60,
  elapsedSeconds: 0,
  completedCycles: 0,
  linkedTaskId: null,
  sessionStartedAt: new Date('2026-01-01T10:00:00Z').toISOString(),
};

describe('focus clock', () => {
  it('advances a free session by the real elapsed delta', () => {
    const result = advanceFocusClock({ ...base, mode: 'free' }, 37, 4);
    expect(result.state.elapsedSeconds).toBe(37);
    expect(result.completedPhase).toBeNull();
  });

  it('counts a running pomodoro down without completing it', () => {
    const result = advanceFocusClock(base, 60, 4);
    expect(result.state.remainingSeconds).toBe(25 * 60 - 60);
    expect(result.state.elapsedSeconds).toBe(60);
    expect(result.completedPhase).toBeNull();
  });

  it('completes the focus phase and hands over to a short break', () => {
    const almost = { ...base, remainingSeconds: 3, elapsedSeconds: 25 * 60 - 3 };
    const result = advanceFocusClock(almost, 4, 4);
    expect(result.completedPhase).toBe('focus');
    expect(result.state.phase).toBe('short_break');
    expect(result.state.remainingSeconds).toBe(5 * 60);
    expect(result.state.completedCycles).toBe(1);
    expect(result.state.isRunning).toBe(false);
    expect(result.state.elapsedSeconds).toBe(0);
  });

  it('uses a long break on the configured cycle boundary', () => {
    const result = advanceFocusClock({ ...base, remainingSeconds: 1, completedCycles: 3 }, 2, 4);
    expect(result.state.phase).toBe('long_break');
    expect(result.state.remainingSeconds).toBe(15 * 60);
  });

  it('returns to focus after a break', () => {
    const onBreak = { ...base, phase: 'short_break' as const, remainingSeconds: 2, elapsedSeconds: 0 };
    const result = advanceFocusClock(onBreak, 5, 4);
    expect(result.completedPhase).toBe('break');
    expect(result.state.phase).toBe('focus');
    expect(result.state.remainingSeconds).toBe(25 * 60);
  });

  it('catches up a whole phase after the app was suspended', () => {
    const result = advanceFocusClock(base, 26 * 60, 4);
    expect(result.completedPhase).toBe('focus');
    expect(focusSecondsAtCompletion(base, 26 * 60)).toBe(26 * 60);
  });

  it('ignores ticks while paused', () => {
    const paused = { ...base, isRunning: false };
    expect(advanceFocusClock(paused, 30, 4).state).toBe(paused);
  });
});
