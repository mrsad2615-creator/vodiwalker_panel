import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
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
} from '@/components/ui/icons';
import type { NavTab } from '@/types/domain';
import { applySectionAppearance } from '@/lib/sections';
import { INITIAL_CHROME, resolveScrollChrome, type ScrollChrome } from '@/lib/scrollChrome';
import { useApp } from '@/context/AppContext';
import { KairosMarkTile } from '@/components/ui/KairosMark';
import { Search } from '@/components/ui/icons';
import { APP_VERSION, BUILD_NUMBER } from '@/lib/buildInfo';
import { Numeric } from '@/components/ui/Numeric';
import { ToastHost } from '@/components/ui/ToastHost';
import { formatTimerSeconds } from '@/lib/format';
import { setupAndroidBackButton, triggerHaptic } from '@/lib/native';
import { SearchSheet } from '@/components/search/SearchSheet';

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
  const {
    activeTab,
    setActiveTab,
    settings,
    toasts,
    dismissToast,
    focus,
    searchOpen,
    setSearchOpen,
  } = useApp();
  const [moreOpen, setMoreOpen] = useState(false);
  const [chrome, setChrome] = useState<ScrollChrome>(INITIAL_CHROME);

  // One palette for the product; the section only records which room it is.
  useEffect(() => {
    applySectionAppearance(
      {
        accent: settings.accentColor,
        backgroundId: settings.backgroundId,
        motionLevel: settings.motionLevel,
      },
      activeTab
    );
  }, [activeTab, settings.accentColor, settings.backgroundId, settings.motionLevel]);

  const handleNav = (tab: NavTab) => {
    setMoreOpen(false);
    if (tab === activeTab) return;
    // Revealing the bar is part of navigating, not of an effect that may land
    // after the user has already started reading.
    setChrome(INITIAL_CHROME);
    void triggerHaptic('light', settings.hapticsEnabled);
    setActiveTab(tab);
    // Scroll reset happens in the effect below — the document no longer scrolls.
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

  // Ctrl/⌘+K anywhere in the product opens the box. Escape is handled by the sheet.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setSearchOpen(!searchOpen);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [searchOpen, setSearchOpen]);

  const isSecondaryActive = SECONDARY_TABS.some((t) => t.id === activeTab);

  // The shell scrolls its <main>, not the document, so a tab change has to reset
  // the scroll position explicitly (otherwise you land mid-page on a new screen).
  const scrollRef = useRef<HTMLElement | null>(null);
  useEffect(() => {
    const node = scrollRef.current;
    if (!node) return;
    if (typeof node.scrollTo === 'function') node.scrollTo({ top: 0, behavior: 'auto' });
    else node.scrollTop = 0;
  }, [activeTab]);

  // The top bar tucks itself away while reading downward and returns on the way
  // back up. The decision is pure and returns the previous object whenever
  // nothing changed, so the common case costs no render — and no rAF is needed,
  // which means a throttled or suspended frame (background tab, cold WebView)
  // can never leave the bar stuck open.
  // The listener is bound through a ref callback, not an effect: the scroller
  // can be replaced when the shell re-renders, and an effect that captured the
  // old node would leave the new one unable to move the bar at all.
  const detachRef = useRef<(() => void) | null>(null);
  const bindScroller = useCallback((node: HTMLElement | null) => {
    detachRef.current?.();
    detachRef.current = null;
    scrollRef.current = node;
    if (!node) return;

    const onScroll = () => {
      setChrome((prev) => resolveScrollChrome(prev, node.scrollTop));
    };

    node.addEventListener('scroll', onScroll, { passive: true });
    detachRef.current = () => node.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <div className="kairos-ambient-bg h-full w-full">
      <ToastHost toasts={toasts} onDismiss={dismissToast} />
      <SearchSheet open={searchOpen} onClose={() => setSearchOpen(false)} />

      {/* Shell owns the viewport; only `.app-scroll` moves. */}
      <div className="app-shell app-safe-frame relative mx-auto w-full max-w-[480px]">
        <main ref={bindScroller} className="app-scroll">
        {/* The bar is sticky and hides by transform only. Toggling layout height
            (the 1.4.4 approach) shifted the content under the finger, which
            re-fired scroll events and made the bar bounce up and down. */}
        <div
          data-chrome={chrome.hidden ? 'hidden' : 'visible'}
          className="sticky top-0 z-30 transition-transform duration-300 ease-out"
          style={{ transform: chrome.hidden ? 'translateY(-104%)' : 'translateY(0)' }}
          aria-hidden={chrome.hidden}
        >
        <header
          className="app-safe-header flex min-h-0 items-center justify-between border-b border-[var(--border)] px-4 pb-3"
          style={{
            backgroundColor: 'color-mix(in srgb, var(--bg) 84%, transparent)',
            backdropFilter: 'blur(14px) saturate(120%)',
            WebkitBackdropFilter: 'blur(14px) saturate(120%)',
          }}
        >
          <button
            type="button"
            onClick={() => handleNav('dashboard')}
            aria-label="خانه"
            className="flex items-center gap-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent-fg)]"
          >
            <KairosMarkTile size={26} />
            <span className="font-display text-[14px] font-semibold leading-none tracking-[0.12em] text-[var(--text)]">
              KAIROS
            </span>
          </button>

          <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setSearchOpen(true)}
            aria-label="جست‌وجوی سراسری"
            title="جست‌وجو (Ctrl+K)"
            className="grid h-8 w-8 place-items-center rounded-full border border-[var(--border)] text-[var(--text-2)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text)]"
          >
            <Search className="h-3.5 w-3.5" />
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
          </div>
        </header>
        </div>

        {focus.isRunning && activeTab !== 'focus' && (
          <div
            className={`pointer-events-none fixed inset-x-0 top-0 z-40 mx-auto flex w-full max-w-[480px] justify-end px-4 transition-opacity duration-200 ${
              chrome.hidden ? 'opacity-100' : 'opacity-0'
            }`}
            style={{ paddingTop: 'calc(var(--safe-top) + 0.5rem)' }}
          >
            <button
              type="button"
              onClick={() => handleNav('focus')}
              aria-label="بازگشت به تایمر تمرکز"
              tabIndex={chrome.hidden ? 0 : -1}
              className={`pointer-events-auto flex items-center gap-2 rounded-[var(--r-pill)] border border-[var(--border-strong)] px-2.5 py-1 ${
                chrome.hidden ? '' : 'invisible'
              }`}
              style={{ backgroundColor: 'color-mix(in srgb, var(--surface-2) 92%, transparent)' }}
            >
              <span className="focus-live-dot h-1.5 w-1.5 rounded-full bg-[var(--accent-fg)]" />
              <Numeric value={liveFocusDisplay} variant="geo" className="text-[11px] font-medium" />
            </button>
          </div>
        )}

        <div className="app-safe-content px-4 pt-4">{children}</div>
        </main>

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
                    size={18}
                    weight={isActive ? 'fill' : 'duotone'}
                    className={isActive ? 'text-[var(--accent-fg)]' : ''}
                    aria-hidden="true"
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
                size={18}
                weight={isSecondaryActive ? 'fill' : 'duotone'}
                className={isSecondaryActive ? 'text-[var(--accent-fg)]' : ''}
                aria-hidden="true"
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
                        <Icon size={16} weight={isActive ? 'fill' : 'duotone'} aria-hidden="true" />
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
                  <span className="num-geo text-[10px] text-[var(--text-3)]">
                    KAIROS {APP_VERSION} · b{BUILD_NUMBER}
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
