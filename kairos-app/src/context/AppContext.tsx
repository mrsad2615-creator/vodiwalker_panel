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
import { db, ensureSettings, updateSettings } from '@/lib/db';
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
import {
  pauseClock,
  resolvePlan,
  seekSegment,
  startClock,
  tickClock,
  totalFocusSeconds,
  type FocusClockState,
} from '@/lib/focusTimer';
import { buildPlan, type ChronoConfig, type ChronoMode } from '@/lib/chrono/plan';
import { chronoConfigFromSettings, planForMode } from '@/lib/chrono/config';
import {
  clearActiveRun,
  hasProgress,
  loadActiveRun,
  recordFocusProgress,
  saveActiveRun,
} from '@/lib/chrono/persistence';
import {
  mirrorChronoRun,
  onChronoControl,
  openDndSettings,
  readDndStatus,
  setDoNotDisturb,
  startChronoNotification,
  stopChronoNotification,
  syncNativeFocusAlert,
} from '@/lib/chrono/native';

export type PomodoroPhase = 'focus' | 'short_break' | 'long_break';

export interface TaskModalRequest {
  open: boolean;
  task?: Task | null;
  defaultDueDate?: string | null;
  defaultQuadrant?: EisenhowerQuadrant;
}

export type FocusState = FocusClockState;

/** A session positioned at the first segment of its plan, paused. */
export function freshFocusState(mode: FocusModeType, config: ChronoConfig): FocusState {
  const plan = buildPlan(mode as ChronoMode, config);
  const first = plan[0];
  return {
    mode,
    phase: 'focus',
    isRunning: false,
    focusMinutes: config.focusMinutes,
    shortBreakMinutes: config.shortBreakMinutes,
    longBreakMinutes: config.longBreakMinutes,
    remainingSeconds: first.seconds,
    elapsedSeconds: 0,
    completedCycles: 0,
    linkedTaskId: null,
    sessionStartedAt: null,
    anchorMs: null,
    anchorRemaining: first.seconds,
    anchorElapsed: 0,
    plan,
    segmentIndex: 0,
    autoAdvance: false,
    accruedFocusSeconds: 0,
    recordedFocusSeconds: 0,
    finished: false,
  };
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
  /** Chrono Core: patch the interval/countdown defaults and rebuild the plan. */
  setChronoConfig: (patch: Partial<ChronoConfig>) => void;
  /** Chrono Core: jump to a plan segment (paused at its start). */
  seekFocusSegment: (index: number) => void;
  /** Chrono Core: run straight through the plan or stop at every boundary. */
  setAutoAdvance: (value: boolean) => void;
  /** Global search lives here, not in the shell: a shell re-render used to close
   *  the box mid-typing. */
  searchOpen: boolean;
  setSearchOpen: (open: boolean) => void;
  /** Asking for "do not disturb" is opt-in and honest about the system grant. */
  setDndInFocus: (enabled: boolean) => Promise<void>;
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
  const [searchOpen, setSearchOpen] = useState(false);
  const [taskModal, setTaskModal] = useState<TaskModalRequest>({ open: false });

  const settings = useSettings();
  const allTasks = useTasks();
  const firedRemindersRef = useRef<Set<string>>(new Set());

  const [focus, setFocus] = useState<FocusState>(() =>
    freshFocusState('pomodoro', {
      focusMinutes: 25,
      shortBreakMinutes: 5,
      longBreakMinutes: 15,
      cyclesBeforeLong: 4,
      blocks: 4,
      blockMinutes: 45,
      restMinutes: 10,
      countdownMinutes: 30,
    })
  );

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

  // Bootstrap the local database and native shell without inventing user history.
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        await initNativeShell();
        const s = await ensureSettings();
        const config = chronoConfigFromSettings(s);

        // Chrono Core: a session that was running when Android killed the app is
        // rebuilt from disk with its anchors intact. The very next tick catches up
        // on everything that happened while the page was gone.
        const restored = await loadActiveRun();
        if (!active) return;

        if (restored && hasProgress(restored)) {
          setFocus({
            ...restored,
            focusMinutes: config.focusMinutes,
            shortBreakMinutes: config.shortBreakMinutes,
            longBreakMinutes: config.longBreakMinutes,
            autoAdvance: restored.autoAdvance,
            plan: restored.plan && restored.plan.length > 0 ? restored.plan : planForMode(restored.mode, config),
          });
        } else {
          setFocus((prev) => ({
            ...freshFocusState(prev.mode === 'free' ? 'free' : 'pomodoro', config),
            linkedTaskId: prev.linkedTaskId,
            autoAdvance: s.autoAdvance,
          }));
        }
        setReady(true);
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
    void syncTaskRemindersNative(allTasks, settings.notificationsEnabled).catch(() => {
      window.dispatchEvent(new Event('kairos-reminder-sync-error'));
    });
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
  useEffect(() => {
    if (!ready) return;
    let active = true;
    // useSettings has a temporary default while Dexie loads. Re-read the stored
    // value so that a previously muted timer cannot ring during app startup.
    void db.settings.get('default').then((stored) => {
      if (active && stored) return syncNativeFocusAlert(stored.soundEnabled === true);
    }).catch(() => {
      if (active) pushToast({ title: 'تنظیم صدای تایمر همگام نشد', description: 'تنظیمات را دوباره بررسی کن؛ ممکن است صدای پایان با انتخابت مطابقت نداشته باشد.', tone: 'warning' });
    });
    return () => { active = false; };
  }, [ready, settings.soundEnabled, pushToast]);

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
      completedNaturally: boolean,
      next: FocusState | null
    ) => {
      if (elapsedSeconds < 60) return false;
      const linked = linkedTaskId
        ? tasksRef.current.find((t) => t.id === linkedTaskId)
        : undefined;
      const mins = Math.max(1, Math.round(elapsedSeconds / 60));
      await recordFocusProgress({
        mode,
        durationMinutes: mins,
        durationSeconds: elapsedSeconds,
        dateKey: getTodayKey(),
        taskId: linked?.id ?? null,
        taskTitleSnapshot: linked?.title ?? null,
        completedNaturally,
        startedAt: startedAt ?? new Date(Date.now() - elapsedSeconds * 1000).toISOString(),
        endedAt: new Date().toISOString(),
      }, next);
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
  // A completed phase and its recovery state must reach disk together. During
  // that small critical section controls cannot replace the same session.
  const focusCommitInFlight = useRef(false);
  const focusRetryAfter = useRef(0);

  const syncFocusClock = useCallback(() => {
    const prev = focusRef.current;
    if (!prev.isRunning || focusCommitInFlight.current) return;

    const result = tickClock(prev, Date.now(), settingsRef.current.pomodoroCyclesBeforeLong);
    if (result.state === prev) return;
    const s = settingsRef.current;

    // A catch-up can cross focus AND break boundaries; the last phase alone is
    // not enough to decide whether there are focus seconds to record.
    if (result.completedFocusSeconds >= 60) {
      if (Date.now() < focusRetryAfter.current) return;
      const next = {
        ...result.state,
        recordedFocusSeconds: (prev.recordedFocusSeconds ?? 0) + result.completedFocusSeconds,
      };
      focusCommitInFlight.current = true;
      void commitFocusRecord(prev.mode, result.completedFocusSeconds, prev.linkedTaskId,
        prev.sessionStartedAt, true, next).then(() => {
        focusRef.current = next;
        setFocus(next);
        if (!Capacitor.isNativePlatform()) playLuxuryChime(s.soundEnabled);
        void triggerHaptic('success', s.hapticsEnabled);
        void fireInstantNotification('جلسه تمرکز با موفقیت پایان یافت',
          `${Math.max(1, Math.round(result.completedFocusSeconds / 60))} دقیقه تمرکز در آمار شما ثبت شد.`,
          { soundEnabled: false });
        pushToast({ title: 'جلسه تمرکز ثبت شد',
          description: `${Math.max(1, Math.round(result.completedFocusSeconds / 60))} دقیقه به آمار اضافه شد.`, tone: 'success' });
        focusRetryAfter.current = 0;
      }).catch(() => {
        focusRetryAfter.current = Date.now() + 10_000;
        pushToast({ title: 'ثبت تمرکز انجام نشد', description: 'دادهٔ جلسه حفظ شده و ثبت دوباره تلاش می‌شود.', tone: 'warning' });
      }).finally(() => { focusCommitInFlight.current = false; });
      return;
    }

    focusRef.current = result.state;
    setFocus(result.state);
    if (result.completedPhase === 'break') {
      if (!Capacitor.isNativePlatform()) playLuxuryChime(s.soundEnabled);
      void triggerHaptic('success', s.hapticsEnabled);
      pushToast({ title: 'زمان استراحت به پایان رسید', description: 'آماده شروع چرخه جدید تمرکز هستید.', tone: 'default' });
    }
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

  /**
   * Switches mode by *rebuilding the plan*, never by patching a few fields: the
   * segment list is the session, so replacing it wholesale is what guarantees no
   * half-pomodoro, half-interval state can survive a tap.
   */
  const setFocusMode = useCallback((mode: FocusModeType) => {
    if (focusCommitInFlight.current) return;
    const config = chronoConfigFromSettings(settingsRef.current);
    setFocus((prev) => ({
      ...freshFocusState(mode, config),
      linkedTaskId: prev.linkedTaskId,
      autoAdvance: prev.autoAdvance,
    }));
    void clearActiveRun();
    void stopChronoNotification();
  }, []);

  const setChronoConfig = useCallback((patch: Partial<ChronoConfig>) => {
    if (focusCommitInFlight.current) return;
    const settingsPatch: Record<string, number> = {};
    if (patch.focusMinutes !== undefined) settingsPatch.pomodoroFocusMin = patch.focusMinutes;
    if (patch.shortBreakMinutes !== undefined) settingsPatch.pomodoroShortBreakMin = patch.shortBreakMinutes;
    if (patch.longBreakMinutes !== undefined) settingsPatch.pomodoroLongBreakMin = patch.longBreakMinutes;
    if (patch.cyclesBeforeLong !== undefined) settingsPatch.pomodoroCyclesBeforeLong = patch.cyclesBeforeLong;
    if (patch.blocks !== undefined) settingsPatch.intervalBlocks = patch.blocks;
    if (patch.blockMinutes !== undefined) settingsPatch.intervalBlockMin = patch.blockMinutes;
    if (patch.restMinutes !== undefined) settingsPatch.intervalRestMin = patch.restMinutes;
    if (patch.countdownMinutes !== undefined) settingsPatch.countdownMin = patch.countdownMinutes;

    const merged: UserSettings = { ...settingsRef.current, ...settingsPatch };
    settingsRef.current = merged;
    void updateSettings(settingsPatch);

    const config = chronoConfigFromSettings(merged);
    setFocus((prev) => ({
      ...freshFocusState(prev.mode, config),
      linkedTaskId: prev.linkedTaskId,
      autoAdvance: prev.autoAdvance,
    }));
    void clearActiveRun();
    void stopChronoNotification();
  }, []);

  const seekFocusSegment = useCallback((index: number) => {
    if (focusCommitInFlight.current) return;
    setFocus((prev) => {
      const base = prev.isRunning ? pauseClock(prev, Date.now()) : prev;
      return seekSegment(base, index, settingsRef.current.pomodoroCyclesBeforeLong);
    });
  }, []);

  const setAutoAdvance = useCallback((value: boolean) => {
    if (focusCommitInFlight.current) return;
    setFocus((prev) => ({ ...prev, autoAdvance: value }));
    void updateSettings({ autoAdvance: value });
  }, []);

  const setFocusDurations = useCallback(
    (focusMin: number, shortMin: number, longMin: number) => {
      if (focusCommitInFlight.current) return;
      setFocus((prev) => {
        const config = {
          ...chronoConfigFromSettings(settingsRef.current),
          focusMinutes: focusMin,
          shortBreakMinutes: shortMin,
          longBreakMinutes: longMin,
        };
        const plan = buildPlan(prev.mode as ChronoMode, config);
        const index = Math.min(plan.length - 1, Math.max(0, prev.segmentIndex ?? 0));
        const segment = plan[index];
        const nextRemaining = segment.seconds;
        return {
          ...prev,
          focusMinutes: focusMin,
          shortBreakMinutes: shortMin,
          longBreakMinutes: longMin,
          plan,
          segmentIndex: index,
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
    if (focusCommitInFlight.current) return;
    setFocus((prev) => ({ ...prev, linkedTaskId: taskId }));
  }, []);

  /**
   * Turning the switch on is a two-step promise: persist the preference, then ask
   * the system. If the policy access is missing we send the reader to the screen
   * that grants it and say so — a switch that flips while nothing changes is worse
   * than one that refuses.
   */
  const setDndInFocus = useCallback(
    async (enabled: boolean) => {
      if (!enabled) {
        await updateSettings({ dndInFocus: false });
        await setDoNotDisturb(false);
        return;
      }
      const status = await readDndStatus();
      if (!status) {
        pushToast({
          title: 'این قابلیت فقط روی اندروید کار می‌کند',
          description: 'در نسخهٔ وب، اعلان‌های مرورگر در دسترس نیستند.',
          tone: 'warning',
        });
        return;
      }
      if (!status.granted) {
        await openDndSettings();
        pushToast({
          title: 'اجازهٔ «بدون مزاحمت» لازم است',
          description: 'در صفحهٔ بازشده، کایروس را مجاز کنید و به اپ برگردید.',
          tone: 'warning',
        });
        return;
      }
      await updateSettings({ dndInFocus: true });
      pushToast({ title: 'در جلسه‌های بعدی مزاحمت قطع می‌شود', tone: 'success' });
    },
    [pushToast]
  );

  const applyDnd = useCallback((enabled: boolean) => {
    if (!settingsRef.current.dndInFocus) return;
    void setDoNotDisturb(enabled);
  }, []);

  // A break or paused phase must not leave the device in our temporary DND.
  // On restart, a saved run is reconciled to the same policy once ready.
  useEffect(() => {
    if (!ready) return;
    void setDoNotDisturb(settings.dndInFocus && focus.isRunning && focus.phase === 'focus');
  }, [ready, settings.dndInFocus, focus.isRunning, focus.phase]);

  const startOrResumeFocus = useCallback(() => {
    if (focusCommitInFlight.current) return;
    if (focusRef.current.finished) {
      pushToast({ title: 'برنامهٔ تمرکز پایان یافته است', description: 'برای شروع دوباره تایمر را بازنشانی کنید.', tone: 'warning' });
      return;
    }
    void triggerHaptic('light', settingsRef.current.hapticsEnabled);
    setFocus((prev) => startClock(prev, Date.now()));
    applyDnd(focusRef.current.phase === 'focus');
  }, [applyDnd, pushToast]);

  const pauseFocus = useCallback(() => {
    if (focusCommitInFlight.current) return;
    void triggerHaptic('light', settingsRef.current.hapticsEnabled);
    setFocus((prev) => pauseClock(prev, Date.now()));
    // Pausing is a pause for the phone too: notifications are not held hostage.
    applyDnd(false);
  }, [applyDnd]);

  const resetFocus = useCallback(() => {
    if (focusCommitInFlight.current) return;
    setFocus((prev) => {
      const config = chronoConfigFromSettings(settingsRef.current);
      return {
        ...freshFocusState(prev.mode, config),
        linkedTaskId: prev.linkedTaskId,
        autoAdvance: prev.autoAdvance,
      };
    });
    void clearActiveRun();
    void stopChronoNotification();
    applyDnd(false);
  }, [applyDnd]);

  const finishAndSaveFocus = useCallback(async () => {
    if (focusCommitInFlight.current) return;
    const snap = focusRef.current;
    const now = Date.now();
    // Account for boundaries crossed while the UI was suspended, without
    // treating elapsed break time as focus.
    const caughtUp = tickClock(snap, now, settingsRef.current.pomodoroCyclesBeforeLong).state;
    const liveElapsed = Math.max(0, totalFocusSeconds(
      caughtUp, now, settingsRef.current.pomodoroCyclesBeforeLong
    ) - (snap.recordedFocusSeconds ?? 0));
    if (liveElapsed < 60) {
      pushToast({ title: 'حداقل 1 دقیقه تمرکز ثبت‌نشده نیاز است',
        description: 'بخش‌های قبلاً ثبت‌شده دوباره به آمار اضافه نمی‌شوند.', tone: 'warning' });
      return;
    }
    focusCommitInFlight.current = true;
    try {
      await commitFocusRecord(snap.mode, liveElapsed, snap.linkedTaskId, snap.sessionStartedAt, false, null);
      const fresh = {
        ...freshFocusState(snap.mode, chronoConfigFromSettings(settingsRef.current)),
        linkedTaskId: snap.linkedTaskId, autoAdvance: snap.autoAdvance,
      };
      focusRef.current = fresh;
      setFocus(fresh);
      if (!Capacitor.isNativePlatform()) playLuxuryChime(settingsRef.current.soundEnabled);
      void triggerHaptic('success', settingsRef.current.hapticsEnabled);
      pushToast({ title: 'جلسه تمرکز ذخیره شد',
        description: `${Math.max(1, Math.round(liveElapsed / 60))} دقیقه تمرکز در گزارش‌های شما ثبت گردید.`, tone: 'success' });
      void stopChronoNotification();
      applyDnd(false);
    } catch {
      pushToast({ title: 'ثبت تمرکز انجام نشد', description: 'جلسه همچنان در دسترس است؛ دوباره تلاش کنید.', tone: 'warning' });
    } finally {
      focusCommitInFlight.current = false;
    }
  }, [commitFocusRecord, pushToast, applyDnd]);

  /**
   * Advances past the current segment. In a cyclic plan (pomodoro) it wraps to the
   * first segment; in a finite plan (interval, countdown) it stops on the last one
   * so a skip can never invent work that was not planned.
   */
  const skipPomodoroPhase = useCallback(() => {
    if (focusCommitInFlight.current) return;
    setFocus((prev) => {
      const plan = resolvePlan(prev, settingsRef.current.pomodoroCyclesBeforeLong);
      if (plan.length === 0) return prev;
      const current = Math.min(plan.length - 1, Math.max(0, prev.segmentIndex ?? 0));
      const cyclic = prev.mode === 'pomodoro';
      const next = current + 1 >= plan.length ? (cyclic ? 0 : plan.length - 1) : current + 1;
      return seekSegment(prev, next, settingsRef.current.pomodoroCyclesBeforeLong);
    });
  }, []);

  // ── Chrono Core: durability ───────────────────────────────────────────────
  // The session is written to IndexedDB on every structural change and once every
  // 15 s while it runs. Writing per second would be noise; writing never is how a
  // 45-minute block disappears when Android reclaims the app.
  const pausedRemaining = focus.isRunning ? null : focus.remainingSeconds;
  const pausedElapsed = focus.isRunning ? null : focus.elapsedSeconds;
  useEffect(() => {
    if (!ready) return;
    if (!focusCommitInFlight.current) void saveActiveRun(focusRef.current);
    if (!focus.isRunning) return;
    const id = window.setInterval(() => {
      if (!focusCommitInFlight.current) void saveActiveRun(focusRef.current);
    }, 15_000);
    return () => window.clearInterval(id);
    // The running display changes every second, but anchors are sufficient to
    // recover it. Persist only structural changes and the periodic checkpoint.
  }, [ready, focus.mode, focus.isRunning, focus.phase, focus.segmentIndex, focus.plan,
    focus.anchorMs, focus.anchorRemaining, focus.anchorElapsed, focus.completedCycles,
    focus.accruedFocusSeconds, focus.recordedFocusSeconds, focus.finished,
    focus.linkedTaskId, focus.sessionStartedAt, focus.autoAdvance, pausedRemaining, pausedElapsed]);

  // ── Chrono Core: notification mirror ──────────────────────────────────────
  // Only structural moments are pushed: the shade counts down on its own.
  useEffect(() => {
    if (!ready) return;
    if (focus.isRunning) {
      void startChronoNotification(focus);
      return;
    }
    if (hasProgress(focus)) void mirrorChronoRun(focus);
    else void stopChronoNotification();
    // Deliberately keyed on shape, not on the per-second countdown.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, focus.isRunning, focus.segmentIndex, focus.mode]);

  // ── Chrono Core: controls from the notification shade ─────────────────────
  useEffect(() => {
    let dispose: () => void = () => {};
    let cancelled = false;
    void onChronoControl((action) => {
      const current = focusRef.current;
      if (action === 'stop') {
        void finishAndSaveFocus();
        return;
      }
      if (action === 'pause' && current.isRunning) pauseFocus();
      if (action === 'resume' && !current.isRunning) startOrResumeFocus();
      // 'complete' comes from the native end-of-session alarm. The clock in this
      // page is still the authority on what a completed phase means (it decides
      // focus vs break, and banks the seconds), so we let the next tick do it
      // rather than save a session from a background event.
    }).then((cleanup) => {
      if (cancelled) cleanup();
      else dispose = cleanup;
    });
    return () => {
      cancelled = true;
      dispose();
    };
  }, [finishAndSaveFocus, pauseFocus, startOrResumeFocus]);

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
      setChronoConfig,
      seekFocusSegment,
      setAutoAdvance,
      searchOpen,
      setSearchOpen,
      setDndInFocus,
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
      setChronoConfig,
      seekFocusSegment,
      setAutoAdvance,
      searchOpen,
      setSearchOpen,
      setDndInFocus,
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
