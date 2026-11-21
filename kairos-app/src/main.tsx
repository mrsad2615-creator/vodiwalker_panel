import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Capacitor } from '@capacitor/core';

// Typography: variable fonts, bundled offline — one file per family covers
// every weight (no 8 static faces to download, smoother interpolation).
import '@fontsource-variable/vazirmatn';
import '@fontsource-variable/space-grotesk';
import '@fontsource-variable/fraunces';


import '@/styles/design-system.css';
import { startSafeAreaSync } from '@/lib/safeArea';
import { bootstrapAppearance } from '@/lib/theme';
import { installAudioUnlock } from '@/lib/audioContext';
import App from './App';

// Theme before the first paint: the accent, background and motion level are
// read from the synchronous cache, so a custom accent never flashes violet.
bootstrapAppearance();

// Publish notch / status-bar / gesture-bar insets before the first paint so the
// shell never renders underneath the system bars, then keep them fresh.
startSafeAreaSync();

// Warm the two faces the shell paints with (avoids a late swap mid-launch).
if (typeof document !== 'undefined' && document.fonts?.load) {
  void document.fonts.load('600 16px "Vazirmatn Variable"').catch(() => undefined);
  void document.fonts.load('600 16px "Space Grotesk Variable"').catch(() => undefined);
  void document.fonts.load('600 16px "Fraunces Variable"').catch(() => undefined);
}

/**
 * Service worker policy.
 * Web: a PWA is useful, so register the generated worker.
 * APK: never — the assets ship inside the package, and a precache worker is a
 * known way to keep showing an old build after installing a new APK. Any worker
 * and cache left behind by earlier versions is removed once.
 */
function configureServiceWorker(): void {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;

  if (Capacitor.isNativePlatform()) {
    const FLAG = 'kairos:sw-purged:v1';
    try {
      if (window.localStorage.getItem(FLAG) === '1') return;
    } catch {
      // no storage — still attempt the cleanup below
    }
    void (async () => {
      try {
        const registrations = await navigator.serviceWorker.getRegistrations();
        await Promise.all(registrations.map((registration) => registration.unregister()));
        if (typeof caches !== 'undefined') {
          const keys = await caches.keys();
          await Promise.all(keys.map((key) => caches.delete(key)));
        }
        try {
          window.localStorage.setItem(FLAG, '1');
        } catch {
          // ignore
        }
      } catch {
        // Nothing to clean.
      }
    })();
    return;
  }

  if (import.meta.env.PROD) {
    window.addEventListener('load', () => {
      void navigator.serviceWorker.register('/sw.js').catch(() => undefined);
    });
  }
}

configureServiceWorker();
installAudioUnlock();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
