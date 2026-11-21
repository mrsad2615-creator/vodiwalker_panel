import type { FocusSession, Habit, HabitLog, Task } from '@/types/domain';
import { addDays } from './date';

/**
 * Home metrics.
 *
 * The Home board is built from these four pure functions and nothing else, so
 * every ring, bar, matrix and trend on the first screen is arithmetic over the
 * user's own rows — there is no sample value, no placeholder curve and no
 * "illustrative" number anywhere in the UI.
 */

export interface DailyPoint {
  dateKey: string;
  focusMinutes: number;
  tasksDone: number;
  habitsDone: number;
  /** Focus minutes + completed tasks + completed habits — the activity index. */
  activity: number;
}

export interface HomeInput {
  focusSessions: FocusSession[];
  tasks: Task[];
  habitLogs: HabitLog[];
}

/** A dense, gap-free series ending at `endKey` (oldest first). */
export function buildDailySeries(input: HomeInput, endKey: string, days = 14): DailyPoint[] {
  const byDate = new Map<string, DailyPoint>();

  for (let i = days - 1; i >= 0; i -= 1) {
    const key = addDays(endKey, -i);
    byDate.set(key, { dateKey: key, focusMinutes: 0, tasksDone: 0, habitsDone: 0, activity: 0 });
  }

  for (const session of input.focusSessions) {
    const point = byDate.get(session.dateKey);
    if (point) point.focusMinutes += session.durationMinutes;
  }

  for (const task of input.tasks) {
    if (task.status !== 'completed') continue;
    const key = task.completedDate ?? task.dueDate;
    const point = key ? byDate.get(key) : undefined;
    if (point) point.tasksDone += 1;
  }

  for (const log of input.habitLogs) {
    if (log.status !== 'completed') continue;
    const point = byDate.get(log.dateKey);
    if (point) point.habitsDone += 1;
  }

  const series = [...byDate.values()];
  for (const point of series) {
    point.activity = Math.round(point.focusMinutes + point.tasksDone * 10 + point.habitsDone * 5);
  }
  return series;
}

export interface WeekComparison {
  focusMinutes: number;
  tasksDone: number;
  habitsDone: number;
  /** Percentage change of the composite activity vs the previous window, or null. */
  deltaPercent: number | null;
}

/** Compares the last `window` days with the `window` days before them. */
export function compareWindows(series: DailyPoint[], window = 7): WeekComparison {
  const sum = (points: DailyPoint[]) =>
    points.reduce(
      (acc, p) => ({
        focusMinutes: acc.focusMinutes + p.focusMinutes,
        tasksDone: acc.tasksDone + p.tasksDone,
        habitsDone: acc.habitsDone + p.habitsDone,
        activity: acc.activity + p.activity,
      }),
      { focusMinutes: 0, tasksDone: 0, habitsDone: 0, activity: 0 }
    );

  const current = sum(series.slice(-window));
  const previous = sum(series.slice(-window * 2, -window));

  const deltaPercent =
    previous.activity === 0
      ? current.activity > 0
        ? 100
        : null
      : Math.round(((current.activity - previous.activity) / previous.activity) * 100);

  return {
    focusMinutes: current.focusMinutes,
    tasksDone: current.tasksDone,
    habitsDone: current.habitsDone,
    deltaPercent,
  };
}

export interface TaskBreakdown {
  todo: number;
  inProgress: number;
  completed: number;
  total: number;
  /** Completion ratio 0–100 over non-archived tasks. */
  completionPercent: number;
}

export function taskBreakdown(tasks: Task[]): TaskBreakdown {
  const active = tasks.filter((t) => !t.archived);
  const completed = active.filter((t) => t.status === 'completed').length;
  const inProgress = active.filter((t) => t.status === 'in_progress').length;
  const todo = active.length - completed - inProgress;
  return {
    todo,
    inProgress,
    completed,
    total: active.length,
    completionPercent: active.length ? Math.round((completed / active.length) * 100) : 0,
  };
}

export type HabitCellStatus = 'done' | 'partial' | 'missed' | 'empty';

export interface HabitMatrix {
  days: string[];
  rows: { habitId: string; name: string; icon: Habit['icon']; accentColor: string; cells: HabitCellStatus[] }[];
  /** Longest current streak across the visible habits. */
  bestStreak: number;
  /** Completed cells ÷ (habits × days), 0–100. */
  density: number;
}

/**
 * A 7-day × habit matrix for the Home board. Days are the *last seven* calendar
 * days ending at `endKey`, so the newest column is today.
 */
export function habitWeekMatrix(
  habits: Habit[],
  logs: HabitLog[],
  endKey: string,
  days = 7
): HabitMatrix {
  const keys = Array.from({ length: days }, (_, i) => addDays(endKey, -(days - 1 - i)));
  const active = habits.filter((h) => !h.archived);

  const logIndex = new Map<string, HabitLog>();
  for (const log of logs) logIndex.set(`${log.habitId}::${log.dateKey}`, log);

  const rows = active.map((habit) => {
    const cells = keys.map((key): HabitCellStatus => {
      const log = logIndex.get(`${habit.id}::${key}`);
      if (!log) return 'empty';
      if (log.status === 'completed') return 'done';
      if (log.status === 'partial') return 'partial';
      return 'missed';
    });
    return {
      habitId: habit.id,
      name: habit.name,
      icon: habit.icon,
      accentColor: habit.accentColor,
      cells,
    };
  });

  const done = rows.reduce((acc, row) => acc + row.cells.filter((c) => c === 'done').length, 0);
  const slots = rows.length * days;

  // Best current streak: walk back from today while the cell is done.
  let bestStreak = 0;
  for (const row of rows) {
    let streak = 0;
    for (let i = row.cells.length - 1; i >= 0; i -= 1) {
      if (row.cells[i] === 'done') streak += 1;
      else break;
    }
    bestStreak = Math.max(bestStreak, streak);
  }

  return {
    days: keys,
    rows,
    bestStreak,
    density: slots ? Math.round((done / slots) * 100) : 0,
  };
}

/** The next `days` scheduled, uncompleted tasks, grouped by day. */
export function upcomingByDay(tasks: Task[], todayKey: string, days = 5) {
  const keys = Array.from({ length: days }, (_, i) => addDays(todayKey, i));
  return keys.map((key) => ({
    dateKey: key,
    tasks: tasks
      .filter((t) => !t.archived && t.status !== 'completed' && t.dueDate === key)
      .sort((a, b) => (a.dueTime ?? '99:99').localeCompare(b.dueTime ?? '99:99')),
  }));
}
