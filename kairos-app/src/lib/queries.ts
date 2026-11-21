import { useLiveQuery } from 'dexie-react-hooks';
import type { FocusSession, Goal, Habit, HabitLog, Task, UserSettings } from '@/types/domain';
import { db, DEFAULT_SETTINGS } from './db';

const EMPTY_TASKS: Task[] = [];
const EMPTY_HABITS: Habit[] = [];
const EMPTY_HABIT_LOGS: HabitLog[] = [];
const EMPTY_FOCUS_SESSIONS: FocusSession[] = [];
const EMPTY_GOALS: Goal[] = [];

export function useTasks(): Task[] {
  return useLiveQuery(() => db.tasks.toArray(), [], EMPTY_TASKS);
}

export function useHabits(): Habit[] {
  return useLiveQuery(() => db.habits.toArray(), [], EMPTY_HABITS);
}

export function useHabitLogs(): HabitLog[] {
  return useLiveQuery(() => db.habitLogs.toArray(), [], EMPTY_HABIT_LOGS);
}

export function useFocusSessions(): FocusSession[] {
  return useLiveQuery(() => db.focusSessions.toArray(), [], EMPTY_FOCUS_SESSIONS);
}

export function useGoals(): Goal[] {
  return useLiveQuery(() => db.goals.toArray(), [], EMPTY_GOALS);
}

export function useSettings(): UserSettings {
  return useLiveQuery(
    async () => {
      const row = await db.settings.get('default');
      if (!row) return DEFAULT_SETTINGS;
      // Rows written before 1.4.0 have no appearance fields — backfill in memory
      // so the theme engine always receives a complete, valid object.
      return { ...DEFAULT_SETTINGS, ...row, id: 'default' as const };
    },
    [],
    DEFAULT_SETTINGS
  );
}
