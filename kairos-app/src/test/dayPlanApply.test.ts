import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createTask, db, ensureSettings } from '@/lib/db';
import { buildDayPlan } from '@/lib/planner';
import { applyDayPlanPreview, previewDayPlan } from '@/lib/dayPlanApply';
import { undoEntry } from '@/lib/undo';

const TODAY = '2026-11-20';
const input = {
  title: 'کار', description: '', dueDate: TODAY, dueTime: null,
  priority: 'medium' as const, quadrant: 'q2' as const, category: 'work' as const,
  tags: [], status: 'todo' as const, subtasks: [], recurrence: 'none' as const,
  reminderEnabled: false, reminderOffsetMin: 0, estimateMin: 30,
};

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 10, 20, 9, 0, 0));
  await db.transaction('rw', db.tables, async () => { await Promise.all(db.tables.map((table) => table.clear())); });
});
afterEach(() => vi.useRealTimers());

async function proposed() {
  const first = await createTask({ ...input, title: 'الف' });
  const second = await createTask({ ...input, title: 'ب' });
  const busy = await createTask({ ...input, title: 'رزرو', dueTime: '09:15', estimateMin: 45 });
  const undated = await createTask({ ...input, title: 'بدون تاریخ', dueDate: null });
  const overdue = await createTask({ ...input, title: 'عقب‌افتاده', dueDate: '2026-11-19' });
  const tasks = await db.tasks.toArray();
  const plan = buildDayPlan({ tasks: [first, second, undated, overdue], sessions: [], todayKey: TODAY, goalMinutes: 60 });
  // Exercise multiple items independent of capacity inference.
  plan.blocks = [...plan.blocks, ...plan.overflow];
  const preview = previewDayPlan(plan, tasks, TODAY, '09:00');
  return { first, second, busy, undated, overdue, preview };
}

describe('safe daily plan scheduling', () => {
  it('shows only today’s eligible tasks, avoids estimated interval overlap and never wraps midnight', async () => {
    const { first, second, undated, overdue, preview } = await proposed();
    expect(preview.suggested.map((entry) => entry.taskId)).toEqual([first.id, second.id]);
    expect(preview.suggested.map((entry) => entry.time)).toEqual(['10:00', '10:30']);
    expect(preview.skipped).toBe(2);
    expect(preview.suggested.some((entry) => [undated.id, overdue.id].includes(entry.taskId))).toBe(false);
    const plan = buildDayPlan({ tasks: [first], sessions: [], todayKey: TODAY, goalMinutes: 60 });
    expect(previewDayPlan(plan, await db.tasks.toArray(), TODAY, '23:45').suggested).toHaveLength(0);
    expect(() => previewDayPlan(plan, [first], TODAY, '08:00')).toThrow('plan-invalid-time');
  });

  it('requires confirmation and commits all times with independent safe undo', async () => {
    const { first, second, preview } = await proposed();
    await expect(applyDayPlanPreview(preview, false)).rejects.toThrow('plan-not-confirmed');
    expect((await db.tasks.get(first.id))?.dueTime).toBeNull();
    expect(await applyDayPlanPreview(preview, true)).toBe(2);
    expect((await db.tasks.get(first.id))?.dueTime).toBe('10:00');
    expect((await db.tasks.get(second.id))?.dueTime).toBe('10:30');
    const entries = await db.undoHistory.toArray();
    expect(entries).toHaveLength(2);
    expect((await undoEntry(entries.find((entry) => entry.entityId === first.id)!.id)).ok).toBe(true);
    expect((await db.tasks.get(first.id))?.dueTime).toBeNull();
    expect((await db.tasks.get(second.id))?.dueTime).toBe('10:30');
  });

  it('rejects stale data and passed start times without writing any changes', async () => {
    const { first, busy, preview } = await proposed();
    await db.tasks.update(busy.id, { dueTime: '11:00' });
    await expect(applyDayPlanPreview(preview, true)).rejects.toThrow('plan-changed');
    expect((await db.tasks.get(first.id))?.dueTime).toBeNull();
    expect(await db.undoHistory.count()).toBe(0);
    const fresh = { ...preview, snapshot: (await db.tasks.toArray()).sort((a, b) => a.id.localeCompare(b.id)) };
    vi.setSystemTime(new Date(2026, 10, 20, 10, 1, 0));
    await expect(applyDayPlanPreview(fresh, true)).rejects.toThrow('plan-time-passed');
    expect((await db.tasks.get(first.id))?.dueTime).toBeNull();
  });

  it('does not apply a sample task when its visibility changes after preview', async () => {
    await ensureSettings();
    const task = await createTask({ ...input, sample: true });
    const plan = buildDayPlan({ tasks: [task], sessions: [], todayKey: TODAY, goalMinutes: 60 });
    const preview = previewDayPlan(plan, await db.tasks.toArray(), TODAY, '10:00');
    await db.settings.update('default', { sampleVisible: false });
    await expect(applyDayPlanPreview(preview, true)).rejects.toThrow('plan-changed');
    expect((await db.tasks.get(task.id))?.dueTime).toBeNull();
  });

  it('rolls back the entire batch if writing the second undo entry fails', async () => {
    const { first, second, preview } = await proposed();
    const originalAdd = db.eventHistory.add.bind(db.eventHistory);
    let updates = 0;
    const spy = vi.spyOn(db.eventHistory, 'add').mockImplementation((...args) => {
      if (++updates === 2) throw new Error('simulated failure');
      return originalAdd(...args);
    });
    try {
      await expect(applyDayPlanPreview(preview, true)).rejects.toThrow();
      expect((await db.tasks.get(first.id))?.dueTime).toBeNull();
      expect((await db.tasks.get(second.id))?.dueTime).toBeNull();
      expect(await db.undoHistory.count()).toBe(0);
    } finally { spy.mockRestore(); }
  });
});
