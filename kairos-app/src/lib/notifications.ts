import { Capacitor } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';
import type { Task } from '@/types/domain';
import { parseDateKey } from './date';
import { getAudioContext } from './audioContext';

function stableNotificationId(key: string): number {
  let hash = 0;
  for (let i = 0; i < key.length; i++) {
    hash = (hash * 31 + key.charCodeAt(i)) | 0;
  }
  return Math.abs(hash % 2_000_000_000) + 1;
}

export type NotificationPermissionState = 'granted' | 'denied' | 'prompt' | 'unsupported';

export async function getNotificationPermission(): Promise<NotificationPermissionState> {
  if (Capacitor.isNativePlatform()) {
    try {
      const status = await LocalNotifications.checkPermissions();
      if (status.display === 'granted') return 'granted';
      if (status.display === 'denied') return 'denied';
      return 'prompt';
    } catch {
      return 'unsupported';
    }
  }
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  if (Notification.permission === 'granted') return 'granted';
  if (Notification.permission === 'denied') return 'denied';
  return 'prompt';
}

export async function requestNotificationPermission(): Promise<NotificationPermissionState> {
  if (Capacitor.isNativePlatform()) {
    try {
      const status = await LocalNotifications.requestPermissions();
      return status.display === 'granted' ? 'granted' : 'denied';
    } catch {
      return 'unsupported';
    }
  }
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported';
  }
  try {
    const res = await Notification.requestPermission();
    return res === 'granted' ? 'granted' : res === 'denied' ? 'denied' : 'prompt';
  } catch {
    return 'unsupported';
  }
}

/**
 * The end-of-session bell.
 *
 * The 1.7 chime was two sine partials at 0.08 gain — effectively inaudible on a
 * phone speaker next to a desk fan, which is why sessions "finished silently".
 * This is a struck bell instead: a warm fundamental, a partial a fifth above, and
 * a shimmer an octave up, each with its own decay and a 70 ms stagger, so the
 * strike reads as one gesture rather than three beeps. It runs through a limiter
 * so it is loud without clipping.
 *
 * Still Web Audio, so the APK carries no licensed audio. A backgrounded WebView
 * is suspended by Android — that case belongs to the native notification
 * channel, not to this function.
 */
export function playLuxuryChime(enabled: boolean = true): void {
  if (!enabled) return;
  try {
    const ctx = getAudioContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined);

    const now = ctx.currentTime;

    const limiter = ctx.createDynamicsCompressor();
    limiter.threshold.value = -8;
    limiter.knee.value = 4;
    limiter.ratio.value = 10;
    limiter.attack.value = 0.003;
    limiter.release.value = 0.3;
    limiter.connect(ctx.destination);

    // Frequency · delay · gain · decay — a major triad, struck low to high.
    const partials: [number, number, number, number][] = [
      [264, 0, 0.34, 2.6],
      [396, 0.07, 0.24, 2.1],
      [528, 0.13, 0.17, 1.7],
      [792, 0.2, 0.1, 1.2],
    ];

    for (const [frequency, delay, level, decay] of partials) {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(frequency, now + delay);
      // A hair of detune gives the partial its struck-metal shimmer.
      osc.detune.setValueAtTime(4, now + delay);

      gain.gain.setValueAtTime(0.0001, now + delay);
      gain.gain.exponentialRampToValueAtTime(level, now + delay + 0.012);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + delay + decay);

      osc.connect(gain).connect(limiter);
      osc.start(now + delay);
      osc.stop(now + delay + decay + 0.05);
    }

    window.setTimeout(() => {
      try {
        limiter.disconnect();
      } catch {
        // already gone
      }
    }, 3600);
  } catch {
    // Audio playback is optional; a blocked context must never break a session.
  }
}

export async function fireInstantNotification(
  title: string,
  body: string,
  options?: { soundEnabled?: boolean }
): Promise<boolean> {
  playLuxuryChime(options?.soundEnabled ?? true);

  if (Capacitor.isNativePlatform()) {
    try {
      const perm = await getNotificationPermission();
      if (perm !== 'granted') return false;
      await LocalNotifications.schedule({
        notifications: [
          {
            id: stableNotificationId(`instant_${Date.now()}`),
            title,
            body,
            schedule: { at: new Date(Date.now() + 400) },
          },
        ],
      });
      return true;
    } catch {
      return false;
    }
  }

  if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
    try {
      new Notification(title, {
        body,
        icon: '/kairos-icon-192.png',
        badge: '/kairos-icon-192.png',
      });
      return true;
    } catch {
      return false;
    }
  }

  return false;
}

/**
 * Computes the exact scheduled reminder Date for a task (or null if invalid/past).
 */
export function getTaskReminderDate(task: Task): Date | null {
  if (!task.reminderEnabled || !task.dueDate || task.status === 'completed' || task.archived) {
    return null;
  }
  const base = parseDateKey(task.dueDate);
  if (task.dueTime) {
    const [hh, mm] = task.dueTime.split(':').map(Number);
    base.setHours(Number.isFinite(hh) ? hh : 9, Number.isFinite(mm) ? mm : 0, 0, 0);
  } else {
    base.setHours(9, 0, 0, 0);
  }
  const offsetMs = (task.reminderOffsetMin || 0) * 60_000;
  return new Date(base.getTime() - offsetMs);
}

/**
 * Syncs upcoming task reminders to native Android AlarmManager / LocalNotifications.
 */
export async function syncTaskRemindersNative(
  tasks: Task[],
  notificationsEnabled: boolean
): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    const pending = await LocalNotifications.getPending();
    if (pending.notifications.length > 0) {
      await LocalNotifications.cancel({ notifications: pending.notifications });
    }
    if (!notificationsEnabled) return;

    const perm = await getNotificationPermission();
    if (perm !== 'granted') return;

    const nowMs = Date.now();
    const upcoming = tasks
      .map((t) => ({ task: t, at: getTaskReminderDate(t) }))
      .filter((item): item is { task: Task; at: Date } => !!item.at && item.at.getTime() > nowMs)
      .slice(0, 48);

    if (upcoming.length === 0) return;

    await LocalNotifications.schedule({
      notifications: upcoming.map(({ task, at }) => ({
        id: stableNotificationId(task.id),
        title: `یادآوری کایروس • ${task.title}`,
        body: task.description || 'زمان انجام این وظیفه فرا رسیده است.',
        schedule: { at, allowWhileIdle: true },
        extra: { taskId: task.id },
      })),
    });
  } catch {
    // Graceful fallback
  }
}
