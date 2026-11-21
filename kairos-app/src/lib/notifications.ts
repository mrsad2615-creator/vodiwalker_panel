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
 * Plays a refined minimal harmonic bell chime using Web Audio API (no external audio files needed).
 */
export function playLuxuryChime(enabled: boolean = true): void {
  if (!enabled) return;
  try {
    // Shared singleton: creating one context per chime used to exhaust the
    // WebView's context budget and kill the sound after a few sessions.
    const ctx = getAudioContext();
    if (!ctx) return;
    const now = ctx.currentTime;

    const freqs = [528, 792];
    freqs.forEach((freq, idx) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, now + idx * 0.14);

      gain.gain.setValueAtTime(0.0001, now + idx * 0.14);
      gain.gain.exponentialRampToValueAtTime(0.08, now + idx * 0.14 + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + idx * 0.14 + 0.9);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now + idx * 0.14);
      osc.stop(now + idx * 0.14 + 0.95);
    });
  } catch {
    // Audio playback optional if blocked by browser autoplay policy
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
