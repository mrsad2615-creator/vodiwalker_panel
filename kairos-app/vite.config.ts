import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'node:path';
import { readFileSync } from 'node:fs';

const pkg = JSON.parse(readFileSync(path.resolve(__dirname, 'package.json'), 'utf8')) as {
  version: string;
};

/**
 * Build identity, injected into the bundle so the app can *show* which build it
 * is: previously the version strings inside the UI were hard-coded, which made
 * every new APK look identical to the previous one.
 */
const BUILD_SHA = (process.env.KAIROS_BUILD_SHA ?? '').slice(0, 7) || 'local';
const BUILD_NUMBER = process.env.KAIROS_BUILD_NUMBER ?? 'dev';
const BUILD_TIME = process.env.KAIROS_BUILD_TIME ?? new Date().toISOString();

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
    __BUILD_NUMBER__: JSON.stringify(BUILD_NUMBER),
    __BUILD_SHA__: JSON.stringify(BUILD_SHA),
    __BUILD_TIME__: JSON.stringify(BUILD_TIME),
  },
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      // No auto-injected registration: main.tsx decides per platform. Inside the
      // APK a precache service worker can serve a stale UI after an APK update,
      // so it is only registered on the real web build.
      injectRegister: null,
      includeAssets: ['favicon-32.png', 'kairos-icon-192.png', 'kairos-icon-512.png', 'kairos-icon-maskable-512.png'],
      manifest: {
        name: 'KAIROS — کایروس | استودیوی نظم و تمرکز',
        short_name: 'KAIROS',
        description: 'اپلیکیشن پریمیوم مدیریت وظایف، عادت‌ها، تقویم شمسی، تمرکز و تحلیل عملکرد',
        lang: 'fa',
        dir: 'rtl',
        start_url: '/',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#0A0A0C',
        theme_color: '#0A0A0C',
        categories: ['productivity', 'lifestyle'],
        icons: [
          {
            src: '/kairos-icon-192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/kairos-icon-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/kairos-icon-maskable-192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'maskable',
          },
          {
            src: '/kairos-icon-maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2,ogg}'],
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    allowedHosts: true,
    cors: true,
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    allowedHosts: true,
  },
  build: {
    target: 'es2022',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'framer-motion'],
          db: ['dexie', 'dexie-react-hooks', 'jalaali-js'],
          icons: ['@phosphor-icons/react'],
        },
      },
    },
  },
});
