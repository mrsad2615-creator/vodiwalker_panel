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
    // The native launch window is pure black + the vector mark (see
    // res/values*/styles.xml). The cinematic reveal itself is the in-app
    // LaunchScreen, so the plugin never shows a second splash: duration 0 means
    // it stays out of the way and the system splash hands over as soon as the
    // WebView has its first frame.
    SplashScreen: {
      launchAutoHide: true,
      launchShowDuration: 0,
      backgroundColor: '#000000',
      showSpinner: false,
    },
    // Native HTTP is used explicitly (see src/lib/ai/transport.ts) rather than by
    // letting the plugin patch window.fetch for the whole app: the patch would
    // intercept every request the WebView makes, and one failure there is a blank
    // screen, not a failed API call.
    LocalNotifications: {
      iconColor: '#6D4AFF',
      sound: 'default',
    },
  },
};

export default config;
