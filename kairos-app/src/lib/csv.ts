import type { FocusSession, Habit, HabitLog, Task } from '@/types/domain';

/**
 * CSV, because the data is yours.
 *
 * A backup is for coming back; a CSV is for leaving — Excel, Numbers, Google
 * Sheets, a spreadsheet someone else can read. So the export is deliberately
 * tabular, one row per thing, with a header row in plain English column names
 * (Persian content, stable keys) and BOM-prefixed so Excel opens the Persian
 * correctly instead of mojibake.
 */

const BOM = '\uFEFF';

function cell(value: unknown): string {
  const text = value === null || value === undefined ? '' : String(value);
  if (/[",\n\r]/.test(text)) return `"${text.replace(/"/g, '""')}"`;
  return text;
}

function toCsv(rows: unknown[][]): string {
  return BOM + rows.map((row) => row.map(cell).join(',')).join('\r\n') + '\r\n';
}

export function tasksToCsv(tasks: Task[]): string {
  const rows: unknown[][] = [
    [
      'id',
      'title',
      'status',
      'due_date',
      'due_time',
      'priority',
      'quadrant',
      'category',
      'tags',
      'completed_date',
      'postpone_count',
      'created_at',
    ],
  ];

  for (const task of tasks) {
    rows.push([
      task.id,
      task.title,
      task.status,
      task.dueDate ?? '',
      task.dueTime ?? '',
      task.priority,
      task.quadrant,
      task.category,
      task.tags.join(' | '),
      task.completedDate ?? '',
      task.postponeCount,
      task.createdAt,
    ]);
  }

  return toCsv(rows);
}

export function habitLogsToCsv(habits: Habit[], logs: HabitLog[]): string {
  const byId = new Map(habits.map((habit) => [habit.id, habit]));
  const rows: unknown[][] = [['date', 'habit', 'status', 'value', 'note']];

  for (const log of [...logs].sort((a, b) => a.dateKey.localeCompare(b.dateKey))) {
    rows.push([
      log.dateKey,
      byId.get(log.habitId)?.name ?? log.habitId,
      log.status,
      log.value,
      log.note ?? '',
    ]);
  }

  return toCsv(rows);
}

export function focusSessionsToCsv(sessions: FocusSession[]): string {
  const rows: unknown[][] = [['date', 'mode', 'minutes', 'task_id', 'started_at']];
  for (const session of [...sessions].sort((a, b) => a.startedAt.localeCompare(b.startedAt))) {
    rows.push([
      session.dateKey,
      session.mode,
      session.durationMinutes,
      session.taskId ?? '',
      session.startedAt,
    ]);
  }
  return toCsv(rows);
}

/** One file with everything, for the person who wants to *look* at their data. */
export function buildExportCsvBundle(input: {
  tasks: Task[];
  habits: Habit[];
  habitLogs: HabitLog[];
  focusSessions: FocusSession[];
}): { tasks: string; habits: string; focus: string } {
  return {
    tasks: tasksToCsv(input.tasks),
    habits: habitLogsToCsv(input.habits, input.habitLogs),
    focus: focusSessionsToCsv(input.focusSessions),
  };
}
