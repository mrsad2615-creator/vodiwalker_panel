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
    // Insets are owned by MainActivity.java (decor-view padding + consumed
    // system-bar insets). SystemBars must NOT touch them as well, otherwise the
    // two mechanisms cancel each other out — which is what kept breaking the
    // layout on Android 15/16. It still controls the bar icon style.
    SystemBars: {
      insetsHandling: 'disable',
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
