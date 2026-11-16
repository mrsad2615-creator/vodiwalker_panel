import type { ReactNode } from 'react';
import {
  BarChart3,
  CalendarDays,
  CheckSquare,
  Flame,
  LayoutDashboard,
  Settings,
  Timer,
} from 'lucide-react';
import type { NavTab } from '@/types/domain';
import { useApp } from '@/context/AppContext';
import { KairosLogoMark } from '@/components/ui/KairosMark';
import { Numeric } from '@/components/ui/Numeric';
import { ToastHost } from '@/components/ui/ToastHost';
import { formatTimerSeconds } from '@/lib/format';
import { triggerHaptic } from '@/lib/native';

const BOTTOM_NAV_ITEMS: {
  id: NavTab;
  label: string;
  icon: typeof LayoutDashboard;
}[] = [
  { id: 'dashboard', label: 'داشبورد', icon: LayoutDashboard },
  { id: 'tasks', label: 'وظایف', icon: CheckSquare },
  { id: 'habits', label: 'عادت‌ها', icon: Flame },
  { id: 'calendar', label: 'تقویم', icon: CalendarDays },
  { id: 'focus', label: 'تمرکز', icon: Timer },
  { id: 'analytics', label: 'تحلیل', icon: BarChart3 },
];

export function AppShell({ children }: { children: ReactNode }) {
  const { activeTab, setActiveTab, settings, toasts, dismissToast, focus } = useApp();

  const handleNav = (tab: NavTab) => {
    if (tab !== activeTab) {
      void triggerHaptic('light', settings.hapticsEnabled);
      setActiveTab(tab);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const liveFocusDisplay =
    focus.mode === 'pomodoro'
      ? formatTimerSeconds(focus.remainingSeconds)
      : formatTimerSeconds(focus.elapsedSeconds);

  return (
    <div className="kairos-ambient-bg min-h-[100dvh] w-full">
      <ToastHost toasts={toasts} onDismiss={dismissToast} />

      {/* Centered Flagship Android Mobile Canvas (edge-to-edge on mobile, architectural stage on desktop) */}
      <div className="relative mx-auto flex min-h-[100dvh] w-full max-w-[480px] flex-col sm:border-x sm:border-[#B69AEF]/[0.07] sm:shadow-[0_0_90px_rgba(0,0,0,0.85)]">
        {/* Top Minimal Luxury App Bar */}
        <header className="sticky top-0 z-30 flex items-center justify-between border-b border-[#B69AEF]/[0.07] bg-[#09090D]/82 px-4 py-3 backdrop-blur-xl">
          <button
            type="button"
            onClick={() => handleNav('dashboard')}
            className="flex items-center gap-2.5 text-right focus:outline-none"
          >
            <KairosLogoMark size={30} />
            <div className="flex flex-col leading-none">
              <span className="font-brand text-[12px] font-bold text-[#F4F1F8]">
                KAIROS
              </span>
              <span className="mt-1 text-[10px] font-medium text-[#A79EB9]">
                کایروس
              </span>
            </div>
          </button>

          <div className="flex items-center gap-2">
            {/* Live Persistent Focus Indicator Pill when timer is running on another page */}
            {focus.isRunning && activeTab !== 'focus' && (
              <button
                type="button"
                onClick={() => handleNav('focus')}
                className="flex items-center gap-1.5 rounded-full border border-[#8052D6]/50 bg-[#241936]/90 px-2.5 py-1 text-xs text-[#F4F1F8] shadow-[0_0_16px_rgba(128,82,214,0.35)] transition hover:border-[#B69AEF]"
              >
                <span className="h-1.5 w-1.5 rounded-full bg-[#B69AEF]" />
                <Numeric value={liveFocusDisplay} variant="geo" className="text-[11px] font-semibold" />
              </button>
            )}

            <button
              type="button"
              onClick={() => handleNav('settings')}
              aria-label="تنظیمات"
              className={`flex h-9 w-9 items-center justify-center rounded-xl border transition ${
                activeTab === 'settings'
                  ? 'border-[#B69AEF]/45 bg-[#241936] text-[#B69AEF]'
                  : 'border-[#B69AEF]/10 bg-[#15141B] text-[#A79EB9] hover:border-[#B69AEF]/25 hover:text-[#F4F1F8]'
              }`}
            >
              <Settings className="h-[17px] w-[17px]" strokeWidth={1.75} />
            </button>
          </div>
        </header>

        {/* Main Scrollable Content Stage */}
        <main className="flex-1 px-4 pt-4 pb-28">{children}</main>

        {/* Floating Minimal Glassmorphic Bottom Navigation Dock */}
        <nav
          aria-label="ناوبری اصلی"
          className="pointer-events-none fixed inset-x-0 bottom-0 z-40 mx-auto w-full max-w-[480px] px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
        >
          <div className="luxury-glass-dock pointer-events-auto grid grid-cols-6 items-center rounded-[22px] px-1.5 py-1.5">
            {BOTTOM_NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => handleNav(item.id)}
                  aria-current={isActive ? 'page' : undefined}
                  className={`relative flex flex-col items-center justify-center gap-1 rounded-[16px] py-2 transition-all duration-200 ${
                    isActive
                      ? 'bg-[#241936]/95 text-[#F4F1F8] shadow-[inset_0_1px_0_rgba(182,154,239,0.22)]'
                      : 'text-[#6E6680] hover:text-[#A79EB9]'
                  }`}
                >
                  {isActive && (
                    <span className="absolute -top-0.5 h-[2.5px] w-5 rounded-full bg-[#8052D6] shadow-[0_0_8px_#8052D6]" />
                  )}
                  <Icon
                    className={`h-[18px] w-[18px] transition-colors ${
                      isActive ? 'text-[#B69AEF]' : ''
                    }`}
                    strokeWidth={isActive ? 2.1 : 1.7}
                  />
                  <span className="text-[10px] font-medium leading-none">{item.label}</span>
                </button>
              );
            })}
          </div>
        </nav>
      </div>
    </div>
  );
}
