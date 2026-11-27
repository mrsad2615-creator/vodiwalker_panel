import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearHabitLog, createHabit, db, deleteHabit, updateHabit, upsertHabitLog } from '@/lib/db';
import { lastUndoable, undoEntry } from '@/lib/undo';

beforeEach(async () => {
  await db.transaction('rw', db.tables, () => Promise.all(db.tables.map((table) => table.clear())));
});

async function habit() {
  return createHabit({ name: 'تمرین', description: '', icon: 'brain', accentColor: '#6D4AFF', scheduleType: 'daily', scheduledDays: [0, 1, 2, 3, 4, 5, 6], weeklyTarget: 7, trackingMode: 'boolean', targetValue: 1, unit: '', reminderTime: null, startDate: '2026-01-01' });
}

describe('habit logging and deletion safety', () => {
  it('rolls back a log if the undo entry cannot be stored', async () => {
    const row = await habit();
    const spy = vi.spyOn(db.undoHistory, 'add').mockRejectedValueOnce(new Error('undo write failed'));
    try {
      await expect(upsertHabitLog(row.id, '2026-01-02', 'completed', 1)).rejects.toThrow('undo write failed');
      expect(await db.habitLogs.count()).toBe(0);
    } finally { spy.mockRestore(); }
  });

  it('rejects a second stale tap rather than overwriting the newer log', async () => {
    const row = await habit();
    const expected = { habit: row, log: null };
    const results = await Promise.allSettled([
      upsertHabitLog(row.id, '2026-01-02', 'completed', 1, undefined, expected),
      upsertHabitLog(row.id, '2026-01-02', 'missed', 0, undefined, expected),
    ]);
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    expect(results.filter((result) => result.status === 'rejected')).toHaveLength(1);
    expect(await db.habitLogs.count()).toBe(1);
  });

  it('prevents orphaned log creation when deletion races a new log', async () => {
    const row = await habit();
    await Promise.allSettled([deleteHabit(row.id, row), upsertHabitLog(row.id, '2026-01-02', 'completed', 1)]);
    expect(await db.habits.get(row.id)).toBeUndefined();
    expect(await db.habitLogs.where('habitId').equals(row.id).count()).toBe(0);
    await expect(upsertHabitLog(row.id, '2026-01-03', 'completed', 1)).rejects.toThrow('habit-not-found');
  });

  it('removes historical undo entries even for a previously cleared log', async () => {
    const row = await habit();
    await upsertHabitLog(row.id, '2026-01-02', 'completed', 1);
    await clearHabitLog(row.id, '2026-01-02');
    const entry = await lastUndoable();
    expect(entry?.kind).toBe('habit.log');
    await deleteHabit(row.id, row);
    expect((await undoEntry(entry!.id)).ok).toBe(false);
    expect(await db.habitLogs.count()).toBe(0);
  });

  it('refuses a deletion whose confirmed habit snapshot is stale', async () => {
    const row = await habit();
    await updateHabit(row.id, { name: 'نام جدید' });
    await expect(deleteHabit(row.id, row)).rejects.toThrow('habit-delete-conflict');
    expect((await db.habits.get(row.id))?.name).toBe('نام جدید');
  });
});
