import { db, ensureSettings } from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import type { ManagerInput } from '@/lib/coach';

/**
 * One read of everything the manager reasons about.
 *
 * The cloud turn and the offline coach see exactly the same shape, so the two can
 * never disagree about the user's day — and a test can build the same object by
 * hand without touching the database.
 */
export async function snapshotForManager(): Promise<ManagerInput> {
  const [tasks, habits, habitLogs, sessions, goals, projects, stages, settings] = await Promise.all([
    db.tasks.toArray(),
    db.habits.toArray(),
    db.habitLogs.toArray(),
    db.focusSessions.toArray(),
    db.goals.toArray(),
    db.projects.toArray(),
    db.projectStages.toArray(),
    ensureSettings(),
  ]);

  return {
    tasks,
    habits,
    habitLogs,
    sessions,
    goals,
    projects,
    stages,
    todayKey: getTodayKey(),
    goalMinutes: settings.dailyFocusGoalMinutes,
  };
}
