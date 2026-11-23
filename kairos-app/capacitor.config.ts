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
    // Requests from the WebView are browser requests: the provider's CORS policy
    // decides whether they are allowed, and several refuse an `Authorization`
    // preflight from an app origin. CapacitorHttp performs them in Java instead —
    // no CORS, no preflight, no mixed-content block. This is what makes the API
    // work on a real phone, not just in the desktop preview.
    CapacitorHttp: {
      enabled: true,
    },
    LocalNotifications: {
      iconColor: '#6D4AFF',
      sound: 'default',
    },
  },
};

export default config;
