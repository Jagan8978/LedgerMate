import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// In development the browser talks to http://localhost:5173 only. Vite forwards /api/* to the
// Spring Boot backend, so the HttpOnly auth cookie stays same-origin (works with SameSite=Strict).
// If the backend runs on another port: $env:VITE_BACKEND_URL="http://localhost:8081"; npm run dev
const backend = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
