import { useEffect, useState, type ReactNode } from 'react';
import {
  BarChart3,
  CalendarDays,
  CheckSquare,
  ChevronLeft,
  Flame,
  LayoutDashboard,
  MoreHorizontal,
  Settings,
  Timer,
} from 'lucide-react';
import type { NavTab } from '@/types/domain';
import { useApp } from '@/context/AppContext';
import { KairosMarkTile } from '@/components/ui/KairosMark';
import { Numeric } from '@/components/ui/Numeric';
import { ToastHost } from '@/components/ui/ToastHost';
import { formatTimerSeconds } from '@/lib/format';
import { setupAndroidBackButton, triggerHaptic } from '@/lib/native';

type PrimaryTab = Extract<NavTab, 'dashboard' | 'tasks' | 'habits' | 'focus'>;

const PRIMARY_TABS: { id: PrimaryTab; label: string; icon: typeof LayoutDashboard }[] = [
  { id: 'dashboard', label: 'خانه', icon: LayoutDashboard },
  { id: 'tasks', label: 'وظایف', icon: CheckSquare },
  { id: 'habits', label: 'عادت‌ها', icon: Flame },
  { id: 'focus', label: 'تمرکز', icon: Timer },
];

const SECONDARY_TABS: { id: NavTab; label: string; hint: string; icon: typeof LayoutDashboard }[] = [
  { id: 'calendar', label: 'تقویم', hint: 'نمای ماهانه شمسی و برنامه‌ی روز', icon: CalendarDays },
  { id: 'analytics', label: 'تحلیل عملکرد', hint: 'روند، گزارش ماهانه و سالانه', icon: BarChart3 },
  { id: 'settings', label: 'تنظیمات', hint: 'پروفایل، یادآوری و پشتیبان‌گیری', icon: Settings },
];

export function AppShell({ children }: { children: ReactNode }) {
  const { activeTab, setActiveTab, settings, toasts, dismissToast, focus } = useApp();
  const [moreOpen, setMoreOpen] = useState(false);

  const handleNav = (tab: NavTab) => {
    setMoreOpen(false);
    if (tab === activeTab) return;
    void triggerHaptic('light', settings.hapticsEnabled);
    setActiveTab(tab);
    window.scrollTo({ top: 0 });
  };

  // Android hardware back: close sheet → go home → leave app (never kill mid-flow)
  useEffect(() => {
    let remove: (() => void) | undefined;
    void setupAndroidBackButton(() => {
      if (moreOpen) {
        setMoreOpen(false);
        return true;
      }
      if (activeTab !== 'dashboard') {
        setActiveTab('dashboard');
        return true;
      }
      return false;
    }).then((cleanup) => {
      remove = cleanup;
    });
    return () => remove?.();
  }, [moreOpen, activeTab, setActiveTab]);

  const liveFocusDisplay =
    focus.mode === 'pomodoro'
      ? formatTimerSeconds(focus.remainingSeconds)
      : formatTimerSeconds(focus.elapsedSeconds);

  const isSecondaryActive = SECONDARY_TABS.some((t) => t.id === activeTab);

  return (
    <div className="kairos-ambient-bg min-h-[100dvh] w-full">
      <ToastHost toasts={toasts} onDismiss={dismissToast} />

      <div className="app-safe-frame relative mx-auto flex min-h-[100dvh] w-full max-w-[480px] flex-col">
        <header className="app-safe-header sticky top-0 z-30 flex items-center justify-between border-b border-[var(--border)] bg-[var(--bg)] px-4 pb-3 backdrop-blur-md">
          <button
            type="button"
            onClick={() => handleNav('dashboard')}
            aria-label="خانه"
            className="flex items-center gap-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent-fg)]"
          >
            <KairosMarkTile size={26} />
            <span className="font-brand text-[12px] font-semibold text-[var(--text)]">KAIROS</span>
          </button>

          {focus.isRunning && activeTab !== 'focus' && (
            <button
              type="button"
              onClick={() => handleNav('focus')}
              aria-label="بازگشت به تایمر تمرکز"
              className="flex items-center gap-2 rounded-[var(--r-pill)] border border-[var(--border-strong)] bg-[var(--surface-2)] px-2.5 py-1"
            >
              <span className="focus-live-dot h-1.5 w-1.5 rounded-full bg-[var(--accent-fg)]" />
              <Numeric value={liveFocusDisplay} variant="geo" className="text-[11px] font-medium" />
            </button>
          )}
        </header>

        <main className="app-safe-content flex-1 px-4 pt-4">{children}</main>

        <nav
          aria-label="ناوبری اصلی"
          className="app-safe-tabbar app-safe-x pointer-events-none fixed inset-x-0 bottom-0 z-40 mx-auto w-full max-w-[480px] px-3"
        >
          <div className="luxury-glass-dock pointer-events-auto grid grid-cols-5 items-center gap-0.5 p-1">
            {PRIMARY_TABS.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => handleNav(item.id)}
                  aria-current={isActive ? 'page' : undefined}
                  className={`flex flex-col items-center justify-center gap-1 rounded-[11px] py-2 transition-colors ${
                    isActive ? 'bg-[var(--surface-3)] text-[var(--text)]' : 'text-[var(--text-3)]'
                  }`}
                >
                  <Icon
                    className={`h-[18px] w-[18px] ${isActive ? 'text-[var(--accent-fg)]' : ''}`}
                    strokeWidth={isActive ? 2 : 1.7}
                  />
                  <span className="text-[10px] leading-none">{item.label}</span>
                </button>
              );
            })}

            <button
              type="button"
              onClick={() => {
                void triggerHaptic('light', settings.hapticsEnabled);
                setMoreOpen(true);
              }}
              aria-expanded={moreOpen}
              aria-current={isSecondaryActive ? 'page' : undefined}
              className={`flex flex-col items-center justify-center gap-1 rounded-[11px] py-2 transition-colors ${
                isSecondaryActive ? 'bg-[var(--surface-3)] text-[var(--text)]' : 'text-[var(--text-3)]'
              }`}
            >
              <MoreHorizontal
                className={`h-[18px] w-[18px] ${isSecondaryActive ? 'text-[var(--accent-fg)]' : ''}`}
                strokeWidth={isSecondaryActive ? 2 : 1.7}
              />
              <span className="text-[10px] leading-none">بیشتر</span>
            </button>
          </div>
        </nav>

        {moreOpen && (
          <div className="fixed inset-0 z-50 flex items-end justify-center">
            <button
              type="button"
              aria-label="بستن فهرست"
              onClick={() => setMoreOpen(false)}
              className="absolute inset-0 bg-black/60"
            />
            <div
              role="dialog"
              aria-modal="true"
              aria-label="بخش‌های دیگر"
              className="app-safe-bottom-sheet relative w-full max-w-[480px] px-3"
            >
              <div className="luxury-glass-sheet overflow-hidden p-2">
                {SECONDARY_TABS.map((item) => {
                  const Icon = item.icon;
                  const isActive = activeTab === item.id;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => handleNav(item.id)}
                      aria-label={`بخش ${item.label}`}
                      className="flex w-full items-center gap-3 rounded-[12px] px-3 py-3 text-right transition-colors hover:bg-[var(--surface-3)]"
                    >
                      <span
                        className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px] border border-[var(--border)] bg-[var(--surface-1)] ${
                          isActive ? 'text-[var(--accent-fg)]' : 'text-[var(--text-2)]'
                        }`}
                      >
                        <Icon className="h-4 w-4" strokeWidth={1.8} />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-[13px] font-medium text-[var(--text)]">
                          {item.label}
                        </span>
                        <span className="mt-0.5 block truncate text-[11px] text-[var(--text-3)]">
                          {item.hint}
                        </span>
                      </span>
                      <ChevronLeft className="h-4 w-4 shrink-0 text-[var(--text-3)]" />
                    </button>
                  );
                })}
                <div className="mt-1 border-t border-[var(--border)] px-3 pt-2.5 pb-1 text-center">
                  <span className="num-geo text-[10px] text-[var(--text-3)]">KAIROS 1.2.0</span>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
