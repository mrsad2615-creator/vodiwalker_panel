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
import {
  focusSecondsNow,
  pauseClock,
  startClock,
  tickClock,
  type FocusClockState,
} from '@/lib/focusTimer';

export type PomodoroPhase = 'focus' | 'short_break' | 'long_break';

export interface TaskModalRequest {
  open: boolean;
  task?: Task | null;
  defaultDueDate?: string | null;
  defaultQuadrant?: EisenhowerQuadrant;
}

export type FocusState = FocusClockState;

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
    anchorMs: null,
    anchorRemaining: 25 * 60,
    anchorElapsed: 0,
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

  // ── Focus Timer ────────────────────────────────────────────────────────────
  // Anchored to absolute time (see lib/focusTimer.ts). Every tick asks the same
  // question — "how many whole seconds since anchorMs?" — so the countdown can
  // never run fast, can never drift, and catches up exactly after the WebView
  // was frozen. State is only replaced when a visible value changes, which means
  // one render per second instead of four.
  const focusRef = useRef(focus);
  focusRef.current = focus;

  const syncFocusClock = useCallback(() => {
    const prev = focusRef.current;
    if (!prev.isRunning) return;

    const result = tickClock(prev, Date.now(), settingsRef.current.pomodoroCyclesBeforeLong);
    if (result.state === prev) return;

    focusRef.current = result.state;
    setFocus(result.state);
    if (!result.completedPhase) return;

    const s = settingsRef.current;
    playLuxuryChime(s.soundEnabled);
    void triggerHaptic('success', s.hapticsEnabled);

    if (result.completedPhase === 'focus') {
      const totalFocused = result.completedFocusSeconds;
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
  }, [commitFocusRecord, pushToast]);

  useEffect(() => {
    if (!focus.isRunning) return;
    // 250 ms only to hit the second boundary promptly; the render cost is one
    // update per second because tickClock returns the same state otherwise.
    const interval = window.setInterval(syncFocusClock, 250);
    const onVisible = () => {
      if (document.visibilityState === 'visible') syncFocusClock();
    };
    const onAppState = () => syncFocusClock();
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('focus', onAppState);
    window.addEventListener('pageshow', onAppState);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('focus', onAppState);
      window.removeEventListener('pageshow', onAppState);
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
      anchorMs: null,
      anchorRemaining: prev.focusMinutes * 60,
      anchorElapsed: 0,
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
          // Re-anchor so a duration change made while paused is what the clock
          // counts down from.
          anchorRemaining: prev.isRunning ? prev.anchorRemaining : nextRemaining,
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
    setFocus((prev) => startClock(prev, Date.now()));
  }, []);

  const pauseFocus = useCallback(() => {
    void triggerHaptic('light', settingsRef.current.hapticsEnabled);
    setFocus((prev) => pauseClock(prev, Date.now()));
  }, []);

  const resetFocus = useCallback(() => {
    setFocus((prev) => {
      const nextRemaining =
        prev.phase === 'focus'
          ? prev.focusMinutes * 60
          : prev.phase === 'short_break'
          ? prev.shortBreakMinutes * 60
          : prev.longBreakMinutes * 60;
      return {
        ...prev,
        isRunning: false,
        remainingSeconds: nextRemaining,
        elapsedSeconds: 0,
        sessionStartedAt: null,
        anchorMs: null,
        anchorRemaining: nextRemaining,
        anchorElapsed: 0,
      };
    });
  }, []);

  const finishAndSaveFocus = useCallback(async () => {
    const snap = focus;
    if (snap.phase !== 'focus' && snap.mode === 'pomodoro') {
      resetFocus();
      return;
    }
    // Read the live value: the anchor may be several seconds ahead of the last
    // rendered state (the UI only re-renders once per second).
    const liveElapsed = focusSecondsNow(snap, Date.now());
    if (liveElapsed < 60) {
      pushToast({
        title: 'حداقل 1 دقیقه تمرکز نیاز است',
        description: 'جلسات کمتر از یک دقیقه در تاریخچه ثبت نمی‌شوند.',
        tone: 'warning',
      });
      return;
    }
    const saved = await commitFocusRecord(
      snap.mode,
      liveElapsed,
      snap.linkedTaskId,
      snap.sessionStartedAt,
      false
    );
    if (saved) {
      playLuxuryChime(settingsRef.current.soundEnabled);
      void triggerHaptic('success', settingsRef.current.hapticsEnabled);
      const mins = Math.max(1, Math.round(liveElapsed / 60));
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
      anchorMs: null,
      anchorRemaining: prev.focusMinutes * 60,
      anchorElapsed: 0,
    }));
  }, [focus, commitFocusRecord, pushToast, resetFocus]);

  const skipPomodoroPhase = useCallback(() => {
    setFocus((prev) => {
      const nextPhase: PomodoroPhase = prev.phase === 'focus' ? 'short_break' : 'focus';
      const nextRemaining =
        nextPhase === 'focus' ? prev.focusMinutes * 60 : prev.shortBreakMinutes * 60;
      return {
        ...prev,
        isRunning: false,
        phase: nextPhase,
        remainingSeconds: nextRemaining,
        anchorMs: null,
        anchorRemaining: nextRemaining,
        anchorElapsed: 0,
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
