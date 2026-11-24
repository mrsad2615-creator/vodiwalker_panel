import { describe, expect, it } from 'vitest';
import { notificationText } from '@/lib/chrono/native';
import type { FocusClockState } from '@/lib/focusTimer';

const base: FocusClockState = {
  mode: 'free', phase: 'focus', isRunning: true, focusMinutes: 25,
  shortBreakMinutes: 5, longBreakMinutes: 15, remainingSeconds: 0,
  elapsedSeconds: 73, completedCycles: 0, linkedTaskId: null,
  sessionStartedAt: null, anchorMs: null, anchorRemaining: 0, anchorElapsed: 73,
  plan: [{ kind: 'focus', label: 'زمان‌سنج آزاد', seconds: 0 }], segmentIndex: 0,
};

describe('native timer mirror payload', () => {
  it('sends elapsed time for the open-ended clock without a fake zero-minute subtitle', () => {
    const payload = notificationText(base);
    expect(payload.totalSeconds).toBe(0);
    expect(payload.elapsedSeconds).toBe(73);
    expect(payload.subtitle).toBe('بدون محدودیت زمانی');
  });
  it('preserves countdown duration and remaining time', () => {
    const payload = notificationText({ ...base, mode: 'countdown', remainingSeconds: 30,
      plan: [{ kind: 'focus', label: 'تمرکز', seconds: 120 }] });
    expect(payload.totalSeconds).toBe(120);
    expect(payload.remainingSeconds).toBe(30);
  });
});
