import type { Goal, Habit, NavTab, Task } from '@/types/domain';

/**
 * Global search.
 *
 * One box over the whole workspace: tasks, habits, goals and the sections
 * themselves. Pure functions, so the ranking is provable without a browser.
 *
 * Persian needs its own normalisation before anything is compared. `ي` and `ی`
 * are the same letter, `ك` and `ک` are the same letter, the ZWNJ that joins
 * «می‌شود» is invisible, and Arabic diacritics are typed by some keyboards and
 * not others. A search box that misses «می‌شود» when you type «میشود» is broken,
 * however fast it is.
 */

export type SearchKind = 'task' | 'habit' | 'goal' | 'section';

export interface SearchableItem {
  id: string;
  kind: SearchKind;
  title: string;
  /** Second line in the results list. */
  subtitle?: string;
  tab: NavTab;
  keywords?: string[];
  /** Finished work ranks below open work at the same relevance. */
  done?: boolean;
}

export interface SearchResult extends SearchableItem {
  score: number;
}

const ZWNJ = /\u200c/g;
const DIACRITICS = /[\u064b-\u0652\u0670\u0640]/g;

/** Normalises Persian/Arabic text for comparison. */
export function normalizeFa(input: string): string {
  return input
    .toLowerCase()
    .replace(/ي/g, 'ی')
    .replace(/ك/g, 'ک')
    .replace(/ۀ/g, 'ه')
    .replace(/أ|إ|آ/g, 'ا')
    .replace(DIACRITICS, '')
    .replace(ZWNJ, ' ')
    .replace(/[^\p{L}\p{N} ]+/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

const KIND_WEIGHT: Record<SearchKind, number> = {
  section: 4,
  task: 3,
  habit: 2,
  goal: 1,
};

/**
 * Relevance of one item for one query.
 * `0` means "does not match at all" and the item is dropped.
 */
export function scoreMatch(query: string, item: SearchableItem): number {
  const needle = normalizeFa(query);
  if (!needle) return 0;

  const title = normalizeFa(item.title);
  const haystack = normalizeFa(
    [item.title, item.subtitle ?? '', ...(item.keywords ?? [])].join(' ')
  );

  // Every token must appear somewhere, otherwise "کتاب خرید" would match every
  // task that has either word.
  const tokens = needle.split(' ').filter(Boolean);
  if (!tokens.every((token) => haystack.includes(token))) return 0;

  let score = 0;
  if (title === needle) score += 120;
  else if (title.startsWith(needle)) score += 80;
  else if (title.includes(needle)) score += 55;
  else if (tokens.every((token) => title.includes(token))) score += 40;
  else score += 12;

  // Word starts beat mid-word hits: "خر" should find «خرید» before «آخر».
  const wordStart = new RegExp(`(^| )${needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`);
  if (wordStart.test(title)) score += 25;

  score += KIND_WEIGHT[item.kind];
  if (item.done) score -= 15;
  return score;
}

/** Ranked results, best first. Stable for equal scores. */
export function searchEverything(
  query: string,
  items: SearchableItem[],
  limit = 12
): SearchResult[] {
  if (!normalizeFa(query)) return [];
  const scored: SearchResult[] = [];
  for (const item of items) {
    const score = scoreMatch(query, item);
    if (score > 0) scored.push({ ...item, score });
  }
  scored.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title, 'fa'));
  return scored.slice(0, limit);
}

/** The seven rooms, so the box can also be used as navigation. */
export function sectionItems(): SearchableItem[] {
  return [
    { id: 'nav:dashboard', kind: 'section', title: 'خانه', subtitle: 'کارهای امروز، عادت‌ها و خلاصهٔ هفته', tab: 'dashboard', keywords: ['داشبورد', 'خانه'] },
    { id: 'nav:focus', kind: 'section', title: 'تمرکز', subtitle: 'زمان‌سنج، سکوت و فضای صوتی', tab: 'focus', keywords: ['تایمر', 'پومودورو', 'صدا'] },
    { id: 'nav:tasks', kind: 'section', title: 'وظایف', subtitle: 'فهرست کارها و ماتریس آیزنهاور', tab: 'tasks', keywords: ['کار', 'تسک'] },
    { id: 'nav:habits', kind: 'section', title: 'عادت‌ها', subtitle: 'زنجیره‌ها و ثبت روزانه', tab: 'habits', keywords: ['روتین', 'زنجیره'] },
    { id: 'nav:calendar', kind: 'section', title: 'تقویم', subtitle: 'نمای ماه و سال شمسی', tab: 'calendar', keywords: ['تاریخ', 'ماه', 'سال'] },
    { id: 'nav:analytics', kind: 'section', title: 'تحلیل عملکرد', subtitle: 'گزارش هفته، ماه و سال', tab: 'analytics', keywords: ['گزارش', 'آمار'] },
    { id: 'nav:settings', kind: 'section', title: 'تنظیمات', subtitle: 'ظاهر، یادآوری، پشتیبان', tab: 'settings', keywords: ['تنظیم', 'پشتیبان'] },
  ];
}

export interface SearchIndexInput {
  tasks: Task[];
  habits: Habit[];
  goals: Goal[];
}

/** Builds the searchable index from live rows. */
export function buildSearchIndex({ tasks, habits, goals }: SearchIndexInput): SearchableItem[] {
  const items: SearchableItem[] = [...sectionItems()];

  for (const task of tasks) {
    if (task.archived) continue;
    const done = task.status === 'completed';
    items.push({
      id: `task:${task.id}`,
      kind: 'task',
      title: task.title,
      subtitle: done ? 'انجام‌شده' : task.dueDate ? `وظیفه · سررسید ${task.dueDate}` : 'وظیفه',
      tab: 'tasks',
      keywords: [task.description, task.category, task.quadrant].filter(Boolean) as string[],
      done,
    });
  }

  for (const habit of habits) {
    if (habit.archived) continue;
    items.push({
      id: `habit:${habit.id}`,
      kind: 'habit',
      title: habit.name,
      subtitle: 'عادت',
      tab: 'habits',
      keywords: [habit.description].filter(Boolean) as string[],
    });
  }

  for (const goal of goals) {
    items.push({
      id: `goal:${goal.id}`,
      kind: 'goal',
      title: goal.title,
      subtitle: goal.completed ? 'هدف تمام‌شده' : 'هدف',
      tab: 'dashboard',
      keywords: [goal.description, goal.category].filter(Boolean) as string[],
      done: goal.completed,
    });
  }

  return items;
}
