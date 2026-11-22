import { addDays, getTodayKey } from './date';
import type { JournalEntry } from '@/types/domain';

/**
 * The journal's arithmetic.
 *
 * A journal is not a diary app's feature list; it is three numbers that make
 * writing worth the trouble: how many days in a row you actually showed up, how
 * the last week felt, and how today compares to it. Everything here is derived
 * from real rows — no entry, no number.
 */

export const MOODS = [
  { value: 1, label: 'بد', glyph: '◔' },
  { value: 2, label: 'کم', glyph: '◑' },
  { value: 3, label: 'معمولی', glyph: '◕' },
  { value: 4, label: 'خوب', glyph: '●' },
  { value: 5, label: 'عالی', glyph: '✦' },
] as const;

export function makeJournalId(dateKey: string): string {
  return `journal-${dateKey}`;
}

export interface JournalStats {
  /** Consecutive days with an entry, counting back from today (or yesterday). */
  streak: number;
  total: number;
  /** Mean mood over the last seven days, or null when there is nothing to mean. */
  weekAverage: number | null;
  /** Today's mood, if today was written. */
  todayMood: number | null;
  /** Seven booleans, oldest first: did a day get written? */
  weekWritten: boolean[];
}

export function computeJournalStats(
  entries: JournalEntry[],
  todayKey: string = getTodayKey()
): JournalStats {
  const byDate = new Map(entries.map((entry) => [entry.dateKey, entry]));

  // A streak survives "today not written yet": it counts back from today if
  // today exists, otherwise from yesterday. Otherwise every morning would show
  // a broken streak for a user who has written every day for a month.
  let cursor = byDate.has(todayKey) ? todayKey : addDays(todayKey, -1);
  let streak = 0;
  while (byDate.has(cursor)) {
    streak++;
    cursor = addDays(cursor, -1);
  }

  const weekKeys = Array.from({ length: 7 }, (_, i) => addDays(todayKey, -(6 - i)));
  const weekWritten = weekKeys.map((key) => byDate.has(key));
  const weekMoods = weekKeys
    .map((key) => byDate.get(key)?.mood)
    .filter((mood): mood is number => typeof mood === 'number' && mood > 0);

  return {
    streak,
    total: entries.length,
    weekAverage: weekMoods.length
      ? Math.round((weekMoods.reduce((sum, mood) => sum + mood, 0) / weekMoods.length) * 10) / 10
      : null,
    todayMood: byDate.get(todayKey)?.mood ?? null,
    weekWritten,
  };
}
