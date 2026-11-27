import { db } from './db';
import { can, consumeGrant } from './permissions';
import type { EventEntity } from './events';
import type { Task } from '@/types/domain';

/**
 * Undo manager.
 *
 * The rule from the product spec: an automated or semi-automated change is only
 * allowed if it can be taken back. So the write helpers in `db.ts` leave a
 * "before" snapshot behind for every restorable operation, and this module is the
 * only thing that replays one.
 *
 * What is restorable: edits to an existing row (task, habit, habit-log, goal,
 * journal, project, project stage). What is *not*: deletions. Bringing a row back from nothing is a
 * different promise than undoing an edit, and quietly pretending otherwise is
 * how data-loss bugs are born. Deletions stay level 3 — they need confirmation
 * and a backup, never a hopeful undo button.
 */

export type UndoKind =
  | 'task.update'
  | 'task.status'
  | 'task.archive'
  | 'habit.update'
  | 'habit.log'
  | 'goal.update'
  | 'journal.save'
  | 'project.update'
  | 'project.status'
  | 'stage.update'
  | 'stage.toggle';

export interface UndoEntry {
  id: string;
  at: string;
  kind: UndoKind;
  entity: EventEntity;
  entityId: string;
  /** Short Persian label for a future "یک قدم برگرد" surface. */
  label: string;
  /** Row as it was (null when the row did not exist yet, e.g. the first log). */
  before: unknown | null;
  /** Row as it became, so a future redo is possible without guessing. */
  after: unknown | null;
  /** Present only for a transactionally recorded recurring completion.
   * A null spawned row means an existing occurrence was reused, never deleted. */
  recurrenceEffect?: { version: 1; spawned: Task | null };
  /** Set when the entry has already been replayed. */
  undoneAt?: string | null;
}

export const UNDO_RETENTION = 200;
const PRUNE_BATCH = 50;

export const RESTORABLE_KINDS: readonly UndoKind[] = [
  'task.update',
  'task.status',
  'task.archive',
  'habit.update',
  'habit.log',
  'goal.update',
  'journal.save',
  'project.update',
  'project.status',
  'stage.update',
  'stage.toggle',
];

export function isRestorable(kind: string): kind is UndoKind {
  return (RESTORABLE_KINDS as readonly string[]).includes(kind);
}

function tableFor(kind: UndoKind) {
  switch (kind) {
    case 'task.update':
    case 'task.status':
    case 'task.archive':
      return db.tasks;
    case 'habit.update':
      return db.habits;
    case 'habit.log':
      return db.habitLogs;
    case 'goal.update':
      return db.goals;
    case 'journal.save':
      return db.journal;
    case 'project.update':
    case 'project.status':
      return db.projects;
    case 'stage.update':
    case 'stage.toggle':
      return db.projectStages;
  }
}

export interface UndoResult {
  ok: boolean;
  entry?: UndoEntry;
  reason?: 'empty' | 'not-restorable' | 'not-allowed' | 'conflict' | 'recurring-unsafe' | 'failed';
}

/** The most recent restorable entry that has not been replayed yet. */
export async function lastUndoable(): Promise<UndoEntry | null> {
  const entries = await db.undoHistory.orderBy('at').reverse().toArray();
  const entry = entries.find((row) => !row.undoneAt && isRestorable(row.kind));
  return entry ?? null;
}

export async function listUndoable(limit = 10): Promise<UndoEntry[]> {
  const entries = await db.undoHistory.orderBy('at').reverse().toArray();
  return entries.filter((row) => !row.undoneAt && isRestorable(row.kind)).slice(0, limit);
}

/**
 * Replays one entry: the row is put back exactly as it was, or removed again if
 * it did not exist before. Runs in a transaction with the bookkeeping, so a
 * half-undone state cannot survive a crash.
 */
export async function undoEntry(entryId: string): Promise<UndoResult> {
  const entry = await db.undoHistory.get(entryId);
  if (!entry) return { ok: false, reason: 'empty' };
  if (entry.undoneAt) return { ok: false, reason: 'empty', entry };
  if (!isRestorable(entry.kind)) return { ok: false, reason: 'not-restorable', entry };

  const decision = await can({ action: 'undo.apply', source: 'user', confirmed: true });
  if (!decision.allowed) return { ok: false, reason: 'not-allowed', entry };

  const table = tableFor(entry.kind);

  try {
    const result = await db.transaction('rw', [table, db.undoHistory, db.eventHistory, db.tasks, db.activeRun, db.focusSessions], async (): Promise<UndoResult> => {
      // Re-read both rows while holding the write transaction: a stale undo may
      // otherwise overwrite a later edit or replay an entry twice.
      const latest = await db.undoHistory.get(entryId);
      if (!latest || latest.undoneAt) return { ok: false, reason: 'empty', entry };
      if (JSON.stringify(latest) !== JSON.stringify(entry)) return { ok: false, reason: 'conflict', entry: latest };
      const current = await table.get(latest.entityId);
      if (JSON.stringify(current ?? null) !== JSON.stringify(latest.after ?? null)) {
        return { ok: false, reason: 'conflict', entry: latest };
      }
      // Legacy entries did not record the generated occurrence. New entries do;
      // remove only an unchanged, unreferenced occurrence created by this exact
      // operation. A pre-existing duplicate is never removed.
      if (latest.kind === 'task.status' && latest.after && latest.before) {
        const before = latest.before as { status?: string };
        const after = latest.after as { status?: string; recurrence?: string };
        if (after.recurrence && after.recurrence !== 'none' && before.status !== 'completed' && after.status === 'completed') {
          const effect = latest.recurrenceEffect;
          if (!effect || effect.version !== 1 || effect.spawned === undefined) return { ok: false, reason: 'recurring-unsafe', entry: latest };
          if (effect.spawned) {
            const spawned = await db.tasks.get(effect.spawned.id);
            const [tasks, active, sessions] = await Promise.all([
              db.tasks.toArray(), db.activeRun.get('active'), db.focusSessions.where('taskId').equals(effect.spawned.id).count(),
            ]);
            const referenced = active?.linkedTaskId === effect.spawned.id || sessions > 0 ||
              tasks.some((task) => task.id !== effect.spawned!.id && task.dependsOn?.includes(effect.spawned!.id));
            if (!spawned || JSON.stringify(spawned) !== JSON.stringify(effect.spawned) || referenced) {
              return { ok: false, reason: 'recurring-unsafe', entry: latest };
            }
            await db.tasks.delete(spawned.id);
          }
        }
      }
      if (latest.before === null || latest.before === undefined) {
        await table.delete(latest.entityId);
      } else {
        await table.put(latest.before as never);
      }

      const undone = { ...latest, undoneAt: new Date().toISOString() };
      await db.undoHistory.put(undone);
      await db.eventHistory.put({
        id: `evt-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        at: new Date().toISOString(),
        kind: 'undo.applied',
        entity: latest.entity,
        entityId: latest.entityId,
        source: 'undo',
        level: 3,
        data: { undoKind: latest.kind },
      });
      return { ok: true, entry: undone };
    });
    if (result.ok && decision.grantId) await consumeGrant(decision.grantId);
    return result;
  } catch {
    return { ok: false, reason: 'failed', entry };
  }
}

/** Undo the newest thing, which is what a real "برگرد" button means. */
export async function undoLast(): Promise<UndoResult> {
  const entry = await lastUndoable();
  if (!entry) return { ok: false, reason: 'empty' };
  return undoEntry(entry.id);
}

/**
 * Keeps the stack bounded. Called from the writer after each push, and cheap:
 * it counts first and only deletes when the cap is exceeded.
 */
export async function pruneUndo(retain = UNDO_RETENTION): Promise<number> {
  const total = await db.undoHistory.count();
  if (total <= retain) return 0;

  // Oldest first, and already-replayed entries go first inside the same window.
  const oldest = await db.undoHistory
    .orderBy('at')
    .limit(Math.min(PRUNE_BATCH, total - retain))
    .primaryKeys();

  if (oldest.length === 0) return 0;
  await db.undoHistory.bulkDelete(oldest);
  return oldest.length;
}
