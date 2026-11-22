import { describe, expect, it } from 'vitest';
import { db } from '@/lib/db';
import { countEvents, eventCountsByKind, historyOf, listEvents, pruneEvents } from '@/lib/events';
import {
  archiveTask,
  clearAllUserData,
  createHabit,
  createTask,
  deleteTask,
  recordFocusSession,
  setTaskStatus,
  updateTask,
  upsertHabitLog,
  upsertJournalEntry,
} from '@/lib/db';

/**
 * The ledger is the foundation of everything that comes after it (undo,
 * automation, adaptive ranking, explanations), so it is tested for the two
 * mistakes that would poison that: events that never get written, and events
 * that keep state or text they should not.
 */

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

async function clearAll() {
  await Promise.all([
    db.tasks.clear(),
    db.habits.clear(),
    db.habitLogs.clear(),
    db.focusSessions.clear(),
    db.goals.clear(),
    db.journal.clear(),
    db.eventHistory.clear(),
    db.undoHistory.clear(),
  ]);
}

const baseTask = {
  title: 'کار نمونه',
  description: '',
  dueDate: '2025-06-20',
  dueTime: null,
  priority: 'medium' as const,
  quadrant: 'q2' as const,
  category: 'work' as const,
  tags: [],
  status: 'todo' as const,
  subtasks: [],
  recurrence: 'none' as const,
  reminderEnabled: false,
  reminderOffsetMin: 0,
};

describe('event history', () => {
  it('records a creation with the fields an audit needs', async () => {
    await clearAll();
    const task = await createTask({ ...baseTask });

    const events = await listEvents({ entityId: task.id });
    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ kind: 'task.created', entity: 'task', entityId: task.id });
    expect(events[0].source).toBe('user');
    expect(new Date(events[0].at).getTime()).toBeGreaterThan(0);
  });

  it('separates a status change from a plain edit, and counts postponements', async () => {
    await clearAll();
    const task = await createTask({ ...baseTask });

    await updateTask(task.id, { title: 'عنوان تازه' });
    await setTaskStatus(task.id, 'in_progress');
    // Moving the due date forward is the postponement signal the analytics use.
    await updateTask(task.id, { dueDate: '2025-06-25' });

    const kinds = (await historyOf(task.id)).map((event) => event.kind).reverse();
    expect(kinds).toContain('task.updated');
    expect(kinds).toContain('task.status');
    expect(kinds).toContain('task.postponed');

    const postponed = (await historyOf(task.id)).find((event) => event.kind === 'task.postponed');
    expect(postponed?.data?.count).toBe(1);
  });

  it('marks deletions as level 3 and archives as level 2', async () => {
    await clearAll();
    const task = await createTask({ ...baseTask });

    await archiveTask(task.id, true);
    await deleteTask(task.id);

    const events = await historyOf(task.id);
    expect(events.find((event) => event.kind === 'task.archived')?.level).toBe(2);
    expect(events.find((event) => event.kind === 'task.deleted')?.level).toBe(3);
  });

  it('logs habit logs, focus sessions, journal writes and restores', async () => {
    await clearAll();
    const habit = await createHabit({
      name: 'کتاب',
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

    await upsertHabitLog(habit.id, '2025-06-20', 'completed', 1);
    await recordFocusSession({
      mode: 'pomodoro',
      durationMinutes: 45,
      durationSeconds: 2700,
      dateKey: '2025-06-20',
      taskId: null,
      taskTitleSnapshot: null,
      completedNaturally: true,
      startedAt: '2025-06-20T09:00:00.000Z',
      endedAt: '2025-06-20T09:45:00.000Z',
    });
    await upsertJournalEntry('2025-06-20', { mood: 4, highlight: 'خط خوب' });
    await clearAllUserData();

    const counts = await eventCountsByKind('2000-01-01');
    expect(counts['habit.logged']).toBe(1);
    expect(counts['focus.recorded']).toBe(1);
    expect(counts['journal.saved']).toBe(1);
    expect(counts['data.cleared']).toBe(1);
  });

  it('never stores the text a person typed into the journal', async () => {
    await clearAll();
    await upsertJournalEntry('2025-06-21', { mood: 5, note: 'یک جملهٔ خصوصی', highlight: 'چیز محرمانه' });

    const raw = JSON.stringify(await db.eventHistory.toArray());
    expect(raw).not.toContain('خصوصی');
    expect(raw).not.toContain('محرمانه');
  });

  it('prunes the oldest rows once the ledger is over its cap', async () => {
    await clearAll();
    const now = Date.now();
    const rows = Array.from({ length: 12 }, (_, index) => ({
      id: `evt-seed-${index}`,
      at: new Date(now - (12 - index) * 1000).toISOString(),
      kind: 'task.created' as const,
      entity: 'task' as const,
      entityId: `t-${index}`,
      source: 'user' as const,
      level: 1 as const,
    }));
    await db.eventHistory.bulkPut(rows);

    const removed = await pruneEvents(8);
    expect(removed).toBe(4);
    expect(await countEvents()).toBe(8);

    const left = await listEvents({ limit: 20 });
    expect(left.some((event) => event.entityId === 't-0')).toBe(false);
    expect(left.some((event) => event.entityId === 't-11')).toBe(true);
  });

  it('filters by entity and kind without losing the newest-first order', async () => {
    await clearAll();
    const a = await createTask({ ...baseTask, title: 'الف' });
    const b = await createTask({ ...baseTask, title: 'ب' });
    await updateTask(a.id, { title: 'الف ۲' });
    await flush();

    const all = await listEvents({ limit: 50 });
    expect(all.length).toBe(3);
    expect(all[0].at >= all[all.length - 1].at).toBe(true);

    const onlyTasks = await listEvents({ entity: 'task', limit: 50 });
    expect(onlyTasks.every((event) => event.entity === 'task')).toBe(true);

    const onlyB = await listEvents({ entityId: b.id });
    expect(onlyB.every((event) => event.entityId === b.id)).toBe(true);
  });
});
