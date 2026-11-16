import { useCallback, useState } from 'react';
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

function KairosRouter() {
  const { ready, activeTab } = useApp();
  const [introDone, setIntroDone] = useState(false);

  const handleIntroFinish = useCallback(() => {
    setIntroDone(true);
  }, []);

  return (
    <>
      <AnimatePresence>
        {(!ready || !introDone) && (
          <MrsadSplashIntro ready={ready} onFinish={handleIntroFinish} />
        )}
      </AnimatePresence>

      {ready && (
        <AppShell>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              transition={{ duration: 0.16, ease: [0.22, 1, 0.36, 1] }}
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
