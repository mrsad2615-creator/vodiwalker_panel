import { useCallback, useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AppProvider, useApp } from '@/context/AppContext';
import { AppShell } from '@/components/layout/AppShell';
import { MrsadSplashIntro } from '@/components/ui/MrsadSplashIntro';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { TasksPage } from '@/features/tasks/TasksPage';
import { TaskModal } from '@/features/tasks/TaskModal';
import { HabitsPage } from '@/features/habits/HabitsPage';
import { CalendarPage } from '@/features/calendar/CalendarPage';
import { FocusPage } from '@/features/focus/FocusPage';
import { AnalyticsPage } from '@/features/analytics/AnalyticsPage';
import { SettingsPage } from '@/features/settings/SettingsPage';

type SplashPhase = 'playing' | 'mounting_app' | 'exiting' | 'done';

/** Single source of truth for the launch choreography (mirrors design-system.css). */
const LAUNCH = {
  splashHoldMs: 700,
  exitFadeMs: 240,
  bootFailsafeMs: 2500,
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

  // Hold the launch screen briefly, then mount the shell underneath it so the
  // first frame the user sees is already fully rendered.
  useEffect(() => {
    const mountTimer = window.setTimeout(() => {
      setPhase((prev) => (prev === 'playing' ? 'mounting_app' : prev));
    }, LAUNCH.splashHoldMs);

    const failsafeTimer = window.setTimeout(() => {
      setBootStalled(true);
    }, LAUNCH.bootFailsafeMs);

    return () => {
      window.clearTimeout(mountTimer);
      window.clearTimeout(failsafeTimer);
    };
  }, []);

  useEffect(() => {
    if (!ready || phase !== 'mounting_app') return;
    const exitTimer = window.setTimeout(() => setPhase('exiting'), 0);
    return () => window.clearTimeout(exitTimer);
  }, [ready, phase]);

  useEffect(() => {
    if (phase !== 'exiting') return;
    const doneTimer = window.setTimeout(() => setPhase('done'), LAUNCH.exitFadeMs);
    return () => window.clearTimeout(doneTimer);
  }, [phase]);

  const handleSkipIntro = useCallback(() => setPhase('done'), []);

  const shouldRenderApp = ready && phase !== 'playing';

  if (!ready && bootStalled) return <BootRecovery />;

  return (
    <>
      {phase !== 'done' && (
        <MrsadSplashIntro exiting={phase === 'exiting'} onSkip={handleSkipIntro} />
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

export default function App() {
  return (
    <ErrorBoundary>
      <AppProvider>
        <KairosRouter />
      </AppProvider>
    </ErrorBoundary>
  );
}
