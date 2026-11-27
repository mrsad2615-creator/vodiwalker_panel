import type { KairosBackupPayload } from '@/types/domain';

/** Supported historical JSON backup formats, independent of Dexie's schema version. */
const REQUIRED = ['tasks', 'habits', 'habitLogs', 'focusSessions', 'goals'] as const;
const OPTIONAL = ['journal', 'projects', 'projectStages', 'assistantHistory'] as const;
export const BACKUP_COLLECTIONS = [...REQUIRED, ...OPTIONAL] as const;
export type BackupCollection = (typeof BACKUP_COLLECTIONS)[number];

const REQUIRED_STRINGS: Record<BackupCollection, readonly string[]> = {
  tasks: ['id', 'title', 'status'],
  habits: ['id', 'name', 'scheduleType', 'trackingMode'],
  habitLogs: ['id', 'habitId', 'dateKey', 'status'],
  focusSessions: ['id', 'mode', 'dateKey', 'startedAt', 'endedAt'],
  goals: ['id', 'title'],
  journal: ['id', 'dateKey', 'note', 'highlight'],
  projects: ['id', 'title', 'status'],
  projectStages: ['id', 'projectId', 'title', 'kind'],
  assistantHistory: ['id', 'createdAt', 'intent', 'title'],
};

const STRING_FIELDS: Record<BackupCollection, readonly string[]> = {
  tasks: ['description', 'priority', 'quadrant', 'category', 'recurrence', 'createdAt', 'updatedAt'],
  habits: ['description', 'icon', 'accentColor', 'unit', 'startDate', 'createdAt', 'updatedAt'],
  habitLogs: ['updatedAt'],
  focusSessions: [],
  goals: ['description', 'category', 'unit', 'createdAt', 'updatedAt'],
  journal: ['createdAt', 'updatedAt'],
  projects: ['description', 'category', 'createdAt', 'updatedAt'],
  projectStages: ['createdAt', 'updatedAt'],
  assistantHistory: [],
};
const NUMBER_FIELDS: Record<BackupCollection, readonly string[]> = {
  tasks: ['reminderOffsetMin', 'postponeCount'],
  habits: ['weeklyTarget', 'targetValue'],
  habitLogs: ['value'],
  focusSessions: ['durationMinutes'],
  goals: ['currentValue', 'targetValue'],
  journal: ['mood'],
  projects: [],
  projectStages: ['order', 'weight'],
  assistantHistory: [],
};
const BOOLEAN_FIELDS: Record<BackupCollection, readonly string[]> = {
  tasks: ['reminderEnabled', 'archived'], habits: ['archived'], habitLogs: [],
  focusSessions: ['completedNaturally'], goals: ['completed'], journal: [], projects: [], projectStages: ['done'], assistantHistory: ['sample'],
};

const CATEGORIES = ['work', 'personal', 'health', 'learning', 'finance', 'creative'];
const DATE_FIELDS: Record<BackupCollection, readonly string[]> = {
  tasks: ['dueDate', 'completedDate'], habits: ['startDate'], habitLogs: ['dateKey'],
  focusSessions: ['dateKey'], goals: ['deadline'], journal: ['dateKey'],
  projects: ['startDate', 'dueDate'], projectStages: ['dueDate'], assistantHistory: [],
};
function validDay(value: unknown): boolean {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return year >= 1 && month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1];
}
function validOptionalTime(value: unknown): boolean {
  return value === undefined || value === null || (typeof value === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(value));
}

/**
 * Reject incompatible or structurally unsafe files before any DB transaction.
 * This is a conservative structural check, not cryptographic authenticity or
 * complete semantic validation. Historical optional collections remain optional.
 */
export function validateBackup(value: unknown): asserts value is KairosBackupPayload {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid-backup');
  const data = value as Record<string, unknown>;
  if (data.app !== 'KAIROS' || !Number.isInteger(data.version) || ![1, 2, 3, 4].includes(data.version as number)) {
    throw new Error('invalid-backup');
  }
  for (const name of BACKUP_COLLECTIONS) {
    const rows = data[name];
    if (rows === undefined && (OPTIONAL as readonly string[]).includes(name) && !(name === 'assistantHistory' && data.version === 4)) continue;
    if (!Array.isArray(rows)) throw new Error('invalid-backup');
    const ids = new Set<string>();
    for (const value of rows) {
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid-backup');
      const row = value as Record<string, unknown>;
      if (REQUIRED_STRINGS[name].some((key) =>
        typeof row[key] !== 'string' || (key !== 'note' && key !== 'highlight' && !(row[key] as string).trim())
      )) throw new Error('invalid-backup');
      if (STRING_FIELDS[name].some((key) => typeof row[key] !== 'string') ||
          NUMBER_FIELDS[name].some((key) => typeof row[key] !== 'number' || !Number.isFinite(row[key])) ||
          BOOLEAN_FIELDS[name].some((key) => typeof row[key] !== 'boolean')) throw new Error('invalid-backup');
      const id = row.id as string;
      if (ids.has(id)) throw new Error('invalid-backup');
      ids.add(id);
      if (row.sample !== undefined && typeof row.sample !== 'boolean') throw new Error('invalid-backup');
      if (DATE_FIELDS[name].some((field) => row[field] !== undefined && row[field] !== null && !validDay(row[field]))) throw new Error('invalid-backup');
      if (['tasks', 'goals', 'projects'].includes(name) && !CATEGORIES.includes(row.category as string)) throw new Error('invalid-backup');
      if (name === 'tasks') {
        const refs = [row.goalId, row.projectId];
        if (!Array.isArray(row.tags) || row.tags.some((tag: unknown) => typeof tag !== 'string') ||
            !Array.isArray(row.subtasks) || row.subtasks.some((item: unknown) => {
              if (!item || typeof item !== 'object' || Array.isArray(item)) return true;
              const subtask = item as Record<string, unknown>;
              return typeof subtask.id !== 'string' || typeof subtask.title !== 'string' || typeof subtask.completed !== 'boolean';
            }) || !['todo', 'in_progress', 'completed'].includes(row.status as string) ||
            !['high', 'medium', 'low'].includes(row.priority as string) ||
            !['q1', 'q2', 'q3', 'q4'].includes(row.quadrant as string) ||
            !['none', 'daily', 'weekdays', 'weekly', 'monthly'].includes(row.recurrence as string) ||
            !validOptionalTime(row.dueTime) ||
            (row.recurrenceSeriesId !== undefined && (typeof row.recurrenceSeriesId !== 'string' || !row.recurrenceSeriesId.trim())) ||
            refs.some((ref) => ref !== undefined && ref !== null && typeof ref !== 'string') ||
            (row.dependsOn !== undefined && (!Array.isArray(row.dependsOn) || row.dependsOn.some((ref: unknown) => typeof ref !== 'string'))) ||
            (row.estimateMin !== undefined && row.estimateMin !== null && (typeof row.estimateMin !== 'number' || !Number.isFinite(row.estimateMin)))) throw new Error('invalid-backup');
      }
      if (name === 'habits' && (!Array.isArray(row.scheduledDays) || row.scheduledDays.some((day: unknown) => !Number.isInteger(day) || (day as number) < 0 || (day as number) > 6) ||
        !['daily', 'specific_days', 'weekly_target'].includes(row.scheduleType as string) ||
        !['boolean', 'quantitative'].includes(row.trackingMode as string) || !validOptionalTime(row.reminderTime))) throw new Error('invalid-backup');
      if (name === 'focusSessions' && !['pomodoro', 'free', 'interval', 'countdown'].includes(row.mode as string)) throw new Error('invalid-backup');
      if (name === 'habitLogs' && !['completed', 'partial', 'missed'].includes(row.status as string)) throw new Error('invalid-backup');
      if (name === 'projects' && !['active', 'paused', 'done', 'archived'].includes(row.status as string)) throw new Error('invalid-backup');
      if (name === 'projectStages' && !['stage', 'milestone'].includes(row.kind as string)) throw new Error('invalid-backup');
      if (name === 'assistantHistory') {
        const action = row.action;
        if (!Number.isFinite(Date.parse(row.createdAt as string)) ||
            (row.prompt !== undefined && typeof row.prompt !== 'string') ||
            !Array.isArray(row.lines) || row.lines.some((line: unknown) => typeof line !== 'string') ||
            (row.unknown !== undefined && typeof row.unknown !== 'boolean') ||
            (action !== undefined && (!action || typeof action !== 'object' || Array.isArray(action) ||
              typeof (action as Record<string, unknown>).label !== 'string' ||
              !['dashboard', 'tasks', 'projects', 'focus', 'analytics', 'habits'].includes((action as Record<string, unknown>).tab as string)))) throw new Error('invalid-backup');
      }
    }
  }
}
