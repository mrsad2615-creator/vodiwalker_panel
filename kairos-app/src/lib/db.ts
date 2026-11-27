import Dexie, { type Table } from 'dexie';
import { DEFAULT_APPEARANCE } from '@/lib/theme';
import type { ClockSegment } from '@/lib/chrono/plan';
import type { PomodoroPhase } from '@/lib/focusTimer';
import type {
  AssistantHistoryEntry,
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
import { BACKUP_COLLECTIONS, validateBackup } from './backupValidation';
import { restorableSettings } from './backupSettings';
import { createId } from './format';
import type { Authority, EventEntity, EventKind, EventSource, KairosEvent } from './events';
import type { PermissionGrant } from './permissions';
import type { UndoEntry } from './undo';

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
  widgetShowTitles: true,
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
  assistantHistory!: Table<AssistantHistoryEntry, string>;

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

    // New table only; existing rows and all previous schemas stay untouched.
    this.version(6).stores({ assistantHistory: 'id, createdAt' });
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
  /** Missing on older saved sessions; migrated conservatively during read. */
  recordedFocusSeconds?: number;
  finished?: boolean;
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

async function ensureSettingsInTransaction(): Promise<UserSettings> {
  const existing = await db.settings.get('default');
  if (existing) {
    const merged: UserSettings = { ...DEFAULT_SETTINGS, ...existing, id: 'default' };
    const missing = (Object.keys(DEFAULT_SETTINGS) as (keyof UserSettings)[]).some((key) => existing[key] === undefined);
    if (missing) await db.settings.put(merged);
    return merged;
  }
  const fresh: UserSettings = { ...DEFAULT_SETTINGS, updatedAt: new Date().toISOString() };
  await db.settings.add(fresh);
  return fresh;
}

export async function ensureSettings(): Promise<UserSettings> {
  return db.transaction('rw', db.settings, ensureSettingsInTransaction);
}

export async function updateSettings(patch: Partial<Omit<UserSettings, 'id'>>): Promise<UserSettings> {
  const next = await db.transaction('rw', db.settings, async () => {
    const current = await ensureSettingsInTransaction();
    const value: UserSettings = { ...current, ...patch, id: 'default', updatedAt: new Date().toISOString() };
    await db.settings.put(value);
    return value;
  });
  await logEvent('settings.updated', 'settings', 'default', {
    level: 2, data: { keys: Object.keys(patch).join(',').slice(0, 200) },
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

function pruneTaskLedgerOccasionally(): void {
  if ((writeCounter += 1) % 64 !== 0) return;
  void import('./undo').then(({ pruneUndo }) => pruneUndo()).catch(() => undefined);
  void import('./events').then(({ pruneEvents }) => pruneEvents()).catch(() => undefined);
}

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

/** Caller must include undoHistory in its write transaction. */
async function appendUndoInTransaction(entry: Pick<UndoEntry, 'kind' | 'entity' | 'entityId' | 'label' | 'before' | 'after'>): Promise<void> {
  const latest = await db.undoHistory.orderBy('at').last();
  const previous = latest ? Date.parse(latest.at) : NaN;
  const at = new Date(Math.max(Date.now(), Number.isFinite(previous) ? previous + 1 : 0)).toISOString();
  await db.undoHistory.add({ id: createId('und'), at, undoneAt: null, ...entry });
}

// ==================== TASKS ====================

function nextOccurrence(task: Task, dueDate: string, now: string): Task {
  return {
    ...task,
    id: createId('tsk'), dueDate, status: 'todo',
    subtasks: task.subtasks.map((subtask) => ({ ...subtask, id: createId('sub'), completed: false })),
    tags: [...task.tags], dependsOn: task.dependsOn ? [...task.dependsOn] : undefined,
    archived: false, postponeCount: 0, createdAt: now, updatedAt: now, completedAt: null, completedDate: null,
  };
}

export async function createTask(
  input: Omit<Task, 'id' | 'createdAt' | 'updatedAt' | 'completedAt' | 'completedDate' | 'postponeCount' | 'archived'>
): Promise<Task> {
  const now = new Date().toISOString();
  const isDone = input.status === 'completed';
  const id = createId('tsk');
  const task: Task = {
    ...input,
    id,
    ...(input.recurrence !== 'none' ? { recurrenceSeriesId: input.recurrenceSeriesId || id } : {}),
    archived: false,
    postponeCount: 0,
    createdAt: now,
    updatedAt: now,
    completedAt: isDone ? now : null,
    completedDate: isDone ? getTodayKey() : null,
  };
  const recurrence = task.recurrence;
  if (isDone && recurrence !== 'none') {
    // Creating an already-completed recurring task is still a completion; both
    // occurrences must commit together or neither is written.
    await db.transaction('rw', [db.tasks, db.eventHistory], async () => {
      const nextDate = computeNextRecurrenceDate(task.dueDate ?? getTodayKey(), recurrence);
      const candidates = await db.tasks.where('dueDate').equals(nextDate).toArray();
      const duplicate = candidates.some((candidate) => candidate.recurrenceSeriesId === task.recurrenceSeriesId &&
        candidate.recurrence === task.recurrence && candidate.status !== 'completed' && !candidate.archived);
      await db.tasks.add(task);
      await db.eventHistory.add({ id: createId('evt'), at: now, kind: 'task.created', entity: 'task', entityId: task.id,
        source: 'user', level: 1, data: { status: task.status, hasDueDate: task.dueDate !== null, quadrant: task.quadrant } });
      if (!duplicate) {
        const next = nextOccurrence(task, nextDate, now);
        await db.tasks.add(next);
        await db.eventHistory.add({ id: createId('evt'), at: now, kind: 'task.created', entity: 'task', entityId: next.id,
          source: 'user', level: 1, data: { status: next.status, hasDueDate: true, quadrant: next.quadrant } });
      }
    });
    pruneTaskLedgerOccasionally();
  } else {
    await db.tasks.put(task);
    await logEvent('task.created', 'task', task.id, {
      data: { status: task.status, hasDueDate: task.dueDate !== null, quadrant: task.quadrant },
    });
  }
  return task;
}

export async function updateTask(
  id: string,
  patch: Partial<Omit<Task, 'id' | 'createdAt'>>
): Promise<Task | undefined> {
  // A single transaction prevents simultaneous updates from reading the same
  // old row and guarantees the undo snapshot belongs to the committed change.
  const result = await db.transaction('rw', [db.tasks, db.undoHistory, db.eventHistory], async () => {
    const existing = await db.tasks.get(id);
    if (!existing) return undefined;
    if (patch.status !== undefined && patch.status !== existing.status && existing.recurrence !== 'none') {
      throw new Error('recurring-status-requires-setTaskStatus');
    }
    const now = new Date().toISOString();
    const postponed = Boolean(patch.dueDate && existing.dueDate && patch.dueDate > existing.dueDate && existing.status !== 'completed');
    const statusChanged = patch.status !== undefined && patch.status !== existing.status;
    const next: Task = {
      ...existing,
      ...patch,
      ...(existing.recurrence === 'none' && patch.recurrence && patch.recurrence !== 'none' && !existing.recurrenceSeriesId
        ? { recurrenceSeriesId: existing.id } : {}),
      postponeCount: existing.postponeCount + (postponed ? 1 : 0),
      completedAt: statusChanged ? (patch.status === 'completed' ? now : null) : existing.completedAt,
      completedDate: statusChanged ? (patch.status === 'completed' ? getTodayKey() : null) : existing.completedDate,
      updatedAt: now,
    };
    await db.tasks.put(next);
    const latest = await db.undoHistory.orderBy('at').last();
    const previousAt = latest ? Date.parse(latest.at) : NaN;
    const at = new Date(Math.max(Date.now(), Number.isFinite(previousAt) ? previousAt + 1 : 0)).toISOString();
    await db.undoHistory.add({
      id: createId('und'), at, kind: statusChanged ? 'task.status' : 'task.update', entity: 'task', entityId: id,
      label: statusChanged ? 'تغییر وضعیت وظیفه' : 'ویرایش وظیفه', before: existing, after: next, undoneAt: null,
    });
    await db.eventHistory.add({ id: createId('evt'), at: now, kind: 'task.updated', entity: 'task', entityId: id, source: 'user', level: 2 });
    if (postponed) await db.eventHistory.add({
      id: createId('evt'), at: now, kind: 'task.postponed', entity: 'task', entityId: id,
      source: 'user', level: 2, data: { count: next.postponeCount },
    });
    if (statusChanged) await db.eventHistory.add({
      id: createId('evt'), at: now, kind: 'task.status', entity: 'task', entityId: id,
      source: 'user', level: 2, data: { from: existing.status, to: next.status },
    });
    return next;
  });
  if (result) pruneTaskLedgerOccasionally();
  return result;
}

/**
 * Status, next occurrence and undo metadata commit together. New series use
 * stable identity; old rows without an identity retain the legacy title/date
 * heuristic because automatically joining old records could lose user data.
 */
type TaskEditPatch = Partial<Omit<Task, 'id' | 'createdAt' | 'status' | 'completedAt' | 'completedDate'>>;

export async function setTaskStatus(
  id: string,
  newStatus: TaskStatus,
  options?: { expected: Task; patch: TaskEditPatch }
): Promise<{ task?: Task; spawnedNext?: Task }> {
  const result = await db.transaction('rw', [db.tasks, db.undoHistory, db.eventHistory], async () => {
    const existing = await db.tasks.get(id);
    if (!existing) {
      if (options) throw new Error('task-edit-conflict');
      return {};
    }
    if (options && JSON.stringify(existing) !== JSON.stringify(options.expected)) throw new Error('task-edit-conflict');
    const statusChanged = existing.status !== newStatus;
    if (!statusChanged && !options) return { task: existing };

    const now = new Date().toISOString();
    const today = getTodayKey();
    const patch = options?.patch ?? {};
    const postponed = Boolean(patch.dueDate && existing.dueDate && patch.dueDate > existing.dueDate && existing.status !== 'completed');
    const updated: Task = {
      ...existing,
      ...patch,
      ...(existing.recurrence === 'none' && patch.recurrence && patch.recurrence !== 'none' && !existing.recurrenceSeriesId
        ? { recurrenceSeriesId: existing.id } : {}),
      status: newStatus,
      postponeCount: existing.postponeCount + (postponed ? 1 : 0),
      completedAt: statusChanged ? (newStatus === 'completed' ? now : null) : existing.completedAt,
      completedDate: statusChanged ? (newStatus === 'completed' ? today : null) : existing.completedDate,
      updatedAt: now,
    };
    const recurringCompletion = statusChanged && newStatus === 'completed' && updated.recurrence !== 'none';
    let spawnedNext: Task | undefined;
    if (recurringCompletion && updated.recurrence !== 'none') {
      const nextDate = computeNextRecurrenceDate(updated.dueDate ?? today, updated.recurrence);
      const candidates = await db.tasks.where('dueDate').equals(nextDate).toArray();
      const duplicate = candidates.find((candidate) => candidate.id !== id &&
        (updated.recurrenceSeriesId
          ? candidate.recurrenceSeriesId === updated.recurrenceSeriesId
          : candidate.title.trim() === updated.title.trim()) &&
        candidate.recurrence === updated.recurrence && candidate.status !== 'completed' && !candidate.archived);
      if (!duplicate) {
        spawnedNext = nextOccurrence(updated, nextDate, now);
        await db.tasks.add(spawnedNext);
      }
    }
    await db.tasks.put(updated);
    const latest = await db.undoHistory.orderBy('at').last();
    const previousAt = latest ? Date.parse(latest.at) : NaN;
    const at = new Date(Math.max(Date.now(), Number.isFinite(previousAt) ? previousAt + 1 : 0)).toISOString();
    await db.undoHistory.add({
      id: createId('und'), at, kind: statusChanged ? 'task.status' : 'task.update', entity: 'task', entityId: id,
      label: statusChanged ? 'تغییر وضعیت وظیفه' : 'ویرایش وظیفه', before: existing, after: updated, undoneAt: null,
      ...(recurringCompletion ? { recurrenceEffect: { version: 1 as const, spawned: spawnedNext ?? null } } : {}),
    });
    if (options) await db.eventHistory.add({
      id: createId('evt'), at: now, kind: 'task.updated', entity: 'task', entityId: id, source: 'user', level: 2,
    });
    if (postponed) await db.eventHistory.add({
      id: createId('evt'), at: now, kind: 'task.postponed', entity: 'task', entityId: id, source: 'user', level: 2,
      data: { count: updated.postponeCount },
    });
    if (statusChanged) await db.eventHistory.add({
      id: createId('evt'), at: now, kind: 'task.status', entity: 'task', entityId: id,
      source: 'user', level: 2, data: { from: existing.status, to: newStatus },
    });
    if (spawnedNext) {
      await db.eventHistory.add({
        id: createId('evt'), at: now, kind: 'task.created', entity: 'task', entityId: spawnedNext.id,
        source: 'user', level: 1, data: { status: 'todo', hasDueDate: true, quadrant: spawnedNext.quadrant },
      });
    }
    return { task: updated, spawnedNext };
  });
  if (result.task) pruneTaskLedgerOccasionally();
  return result;
}

export async function archiveTask(id: string, archived: boolean): Promise<void> {
  await updateTask(id, { archived });
  await logEvent('task.archived', 'task', id, { level: 2, data: { archived } });
}

export async function deleteTask(id: string, expected?: Task): Promise<void> {
  await db.transaction('rw', [db.tasks, db.undoHistory, db.migrationLog, db.eventHistory], async () => {
    const current = await db.tasks.get(id);
    if (!current || (expected && JSON.stringify(current) !== JSON.stringify(expected))) throw new Error('task-delete-conflict');
    await db.tasks.delete(id);
    await db.undoHistory.where('entityId').equals(id).and((row) => row.entity === 'task').delete();
    // v5 migration snapshots can contain an older copy of the deleted title.
    const migrations = await db.migrationLog.toArray();
    for (const migration of migrations) {
      if (!Array.isArray(migration.snapshot?.tasks)) continue;
      const tasks = migration.snapshot.tasks.filter((row) => !(row && typeof row === 'object' && 'id' in row && row.id === id));
      if (tasks.length !== migration.snapshot.tasks.length) {
        await db.migrationLog.put({ ...migration, snapshot: { ...migration.snapshot, tasks } });
      }
    }
    await db.eventHistory.add({ id: createId('evt'), at: new Date().toISOString(), kind: 'task.deleted',
      entity: 'task', entityId: id, source: 'user', level: 3 });
  });
  pruneTaskLedgerOccasionally();
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
  patch: Partial<Omit<Habit, 'id' | 'createdAt'>>,
  expected?: Habit
): Promise<Habit | undefined> {
  const result = await db.transaction('rw', [db.habits, db.undoHistory, db.eventHistory], async () => {
    const existing = await db.habits.get(id);
    if (expected && JSON.stringify(existing) !== JSON.stringify(expected)) throw new Error('habit-edit-conflict');
    if (!existing) return undefined;
    const next: Habit = { ...existing, ...patch, updatedAt: new Date().toISOString() };
    await db.habits.put(next);
    await appendUndoInTransaction({ kind: 'habit.update', entity: 'habit', entityId: id,
      label: 'ویرایش عادت', before: existing, after: next });
    await db.eventHistory.add({ id: createId('evt'), at: next.updatedAt, kind: 'habit.updated', entity: 'habit', entityId: id, source: 'user', level: 2 });
    return next;
  });
  if (result) pruneTaskLedgerOccasionally();
  return result;
}

export async function deleteHabit(id: string, expected?: Habit): Promise<void> {
  await db.transaction('rw', [db.habits, db.habitLogs, db.undoHistory], async () => {
    const current = await db.habits.get(id);
    if (!current || (expected && JSON.stringify(current) !== JSON.stringify(expected))) throw new Error('habit-delete-conflict');
    const logs = await db.habitLogs.where('habitId').equals(id).primaryKeys();
    await db.habits.delete(id);
    await db.habitLogs.bulkDelete(logs);
    await db.undoHistory.filter((row) => {
      if (row.entity === 'habit') return row.entityId === id;
      if (row.entity !== 'habitLog') return false;
      const before = row.before as { habitId?: unknown } | null;
      const after = row.after as { habitId?: unknown } | null;
      return before?.habitId === id || after?.habitId === id;
    }).delete();
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
  note?: string,
  expected?: { habit: Habit; log: HabitLog | null }
): Promise<HabitLog> {
  const result = await db.transaction('rw', [db.habits, db.habitLogs, db.undoHistory, db.eventHistory], async () => {
    const habit = await db.habits.get(habitId);
    if (!habit) throw new Error('habit-not-found');
    const now = new Date().toISOString();
    const log: HabitLog = { sample: habit.sample, id: makeHabitLogId(habitId, dateKey), habitId, dateKey, status, value, note, updatedAt: now };
    const previous = await db.habitLogs.get(log.id);
    if (expected && (JSON.stringify(habit) !== JSON.stringify(expected.habit) || JSON.stringify(previous ?? null) !== JSON.stringify(expected.log))) throw new Error('habit-log-conflict');
    await db.habitLogs.put(log);
    const latest = await db.undoHistory.orderBy('at').last();
    const at = new Date(Math.max(Date.now(), latest ? Date.parse(latest.at) + 1 || 0 : 0)).toISOString();
    await db.undoHistory.add({ id: createId('und'), at, kind: 'habit.log', entity: 'habitLog', entityId: log.id,
      label: 'ثبت عادت', before: previous ?? null, after: log, undoneAt: null });
    await db.eventHistory.add({ id: createId('evt'), at: now, kind: 'habit.logged', entity: 'habitLog', entityId: log.id,
      source: 'user', level: 2, data: { habitId, dateKey, status, value } });
    return log;
  });
  pruneTaskLedgerOccasionally();
  return result;
}

/** Clear one day's log and record a conflict-safe undo in the same transaction. */
export async function clearHabitLog(habitId: string, dateKey: string, expected?: { habit: Habit; log: HabitLog | null }): Promise<void> {
  await db.transaction('rw', [db.habits, db.habitLogs, db.undoHistory, db.eventHistory], async () => {
    const id = makeHabitLogId(habitId, dateKey);
    const habit = await db.habits.get(habitId);
    if (!habit) throw new Error('habit-not-found');
    const previous = await db.habitLogs.get(id);
    if (expected && (JSON.stringify(habit) !== JSON.stringify(expected.habit) || JSON.stringify(previous ?? null) !== JSON.stringify(expected.log))) throw new Error('habit-log-conflict');
    await db.habitLogs.delete(id);
    if (previous) {
      const latest = await db.undoHistory.orderBy('at').last();
      const at = new Date(Math.max(Date.now(), latest ? Date.parse(latest.at) + 1 || 0 : 0)).toISOString();
      await db.undoHistory.add({ id: createId('und'), at, kind: 'habit.log', entity: 'habitLog', entityId: id,
        label: 'برداشتن ثبت عادت', before: previous, after: null, undoneAt: null });
    }
    await db.eventHistory.add({ id: createId('evt'), at: new Date().toISOString(), kind: 'habit.unlogged',
      entity: 'habitLog', entityId: id, source: 'user', level: 2, data: { habitId, dateKey } });
  });
  pruneTaskLedgerOccasionally();
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

export async function deleteFocusSession(id: string, expected?: FocusSession): Promise<void> {
  await db.transaction('rw', db.focusSessions, async () => {
    const current = await db.focusSessions.get(id);
    if (!current || (expected && JSON.stringify(current) !== JSON.stringify(expected))) throw new Error('focus-delete-conflict');
    await db.focusSessions.delete(id);
  });
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
  patch: Partial<Omit<Goal, 'id' | 'createdAt'>>,
  expected?: Goal
): Promise<Goal | undefined> {
  const result = await db.transaction('rw', [db.goals, db.undoHistory, db.eventHistory], async () => {
    const existing = await db.goals.get(id);
    if (expected && JSON.stringify(existing) !== JSON.stringify(expected)) throw new Error('goal-edit-conflict');
    if (!existing) return undefined;
    const currentValue = patch.currentValue ?? existing.currentValue;
    const targetValue = patch.targetValue ?? existing.targetValue;
    const completed = patch.completed !== undefined ? patch.completed : targetValue > 0 && currentValue >= targetValue;
    const next: Goal = { ...existing, ...patch, currentValue, targetValue, completed, updatedAt: new Date().toISOString() };
    await db.goals.put(next);
    await appendUndoInTransaction({ kind: 'goal.update', entity: 'goal', entityId: id, label: 'ویرایش هدف', before: existing, after: next });
    await db.eventHistory.add({ id: createId('evt'), at: next.updatedAt, kind: 'goal.updated', entity: 'goal', entityId: id,
      source: 'user', level: 2, data: { current: next.currentValue, target: next.targetValue, completed: next.completed } });
    return next;
  });
  if (result) pruneTaskLedgerOccasionally();
  return result;
}

export async function deleteGoal(id: string, expected?: Goal): Promise<void> {
  await db.transaction('rw', [db.goals, db.undoHistory, db.migrationLog], async () => {
    const current = await db.goals.get(id);
    if (!current || (expected && JSON.stringify(current) !== JSON.stringify(expected))) throw new Error('goal-delete-conflict');
    await db.goals.delete(id);
    await db.undoHistory.where('entityId').equals(id).and((row) => row.entity === 'goal').delete();
    for (const migration of await db.migrationLog.toArray()) {
      if (!Array.isArray(migration.snapshot?.goals)) continue;
      const goals = migration.snapshot.goals.filter((row) => !(row && typeof row === 'object' && 'id' in row && row.id === id));
      if (goals.length !== migration.snapshot.goals.length) await db.migrationLog.put({ ...migration, snapshot: { ...migration.snapshot, goals } });
    }
  });
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
  patch: Partial<Omit<Project, 'id' | 'createdAt'>>,
  expected?: Project
): Promise<Project | undefined> {
  const result = await db.transaction('rw', [db.projects, db.undoHistory, db.eventHistory], async () => {
    const existing = await db.projects.get(id);
    if (expected && JSON.stringify(existing) !== JSON.stringify(expected)) throw new Error('project-edit-conflict');
    if (!existing) return undefined;
    const next: Project = { ...existing, ...patch, updatedAt: new Date().toISOString() };
    const statusChanged = patch.status !== undefined && patch.status !== existing.status;
    await db.projects.put(next);
    await appendUndoInTransaction({ kind: statusChanged ? 'project.status' : 'project.update', entity: 'project', entityId: id,
      label: statusChanged ? 'تغییر وضعیت پروژه' : 'ویرایش پروژه', before: existing, after: next });
    await db.eventHistory.add({ id: createId('evt'), at: next.updatedAt, kind: statusChanged ? 'project.archived' : 'project.updated',
      entity: 'project', entityId: id, source: 'user', level: 2,
      ...(statusChanged ? { data: { from: existing.status, to: next.status } } : {}) });
    return next;
  });
  if (result) pruneTaskLedgerOccasionally();
  return result;
}

export async function setProjectStatus(id: string, status: Project['status']): Promise<void> {
  await updateProject(id, { status });
}

export async function deleteProject(id: string, expected?: Project): Promise<void> {
  await db.transaction('rw', [db.projects, db.projectStages, db.tasks, db.undoHistory], async () => {
    const current = await db.projects.get(id);
    if (!current || (expected && JSON.stringify(current) !== JSON.stringify(expected))) throw new Error('project-delete-conflict');
    const stages = await db.projectStages.where('projectId').equals(id).primaryKeys();
    await db.undoHistory.where('entityId').anyOf([id, ...stages]).and((row) => row.entity === 'project').delete();
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
  await db.transaction('rw', [db.projects, db.projectStages], async () => {
    if (!await db.projects.get(stage.projectId)) throw new Error('project-not-found');
    await db.projectStages.add(stage);
  });
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
  const result = await db.transaction('rw', [db.projectStages, db.undoHistory, db.eventHistory], async () => {
    const existing = await db.projectStages.get(id);
    if (!existing) return undefined;
    const done = patch.done ?? existing.done;
    const next: ProjectStage = { ...existing, ...patch, done, completedAt: done ? existing.completedAt ?? new Date().toISOString() : null, updatedAt: new Date().toISOString() };
    const toggled = done !== existing.done;
    await db.projectStages.put(next);
    await appendUndoInTransaction({ kind: toggled ? 'stage.toggle' : 'stage.update', entity: 'project', entityId: id,
      label: toggled ? 'تیک مرحله' : 'ویرایش مرحله', before: existing, after: next });
    await db.eventHistory.add({ id: createId('evt'), at: next.updatedAt, kind: toggled ? 'stage.toggled' : 'stage.created',
      entity: 'project', entityId: next.projectId, source: 'user', level: 2, data: { stageId: id, done } });
    return next;
  });
  if (result) pruneTaskLedgerOccasionally();
  return result;
}

export async function toggleStage(id: string): Promise<ProjectStage | undefined> {
  const result = await db.transaction('rw', [db.projectStages, db.undoHistory, db.eventHistory], async () => {
    const existing = await db.projectStages.get(id);
    if (!existing) return undefined;
    const now = new Date().toISOString();
    const next: ProjectStage = { ...existing, done: !existing.done, completedAt: existing.done ? null : now, updatedAt: now };
    await db.projectStages.put(next);
    await appendUndoInTransaction({ kind: 'stage.toggle', entity: 'project', entityId: id, label: 'تیک مرحله', before: existing, after: next });
    await db.eventHistory.add({ id: createId('evt'), at: now, kind: 'stage.toggled', entity: 'project', entityId: next.projectId,
      source: 'user', level: 2, data: { stageId: id, done: next.done } });
    return next;
  });
  if (result) pruneTaskLedgerOccasionally();
  return result;
}

export async function deleteStage(id: string, expected?: ProjectStage): Promise<void> {
  const existing = await db.transaction('rw', [db.projectStages, db.undoHistory], async () => {
    const current = await db.projectStages.get(id);
    if (!current || (expected && JSON.stringify(current) !== JSON.stringify(expected))) throw new Error('stage-delete-conflict');
    await db.projectStages.delete(id);
    await db.undoHistory.where('entityId').equals(id).and((row) => row.entity === 'project').delete();
    return current;
  });
  if (existing) {
    await logEvent('stage.deleted', 'project', existing.projectId, {
      level: 3,
      data: { stageId: id },
    });
  }
}

// ==================== BACKUP / RESTORE / WIPE ====================

export async function exportBackup(): Promise<KairosBackupPayload> {
  // Initialise before opening the readonly transaction; all exported tables
  // must be read in one snapshot so concurrent edits cannot mix two states.
  await ensureSettings();
  const [tasks, habits, habitLogs, focusSessions, goals, settingsRow, journal, projects, projectStages, assistantHistory] =
    await db.transaction('r', [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings, db.journal, db.projects, db.projectStages, db.assistantHistory],
      () => Promise.all([
        db.tasks.toArray(),
        db.habits.toArray(),
        db.habitLogs.toArray(),
        db.focusSessions.toArray(),
        db.goals.toArray(),
        db.settings.get('default'),
        db.journal.toArray(),
        db.projects.toArray(),
        db.projectStages.toArray(),
        db.assistantHistory.toArray(),
      ]));
  const settings = settingsRow ?? { ...DEFAULT_SETTINGS };
  return {
    // v4 adds assistant history. Older v1–v3 files remain readable.
    version: 4,
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
    assistantHistory,
  };
}

export interface BackupImportResult {
  added: number;
  skipped: number;
}

/** Point-in-time estimate only; import repeats the checks inside its write transaction. */
export async function inspectBackup(payload: KairosBackupPayload): Promise<BackupImportResult> {
  validateBackup(payload);
  return db.transaction('r', BACKUP_COLLECTIONS.map((name) => db.table(name)), async () => {
    let added = 0;
    let skipped = 0;
    for (const name of BACKUP_COLLECTIONS) {
      const rows = payload[name] ?? [];
      const existing = await db.table(name).bulkGet(rows.map((row) => row.id));
      const present = existing.filter((row) => row !== undefined).length;
      skipped += present;
      added += rows.length - present;
    }
    return { added, skipped };
  });
}

/** Merge missing records. Personal preferences are replaced only by explicit opt-in. */
export async function importBackup(payload: KairosBackupPayload, options?: { restorePreferences?: boolean }): Promise<BackupImportResult> {
  validateBackup(payload);
  const preferences = options?.restorePreferences ? restorableSettings(payload.settings) : undefined;
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
    [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.journal, db.projects, db.projectStages, db.assistantHistory, db.settings, db.activeRun],
    async () => {
      if (preferences && await db.activeRun.get('active')) throw new Error('focus-session-active');
      await addMissing(db.tasks, payload.tasks);
      await addMissing(db.habits, payload.habits);
      await addMissing(db.habitLogs, payload.habitLogs);
      await addMissing(db.focusSessions, payload.focusSessions);
      await addMissing(db.goals, payload.goals);
      await addMissing(db.journal, payload.journal);
      await addMissing(db.projects, payload.projects);
      await addMissing(db.projectStages, payload.projectStages);
      await addMissing(db.assistantHistory, payload.assistantHistory);
      if (containsSamples || preferences) {
        const current = await db.settings.get('default');
        await db.settings.put({
          ...DEFAULT_SETTINGS, ...current, ...preferences, id: 'default',
          hasSampleData: containsSamples || current?.hasSampleData === true,
          updatedAt: new Date().toISOString(),
        });
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
      db.activeRun,
      db.eventHistory,
      db.undoHistory,
      db.permissionGrants,
      db.migrationLog,
      db.assistantHistory,
    ],
    async () => {
      const current = await ensureSettingsInTransaction();
      await Promise.all([
        db.tasks.clear(),
        db.habits.clear(),
        db.habitLogs.clear(),
        db.focusSessions.clear(),
        db.goals.clear(),
        db.journal.clear(),
        db.projects.clear(),
        db.projectStages.clear(),
        db.activeRun.clear(),
        db.eventHistory.clear(),
        db.undoHistory.clear(),
        db.permissionGrants.clear(),
        db.migrationLog.clear(),
        db.assistantHistory.clear(),
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
  patch: { mood?: number; note?: string; highlight?: string },
  expected?: JournalEntry | null
): Promise<JournalEntry> {
  const entry = await db.transaction('rw', [db.journal, db.undoHistory, db.eventHistory], async () => {
    const id = `journal-${dateKey}`;
    const existing = await db.journal.get(id);
    if (expected !== undefined && JSON.stringify(existing ?? null) !== JSON.stringify(expected)) throw new Error('journal-edit-conflict');
    const now = new Date().toISOString();
    const next: JournalEntry = {
      id, dateKey, mood: patch.mood ?? existing?.mood ?? 3,
      note: patch.note ?? existing?.note ?? '', highlight: patch.highlight ?? existing?.highlight ?? '',
      createdAt: existing?.createdAt ?? now, updatedAt: now,
    };
    await db.journal.put(next);
    await appendUndoInTransaction({ kind: 'journal.save', entity: 'journal', entityId: id,
      label: existing ? 'ویرایش ژورنال' : 'نوشتن ژورنال', before: existing ?? null, after: next });
    await db.eventHistory.add({ id: createId('evt'), at: now, kind: 'journal.saved', entity: 'journal', entityId: id,
      source: 'user', level: existing ? 3 : 2, data: { dateKey, mood: next.mood, isOverwrite: Boolean(existing) } });
    return next;
  });
  pruneTaskLedgerOccasionally();
  return entry;
}

export async function deleteJournalEntry(dateKey: string, expected?: JournalEntry): Promise<void> {
  const id = `journal-${dateKey}`;
  await db.transaction('rw', [db.journal, db.undoHistory], async () => {
    const current = await db.journal.get(id);
    if (!current || (expected && JSON.stringify(current) !== JSON.stringify(expected))) throw new Error('journal-delete-conflict');
    await db.journal.delete(id);
    await db.undoHistory.where('entityId').equals(id).and((row) => row.entity === 'journal').delete();
  });
  await logEvent('journal.deleted', 'journal', id, { level: 3, data: { dateKey } });
}
