import type { NavTab } from '@/types/domain';

/** The fourth shortcut is kept in settings as a reserve for safe upgrades. */
export type ShortcutTab = Exclude<NavTab, 'dashboard' | 'assistant'>;
export const SHORTCUTS: readonly ShortcutTab[] = ['tasks', 'habits', 'focus', 'motivation', 'projects', 'calendar', 'analytics', 'settings'];
export const DEFAULT_SHORTCUTS: ShortcutTab[] = ['tasks', 'habits', 'focus', 'motivation'];

export function normalizeShortcuts(value: unknown): ShortcutTab[] {
  if (!Array.isArray(value) || value.length !== 4 || new Set(value).size !== 4 ||
      value.some((entry) => !SHORTCUTS.includes(entry as ShortcutTab))) return [...DEFAULT_SHORTCUTS];
  return value as ShortcutTab[];
}

/** Move an existing shortcut into the slot by swapping, never leaving duplicates. */
export function changeShortcut(slots: ShortcutTab[], index: number, next: ShortcutTab): ShortcutTab[] {
  if (index < 0 || index >= 4 || !SHORTCUTS.includes(next)) return normalizeShortcuts(slots);
  const result = [...normalizeShortcuts(slots)];
  const oldIndex = result.indexOf(next);
  if (oldIndex >= 0) result[oldIndex] = result[index];
  result[index] = next;
  return result;
}
