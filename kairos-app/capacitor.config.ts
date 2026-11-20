import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'app.kairos.productivity',
  appName: 'KAIROS',
  webDir: 'dist',
  backgroundColor: '#0A0A0C',
  android: {
    backgroundColor: '#0A0A0C',
    allowMixedContent: false,
    // IMPORTANT: Keep captureInput false so Android soft keyboards (Gboard, Samsung, Persian IME)
    // use native Chromium InputConnection without garbling text or breaking Backspace!
    captureInput: false,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    // Capacitor 8 edge-to-edge: inject --safe-area-inset-* so the sticky header
    // and the tab bar can stay clear of the status bar, notch and gesture bar.
    SystemBars: {
      insetsHandling: 'css',
      initialViewportFitValueHint: 'cover',
      style: 'DARK',
    },
    SplashScreen: {
      launchAutoHide: true,
      launchShowDuration: 0,
      backgroundColor: '#0A0A0C',
      showSpinner: false,
    },
    LocalNotifications: {
      iconColor: '#6E56CF',
      sound: 'default',
    },
  },
};

export default config;
