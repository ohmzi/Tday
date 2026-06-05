import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import { sentryVitePlugin } from "@sentry/vite-plugin";
import { VitePWA } from "vite-plugin-pwa";
import { execSync } from "node:child_process";
import path from "path";

// A unique id per build, used as the cache key for stale-build detection.
// Prefer an injected GIT_SHA (Docker build-arg), else read git, else "dev";
// always suffixed with a compact UTC timestamp so it changes every build.
function resolveBuildId(): string {
  let sha = process.env.GIT_SHA?.trim();
  if (!sha) {
    try {
      sha = execSync("git rev-parse --short HEAD", {
        stdio: ["ignore", "pipe", "ignore"],
      })
        .toString()
        .trim();
    } catch {
      sha = "";
    }
  }
  const ts = new Date().toISOString().replace(/[-:]/g, "").replace(/\.\d+/, "");
  return `${sha || "dev"}-${ts}`;
}

const BUILD_ID = resolveBuildId();
const APP_VERSION = process.env.npm_package_version ?? "0.0.0";

// Emit dist/version.json carrying the same BUILD_ID baked into the bundle, so
// the backend serves it at /version.json for clients to poll. Single source of
// truth: the const above feeds both the define and this file.
function versionJsonPlugin(): Plugin {
  return {
    name: "tday-version-json",
    generateBundle() {
      this.emitFile({
        type: "asset",
        fileName: "version.json",
        source: JSON.stringify({ buildId: BUILD_ID, version: APP_VERSION }) + "\n",
      });
    },
  };
}

export default defineConfig({
  plugins: [
    react(),
    versionJsonPlugin(),
    VitePWA({
      registerType: "autoUpdate",
      manifest: false,
      injectRegister: null,
      strategies: "injectManifest",
      srcDir: "src",
      filename: "sw.ts",
      workbox: {
        globPatterns: ["**/*.{js,css,html,ico,png,svg,woff2}"],
        navigateFallback: "/index.html",
        navigateFallbackDenylist: [/^\/api/, /^\/ws/],
      },
    }),
    sentryVitePlugin({
      org: "tday-kb",
      project: "tday-web",
      authToken: process.env.SENTRY_AUTH_TOKEN,
    }),
  ],
  define: {
    __APP_VERSION__: JSON.stringify(APP_VERSION),
    __BUILD_ID__: JSON.stringify(BUILD_ID),
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
    rollupOptions: {
      output: {
        manualChunks: {
          "vendor-react": ["react", "react-dom"],
          "vendor-icons": ["lucide-react"],
          "vendor-date": ["date-fns"],
          "vendor-i18n": ["i18next", "react-i18next"],
        },
      },
    },
  },
});
