import { describe, expect, it } from 'vitest';
import {
  pauseClock,
  phaseDurationSeconds,
  readClock,
  startClock,
  tickClock,
  type FocusClockState,
} from '@/lib/focusTimer';

/**
 * These tests lock the regression that made the clock run ~4× fast: the old
 * tick advanced by `Math.max(1, Math.round(deltaMs / 1000))`, which turned
 * every 250 ms interval into a full second.
 */

const baseState = (overrides: Partial<FocusClockState> = {}): FocusClockState => ({
  mode: 'pomodoro',
  phase: 'focus',
  isRunning: false,
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  remainingSeconds: 25 * 60,
  elapsedSeconds: 0,
  completedCycles: 0,
  linkedTaskId: null,
  sessionStartedAt: null,
  anchorMs: null,
  anchorRemaining: 25 * 60,
  anchorElapsed: 0,
  ...overrides,
});

const T0 = 1_700_000_000_000;

describe('KAIROS focus clock (anchored)', () => {
  it('counts down at exactly 1× wall-clock time', () => {
    let state = startClock(baseState(), T0);

    // Simulate the real UI: a tick every 250 ms for ten seconds.
    for (let ms = 250; ms <= 10_000; ms += 250) {
      state = tickClock(state, T0 + ms, 4).state;
    }

    // The old engine would read 1470 here (one second per 250 ms tick).
    expect(state.remainingSeconds).toBe(1500 - 10);
    expect(state.elapsedSeconds).toBe(10);
  });

  it('does not re-render four times a second', () => {
    const running = startClock(baseState(), T0);
    const a = tickClock(running, T0 + 100, 4);
    const b = tickClock(running, T0 + 900, 4);
    expect(a.state).toBe(running); // untouched → React bails out
    expect(b.state).toBe(running);

    const c = tickClock(running, T0 + 1000, 4);
    expect(c.state).not.toBe(running);
    expect(c.state.remainingSeconds).toBe(1499);
  });

  it('catches up exactly after the WebView was frozen for five minutes', () => {
    const running = startClock(baseState(), T0);
    const result = tickClock(running, T0 + 5 * 60 * 1000, 4);
    expect(result.state.remainingSeconds).toBe(1500 - 300);
    expect(result.completedPhase).toBeNull();
  });

  it('pause freezes the value and resume continues from it', () => {
    const running = startClock(baseState(), T0);
    const after30 = tickClock(running, T0 + 30_000, 4).state;
    expect(after30.remainingSeconds).toBe(1470);

    const paused = pauseClock(after30, T0 + 30_000);
    expect(paused.isRunning).toBe(false);
    expect(paused.anchorMs).toBeNull();
    expect(paused.remainingSeconds).toBe(1470);

    // Ten minutes later, still paused: nothing moved.
    expect(readClock(paused, T0 + 10 * 60_000).remainingSeconds).toBe(1470);

    const resumed = startClock(paused, T0 + 10 * 60_000);
    const after = tickClock(resumed, T0 + 10 * 60_000 + 30_000, 4).state;
    expect(after.remainingSeconds).toBe(1440);
  });

  it('free mode counts up, and never completes by itself', () => {
    const free = startClock(
      baseState({ mode: 'free', remainingSeconds: 0, anchorRemaining: 0 }),
      T0
    );
    const result = tickClock(free, T0 + 90_000, 4);
    expect(result.state.elapsedSeconds).toBe(90);
    expect(result.completedPhase).toBeNull();
  });

  it('completes the focus phase on the exact second and hands over to a break', () => {
    const running = startClock(baseState({ focusMinutes: 1, remainingSeconds: 60, anchorRemaining: 60 }), T0);
    expect(tickClock(running, T0 + 59_000, 4).completedPhase).toBeNull();

    const done = tickClock(running, T0 + 60_000, 4);
    expect(done.completedPhase).toBe('focus');
    expect(done.completedFocusSeconds).toBe(60);
    expect(done.state.phase).toBe('short_break');
    expect(done.state.remainingSeconds).toBe(5 * 60);
    expect(done.state.isRunning).toBe(false);
    expect(done.state.completedCycles).toBe(1);
  });

  it('counts the overflow when the app was closed past the boundary', () => {
    const running = startClock(baseState({ focusMinutes: 1, remainingSeconds: 60, anchorRemaining: 60 }), T0);
    // 90 s later: the phase ended 30 s ago, but only 60 s were "focus".
    const done = tickClock(running, T0 + 90_000, 4);
    expect(done.completedPhase).toBe('focus');
    expect(done.completedFocusSeconds).toBe(60);
  });

  it('uses the long break every Nth focus session, then returns to focus', () => {
    const fourth = startClock(
      baseState({
        focusMinutes: 1,
        remainingSeconds: 60,
        anchorRemaining: 60,
        completedCycles: 3,
      }),
      T0
    );
    const done = tickClock(fourth, T0 + 60_000, 4);
    expect(done.state.phase).toBe('long_break');
    expect(done.state.remainingSeconds).toBe(15 * 60);

    const breakDone = tickClock(done.state, T0 + 60_000, 4);
    expect(breakDone.completedPhase).toBeNull(); // break is not running yet

    const runningBreak = startClock(done.state, T0 + 60_000);
    const finishedBreak = tickClock(runningBreak, T0 + 60_000 + 15 * 60_000, 4);
    expect(finishedBreak.completedPhase).toBe('break');
    expect(finishedBreak.state.phase).toBe('focus');
    // The state in this test runs with a 1-minute focus phase.
    expect(finishedBreak.state.remainingSeconds).toBe(60);
  });

  it('reports the live value even when the render is a second behind', () => {
    const running = startClock(baseState(), T0);
    const stale = tickClock(running, T0 + 4_000, 4).state; // UI shows 1496
    const live = readClock(stale, T0 + 7_400); // 3.4 s later, no render yet
    expect(live.remainingSeconds).toBe(1493);
    expect(live.elapsedSeconds).toBe(7);
  });

  it('knows each phase duration', () => {
    expect(phaseDurationSeconds(baseState())).toBe(1500);
    expect(phaseDurationSeconds(baseState({ phase: 'short_break' }))).toBe(300);
    expect(phaseDurationSeconds(baseState({ phase: 'long_break' }))).toBe(900);
    expect(phaseDurationSeconds(baseState({ mode: 'free' }))).toBe(0);
  });
});
