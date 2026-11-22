import Dexie, { type Table } from 'dexie';
import { DEFAULT_APPEARANCE } from '@/lib/theme';
import type { ClockSegment } from '@/lib/chrono/plan';
import type { PomodoroPhase } from '@/lib/focusTimer';
import type {
  FocusModeType,
  FocusSession,
  Goal,
  Habit,
  HabitLog,
  HabitLogStatus,
  JournalEntry,
  KairosBackupPayload,
  Task,
  TaskStatus,
  UserSettings,
} from '@/types/domain';
import { computeNextRecurrenceDate, getTodayKey } from './date';
import { createId } from './format';

export const DEFAULT_SETTINGS: UserSettings = {
  id: 'default',
  accentColor: DEFAULT_APPEARANCE.accent,
  backgroundId: DEFAULT_APPEARANCE.backgroundId,
  motionLevel: DEFAULT_APPEARANCE.motionLevel,
  homeMode: 'cinematic',
  displayName: 'کاربر کایروس',
  dailyFocusGoalMinutes: 120,
  dailyTaskGoal: 6,
  pomodoroFocusMin: 25,
  pomodoroShortBreakMin: 5,
  pomodoroLongBreakMin: 15,
  pomodoroCyclesBeforeLong: 4,
  intervalBlocks: 4,
  intervalBlockMin: 45,
  intervalRestMin: 10,
  countdownMin: 30,
  autoAdvance: false,
  dndInFocus: false,
  notificationsEnabled: true,
  soundEnabled: true,
  hapticsEnabled: true,
  hasSampleData: false,
  sampleBannerDismissed: false,
  updatedAt: new Date().toISOString(),
};

class KairosDatabase extends Dexie {
  tasks!: Table<Task, string>;
  habits!: Table<Habit, string>;
  habitLogs!: Table<HabitLog, string>;
  focusSessions!: Table<FocusSession, string>;
  goals!: Table<Goal, string>;
  settings!: Table<UserSettings, string>;
  /** Exactly one row (`id: 'active'`): the session that is not finished yet. */
  activeRun!: Table<ActiveRunRecord, string>;
  journal!: Table<JournalEntry, string>;

  constructor() {
    super('KairosLuxuryProductivityDB');
    this.version(1).stores({
      tasks: 'id, dueDate, status, priority, quadrant, category, archived, completedDate, createdAt',
      habits: 'id, archived, startDate, createdAt',
      habitLogs: 'id, habitId, dateKey, [habitId+dateKey], status',
      focusSessions: 'id, dateKey, taskId, mode, startedAt',
      goals: 'id, completed, category, deadline',
      settings: 'id',
    });

    // v2 — Chrono Core. The running session is written to disk so closing the
    // app (or Android killing the WebView) no longer erases a 40-minute focus
    // block. Only the one row is kept; history still lives in `focusSessions`.
    this.version(2).stores({
      activeRun: 'id, updatedAt',
    });

    // v3.2 — the journal. One row per day, keyed by its date so the app can ask
    // "what did today look like?" in a single get(), and range over a month.
    this.version(3).stores({
      journal: 'id, dateKey',
    });
  }
}

export const db = new KairosDatabase();

/**
 * A saved Chrono session. Kept deliberately close to the clock state so restoring
 * is a copy, not a translation — every field the arithmetic needs is here.
 */
export interface ActiveRunRecord {
  id: 'active';
  mode: FocusModeType;
  plan: ClockSegment[];
  segmentIndex: number;
  phase: PomodoroPhase;
  isRunning: boolean;
  remainingSeconds: number;
  elapsedSeconds: number;
  /** Epoch ms. `null` while paused. */
  anchorMs: number | null;
  anchorRemaining: number;
  anchorElapsed: number;
  completedCycles: number;
  accruedFocusSeconds: number;
  autoAdvance: boolean;
  linkedTaskId: string | null;
  sessionStartedAt: string | null;
  updatedAt: string;
}

export function makeHabitLogId(habitId: string, dateKey: string): string {
  return `${habitId}::${dateKey}`;
}

export async function ensureSettings(): Promise<UserSettings> {
  const existing = await db.settings.get('default');
  if (existing) {
    // Backfill every field added after the row was first written (1.4.0
    // appearance, 1.6.0 home mode, 1.8.0 Chrono Core). A field-by-field check
    // beats a hand-maintained list: the list is what kept getting forgotten.
    const merged: UserSettings = { ...DEFAULT_SETTINGS, ...existing, id: 'default' };
    const missing = (Object.keys(DEFAULT_SETTINGS) as (keyof UserSettings)[]).some(
      (key) => existing[key] === undefined
    );
    if (missing) await db.settings.put(merged);
    return merged;
  }
  const fresh: UserSettings = { ...DEFAULT_SETTINGS, updatedAt: new Date().toISOString() };
  await db.settings.put(fresh);
  return fresh;
}

export async function updateSettings(patch: Partial<Omit<UserSettings, 'id'>>): Promise<UserSettings> {
  const current = await ensureSettings();
  const next: UserSettings = {
    ...current,
    ...patch,
    id: 'default',
    updatedAt: new Date().toISOString(),
  };
  await db.settings.put(next);
  return next;
}

// ==================== TASKS ====================

export async function createTask(
  input: Omit<Task, 'id' | 'createdAt' | 'updatedAt' | 'completedAt' | 'completedDate' | 'postponeCount' | 'archived'>
): Promise<Task> {
  const now = new Date().toISOString();
  const isDone = input.status === 'completed';
  const task: Task = {
    ...input,
    id: createId('tsk'),
    archived: false,
    postponeCount: 0,
    createdAt: now,
    updatedAt: now,
    completedAt: isDone ? now : null,
    completedDate: isDone ? getTodayKey() : null,
  };
  await db.tasks.put(task);
  return task;
}

export async function updateTask(
  id: string,
  patch: Partial<Omit<Task, 'id' | 'createdAt'>>
): Promise<Task | undefined> {
  const existing = await db.tasks.get(id);
  if (!existing) return undefined;
  const now = new Date().toISOString();

  // Detect postponement when dueDate moves strictly forward on an incomplete task
  let postponeCount = existing.postponeCount;
  if (
    patch.dueDate &&
    existing.dueDate &&
    patch.dueDate > existing.dueDate &&
    existing.status !== 'completed'
  ) {
    postponeCount += 1;
  }

  let completedAt = existing.completedAt;
  let completedDate = existing.completedDate;
  if (patch.status && patch.status !== existing.status) {
    if (patch.status === 'completed') {
      completedAt = now;
      completedDate = getTodayKey();
    } else {
      completedAt = null;
      completedDate = null;
    }
  }

  const next: Task = {
    ...existing,
    ...patch,
    postponeCount,
    completedAt,
    completedDate,
    updatedAt: now,
  };
  await db.tasks.put(next);
  return next;
}

/**
 * Transitions a task status cleanly and automatically schedules the next recurrence
 * without creating duplicate tasks.
 */
export async function setTaskStatus(
  id: string,
  newStatus: TaskStatus
): Promise<{ task?: Task; spawnedNext?: Task }> {
  const existing = await db.tasks.get(id);
  if (!existing) return {};

  const updated = await updateTask(id, { status: newStatus });
  let spawnedNext: Task | undefined;

  if (
    updated &&
    newStatus === 'completed' &&
    existing.status !== 'completed' &&
    existing.recurrence !== 'none'
  ) {
    const baseDate = existing.dueDate ?? getTodayKey();
    const nextDate = computeNextRecurrenceDate(baseDate, existing.recurrence);

    // Prevent duplicates: check if an uncompleted task with identical title & dueDate already exists
    const existingCandidates = await db.tasks.where('dueDate').equals(nextDate).toArray();
    const duplicate = existingCandidates.find(
      (c) =>
        c.title.trim() === existing.title.trim() &&
        c.recurrence === existing.recurrence &&
        c.status !== 'completed' &&
        !c.archived
    );

    if (!duplicate) {
      spawnedNext = await createTask({
        title: existing.title,
        description: existing.description,
        dueDate: nextDate,
        dueTime: existing.dueTime,
        priority: existing.priority,
        quadrant: existing.quadrant,
        category: existing.category,
        tags: [...existing.tags],
        status: 'todo',
        subtasks: existing.subtasks.map((s) => ({
          id: createId('sub'),
          title: s.title,
          completed: false,
        })),
        recurrence: existing.recurrence,
        reminderEnabled: existing.reminderEnabled,
        reminderOffsetMin: existing.reminderOffsetMin,
      });
    }
  }

  return { task: updated, spawnedNext };
}

export async function archiveTask(id: string, archived: boolean): Promise<void> {
  await updateTask(id, { archived });
}

export async function deleteTask(id: string): Promise<void> {
  await db.tasks.delete(id);
}

// ==================== HABITS & LOGS ====================

export async function createHabit(
  input: Omit<Habit, 'id' | 'createdAt' | 'updatedAt' | 'archived'>
): Promise<Habit> {
  const now = new Date().toISOString();
  const habit: Habit = {
    ...input,
    id: createId('hab'),
    archived: false,
    createdAt: now,
    updatedAt: now,
  };
  await db.habits.put(habit);
  return habit;
}

export async function updateHabit(
  id: string,
  patch: Partial<Omit<Habit, 'id' | 'createdAt'>>
): Promise<Habit | undefined> {
  const existing = await db.habits.get(id);
  if (!existing) return undefined;
  const next: Habit = {
    ...existing,
    ...patch,
    updatedAt: new Date().toISOString(),
  };
  await db.habits.put(next);
  return next;
}

export async function deleteHabit(id: string): Promise<void> {
  await db.transaction('rw', db.habits, db.habitLogs, async () => {
    await db.habits.delete(id);
    await db.habitLogs.where('habitId').equals(id).delete();
  });
}

/**
 * Sets a habit log for a specific dateKey.
 * Uses deterministic composite key `${habitId}::${dateKey}` so duplicate records are impossible.
 */
export async function upsertHabitLog(
  habitId: string,
  dateKey: string,
  status: HabitLogStatus,
  value: number,
  note?: string
): Promise<HabitLog> {
  const log: HabitLog = {
    id: makeHabitLogId(habitId, dateKey),
    habitId,
    dateKey,
    status,
    value,
    note,
    updatedAt: new Date().toISOString(),
  };
  await db.habitLogs.put(log);
  return log;
}

/**
 * Clears a log record so that day accurately returns to the "Unlogged / No Data" state
 * (distinct from explicitly missed!).
 */
export async function clearHabitLog(habitId: string, dateKey: string): Promise<void> {
  await db.habitLogs.delete(makeHabitLogId(habitId, dateKey));
}

// ==================== FOCUS SESSIONS ====================

export async function recordFocusSession(
  input: Omit<FocusSession, 'id'>
): Promise<FocusSession> {
  const session: FocusSession = {
    ...input,
    id: createId('foc'),
  };
  await db.focusSessions.put(session);
  return session;
}

export async function deleteFocusSession(id: string): Promise<void> {
  await db.focusSessions.delete(id);
}

// ==================== GOALS ====================

export async function createGoal(
  input: Omit<Goal, 'id' | 'createdAt' | 'updatedAt' | 'completed'>
): Promise<Goal> {
  const now = new Date().toISOString();
  const completed = input.targetValue > 0 && input.currentValue >= input.targetValue;
  const goal: Goal = {
    ...input,
    id: createId('gol'),
    completed,
    createdAt: now,
    updatedAt: now,
  };
  await db.goals.put(goal);
  return goal;
}

export async function updateGoal(
  id: string,
  patch: Partial<Omit<Goal, 'id' | 'createdAt'>>
): Promise<Goal | undefined> {
  const existing = await db.goals.get(id);
  if (!existing) return undefined;
  const currentValue = patch.currentValue ?? existing.currentValue;
  const targetValue = patch.targetValue ?? existing.targetValue;
  const completed =
    patch.completed !== undefined
      ? patch.completed
      : targetValue > 0 && currentValue >= targetValue;
  const next: Goal = {
    ...existing,
    ...patch,
    currentValue,
    targetValue,
    completed,
    updatedAt: new Date().toISOString(),
  };
  await db.goals.put(next);
  return next;
}

export async function deleteGoal(id: string): Promise<void> {
  await db.goals.delete(id);
}

// ==================== BACKUP / RESTORE / WIPE ====================

export async function exportBackup(): Promise<KairosBackupPayload> {
  const [tasks, habits, habitLogs, focusSessions, goals, settings, journal] = await Promise.all([
    db.tasks.toArray(),
    db.habits.toArray(),
    db.habitLogs.toArray(),
    db.focusSessions.toArray(),
    db.goals.toArray(),
    ensureSettings(),
    db.journal.toArray(),
  ]);
  return {
    version: 2,
    exportedAt: new Date().toISOString(),
    app: 'KAIROS',
    tasks,
    habits,
    habitLogs,
    focusSessions,
    goals,
    settings,
    journal,
  };
}

export async function importBackup(payload: KairosBackupPayload): Promise<void> {
  if (!payload || payload.app !== 'KAIROS' || !Array.isArray(payload.tasks)) {
    throw new Error('فرمت فایل پشتیبان کایروس معتبر نیست.');
  }
  await db.transaction(
    'rw',
    [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings, db.journal],
    async () => {
      await Promise.all([
        db.tasks.clear(),
        db.habits.clear(),
        db.habitLogs.clear(),
        db.focusSessions.clear(),
        db.goals.clear(),
        db.journal.clear(),
      ]);
      if (payload.tasks.length) await db.tasks.bulkPut(payload.tasks);
      if (payload.habits?.length) await db.habits.bulkPut(payload.habits);
      if (payload.habitLogs?.length) await db.habitLogs.bulkPut(payload.habitLogs);
      if (payload.focusSessions?.length) await db.focusSessions.bulkPut(payload.focusSessions);
      if (payload.goals?.length) await db.goals.bulkPut(payload.goals);
      if (payload.journal?.length) await db.journal.bulkPut(payload.journal);
      if (payload.settings) {
        await db.settings.put({ ...DEFAULT_SETTINGS, ...payload.settings, id: 'default' });
      }
    }
  );
}

export async function clearAllUserData(): Promise<void> {
  const current = await ensureSettings();
  await db.transaction(
    'rw',
    [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings, db.journal],
    async () => {
      await Promise.all([
        db.tasks.clear(),
        db.habits.clear(),
        db.habitLogs.clear(),
        db.focusSessions.clear(),
        db.goals.clear(),
        db.journal.clear(),
      ]);
      await db.settings.put({
        ...current,
        hasSampleData: false,
        sampleBannerDismissed: true,
        updatedAt: new Date().toISOString(),
      });
    }
  );
}

/* ── Journal ────────────────────────────────────────────────────────────────── */

/**
 * Writes (or rewrites) the entry for one day. The row is keyed by date, so the
 * second write of a day is an edit — never a second entry in the list.
 */
export async function upsertJournalEntry(
  dateKey: string,
  patch: { mood?: number; note?: string; highlight?: string }
): Promise<JournalEntry> {
  const id = `journal-${dateKey}`;
  const existing = await db.journal.get(id);
  const now = new Date().toISOString();

  const entry: JournalEntry = {
    id,
    dateKey,
    mood: patch.mood ?? existing?.mood ?? 3,
    note: patch.note ?? existing?.note ?? '',
    highlight: patch.highlight ?? existing?.highlight ?? '',
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };

  await db.journal.put(entry);
  return entry;
}

export async function deleteJournalEntry(dateKey: string): Promise<void> {
  await db.journal.delete(`journal-${dateKey}`);
}
