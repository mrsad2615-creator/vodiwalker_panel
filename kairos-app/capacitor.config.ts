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
    // Capacitor 8 / Android 15+ edge-to-edge. Because index.html does NOT ask
    // for viewport-fit=cover, Capacitor pads the WebView itself with the real
    // status-bar, notch and gesture-bar insets on every WebView version — the
    // one behaviour that cannot be broken by a WebView update.
    SystemBars: {
      insetsHandling: 'native',
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
