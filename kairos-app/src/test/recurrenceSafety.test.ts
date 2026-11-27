import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createTask, db, setTaskStatus, updateTask } from '@/lib/db';
import { lastUndoable, undoEntry } from '@/lib/undo';

const input = {
  title: 'تکرار', description: '', dueDate: '2026-11-20', dueTime: '10:00',
  priority: 'high' as const, quadrant: 'q2' as const, category: 'work' as const,
  tags: ['مهم'], status: 'todo' as const, subtasks: [{ id: 'sub', title: 'قدم', completed: true }],
  recurrence: 'daily' as const, reminderEnabled: true, reminderOffsetMin: 15,
  projectId: 'project', goalId: 'goal', estimateMin: 30, dependsOn: ['prerequisite'],
};

beforeEach(async () => {
  await db.transaction('rw', db.tables, () => Promise.all(db.tables.map((table) => table.clear())));
});

describe('atomic recurring completion', () => {
  it('creates one next occurrence under concurrent completion and preserves links', async () => {
    const task = await createTask(input);
    const results = await Promise.all([setTaskStatus(task.id, 'completed'), setTaskStatus(task.id, 'completed')]);
    const next = results.find((result) => result.spawnedNext)?.spawnedNext;
    expect(results.filter((result) => result.spawnedNext)).toHaveLength(1);
    expect(await db.tasks.count()).toBe(2);
    expect(next).toMatchObject({ dueDate: '2026-11-21', projectId: 'project', goalId: 'goal', estimateMin: 30, dependsOn: ['prerequisite'], tags: ['مهم'] });
    expect(next?.subtasks[0]).toMatchObject({ title: 'قدم', completed: false });
    expect(next?.subtasks[0].id).not.toBe('sub');
    expect(await db.undoHistory.count()).toBe(1);
  });

  it('creating a completed recurring task also creates its next occurrence atomically', async () => {
    const task = await createTask({ ...input, status: 'completed' });
    const rows = await db.tasks.toArray();
    expect(rows).toHaveLength(2);
    expect(rows.find((row) => row.id !== task.id)).toMatchObject({ status: 'todo', dueDate: '2026-11-21', recurrenceSeriesId: task.recurrenceSeriesId });
  });

  it('rolls back creation of a completed recurring series if its ledger write fails', async () => {
    const spy = vi.spyOn(db.eventHistory, 'add').mockRejectedValueOnce(new Error('ledger failure'));
    try {
      await expect(createTask({ ...input, status: 'completed' })).rejects.toThrow('ledger failure');
      expect(await db.tasks.count()).toBe(0);
    } finally { spy.mockRestore(); }
  });

  it('rolls back completion and next occurrence if the undo record cannot be stored', async () => {
    const task = await createTask(input);
    const spy = vi.spyOn(db.undoHistory, 'add').mockRejectedValueOnce(new Error('disk failure'));
    try {
      await expect(setTaskStatus(task.id, 'completed')).rejects.toThrow('disk failure');
      expect(await db.tasks.toArray()).toEqual([task]);
      expect(await db.undoHistory.count()).toBe(0);
    } finally { spy.mockRestore(); }
  });

  it('reverts completion and removes the exact unchanged generated occurrence', async () => {
    const task = await createTask(input);
    const { spawnedNext } = await setTaskStatus(task.id, 'completed');
    const entry = (await lastUndoable())!;
    expect((await undoEntry(entry.id)).ok).toBe(true);
    expect(await db.tasks.get(task.id)).toEqual(task);
    expect(await db.tasks.get(spawnedNext!.id)).toBeUndefined();
  });

  it('refuses undo after the generated occurrence was edited', async () => {
    const task = await createTask(input);
    const { spawnedNext } = await setTaskStatus(task.id, 'completed');
    const entry = (await lastUndoable())!;
    await updateTask(spawnedNext!.id, { title: 'ویرایش بعدی' });
    expect((await undoEntry(entry.id)).reason).toBe('recurring-unsafe');
    expect((await db.tasks.get(task.id))?.status).toBe('completed');
    expect((await db.tasks.get(spawnedNext!.id))?.title).toBe('ویرایش بعدی');
  });

  it('does not remove an occurrence referenced by a newer task', async () => {
    const task = await createTask(input);
    const { spawnedNext } = await setTaskStatus(task.id, 'completed');
    const entry = (await lastUndoable())!;
    await createTask({ ...input, title: 'dependent', recurrence: 'none', dependsOn: [spawnedNext!.id] });
    expect((await undoEntry(entry.id)).reason).toBe('recurring-unsafe');
    expect(await db.tasks.get(spawnedNext!.id)).toBeDefined();
  });

  it('saves an edit and recurring completion together and undoes both together', async () => {
    const task = await createTask({ ...input, recurrence: 'none' });
    const result = await setTaskStatus(task.id, 'completed', { expected: task, patch: { title: 'نام تازه', recurrence: 'daily', dueDate: '2026-11-22' } });
    expect(result.spawnedNext).toMatchObject({ title: 'نام تازه', dueDate: '2026-11-23', recurrenceSeriesId: task.id });
    expect((await undoEntry((await lastUndoable())!.id)).ok).toBe(true);
    expect(await db.tasks.get(task.id)).toEqual(task);
    expect(await db.tasks.get(result.spawnedNext!.id)).toBeUndefined();
  });

  it('refuses a stale edit without writing any portion of the form', async () => {
    const task = await createTask(input);
    await updateTask(task.id, { description: 'ویرایش بعدی' });
    await expect(setTaskStatus(task.id, 'completed', { expected: task, patch: { title: 'فرم قدیمی' } })).rejects.toThrow('task-edit-conflict');
    expect(await db.tasks.get(task.id)).toMatchObject({ title: input.title, description: 'ویرایش بعدی', status: 'todo' });
    expect(await db.tasks.count()).toBe(1);
  });

  it('serializes simultaneous independent edits and keeps their undo snapshots', async () => {
    const task = await createTask({ ...input, recurrence: 'none' });
    await Promise.all([updateTask(task.id, { title: 'جدید' }), updateTask(task.id, { dueTime: '12:30' })]);
    expect(await db.tasks.get(task.id)).toMatchObject({ title: 'جدید', dueTime: '12:30' });
    const latest = (await lastUndoable())!;
    expect((await undoEntry(latest.id)).ok).toBe(true);
    expect((await undoEntry((await lastUndoable())!.id)).ok).toBe(true);
    expect(await db.tasks.get(task.id)).toEqual(task);
  });

  it('rejects an updateTask status bypass on recurring tasks', async () => {
    const task = await createTask(input);
    await expect(updateTask(task.id, { status: 'completed' })).rejects.toThrow('recurring-status-requires-setTaskStatus');
    expect(await db.tasks.get(task.id)).toEqual(task);
  });

  it('keeps independent same-title recurring series separate', async () => {
    const first = await createTask(input);
    const otherSeries = await createTask({ ...input, dueDate: '2026-11-21' });
    expect(first.recurrenceSeriesId).not.toBe(otherSeries.recurrenceSeriesId);
    const result = await setTaskStatus(first.id, 'completed');
    expect(result.spawnedNext?.dueDate).toBe('2026-11-21');
    expect(result.spawnedNext?.recurrenceSeriesId).toBe(first.recurrenceSeriesId);
    expect(await db.tasks.count()).toBe(3);
  });

  it('never deletes a pre-existing candidate when undoing completion', async () => {
    const task = await createTask(input);
    const existing = await createTask({ ...input, dueDate: '2026-11-21', recurrenceSeriesId: task.recurrenceSeriesId });
    const result = await setTaskStatus(task.id, 'completed');
    expect(result.spawnedNext).toBeUndefined();
    expect((await undoEntry((await lastUndoable())!.id)).ok).toBe(true);
    expect(await db.tasks.get(existing.id)).toEqual(existing);
  });
});
