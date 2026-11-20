import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// 100% Offline Bundled Typography System
import '@fontsource/vazirmatn/400.css';
import '@fontsource/vazirmatn/500.css';
import '@fontsource/vazirmatn/600.css';
import '@fontsource/vazirmatn/700.css';
import '@fontsource/vazirmatn/800.css';


import '@fontsource/space-grotesk/500.css';
import '@fontsource/space-grotesk/600.css';
import '@fontsource/space-grotesk/700.css';


import '@/styles/design-system.css';
import { startSafeAreaSync } from '@/lib/safeArea';
import App from './App';

// Publish notch / status-bar / gesture-bar insets before the first paint so the
// shell never renders underneath the system bars, then keep them fresh.
startSafeAreaSync();

// Warm the two faces the shell paints with (avoids a late swap mid-launch).
if (typeof document !== 'undefined' && document.fonts?.load) {
  void document.fonts.load('600 16px Vazirmatn').catch(() => undefined);
  void document.fonts.load('600 16px "Space Grotesk"').catch(() => undefined);
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
