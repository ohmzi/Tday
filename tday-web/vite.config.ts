import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { sentryVitePlugin } from "@sentry/vite-plugin";
import path from "path";

export default defineConfig({
  plugins: [
    react(),
    sentryVitePlugin({
      org: "tday-kb",
      project: "tday-web",
      authToken: process.env.SENTRY_AUTH_TOKEN,
    }),
  ],
  define: {
    __APP_VERSION__: JSON.stringify(process.env.npm_package_version ?? "0.0.0"),
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
  },
});
