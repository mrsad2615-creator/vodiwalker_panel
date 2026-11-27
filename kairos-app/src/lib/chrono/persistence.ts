import { db, logEvent, type ActiveRunRecord } from '@/lib/db';
import { createId } from '@/lib/format';
import type { FocusSession } from '@/types/domain';
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

// Keep snapshot writes, completion commits and resets ordered even when callers
// do not await them. A failed write must not poison the queue for later retry.
let writeQueue: Promise<unknown> = Promise.resolve();
function enqueue<T>(work: () => Promise<T>): Promise<T> {
  const pending = writeQueue.then(work, work);
  writeQueue = pending.catch(() => undefined);
  return pending;
}

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
    recordedFocusSeconds: state.recordedFocusSeconds ?? 0,
    finished: state.finished === true,
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
    // Old builds recorded completed segments separately but had no counter.
    // Assume they were recorded rather than double-counting them on upgrade.
    recordedFocusSeconds: record.recordedFocusSeconds ?? record.accruedFocusSeconds ?? 0,
    finished: record.finished === true,
  };
}

/** Record focus and advance/clear its crash-recovery row in the same transaction. */
export async function recordFocusProgress(input: Omit<FocusSession, 'id'>, next: FocusClockState | null, nowMs = Date.now()): Promise<FocusSession> {
  const session = { ...input, id: createId('foc') };
  await enqueue(() => db.transaction('rw', [db.focusSessions, db.activeRun], async () => {
    await db.focusSessions.add(session);
    if (next && hasProgress(next)) await db.activeRun.put(toRecord(next, nowMs));
    else await db.activeRun.delete(ACTIVE_ID);
  }));
  void logEvent('focus.recorded', 'focusSession', session.id, {
    data: { minutes: Math.round(session.durationMinutes), mode: session.mode, natural: session.completedNaturally },
  });
  return session;
}

export async function saveActiveRun(state: FocusClockState, nowMs = Date.now()): Promise<void> {
  try {
    await enqueue(async () => {
      if (!hasProgress(state)) {
        await db.activeRun.delete(ACTIVE_ID);
        return;
      }
      await db.activeRun.put(toRecord(state, nowMs));
    });
  } catch {
    // Storage is a nice-to-have: a locked or full database must never break the
    // timer that is already on screen.
  }
}

export async function loadActiveRun(): Promise<FocusClockState | null> {
  try {
    await writeQueue;
    const record = await db.activeRun.get(ACTIVE_ID);
    return record ? fromRecord(record) : null;
  } catch {
    return null;
  }
}

export async function clearActiveRun(): Promise<void> {
  try {
    await enqueue(() => db.activeRun.delete(ACTIVE_ID));
  } catch {
    // ignore
  }
}
