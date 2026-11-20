import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  server: {
    host: '0.0.0.0',
    port: 5173,
    // Allow Arena preview host (e2b.app) — no origin rejection
    cors: true,
    hmr: { host: 'localhost' },
    // Vite 5: disable host check (or list allowed hosts)
    // allowedHosts: true allows any host (useful for preview proxy)
    allowedHosts: true,
  },
  preview: {
    host: '0.0.0.0',
    port: 5173,
    cors: true,
    allowedHosts: true,
    // Required headers for PWA / sharedArrayBuffer if needed
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin',
      'Cross-Origin-Embedder-Policy': 'require-corp',
    },
  },
  plugins: [
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'robots.txt'],
      manifest: {
        name: 'پیرانشهر — شبیه‌ساز رانندگی',
        short_name: 'پیرانشهر',
        description: 'شبیه‌ساز رانندگی پیرانشهر — Photorealistic open-world driving simulator (prototype)',
        theme_color: '#0b1628',
        background_color: '#070b13',
        display: 'standalone',
        dir: 'rtl',
        lang: 'fa',
        orientation: 'landscape',
        icons: [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' }
        ]
      }
    })
  ]
});
