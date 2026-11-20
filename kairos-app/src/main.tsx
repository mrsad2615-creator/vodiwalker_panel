import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// Typography: variable fonts, bundled offline — one file per family covers
// every weight (no 8 static faces to download, smoother interpolation).
import '@fontsource-variable/vazirmatn';
import '@fontsource-variable/space-grotesk';


import '@/styles/design-system.css';
import { startSafeAreaSync } from '@/lib/safeArea';
import App from './App';

// Publish notch / status-bar / gesture-bar insets before the first paint so the
// shell never renders underneath the system bars, then keep them fresh.
startSafeAreaSync();

// Warm the two faces the shell paints with (avoids a late swap mid-launch).
if (typeof document !== 'undefined' && document.fonts?.load) {
  void document.fonts.load('600 16px "Vazirmatn Variable"').catch(() => undefined);
  void document.fonts.load('600 16px "Space Grotesk Variable"').catch(() => undefined);
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
