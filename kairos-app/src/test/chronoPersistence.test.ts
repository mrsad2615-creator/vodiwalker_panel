import { beforeEach, describe, expect, it, vi } from 'vitest';
import { db } from '@/lib/db';
import {
  clearActiveRun,
  fromRecord,
  hasProgress,
  loadActiveRun,
  recordFocusProgress,
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
    await clearActiveRun();
    await db.focusSessions.clear();
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

  it('atomically records a completed segment and its advanced recovery state', async () => {
    const state = runningState();
    await saveActiveRun(state, T0);
    const advanced = { ...tickClock(state, T0 + 300_000, 4).state, recordedFocusSeconds: 300 };
    await recordFocusProgress({ mode: 'interval', durationMinutes: 5, durationSeconds: 300, dateKey: '2026-01-01', taskId: null, taskTitleSnapshot: null, completedNaturally: true, startedAt: '2026-01-01T00:00:00.000Z', endedAt: '2026-01-01T00:05:00.000Z' }, advanced, T0 + 300_000);
    expect(await db.focusSessions.count()).toBe(1);
    expect((await loadActiveRun())?.recordedFocusSeconds).toBe(300);
    expect((await loadActiveRun())?.segmentIndex).toBe(1);
  });

  it('rolls back the session if the recovery write fails and allows a retry', async () => {
    const state = runningState();
    await saveActiveRun(state, T0);
    const spy = vi.spyOn(db.activeRun, 'put').mockRejectedValueOnce(new Error('storage failure'));
    const input = { mode: 'interval' as const, durationMinutes: 5, durationSeconds: 300, dateKey: '2026-01-01', taskId: null, taskTitleSnapshot: null, completedNaturally: true, startedAt: '2026-01-01T00:00:00.000Z', endedAt: '2026-01-01T00:05:00.000Z' };
    const next = { ...tickClock(state, T0 + 300_000, 4).state, recordedFocusSeconds: 300 };
    try {
      await expect(recordFocusProgress(input, next)).rejects.toThrow('storage failure');
      expect(await db.focusSessions.count()).toBe(0);
      expect((await loadActiveRun())?.segmentIndex).toBe(0);
      await recordFocusProgress(input, next);
      expect(await db.focusSessions.count()).toBe(1);
    } finally { spy.mockRestore(); }
  });

  it('orders an outstanding save before a subsequent reset and preserves finished state', async () => {
    const state = { ...runningState(), recordedFocusSeconds: 300, finished: true };
    const save = saveActiveRun(state, T0);
    const clear = clearActiveRun();
    await Promise.all([save, clear]);
    expect(await loadActiveRun()).toBeNull();
    await saveActiveRun(state, T0);
    expect(await loadActiveRun()).toMatchObject({ recordedFocusSeconds: 300, finished: true });
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
