import type { KairosBackupPayload } from '@/types/domain';

/** Supported historical JSON backup formats, independent of Dexie's schema version. */
const REQUIRED = ['tasks', 'habits', 'habitLogs', 'focusSessions', 'goals'] as const;
const OPTIONAL = ['journal', 'projects', 'projectStages'] as const;
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
};
const BOOLEAN_FIELDS: Record<BackupCollection, readonly string[]> = {
  tasks: ['reminderEnabled', 'archived'], habits: ['archived'], habitLogs: [],
  focusSessions: ['completedNaturally'], goals: ['completed'], journal: [], projects: [], projectStages: ['done'],
};

/**
 * Reject incompatible or structurally unsafe files before any DB transaction.
 * This is a conservative structural check, not cryptographic authenticity or
 * complete semantic validation. Historical optional collections remain optional.
 */
export function validateBackup(value: unknown): asserts value is KairosBackupPayload {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid-backup');
  const data = value as Record<string, unknown>;
  if (data.app !== 'KAIROS' || !Number.isInteger(data.version) || ![1, 2, 3].includes(data.version as number)) {
    throw new Error('invalid-backup');
  }
  for (const name of BACKUP_COLLECTIONS) {
    const rows = data[name];
    if (rows === undefined && (OPTIONAL as readonly string[]).includes(name)) continue;
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
      if (name === 'tasks') {
        if (!Array.isArray(row.tags) || row.tags.some((tag: unknown) => typeof tag !== 'string') ||
            !Array.isArray(row.subtasks) || row.subtasks.some((item: unknown) => {
              if (!item || typeof item !== 'object' || Array.isArray(item)) return true;
              const subtask = item as Record<string, unknown>;
              return typeof subtask.id !== 'string' || typeof subtask.title !== 'string' || typeof subtask.completed !== 'boolean';
            }) || !['todo', 'in_progress', 'completed'].includes(row.status as string)) throw new Error('invalid-backup');
      }
      if (name === 'habits' && (!Array.isArray(row.scheduledDays) || row.scheduledDays.some((day: unknown) => !Number.isInteger(day) || (day as number) < 0 || (day as number) > 6))) throw new Error('invalid-backup');
      if (name === 'habitLogs' && !['completed', 'partial', 'missed'].includes(row.status as string)) throw new Error('invalid-backup');
      if (name === 'projects' && !['active', 'paused', 'done', 'archived'].includes(row.status as string)) throw new Error('invalid-backup');
      if (name === 'projectStages' && !['stage', 'milestone'].includes(row.kind as string)) throw new Error('invalid-backup');
    }
  }
}
