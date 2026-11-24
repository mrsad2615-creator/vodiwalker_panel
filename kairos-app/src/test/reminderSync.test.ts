import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Task } from '@/types/domain';

const native = vi.hoisted(() => ({
  pending: vi.fn(), cancel: vi.fn(), schedule: vi.fn(), permission: vi.fn(),
}));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => true } }));
vi.mock('@capacitor/local-notifications', () => ({ LocalNotifications: {
  getPending: native.pending, cancel: native.cancel, schedule: native.schedule,
  checkPermissions: native.permission,
} }));
import { syncTaskRemindersNative } from '@/lib/notifications';

const task = { id: 'sample', title: 'نمونه', description: '', dueDate: '2099-01-01', dueTime: '12:00', reminderEnabled: true, reminderOffsetMin: 0, status: 'todo', archived: false } as Task;

beforeEach(() => {
  vi.clearAllMocks();
  native.pending.mockResolvedValue({ notifications: [] });
  native.cancel.mockResolvedValue(undefined);
  native.schedule.mockResolvedValue(undefined);
  native.permission.mockResolvedValue({ display: 'granted' });
});

describe('native reminder synchronisation', () => {
  it('cancels task reminders but leaves other notifications untouched', async () => {
    native.pending.mockResolvedValue({ notifications: [{ id: 1, extra: { taskId: 'sample' } }, { id: 2 }] });
    await syncTaskRemindersNative([], true);
    expect(native.cancel).toHaveBeenCalledWith({ notifications: [{ id: 1, extra: { taskId: 'sample' } }] });
    expect(native.schedule).not.toHaveBeenCalled();
  });

  it('applies a hide after an older schedule even if native calls are slow', async () => {
    let release!: () => void;
    const slow = new Promise<void>((resolve) => { release = resolve; });
    const pending: Array<{ id: number; extra?: { taskId: string } }> = [];
    native.pending.mockImplementation(async () => ({ notifications: [...pending] }));
    native.schedule.mockImplementation(async ({ notifications }) => { await slow; pending.push(...notifications); });
    native.cancel.mockImplementation(async () => { pending.length = 0; });
    const showing = syncTaskRemindersNative([task], true);
    await vi.waitFor(() => expect(native.schedule).toHaveBeenCalledTimes(1));
    const hiding = syncTaskRemindersNative([], true);
    release();
    await Promise.all([showing, hiding]);
    expect(native.cancel).toHaveBeenCalledTimes(1);
    expect(pending).toEqual([]);
  });
});
