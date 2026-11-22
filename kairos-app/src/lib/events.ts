import { db } from './db';

/**
 * Event history — the app's own ledger.
 *
 * Why a log and not "just more rows": every intelligent behaviour that comes
 * later (adaptive ranking, undo, automation, explanations) needs to answer three
 * questions that the current tables cannot: *what happened*, *when*, and *who
 * decided it* — the user, the system, or an automation rule. A task row only
 * holds the end state; it forgets that it was postponed twice and then completed.
 *
 * Rules this ledger keeps:
 *   · append-only — an event is never edited, only added (undo is its own event);
 *   · small — payloads hold identifiers and numbers, never journal text;
 *   · bounded — the oldest rows are pruned past `EVENT_RETENTION`;
 *   · silent on failure — a logging error must never break the write it describes.
 */

export type EventKind =
  | 'task.created'
  | 'task.updated'
  | 'task.status'
  | 'task.postponed'
  | 'task.archived'
  | 'task.deleted'
  | 'habit.created'
  | 'habit.updated'
  | 'habit.logged'
  | 'habit.unlogged'
  | 'habit.deleted'
  | 'focus.recorded'
  | 'focus.deleted'
  | 'goal.created'
  | 'goal.updated'
  | 'goal.deleted'
  | 'journal.saved'
  | 'journal.deleted'
  | 'project.created'
  | 'project.updated'
  | 'project.archived'
  | 'project.deleted'
  | 'stage.created'
  | 'stage.toggled'
  | 'stage.deleted'
  | 'settings.updated'
  | 'data.imported'
  | 'data.cleared'
  | 'undo.applied'
  | 'rule.executed'
  | 'migration.completed';

export type EventEntity =
  | 'task'
  | 'habit'
  | 'habitLog'
  | 'focusSession'
  | 'goal'
  | 'journal'
  | 'project'
  | 'settings'
  | 'system';

/** Who caused it. Automation is separated on purpose: it is what the user audits. */
export type EventSource = 'user' | 'system' | 'automation' | 'undo';

/** Mirrors the three authority levels from the product spec. */
export type Authority = 1 | 2 | 3;

export interface KairosEvent {
  id: string;
  /** ISO timestamp, local wall clock of the device. */
  at: string;
  kind: EventKind;
  entity: EventEntity;
  entityId: string;
  source: EventSource;
  level: Authority;
  /** Small structured extras (numbers and flags only). */
  data?: Record<string, string | number | boolean | null>;
}

/** Roughly a year of heavy use; older rows are pruned in batches. */
export const EVENT_RETENTION = 4000;
const PRUNE_BATCH = 400;

export interface EventQuery {
  entity?: EventEntity;
  entityId?: string;
  kind?: EventKind;
  since?: string;
  limit?: number;
}

/** Newest first. Pure read; no pruning, so a caller never loses history mid-read. */
export async function listEvents(query: EventQuery = {}): Promise<KairosEvent[]> {
  const limit = Math.max(1, query.limit ?? 50);
  let collection = db.eventHistory.toCollection();

  if (query.entityId) {
    collection = db.eventHistory.where('entityId').equals(query.entityId);
  } else if (query.kind) {
    collection = db.eventHistory.where('kind').equals(query.kind);
  } else if (query.entity) {
    collection = db.eventHistory.where('entity').equals(query.entity);
  }

  let rows = await collection.sortBy('at');

  if (query.entity && query.entityId) {
    rows = rows.filter((row) => row.entity === query.entity);
  }
  if (query.kind && query.entityId) {
    rows = rows.filter((row) => row.kind === query.kind);
  }
  if (query.since) {
    rows = rows.filter((row) => row.at >= query.since!);
  }

  return rows.reverse().slice(0, limit);
}

export async function countEvents(): Promise<number> {
  return db.eventHistory.count();
}

/**
 * Trims the ledger to its retention window. Called opportunistically from the
 * writer, not on every read, so it never sits in a user's interaction path.
 */
export async function pruneEvents(retain = EVENT_RETENTION): Promise<number> {
  const total = await db.eventHistory.count();
  if (total <= retain) return 0;

  const doomed = await db.eventHistory
    .orderBy('at')
    .limit(Math.min(PRUNE_BATCH, total - retain))
    .primaryKeys();

  if (doomed.length === 0) return 0;
  await db.eventHistory.bulkDelete(doomed);
  return doomed.length;
}

/** A compact, human-auditable history for one row (used by the assistant later). */
export async function historyOf(entityId: string, limit = 20): Promise<KairosEvent[]> {
  return listEvents({ entityId, limit });
}

/** Counts by kind inside a window — the shape the analytics engine will read. */
export async function eventCountsByKind(since: string): Promise<Record<string, number>> {
  const rows = await db.eventHistory.where('at').aboveOrEqual(since).toArray();
  const counts: Record<string, number> = {};
  for (const row of rows) counts[row.kind] = (counts[row.kind] ?? 0) + 1;
  return counts;
}
