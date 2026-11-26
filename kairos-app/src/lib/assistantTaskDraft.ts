import { db } from './db';
import { getTodayKey } from './date';
import { createId } from './format';
import { normalizeCommand } from './commands';
import type { KairosEvent } from './events';
import type { Task } from '@/types/domain';

/** Only the explicitly supported request is intercepted; never guess titles. */
export function wantsTodayTaskList(text: string): boolean {
  const q = normalizeCommand(text);
  return /امروز/.test(q) && !/فردا/.test(q) && /(وظیفه|وظایف|کارها|کارهای|لیست کار|فهرست کار)/.test(q)
    && /(بساز|ایجاد کن|درست کن|اضافه کن|ثبت کن|بنویس)/.test(q);
}

export function parseTaskLines(raw: string): string[] {
  return raw.split(/\r?\n/).map((line) => line.replace(/^\s*(?:[-*•]\s+|[0-9۰-۹]+[.)،-]\s*)/, '').trim()).filter(Boolean);
}

export function validateTaskTitles(titles: string[]): string | null {
  if (!titles.length) return 'حداقل یک عنوان بنویس.';
  if (titles.length > 20) return 'در هر نوبت حداکثر ۲۰ وظیفه بساز.';
  if (titles.some((title) => title.length > 160)) return 'هر عنوان باید حداکثر ۱۶۰ نویسه باشد.';
  const keys = titles.map(normalizeCommand);
  if (keys.some((key) => !key)) return 'برای هر وظیفه عنوان معنادار بنویس.';
  if (new Set(keys).size !== keys.length) return 'عنوان تکراری در فهرست هست؛ آن را اصلاح کن.';
  return null;
}

/** Called only after the explicit preview confirmation; all or nothing. */
export async function createConfirmedTodayTasks(titles: string[], dateKey: string, confirmed: boolean): Promise<Task[]> {
  const error = validateTaskTitles(titles);
  if (!confirmed || error || dateKey !== getTodayKey()) throw new Error(error ?? 'confirmation-or-date-invalid');
  const now = new Date().toISOString();
  const tasks: Task[] = titles.map((title) => ({
    id: createId('tsk'), title, description: '', dueDate: dateKey, dueTime: null,
    priority: 'medium', quadrant: 'q2', category: 'personal', tags: [], status: 'todo',
    subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0,
    archived: false, postponeCount: 0, createdAt: now, updatedAt: now,
    completedDate: null, completedAt: null,
  }));
  const events: KairosEvent[] = tasks.map((task) => ({
    id: createId('evt'), at: now, kind: 'task.created', entity: 'task', entityId: task.id,
    source: 'user', level: 2, data: { via: 'assistant-confirmed', hasDueDate: true },
  }));
  await db.transaction('rw', db.tasks, db.eventHistory, async () => {
    await db.tasks.bulkAdd(tasks);
    await db.eventHistory.bulkAdd(events);
  });
  return tasks;
}
