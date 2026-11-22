import { createId } from './format';
import { db } from './db';
import type { Authority, EventSource } from './events';

/**
 * Permission manager — three levels of authority, as data.
 *
 * The product rule is simple and has to stay enforceable in code:
 *
 *   1 · autonomous  — arithmetic and reads. Never asks.
 *   2 · conditional — changes to *plan*: moving a task, rescheduling, logging.
 *                     Allowed when the user did it; requires a grant when the
 *                     system or an automation did it.
 *   3 · protected   — anything destructive or historical: deleting, clearing
 *                     history, overwriting a journal entry, restoring a backup.
 *                     Always needs an explicit confirmation.
 *
 * A grant is never open-ended: it carries a scope (one entity or all), an
 * expiry, a use budget, and it can be revoked. An exhausted or expired grant is
 * treated as absent — a stale permission is not a permission.
 */

export type ActionKind =
  // level 1
  | 'focus.record'
  | 'insight.compute'
  | 'report.generate'
  // level 2
  | 'task.update'
  | 'task.reschedule'
  | 'task.priority'
  | 'task.status'
  | 'task.reminder'
  | 'task.archive'
  | 'habit.log'
  | 'habit.update'
  | 'goal.update'
  | 'journal.save'
  | 'dayplan.apply'
  | 'settings.update'
  // level 3
  | 'task.delete'
  | 'habit.delete'
  | 'goal.delete'
  | 'journal.overwrite'
  | 'data.clear'
  | 'data.restore'
  | 'rule.create'
  | 'rule.delete'
  | 'undo.apply';

export const ACTION_AUTHORITY: Record<ActionKind, Authority> = {
  'focus.record': 1,
  'insight.compute': 1,
  'report.generate': 1,

  'task.update': 2,
  'task.reschedule': 2,
  'task.priority': 2,
  'task.status': 2,
  'task.reminder': 2,
  'task.archive': 2,
  'habit.log': 2,
  'habit.update': 2,
  'goal.update': 2,
  'journal.save': 2,
  'dayplan.apply': 2,
  'settings.update': 2,

  'task.delete': 3,
  'habit.delete': 3,
  'goal.delete': 3,
  'journal.overwrite': 3,
  'data.clear': 3,
  'data.restore': 3,
  'rule.create': 3,
  'rule.delete': 3,
  'undo.apply': 3,
};

export function authorityOf(action: ActionKind): Authority {
  return ACTION_AUTHORITY[action] ?? 3;
}

export interface PermissionGrant {
  id: string;
  action: ActionKind;
  /** `null` = any entity of that action; an id limits the grant to one row. */
  entityId: string | null;
  grantedAt: string;
  /** ISO timestamp, or null for "until revoked". */
  expiresAt: string | null;
  /** How many times it may fire, or null for unbounded. */
  maxUses: number | null;
  uses: number;
  revokedAt: string | null;
  /** Why the user granted it — kept for the audit trail, shown in the UI later. */
  note: string;
}

export interface PermissionDecision {
  allowed: boolean;
  level: Authority;
  /** True when a human must confirm right now (level 3, or level 2 by a machine). */
  requiresConfirmation: boolean;
  reason: 'autonomous' | 'user-action' | 'granted' | 'needs-grant' | 'needs-confirmation' | 'revoked' | 'expired' | 'exhausted';
  grantId?: string;
}

export interface PermissionRequest {
  action: ActionKind;
  /** Who is asking: the person, or a rule. */
  source: EventSource;
  entityId?: string | null;
  /** Set when a human has already confirmed the operation in this turn. */
  confirmed?: boolean;
  /** Injectable clock for tests. */
  now?: Date;
}

function isLive(grant: PermissionGrant, now: Date): { live: boolean; reason: PermissionDecision['reason'] } {
  if (grant.revokedAt) return { live: false, reason: 'revoked' };
  if (grant.expiresAt && new Date(grant.expiresAt).getTime() <= now.getTime()) {
    return { live: false, reason: 'expired' };
  }
  if (grant.maxUses !== null && grant.uses >= grant.maxUses) {
    return { live: false, reason: 'exhausted' };
  }
  return { live: true, reason: 'granted' };
}

/**
 * The newest grant covering this action and entity — live or not. `can()` needs
 * the dead ones too: "your grant expired" and "your grant is used up" are very
 * different sentences to a user than "you have no permission".
 */
async function findCoveringGrant(
  action: ActionKind,
  entityId: string | null | undefined
): Promise<PermissionGrant | null> {
  const grants = await db.permissionGrants.where('action').equals(action).toArray();
  const candidates = grants
    .filter((grant) => grant.entityId === null || grant.entityId === (entityId ?? null))
    .sort((a, b) => b.grantedAt.localeCompare(a.grantedAt));
  return candidates[0] ?? null;
}

/** The newest *live* grant covering this action and entity, if any. */
export async function findGrant(
  action: ActionKind,
  entityId: string | null | undefined,
  now: Date = new Date()
): Promise<PermissionGrant | null> {
  const grant = await findCoveringGrant(action, entityId);
  if (!grant) return null;
  return isLive(grant, now).live ? grant : null;
}

/**
 * The single question every write path asks: may I?
 *
 * This is deliberately synchronous in shape but async in fact — a grant lookup
 * touches IndexedDB. Callers on a hot path (a tap) may pass `source: 'user'`,
 * which never needs a lookup.
 */
export async function can(request: PermissionRequest): Promise<PermissionDecision> {
  const level = authorityOf(request.action);
  const now = request.now ?? new Date();

  if (level === 1) {
    return { allowed: true, level, requiresConfirmation: false, reason: 'autonomous' };
  }

  if (request.source === 'user') {
    // A person editing their own plan is the plan — except for level 3, where
    // "allowed" must not be true until the confirmation actually exists.
    if (level === 3 && request.confirmed !== true) {
      return { allowed: false, level, requiresConfirmation: true, reason: 'needs-confirmation' };
    }
    return { allowed: true, level, requiresConfirmation: false, reason: 'user-action' };
  }

  const grant = await findCoveringGrant(request.action, request.entityId);
  if (!grant) {
    return {
      allowed: level !== 3 && request.confirmed === true,
      level,
      requiresConfirmation: true,
      reason: 'needs-grant',
    };
  }

  // A grant that exists but no longer applies is reported for what it is.
  const state = isLive(grant, now);
  if (!state.live) {
    return { allowed: false, level, requiresConfirmation: true, reason: state.reason };
  }

  return {
    allowed: true,
    level,
    requiresConfirmation: level === 3 && request.confirmed !== true,
    reason: 'granted',
    grantId: grant.id,
  };
}

export async function grantPermission(input: {
  action: ActionKind;
  entityId?: string | null;
  expiresAt?: string | null;
  maxUses?: number | null;
  note?: string;
  now?: Date;
}): Promise<PermissionGrant> {
  const now = input.now ?? new Date();
  const grant: PermissionGrant = {
    id: createId('per'),
    action: input.action,
    entityId: input.entityId ?? null,
    grantedAt: now.toISOString(),
    expiresAt: input.expiresAt ?? null,
    maxUses: input.maxUses ?? null,
    uses: 0,
    revokedAt: null,
    note: input.note ?? '',
  };
  await db.permissionGrants.put(grant);
  return grant;
}

export async function revokePermission(id: string, now: Date = new Date()): Promise<void> {
  const grant = await db.permissionGrants.get(id);
  if (!grant || grant.revokedAt) return;
  await db.permissionGrants.put({ ...grant, revokedAt: now.toISOString() });
}

/** Counts one use; the grant retires itself when the budget runs out. */
export async function consumeGrant(id: string, now: Date = new Date()): Promise<PermissionGrant | null> {
  const grant = await db.permissionGrants.get(id);
  if (!grant) return null;
  const state = isLive(grant, now);
  if (!state.live) return null;

  // The use counter is the whole story: `maxUses` makes the next lookup return
  // "exhausted" without mutating `revokedAt`, so revocation stays a human act.
  const next: PermissionGrant = { ...grant, uses: grant.uses + 1 };
  await db.permissionGrants.put(next);
  return next;
}

export async function listGrants(onlyLive = true, now: Date = new Date()): Promise<PermissionGrant[]> {
  const grants = await db.permissionGrants.toArray();
  const filtered = onlyLive ? grants.filter((grant) => isLive(grant, now).live) : grants;
  return filtered.sort((a, b) => (b.revokedAt ?? '').localeCompare(a.revokedAt ?? ''));
}

/** Machine-readable explanation for the audit trail and the UI. */
export function explainDecision(decision: PermissionDecision): string {
  switch (decision.reason) {
    case 'autonomous':
      return 'این اقدام محاسباتی است و مجوز نمی‌خواهد.';
    case 'user-action':
      return 'خودت درخواست کرده‌ای؛ انجام شد.';
    case 'granted':
      return 'بر اساس مجوزی که قبلاً داده‌ای انجام شد.';
    case 'needs-grant':
      return 'برای این کار به مجوز مشخص نیاز است.';
    case 'needs-confirmation':
      return 'این کار برگشت‌ناپذیر است و تأیید صریح می‌خواهد.';
    case 'revoked':
      return 'مجوز لغو شده است.';
    case 'expired':
      return 'مدت مجوز تمام شده است.';
    case 'exhausted':
      return 'سهمیهٔ مجوز مصرف شده است.';
    default:
      return 'وضعیت مجوز نامشخص است.';
  }
}
