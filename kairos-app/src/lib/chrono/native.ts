import { Capacitor, registerPlugin } from '@capacitor/core';
import type { FocusClockState } from '@/lib/focusTimer';
import { currentSegment, phaseDurationSeconds } from '@/lib/focusTimer';
import { CHRONO_MODES } from '@/lib/chrono/plan';

/**
 * Chrono Core — native mirror.
 *
 * On Android the running session is mirrored into a foreground-service
 * notification (see android/…/ChronoService.java) so it survives the app being
 * backgrounded and can be paused from the shade. On the web — and in tests —
 * every function here is a no-op, which keeps the clock logic platform-free.
 */

interface ChronoNotificationOptions {
  label: string;
  subtitle: string;
  remainingSeconds: number;
  totalSeconds: number;
  running: boolean;
}

export interface DndStatus {
  granted: boolean;
  active: boolean;
}

interface ChronoPlugin {
  start(options: ChronoNotificationOptions): Promise<void>;
  update(options: ChronoNotificationOptions): Promise<void>;
  stop(): Promise<void>;
  dndStatus(): Promise<DndStatus>;
  setDnd(options: { enabled: boolean }): Promise<void>;
  openDndSettings(): Promise<void>;
  addListener(
    eventName: 'control',
    listener: (data: { action: 'pause' | 'resume' | 'stop' }) => void
  ): Promise<{ remove: () => Promise<void> }>;
}

const Chrono = registerPlugin<ChronoPlugin>('Chrono');

export function chronoNativeAvailable(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

/** Human label for the shade: mode name plus the current segment. */
export function notificationText(state: FocusClockState): ChronoNotificationOptions {
  const mode = CHRONO_MODES.find((entry) => entry.id === state.mode);
  const segment = currentSegment(state);
  const minutes = Math.round(phaseDurationSeconds(state) / 60);
  return {
    label: mode ? mode.label : 'تمرکز',
    subtitle: segment
      ? `${segment.label} · ${minutes} دقیقه`
      : `${Math.max(0, Math.round(state.remainingSeconds / 60))} دقیقه`,
    remainingSeconds: Math.max(0, Math.round(state.remainingSeconds)),
    totalSeconds: Math.max(0, Math.round(phaseDurationSeconds(state))),
    running: state.isRunning,
  };
}

export async function mirrorChronoRun(state: FocusClockState): Promise<void> {
  if (!chronoNativeAvailable()) return;
  try {
    await Chrono.update(notificationText(state));
  } catch {
    // The service may not be running yet (first call) — start it instead.
    try {
      await Chrono.start(notificationText(state));
    } catch {
      // Notifications are optional.
    }
  }
}

export async function startChronoNotification(state: FocusClockState): Promise<void> {
  if (!chronoNativeAvailable()) return;
  try {
    await Chrono.start(notificationText(state));
  } catch {
    // ignore
  }
}

export async function stopChronoNotification(): Promise<void> {
  if (!chronoNativeAvailable()) return;
  try {
    await Chrono.stop();
  } catch {
    // ignore
  }
}

/**
 * Subscribes to pause/resume/stop coming from the notification shade.
 * Returns a cleanup function (no-op on web).
 */
export async function onChronoControl(
  handler: (action: 'pause' | 'resume' | 'stop' | 'complete') => void
): Promise<() => void> {
  if (!chronoNativeAvailable()) return () => {};
  try {
    const listener = await Chrono.addListener('control', (data) => handler(data.action));
    return () => {
      void listener.remove();
    };
  } catch {
    return () => {};
  }
}

/**
 * Do-not-disturb for the length of a session.
 *
 * Three honest outcomes: it worked, the user has not granted the policy access
 * (we open the system screen instead of pretending), or the platform cannot do it
 * at all. The UI shows the difference.
 */
export async function readDndStatus(): Promise<DndStatus | null> {
  if (!chronoNativeAvailable()) return null;
  try {
    return await Chrono.dndStatus();
  } catch {
    return null;
  }
}

export async function setDoNotDisturb(enabled: boolean): Promise<'ok' | 'not-granted' | 'unsupported'> {
  if (!chronoNativeAvailable()) return 'unsupported';
  try {
    await Chrono.setDnd({ enabled });
    return 'ok';
  } catch (error) {
    const message = String((error as { message?: string })?.message ?? error);
    return message.includes('not_granted') ? 'not-granted' : 'unsupported';
  }
}

export async function openDndSettings(): Promise<void> {
  if (!chronoNativeAvailable()) return;
  try {
    await Chrono.openDndSettings();
  } catch {
    // nothing else to do
  }
}
