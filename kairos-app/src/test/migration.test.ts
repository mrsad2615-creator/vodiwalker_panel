import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import Dexie from 'dexie';
import { db } from '@/lib/db';

/**
 * Migration proof.
 *
 * The database has three historical schema versions in the wild, so "we added
 * tables" is not an acceptable claim — it is a claim about *other people's data*.
 * This test builds a genuine v3 database with realistic rows, closes it, lets the
 * app open the same database at v4, and then checks that every row survived, that
 * the new tables exist and are empty, and that the upgrade wrote its own record.
 */

const DB_NAME = 'KairosLuxuryProductivityDB';

/** The v1–v3 schema, exactly as shipped. Kept here on purpose: this is history. */
const LEGACY_SCHEMA = {
  tasks: 'id, dueDate, status, priority, quadrant, category, archived, completedDate, createdAt',
  habits: 'id, archived, startDate, createdAt',
  habitLogs: 'id, habitId, dateKey, [habitId+dateKey], status',
  focusSessions: 'id, dateKey, taskId, mode, startedAt',
  goals: 'id, completed, category, deadline',
  settings: 'id',
  activeRun: 'id, updatedAt',
  journal: 'id, dateKey',
} as const;

const now = new Date('2025-06-20T09:00:00.000Z').toISOString();

function legacyTask(index: number) {
  return {
    id: `tsk-legacy-${index}`,
    title: `وظیفهٔ قدیمی ${index}`,
    description: 'متن قبلی',
    dueDate: '2025-06-20',
    dueTime: '10:00',
    priority: 'high',
    quadrant: 'q1',
    category: 'work',
    tags: ['قدیمی'],
    status: 'todo',
    subtasks: [{ id: `sub-${index}`, title: 'زیرکار', completed: false }],
    recurrence: 'none',
    reminderEnabled: true,
    reminderOffsetMin: 15,
    archived: false,
    postponeCount: 2,
    createdAt: now,
    updatedAt: now,
    completedDate: null,
    completedAt: null,
  };
}

const legacyHabit = {
  id: 'hab-legacy-1',
  name: 'کتاب خواندن',
  description: '',
  icon: 'book',
  accentColor: '#b9a2ff',
  scheduleType: 'daily',
  scheduledDays: [0, 1, 2, 3, 4, 5, 6],
  weeklyTarget: 7,
  trackingMode: 'boolean',
  targetValue: 1,
  unit: '',
  reminderTime: '21:00',
  archived: false,
  startDate: '2025-01-01',
  createdAt: now,
  updatedAt: now,
};

let seededCounts: Record<string, number> = {};

beforeAll(async () => {
  // Start from nothing, so the upgrade is measured on a clean v3 database.
  db.close();
  await Dexie.delete(DB_NAME);

  const legacy = new Dexie(DB_NAME);
  legacy.version(3).stores(LEGACY_SCHEMA);
  await legacy.open();

  const tasks = Array.from({ length: 20 }, (_, index) => legacyTask(index));
  const habitLogs = Array.from({ length: 12 }, (_, index) => ({
    id: `hab-legacy-1::2025-06-${String(index + 1).padStart(2, '0')}`,
    habitId: 'hab-legacy-1',
    dateKey: `2025-06-${String(index + 1).padStart(2, '0')}`,
    status: 'completed',
    value: 1,
    updatedAt: now,
  }));
  const focusSessions = Array.from({ length: 6 }, (_, index) => ({
    id: `foc-legacy-${index}`,
    mode: 'pomodoro',
    durationMinutes: 45,
    durationSeconds: 2700,
    dateKey: '2025-06-20',
    taskId: `tsk-legacy-${index}`,
    taskTitleSnapshot: `وظیفهٔ قدیمی ${index}`,
    completedNaturally: true,
    startedAt: now,
    endedAt: now,
  }));
  const goals = [
    {
      id: 'gol-legacy-1',
      title: 'هدف قدیمی',
      description: '',
      category: 'learning',
      currentValue: 3,
      targetValue: 10,
      unit: 'فصل',
      deadline: '2025-12-01',
      completed: false,
      createdAt: now,
      updatedAt: now,
    },
  ];
  const journal = [
    {
      id: 'journal-2025-06-19',
      dateKey: '2025-06-19',
      mood: 4,
      note: 'یادداشت قدیمی',
      highlight: 'خط قدیمی',
      createdAt: now,
      updatedAt: now,
    },
  ];
  const settings = {
    id: 'default',
    accentColor: '#b9a2ff',
    homeMode: 'detailed',
    displayName: 'کاربر قدیمی',
    updatedAt: now,
  };

  await legacy.table('tasks').bulkPut(tasks);
  await legacy.table('habits').put(legacyHabit);
  await legacy.table('habitLogs').bulkPut(habitLogs);
  await legacy.table('focusSessions').bulkPut(focusSessions);
  await legacy.table('goals').bulkPut(goals);
  await legacy.table('journal').bulkPut(journal);
  await legacy.table('settings').put(settings);

  seededCounts = {
    tasks: tasks.length,
    habits: 1,
    habitLogs: habitLogs.length,
    focusSessions: focusSessions.length,
    goals: goals.length,
    journal: journal.length,
    settings: 1,
  };

  legacy.close();

  // The app's own database opens here — which runs the v3 → v4 upgrade.
  await db.open();
});

afterAll(() => {
  db.close();
});

describe('schema v3 → v4 migration', () => {
  it('keeps every row that was already there', async () => {
    expect(await db.tasks.count()).toBe(seededCounts.tasks);
    expect(await db.habits.count()).toBe(seededCounts.habits);
    expect(await db.habitLogs.count()).toBe(seededCounts.habitLogs);
    expect(await db.focusSessions.count()).toBe(seededCounts.focusSessions);
    expect(await db.goals.count()).toBe(seededCounts.goals);
    expect(await db.journal.count()).toBe(seededCounts.journal);
    expect(await db.settings.count()).toBe(seededCounts.settings);
  });

  it('keeps the actual content, not just the count', async () => {
    const task = await db.tasks.get('tsk-legacy-7');
    expect(task).toBeDefined();
    expect(task?.title).toBe('وظیفهٔ قدیمی 7');
    expect(task?.subtasks).toHaveLength(1);
    expect(task?.postponeCount).toBe(2);
    expect(task?.tags).toEqual(['قدیمی']);

    const journal = await db.journal.get('journal-2025-06-19');
    expect(journal?.note).toBe('یادداشت قدیمی');

    const settings = await db.settings.get('default');
    expect(settings?.homeMode).toBe('detailed');
    expect(settings?.displayName).toBe('کاربر قدیمی');
  });

  it('creates the four new tables, empty', async () => {
    expect(await db.eventHistory.count()).toBe(0);
    expect(await db.undoHistory.count()).toBe(0);
    expect(await db.permissionGrants.count()).toBe(0);
  });

  it('leaves one honest record per upgrade step', async () => {
    const logs = (await db.migrationLog.toArray()).sort((a, b) => a.at.localeCompare(b.at));
    // v3 → v4 (new ledger tables) and v4 → v5 (projects + task fields).
    expect(logs.map((log) => `${log.from}->${log.to}`)).toEqual(['3->4', '4->5']);
    expect(logs.every((log) => log.ok)).toBe(true);

    const first = logs[0];
    expect(first.counts.tasks).toBe(seededCounts.tasks);
    expect(first.counts.habitLogs).toBe(seededCounts.habitLogs);

    // v5 is the version that rewrites rows, so it must carry a snapshot.
    const second = logs[1];
    expect(second.snapshot?.tasks).toHaveLength(seededCounts.tasks);
    expect(second.snapshot?.goals).toHaveLength(seededCounts.goals);
    expect(new Date(second.at).getTime()).toBeGreaterThan(0);
  });

  it('is usable immediately after the upgrade', async () => {
    const { createTask } = await import('@/lib/db');
    const created = await createTask({
      title: 'پس از مهاجرت',
      description: '',
      dueDate: null,
      dueTime: null,
      priority: 'medium',
      quadrant: 'q2',
      category: 'work',
      tags: [],
      status: 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
    });

    expect(created.id).toBeTruthy();
    // Old rows are still readable next to the new one.
    expect(await db.tasks.count()).toBe(seededCounts.tasks + 1);
    const events = await db.eventHistory.toArray();
    expect(events.some((event) => event.entityId === created.id)).toBe(true);
  });

  it('does not run the upgrade a second time', async () => {
    db.close();
    await db.open();
    expect(await db.migrationLog.count()).toBe(2);
    expect(await db.tasks.count()).toBeGreaterThan(seededCounts.tasks);
  });
});
