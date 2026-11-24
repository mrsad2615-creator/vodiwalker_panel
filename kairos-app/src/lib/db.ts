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
  Project,
  ProjectStage,
  Task,
  TaskStatus,
  UserSettings,
} from '@/types/domain';
import { computeNextRecurrenceDate, getTodayKey } from './date';
import { validateBackup } from './backupValidation';
import { createId } from './format';
import type { Authority, EventEntity, EventKind, EventSource, KairosEvent } from './events';
import type { PermissionGrant } from './permissions';
import type { UndoEntry, UndoKind } from './undo';

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
  sampleSetupComplete: false,
  sampleVisible: true,
  aiEnabled: false,
  aiBaseUrl: '',
  aiModel: '',
  aiKey: '',
  aiAutoApply: false,
  aiConsentAt: null,
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
  /** Append-only ledger (v4). Reads live in `lib/events.ts`. */
  eventHistory!: Table<KairosEvent, string>;
  /** One "before" snapshot per restorable change (v4). Reads live in `lib/undo.ts`. */
  undoHistory!: Table<UndoEntry, string>;
  /** Time-boxed, use-limited permissions (v4). Logic lives in `lib/permissions.ts`. */
  permissionGrants!: Table<PermissionGrant, string>;
  /** One row per schema upgrade: what the migration saw and what it did (v4). */
  migrationLog!: Table<MigrationLogRecord, string>;
  /** goal → project → stage → task (v5). */
  projects!: Table<Project, string>;
  projectStages!: Table<ProjectStage, string>;

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

    // v3.4 — Stage 1 infrastructure. Four new tables, and *no change to any
    // existing table or index*: the audit trail, the undo stack, the permission
    // grants, and a log of the upgrade itself. Everything the app already knew
    // stays exactly where it was.
    this.version(4)
      .stores({
        eventHistory: 'id, at, kind, entity, entityId',
        undoHistory: 'id, at, kind, entityId',
        permissionGrants: 'id, action, expiresAt, revokedAt',
        migrationLog: 'id, at',
      })
      .upgrade(async (tx) => {
        // Count what exists so the migration can be proven, not assumed.
        const counts: Record<string, number> = {};
        for (const name of ['tasks', 'habits', 'habitLogs', 'focusSessions', 'goals', 'journal', 'settings']) {
          counts[name] = await tx.table(name).count();
        }
        await tx.table('migrationLog').put({
          id: `mig-${Date.now()}`,
          at: new Date().toISOString(),
          from: 3,
          to: 4,
          counts,
          ok: true,
        });
      });

    // v3.5 — projects and stages, plus three optional fields on tasks
    // (projectId, estimateMin, dependsOn). Existing rows are copied forward with
    // a snapshot so the change is reversible even without a saved backup file.
    this.version(5)
      .stores({
        projects: 'id, goalId, status, dueDate, createdAt',
        projectStages: 'id, projectId, order, done',
        tasks:
          'id, dueDate, status, priority, quadrant, category, archived, completedDate, createdAt, goalId, projectId',
      })
      .upgrade(async (tx) => {
        const tasks = await tx.table('tasks').toArray();
        const goals = await tx.table('goals').toArray();
        const habits = await tx.table('habits').count();
        const habitLogs = await tx.table('habitLogs').count();
        const focusSessions = await tx.table('focusSessions').count();
        const journal = await tx.table('journal').count();

        // Normalise the new optional fields so every consumer can rely on them
        // existing, without guessing "undefined means null or means absent".
        for (const task of tasks) {
          const next = task as Record<string, unknown>;
          if (next.projectId === undefined) next.projectId = null;
          if (next.estimateMin === undefined) next.estimateMin = null;
          if (next.dependsOn === undefined) next.dependsOn = [];
          if (next.goalId === undefined) next.goalId = null;
          await tx.table('tasks').put(next);
        }

        await tx.table('migrationLog').put({
          id: `mig-${Date.now()}`,
          at: new Date().toISOString(),
          from: 4,
          to: 5,
          counts: {
            tasks: tasks.length,
            goals: goals.length,
            habits,
            habitLogs,
            focusSessions,
            journal,
            projects: 0,
            projectStages: 0,
          },
          ok: true,
          snapshot: { tasks, goals },
        });
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

/**
 * One row per schema upgrade. The migration writes it inside its own transaction,
 * so a half-finished upgrade cannot leave an optimistic "ok" behind.
 */
export interface MigrationLogRecord {
  id: string;
  at: string;
  from: number;
  to: number;
  counts: Record<string, number>;
  ok: boolean;
  /**
   * A restore point for the rows a migration *touches*. v5 only adds fields, so
   * the snapshot is small; when a future migration rewrites rows, this is the
   * copy that makes the change reversible without asking the user for a file.
   */
  snapshot?: { tasks?: unknown[]; goals?: unknown[] };
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
  await logEvent('settings.updated', 'settings', 'default', {
    level: 2,
    data: { keys: Object.keys(patch).join(',').slice(0, 200) },
  });
  return next;
}

// ==================== LEDGER: EVENTS + UNDO ====================
//
// Every restorable write leaves two traces: a small append-only event (what
// happened, who did it, at which authority level) and, for edits, a "before"
// snapshot that `lib/undo.ts` can replay. Both are best-effort: a logging failure
// must never turn a successful save into a failed one, so every call is guarded.

let writeCounter = 0;

export async function logEvent(
  kind: EventKind,
  entity: EventEntity,
  entityId: string,
  options: {
    source?: EventSource;
    level?: Authority;
    data?: KairosEvent['data'];
  } = {}
): Promise<void> {
  try {
    await db.eventHistory.put({
      id: createId('evt'),
      at: new Date().toISOString(),
      kind,
      entity,
      entityId,
      source: options.source ?? 'user',
      level: options.level ?? 1,
      ...(options.data ? { data: options.data } : {}),
    });
    writeCounter += 1;
    if (writeCounter % 64 === 0) {
      const { pruneEvents } = await import('./events');
      void pruneEvents().catch(() => undefined);
    }
  } catch {
    // never let the ledger break the write it describes
  }
}

async function pushUndo(entry: {
  kind: UndoKind;
  entity: EventEntity;
  entityId: string;
  label: string;
  before: unknown | null;
  after: unknown | null;
}): Promise<void> {
  try {
    await db.undoHistory.put({
      id: createId('und'),
      at: new Date().toISOString(),
      undoneAt: null,
      ...entry,
    });
    if ((writeCounter += 1) % 64 === 0) {
      const { pruneUndo } = await import('./undo');
      void pruneUndo().catch(() => undefined);
    }
  } catch {
    // the edit still stands; the safety net is a bonus, not a condition
  }
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
  await logEvent('task.created', 'task', task.id, {
    data: { status: task.status, hasDueDate: task.dueDate !== null, quadrant: task.quadrant },
  });
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

  const postponed = postponeCount > existing.postponeCount;
  await pushUndo({
    kind: patch.status && patch.status !== existing.status ? 'task.status' : 'task.update',
    entity: 'task',
    entityId: id,
    label: patch.status && patch.status !== existing.status ? 'تغییر وضعیت وظیفه' : 'ویرایش وظیفه',
    before: existing,
    after: next,
  });
  await logEvent('task.updated', 'task', id, { level: 2 });
  if (postponed) {
    await logEvent('task.postponed', 'task', id, {
      level: 2,
      data: { count: postponeCount },
    });
  }
  if (patch.status && patch.status !== existing.status) {
    await logEvent('task.status', 'task', id, {
      level: 2,
      data: { from: existing.status, to: patch.status },
    });
  }
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
        sample: existing.sample,
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
  await logEvent('task.archived', 'task', id, { level: 2, data: { archived } });
}

export async function deleteTask(id: string): Promise<void> {
  await db.tasks.delete(id);
  // Deletion is level 3 on purpose: it is confirmed, logged, and *not* undoable.
  await logEvent('task.deleted', 'task', id, { level: 3 });
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
  await logEvent('habit.created', 'habit', habit.id, { data: { scheduleType: habit.scheduleType } });
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
  await pushUndo({
    kind: 'habit.update',
    entity: 'habit',
    entityId: id,
    label: 'ویرایش عادت',
    before: existing,
    after: next,
  });
  await logEvent('habit.updated', 'habit', id, { level: 2 });
  return next;
}

export async function deleteHabit(id: string): Promise<void> {
  await db.transaction('rw', db.habits, db.habitLogs, async () => {
    await db.habits.delete(id);
    await db.habitLogs.where('habitId').equals(id).delete();
  });
  await logEvent('habit.deleted', 'habit', id, { level: 3 });
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
  const habit = await db.habits.get(habitId);
  const log: HabitLog = {
    sample: habit?.sample,
    id: makeHabitLogId(habitId, dateKey),
    habitId,
    dateKey,
    status,
    value,
    note,
    updatedAt: new Date().toISOString(),
  };
  const previous = await db.habitLogs.get(log.id);
  await db.habitLogs.put(log);
  await pushUndo({
    kind: 'habit.log',
    entity: 'habitLog',
    entityId: log.id,
    label: 'ثبت عادت',
    before: previous ?? null,
    after: log,
  });
  await logEvent('habit.logged', 'habitLog', log.id, {
    level: 2,
    data: { habitId, dateKey, status, value },
  });
  return log;
}

/**
 * Clears a log record so that day accurately returns to the "Unlogged / No Data" state
 * (distinct from explicitly missed!).
 */
export async function clearHabitLog(habitId: string, dateKey: string): Promise<void> {
  const id = makeHabitLogId(habitId, dateKey);
  const previous = await db.habitLogs.get(id);
  await db.habitLogs.delete(id);
  if (previous) {
    await pushUndo({
      kind: 'habit.log',
      entity: 'habitLog',
      entityId: id,
      label: 'برداشتن ثبت عادت',
      before: previous,
      after: null,
    });
  }
  await logEvent('habit.unlogged', 'habitLog', id, { level: 2, data: { habitId, dateKey } });
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
  await logEvent('focus.recorded', 'focusSession', session.id, {
    data: { minutes: Math.round(session.durationMinutes), mode: session.mode, natural: session.completedNaturally },
  });
  return session;
}

export async function deleteFocusSession(id: string): Promise<void> {
  await db.focusSessions.delete(id);
  await logEvent('focus.deleted', 'focusSession', id, { level: 3 });
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
  await logEvent('goal.created', 'goal', goal.id, { data: { target: goal.targetValue } });
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
  await pushUndo({
    kind: 'goal.update',
    entity: 'goal',
    entityId: id,
    label: 'ویرایش هدف',
    before: existing,
    after: next,
  });
  await logEvent('goal.updated', 'goal', id, {
    level: 2,
    data: { current: next.currentValue, target: next.targetValue, completed: next.completed },
  });
  return next;
}

export async function deleteGoal(id: string): Promise<void> {
  await db.goals.delete(id);
  await logEvent('goal.deleted', 'goal', id, { level: 3 });
}

// ==================== PROJECTS & STAGES ====================

export async function createProject(
  input: Omit<Project, 'id' | 'createdAt' | 'updatedAt' | 'status'> &
    Partial<Pick<Project, 'status'>>
): Promise<Project> {
  const now = new Date().toISOString();
  const project: Project = {
    ...input,
    status: input.status ?? 'active',
    id: createId('prj'),
    createdAt: now,
    updatedAt: now,
  };
  await db.projects.put(project);
  await logEvent('project.created', 'project', project.id, { level: 1 });
  return project;
}

export async function updateProject(
  id: string,
  patch: Partial<Omit<Project, 'id' | 'createdAt'>>
): Promise<Project | undefined> {
  const existing = await db.projects.get(id);
  if (!existing) return undefined;
  const next: Project = { ...existing, ...patch, updatedAt: new Date().toISOString() };
  await db.projects.put(next);
  const statusChanged = patch.status !== undefined && patch.status !== existing.status;
  await pushUndo({
    kind: statusChanged ? 'project.status' : 'project.update',
    entity: 'project',
    entityId: id,
    label: statusChanged ? 'تغییر وضعیت پروژه' : 'ویرایش پروژه',
    before: existing,
    after: next,
  });
  await logEvent(statusChanged ? 'project.archived' : 'project.updated', 'project', id, {
    level: 2,
    data: statusChanged ? { from: existing.status, to: next.status } : undefined,
  });
  return next;
}

export async function setProjectStatus(id: string, status: Project['status']): Promise<void> {
  await updateProject(id, { status });
}

export async function deleteProject(id: string): Promise<void> {
  await db.transaction('rw', db.projects, db.projectStages, db.tasks, async () => {
    await db.projects.delete(id);
    await db.projectStages.where('projectId').equals(id).delete();
    // Tasks are never deleted with their project: the work happened.
    const linked = await db.tasks.where('projectId').equals(id).toArray();
    for (const task of linked) {
      await db.tasks.put({ ...task, projectId: null, updatedAt: new Date().toISOString() });
    }
  });
  // Not undoable by design: deletions are level 3 and need a backup, never a
  // hopeful undo button. The tasks that pointed here are kept and unlinked.
  await logEvent('project.deleted', 'project', id, { level: 3 });
}

export async function createStage(
  input: Omit<ProjectStage, 'id' | 'createdAt' | 'updatedAt' | 'done' | 'completedAt'> &
    Partial<Pick<ProjectStage, 'done'>>
): Promise<ProjectStage> {
  const now = new Date().toISOString();
  const stage: ProjectStage = {
    ...input,
    done: input.done ?? false,
    completedAt: input.done ? now : null,
    id: createId('stg'),
    createdAt: now,
    updatedAt: now,
  };
  await db.projectStages.put(stage);
  await logEvent('stage.created', 'project', stage.projectId, {
    level: 2,
    data: { stageId: stage.id },
  });
  return stage;
}

export async function updateStage(
  id: string,
  patch: Partial<Omit<ProjectStage, 'id' | 'projectId' | 'createdAt'>>
): Promise<ProjectStage | undefined> {
  const existing = await db.projectStages.get(id);
  if (!existing) return undefined;
  const done = patch.done ?? existing.done;
  const next: ProjectStage = {
    ...existing,
    ...patch,
    done,
    completedAt: done ? existing.completedAt ?? new Date().toISOString() : null,
    updatedAt: new Date().toISOString(),
  };
  await db.projectStages.put(next);
  const toggled = done !== existing.done;
  await pushUndo({
    kind: toggled ? 'stage.toggle' : 'stage.update',
    entity: 'project',
    // The row itself is the restoration target, so the id is the stage's.
    entityId: id,
    label: toggled ? 'تیک مرحله' : 'ویرایش مرحله',
    before: existing,
    after: next,
  });
  await logEvent(toggled ? 'stage.toggled' : 'stage.created', 'project', next.projectId, {
    level: 2,
    data: { stageId: id, done },
  });
  return next;
}

export async function toggleStage(id: string): Promise<ProjectStage | undefined> {
  const existing = await db.projectStages.get(id);
  if (!existing) return undefined;
  return updateStage(id, { done: !existing.done });
}

export async function deleteStage(id: string): Promise<void> {
  const existing = await db.projectStages.get(id);
  await db.projectStages.delete(id);
  if (existing) {
    await logEvent('stage.deleted', 'project', existing.projectId, {
      level: 3,
      data: { stageId: id },
    });
  }
}

// ==================== BACKUP / RESTORE / WIPE ====================

export async function exportBackup(): Promise<KairosBackupPayload> {
  const [tasks, habits, habitLogs, focusSessions, goals, settings, journal, projects, projectStages] =
    await Promise.all([
      db.tasks.toArray(),
      db.habits.toArray(),
      db.habitLogs.toArray(),
      db.focusSessions.toArray(),
      db.goals.toArray(),
      ensureSettings(),
      db.journal.toArray(),
      db.projects.toArray(),
      db.projectStages.toArray(),
    ]);
  return {
    // v3 is the first format that carries projects; restore stays backward
    // compatible, so an old file is still accepted with the same version check.
    version: 3,
    exportedAt: new Date().toISOString(),
    app: 'KAIROS',
    tasks,
    habits,
    habitLogs,
    focusSessions,
    goals,
    // The API key is a device secret, not user data: a backup is a file the user
    // may hand to someone else, so the key never enters it.
    settings: { ...settings, aiKey: '' },
    journal,
    projects,
    projectStages,
  };
}

export interface BackupImportResult {
  added: number;
  skipped: number;
}

/** Restore missing records only. Existing rows and settings are never overwritten. */
export async function importBackup(payload: KairosBackupPayload): Promise<BackupImportResult> {
  validateBackup(payload);
  let added = 0;
  let skipped = 0;
  async function addMissing<T extends { id: string }>(table: Table<T, string>, rows?: T[]) {
    if (!rows?.length) return;
    const existing = await table.bulkGet(rows.map((row) => row.id));
    const missing = rows.filter((_, index) => existing[index] === undefined);
    if (missing.length) await table.bulkAdd(missing);
    added += missing.length;
    skipped += rows.length - missing.length;
  }
  const sampleEligibleRows = [...payload.tasks, ...payload.habits, ...payload.habitLogs, ...payload.focusSessions, ...payload.goals, ...(payload.projects ?? []), ...(payload.projectStages ?? [])];
  // Old backups have no per-row provenance but can carry the legacy global flag.
  // Preserve the warning conservatively; never relabel an existing row by guessing.
  const containsSamples = sampleEligibleRows.some((row) => row.sample === true) ||
    (sampleEligibleRows.length > 0 && payload.settings?.hasSampleData === true);
  await db.transaction(
    'rw',
    [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.journal, db.projects, db.projectStages, db.settings],
    async () => {
      await addMissing(db.tasks, payload.tasks);
      await addMissing(db.habits, payload.habits);
      await addMissing(db.habitLogs, payload.habitLogs);
      await addMissing(db.focusSessions, payload.focusSessions);
      await addMissing(db.goals, payload.goals);
      await addMissing(db.journal, payload.journal);
      await addMissing(db.projects, payload.projects);
      await addMissing(db.projectStages, payload.projectStages);
      if (containsSamples) {
        const current = await db.settings.get('default');
        if (!current || !current.hasSampleData) await db.settings.put({ ...DEFAULT_SETTINGS, ...current, id: 'default', hasSampleData: true });
      }
    }
  );
  await logEvent('data.imported', 'system', 'backup', {
    level: 3,
    data: { added, skipped },
  });
  return { added, skipped };
}

export async function clearAllUserData(): Promise<void> {
  const current = await ensureSettings();
  await db.transaction(
    'rw',
    [
      db.tasks,
      db.habits,
      db.habitLogs,
      db.focusSessions,
      db.goals,
      db.settings,
      db.journal,
      db.projects,
      db.projectStages,
    ],
    async () => {
      await Promise.all([
        db.tasks.clear(),
        db.habits.clear(),
        db.habitLogs.clear(),
        db.focusSessions.clear(),
        db.goals.clear(),
        db.journal.clear(),
        db.projects.clear(),
        db.projectStages.clear(),
      ]);
      await db.settings.put({
        ...current,
        hasSampleData: false,
        sampleBannerDismissed: true,
        updatedAt: new Date().toISOString(),
      });
    }
  );
  await logEvent('data.cleared', 'system', 'all', { level: 3 });
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
  await pushUndo({
    kind: 'journal.save',
    entity: 'journal',
    entityId: id,
    label: existing ? 'ویرایش ژورنال' : 'نوشتن ژورنال',
    before: existing ?? null,
    after: entry,
  });
  // Overwriting an existing day is level 3 in the spec; the first write is not.
  await logEvent('journal.saved', 'journal', id, {
    level: existing ? 3 : 2,
    data: { dateKey, mood: entry.mood, isOverwrite: Boolean(existing) },
  });
  return entry;
}

export async function deleteJournalEntry(dateKey: string): Promise<void> {
  const id = `journal-${dateKey}`;
  await db.journal.delete(id);
  await logEvent('journal.deleted', 'journal', id, { level: 3, data: { dateKey } });
}
