import type { Task } from '@/types/domain';
import { dateKeyToJalaali } from '@/lib/date';
import { contrastRatio, DEFAULT_ACCENT, deriveAccent, WIDGET_SURFACE, WIDGET_TEXT_FALLBACK } from '@/lib/theme';

export interface WidgetSnapshot {
  day: string;
  year: number;
  updatedAt: number;
  accent: string;
  showTitles: boolean;
  todayTotal: number;
  todayDone: number;
  tasks: { id: string; title: string; status: Task['status']; sample: boolean }[];
  monthlyDone: number[];
}

/** A small, read-only, date-stamped projection. No IDs or private titles when hidden. */
export function buildWidgetSnapshot(tasks: Task[], day: string, showTitles: boolean, sampleVisible: boolean, accentColor = DEFAULT_ACCENT, updatedAt = Date.now()): WidgetSnapshot {
  const derived = deriveAccent(accentColor).fg;
  const accent = contrastRatio(derived, WIDGET_SURFACE) >= 4.5 ? derived : WIDGET_TEXT_FALLBACK;
  const visible = tasks.filter((t) => sampleVisible || !t.sample);
  const priority = { high: 0, medium: 1, low: 2 } as const;
  const today = visible.filter((t) => !t.archived && t.dueDate === day).sort((a, b) =>
    Number(a.status === 'completed') - Number(b.status === 'completed') ||
    (a.dueTime ?? '99:99').localeCompare(b.dueTime ?? '99:99') ||
    (priority[a.priority] ?? 1) - (priority[b.priority] ?? 1) || a.id.localeCompare(b.id));
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
    day, year, updatedAt, accent, showTitles,
    todayTotal: today.length,
    todayDone: today.filter((task) => task.status === 'completed').length,
    tasks: showTitles ? today.slice(0, 3).map((task) => ({ id: task.id, title: task.title.slice(0, 80), status: task.status, sample: task.sample === true })) : [],
    monthlyDone,
  };
}
