import type { Task } from '@/types/domain';
import { dateKeyToJalaali } from '@/lib/date';

export interface WidgetSnapshot {
  day: string;
  year: number;
  showTitles: boolean;
  todayTotal: number;
  todayDone: number;
  tasks: { title: string; status: Task['status']; sample: boolean }[];
  monthlyDone: number[];
}

/** A small, read-only, date-stamped projection. No IDs or private titles when hidden. */
export function buildWidgetSnapshot(tasks: Task[], day: string, showTitles: boolean, sampleVisible: boolean): WidgetSnapshot {
  const visible = tasks.filter((t) => !t.archived && (sampleVisible || !t.sample));
  const today = visible.filter((t) => t.dueDate === day);
  const year = dateKeyToJalaali(day).jy;
  const monthlyDone = Array<number>(12).fill(0);
  for (const task of visible) {
    if (task.status !== 'completed' || !task.completedDate) continue;
    try {
      const date = dateKeyToJalaali(task.completedDate);
      if (date.jy === year && date.jm >= 1 && date.jm <= 12) monthlyDone[date.jm - 1] += 1;
    } catch {
      // An invalid historical date must not stop other widget data from updating.
    }
  }
  return {
    day, year, showTitles,
    todayTotal: today.length,
    todayDone: today.filter((task) => task.status === 'completed').length,
    tasks: showTitles ? today.slice(0, 3).map((task) => ({ title: task.title.slice(0, 80), status: task.status, sample: task.sample === true })) : [],
    monthlyDone,
  };
}
