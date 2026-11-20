import { describe, expect, it } from 'vitest';
import { buildBackupFileName, readBackupFile } from '@/lib/backup';

const payload = {
  version: 1,
  exportedAt: '2026-01-01T00:00:00.000Z',
  app: 'KAIROS' as const,
  tasks: [],
  habits: [],
  habitLogs: [],
  focusSessions: [],
  goals: [],
  settings: {
    id: 'default',
    displayName: 'کاربر',
    dailyFocusGoalMinutes: 120,
    dailyTaskGoal: 6,
    pomodoroFocusMin: 25,
    pomodoroShortBreakMin: 5,
    pomodoroLongBreakMin: 15,
    pomodoroCyclesBeforeLong: 4,
    notificationsEnabled: true,
    soundEnabled: true,
    hapticsEnabled: true,
    hasSampleData: false,
    sampleBannerDismissed: false,
    updatedAt: '2026-01-01T00:00:00.000Z',
  },
};

describe('backup files', () => {
  it('names backups by ISO date', () => {
    expect(buildBackupFileName(new Date('2026-03-04T12:00:00Z'))).toBe('kairos-backup-2026-03-04.json');
  });

  it('reads a valid backup file', async () => {
    const file = new File([JSON.stringify(payload)], 'kairos-backup.json', { type: 'application/json' });
    await expect(readBackupFile(file)).resolves.toMatchObject({ version: 1, app: 'KAIROS' });
  });

  it('rejects a file that is not a KAIROS backup', async () => {
    const file = new File(['{"hello":"world"}'], 'random.json', { type: 'application/json' });
    await expect(readBackupFile(file)).rejects.toThrow('invalid-backup');
  });
});
