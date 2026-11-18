import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * VodiGesture preview workbench.
 *
 * Bound to 0.0.0.0 so the sandbox live-preview proxy can reach it, with permissive
 * host/origin handling because the preview is served from a dynamic `*.e2b.app` origin.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    // The proxy host and the origin differ from localhost, so never gate on them.
    allowedHosts: true,
    cors: true,
    hmr: { clientPort: 443, protocol: 'wss' },
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    allowedHosts: true,
    cors: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
