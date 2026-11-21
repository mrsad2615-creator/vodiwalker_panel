import { describe, expect, it } from 'vitest';
import {
  buildDailySeries,
  compareWindows,
  habitWeekMatrix,
  taskBreakdown,
  upcomingByDay,
} from '@/lib/homeMetrics';
import type { FocusSession, Habit, HabitLog, Task } from '@/types/domain';

const TODAY = '2025-06-10';

const session = (dateKey: string, minutes: number) =>
  ({ id: `${dateKey}-${minutes}`, dateKey, durationMinutes: minutes }) as FocusSession;

const task = (partial: Partial<Task>) => ({ archived: false, status: 'todo', ...partial }) as Task;

const log = (habitId: string, dateKey: string, status: HabitLog['status']) =>
  ({ id: `${habitId}::${dateKey}`, habitId, dateKey, status, value: status === 'completed' ? 1 : 0 }) as HabitLog;

const habit = (id: string, name: string) =>
  ({ id, name, icon: 'book', accentColor: '#fff', archived: false }) as unknown as Habit;

describe('buildDailySeries', () => {
  it('returns a dense window ending today, oldest first', () => {
    const series = buildDailySeries(
      { focusSessions: [session(TODAY, 30)], tasks: [], habitLogs: [] },
      TODAY,
      7
    );
    expect(series).toHaveLength(7);
    expect(series[6].dateKey).toBe(TODAY);
    expect(series[6].focusMinutes).toBe(30);
    expect(series[0].focusMinutes).toBe(0);
  });

  it('ignores rows outside the window and non-completed logs', () => {
    const series = buildDailySeries(
      {
        focusSessions: [session('2020-01-01', 90), session(TODAY, 15)],
        tasks: [task({ status: 'completed', completedDate: '2020-01-01' })],
        habitLogs: [log('h1', TODAY, 'partial')],
      },
      TODAY,
      7
    );
    expect(series.reduce((a, p) => a + p.focusMinutes, 0)).toBe(15);
    expect(series.reduce((a, p) => a + p.tasksDone, 0)).toBe(0);
    expect(series.reduce((a, p) => a + p.habitsDone, 0)).toBe(0);
  });

  it('counts completed tasks on their completion date', () => {
    const series = buildDailySeries(
      { focusSessions: [], tasks: [task({ status: 'completed', completedDate: TODAY })], habitLogs: [] },
      TODAY,
      7
    );
    expect(series[6].tasksDone).toBe(1);
  });
});

describe('compareWindows', () => {
  it('reports null when there is no history at all', () => {
    const series = buildDailySeries({ focusSessions: [], tasks: [], habitLogs: [] }, TODAY, 14);
    expect(compareWindows(series, 7).deltaPercent).toBeNull();
  });

  it('measures growth against the previous week', () => {
    // 14-day window: put 60 min in the previous week and 120 in this week.
    const sessions = [session('2025-06-03', 60), session('2025-06-09', 120)];
    const series = buildDailySeries({ focusSessions: sessions, tasks: [], habitLogs: [] }, TODAY, 14);
    const comparison = compareWindows(series, 7);

    expect(comparison.focusMinutes).toBe(120);
    expect(comparison.deltaPercent).toBe(100);
  });
});

describe('taskBreakdown', () => {
  it('separates statuses and ignores archived tasks', () => {
    const breakdown = taskBreakdown([
      task({ status: 'completed' }),
      task({ status: 'completed' }),
      task({ status: 'in_progress' }),
      task({ status: 'todo' }),
      task({ archived: true, status: 'todo' }),
    ]);

    expect(breakdown).toMatchObject({ todo: 1, inProgress: 1, completed: 2, total: 4 });
    expect(breakdown.completionPercent).toBe(50);
  });

  it('is safe on an empty board', () => {
    expect(taskBreakdown([]).completionPercent).toBe(0);
  });
});

describe('habitWeekMatrix', () => {
  it('maps the last seven days and computes density + streak', () => {
    const habits = [habit('h1', 'کتاب'), habit('h2', 'ورزش')];
    const logs = [
      log('h1', '2025-06-08', 'completed'),
      log('h1', '2025-06-09', 'completed'),
      log('h1', TODAY, 'completed'),
      log('h2', TODAY, 'partial'),
      log('h2', '2025-06-09', 'missed'),
    ];
    const matrix = habitWeekMatrix(habits, logs, TODAY, 7);

    expect(matrix.days).toHaveLength(7);
    expect(matrix.days[6]).toBe(TODAY);
    expect(matrix.rows[0].cells.slice(-3)).toEqual(['done', 'done', 'done']);
    expect(matrix.rows[1].cells[6]).toBe('partial');
    expect(matrix.bestStreak).toBe(3);
    expect(matrix.density).toBe(Math.round((3 / 14) * 100));
  });

  it('survives having no habits', () => {
    const matrix = habitWeekMatrix([], [], TODAY, 7);
    expect(matrix.rows).toHaveLength(0);
    expect(matrix.density).toBe(0);
  });
});

describe('upcomingByDay', () => {
  it('groups only unfinished scheduled tasks, sorted by time', () => {
    const days = upcomingByDay(
      [
        task({ id: 'a', dueDate: TODAY, dueTime: '18:00' }),
        task({ id: 'b', dueDate: TODAY, dueTime: '09:00' }),
        task({ id: 'c', dueDate: TODAY, status: 'completed' }),
        task({ id: 'd', dueDate: '2025-06-30' }),
      ],
      TODAY,
      3
    );

    expect(days).toHaveLength(3);
    expect(days[0].tasks.map((t) => t.id)).toEqual(['b', 'a']);
    expect(days[1].tasks).toHaveLength(0);
  });
});
