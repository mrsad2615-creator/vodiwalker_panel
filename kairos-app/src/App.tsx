import { useCallback, useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { AppProvider, useApp } from '@/context/AppContext';
import { AppShell } from '@/components/layout/AppShell';
import { MrsadSplashIntro } from '@/components/ui/MrsadSplashIntro';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { TasksPage } from '@/features/tasks/TasksPage';
import { TaskModal } from '@/features/tasks/TaskModal';
import { HabitsPage } from '@/features/habits/HabitsPage';
import { CalendarPage } from '@/features/calendar/CalendarPage';
import { FocusPage } from '@/features/focus/FocusPage';
import { AnalyticsPage } from '@/features/analytics/AnalyticsPage';
import { SettingsPage } from '@/features/settings/SettingsPage';

type SplashPhase = 'playing' | 'mounting_app' | 'exiting' | 'done';

function KairosRouter() {
  const { ready, activeTab } = useApp();
  const [phase, setPhase] = useState<SplashPhase>('playing');

  // Choreography eliminates Android cold-start stutter completely:
  // 1) 0ms - 1220ms: ONLY pure-CSS mrsad splash is mounted -> buttery 120fps on GPU compositor
  // 2) 1220ms (once logo holds still): mount AppShell silently underneath while splash stays opaque
  // 3) 1560ms (when ready): trigger 320ms GPU opacity fade-out
  // 4) 1900ms: unmount splash cleanly (plus 3200ms hard failsafe so it can NEVER hang)
  useEffect(() => {
    const mountTimer = window.setTimeout(() => {
      setPhase((prev) => (prev === 'playing' ? 'mounting_app' : prev));
    }, 1220);

    const failsafeTimer = window.setTimeout(() => {
      setPhase('done');
    }, 3200);

    return () => {
      window.clearTimeout(mountTimer);
      window.clearTimeout(failsafeTimer);
    };
  }, []);

  useEffect(() => {
    if (!ready || phase !== 'mounting_app') return;
    const exitTimer = window.setTimeout(() => {
      setPhase('exiting');
    }, 320);
    return () => window.clearTimeout(exitTimer);
  }, [ready, phase]);

  useEffect(() => {
    if (phase !== 'exiting') return;
    const doneTimer = window.setTimeout(() => {
      setPhase('done');
    }, 340);
    return () => window.clearTimeout(doneTimer);
  }, [phase]);

  const handleSkipIntro = useCallback(() => {
    setPhase('done');
  }, []);

  const shouldRenderApp = ready && phase !== 'playing';
  const showSplash = phase !== 'done';

  return (
    <>
      {showSplash && (
        <MrsadSplashIntro
          exiting={phase === 'exiting'}
          onSkip={handleSkipIntro}
        />
      )}

      {shouldRenderApp && (
        <AppShell>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.14, ease: [0.22, 1, 0.36, 1] }}
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

          {/* Global Fast Task Create / Edit Modal */}
          <TaskModal />
        </AppShell>
      )}
    </>
  );
}

export default function App() {
  return (
    <AppProvider>
      <KairosRouter />
    </AppProvider>
  );
}
