import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { applyScheme, SCHEME_OPTIONS } from './theme/workbenchTheme';
import './theme/base.css';

// Paint the default (Obsidian Cinema) scheme before the first React commit to avoid a flash.
applyScheme(document.documentElement, SCHEME_OPTIONS[0]!.scheme, 'dark');

const container = document.getElementById('root');
if (!container) throw new Error('Root container missing');

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
