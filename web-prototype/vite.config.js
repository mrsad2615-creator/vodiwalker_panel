import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  server: { host: '0.0.0.0', port: 5173 },
  preview: { host: '0.0.0.0', port: 5173 },
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
