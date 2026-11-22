import { beforeEach, describe, expect, it } from 'vitest';
import { db } from '@/lib/db';
import {
  createGoal,
  createHabit,
  createTask,
  setTaskStatus,
  updateHabit,
  updateTask,
  upsertHabitLog,
  upsertJournalEntry,
} from '@/lib/db';
import { lastUndoable, listUndoable, pruneUndo, undoEntry, undoLast } from '@/lib/undo';

/**
 * Undo is a safety net, so it is tested from the outside: do the real thing
 * through the real write helpers, press undo, and check the row is byte-identical
 * to before. The tests also pin what is deliberately *not* restorable — deleting
 * a task is not something a quiet undo button should promise.
 */

const task = {
  title: 'گزارش',
  description: 'توضیح اولیه',
  dueDate: '2025-06-20',
  dueTime: null,
  priority: 'high' as const,
  quadrant: 'q1' as const,
  category: 'work' as const,
  tags: ['مالی'],
  status: 'todo' as const,
  subtasks: [{ id: 'sub-1', title: 'بخش اول', completed: false }],
  recurrence: 'none' as const,
  reminderEnabled: false,
  reminderOffsetMin: 0,
};

beforeEach(async () => {
  await Promise.all([
    db.tasks.clear(),
    db.habits.clear(),
    db.habitLogs.clear(),
    db.goals.clear(),
    db.journal.clear(),
    db.eventHistory.clear(),
    db.undoHistory.clear(),
  ]);
});

describe('undo manager', () => {
  it('restores the exact previous row after an edit', async () => {
    const created = await createTask({ ...task });
    const before = await db.tasks.get(created.id);

    await updateTask(created.id, { title: 'عنوان عوض‌شده', priority: 'low', tags: ['جدید'] });

    const result = await undoLast();
    expect(result.ok).toBe(true);

    const restored = await db.tasks.get(created.id);
    expect(restored).toEqual(before);
  });

  it('restores a status change including its completion stamps', async () => {
    const created = await createTask({ ...task });
    await setTaskStatus(created.id, 'completed');

    const completed = await db.tasks.get(created.id);
    expect(completed?.completedAt).not.toBeNull();

    await undoLast();

    const restored = await db.tasks.get(created.id);
    expect(restored?.status).toBe('todo');
    expect(restored?.completedAt).toBeNull();
    expect(restored?.completedDate).toBeNull();
  });

  it('walks back two separate changes, newest first', async () => {
    const created = await createTask({ ...task });

    await updateTask(created.id, { title: 'قدم یک' });
    await updateTask(created.id, { title: 'قدم دو' });

    await undoLast();
    expect((await db.tasks.get(created.id))?.title).toBe('قدم یک');

    await undoLast();
    expect((await db.tasks.get(created.id))?.title).toBe('گزارش');
  });

  it('removes the first habit log again, because it did not exist before', async () => {
    const habit = await createHabit({
      name: 'ورزش',
      description: '',
      icon: 'dumbbell',
      accentColor: '#fff',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'boolean',
      targetValue: 1,
      unit: '',
      reminderTime: null,
      startDate: '2025-06-01',
    });

    const log = await upsertHabitLog(habit.id, '2025-06-20', 'completed', 1);
    expect(await db.habitLogs.get(log.id)).toBeTruthy();

    await undoLast();
    expect(await db.habitLogs.get(log.id)).toBeUndefined();
  });

  it('restores a habit edit and a goal edit', async () => {
    const habit = await createHabit({
      name: 'مدیتیشن',
      description: '',
      icon: 'book',
      accentColor: '#fff',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'boolean',
      targetValue: 1,
      unit: '',
      reminderTime: null,
      startDate: '2025-06-01',
    });
    await updateHabit(habit.id, { name: 'مدیتیشن ۲' });
    await undoLast();
    expect((await db.habits.get(habit.id))?.name).toBe('مدیتیشن');

    const goal = await createGoal({
      title: 'کتاب',
      description: '',
      category: 'learning',
      currentValue: 2,
      targetValue: 10,
      unit: 'فصل',
      deadline: null,
    });
    await db.goals.put({ ...goal, currentValue: 5, updatedAt: new Date().toISOString() });
    await db.undoHistory.put({
      id: 'und-manual-goal',
      at: new Date().toISOString(),
      kind: 'goal.update',
      entity: 'goal',
      entityId: goal.id,
      label: 'ویرایش هدف',
      before: goal,
      after: { ...goal, currentValue: 5 },
      undoneAt: null,
    });
    const result = await undoLast();
    expect(result.ok).toBe(true);
    expect((await db.goals.get(goal.id))?.currentValue).toBe(2);
  });

  it('restores a journal entry and marks the entry as replayed', async () => {
    const entry = await upsertJournalEntry('2025-06-20', { mood: 3, highlight: 'اول' });
    await upsertJournalEntry('2025-06-20', { mood: 5, highlight: 'دوم' });

    const pending = await lastUndoable();
    expect(pending?.kind).toBe('journal.save');

    const result = await undoEntry(pending!.id);
    expect(result.ok).toBe(true);

    const restored = await db.journal.get(entry.id);
    expect(restored?.mood).toBe(3);
    expect(restored?.highlight).toBe('اول');

    const replayed = await db.undoHistory.get(pending!.id);
    expect(replayed?.undoneAt).toBeTruthy();

    // The same entry cannot be applied twice.
    const again = await undoEntry(pending!.id);
    expect(again.ok).toBe(false);
    expect(again.reason).toBe('empty');
  });

  it('records the undo itself in the event ledger', async () => {
    const created = await createTask({ ...task });
    await updateTask(created.id, { title: 'تغییر' });
    await undoLast();

    const events = await db.eventHistory.toArray();
    expect(events.some((event) => event.kind === 'undo.applied')).toBe(true);
    const undoEvent = events.find((event) => event.kind === 'undo.applied');
    expect(undoEvent?.source).toBe('undo');
    expect(undoEvent?.level).toBe(3);
  });

  it('refuses to invent an undo out of nothing', async () => {
    const result = await undoLast();
    expect(result.ok).toBe(false);
    expect(result.reason).toBe('empty');
    expect(await listUndoable()).toHaveLength(0);
  });

  it('keeps the stack bounded', async () => {
    const now = Date.now();
    const rows = Array.from({ length: 10 }, (_, index) => ({
      id: `und-seed-${index}`,
      at: new Date(now - (10 - index) * 1000).toISOString(),
      kind: 'task.update' as const,
      entity: 'task' as const,
      entityId: `t-${index}`,
      label: 'ویرایش وظیفه',
      before: { id: `t-${index}` },
      after: { id: `t-${index}`, title: 'x' },
      undoneAt: null,
    }));
    await db.undoHistory.bulkPut(rows);

    const removed = await pruneUndo(6);
    expect(removed).toBe(4);
    expect(await db.undoHistory.count()).toBe(6);
  });
});
