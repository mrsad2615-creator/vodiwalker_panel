import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearAllUserData,
  clearHabitLog,
  createHabit,
  createTask,
  db,
  exportBackup,
  importBackup,
  recordFocusSession,
  setTaskStatus,
  updateTask,
  upsertHabitLog,
} from '@/lib/db';

describe('IndexedDB Persistence, Duplicate Prevention & Backup/Restore', () => {
  beforeEach(async () => {
    await clearAllUserData();
  });

  it('prevents duplicate recurring task generation and tracks postponements accurately', async () => {
    const task = await createTask({
      title: 'مرور صبحگاهی',
      description: '',
      dueDate: '2026-11-16',
      dueTime: '08:00',
      priority: 'high',
      quadrant: 'q1',
      category: 'personal',
      tags: ['صبح'],
      status: 'todo',
      subtasks: [],
      recurrence: 'daily',
      reminderEnabled: false,
      reminderOffsetMin: 0,
    });

    // Postpone forward by 1 day -> postponeCount increments
    const moved = await updateTask(task.id, { dueDate: '2026-11-17' });
    expect(moved?.postponeCount).toBe(1);

    // Complete recurring task -> spawns next occurrence (2026-11-18)
    const firstComplete = await setTaskStatus(task.id, 'completed');
    expect(firstComplete.spawnedNext?.dueDate).toBe('2026-11-18');

    // Toggle back to todo and complete again -> must NOT duplicate 2026-11-18!
    await setTaskStatus(task.id, 'todo');
    const secondComplete = await setTaskStatus(task.id, 'completed');
    expect(secondComplete.spawnedNext).toBeUndefined();

    const allTasks = await db.tasks.toArray();
    expect(allTasks).toHaveLength(2);
  });

  it('prevents duplicate habit logs via composite key and allows returning to unlogged', async () => {
    const habit = await createHabit({
      name: 'نوشیدن آب',
      description: '',
      icon: 'droplets',
      accentColor: '#6E56CF',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'quantitative',
      targetValue: 8,
      unit: 'لیوان',
      reminderTime: null,
      startDate: '2026-11-16',
    });

    await upsertHabitLog(habit.id, '2026-11-16', 'partial', 4);
    await upsertHabitLog(habit.id, '2026-11-16', 'completed', 8);

    const logs = await db.habitLogs.where('habitId').equals(habit.id).toArray();
    expect(logs).toHaveLength(1);
    expect(logs[0].status).toBe('completed');
    expect(logs[0].value).toBe(8);

    await clearHabitLog(habit.id, '2026-11-16');
    expect(await db.habitLogs.where('habitId').equals(habit.id).count()).toBe(0);
  });

  it('exports full JSON backup and cleanly restores after a complete wipe', async () => {
    await createTask({
      title: 'وظیفه بکاپ',
      description: 'تست پشتیبان',
      dueDate: '2026-11-16',
      dueTime: null,
      priority: 'medium',
      quadrant: 'q2',
      category: 'work',
      tags: ['تست'],
      status: 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
    });
    await recordFocusSession({
      mode: 'pomodoro',
      durationMinutes: 25,
      durationSeconds: 1500,
      dateKey: '2026-11-16',
      taskId: null,
      taskTitleSnapshot: null,
      completedNaturally: true,
      startedAt: new Date().toISOString(),
      endedAt: new Date().toISOString(),
    });

    const backup = await exportBackup();
    expect(backup.app).toBe('KAIROS');
    expect(backup.tasks).toHaveLength(1);
    expect(backup.focusSessions).toHaveLength(1);

    await clearAllUserData();
    expect(await db.tasks.count()).toBe(0);
    expect(await db.focusSessions.count()).toBe(0);

    await importBackup(backup);
    expect(await db.tasks.count()).toBe(1);
    expect(await db.focusSessions.count()).toBe(1);
    expect((await db.tasks.toArray())[0].title).toBe('وظیفه بکاپ');
  });
});
