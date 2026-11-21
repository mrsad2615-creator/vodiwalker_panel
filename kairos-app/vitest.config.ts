import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // The suite renders the whole app plus a 12-month year grid; CI runners are
    // roughly 3× slower than a laptop, so give the heavy tests real headroom.
    testTimeout: 30000,
    hookTimeout: 30000,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
