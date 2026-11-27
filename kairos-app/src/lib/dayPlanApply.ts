import type { Task } from '@/types/domain';
import type { DayPlan } from './planner';
import { db, setTaskStatus } from './db';
import { getTodayKey } from './date';

export interface SuggestedTime { taskId: string; title: string; time: string; minutes: number }
export interface DayPlanPreview {
  dateKey: string;
  snapshot: Task[];
  suggested: SuggestedTime[];
  skipped: number;
}

function minutes(time: string | null): number | null {
  if (!time || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) return null;
  const [hours, mins] = time.split(':').map(Number);
  return hours * 60 + mins;
}
function stamp(value: number): string {
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
}
function duration(value: number | null | undefined): number {
  return Number.isFinite(value) && value! > 0 ? Math.min(720, Math.ceil(value!)) : 30;
}
function sortSnapshot(tasks: Task[]): Task[] { return [...tasks].sort((a, b) => a.id.localeCompare(b.id)); }

/** Proposal only. Only tasks already due today are eligible: we never silently
 * change an overdue date, set a time on an undated task, or cross midnight.
 * Known task intervals are estimates, not a guarantee about the user's calendar. */
export function previewDayPlan(plan: DayPlan, tasks: Task[], dateKey: string, start: string, now = new Date()): DayPlanPreview {
  const cursorStart = minutes(start);
  if (dateKey !== getTodayKey(now) || cursorStart === null || cursorStart < now.getHours() * 60 + now.getMinutes()) {
    throw new Error('plan-invalid-time');
  }
  const all = new Map(tasks.map((task) => [task.id, task]));
  const intervals = tasks.flatMap((task) => {
    const startAt = minutes(task.dueTime);
    return task.dueDate === dateKey && !task.archived && task.status !== 'completed' && startAt !== null
      ? [{ start: startAt, end: Math.min(1440, startAt + duration(task.estimateMin)) }] : [];
  });
  const suggested: SuggestedTime[] = [];
  let cursor = cursorStart;
  const seen = new Set<string>();
  for (const block of plan.blocks) {
    if (suggested.length >= 50) continue;
    const task = all.get(block.taskId);
    if (!task || seen.has(task.id) || task.archived || task.status === 'completed' || task.dueDate !== dateKey || task.dueTime !== null) continue;
    seen.add(task.id);
    const length = duration(block.minutes);
    while (cursor + length <= 1440) {
      const clash = intervals.filter((item) => cursor < item.end && cursor + length > item.start).sort((a, b) => a.end - b.end)[0];
      if (!clash) break;
      cursor = Math.ceil(clash.end / 5) * 5;
    }
    if (cursor + length > 1440) continue;
    suggested.push({ taskId: task.id, title: task.title, time: stamp(cursor), minutes: length });
    intervals.push({ start: cursor, end: cursor + length });
    cursor += length;
  }
  return { dateKey, snapshot: sortSnapshot(tasks), suggested, skipped: plan.blocks.length - suggested.length };
}

/** All writes and undo records commit together; stale previews fail closed. */
export async function applyDayPlanPreview(preview: DayPlanPreview, confirmed: boolean): Promise<number> {
  if (!confirmed || preview.suggested.length === 0 || preview.suggested.length > 50) throw new Error('plan-not-confirmed');
  const ids = preview.suggested.map((item) => item.taskId);
  if (new Set(ids).size !== ids.length) throw new Error('plan-invalid-preview');
  return db.transaction('rw', [db.tasks, db.undoHistory, db.eventHistory, db.settings], async () => {
    const now = new Date();
    if (getTodayKey(now) !== preview.dateKey || preview.suggested.some((entry) => {
      const value = minutes(entry.time);
      return value === null || value < now.getHours() * 60 + now.getMinutes();
    })) throw new Error('plan-time-passed');
    const current = sortSnapshot(await db.tasks.toArray());
    if (JSON.stringify(current) !== JSON.stringify(preview.snapshot)) throw new Error('plan-changed');
    const expected = new Map(current.map((task) => [task.id, task]));
    const settings = await db.settings.get('default');
    for (const entry of preview.suggested) {
      const task = expected.get(entry.taskId);
      if (!task || task.dueDate !== preview.dateKey || task.dueTime !== null || task.archived || task.status === 'completed' || (task.sample && settings?.sampleVisible !== true)) throw new Error('plan-changed');
      await setTaskStatus(task.id, task.status, { expected: task, patch: { dueTime: entry.time } });
    }
    return preview.suggested.length;
  });
}
