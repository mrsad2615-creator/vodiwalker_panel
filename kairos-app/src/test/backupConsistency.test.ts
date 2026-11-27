import { beforeEach, describe, expect, it } from 'vitest';
import { createTask, db, ensureSettings, exportBackup, importBackup, inspectBackup } from '@/lib/db';

beforeEach(async () => {
  await db.transaction('rw', db.tables, () => Promise.all(db.tables.map((table) => table.clear())));
  await ensureSettings();
});

describe('consistent backup snapshot', () => {
  it('exports related records at the same committed state while another transaction updates them', async () => {
    const task = await createTask({ title: '0', description: '', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0, projectId: 'p' });
    await db.projects.put({ id: 'p', title: '0', description: '', category: 'work', status: 'active', goalId: null, startDate: null, dueDate: null, createdAt: '2026-01-01', updatedAt: '2026-01-01' });
    for (let i = 1; i <= 20; i++) {
      const [backup] = await Promise.all([
        exportBackup(),
        db.transaction('rw', [db.tasks, db.projects], async () => {
          await db.tasks.update(task.id, { title: String(i) });
          await db.projects.update('p', { title: String(i) });
        }),
      ]);
      expect(backup.tasks[0].title).toBe(backup.projects?.[0].title);
    }
  });

  it('previews collisions without writes and recalculates safely when data changes before import', async () => {
    const task = await createTask({ title: 'existing', description: '', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
    const backup = await exportBackup();
    backup.tasks.push({ ...task, id: 'incoming', title: 'incoming' });
    const before = await db.tasks.toArray();
    expect(await inspectBackup(backup)).toEqual({ added: 1, skipped: 1 });
    expect(await db.tasks.toArray()).toEqual(before);
    await db.tasks.put({ ...task, id: 'incoming', title: 'created after preview' });
    expect(await importBackup(backup)).toEqual({ added: 0, skipped: 2 });
    expect((await db.tasks.get('incoming'))?.title).toBe('created after preview');
  });

  it('restores explicitly chosen preferences without importing secrets or device permissions', async () => {
    await db.settings.update('default', { displayName: 'فایل', homeMode: 'compact', accentColor: '#123456', navShortcuts: ['projects', 'habits', 'focus', 'motivation'], motivationFavorites: ['m1'], aiKey: 'not-exported', notificationsEnabled: false });
    const backup = await exportBackup();
    await db.settings.update('default', { displayName: 'دستگاه', homeMode: 'detailed', aiKey: 'device-secret', notificationsEnabled: true, aiAutoApply: false });
    backup.settings.aiKey = 'attacker-secret';
    backup.settings.aiAutoApply = true;
    await importBackup(backup, { restorePreferences: true });
    expect(await db.settings.get('default')).toMatchObject({ displayName: 'فایل', homeMode: 'compact', accentColor: '#123456', navShortcuts: ['projects', 'habits', 'focus', 'motivation'], motivationFavorites: ['m1'], aiKey: 'device-secret', notificationsEnabled: true, aiAutoApply: false });
  });

  it('rejects malformed opted-in preferences before writing records', async () => {
    const backup = await exportBackup();
    // Use a valid task to ensure the settings validation is what stops the import.
    const task = await createTask({ title: 'new', description: '', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
    backup.tasks = [{ ...task, id: 'incoming' }];
    backup.settings.dailyTaskGoal = -1;
    await expect(importBackup(backup, { restorePreferences: true })).rejects.toThrow('invalid-backup-settings');
    expect(await db.tasks.get('incoming')).toBeUndefined();
    expect((await db.settings.get('default'))?.dailyTaskGoal).toBe(6);
  });

  it('does not alter records or preferences when an active session blocks preference restoration', async () => {
    const task = await createTask({ title: 'new', description: '', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
    const backup = await exportBackup();
    backup.tasks = [{ ...task, id: 'incoming' }];
    await db.activeRun.put({ id: 'active' } as never);
    await expect(importBackup(backup, { restorePreferences: true })).rejects.toThrow('focus-session-active');
    expect(await db.tasks.get('incoming')).toBeUndefined();
  });

  it('excludes the device AI key without modifying stored settings', async () => {
    await db.settings.update('default', { aiKey: 'secret-not-for-export' });
    const backup = await exportBackup();
    expect(backup.settings.aiKey).toBe('');
    expect((await db.settings.get('default'))?.aiKey).toBe('secret-not-for-export');
    expect(JSON.stringify(backup)).not.toContain('secret-not-for-export');
  });
});
