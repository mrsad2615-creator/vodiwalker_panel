import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAllUserData, createTask, db, deleteTask, ensureSettings, setTaskStatus, updateTask } from '@/lib/db';
import { lastUndoable, undoEntry } from '@/lib/undo';

const taskInput = {
  title: 'اطلاعات خصوصی', description: 'متن محرمانه', dueDate: '2026-11-20', dueTime: null,
  priority: 'medium' as const, quadrant: 'q2' as const, category: 'work' as const,
  tags: [], status: 'todo' as const, subtasks: [], recurrence: 'none' as const,
  reminderEnabled: false, reminderOffsetMin: 0,
};

beforeEach(async () => {
  await db.transaction('rw', db.tables, async () => {
    await Promise.all(db.tables.map((table) => table.clear()));
  });
});

describe('local data safety', () => {
  it('clears private snapshots, history, grants and active session while preserving settings', async () => {
    const settings = await ensureSettings();
    const task = await createTask(taskInput);
    await updateTask(task.id, { title: 'عنوان جدید' });
    await db.table('migrationLog').put({ id: 'migration', at: new Date().toISOString(), snapshot: { tasks: [task] } });
    await db.table('permissionGrants').put({ id: 'grant', action: 'test' });
    await db.table('activeRun').put({ id: 'active', linkedTaskId: task.id, updatedAt: new Date().toISOString() });
    expect(await db.undoHistory.count()).toBeGreaterThan(0);

    await clearAllUserData();

    for (const table of db.tables.filter((table) => !['settings', 'eventHistory'].includes(table.name))) {
      expect(await table.count(), table.name).toBe(0);
    }
    expect(await db.settings.get('default')).toMatchObject({ displayName: settings.displayName });
    const events = await db.eventHistory.toArray();
    expect(events.every((event) => event.kind === 'data.cleared')).toBe(true);
    expect(JSON.stringify(events)).not.toContain('محرمانه');
    expect(await lastUndoable()).toBeNull();
  });

  it('rolls back the database transaction when a table fails to clear', async () => {
    const task = await createTask(taskInput);
    await updateTask(task.id, { title: 'edited' });
    const failure = vi.spyOn(db.migrationLog, 'clear').mockRejectedValueOnce(new Error('simulated failure'));
    try {
      await expect(clearAllUserData()).rejects.toThrow('simulated failure');
      expect((await db.tasks.get(task.id))?.title).toBe('edited');
      expect(await db.undoHistory.count()).toBeGreaterThan(0);
    } finally {
      failure.mockRestore();
    }
  });

  it('does not overwrite a later edit or resurrect a deleted row', async () => {
    const task = await createTask(taskInput);
    await updateTask(task.id, { title: 'first edit' });
    const entry = (await lastUndoable())!;
    await updateTask(task.id, { title: 'later edit' });
    expect((await undoEntry(entry.id)).reason).toBe('conflict');
    expect((await db.tasks.get(task.id))?.title).toBe('later edit');
    await db.tasks.delete(task.id);
    expect((await undoEntry(entry.id)).reason).toBe('conflict');
    expect(await db.tasks.get(task.id)).toBeUndefined();
  });

  it('deletes a confirmed task and its private history without touching other snapshots', async () => {
    const task = await createTask(taskInput);
    const other = await createTask({ ...taskInput, title: 'other' });
    const edited = (await updateTask(task.id, { title: 'سند خصوصی' }))!;
    await db.table('migrationLog').put({ id: 'migration', at: new Date().toISOString(), snapshot: { tasks: [task, other] } });
    await deleteTask(task.id, edited);
    expect(await db.tasks.get(task.id)).toBeUndefined();
    expect(await db.tasks.get(other.id)).toEqual(other);
    expect(await db.undoHistory.where('entityId').equals(task.id).count()).toBe(0);
    expect((await db.migrationLog.get('migration'))?.snapshot?.tasks).toEqual([other]);
  });

  it('refuses deletion if the confirmation snapshot became stale', async () => {
    const task = await createTask(taskInput);
    await updateTask(task.id, { title: 'تغییر بعدی' });
    await expect(deleteTask(task.id, task)).rejects.toThrow('task-delete-conflict');
    expect((await db.tasks.get(task.id))?.title).toBe('تغییر بعدی');
  });

  it('does not apply the same undo twice when requests race', async () => {
    const task = await createTask(taskInput);
    await updateTask(task.id, { title: 'changed' });
    const entry = (await lastUndoable())!;
    const results = await Promise.all([undoEntry(entry.id), undoEntry(entry.id)]);
    expect(results.filter((result) => result.ok)).toHaveLength(1);
    expect((await db.tasks.get(task.id))?.title).toBe(taskInput.title);
    expect((await db.eventHistory.toArray()).filter((event) => event.kind === 'undo.applied')).toHaveLength(1);
  });

  it('refuses a partial undo of recurring task completion', async () => {
    const task = await createTask({ ...taskInput, recurrence: 'daily' });
    const { spawnedNext } = await setTaskStatus(task.id, 'completed');
    expect(spawnedNext).toBeDefined();
    const entry = (await lastUndoable())!;
    // Old installations contain entries without a generated-occurrence snapshot.
    await db.undoHistory.update(entry.id, { recurrenceEffect: undefined });
    expect((await undoEntry(entry.id)).reason).toBe('recurring-unsafe');
    expect((await db.tasks.get(task.id))?.status).toBe('completed');
    expect(await db.tasks.get(spawnedNext!.id)).toBeDefined();
  });
});
