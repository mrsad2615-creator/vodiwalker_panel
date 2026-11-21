import { db, type ActiveRunRecord } from '@/lib/db';
import type { FocusClockState } from '@/lib/focusTimer';

/**
 * Chrono Core — crash recovery.
 *
 * The clock was already immune to a frozen WebView, but not to the WebView being
 * *destroyed*: Android reclaims the background app, the page reloads, and a
 * 40-minute deep-work block vanished. The running session is therefore written
 * to one row in IndexedDB and rebuilt on boot with its anchors intact, so the
 * timer continues as if nothing happened — including any segments that elapsed
 * while the app was gone.
 *
 * A session that has not started yet is never written: the table holds at most
 * one meaningful row, and a "fresh 25:00, not started" state is not state.
 */

const ACTIVE_ID = 'active';

export function hasProgress(state: FocusClockState): boolean {
  return (
    state.isRunning ||
    state.elapsedSeconds > 0 ||
    (state.accruedFocusSeconds ?? 0) > 0 ||
    (state.completedCycles ?? 0) > 0
  );
}

export function toRecord(state: FocusClockState, nowMs: number): ActiveRunRecord {
  return {
    id: ACTIVE_ID,
    mode: state.mode,
    plan: state.plan ?? [],
    segmentIndex: state.segmentIndex ?? 0,
    phase: state.phase,
    isRunning: state.isRunning,
    remainingSeconds: state.remainingSeconds,
    elapsedSeconds: state.elapsedSeconds,
    anchorMs: state.anchorMs,
    anchorRemaining: state.anchorRemaining,
    anchorElapsed: state.anchorElapsed,
    completedCycles: state.completedCycles,
    accruedFocusSeconds: state.accruedFocusSeconds ?? 0,
    autoAdvance: state.autoAdvance === true,
    linkedTaskId: state.linkedTaskId,
    sessionStartedAt: state.sessionStartedAt,
    updatedAt: new Date(nowMs).toISOString(),
  };
}

export function fromRecord(record: ActiveRunRecord): FocusClockState {
  return {
    mode: record.mode,
    phase: record.phase,
    isRunning: record.isRunning,
    // Duration fields are settings-owned; the plan carries the real lengths and
    // `applyPlan` fills these in again on restore when they are needed.
    focusMinutes: 25,
    shortBreakMinutes: 5,
    longBreakMinutes: 15,
    remainingSeconds: record.remainingSeconds,
    elapsedSeconds: record.elapsedSeconds,
    completedCycles: record.completedCycles,
    linkedTaskId: record.linkedTaskId,
    sessionStartedAt: record.sessionStartedAt,
    anchorMs: record.anchorMs,
    anchorRemaining: record.anchorRemaining,
    anchorElapsed: record.anchorElapsed,
    plan: record.plan,
    segmentIndex: record.segmentIndex,
    autoAdvance: record.autoAdvance,
    accruedFocusSeconds: record.accruedFocusSeconds,
    finished: false,
  };
}

export async function saveActiveRun(state: FocusClockState, nowMs = Date.now()): Promise<void> {
  try {
    if (!hasProgress(state)) {
      await db.activeRun.delete(ACTIVE_ID);
      return;
    }
    await db.activeRun.put(toRecord(state, nowMs));
  } catch {
    // Storage is a nice-to-have: a locked or full database must never break the
    // timer that is already on screen.
  }
}

export async function loadActiveRun(): Promise<FocusClockState | null> {
  try {
    const record = await db.activeRun.get(ACTIVE_ID);
    return record ? fromRecord(record) : null;
  } catch {
    return null;
  }
}

export async function clearActiveRun(): Promise<void> {
  try {
    await db.activeRun.delete(ACTIVE_ID);
  } catch {
    // ignore
  }
}
