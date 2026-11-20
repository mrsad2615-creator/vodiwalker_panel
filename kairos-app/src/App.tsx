import { useCallback, useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AppProvider, useApp } from '@/context/AppContext';
import { AppShell } from '@/components/layout/AppShell';
import { LaunchScreen } from '@/components/ui/LaunchScreen';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { IconContext } from '@/components/ui/icons';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { TasksPage } from '@/features/tasks/TasksPage';
import { TaskModal } from '@/features/tasks/TaskModal';
import { HabitsPage } from '@/features/habits/HabitsPage';
import { CalendarPage } from '@/features/calendar/CalendarPage';
import { FocusPage } from '@/features/focus/FocusPage';
import { AnalyticsPage } from '@/features/analytics/AnalyticsPage';
import { SettingsPage } from '@/features/settings/SettingsPage';

type SplashPhase = 'playing' | 'exiting' | 'done';

/** Single source of truth for the launch choreography (mirrors design-system.css). */
const LAUNCH = {
  /** Minimum on-screen time so the logo motion can finish (ms). */
  splashHoldMs: 900,
  /** Cross-fade into the shell (ms). */
  exitFadeMs: 260,
  /** Above this the app shows the recovery screen instead of an endless splash. */
  bootFailsafeMs: 4000,
} as const;

function BootRecovery() {
  return (
    <div className="flex min-h-[100dvh] flex-col items-center justify-center gap-4 bg-[var(--bg)] px-8 text-center">
      <span className="text-[13px] font-semibold text-[var(--text)]">راه‌اندازی طول کشید</span>
      <p className="text-[12px] leading-6 text-[var(--text-2)]">
        داده‌های محلی در دسترس نیستند. یک‌بار برنامه را بازخوانی کنید.
      </p>
      <button
        type="button"
        onClick={() => window.location.reload()}
        className="btn-royal px-5 py-2.5 text-[12px]"
      >
        بازخوانی برنامه
      </button>
    </div>
  );
}

function KairosRouter() {
  const { ready, activeTab } = useApp();
  const [phase, setPhase] = useState<SplashPhase>('playing');
  const [bootStalled, setBootStalled] = useState(false);
  const [fontsReady, setFontsReady] = useState(false);
  const [minDwellElapsed, setMinDwellElapsed] = useState(false);

  // Minimum dwell so the launch motion is never cut in half.
  useEffect(() => {
    const timer = window.setTimeout(() => setMinDwellElapsed(true), LAUNCH.splashHoldMs);
    return () => window.clearTimeout(timer);
  }, []);

  // Typography gate: never reveal the shell with fallback glyphs.
  useEffect(() => {
    let alive = true;
    const fonts = typeof document !== 'undefined' ? document.fonts : undefined;
    if (!fonts || typeof fonts.load !== 'function') {
      setFontsReady(true);
      return;
    }
    const weights: Array<Promise<unknown>> = [];
    try {
      weights.push(fonts.load('600 16px "Vazirmatn Variable"'));
      weights.push(fonts.load('600 16px "Space Grotesk Variable"'));
    } catch {
      // Font loading API quirks — the readiness promise below still gates us.
    }
    Promise.allSettled([...weights, fonts.ready])
      .then(() => {
        if (alive) setFontsReady(true);
      })
      .catch(() => {
        if (alive) setFontsReady(true);
      });
    return () => {
      alive = false;
    };
  }, []);

  // Recovery path: if the local database never resolves, offer a reload instead
  // of leaving the user on the splash forever.
  useEffect(() => {
    const timer = window.setTimeout(() => setBootStalled(true), LAUNCH.bootFailsafeMs);
    return () => window.clearTimeout(timer);
  }, []);

  // Hand over to the shell once data + fonts are in and the dwell has passed.
  useEffect(() => {
    if (phase !== 'playing') return;
    if (!ready || !fontsReady || !minDwellElapsed) return;
    setPhase('exiting');
  }, [ready, fontsReady, minDwellElapsed, phase]);

  useEffect(() => {
    if (phase !== 'exiting') return;
    const timer = window.setTimeout(() => setPhase('done'), LAUNCH.exitFadeMs);
    return () => window.clearTimeout(timer);
  }, [phase]);

  const handleSkipIntro = useCallback(() => setPhase('done'), []);

  const progress = phase !== 'playing' ? 1 : ready ? (fontsReady ? 0.94 : 0.62) : fontsReady ? 0.34 : 0.16;
  // Mount the shell only once we start handing over: while the splash owns the
  // screen the tree stays out of the a11y tree (and never mounts twice).
  const shouldRenderApp = ready && phase !== 'playing';

  if (!ready && bootStalled) return <BootRecovery />;

  return (
    <>
      {phase !== 'done' && (
        <LaunchScreen exiting={phase === 'exiting'} progress={progress} onSkip={handleSkipIntro} />
      )}

      {shouldRenderApp && (
        <AppShell>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={activeTab}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.12, ease: 'easeOut' }}
            >
              {activeTab === 'dashboard' && <DashboardPage />}
              {activeTab === 'tasks' && <TasksPage />}
              {activeTab === 'habits' && <HabitsPage />}
              {activeTab === 'calendar' && <CalendarPage />}
              {activeTab === 'focus' && <FocusPage />}
              {activeTab === 'analytics' && <AnalyticsPage />}
              {activeTab === 'settings' && <SettingsPage />}
            </motion.div>
          </AnimatePresence>

          <TaskModal />
        </AppShell>
      )}
    </>
  );
}

/** Hoisted so icons never re-render from a fresh context object. */
const ICON_CONTEXT = { weight: 'regular', size: 18, mirrored: false } as const;

export default function App() {
  return (
    <ErrorBoundary>
      {/* One optical weight for every icon in the app (Phosphor `regular`). */}
      <IconContext.Provider value={ICON_CONTEXT}>
        <AppProvider>
          <KairosRouter />
        </AppProvider>
      </IconContext.Provider>
    </ErrorBoundary>
  );
}
