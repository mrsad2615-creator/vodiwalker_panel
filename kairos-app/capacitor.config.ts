import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'app.kairos.productivity',
  appName: 'KAIROS',
  webDir: 'dist',
  backgroundColor: '#09090D',
  android: {
    backgroundColor: '#09090D',
    allowMixedContent: false,
    // IMPORTANT: Keep captureInput false so Android soft keyboards (Gboard, Samsung, Persian IME)
    // use native Chromium InputConnection without garbling text or breaking Backspace!
    captureInput: false,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    StatusBar: {
      style: 'DARK',
      backgroundColor: '#09090D',
      overlaysWebView: false,
    },
    SplashScreen: {
      launchAutoHide: true,
      launchShowDuration: 0,
      backgroundColor: '#09090D',
      showSpinner: false,
    },
    LocalNotifications: {
      iconColor: '#8052D6',
      sound: 'default',
    },
  },
};

export default config;
