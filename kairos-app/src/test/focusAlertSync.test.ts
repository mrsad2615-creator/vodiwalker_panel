import { describe, expect, it, vi } from 'vitest';

const native = vi.hoisted(() => ({ setAlertEnabled: vi.fn() }));
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => true, getPlatform: () => 'android' },
  registerPlugin: () => native,
}));
import { syncNativeFocusAlert } from '@/lib/chrono/native';

describe('native focus alert preference', () => {
  it('serializes rapid preference changes and continues after a failed write', async () => {
    let finish!: () => void;
    native.setAlertEnabled.mockImplementationOnce(() => new Promise<void>((resolve) => { finish = resolve; }))
      .mockRejectedValueOnce(new Error('native failure'))
      .mockResolvedValueOnce(undefined);
    const first = syncNativeFocusAlert(true);
    const second = syncNativeFocusAlert(false);
    const third = syncNativeFocusAlert(true);
    // Attach the rejection handler before resolving the first write.
    const failed = expect(second).rejects.toThrow('native failure');
    await vi.waitFor(() => expect(native.setAlertEnabled).toHaveBeenCalledTimes(1));
    finish();
    await first;
    await failed;
    await third;
    expect(native.setAlertEnabled.mock.calls).toEqual([[{ enabled: true }], [{ enabled: false }], [{ enabled: true }]]);
  });
});
