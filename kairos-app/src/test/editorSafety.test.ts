import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createGoal, createProject, createStage, db, deleteProject, ensureSettings, toggleStage, updateGoal, updateSettings, upsertJournalEntry } from '@/lib/db';

beforeEach(async () => {
  await db.transaction('rw', db.tables, () => Promise.all(db.tables.map((table) => table.clear())));
});

describe('concurrent persistence safety', () => {
  it('merges simultaneous independent settings writes', async () => {
    await ensureSettings();
    await Promise.all([updateSettings({ displayName: 'نام تازه' }), updateSettings({ dailyTaskGoal: 12 })]);
    expect(await db.settings.get('default')).toMatchObject({ displayName: 'نام تازه', dailyTaskGoal: 12 });
  });

  it('does not overwrite a newer journal and rolls back if its undo cannot be stored', async () => {
    const first = await upsertJournalEntry('2026-01-02', { note: 'یادداشت اول' }, null);
    await upsertJournalEntry('2026-01-02', { note: 'نسخهٔ تازه' }, first);
    await expect(upsertJournalEntry('2026-01-02', { note: 'ویرایش قدیمی' }, first)).rejects.toThrow('journal-edit-conflict');
    const current = await db.journal.get(first.id);
    const spy = vi.spyOn(db.undoHistory, 'add').mockRejectedValueOnce(new Error('history failure'));
    try {
      await expect(upsertJournalEntry('2026-01-02', { note: 'نباید ذخیره شود' }, current)).rejects.toThrow('history failure');
      expect(await db.journal.get(first.id)).toEqual(current);
    } finally { spy.mockRestore(); }
  });

  it('serializes separate goal edits without losing fields', async () => {
    const goal = await createGoal({ title: 'اول', description: '', category: 'work', currentValue: 0, targetValue: 10, unit: 'بار', deadline: null });
    await Promise.all([updateGoal(goal.id, { title: 'نام جدید' }), updateGoal(goal.id, { currentValue: 3 })]);
    expect(await db.goals.get(goal.id)).toMatchObject({ title: 'نام جدید', currentValue: 3 });
  });

  it('prevents an orphan stage when stage creation races project deletion', async () => {
    const project = await createProject({ title: 'پروژه', description: '', category: 'work', goalId: null, startDate: null, dueDate: null });
    await Promise.allSettled([
      deleteProject(project.id, project),
      createStage({ projectId: project.id, title: 'مرحله', kind: 'stage', order: 0, weight: 1, dueDate: null }),
    ]);
    expect(await db.projects.get(project.id)).toBeUndefined();
    expect(await db.projectStages.where('projectId').equals(project.id).count()).toBe(0);
  });

  it('serializes two stage toggles instead of losing one', async () => {
    const project = await createProject({ title: 'پروژه', description: '', category: 'work', goalId: null, startDate: null, dueDate: null });
    const stage = await createStage({ projectId: project.id, title: 'مرحله', kind: 'stage', order: 0, weight: 1, dueDate: null });
    await Promise.all([toggleStage(stage.id), toggleStage(stage.id)]);
    expect((await db.projectStages.get(stage.id))?.done).toBe(false);
  });
});
