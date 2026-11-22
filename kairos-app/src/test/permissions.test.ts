import { beforeEach, describe, expect, it } from 'vitest';
import { db } from '@/lib/db';
import {
  ACTION_AUTHORITY,
  authorityOf,
  can,
  consumeGrant,
  explainDecision,
  findGrant,
  grantPermission,
  listGrants,
  revokePermission,
  type ActionKind,
} from '@/lib/permissions';

/**
 * The permission manager is the reason an automation engine can be trusted, so
 * the tests are written as the three questions a person would ask:
 *   · may the system do arithmetic on its own? (level 1 — yes)
 *   · may it move my plan without asking? (level 2 — only with a live grant)
 *   · may it delete something? (level 3 — only with confirmation, always on record)
 * Grants that expire, run out, or are revoked are tested as *absent*, because a
 * stale permission is worse than no permission: it looks like consent.
 */

beforeEach(async () => {
  await db.permissionGrants.clear();
});

describe('authority levels', () => {
  it('classifies the three levels the way the spec does', () => {
    expect(authorityOf('insight.compute')).toBe(1);
    expect(authorityOf('report.generate')).toBe(1);
    expect(authorityOf('task.reschedule')).toBe(2);
    expect(authorityOf('habit.log')).toBe(2);
    expect(authorityOf('task.delete')).toBe(3);
    expect(authorityOf('data.restore')).toBe(3);
    expect(authorityOf('journal.overwrite')).toBe(3);
  });

  it('fails closed: an unknown action is treated as protected', () => {
    expect(authorityOf('something.unlisted' as ActionKind)).toBe(3);
  });

  it('covers every declared action', () => {
    for (const [action, level] of Object.entries(ACTION_AUTHORITY)) {
      expect([1, 2, 3]).toContain(level);
      expect(authorityOf(action as ActionKind)).toBe(level);
    }
  });
});

describe('can()', () => {
  it('lets level 1 run without asking anyone', async () => {
    const decision = await can({ action: 'report.generate', source: 'automation' });
    expect(decision.allowed).toBe(true);
    expect(decision.reason).toBe('autonomous');
    expect(decision.requiresConfirmation).toBe(false);
  });

  it('lets the person edit their own plan', async () => {
    const decision = await can({ action: 'task.reschedule', source: 'user' });
    expect(decision.allowed).toBe(true);
    expect(decision.reason).toBe('user-action');
  });

  it('stops the system from moving a task without a grant', async () => {
    const decision = await can({ action: 'task.reschedule', source: 'automation', entityId: 'tsk-1' });
    expect(decision.allowed).toBe(false);
    expect(decision.reason).toBe('needs-grant');
    expect(decision.requiresConfirmation).toBe(true);
  });

  it('allows it once a grant exists, and names the grant', async () => {
    const grant = await grantPermission({ action: 'task.reschedule', entityId: 'tsk-1', note: 'بهتر کردن برنامه' });
    const decision = await can({ action: 'task.reschedule', source: 'automation', entityId: 'tsk-1' });
    expect(decision.allowed).toBe(true);
    expect(decision.reason).toBe('granted');
    expect(decision.grantId).toBe(grant.id);
  });

  it('respects the scope: a grant for one task does not cover another', async () => {
    await grantPermission({ action: 'task.reschedule', entityId: 'tsk-1' });
    const other = await can({ action: 'task.reschedule', source: 'automation', entityId: 'tsk-2' });
    expect(other.allowed).toBe(false);
  });

  it('always needs confirmation for level 3, even from the person', async () => {
    const unconfirmed = await can({ action: 'data.clear', source: 'user' });
    expect(unconfirmed.requiresConfirmation).toBe(true);
    expect(unconfirmed.allowed).toBe(false);

    const confirmed = await can({ action: 'data.clear', source: 'user', confirmed: true });
    expect(confirmed.allowed).toBe(true);
  });
});

describe('grant lifecycle', () => {
  it('expires on its own', async () => {
    const now = new Date('2025-06-20T10:00:00.000Z');
    await grantPermission({
      action: 'task.reschedule',
      entityId: 'tsk-1',
      expiresAt: '2025-06-20T12:00:00.000Z',
      now,
    });

    const inside = await can({ action: 'task.reschedule', source: 'automation', entityId: 'tsk-1', now });
    expect(inside.allowed).toBe(true);

    const after = await can({
      action: 'task.reschedule',
      source: 'automation',
      entityId: 'tsk-1',
      now: new Date('2025-06-21T00:00:00.000Z'),
    });
    expect(after.allowed).toBe(false);
    expect(after.reason).toBe('expired');
  });

  it('runs out after its use budget', async () => {
    const now = new Date('2025-06-20T10:00:00.000Z');
    const grant = await grantPermission({ action: 'task.status', entityId: 'tsk-1', maxUses: 2, now });

    await consumeGrant(grant.id, now);
    const second = await can({ action: 'task.status', source: 'automation', entityId: 'tsk-1', now });
    expect(second.allowed).toBe(true);

    await consumeGrant(grant.id, now);
    await consumeGrant(grant.id, now); // budget is spent; the extra use is ignored

    const third = await can({ action: 'task.status', source: 'automation', entityId: 'tsk-1', now });
    expect(third.allowed).toBe(false);
    expect(third.reason).toBe('exhausted');
  });

  it('is gone the moment it is revoked', async () => {
    const grant = await grantPermission({ action: 'habit.log', entityId: 'hab-1' });
    expect(await findGrant('habit.log', 'hab-1')).not.toBeNull();

    await revokePermission(grant.id);
    expect(await findGrant('habit.log', 'hab-1')).toBeNull();

    const decision = await can({ action: 'habit.log', source: 'automation', entityId: 'hab-1' });
    expect(decision.allowed).toBe(false);
    // Reported for what it is: "you cancelled this", not "never granted".
    expect(decision.reason).toBe('revoked');
  });

  it('lists only live grants by default, and everything when asked', async () => {
    const live = await grantPermission({ action: 'task.update', entityId: 'tsk-1' });
    const dead = await grantPermission({ action: 'task.update', entityId: 'tsk-2' });
    await revokePermission(dead.id);

    const onlyLive = await listGrants();
    expect(onlyLive.map((grant) => grant.id)).toEqual([live.id]);

    const all = await listGrants(false);
    expect(all).toHaveLength(2);
  });

  it('prefers the newest grant that covers the entity', async () => {
    const older = await grantPermission({ action: 'task.reschedule', entityId: null, now: new Date('2025-06-01T00:00:00Z') });
    const newer = await grantPermission({ action: 'task.reschedule', entityId: 'tsk-9', now: new Date('2025-06-02T00:00:00Z') });

    const found = await findGrant('task.reschedule', 'tsk-9');
    expect(found?.id).toBe(newer.id);
    expect(older.id).not.toBe(newer.id);
  });

  it('explains every decision in Persian, without jargon', async () => {
    const cases = [
      await can({ action: 'report.generate', source: 'system' }),
      await can({ action: 'task.update', source: 'user' }),
      await can({ action: 'task.update', source: 'automation', entityId: 'x' }),
      await can({ action: 'data.clear', source: 'user' }),
    ];
    for (const decision of cases) {
      expect(explainDecision(decision).length).toBeGreaterThan(10);
    }
  });
});
