import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import {
  BarChart3,
  CalendarDays,
  CheckSquare,
  ChevronLeft,
  Home,
  Flame,
  MoreHorizontal,
  Settings,
  Timer,
  Lightning,
  Sliders,
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
import { CommandCenter } from '@/features/assistant/CommandCenter';
import { Folder as FolderOpen } from '@/components/ui/icons';
import { changeShortcut, normalizeShortcuts, SHORTCUTS, type ShortcutTab } from '@/lib/navigation';
import { updateSettings } from '@/lib/db';

const DESTINATIONS: { id: NavTab; label: string; hint: string; icon: typeof Home }[] = [
  { id: 'dashboard', label: 'خانه', hint: 'نمای کلی روز', icon: Home },
  { id: 'tasks', label: 'وظایف', hint: 'کارها و اولویت‌ها', icon: CheckSquare },
  { id: 'habits', label: 'عادت‌ها', hint: 'عادت‌ها و پیگیری', icon: Flame },
  { id: 'focus', label: 'تمرکز', hint: 'زمان‌سنج و فضای صوتی', icon: Timer },
  { id: 'motivation', label: 'انگیزه', hint: 'قدرت، حرکت و امید', icon: Lightning },
  { id: 'projects', label: 'پروژه‌ها', hint: 'هدف ← پروژه ← مرحله ← کار', icon: FolderOpen },
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
    pushToast,
    focus,
    searchOpen,
    setSearchOpen,
  } = useApp();
  const [moreOpen, setMoreOpen] = useState(false);
  const [editingNav, setEditingNav] = useState(false);
  const [draftShortcuts, setDraftShortcuts] = useState<ShortcutTab[]>(() => normalizeShortcuts(settings.navShortcuts));
  const [savingNav, setSavingNav] = useState(false);
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
    setEditingNav(false);
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
        setEditingNav(false);
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

  useEffect(() => {
    if (!moreOpen) return;
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setMoreOpen(false); setEditingNav(false); }
    };
    window.addEventListener('keydown', onEscape);
    return () => window.removeEventListener('keydown', onEscape);
  }, [moreOpen]);

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

  const shortcuts = normalizeShortcuts(settings.navShortcuts);
  const primaryTabs = DESTINATIONS.filter((item) => item.id === 'dashboard');
  for (const id of shortcuts) {
    const item = DESTINATIONS.find((destination) => destination.id === id);
    if (item) primaryTabs.push(item);
  }
  const isSecondaryActive = !primaryTabs.some((item) => item.id === activeTab);

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
      {/* One command surface, on every screen: a question box that answers from
          local rows and says "I don't know" instead of guessing. */}
      <CommandCenter />

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

        {settings.hasSampleData && settings.sampleVisible !== false && (
          <div role="status" className="mx-4 mt-3 rounded-xl border border-[var(--accent-fg)]/25 bg-[var(--surface-1)] px-3 py-2 text-[10px] leading-5 text-[var(--text-2)]">
            داده‌های نمونه فعال‌اند؛ آمار و تحلیل‌ها می‌توانند ترکیبی از اطلاعات واقعی و ساختگی باشند.
          </div>
        )}
        <div className="app-safe-content px-4 pt-4">{children}</div>
        </main>

        <nav
          aria-label="ناوبری اصلی"
          className="app-safe-tabbar app-safe-x pointer-events-none fixed inset-x-0 bottom-0 z-40 mx-auto w-full max-w-[480px] px-2"
        >
          <div className="kairos-nav-dock pointer-events-auto grid grid-cols-6 items-stretch">
            {primaryTabs.map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => handleNav(item.id)}
                  aria-current={isActive ? 'page' : undefined}
                  aria-label={item.label}
                  className="kairos-nav-item"
                  data-active={isActive}
                >
                  <span aria-hidden="true" className="kairos-nav-icon">
                    <Icon size={22} weight={isActive ? 'duotone' : 'regular'} />
                  </span>
                  <span className="kairos-nav-label">{item.label}</span>
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
              aria-label="بیشتر"
              className="kairos-nav-item"
              data-active={isSecondaryActive || moreOpen}
            >
              <span aria-hidden="true" className="kairos-nav-icon">
                <MoreHorizontal size={22} weight={isSecondaryActive || moreOpen ? 'duotone' : 'regular'} />
              </span>
              <span className="kairos-nav-label">بیشتر</span>
            </button>
          </div>
        </nav>

        {moreOpen && (
          <div className="fixed inset-0 z-50 flex items-end justify-center">
            <button
              type="button"
              aria-label="بستن فهرست"
              onClick={() => { setMoreOpen(false); setEditingNav(false); }}
              className="absolute inset-0 bg-black/60"
            />
            <div
              role="dialog"
              aria-modal="true"
              aria-label="بخش‌های دیگر"
              className="app-safe-bottom-sheet relative w-full max-w-[480px] px-3"
            >
              <div className="luxury-glass-sheet max-h-[80dvh] overflow-y-auto p-2">
                <div className="flex items-center justify-between gap-2 px-3 py-3">
                  <div><p className="text-sm font-bold text-[var(--text)]">همهٔ بخش‌ها</p><p className="mt-1 text-[10px] text-[var(--text-3)]">میان‌برهای نوار پایین را خودت انتخاب کن</p></div>
                  <button type="button" onClick={() => { setDraftShortcuts(normalizeShortcuts(settings.navShortcuts)); setEditingNav((value) => !value); }} className="flex items-center gap-1 rounded-xl border border-[var(--border)] px-3 py-2 text-[10px] text-[var(--accent-fg)]"><Sliders size={15} /> شخصی‌سازی</button>
                </div>
                {editingNav && (
                  <div className="space-y-2 rounded-xl border border-[var(--border)] bg-[var(--surface-1)] p-3">
                    <p className="text-[10px] leading-5 text-[var(--text-2)]">خانه و فهرست ثابت‌اند؛ چهار جایگاه دیگر را انتخاب کن. انتخاب تکراری، جای دو مورد را عوض می‌کند.</p>
                    {draftShortcuts.map((id, index) => (
                      <label key={index} className="flex items-center justify-between gap-3 text-[11px] text-[var(--text-2)]">
                        <span>میان‌بر {index + 1}</span>
                        <select aria-label={`میان‌بر ${index + 1}`} value={id} onChange={(event) => setDraftShortcuts((current) => changeShortcut(current, index, event.target.value as ShortcutTab))} className="luxury-input max-w-[65%] py-1 text-xs">
                          {SHORTCUTS.map((tab) => <option key={tab} value={tab}>{DESTINATIONS.find((item) => item.id === tab)?.label}</option>)}
                        </select>
                      </label>
                    ))}
                    <div className="flex gap-2">
                      <button type="button" disabled={savingNav} onClick={async () => { setSavingNav(true); try { await updateSettings({ navShortcuts: draftShortcuts }); setEditingNav(false); pushToast({ title: 'میان‌برها ذخیره شدند', tone: 'success' }); } catch { pushToast({ title: 'ذخیره انجام نشد', tone: 'warning' }); } finally { setSavingNav(false); } }} className="btn-royal rounded-xl px-3 py-2 text-[11px] disabled:opacity-50">ذخیرهٔ میان‌برها</button>
                      <button type="button" onClick={() => setEditingNav(false)} className="btn-ghost-luxury rounded-xl px-3 py-2 text-[11px]">انصراف</button>
                    </div>
                  </div>
                )}
                {DESTINATIONS.map((item) => {
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
                        <Icon size={16} weight={isActive ? 'fill' : 'regular'} aria-hidden="true" />
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
