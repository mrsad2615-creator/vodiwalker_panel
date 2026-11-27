import { webcrypto } from 'node:crypto';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { decryptBackup, encryptBackup } from '@/lib/backupCrypto';
import { readBackupFile } from '@/lib/backup';
import type { KairosBackupPayload } from '@/types/domain';

const payload = {
  version: 1, exportedAt: '2026-01-01T00:00:00.000Z', app: 'KAIROS',
  tasks: [], habits: [], habitLogs: [], focusSessions: [], goals: [],
  settings: { id: 'default', displayName: 'اطلاعات خصوصی', dailyFocusGoalMinutes: 120,
    dailyTaskGoal: 6, pomodoroFocusMin: 25, pomodoroShortBreakMin: 5,
    pomodoroLongBreakMin: 15, pomodoroCyclesBeforeLong: 4, notificationsEnabled: true,
    soundEnabled: true, hapticsEnabled: true, hasSampleData: false,
    sampleBannerDismissed: false, updatedAt: '2026-01-01T00:00:00.000Z' },
} as unknown as KairosBackupPayload;

beforeAll(() => { vi.stubGlobal('crypto', webcrypto); });
afterAll(() => { vi.unstubAllGlobals(); });

describe('encrypted backup envelope', () => {
  it('round-trips without exposing plaintext and uses random salt and nonce', async () => {
    const first = await encryptBackup(payload, 'عبارت امن و طولانی');
    const second = await encryptBackup(payload, 'عبارت امن و طولانی');
    expect(JSON.stringify(first)).not.toContain('اطلاعات خصوصی');
    expect(first.salt).not.toBe(second.salt);
    expect(first.iv).not.toBe(second.iv);
    await expect(decryptBackup(first, 'عبارت امن و طولانی')).resolves.toEqual(payload);
    const file = new File([JSON.stringify(first)], 'backup.json');
    await expect(readBackupFile(file)).rejects.toThrow('backup-password-required');
    await expect(readBackupFile(file, 'عبارت امن و طولانی')).resolves.toEqual(payload);
  });
  it('rejects incorrect passwords, modified ciphertext and authenticated headers', async () => {
    const envelope = await encryptBackup(payload, 'a long backup password');
    await expect(decryptBackup(envelope, 'wrong password')).rejects.toThrow('backup-decryption-failed');
    const binary = atob(envelope.ciphertext);
    const changed = btoa(String.fromCharCode(binary.charCodeAt(0) ^ 1) + binary.slice(1));
    await expect(decryptBackup({ ...envelope, ciphertext: changed }, 'a long backup password')).rejects.toThrow('backup-decryption-failed');
    await expect(decryptBackup({ ...envelope, iterations: envelope.iterations + 1 }, 'a long backup password')).rejects.toThrow('backup-decryption-failed');
  });
  it('rejects untrusted expensive KDF parameters and invalid envelope values', async () => {
    const envelope = await encryptBackup(payload, 'a long backup password');
    await expect(decryptBackup({ ...envelope, iterations: 999999999 }, 'a long backup password')).rejects.toThrow('invalid-encrypted-backup');
    await expect(decryptBackup({ ...envelope, iv: 'bad!' }, 'a long backup password')).rejects.toThrow('invalid-encrypted-backup');
  });
});
