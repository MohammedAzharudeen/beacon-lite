/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Built files go straight into Spring Boot's static folder.
export default defineConfig({
  plugins: [react()],
  build: { outDir: '../target/classes/static', emptyOutDir: true },
  server: { proxy: { '/api': 'http://127.0.0.1:8080' } },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/setupTests.ts' },
});
