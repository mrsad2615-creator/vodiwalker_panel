import { defineConfig } from 'vite';

export default defineConfig({
  base: '/black-hole/',
  server: {
    host: '0.0.0.0',
    allowedHosts: true,
    strictPort: false,
  },
  preview: {
    host: '0.0.0.0',
    allowedHosts: true,
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true,
    sourcemap: false,
    minify: 'oxc',
    cssCodeSplit: true,
    reportCompressedSize: false,
    // Three.js is a single, minified WebGL runtime (~150 KB when gzip-compressed).
    chunkSizeWarningLimit: 700,
  },
});
