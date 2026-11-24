import { createTask, db } from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import type { IcsEvent } from '@/lib/ics';
import type { Task } from '@/types/domain';

/**
 * Turning calendar events into tasks.
 *
 * Rules that make the result feel imported *properly* rather than dumped:
 *   · an event with a time becomes a task with that time, so it lands in the
 *     right column of the day;
 *   · an all-day event becomes an unscheduled-hour task on that day;
 *   · anything already present (same title on the same day, same time) is skipped,
 *     so importing the same file twice changes nothing;
 *   · the past is left alone: only days from yesterday onward come in.
 *     (Yesterday is included because timezones and late-night imports exist.)
 */

const MAX_EVENTS = 250;

export interface ImportCandidate {
  event: IcsEvent;
  duplicate: boolean;
}

export function planCalendarImport(
  events: IcsEvent[],
  existingTasks: Task[],
  todayKey: string = getTodayKey()
): { toCreate: ImportCandidate[]; duplicates: number; skippedPast: number; truncated: number } {
  const from = addDaysKey(todayKey, -1);

  const seen = new Set(
    existingTasks
      .filter((task) => !task.archived)
      .map((task) => signature(task.title, task.dueDate, task.dueTime))
  );

  let skippedPast = 0;
  const candidates: ImportCandidate[] = [];

  for (const event of events) {
    if (event.dateKey < from) {
      skippedPast++;
      continue;
    }
    const key = signature(event.title, event.dateKey, event.startTime);
    if (seen.has(key)) {
      candidates.push({ event, duplicate: true });
      continue;
    }
    seen.add(key);
    candidates.push({ event, duplicate: false });
  }

  const unique = candidates.filter((candidate) => !candidate.duplicate);
  const toCreate = unique.slice(0, MAX_EVENTS);

  return {
    toCreate,
    duplicates: candidates.length - unique.length,
    skippedPast,
    truncated: Math.max(0, unique.length - toCreate.length),
  };
}

function addDaysKey(dateKey: string, delta: number): string {
  const date = new Date(`${dateKey}T00:00:00`);
  date.setDate(date.getDate() + delta);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function signature(title: string, dateKey: string | null, time: string | null): string {
  return `${dateKey ?? 'none'}|${time ?? 'all-day'}|${title.trim()}`;
}

export async function importCalendarEvents(
  events: IcsEvent[],
  existingTasks: Task[],
  todayKey: string = getTodayKey()
) {
  // Read inside the same write transaction as the inserts. The caller's UI
  // snapshot can lag behind IndexedDB, and another import may be in flight.
  return db.transaction('rw', [db.tasks, db.eventHistory], async () => {
    const storedTasks = await db.tasks.toArray();
    const plan = planCalendarImport(events, [...existingTasks, ...storedTasks], todayKey);

    for (const { event } of plan.toCreate) {
      await createTask({
        title: event.title,
        description: [event.location, event.description].filter(Boolean).join('\n'),
        dueDate: event.dateKey,
        dueTime: event.startTime,
        priority: 'medium',
        quadrant: 'q2',
        category: 'personal',
        tags: ['تقویم'],
        status: 'todo',
        subtasks: [],
        recurrence: 'none',
        reminderEnabled: false,
        reminderOffsetMin: 0,
        postponeCount: 0,
        completedDate: null,
        completedAt: null,
      } as never);
    }

    return plan;
  });
}
