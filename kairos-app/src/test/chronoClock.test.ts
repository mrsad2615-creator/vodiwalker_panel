import { describe, expect, it } from 'vitest';
import {
  pauseClock,
  readClock,
  seekSegment,
  startClock,
  tickClock,
  totalFocusSeconds,
  type FocusClockState,
} from '@/lib/focusTimer';
import { buildPlan, DEFAULT_CHRONO_CONFIG, type ClockSegment } from '@/lib/chrono/plan';

/**
 * Chrono Core clock behaviour. The pomodoro/free semantics are locked by
 * focusTimer.test.ts; these tests cover what a plan adds: walking boundaries,
 * catching up across several segments, and ending a finite plan.
 */

const T0 = 1_700_000_000_000;

function stateFor(plan: ClockSegment[], mode: FocusClockState['mode'], overrides: Partial<FocusClockState> = {}): FocusClockState {
  const first = plan[0];
  return {
    mode,
    phase: 'focus',
    isRunning: false,
    focusMinutes: 25,
    shortBreakMinutes: 5,
    longBreakMinutes: 15,
    remainingSeconds: first.seconds,
    elapsedSeconds: 0,
    completedCycles: 0,
    linkedTaskId: null,
    sessionStartedAt: null,
    anchorMs: null,
    anchorRemaining: first.seconds,
    anchorElapsed: 0,
    plan,
    segmentIndex: 0,
    autoAdvance: false,
    accruedFocusSeconds: 0,
    ...overrides,
  };
}

const interval = buildPlan('interval', {
  ...DEFAULT_CHRONO_CONFIG,
  blocks: 3,
  blockMinutes: 10,
  restMinutes: 2,
});

describe('Chrono Core — plan-driven clock', () => {
  it('stops on the boundary when auto-advance is off', () => {
    const running = startClock(stateFor(interval, 'interval'), T0);
    const done = tickClock(running, T0 + 10 * 60_000, 4);

    expect(done.completedPhase).toBe('focus');
    expect(done.completedFocusSeconds).toBe(600);
    expect(done.state.isRunning).toBe(false);
    expect(done.state.segmentIndex).toBe(1);
    expect(done.state.phase).toBe('short_break');
    // The next segment waits at its full length — no half-eaten rest.
    expect(done.state.remainingSeconds).toBe(120);
  });

  it('walks straight into the next segment when auto-advance is on', () => {
    const running = startClock(stateFor(interval, 'interval', { autoAdvance: true }), T0);
    const result = tickClock(running, T0 + 10 * 60_000 + 30_000, 4);

    expect(result.state.isRunning).toBe(true);
    expect(result.state.segmentIndex).toBe(1);
    expect(result.state.phase).toBe('short_break');
    // 30 s into a 2-minute rest.
    expect(result.state.remainingSeconds).toBe(90);
    expect(result.completedFocusSeconds).toBe(600);
  });

  it('lands in the right block after the app was frozen across several segments', () => {
    const running = startClock(stateFor(interval, 'interval', { autoAdvance: true }), T0);
    // 10 focus + 2 rest + 10 focus + 2 rest + 3 minutes into the third block.
    const elapsed = (10 + 2 + 10 + 2) * 60 + 3 * 60;
    const result = tickClock(running, T0 + elapsed * 1000, 4);

    expect(result.state.segmentIndex).toBe(4);
    expect(result.state.phase).toBe('focus');
    expect(result.state.remainingSeconds).toBe(7 * 60);
    // Two full blocks were banked, the third is in flight.
    expect(totalFocusSeconds(result.state, T0 + elapsed * 1000)).toBe(1200 + 180);
  });

  it('finishes a finite plan instead of wrapping round', () => {
    const running = startClock(stateFor(interval, 'interval', { autoAdvance: true }), T0);
    const total = (10 + 2 + 10 + 2 + 10) * 60;
    const done = tickClock(running, T0 + total * 1000, 4);

    expect(done.planFinished).toBe(true);
    expect(done.state.finished).toBe(true);
    expect(done.state.isRunning).toBe(false);
    // One catch-up tick banks every block it passed, which is exactly what the
    // statistics row must contain when the app was closed for half an hour.
    expect(done.completedFocusSeconds).toBe(1800);
    expect(totalFocusSeconds(done.state, T0 + total * 1000)).toBe(1800);
  });

  it('counts a single-segment countdown up to its end and stops there', () => {
    const plan = buildPlan('countdown', { ...DEFAULT_CHRONO_CONFIG, countdownMinutes: 5 });
    const running = startClock(stateFor(plan, 'countdown', { autoAdvance: true }), T0);

    expect(tickClock(running, T0 + 4 * 60_000, 4).state.finished).toBeFalsy();

    const done = tickClock(running, T0 + 5 * 60_000, 4);
    expect(done.planFinished).toBe(true);
    expect(done.state.remainingSeconds).toBe(0);
    expect(done.completedFocusSeconds).toBe(300);
  });

  it('keeps an open stopwatch running forever', () => {
    const plan = buildPlan('free', DEFAULT_CHRONO_CONFIG);
    const running = startClock(stateFor(plan, 'free'), T0);
    const later = tickClock(running, T0 + 3 * 3600_000, 4);

    expect(later.state.isRunning).toBe(true);
    expect(later.state.elapsedSeconds).toBe(10_800);
    expect(later.completedPhase).toBeNull();
    expect(readClock(later.state, T0 + 3 * 3600_000 + 500).elapsedSeconds).toBe(10_800);
  });

  it('seeks to any segment, paused at its start', () => {
    const running = startClock(stateFor(interval, 'interval'), T0);
    const jumped = seekSegment(running, 4, 4);

    expect(jumped.isRunning).toBe(false);
    expect(jumped.anchorMs).toBeNull();
    expect(jumped.segmentIndex).toBe(4);
    expect(jumped.remainingSeconds).toBe(600);
    expect(jumped.phase).toBe('focus');
  });

  it('does not re-render while a paused clock is ticked', () => {
    const paused = pauseClock(startClock(stateFor(interval, 'interval'), T0), T0);
    expect(tickClock(paused, T0 + 5_000, 4).state).toBe(paused);
  });

  it('remembers the plan cursor across a pause and resume', () => {
    const running = startClock(stateFor(interval, 'interval', { autoAdvance: true }), T0);
    const inRest = tickClock(running, T0 + 11 * 60_000, 4).state;
    const paused = pauseClock(inRest, T0 + 11 * 60_000);
    const resumed = startClock(paused, T0 + 60 * 60_000);
    const ticked = tickClock(resumed, T0 + 60 * 60_000 + 30_000, 4);

    expect(ticked.state.segmentIndex).toBe(1);
    // 60 s of the 2-minute rest had already run before the pause.
    expect(ticked.state.remainingSeconds).toBe(30);
  });
});
