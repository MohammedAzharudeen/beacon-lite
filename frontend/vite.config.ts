/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Built files go straight into Spring Boot's static folder.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../target/classes/static",
    emptyOutDir: true,
    // Charts are the largest dependency (~150 kB gzipped); a separate chunk keeps the app code small
    rollupOptions: { output: { manualChunks: { charts: ["recharts"] } } },
    chunkSizeWarningLimit: 600,
  },
  server: { proxy: { "/api": "http://127.0.0.1:8080" } },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/setupTests.ts",
  },
});
