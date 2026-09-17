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
      // The glob list is written HERE, under `injectManifest`, and not under `workbox` — which is
      // where it used to live, and which is the reason the two task cues were never precached.
      // `workbox` configures `generateSW` only; this project injects its own worker (`sw.ts`), so
      // nothing read the key and workbox fell back to its own default,
      // `**/*.{js,wasm,css,html}` — js, css and html, which is exactly what the manifest held.
      // So `/task-complete.wav` and `/task-uncomplete.wav` were network-only: not in the manifest,
      // and no runtime route in `src/sw.ts` touches audio either. An installed PWA that played a
      // completion offline therefore handed `play()` an element with no usable source, and a
      // refused cue is silent — as was a first play over a fetch that failed.
      //
      // `wav` is the entry this bug needed: the cues are the only assets the app reaches for at a
      // tap rather than at load, and the native clients bundle the same clip into the app for the
      // same reason — a cue that answers a finger is not a network resource. Both clips together
      // are ~52 KB, fetched once at install. The rest of the list is the set the dead `workbox`
      // key was meant to precache, restored along with it rather than quietly narrowed.
      injectManifest: {
        globPatterns: ["**/*.{js,css,html,ico,png,svg,woff2,wav}"],
      },
      workbox: {
        // `sw.ts` registers its own NavigationRoute; these two are kept for a move back to
        // `generateSW`, and are likewise unread here.
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
