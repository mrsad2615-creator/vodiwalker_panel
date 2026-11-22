import { describe, expect, it } from 'vitest';
import {
  INSIGHT_MIN,
  bestFocusWindow,
  categoryFocusEstimates,
  habitsAtRisk,
  moodFocusLink,
  nextDataStep,
  pickInsights,
  postponeRisks,
  stalledGoals,
} from '@/lib/insights';
import { computeGoalProgress, goalProgressCaption } from '@/lib/goals';
import type { FocusSession, Goal, Habit, HabitLog, JournalEntry, Task } from '@/types/domain';

/**
 * Local intelligence has one rule: never speak without a sample. These tests pin
 * the sample gates (a pattern that is not there must return nothing) and the
 * arithmetic that turns rows into a sentence.
 */

const TODAY = '2025-06-20';

function session(overrides: Partial<FocusSession> & { startedAt: string; dateKey: string }): FocusSession {
  return {
    id: `s-${overrides.startedAt}-${Math.random().toString(36).slice(2, 7)}`,
    mode: 'pomodoro',
    durationMinutes: 45,
    durationSeconds: 45 * 60,
    taskId: null,
    taskTitleSnapshot: null,
    completedNaturally: true,
    endedAt: overrides.startedAt,
    ...overrides,
  } as FocusSession;
}

/** Local-time ISO stamp, so hour buckets do not depend on the runner's zone math. */
const at = (day: number, hour: number) =>
  new Date(2025, 5, day, hour, 15, 0).toISOString();

function task(overrides: Partial<Task>): Task {
  return {
    id: `t-${Math.random().toString(36).slice(2, 8)}`,
    title: 'کار',
    description: '',
    dueDate: null,
    dueTime: null,
    priority: 'medium',
    quadrant: 'q2',
    category: 'work',
    tags: [],
    status: 'todo',
    subtasks: [],
    recurrence: { type: 'none' },
    goalId: null,
    reminderEnabled: false,
    reminderOffsetMin: 0,
    archived: false,
    postponeCount: 0,
    createdAt: '2025-06-01T00:00:00.000Z',
    updatedAt: '2025-06-01T00:00:00.000Z',
    completedDate: null,
    completedAt: null,
    ...overrides,
  } as Task;
}

function habit(overrides: Partial<Habit>): Habit {
  return {
    id: `h-${Math.random().toString(36).slice(2, 8)}`,
    name: 'عادت',
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
    archived: false,
    startDate: '2025-01-01',
    createdAt: '2025-01-01T00:00:00.000Z',
    updatedAt: '2025-01-01T00:00:00.000Z',
    ...overrides,
  } as Habit;
}

describe('bestFocusWindow', () => {
  it('stays silent below the session floor', () => {
    const sessions = [
      session({ startedAt: at(14, 9), dateKey: '2025-06-14' }),
      session({ startedAt: at(15, 9), dateKey: '2025-06-15' }),
      session({ startedAt: at(16, 9), dateKey: '2025-06-16' }),
    ];
    expect(bestFocusWindow(sessions)).toBeNull();
  });

  it('finds the two-hour block where the minutes actually are', () => {
    const sessions = [
      session({ startedAt: at(14, 9), dateKey: '2025-06-14' }),
      session({ startedAt: at(15, 9), dateKey: '2025-06-15' }),
      session({ startedAt: at(16, 9), dateKey: '2025-06-16' }),
      session({ startedAt: at(17, 10), dateKey: '2025-06-17' }),
      session({ startedAt: at(18, 9), dateKey: '2025-06-18' }),
      // Evening noise, much thinner.
      session({ startedAt: at(18, 21), dateKey: '2025-06-18', durationMinutes: 5, durationSeconds: 300 }),
    ];

    const window = bestFocusWindow(sessions);
    expect(window).not.toBeNull();
    expect(window!.startHour).toBe(9);
    expect(window!.endHour).toBe(11);
    expect(window!.sessions).toBeGreaterThanOrEqual(INSIGHT_MIN.windowSessions);
    expect(window!.days).toBeGreaterThanOrEqual(INSIGHT_MIN.windowDays);
  });
});

describe('categoryFocusEstimates', () => {
  it('needs three sessions before it quotes a median', () => {
    const target = task({ id: 't1', category: 'learning' });
    const two = [
      session({ startedAt: at(10, 9), dateKey: '2025-06-10', taskId: 't1', durationMinutes: 30 }),
      session({ startedAt: at(11, 9), dateKey: '2025-06-11', taskId: 't1', durationMinutes: 40 }),
    ];
    expect(categoryFocusEstimates(two, [target]).size).toBe(0);

    const three = [...two, session({ startedAt: at(12, 9), dateKey: '2025-06-12', taskId: 't1', durationMinutes: 90 })];
    const estimates = categoryFocusEstimates(three, [target]);
    expect(estimates.get('learning')?.minutes).toBe(40);
    expect(estimates.get('learning')?.samples).toBe(3);
  });

  it('ignores sessions that are not tied to a task', () => {
    const sessions = [
      session({ startedAt: at(10, 9), dateKey: '2025-06-10' }),
      session({ startedAt: at(11, 9), dateKey: '2025-06-11' }),
      session({ startedAt: at(12, 9), dateKey: '2025-06-12' }),
    ];
    expect(categoryFocusEstimates(sessions, [task({ id: 't1' })]).size).toBe(0);
  });
});

describe('habitsAtRisk', () => {
  const logs = (habitId: string, keys: string[]): HabitLog[] =>
    keys.map((dateKey) => ({
      id: `${habitId}-${dateKey}`,
      habitId,
      dateKey,
      status: 'completed',
      value: 1,
    })) as HabitLog[];

  it('flags a habit that was strong last week and thin this week', () => {
    const h = habit({ id: 'h1', name: 'کتاب' });
    const before = ['2025-06-07', '2025-06-08', '2025-06-09', '2025-06-10', '2025-06-11'];
    const now = ['2025-06-15'];

    const risk = habitsAtRisk({ habits: [h], habitLogs: logs('h1', [...before, ...now]), todayKey: TODAY });
    expect(risk).toHaveLength(1);
    expect(risk[0].habit.id).toBe('h1');
    expect(risk[0].before).toBeGreaterThan(60);
    expect(risk[0].now).toBeLessThanOrEqual(40);
  });

  it('does not shame a habit that was never strong', () => {
    const h = habit({ id: 'h2', name: 'ورزش' });
    const risk = habitsAtRisk({
      habits: [h],
      habitLogs: logs('h2', ['2025-06-14']),
      todayKey: TODAY,
    });
    expect(risk).toHaveLength(0);
  });
});

describe('moodFocusLink', () => {
  const entry = (dateKey: string, mood: number): JournalEntry =>
    ({
      id: `journal-${dateKey}`,
      dateKey,
      mood,
      note: '',
      highlight: '',
      createdAt: `${dateKey}T20:00:00.000Z`,
      updatedAt: `${dateKey}T20:00:00.000Z`,
    }) as JournalEntry;

  it('refuses to speak on too few paired days', () => {
    const journal = [entry('2025-06-14', 5), entry('2025-06-15', 2)];
    const sessions = [
      session({ startedAt: at(14, 9), dateKey: '2025-06-14', durationMinutes: 90 }),
      session({ startedAt: at(15, 9), dateKey: '2025-06-15', durationMinutes: 20 }),
    ];
    expect(moodFocusLink({ journal, sessions })).toBeNull();
  });

  it('quantifies the lift once the days are there', () => {
    const journal: JournalEntry[] = [];
    const sessions: FocusSession[] = [];
    for (let i = 0; i < 5; i++) {
      const key = `2025-06-${String(10 + i).padStart(2, '0')}`;
      journal.push(entry(key, 5));
      sessions.push(session({ startedAt: at(10 + i, 9), dateKey: key, durationMinutes: 90 }));
    }
    for (let i = 0; i < 5; i++) {
      const key = `2025-06-${String(15 + i).padStart(2, '0')}`;
      journal.push(entry(key, 2));
      sessions.push(session({ startedAt: at(15 + i, 9), dateKey: key, durationMinutes: 30 }));
    }

    const link = moodFocusLink({ journal, sessions });
    expect(link).not.toBeNull();
    expect(link!.highAvg).toBe(90);
    expect(link!.lowAvg).toBe(30);
    expect(link!.liftPercent).toBe(200);
  });
});

describe('postponeRisks and stalledGoals', () => {
  it('lists only open tasks that were pushed twice or more', () => {
    const risky = task({ id: 'a', title: 'گزارش', postponeCount: 3, dueDate: '2025-06-18' });
    const once = task({ id: 'b', postponeCount: 1, dueDate: '2025-06-18' });
    const done = task({ id: 'c', postponeCount: 4, status: 'completed' });

    const list = postponeRisks([risky, once, done], TODAY);
    expect(list.map((t) => t.id)).toEqual(['a']);
  });

  it('reports a goal whose linked work has not moved for a week', () => {
    const goal = { id: 'g1', title: 'کتاب نوشتن', completed: false };
    const tasks = [
      task({ id: 'a', goalId: 'g1', status: 'completed', updatedAt: '2025-06-01T10:00:00.000Z' }),
      task({ id: 'b', goalId: 'g1', status: 'todo', updatedAt: '2025-06-02T10:00:00.000Z' }),
    ];
    const stalled = stalledGoals({ goals: [goal], tasks, todayKey: TODAY });
    expect(stalled).toHaveLength(1);
    expect(stalled[0]).toMatchObject({ goalId: 'g1', done: 1, total: 2 });
    expect(stalled[0].stalledDays).toBeGreaterThanOrEqual(7);
  });
});

describe('pickInsights', () => {
  const base = {
    sessions: [] as FocusSession[],
    tasks: [] as Task[],
    habits: [] as Habit[],
    habitLogs: [] as HabitLog[],
    journal: [] as JournalEntry[],
    goals: [] as { id: string; title: string; completed: boolean }[],
    todayKey: TODAY,
  };

  it('returns nothing when nothing is supported, and names the next data step', () => {
    expect(pickInsights(base)).toHaveLength(0);
    expect(nextDataStep(base)).toContain('جلسهٔ تمرکز');
  });

  it('ranks a repeated postponement above a focus window', () => {
    const sessions = [
      session({ startedAt: at(14, 9), dateKey: '2025-06-14' }),
      session({ startedAt: at(15, 9), dateKey: '2025-06-15' }),
      session({ startedAt: at(16, 9), dateKey: '2025-06-16' }),
      session({ startedAt: at(17, 9), dateKey: '2025-06-17' }),
      session({ startedAt: at(18, 9), dateKey: '2025-06-18' }),
    ];
    const tasks = [task({ id: 'a', title: 'مالیات', postponeCount: 4, dueDate: '2025-06-10' })];

    const insights = pickInsights({ ...base, sessions, tasks });
    expect(insights[0].id).toBe('postpone');
    expect(insights[0].title).toContain('مالیات');
    expect(insights.some((insight) => insight.id === 'focus-window')).toBe(true);
  });
});

describe('goal progress', () => {
  const goal: Goal = {
    id: 'g1',
    title: 'آماده‌سازی ارائه',
    description: '',
    category: 'work',
    currentValue: 2,
    targetValue: 10,
    unit: 'صفحه',
    deadline: null,
    completed: false,
    createdAt: '2025-06-01T00:00:00.000Z',
    updatedAt: '2025-06-01T00:00:00.000Z',
  } as Goal;

  it('is earned from linked tasks when any exist', () => {
    const tasks = [
      task({ goalId: 'g1', status: 'completed' }),
      task({ goalId: 'g1', status: 'todo' }),
      task({ goalId: null, status: 'completed' }),
    ];
    const progress = computeGoalProgress(goal, tasks);
    expect(progress.mode).toBe('linked');
    expect(progress.percent).toBe(50);
    expect(goalProgressCaption(goal, progress)).toBe('1 از 2 کار');
  });

  it('falls back to the manual number, and clamps it', () => {
    const progress = computeGoalProgress(goal, []);
    expect(progress.mode).toBe('manual');
    expect(progress.percent).toBe(20);

    const over = computeGoalProgress({ ...goal, currentValue: 40 }, []);
    expect(over.percent).toBe(100);
  });

  it('ignores archived tasks so an archive cannot fake progress', () => {
    const tasks = [
      task({ goalId: 'g1', status: 'completed' }),
      task({ goalId: 'g1', status: 'todo', archived: true }),
    ];
    expect(computeGoalProgress(goal, tasks).total).toBe(1);
    expect(computeGoalProgress(goal, tasks).percent).toBe(100);
  });
});
