import { AnimatePresence, motion } from 'framer-motion';
import { AppProvider, useApp } from '@/context/AppContext';
import { AppShell } from '@/components/layout/AppShell';
import { KairosLogoMark } from '@/components/ui/KairosMark';
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

  if (!ready) {
    return (
      <div className="kairos-ambient-bg flex min-h-[100dvh] flex-col items-center justify-center gap-4 px-6">
        <KairosLogoMark size={56} />
        <div className="text-center">
          <p className="font-brand text-sm font-bold text-[#F4F1F8]">KAIROS</p>
          <p className="mt-1 text-xs text-[#A79EB9]">در حال آماده‌سازی استودیوی شخصی شما...</p>
        </div>
      </div>
    );
  }

  return (
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
  );
}

export default function App() {
  return (
    <AppProvider>
      <KairosRouter />
    </AppProvider>
  );
}
