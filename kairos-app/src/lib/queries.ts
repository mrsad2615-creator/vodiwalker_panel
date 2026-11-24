import { useLiveQuery } from 'dexie-react-hooks';
import type {
  FocusSession,
  Goal,
  Habit,
  HabitLog,
  JournalEntry,
  Project,
  ProjectStage,
  Task,
  UserSettings,
} from '@/types/domain';
import { db, DEFAULT_SETTINGS } from './db';
import type { Table } from 'dexie';

/** Shared by every normal view, search, recommendations and reminder scheduling. */
async function visibleRows<T extends { sample?: boolean }>(table: Table<T, string>): Promise<T[]> {
  const [rows, settings] = await Promise.all([table.toArray(), db.settings.get('default')]);
  return settings?.sampleVisible === false ? rows.filter((row) => row.sample !== true) : rows;
}

const EMPTY_TASKS: Task[] = [];
const EMPTY_HABITS: Habit[] = [];
const EMPTY_HABIT_LOGS: HabitLog[] = [];
const EMPTY_FOCUS_SESSIONS: FocusSession[] = [];
const EMPTY_GOALS: Goal[] = [];
const EMPTY_PROJECTS: Project[] = [];
const EMPTY_STAGES: ProjectStage[] = [];

export function useTasks(): Task[] {
  return useLiveQuery(() => visibleRows(db.tasks), [], EMPTY_TASKS);
}

export function useHabits(): Habit[] {
  return useLiveQuery(() => visibleRows(db.habits), [], EMPTY_HABITS);
}

export function useHabitLogs(): HabitLog[] {
  return useLiveQuery(() => visibleRows(db.habitLogs), [], EMPTY_HABIT_LOGS);
}

export function useFocusSessions(): FocusSession[] {
  return useLiveQuery(() => visibleRows(db.focusSessions), [], EMPTY_FOCUS_SESSIONS);
}

export function useGoals(): Goal[] {
  return useLiveQuery(() => visibleRows(db.goals), [], EMPTY_GOALS);
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

const EMPTY_JOURNAL: JournalEntry[] = [];

export function useProjects(): Project[] {
  return useLiveQuery(() => visibleRows(db.projects), [], EMPTY_PROJECTS);
}

export function useProjectStages(): ProjectStage[] {
  return useLiveQuery(() => visibleRows(db.projectStages), [], EMPTY_STAGES);
}

export function useJournal(): JournalEntry[] {
  return useLiveQuery(() => db.journal.toArray(), [], EMPTY_JOURNAL);
}
