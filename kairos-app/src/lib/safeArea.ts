import { Capacitor } from '@capacitor/core';

/**
 * Safe-area (status bar / notch / gesture bar) manager.
 *
 * Android 15+ forces edge-to-edge for every app, so the WebView is stretched
 * behind the system bars and the layout has to keep itself clear of them. There
 * are two legitimate sources of that information and we read **both**, then take
 * the largest value per side:
 *
 *  1. `env(safe-area-inset-*)` — the web standard. Capacitor 8 keeps the WebView
 *     edge-to-edge and lets Chromium report the real insets (WebView ≥ 140 with
 *     `viewport-fit=cover`).
 *  2. `--safe-area-inset-*` — the variables Capacitor's SystemBars plugin injects
 *     in its `insetsHandling: 'css'` mode (used for older WebViews).
 *
 * When neither reports anything (desktop browser, or Capacitor already padded
 * the WebView natively for WebView < 140) the values stay `0px` and the layout
 * is untouched — no double padding, no gaps.
 *
 * Results are published as `--safe-top | --safe-right | --safe-bottom |
 * --safe-left` on <html>, so every stylesheet rule can just use them.
 */

export interface SafeAreaInsets {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface SafeAreaReport {
  env: SafeAreaInsets;
  capacitor: SafeAreaInsets;
  applied: SafeAreaInsets;
  platform: string;
  webViewMajor: number | null;
}

const ZERO: SafeAreaInsets = { top: 0, right: 0, bottom: 0, left: 0 };

/**
 * Manual calibration, for the rare device/ROM where neither the native padding
 * nor the web insets land correctly. Values are extra pixels the user can dial
 * in from Settings; they are applied as a minimum, never as a replacement, so
 * auto-detection still wins when it reports something larger.
 */
const MANUAL_KEY = 'kairos:safe-area-manual';

export function getManualInsets(): Pick<SafeAreaInsets, 'top' | 'bottom'> {
  if (typeof window === 'undefined') return { top: 0, bottom: 0 };
  try {
    const raw = window.localStorage.getItem(MANUAL_KEY);
    if (!raw) return { top: 0, bottom: 0 };
    const parsed = JSON.parse(raw) as { top?: number; bottom?: number };
    return {
      top: Math.min(96, Math.max(0, Number(parsed.top) || 0)),
      bottom: Math.min(96, Math.max(0, Number(parsed.bottom) || 0)),
    };
  } catch {
    return { top: 0, bottom: 0 };
  }
}

export function setManualInsets(pair: { top: number; bottom: number }): SafeAreaInsets {
  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(MANUAL_KEY, JSON.stringify(pair));
    } catch {
      // storage full / private mode — the session still applies the value
    }
  }
  return syncSafeArea();
}

/** Timed re-measures: insets arrive at page-commit and again after layout. */
const RETRY_MS = [0, 80, 240, 600, 1200, 2400];

let probe: HTMLDivElement | null = null;
let lastApplied: SafeAreaInsets = ZERO;
let lastEnv: SafeAreaInsets = ZERO;
let lastCapacitor: SafeAreaInsets = ZERO;

function toPx(value: string): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed) : 0;
}

function getProbe(): HTMLDivElement | null {
  if (typeof document === 'undefined' || !document.body) return null;
  if (probe && probe.isConnected) return probe;
  probe = document.createElement('div');
  probe.setAttribute('aria-hidden', 'true');
  probe.style.cssText = [
    'position:fixed',
    'top:0',
    'left:0',
    'width:0',
    'height:0',
    'visibility:hidden',
    'pointer-events:none',
    'z-index:-1',
    'padding-top:env(safe-area-inset-top,0px)',
    'padding-right:env(safe-area-inset-right,0px)',
    'padding-bottom:env(safe-area-inset-bottom,0px)',
    'padding-left:env(safe-area-inset-left,0px)',
  ].join(';');
  document.body.appendChild(probe);
  return probe;
}

/** Insets reported by the browser through the CSS `env()` standard. */
function readEnvInsets(): SafeAreaInsets {
  const el = getProbe();
  if (!el) return ZERO;
  const cs = getComputedStyle(el);
  return {
    top: toPx(cs.paddingTop),
    right: toPx(cs.paddingRight),
    bottom: toPx(cs.paddingBottom),
    left: toPx(cs.paddingLeft),
  };
}

/** Insets injected by Capacitor's SystemBars plugin (css handling mode). */
function readCapacitorInsets(): SafeAreaInsets {
  if (typeof document === 'undefined') return ZERO;
  const cs = getComputedStyle(document.documentElement);
  const read = (side: string) => toPx(cs.getPropertyValue(`--safe-area-inset-${side}`));
  return { top: read('top'), right: read('right'), bottom: read('bottom'), left: read('left') };
}

function maxInsets(a: SafeAreaInsets, b: SafeAreaInsets): SafeAreaInsets {
  return {
    top: Math.max(a.top, b.top),
    right: Math.max(a.right, b.right),
    bottom: Math.max(a.bottom, b.bottom),
    left: Math.max(a.left, b.left),
  };
}

function publish(insets: SafeAreaInsets): void {
  const root = document.documentElement;
  root.style.setProperty('--safe-top', `${insets.top}px`);
  root.style.setProperty('--safe-right', `${insets.right}px`);
  root.style.setProperty('--safe-bottom', `${insets.bottom}px`);
  root.style.setProperty('--safe-left', `${insets.left}px`);
  root.dataset.safeArea = `${insets.top}/${insets.right}/${insets.bottom}/${insets.left}`;
}

/** Measure both sources, publish the result and return it. */
export function syncSafeArea(): SafeAreaInsets {
  if (typeof document === 'undefined') return ZERO;
  lastEnv = readEnvInsets();
  lastCapacitor = readCapacitorInsets();
  const detected = maxInsets(lastEnv, lastCapacitor);
  const manual = getManualInsets();
  lastApplied = {
    top: Math.max(detected.top, manual.top),
    right: detected.right,
    bottom: Math.max(detected.bottom, manual.bottom),
    left: detected.left,
  };
  publish(lastApplied);
  return lastApplied;
}

export function getSafeAreaInsets(): SafeAreaInsets {
  return lastApplied;
}

function webViewMajor(): number | null {
  if (typeof navigator === 'undefined') return null;
  const match = /Chrome\/(\d+)/.exec(navigator.userAgent);
  return match ? Number(match[1]) : null;
}

export function getSafeAreaReport(): SafeAreaReport {
  return {
    env: lastEnv,
    capacitor: lastCapacitor,
    applied: lastApplied,
    platform: typeof Capacitor?.getPlatform === 'function' ? Capacitor.getPlatform() : 'web',
    webViewMajor: webViewMajor(),
  };
}

/**
 * Keep the CSS variables in sync for the whole session.
 * Returns a cleanup function.
 */
export function startSafeAreaSync(): () => void {
  if (typeof window === 'undefined') return () => {};

  syncSafeArea();

  const timers = RETRY_MS.map((ms) => window.setTimeout(syncSafeArea, ms));
  const onResize = () => syncSafeArea();
  const onVisibility = () => {
    if (document.visibilityState === 'visible') syncSafeArea();
  };

  window.addEventListener('resize', onResize);
  window.addEventListener('orientationchange', onResize);
  document.addEventListener('visibilitychange', onVisibility);
  window.visualViewport?.addEventListener('resize', onResize);

  // Re-measure when the app comes back from the background: the status bar can
  // change height (e.g. a call banner) while we are paused.
  let removeListener: (() => void) | undefined;
  if (Capacitor.isNativePlatform()) {
    void import('@capacitor/app')
      .then(({ App }) =>
        App.addListener('appStateChange', ({ isActive }) => {
          if (isActive) syncSafeArea();
        })
      )
      .then((handle) => {
        removeListener = () => void handle.remove();
      })
      .catch(() => undefined);
  }

  return () => {
    timers.forEach((timer) => window.clearTimeout(timer));
    window.removeEventListener('resize', onResize);
    window.removeEventListener('orientationchange', onResize);
    document.removeEventListener('visibilitychange', onVisibility);
    window.visualViewport?.removeEventListener('resize', onResize);
    removeListener?.();
    probe?.remove();
    probe = null;
  };
}
