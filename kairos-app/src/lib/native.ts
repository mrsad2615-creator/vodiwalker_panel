import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { StatusBar, Style } from '@capacitor/status-bar';
import { SplashScreen } from '@capacitor/splash-screen';

export function isNativeAndroid(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

export async function initNativeShell(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    await StatusBar.setStyle({ style: Style.Dark });
    await StatusBar.setBackgroundColor({ color: '#0A0A0C' });
  } catch {
    // Graceful no-op outside native shell
  }
  try {
    await SplashScreen.hide();
  } catch {
    // Graceful no-op
  }
}

export async function triggerHaptic(
  kind: 'light' | 'medium' | 'success' | 'warning' = 'light',
  enabled: boolean = true
): Promise<void> {
  if (!enabled) return;
  try {
    if (Capacitor.isNativePlatform()) {
      if (kind === 'success') {
        await Haptics.notification({ type: NotificationType.Success });
      } else if (kind === 'warning') {
        await Haptics.notification({ type: NotificationType.Warning });
      } else {
        await Haptics.impact({
          style: kind === 'medium' ? ImpactStyle.Medium : ImpactStyle.Light,
        });
      }
    } else if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      navigator.vibrate(kind === 'light' ? 10 : kind === 'success' ? [12, 35, 16] : 18);
    }
  } catch {
    // Haptics optional
  }
}

/**
 * Android hardware back button.
 * `handler` returns true when it consumed the event; otherwise the app is sent
 * to the background instead of being killed (Android default for WebView).
 * Returns a cleanup function (no-op on web).
 */
export async function setupAndroidBackButton(handler: () => boolean): Promise<() => void> {
  if (!isNativeAndroid()) return () => {};
  try {
    const { App } = await import('@capacitor/app');
    const listener = await App.addListener('backButton', () => {
      const handled = handler();
      if (!handled) {
        void App.minimizeApp().catch(() => undefined);
      }
    });
    return () => {
      void listener.remove();
    };
  } catch {
    return () => {};
  }
}
