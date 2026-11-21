import { beforeEach, describe, expect, it } from 'vitest';
import { db } from '@/lib/db';
import {
  clearActiveRun,
  fromRecord,
  hasProgress,
  loadActiveRun,
  saveActiveRun,
  toRecord,
} from '@/lib/chrono/persistence';
import { startClock, tickClock, type FocusClockState } from '@/lib/focusTimer';
import { buildPlan, DEFAULT_CHRONO_CONFIG } from '@/lib/chrono/plan';

/**
 * Chrono Core survives the app being killed. These tests use the real Dexie
 * instance (fake-indexeddb), so they cover the schema, the write path and the
 * rebuild — not a mock of them.
 */

const T0 = 1_700_000_000_000;

function runningState(): FocusClockState {
  const plan = buildPlan('interval', { ...DEFAULT_CHRONO_CONFIG, blocks: 2, blockMinutes: 5, restMinutes: 1 });
  return startClock(
    {
      mode: 'interval',
      phase: 'focus',
      isRunning: false,
      focusMinutes: 25,
      shortBreakMinutes: 5,
      longBreakMinutes: 15,
      remainingSeconds: 300,
      elapsedSeconds: 0,
      completedCycles: 0,
      linkedTaskId: 'tsk_1',
      sessionStartedAt: null,
      anchorMs: null,
      anchorRemaining: 300,
      anchorElapsed: 0,
      plan,
      segmentIndex: 0,
      autoAdvance: true,
      accruedFocusSeconds: 0,
    },
    T0
  );
}

describe('Chrono Core — crash recovery', () => {
  beforeEach(async () => {
    await db.activeRun.clear();
  });

  it('writes a running session and rebuilds it with its anchors intact', async () => {
    const running = runningState();
    await saveActiveRun(running, T0 + 30_000);

    const restored = await loadActiveRun();
    expect(restored).not.toBeNull();
    expect(restored?.mode).toBe('interval');
    expect(restored?.plan).toHaveLength(3);
    expect(restored?.isRunning).toBe(true);
    expect(restored?.anchorMs).toBe(T0);
    expect(restored?.autoAdvance).toBe(true);
    expect(restored?.linkedTaskId).toBe('tsk_1');
  });

  it('catches up on everything that happened while the app was closed', async () => {
    const running = runningState();
    await saveActiveRun(running, T0);

    const restored = (await loadActiveRun())!;
    // Reopened 6 minutes later: block one (5 min) is over, the rest is over too,
    // and the second block is 0 s in… so the walk lands at its very start.
    const ticked = tickClock(restored, T0 + 6 * 60_000, 4);

    expect(ticked.state.segmentIndex).toBe(2);
    expect(ticked.completedFocusSeconds).toBe(300);
    expect(ticked.state.isRunning).toBe(true);
  });

  it('never stores a session that has not started', async () => {
    const idle = fromRecord({
      ...toRecord(runningState(), T0),
      isRunning: false,
      anchorMs: null,
      elapsedSeconds: 0,
      accruedFocusSeconds: 0,
      completedCycles: 0,
      remainingSeconds: 300,
    });
    expect(hasProgress(idle)).toBe(false);

    await saveActiveRun(idle, T0);
    expect(await db.activeRun.count()).toBe(0);
  });

  it('clears the row when a session is finished or abandoned', async () => {
    await saveActiveRun(runningState(), T0);
    expect(await db.activeRun.count()).toBe(1);

    await clearActiveRun();
    expect(await loadActiveRun()).toBeNull();
  });

  it('keeps a paused session so a pause is not a data loss', async () => {
    const paused = { ...runningState(), isRunning: false, anchorMs: null, elapsedSeconds: 90, remainingSeconds: 210 };
    await saveActiveRun(paused, T0);

    const restored = (await loadActiveRun())!;
    expect(restored.isRunning).toBe(false);
    expect(restored.remainingSeconds).toBe(210);
    expect(restored.elapsedSeconds).toBe(90);
  });
});
