import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type {
  EisenhowerQuadrant,
  FocusModeType,
  NavTab,
  Task,
  ToastMessage,
  UserSettings,
} from '@/types/domain';
import { Capacitor } from '@capacitor/core';
import { ensureSettings, recordFocusSession } from '@/lib/db';
import { useSettings, useTasks } from '@/lib/queries';
import { getTodayKey } from '@/lib/date';
import { applyAppearance, cacheAppearance, normalizeAppearance } from '@/lib/theme';
import { createId } from '@/lib/format';
import { initNativeShell, triggerHaptic } from '@/lib/native';
import {
  fireInstantNotification,
  getNotificationPermission,
  getTaskReminderDate,
  playLuxuryChime,
  requestNotificationPermission,
  syncTaskRemindersNative,
} from '@/lib/notifications';
import { seedStarterWorkspaceIfEmpty } from '@/lib/seed';
import { advanceFocusClock, focusSecondsAtCompletion } from '@/lib/focusTimer';

export type PomodoroPhase = 'focus' | 'short_break' | 'long_break';

export interface TaskModalRequest {
  open: boolean;
  task?: Task | null;
  defaultDueDate?: string | null;
  defaultQuadrant?: EisenhowerQuadrant;
}

export interface FocusState {
  mode: FocusModeType;
  phase: PomodoroPhase;
  isRunning: boolean;
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  remainingSeconds: number;
  elapsedSeconds: number;
  completedCycles: number;
  linkedTaskId: string | null;
  sessionStartedAt: string | null;
}

interface AppContextValue {
  ready: boolean;
  activeTab: NavTab;
  setActiveTab: (tab: NavTab) => void;
  settings: UserSettings;
  toasts: ToastMessage[];
  pushToast: (toast: Omit<ToastMessage, 'id'>) => void;
  dismissToast: (id: string) => void;
  taskModal: TaskModalRequest;
  openCreateTask: (opts?: {
    defaultDueDate?: string | null;
    defaultQuadrant?: EisenhowerQuadrant;
  }) => void;
  openEditTask: (task: Task) => void;
  closeTaskModal: () => void;
  // Persistent Focus Timer API
  focus: FocusState;
  setFocusMode: (mode: FocusModeType) => void;
  setFocusDurations: (focusMin: number, shortMin: number, longMin: number) => void;
  setFocusLinkedTask: (taskId: string | null) => void;
  startOrResumeFocus: () => void;
  pauseFocus: () => void;
  resetFocus: () => void;
  finishAndSaveFocus: () => Promise<void>;
  skipPomodoroPhase: () => void;
}

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [activeTab, setActiveTab] = useState<NavTab>('dashboard');
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [taskModal, setTaskModal] = useState<TaskModalRequest>({ open: false });

  const settings = useSettings();
  const allTasks = useTasks();
  const firedRemindersRef = useRef<Set<string>>(new Set());

  const [focus, setFocus] = useState<FocusState>({
    mode: 'pomodoro',
    phase: 'focus',
    isRunning: false,
    focusMinutes: 25,
    shortBreakMinutes: 5,
    longBreakMinutes: 15,
    remainingSeconds: 25 * 60,
    elapsedSeconds: 0,
    completedCycles: 0,
    linkedTaskId: null,
    sessionStartedAt: null,
  });

  const pushToast = useCallback((toast: Omit<ToastMessage, 'id'>) => {
    const id = createId('tst');
    setToasts((prev) => [{ ...toast, id }, ...prev].slice(0, 3));
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4200);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // Bootstrap DB, starter workspace (only on very first run), and Capacitor native shell
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        await initNativeShell();
        await seedStarterWorkspaceIfEmpty();
        const s = await ensureSettings();
        if (active) {
          setFocus((prev) => ({
            ...prev,
            focusMinutes: s.pomodoroFocusMin,
            shortBreakMinutes: s.pomodoroShortBreakMin,
            longBreakMinutes: s.pomodoroLongBreakMin,
            remainingSeconds: s.pomodoroFocusMin * 60,
          }));
          setReady(true);
        }
      } catch {
        if (active) setReady(true);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  // Appearance: accent, environment and motion level are tokens on :root.
  // Applying them here keeps the DB row as the single source of truth while the
  // synchronous cache (main.tsx) guarantees a flash-free next boot.
  useEffect(() => {
    const appearance = normalizeAppearance({
      accent: settings.accentColor,
      backgroundId: settings.backgroundId,
      motionLevel: settings.motionLevel,
    });
    applyAppearance(appearance);
    cacheAppearance(appearance);
  }, [settings.accentColor, settings.backgroundId, settings.motionLevel]);

  // Sync Native Android LocalNotifications whenever tasks or notification toggle change
  useEffect(() => {
    if (!ready) return;
    void syncTaskRemindersNative(allTasks, settings.notificationsEnabled);
  }, [ready, allTasks, settings.notificationsEnabled]);

  // Real-time Web/PWA Reminder Scheduler (fires in-app luxury reminder + browser Notification)
  useEffect(() => {
    if (!ready || !settings.notificationsEnabled) return;

    const checkDueReminders = () => {
      const nowMs = Date.now();
      for (const task of allTasks) {
        const at = getTaskReminderDate(task);
        if (!at) continue;
        const delta = nowMs - at.getTime();
        // Fire if within the past 60 seconds and not yet fired
        const key = `${task.id}::${at.getTime()}`;
        if (delta >= 0 && delta < 60_000 && !firedRemindersRef.current.has(key)) {
          firedRemindersRef.current.add(key);
          void fireInstantNotification(
            `یادآوری کایروس • ${task.title}`,
            task.description || 'زمان انجام این وظیفه فرا رسیده است.',
            { soundEnabled: settings.soundEnabled }
          );
          pushToast({
            title: `یادآوری: ${task.title}`,
            description: task.dueTime ? `ساعت ${task.dueTime}` : 'زمان انجام فرا رسیده است',
            tone: 'reminder',
          });
        }
      }
    };

    checkDueReminders();
    const timer = window.setInterval(checkDueReminders, 20_000);
    return () => window.clearInterval(timer);
  }, [ready, allTasks, settings.notificationsEnabled, settings.soundEnabled, pushToast]);

  // Ref for latest settings & tasks inside interval callback
  const settingsRef = useRef(settings);
  settingsRef.current = settings;
  const tasksRef = useRef(allTasks);
  tasksRef.current = allTasks;

  const commitFocusRecord = useCallback(
    async (
      mode: FocusModeType,
      elapsedSeconds: number,
      linkedTaskId: string | null,
      startedAt: string | null,
      completedNaturally: boolean
    ) => {
      if (elapsedSeconds < 60) return false;
      const linked = linkedTaskId
        ? tasksRef.current.find((t) => t.id === linkedTaskId)
        : undefined;
      const mins = Math.max(1, Math.round(elapsedSeconds / 60));
      await recordFocusSession({
        mode,
        durationMinutes: mins,
        durationSeconds: elapsedSeconds,
        dateKey: getTodayKey(),
        taskId: linked?.id ?? null,
        taskTitleSnapshot: linked?.title ?? null,
        completedNaturally,
        startedAt: startedAt ?? new Date(Date.now() - elapsedSeconds * 1000).toISOString(),
        endedAt: new Date().toISOString(),
      });
      return true;
    },
    []
  );

  // First-run permission ask. Reminders default to ON, so the app has to hold
  // the actual system permission — one prompt, once per install, never nagging.
  useEffect(() => {
    if (!ready || !settings.notificationsEnabled) return;
    if (!Capacitor.isNativePlatform()) return;
    const askedKey = 'kairos:notif-permission-asked';
    try {
      if (window.localStorage.getItem(askedKey)) return;
    } catch {
      return;
    }
    void (async () => {
      const state = await getNotificationPermission();
      if (state === 'prompt') {
        const granted = await requestNotificationPermission();
        if (granted === 'granted') {
          pushToast({
            title: 'یادآوری‌ها فعال شد',
            description: 'کایروس می‌تواند سر ساعت برای وظایف شما اعلان بفرستد.',
            tone: 'success',
          });
        }
      }
      try {
        window.localStorage.setItem(askedKey, new Date().toISOString());
      } catch {
        // Private mode — the prompt simply appears again next launch.
      }
    })();
  }, [ready, settings.notificationsEnabled, pushToast]);

  // Focus Timer — wall-clock driven.
  // The previous implementation added exactly one second per interval tick, so a
  // locked screen, a throttled WebView or a dropped frame silently stretched or
  // shrank the session. We now measure the real elapsed time between ticks and
  // advance by that, which also catches up instantly after the app resumes.
  const focusRef = useRef(focus);
  focusRef.current = focus;
  const lastFocusTickRef = useRef<number>(Date.now());

  const advanceFocus = useCallback(
    (deltaSeconds: number) => {
      const prev = focusRef.current;
      if (!prev.isRunning || deltaSeconds <= 0) return;

      const { state: next, completedPhase } = advanceFocusClock(
        prev,
        deltaSeconds,
        settingsRef.current.pomodoroCyclesBeforeLong
      );
      if (next === prev) return;

      focusRef.current = next;
      setFocus(next);
      if (!completedPhase) return;

      const s = settingsRef.current;
      playLuxuryChime(s.soundEnabled);
      void triggerHaptic('success', s.hapticsEnabled);

      if (completedPhase === 'focus') {
        const totalFocused = focusSecondsAtCompletion(prev, deltaSeconds);
        void commitFocusRecord(
          'pomodoro',
          totalFocused,
          prev.linkedTaskId,
          prev.sessionStartedAt,
          true
        );
        void fireInstantNotification(
          'جلسه تمرکز با موفقیت پایان یافت',
          `${Math.max(1, Math.round(totalFocused / 60))} دقیقه تمرکز در آمار شما ثبت شد.`,
          { soundEnabled: false }
        );
        pushToast({
          title: 'جلسه تمرکز ثبت شد',
          description: `${Math.max(1, Math.round(totalFocused / 60))} دقیقه به آمار امروز اضافه شد. اکنون زمان استراحت است.`,
          tone: 'success',
        });
        return;
      }

      pushToast({
        title: 'زمان استراحت به پایان رسید',
        description: 'آماده شروع چرخه جدید تمرکز هستید.',
        tone: 'default',
      });
    },
    [commitFocusRecord, pushToast]
  );

  const syncFocusClock = useCallback(() => {
    const now = Date.now();
    const deltaMs = now - lastFocusTickRef.current;
    lastFocusTickRef.current = now;
    if (deltaMs < 220) return; // ignore jitter, keep at least ~1s resolution
    advanceFocus(Math.max(1, Math.round(deltaMs / 1000)));
  }, [advanceFocus]);

  useEffect(() => {
    if (!focus.isRunning) return;
    lastFocusTickRef.current = Date.now();
    const interval = window.setInterval(syncFocusClock, 250);
    const onVisible = () => {
      if (document.visibilityState === 'visible') syncFocusClock();
    };
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('focus', syncFocusClock);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('focus', syncFocusClock);
    };
  }, [focus.isRunning, syncFocusClock]);

  const setFocusMode = useCallback((mode: FocusModeType) => {
    setFocus((prev) => ({
      ...prev,
      mode,
      isRunning: false,
      phase: 'focus',
      remainingSeconds: prev.focusMinutes * 60,
      elapsedSeconds: 0,
      sessionStartedAt: null,
    }));
  }, []);

  const setFocusDurations = useCallback(
    (focusMin: number, shortMin: number, longMin: number) => {
      setFocus((prev) => {
        const nextRemaining =
          prev.phase === 'focus'
            ? focusMin * 60
            : prev.phase === 'short_break'
            ? shortMin * 60
            : longMin * 60;
        return {
          ...prev,
          focusMinutes: focusMin,
          shortBreakMinutes: shortMin,
          longBreakMinutes: longMin,
          remainingSeconds: prev.isRunning ? prev.remainingSeconds : nextRemaining,
        };
      });
    },
    []
  );

  const setFocusLinkedTask = useCallback((taskId: string | null) => {
    setFocus((prev) => ({ ...prev, linkedTaskId: taskId }));
  }, []);

  const startOrResumeFocus = useCallback(() => {
    void triggerHaptic('light', settingsRef.current.hapticsEnabled);
    setFocus((prev) => ({
      ...prev,
      isRunning: true,
      sessionStartedAt: prev.sessionStartedAt ?? new Date().toISOString(),
    }));
  }, []);

  const pauseFocus = useCallback(() => {
    void triggerHaptic('light', settingsRef.current.hapticsEnabled);
    setFocus((prev) => ({ ...prev, isRunning: false }));
  }, []);

  const resetFocus = useCallback(() => {
    setFocus((prev) => ({
      ...prev,
      isRunning: false,
      remainingSeconds:
        prev.phase === 'focus'
          ? prev.focusMinutes * 60
          : prev.phase === 'short_break'
          ? prev.shortBreakMinutes * 60
          : prev.longBreakMinutes * 60,
      elapsedSeconds: 0,
      sessionStartedAt: null,
    }));
  }, []);

  const finishAndSaveFocus = useCallback(async () => {
    const snap = focus;
    if (snap.phase !== 'focus' && snap.mode === 'pomodoro') {
      resetFocus();
      return;
    }
    if (snap.elapsedSeconds < 60) {
      pushToast({
        title: 'حداقل 1 دقیقه تمرکز نیاز است',
        description: 'جلسات کمتر از یک دقیقه در تاریخچه ثبت نمی‌شوند.',
        tone: 'warning',
      });
      return;
    }
    const saved = await commitFocusRecord(
      snap.mode,
      snap.elapsedSeconds,
      snap.linkedTaskId,
      snap.sessionStartedAt,
      false
    );
    if (saved) {
      playLuxuryChime(settingsRef.current.soundEnabled);
      void triggerHaptic('success', settingsRef.current.hapticsEnabled);
      const mins = Math.max(1, Math.round(snap.elapsedSeconds / 60));
      pushToast({
        title: 'جلسه تمرکز ذخیره شد',
        description: `${mins} دقیقه تمرکز در گزارش‌های شما ثبت گردید.`,
        tone: 'success',
      });
    }
    setFocus((prev) => ({
      ...prev,
      isRunning: false,
      phase: 'focus',
      remainingSeconds: prev.focusMinutes * 60,
      elapsedSeconds: 0,
      sessionStartedAt: null,
    }));
  }, [focus, commitFocusRecord, pushToast, resetFocus]);

  const skipPomodoroPhase = useCallback(() => {
    setFocus((prev) => {
      const nextPhase: PomodoroPhase = prev.phase === 'focus' ? 'short_break' : 'focus';
      return {
        ...prev,
        isRunning: false,
        phase: nextPhase,
        remainingSeconds:
          nextPhase === 'focus' ? prev.focusMinutes * 60 : prev.shortBreakMinutes * 60,
        elapsedSeconds: 0,
        sessionStartedAt: null,
      };
    });
  }, []);

  const openCreateTask = useCallback(
    (opts?: { defaultDueDate?: string | null; defaultQuadrant?: EisenhowerQuadrant }) => {
      setTaskModal({
        open: true,
        task: null,
        defaultDueDate: opts?.defaultDueDate !== undefined ? opts.defaultDueDate : getTodayKey(),
        defaultQuadrant: opts?.defaultQuadrant ?? 'q2',
      });
    },
    []
  );

  const openEditTask = useCallback((task: Task) => {
    setTaskModal({ open: true, task });
  }, []);

  const closeTaskModal = useCallback(() => {
    setTaskModal({ open: false, task: null });
  }, []);

  const value = useMemo<AppContextValue>(
    () => ({
      ready,
      activeTab,
      setActiveTab,
      settings,
      toasts,
      pushToast,
      dismissToast,
      taskModal,
      openCreateTask,
      openEditTask,
      closeTaskModal,
      focus,
      setFocusMode,
      setFocusDurations,
      setFocusLinkedTask,
      startOrResumeFocus,
      pauseFocus,
      resetFocus,
      finishAndSaveFocus,
      skipPomodoroPhase,
    }),
    [
      ready,
      activeTab,
      settings,
      toasts,
      pushToast,
      dismissToast,
      taskModal,
      openCreateTask,
      openEditTask,
      closeTaskModal,
      focus,
      setFocusMode,
      setFocusDurations,
      setFocusLinkedTask,
      startOrResumeFocus,
      pauseFocus,
      resetFocus,
      finishAndSaveFocus,
      skipPomodoroPhase,
    ]
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}
